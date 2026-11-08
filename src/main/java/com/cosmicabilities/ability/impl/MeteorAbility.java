package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.util.Targets;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class MeteorAbility extends CosmicAbility {
    public MeteorAbility(CosmicAbilitiesPlugin plugin) { super(plugin); }

    @Override
    public void cast(Player player, AbilitySlot data) {
        int lvl = data.level();
        double power = scale(1.6, 0.08, lvl) + (data.isPathB() ? 0.5 : 0);
        double impactDamage = scale(4, 0.5, lvl) + (data.isPathA() ? 3 : 0);
        double impactRadius = 3 + (data.evolved() ? 1.5 : 0);
        boolean fireTrail = data.isPathB();
        boolean evolved = data.evolved();

        Location flat = player.getLocation().clone();
        flat.setPitch(0);
        Vector dir = flat.getDirection().normalize();
        player.setVelocity(dir.multiply(power).setY(0.75));
        plugin.engine().addFallImmunity(player.getUniqueId());

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.6f);
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    plugin.engine().removeFallImmunity(player.getUniqueId());
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                world.spawnParticle(Particle.FLAME, loc.clone().add(0, 0.5, 0), 6, 0.2, 0.2, 0.2, 0.02);
                dust(world, loc.clone().add(0, 0.8, 0), Color.fromRGB(6, 214, 160), 1.8f, 4, 0.25);
                if (fireTrail) {
                    for (LivingEntity e : Targets.enemies(player, loc, 1.8)) e.setFireTicks(Math.max(e.getFireTicks(), 60));
                }
                if (t >= 4 && (grounded(player) || t > 80)) {
                    impact(player, loc, impactRadius, impactDamage, evolved);
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> plugin.engine().removeFallImmunity(player.getUniqueId()), 10L);
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static boolean grounded(Player player) {
        Block below = player.getLocation().clone().subtract(0, 0.15, 0).getBlock();
        return below.getType().isSolid() || player.isInWater();
    }

    private void impact(Player player, Location loc, double radius, double damage, boolean evolved) {
        World world = loc.getWorld();
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);
        world.spawnParticle(Particle.EXPLOSION, loc, 4, radius * 0.4, 0.2, radius * 0.4, 0);
        world.spawnParticle(Particle.LAVA, loc, 12, radius * 0.4, 0.1, radius * 0.4, 0);
        ring(world, loc.clone().add(0, 0.2, 0), radius, Particle.FLAME, 30);
        if (evolved) ring(world, loc.clone().add(0, 0.2, 0), radius * 0.6, Particle.SOUL_FIRE_FLAME, 24);
        for (LivingEntity e : Targets.enemies(player, loc, radius)) {
            Targets.damage(player, e, damage);
            Targets.knockAway(loc, e, 0.5, 0.6);
            if (evolved) e.setFireTicks(Math.max(e.getFireTicks(), 100));
        }
    }
}
