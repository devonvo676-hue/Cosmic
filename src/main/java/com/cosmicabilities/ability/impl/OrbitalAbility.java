package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Glowing moon-cubes on a tilted orbit with fading trails. Tier III: they peel off and home in on enemies. */
public class OrbitalAbility extends CosmicAbility {
    public OrbitalAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.ORBITAL); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        int count = 4;
        int duration = 120;
        double radius = 2.3;
        double damage = dmg(4, slot);
        List<BlockDisplay> orbs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlockDisplay orb = Fx.block(player.getLocation().add(0, 1, 0), Material.SEA_LANTERN,
                    new Vector3f(0.38f, 0.38f, 0.38f), new Quaternionf(), true);
            orb.setGlowing(true);
            orb.setGlowColorOverride(Color.fromRGB(0xffe8a3));
            orb.setTeleportDuration(2);
            orbs.add(orb);
        }
        w.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.6f);
        Map<UUID, Integer> lastHit = new HashMap<>();

        every(duration, t -> {
            if (!player.isOnline() || player.isDead()) { orbs.forEach(o -> { if (o.isValid()) o.remove(); }); return false; }
            Location c = player.getLocation().add(0, 1, 0);
            double base = t * 0.2;
            for (int i = 0; i < orbs.size(); i++) {
                double a = base + i * Math.PI * 2 / orbs.size();
                double tilt = Math.sin(a) * 0.45;   // tilted orbital plane
                Location p = c.clone().add(Math.cos(a) * radius, tilt, Math.sin(a) * radius);
                BlockDisplay orb = orbs.get(i);
                orb.teleport(p);
                if (t % 2 == 0) {
                    float spin = t * 0.3f + i;
                    Fx.animate(orb, 2, Fx.centred(0.38f, 0.38f, 0.38f, new Quaternionf().rotateXYZ(spin, spin * 0.6f, 0)), 1);
                }
                w.spawnParticle(Particle.DUST, p, 1, 0.03, 0.03, 0.03, 0, Fx.dust(0xffe8a3, 0.9f));
                Location trail = c.clone().add(Math.cos(a - 0.35) * radius, Math.sin(a - 0.35) * 0.45, Math.sin(a - 0.35) * radius);
                w.spawnParticle(Particle.DUST, trail, 1, 0, 0, 0, 0, Fx.dust(0xadb5bd, 0.6f));
                for (LivingEntity e : Targets.enemies(player, p, 1.1)) {
                    Integer last = lastHit.get(e.getUniqueId());
                    if (last != null && t - last < 10) continue;
                    lastHit.put(e.getUniqueId(), t);
                    hit(player, e, damage);
                    Vector out = e.getLocation().toVector().subtract(c.toVector()).setY(0);
                    if (out.lengthSquared() > 0.01) e.setVelocity(out.normalize().multiply(0.45).setY(0.25));
                    w.spawnParticle(Particle.ELECTRIC_SPARK, p, 10, 0.15, 0.15, 0.15, 0.2);
                    w.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.5f);
                }
            }
            if (t % 16 == 0) w.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.8f);
            if (t >= duration) {
                if (bonus(slot)) launch(player, orbs, dmg(6, slot));
                else orbs.forEach(o -> { if (o.isValid()) o.remove(); });
                return false;
            }
            return true;
        });
        return true;
    }

    /** Planetary Orbit: each orb picks the nearest enemy and flies at it. */
    private void launch(Player player, List<BlockDisplay> orbs, double damage) {
        World w = player.getWorld();
        w.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.3f);
        List<LivingEntity> targets = Targets.enemies(player, player.getLocation(), 14);
        for (int i = 0; i < orbs.size(); i++) {
            BlockDisplay orb = orbs.get(i);
            LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
            Vector fallback = orb.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize();
            every(14, t -> {
                if (!orb.isValid()) return false;
                Location pos = orb.getLocation();
                Location aim = target != null && target.isValid() ? target.getLocation().add(0, target.getHeight() / 2, 0)
                        : pos.clone().add(fallback.clone().multiply(1.2));
                Vector step = aim.toVector().subtract(pos.toVector());
                if (target != null && target.isValid() && step.length() < 1.0) {
                    hit(player, target, damage);
                    w.spawnParticle(Particle.EXPLOSION, aim, 1);
                    Fx.burst(w, aim, Particle.END_ROD, 14, 0.25);
                    w.playSound(aim, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
                    orb.remove();
                    return false;
                }
                if (step.lengthSquared() > 0) step.normalize().multiply(1.2);
                orb.teleport(pos.add(step));
                w.spawnParticle(Particle.END_ROD, pos, 1, 0, 0, 0, 0);
                if (t >= 14) orb.remove();
                return true;
            });
        }
    }
}
