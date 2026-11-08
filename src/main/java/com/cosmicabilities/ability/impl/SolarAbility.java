package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityEngine;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class SolarAbility extends CosmicAbility {
    public SolarAbility(CosmicAbilitiesPlugin plugin) { super(plugin); }

    @Override
    public void cast(Player player, AbilitySlot data) {
        int lvl = data.level();
        int duration = ticks(scale(3, 0.2, lvl));
        double reduction = Math.min(0.7, scale(0.25, 0.03, lvl) + (data.isPathB() ? 0.12 : 0));
        double burstRadius = 3 + (data.isPathA() ? 1.5 : 0);
        double burstDamage = scale(3, 0.3, lvl) + (data.isPathA() ? 3 : 0);
        boolean evolved = data.evolved();

        plugin.engine().setShield(player.getUniqueId(),
                new AbilityEngine.Shield(reduction, System.currentTimeMillis() + duration * 50L, evolved));
        if (evolved) player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 1, true, true));

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    plugin.engine().clearShield(player.getUniqueId());
                    cancel();
                    return;
                }
                Location c = player.getLocation().add(0, 1, 0);
                if (t % 2 == 0) {
                    double a = t * 0.3;
                    for (int i = 0; i < 3; i++) {
                        double ang = a + i * 2 * Math.PI / 3;
                        Location p = c.clone().add(Math.cos(ang) * 1.1, Math.sin(t * 0.2) * 0.6, Math.sin(ang) * 1.1);
                        dust(world, p, Color.fromRGB(255, 209, 102), 1.4f, 1, 0);
                        world.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
                    }
                }
                if (t >= duration) {
                    plugin.engine().clearShield(player.getUniqueId());
                    world.playSound(c, Sound.ENTITY_BLAZE_SHOOT, 1f, 1.4f);
                    world.spawnParticle(Particle.END_ROD, c, 30, 0.2, 0.2, 0.2, 0.25);
                    ring(world, c, burstRadius, Particle.FLAME, 36);
                    for (LivingEntity e : Targets.enemies(player, c, burstRadius)) {
                        Targets.damage(player, e, burstDamage);
                        Targets.knockAway(c, e, 0.5, 0.3);
                        if (evolved) e.setFireTicks(Math.max(e.getFireTicks(), 60));
                    }
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
