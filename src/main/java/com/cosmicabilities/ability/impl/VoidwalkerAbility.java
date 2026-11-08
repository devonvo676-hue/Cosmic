package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.util.Targets;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class VoidwalkerAbility extends CosmicAbility {
    public VoidwalkerAbility(CosmicAbilitiesPlugin plugin) { super(plugin); }

    @Override
    public void cast(Player player, AbilitySlot data) {
        int lvl = data.level();
        double distance = scale(6, 0.6, lvl) + (data.isPathA() ? 3 : 0);
        double blastDamage = scale(3, 0.2, lvl) + (data.isPathB() ? 3 : 0);
        boolean blastAtDestination = data.isPathB() || data.evolved();

        Location from = player.getLocation();
        Location dest = findDestination(player, distance);
        if (dest == null) {
            plugin.engine().notify(player, plugin.mm("<red>No room to blink there."));
            plugin.engine().refundCooldown(player.getUniqueId());
            return;
        }

        World world = player.getWorld();
        // Particle line between the two points.
        Vector step = dest.toVector().subtract(from.toVector());
        double len = step.length();
        if (len > 0) {
            step.normalize().multiply(0.5);
            Location cur = from.clone().add(0, 1, 0);
            for (double d = 0; d < len; d += 0.5) {
                dust(world, cur, Color.fromRGB(114, 9, 183), 1.2f, 1, 0.05);
                cur.add(step);
            }
        }

        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        player.teleport(dest);
        player.setFallDistance(0);
        world.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
        world.spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);

        // The void explosion left behind detonates after half a second.
        Location origin = from.clone();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> voidBlast(player, origin, blastDamage), 10L);
        if (blastAtDestination) {
            Location end = dest.clone();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> voidBlast(player, end, blastDamage * 0.75), 4L);
        }
        if (data.evolved()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 1, true, true));
        }
    }

    private void voidBlast(Player player, Location loc, double damage) {
        if (!player.isOnline()) return;
        World world = loc.getWorld();
        Location c = loc.clone().add(0, 1, 0);
        world.spawnParticle(Particle.PORTAL, c, 80, 0.4, 0.6, 0.4, 1.4);
        world.spawnParticle(Particle.SQUID_INK, c, 15, 0.5, 0.5, 0.5, 0.05);
        world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
        for (LivingEntity e : Targets.enemies(player, c, 2.5)) {
            Targets.damage(player, e, damage);
            Targets.knockAway(c, e, 0.6, 0.35);
        }
    }

    /** Finds the farthest safe 2-block-tall spot along the look direction. */
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
            // Try one block up (e.g. stepping onto a ledge).
            Location up = candidate.clone().add(0, 1, 0);
            if (safe(up)) return up;
        }
        return null;
    }

    private static boolean safe(Location loc) {
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        return feet.isPassable() && head.isPassable() && !feet.isLiquid() || (feet.isLiquid() && head.isPassable());
    }
}
