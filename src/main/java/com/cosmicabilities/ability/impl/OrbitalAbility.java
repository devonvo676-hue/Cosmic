package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A tiny solar system orbits you: the Sun, Earth, Mars and Saturn, each spinning on its axis with its own trail,
 * smashing into anything that gets close. Planetary Orbit (bonus): they break orbit and hunt down enemies.
 */
public class OrbitalAbility extends CosmicAbility {
    private record Planet(String model, int trail, float size, double radius, double speed, double tilt) {}

    private static final Planet[] PLANETS = {
            new Planet("planet_sun", 0xffb703, 0.75f, 2.6, 0.14, 0.25),
            new Planet("planet_earth", 0x4cc9f0, 0.55f, 2.0, 0.22, 0.45),
            new Planet("planet_mars", 0xe76f51, 0.48f, 2.9, 0.18, -0.35),
            new Planet("planet_saturn", 0xe9c46a, 0.62f, 2.3, 0.16, 0.6)};

    public OrbitalAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.ORBITAL); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        int duration = 120;
        double damage = dmg(4, slot);
        List<ItemDisplay> bodies = new ArrayList<>();
        for (Planet p : PLANETS) {
            ItemDisplay d = Fx.model(player.getLocation().add(0, 1, 0), p.model(), 0.05f);
            d.setTeleportDuration(2);
            Fx.animate(d, 6, Fx.spin(p.size(), new Quaternionf()), 1);
            bodies.add(d);
        }
        w.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.6f);
        w.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
        Map<UUID, Integer> lastHit = new HashMap<>();

        every(duration, t -> {
            if (!player.isOnline() || player.isDead()) { bodies.forEach(b -> { if (b.isValid()) b.remove(); }); return false; }
            Location c = player.getLocation().add(0, 1.1, 0);
            for (int i = 0; i < PLANETS.length; i++) {
                Planet pl = PLANETS[i];
                ItemDisplay body = bodies.get(i);
                double a = t * pl.speed() + i * Math.PI / 2;
                Location p = c.clone().add(Math.cos(a) * pl.radius(), Math.sin(a) * pl.tilt(), Math.sin(a) * pl.radius());
                body.teleport(p);
                if (t % 2 == 0 && t > 6) {
                    // each planet spins on its own axis (Saturn keeps its ring tilt)
                    Fx.animate(body, 2, Fx.spin(pl.size(), new Quaternionf().rotateY(t * 0.25f + i)), 1);
                }
                // fading orbit trail in the planet's colour
                double back = a - 0.3;
                w.spawnParticle(Particle.DUST, c.getX() + Math.cos(back) * pl.radius(), c.getY() + Math.sin(back) * pl.tilt(),
                        c.getZ() + Math.sin(back) * pl.radius(), 1, 0, 0, 0, 0, Fx.dust(pl.trail(), 0.7f));
                if (i == 0 && t % 3 == 0) w.spawnParticle(Particle.FLAME, p, 1, 0.1, 0.1, 0.1, 0.01);
                for (LivingEntity e : Targets.enemies(player, p, 1.1)) {
                    Integer last = lastHit.get(e.getUniqueId());
                    if (last != null && t - last < 10) continue;
                    lastHit.put(e.getUniqueId(), t);
                    hit(player, e, damage);
                    if (i == 0) e.setFireTicks(Math.max(e.getFireTicks(), 40));   // the Sun burns
                    Vector out = e.getLocation().toVector().subtract(c.toVector()).setY(0);
                    if (out.lengthSquared() > 0.01) e.setVelocity(out.normalize().multiply(0.5).setY(0.25));
                    w.spawnParticle(Particle.ELECTRIC_SPARK, p, 12, 0.15, 0.15, 0.15, 0.2);
                    w.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.3f);
                }
            }
            if (t % 20 == 0) w.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.8f);
            if (t >= duration) {
                if (bonus(slot)) launch(player, bodies, dmg(6, slot));
                else bodies.forEach(b -> {
                    Fx.animate(b, 5, Fx.spin(0.01f, new Quaternionf()), 1);
                    Fx.removeLater(b, 7);
                });
                return false;
            }
            return true;
        });
        return true;
    }

    /** Planetary Orbit: each planet locks onto an enemy and slams into it. */
    private void launch(Player player, List<ItemDisplay> bodies, double damage) {
        World w = player.getWorld();
        w.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.1f);
        List<LivingEntity> targets = Targets.enemies(player, player.getLocation(), 14);
        for (int i = 0; i < bodies.size(); i++) {
            ItemDisplay body = bodies.get(i);
            LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
            Vector fallback = body.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.2).normalize();
            int trail = PLANETS[i].trail();
            every(16, t -> {
                if (!body.isValid()) return false;
                Location pos = body.getLocation();
                Location aim = target != null && target.isValid() ? target.getLocation().add(0, target.getHeight() / 2, 0)
                        : pos.clone().add(fallback.clone().multiply(1.2));
                Vector step = aim.toVector().subtract(pos.toVector());
                if (target != null && target.isValid() && step.length() < 1.0) {
                    hit(player, target, damage);
                    w.spawnParticle(Particle.EXPLOSION, aim, 1);
                    w.spawnParticle(Particle.DUST, aim, 25, 0.4, 0.4, 0.4, 0, Fx.dust(trail, 1.6f));
                    w.playSound(aim, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
                    body.remove();
                    return false;
                }
                if (step.lengthSquared() > 0) step.normalize().multiply(1.2);
                body.teleport(pos.add(step));
                w.spawnParticle(Particle.DUST, pos, 3, 0.1, 0.1, 0.1, 0, Fx.dust(trail, 1.1f));
                if (t >= 16) body.remove();
                return true;
            });
        }
    }
}
