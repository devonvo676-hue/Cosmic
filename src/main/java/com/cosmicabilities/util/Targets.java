package com.cosmicabilities.util;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.RankTags;
import com.cosmicabilities.fx.Fx;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.scoreboard.Team;
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

    /** Same (non-rank) scoreboard team = ally. */
    public static boolean isAlly(Player caster, Player other) {
        if (caster.equals(other)) return true;
        if (trusts(caster, other)) return true;
        Team team = caster.getScoreboard().getEntryTeam(caster.getName());
        return team != null && !RankTags.isTagTeam(team) && team.hasEntry(other.getName());
    }

    public static boolean isEnemy(Player caster, Entity e) {
        if (!(e instanceof LivingEntity living) || e.equals(caster) || living.isDead() || !living.isValid()) return false;
        if (e instanceof ArmorStand) return false;
        if (e instanceof Tameable tame && tame.isTamed() && caster.getUniqueId().equals(tame.getOwnerUniqueId())) return false;
        if (e instanceof Player p) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return false;
            if (trusts(caster, p)) return false;
            if (isAlly(caster, p)) return false;
            return plugin.getConfig().getBoolean("abilities.hit-players", true);
        }
        if (e instanceof Enemy) return true;
        return plugin.getConfig().getBoolean("abilities.hit-passive-mobs", false);
    }

    /** /trust: the caster's abilities never affect players they trust. */
    public static boolean trusts(Player caster, Player other) {
        com.cosmicabilities.data.PlayerData d = plugin.data().get(caster.getUniqueId());
        return d != null && d.trusts(other.getUniqueId());
    }

    public static List<LivingEntity> enemies(Player caster, Location center, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return list;
        double r2 = radius * radius;
        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!isEnemy(caster, e)) continue;
            LivingEntity le = (LivingEntity) e;
            Location mid = le.getLocation().add(0, le.getHeight() / 2, 0);
            if (mid.distanceSquared(center) <= r2 || le.getLocation().distanceSquared(center) <= r2) list.add(le);
        }
        return list;
    }

    /** Caster plus same-team players within radius. */
    public static List<Player> allies(Player caster, Location center, double radius) {
        List<Player> list = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return list;
        double r2 = radius * radius;
        for (Player p : world.getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (!isAlly(caster, p)) continue;
            if (p.getLocation().distanceSquared(center) <= r2) list.add(p);
        }
        return list;
    }

    /**
     * Deals ability damage attributed to the caster. Fires normal damage events (PvP / region plugins still apply),
     * shows a floating damage number and hitmarker, and tags both players as in combat.
     * Returns the damage actually dealt after armor.
     */
    public static double damage(Player caster, LivingEntity target, double amount, int rgb) {
        if (amount <= 0 || target.isDead()) return 0;
        double mult = plugin.getConfig().getDouble("abilities.damage-multiplier", 1.0);
        double before = target.getHealth() + target.getAbsorptionAmount();
        target.setNoDamageTicks(0);
        if (plugin.getConfig().getBoolean("abilities.armor-piercing", true)) {
            // magic damage ignores armor points (diamond/netherite plates) but Protection still reduces it,
            // so abilities stay meaningful against a full Prot IV kit without one-shotting anyone
            target.damage(amount * mult, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MAGIC)
                    .withCausingEntity(caster).withDirectEntity(caster).build());
        } else {
            target.damage(amount * mult, caster);
        }
        double after = target.isDead() ? 0 : target.getHealth() + target.getAbsorptionAmount();
        double dealt = Math.max(0, before - after);
        if (dealt > 0.05) {
            Fx.damageNumber(target, dealt, rgb);
            Fx.hitmarker(caster, target);
            plugin.combat().tag(caster);
            if (target instanceof Player victim) plugin.combat().tag(victim);
        }
        return dealt;
    }

    public static void heal(LivingEntity target, double amount) {
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max == null ? 20 : max.getValue();
        target.setHealth(Math.min(maxHp, target.getHealth() + amount));
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

    /** Horizontal unit vector the player is facing. */
    public static Vector flatDirection(Player player) {
        Vector v = player.getLocation().getDirection().setY(0);
        if (v.lengthSquared() < 1.0E-4) return new Vector(0, 0, 1);
        return v.normalize();
    }
}
