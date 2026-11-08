package com.cosmicabilities.util;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Shared targeting / damage helpers so every ability treats "enemy" and "ally" the same way. */
public final class Targets {
    private static CosmicAbilitiesPlugin plugin;

    private Targets() {}

    public static void init(CosmicAbilitiesPlugin p) {
        plugin = p;
    }

    public static boolean isEnemy(Player caster, Entity e) {
        if (!(e instanceof LivingEntity living) || e.equals(caster) || living.isDead() || !living.isValid()) return false;
        if (e instanceof ArmorStand) return false;
        if (e instanceof Tameable tame && tame.isTamed() && caster.getUniqueId().equals(tame.getOwnerUniqueId())) return false;
        if (e instanceof Player p) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return false;
            return plugin.getConfig().getBoolean("abilities.hit-players", true);
        }
        if (e instanceof Enemy) return true;
        return plugin.getConfig().getBoolean("abilities.hit-passive-mobs", false);
    }

    public static List<LivingEntity> enemies(Player caster, Location center, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return list;
        double r2 = radius * radius;
        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!isEnemy(caster, e)) continue;
            if (e.getLocation().distanceSquared(center) <= r2) list.add((LivingEntity) e);
        }
        return list;
    }

    /** Players (including the caster) within radius. */
    public static List<Player> allies(Player caster, Location center, double radius) {
        List<Player> list = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return list;
        double r2 = radius * radius;
        for (Player p : world.getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(center) <= r2) list.add(p);
        }
        if (!list.contains(caster) && caster.getWorld().equals(world)
                && caster.getLocation().distanceSquared(center) <= r2) list.add(caster);
        return list;
    }

    /** Deals ability damage attributed to the caster (fires normal damage events, so PvP/region plugins apply). */
    public static void damage(Player caster, LivingEntity target, double amount) {
        if (amount <= 0) return;
        double mult = plugin.getConfig().getDouble("abilities.damage-multiplier", 1.0);
        target.damage(amount * mult, caster);
        target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, target.getHeight() * 0.6, 0),
                8, 0.25, 0.3, 0.25, 0.15);
    }

    public static void knockAway(Location from, LivingEntity target, double strength, double up) {
        Vector v = target.getLocation().toVector().subtract(from.toVector());
        v.setY(0);
        if (v.lengthSquared() < 1.0E-4) v = new Vector(0, 0, 0);
        else v.normalize().multiply(strength);
        v.setY(up);
        target.setVelocity(v);
    }

    /** The point the player is looking at, up to range blocks away, stopping just in front of a wall. */
    public static Location targetPoint(Player player, double range) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (hit != null) {
            Vector pos = hit.getHitPosition().subtract(dir.clone().multiply(0.6));
            return pos.toLocation(player.getWorld());
        }
        return eye.clone().add(dir.multiply(range));
    }
}
