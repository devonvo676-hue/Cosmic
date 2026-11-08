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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CryostasisAbility extends CosmicAbility {
    public CryostasisAbility(CosmicAbilitiesPlugin plugin) { super(plugin); }

    @Override
    public void cast(Player player, AbilitySlot data) {
        int lvl = data.level();
        double radius = scale(5, 0.4, lvl);
        int slowTicks = ticks(scale(2, 0.3, lvl));
        int slowAmp = data.isPathA() ? 2 : 1;
        double damage = 2 + (data.isPathB() ? scale(2, 0.3, lvl) : 0);
        boolean frostbite = data.isPathB();
        boolean evolved = data.evolved();

        Location center = player.getLocation().add(0, 0.3, 0);
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        world.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);

        Set<UUID> hit = new HashSet<>();
        int steps = 8;
        new BukkitRunnable() {
            int step = 1;
            @Override
            public void run() {
                if (step > steps || !player.isOnline()) { cancel(); return; }
                double r = radius * step / steps;
                ring(world, center, r, Particle.SNOWFLAKE, (int) (10 + r * 7));
                dustRing(world, center.clone().add(0, 0.3, 0), r, Color.fromRGB(142, 202, 230), 1.3f, (int) (6 + r * 4));
                for (LivingEntity e : Targets.enemies(player, center, r + 0.5)) {
                    if (!hit.add(e.getUniqueId())) continue;
                    Targets.damage(player, e, damage);
                    if (evolved) {
                        // Absolute Zero: near-root, then the normal slow.
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 6, false, true));
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (e.isValid()) e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmp, false, true));
                        }, 31L);
                    } else {
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmp, false, true));
                    }
                    if (frostbite || evolved) {
                        e.setFreezeTicks(Math.min(e.getMaxFreezeTicks() + 100, e.getFreezeTicks() + 160));
                    }
                    world.spawnParticle(Particle.SNOWFLAKE, e.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
