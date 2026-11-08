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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SupernovaAbility extends CosmicAbility {
    public SupernovaAbility(CosmicAbilitiesPlugin plugin) { super(plugin); }

    @Override
    public void cast(Player player, AbilitySlot data) {
        int lvl = data.level();
        double radius = scale(4, 0.25, lvl) + (data.isPathB() ? 1.5 : 0);
        double damage = scale(6, 0.6, lvl) + (data.isPathA() ? 2.5 : 0);
        double kb = 0.9 + (data.isPathB() ? 0.6 : 0);

        blast(player, player.getLocation(), radius, damage, kb, false);

        if (data.evolved()) {
            // Stellar Cataclysm: wider aftershock.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) blast(player, player.getLocation(), radius * 1.35, damage * 0.6, kb * 0.8, true);
            }, 20L);
        }
    }

    private void blast(Player player, Location origin, double radius, double damage, double kb, boolean after) {
        World world = origin.getWorld();
        Location center = origin.clone().add(0, 1, 0);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, after ? 0.7f : 1.3f);
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 2f);
        world.spawnParticle(Particle.END_ROD, center, 40, 0.2, 0.2, 0.2, 0.35);
        world.spawnParticle(Particle.EXPLOSION, center, 3, 0.5, 0.5, 0.5, 0);

        // Expanding shell animation.
        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                if (step > 5) { cancel(); return; }
                double r = radius * step / 5.0;
                ring(world, center, r, Particle.FLAME, (int) (12 + r * 8));
                dustRing(world, center.clone().add(0, 0.4, 0), r * 0.9,
                        after ? Color.fromRGB(255, 80, 40) : Color.fromRGB(255, 190, 60), 1.6f, (int) (8 + r * 6));
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        for (LivingEntity e : Targets.enemies(player, center, radius)) {
            Targets.damage(player, e, damage);
            Targets.knockAway(origin, e, kb, 0.45);
        }
    }
}
