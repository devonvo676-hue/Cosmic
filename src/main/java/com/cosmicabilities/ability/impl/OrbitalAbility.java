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
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A solar system around you. The Sun, Earth, Mars and Saturn share one circular orbit (evenly spaced, same speed),
 * marked by a glowing ring on the ground. That ring is the ability's area: anything inside it takes pulses of damage,
 * and anything a planet sweeps through is smashed aside. Planetary Orbit (bonus): they break orbit and hunt enemies.
 */
public class OrbitalAbility extends CosmicAbility {
    private record Planet(String sprite, int trail, float size) {}

    private static final Planet[] PLANETS = {
            new Planet("sun", 0xffb703, 1.15f),
            new Planet("earth", 0x4cc9f0, 0.85f),
            new Planet("mars", 0xe76f51, 0.75f),
            new Planet("saturn", 0xe9c46a, 1.35f)};
    private static final double ORBIT = 3.2;      // orbit radius = AoE radius
    private static final double SPEED = 0.11;     // radians per tick (~2.9 s per lap)
    private static final int DURATION = 120;

    public OrbitalAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.ORBITAL); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        double damage = dmg(6, slot);
        List<ItemDisplay> bodies = new ArrayList<>();
        for (int i = 0; i < PLANETS.length; i++) {
            ItemDisplay d = Fx.sprite(position(player.getLocation(), i, 0), PLANETS[i].sprite(), 0.05f, Display.Billboard.CENTER);
            d.setTeleportDuration(1);
            Fx.animate(d, 6, Fx.sized(PLANETS[i].size(), PLANETS[i].size(), 0), 1);
            Fx.removeLater(d, DURATION + 40);
            bodies.add(d);
        }
        // the orbit path, drawn on the ground and following you
        ItemDisplay path = Fx.flat(player.getLocation().add(0, 0.06, 0), "ring_white", 0.1f, 0);
        path.setTeleportDuration(1);
        Fx.animate(path, 6, Fx.flatShape((float) (ORBIT * 2.25), 0), 1);
        Fx.removeLater(path, DURATION + 10);
        w.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.6f);
        w.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
        Map<UUID, Integer> lastHit = new HashMap<>();

        every(DURATION, t -> {
            if (!player.isOnline() || player.isDead()) {
                bodies.forEach(b -> { if (b.isValid()) b.remove(); });
                path.remove();
                return false;
            }
            Location feet = player.getLocation();
            path.teleport(feet.clone().add(0, 0.06, 0));
            Location center = feet.clone().add(0, 1.1, 0);
            for (int i = 0; i < PLANETS.length; i++) {
                Location p = position(feet, i, t);
                bodies.get(i).teleport(p);
                // comet-like tail behind each planet along the orbit
                for (int k = 1; k <= 3; k++) {
                    Location tail = position(feet, i, t - k * 1.5);
                    w.spawnParticle(Particle.DUST, tail, 1, 0, 0, 0, 0, Fx.dust(PLANETS[i].trail(), 1.1f - k * 0.25f));
                }
                if (i == 0 && t % 2 == 0) w.spawnParticle(Particle.FLAME, p, 1, 0.15, 0.15, 0.15, 0.01);
                // direct hits from a planet sweeping through
                for (LivingEntity e : Targets.enemies(player, p, 1.2)) {
                    Integer last = lastHit.get(e.getUniqueId());
                    if (last != null && t - last < 10) continue;
                    lastHit.put(e.getUniqueId(), t);
                    hit(player, e, damage);
                    if (i == 0) e.setFireTicks(Math.max(e.getFireTicks(), 40));
                    Vector out = e.getLocation().toVector().subtract(center.toVector()).setY(0);
                    if (out.lengthSquared() > 0.01) e.setVelocity(out.normalize().multiply(0.55).setY(0.25));
                    Fx.flare(p, "flare_white", 1.4f, 6);
                    w.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.3f);
                }
            }
            // area pulse: everything inside the orbit takes damage every second
            if (t % 20 == 10) {
                Fx.wave(feet, "ring_white", 0.5f, (float) (ORBIT * 2), 8);
                for (LivingEntity e : Targets.enemies(player, center, ORBIT)) hit(player, e, damage * 0.5);
            }
            if (t % 20 == 0) w.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.8f);
            if (t >= DURATION) {
                path.remove();
                if (bonus(slot)) launch(player, bodies, dmg(9, slot));
                else bodies.forEach(b -> {
                    Fx.animate(b, 5, Fx.sized(0.01f, 0.01f, 0), 1);
                    Fx.removeLater(b, 7);
                });
                return false;
            }
            return true;
        });
        return true;
    }

    /** Where planet i is at tick t: same circle, evenly spaced, gently bobbing. */
    private static Location position(Location feet, int i, double t) {
        double a = t * SPEED + i * Math.PI / 2;
        return feet.clone().add(Math.cos(a) * ORBIT, 1.2 + Math.sin(t * 0.08 + i) * 0.15, Math.sin(a) * ORBIT);
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
                    Fx.flare(aim, "flare_gold", 3f, 10);
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
