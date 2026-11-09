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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Instant beam: charge rings at your hand, a white-hot core wrapped in a cyan helix, sparks on impact. */
public class PulsarAbility extends CosmicAbility {
    private static final int CHARGE = 6;
    private static final double RANGE = 40;

    public PulsarAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.PULSAR); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        w.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 2f);
        w.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.6f);
        // charge: a tiny spinning neutron star forms in front of you while rings of energy collapse into it
        Location first = muzzle(player);
        ItemDisplay star = Fx.sprite(first, "pulsar", 0.05f, Display.Billboard.CENTER);
        star.setTeleportDuration(1);
        Fx.animate(star, CHARGE, Fx.sized(1.1f, 1.1f, 6f), 1);
        every(CHARGE, t -> {
            if (!player.isOnline()) { star.remove(); return false; }
            Location hand = muzzle(player);
            Vector dir = player.getEyeLocation().getDirection().normalize();
            hand.setYaw(0);
            hand.setPitch(0);
            star.teleport(hand);
            if (t % 2 == 0) {
                ItemDisplay ring = Fx.disc(hand.clone().add(dir.clone().multiply(0.15)), t % 4 == 0 ? "ring_cyan" : "ring_white", 2.4f, dir);
                Fx.animate(ring, 4, Fx.discShape(0.05f, dir, 1.5f), 1);
                Fx.removeLater(ring, 6);
            }
            w.spawnParticle(Particle.ELECTRIC_SPARK, hand, 3, 0.25, 0.25, 0.25, 0.02);
            w.playSound(hand, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.8f + t * 0.15f);
            if (t == CHARGE) {
                star.remove();
                double damage = dmg(14, slot);
                if (bonus(slot)) {
                    for (double yaw : new double[]{-10, 0, 10}) fire(player, rotateY(dir.clone(), Math.toRadians(yaw)), damage * 0.8);
                } else {
                    fire(player, dir, damage);
                }
                // recoil
                player.setVelocity(player.getVelocity().add(dir.clone().multiply(-0.35).setY(0.05)));
                w.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.8f);
                w.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 0.7f);
                w.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.5f, 2f);
            }
            return true;
        });
        return true;
    }

    private static Location muzzle(Player p) {
        Location eye = p.getEyeLocation();
        return eye.add(eye.getDirection().multiply(1.0)).add(0, -0.25, 0);
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
                Fx.flare(e.getLocation().add(0, e.getHeight() * 0.6, 0), "flare_white", 1.8f, 7);
                w.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 1, 0), 10, 0.1, 0.1, 0.1, 0.15);
                pierced++;
            }
            if (pierced >= 2) break;
        }
        final Location end = pos;
        double length = start.distance(end);
        // the beam: a thick cyan glow with a white-hot core that flashes wider once, then thins out
        ItemDisplay outer = Fx.ray(start, end, "beam_cyan", 1.5f, 9);
        Fx.ray(start, end, "beam_white", 0.5f, 7);
        Fx.ray(start, end, "beam_purple", 0.9f, 5);
        Fx.flare(start, "flare_cyan", 2.2f, 7);
        Fx.flare(end, "flare_white", 3f, 9);
        // railgun shock rings ripple down the beam from you to the target
        for (double d = 1.2; d < length; d += 2.2) {
            Location at = start.clone().add(dir.clone().multiply(d));
            int delay = 1 + (int) (d / 6);
            ItemDisplay ring = Fx.disc(at, ((int) d) % 2 == 0 ? "ring_cyan" : "ring_white", 0.3f, dir);
            Fx.animate(ring, 4, Fx.discShape(1.9f, dir, 0.8f), delay);
            Fx.animate(ring, 4, Fx.discShape(0.05f, dir, 1.6f), delay + 4);
            Fx.removeLater(ring, delay + 10);
        }
        // impact: a shockwave disc facing back down the beam
        ItemDisplay shock = Fx.disc(end.clone().subtract(dir.clone().multiply(0.2)), "ring_cyan", 0.4f, dir);
        Fx.animate(shock, 6, Fx.discShape(4.5f, dir, 1f), 1);
        Fx.removeLater(shock, 8);
        w.playSound(end, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.8f);
        // a double helix of light spirals around the beam
        every(3, t -> {
            for (int i = 0; i < path.size(); i++) {
                Location p = path.get(i);
                double a = i * 0.7 + t * 1.2;
                for (int strand = 0; strand < 2; strand++) {
                    Vector perp = perpendicular(dir, a + strand * Math.PI).multiply(0.45 - t * 0.08);
                    w.spawnParticle(Particle.DUST, p.clone().add(perp), 1, 0, 0, 0, 0,
                            Fx.dust(strand == 0 ? 0x4cc9f0 : 0xc77dff, 1.0f - t * 0.2f));
                }
                if (t == 0 && i % 3 == 0) w.spawnParticle(Particle.END_ROD, p, 1, 0.05, 0.05, 0.05, 0.02);
            }
            if (t == 0) {
                Fx.burst(w, end, Particle.ELECTRIC_SPARK, 30, 0.5);
                w.spawnParticle(Particle.END_ROD, end, 14, 0.1, 0.1, 0.1, 0.18);
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
