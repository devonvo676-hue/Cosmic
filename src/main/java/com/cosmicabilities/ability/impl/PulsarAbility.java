package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Instant beam: charge rings at your hand, a white-hot core wrapped in a cyan helix, sparks on impact. */
public class PulsarAbility extends CosmicAbility {
    private static final double RANGE = 40;

    public PulsarAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.PULSAR); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        w.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 2f);
        every(4, t -> {
            if (!player.isOnline()) return false;
            Location hand = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.9)).add(0, -0.25, 0);
            Fx.ring(w, hand, 0.55 - t * 0.12, 10, t, Fx.dust(0x4cc9f0, 0.8f));
            if (t == 4) {
                Vector dir = player.getEyeLocation().getDirection().normalize();
                double damage = dmg(10, slot);
                if (bonus(slot)) {
                    for (double yaw : new double[]{-10, 0, 10}) fire(player, rotateY(dir.clone(), Math.toRadians(yaw)), damage * 0.8);
                } else {
                    fire(player, dir, damage);
                }
                w.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 0.7f);
                w.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.8f);
            }
            return true;
        });
        return true;
    }

    private static Vector rotateY(Vector v, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Vector(v.getX() * cos - v.getZ() * sin, v.getY(), v.getX() * sin + v.getZ() * cos).normalize();
    }

    private void fire(Player player, Vector dir, double damage) {
        World w = player.getWorld();
        Location start = player.getEyeLocation().add(dir.clone().multiply(0.6)).add(0, -0.2, 0);
        List<Location> path = new ArrayList<>();
        Set<UUID> hitSet = new HashSet<>();
        int pierced = 0;
        Location pos = start.clone();
        for (double d = 0; d < RANGE; d += 0.3) {
            pos = start.clone().add(dir.clone().multiply(d));
            if (pos.getBlock().getType().isSolid()) break;
            path.add(pos);
            for (Entity e : w.getNearbyEntities(pos, 0.6, 0.9, 0.6)) {
                if (!Targets.isEnemy(player, e) || !hitSet.add(e.getUniqueId())) continue;
                hit(player, (LivingEntity) e, pierced == 0 ? damage : damage * 0.6);
                w.spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0.3);
                w.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 1, 0), 10, 0.1, 0.1, 0.1, 0.15);
                pierced++;
            }
            if (pierced >= 2) break;
        }
        final Location end = pos;
        Fx.beam(start, end, Material.LIGHT_BLUE_STAINED_GLASS, 0.42f, 7);
        Fx.beam(start, end, Material.WHITE_CONCRETE, 0.12f, 5);
        // draw the beam over 3 ticks so it lingers and thins out
        every(3, t -> {
            float core = 1.4f - t * 0.35f;
            for (int i = 0; i < path.size(); i++) {
                Location p = path.get(i);
                w.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, Fx.dust(t == 0 ? 0xffffff : 0x90e0ef, core));
                if (t == 0) {
                    double a = i * 0.9;
                    Vector perp = perpendicular(dir, a).multiply(0.28);
                    w.spawnParticle(Particle.DUST, p.clone().add(perp), 1, 0, 0, 0, 0, Fx.dust(0x4cc9f0, 0.9f));
                }
            }
            if (t == 0) {
                Fx.burst(w, end, Particle.ELECTRIC_SPARK, 18, 0.4);
                w.spawnParticle(Particle.END_ROD, end, 6, 0.1, 0.1, 0.1, 0.05);
            }
            return true;
        });
    }

    /** A unit vector perpendicular to dir, rotated by angle around it (for the helix). */
    private static Vector perpendicular(Vector dir, double angle) {
        Vector up = Math.abs(dir.getY()) > 0.95 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector a = dir.clone().crossProduct(up).normalize();
        Vector b = dir.clone().crossProduct(a).normalize();
        return a.multiply(Math.cos(angle)).add(b.multiply(Math.sin(angle)));
    }
}
