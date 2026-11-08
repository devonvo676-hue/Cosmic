package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Blink: leaves a shimmering after-image and a void rift that collapses and detonates. */
public class VoidwalkerAbility extends CosmicAbility {
    public VoidwalkerAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.VOIDWALKER); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location from = player.getLocation();
        Location dest = findDestination(player, 10);
        if (dest == null) {
            plugin.engine().notify(player, plugin.mm("<red>No room to blink there."));
            return false;
        }
        World w = player.getWorld();
        // after-image: a column of particles shaped like the player
        for (double y = 0.1; y < 1.9; y += 0.15) {
            double width = y > 1.4 ? 0.22 : 0.32;
            w.spawnParticle(Particle.DUST, from.clone().add(0, y, 0), 3, width, 0.03, width, 0, Fx.dust(0x7b2cbf, 1.1f));
        }
        Fx.line(w, from.clone().add(0, 1, 0), dest.clone().add(0, 1, 0), 0.35, Fx.dust(0xe0aaff, 0.8f));
        w.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        player.teleport(dest);
        player.setFallDistance(0);
        w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
        w.spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);

        rift(player, from.clone().add(0, 1, 0), dmg(7, slot), 12);
        if (bonus(slot)) {
            rift(player, dest.clone().add(0, 1, 0), dmg(5, slot), 6);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 1, false, true, true));
        }
        return true;
    }

    /** A tear in space: a dark pane spinning open, sucking in, then detonating. */
    private void rift(Player player, Location c, double damage, int fuse) {
        World w = c.getWorld();
        BlockDisplay tear = Fx.block(c, Material.BLACK_STAINED_GLASS, new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf(), false);
        tear.setGlowing(true);
        tear.setGlowColorOverride(Color.fromRGB(0x9d4edd));
        Fx.animate(tear, fuse, Fx.centred(0.15f, 2.2f, 1.2f, new Quaternionf().rotateY(3.5f)), 1);
        every(fuse, t -> {
            if (t % 2 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = t * 0.5 + i;
                    Location p = c.clone().add(Math.cos(a) * 1.6, Math.sin(a * 0.7) * 1.0, Math.sin(a) * 1.6);
                    Vector in = c.toVector().subtract(p.toVector()).normalize();
                    w.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, Fx.dust(0x5a189a, 1.1f));
                    w.spawnParticle(Particle.REVERSE_PORTAL, p, 0, in.getX(), in.getY(), in.getZ(), 0.25);
                }
            }
            if (t == fuse) {
                tear.remove();
                w.spawnParticle(Particle.PORTAL, c, 100, 0.5, 0.8, 0.5, 1.5);
                w.spawnParticle(Particle.SQUID_INK, c, 20, 0.4, 0.5, 0.4, 0.08);
                Fx.ring(w, c, 2.8, 30, 0, Fx.dust(0xc77dff, 1.5f));
                w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.5f);
                w.playSound(c, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 1.2f);
                if (player.isOnline()) {
                    for (LivingEntity e : Targets.enemies(player, c, 3)) {
                        hit(player, e, damage);
                        Targets.knockAway(c, e, 0.8, 0.4);
                    }
                }
            }
            return true;
        });
    }

    private Location findDestination(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, dir, maxDistance, FluidCollisionMode.NEVER, true);
        double allowed = hit == null ? maxDistance : eye.toVector().distance(hit.getHitPosition()) - 0.8;
        Location base = player.getLocation();
        for (double d = allowed; d >= 1.0; d -= 0.5) {
            Location candidate = base.clone().add(dir.clone().multiply(d));
            candidate.setYaw(base.getYaw());
            candidate.setPitch(base.getPitch());
            if (safe(candidate)) return candidate;
            Location up = candidate.clone().add(0, 1, 0);
            if (safe(up)) return up;
        }
        return null;
    }

    private static boolean safe(Location loc) {
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        return feet.isPassable() && head.isPassable();
    }
}
