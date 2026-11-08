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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PulsarAbility extends CosmicAbility {
    private static final double RANGE = 40;

    public PulsarAbility(CosmicAbilitiesPlugin plugin) { super(plugin); }

    @Override
    public void cast(Player player, AbilitySlot data) {
        int lvl = data.level();
        double speed = scale(1.2, 0.1, lvl);
        double damage = scale(7, 0.7, lvl);
        int pierce = data.isPathA() ? 3 : 1;
        boolean burst = data.isPathB();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.8f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 2f);

        Vector dir = player.getEyeLocation().getDirection().normalize();
        if (data.evolved()) {
            // Quasar: three beams in a spread.
            for (double yaw : new double[]{-9, 0, 9}) {
                fire(player, rotateY(dir.clone(), Math.toRadians(yaw)), speed, damage * 0.75, pierce, burst);
            }
        } else {
            fire(player, dir, speed, damage, pierce, burst);
        }
    }

    private static Vector rotateY(Vector v, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z).normalize();
    }

    private void fire(Player player, Vector dir, double speed, double damage, int pierce, boolean burst) {
        World world = player.getWorld();
        Location pos = player.getEyeLocation().clone().add(dir.clone().multiply(0.6));
        Set<UUID> hit = new HashSet<>();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(76, 201, 240), 1.3f);

        new BukkitRunnable() {
            double travelled = 0;
            int hits = 0;
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                double moved = 0;
                while (moved < speed) {
                    double inc = Math.min(0.4, speed - moved);
                    pos.add(dir.clone().multiply(inc));
                    moved += inc;
                    travelled += inc;
                    world.spawnParticle(Particle.DUST, pos, 1, 0, 0, 0, 0, dust);
                    if (pos.getBlock().getType().isSolid() || travelled >= RANGE) {
                        end(pos);
                        cancel();
                        return;
                    }
                    for (Entity e : world.getNearbyEntities(pos, 0.7, 0.9, 0.7)) {
                        if (!Targets.isEnemy(player, e) || !hit.add(e.getUniqueId())) continue;
                        LivingEntity target = (LivingEntity) e;
                        Targets.damage(player, target, damage);
                        world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.2);
                        if (burst) {
                            world.spawnParticle(Particle.EXPLOSION, pos, 1);
                            world.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
                            for (LivingEntity near : Targets.enemies(player, pos, 2.5)) {
                                if (!near.equals(target)) Targets.damage(player, near, damage * 0.5);
                            }
                        }
                        hits++;
                        if (hits >= pierce) {
                            cancel();
                            return;
                        }
                    }
                }
                world.spawnParticle(Particle.END_ROD, pos, 1, 0, 0, 0, 0);
            }

            private void end(Location at) {
                world.spawnParticle(Particle.END_ROD, at, 8, 0.1, 0.1, 0.1, 0.05);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
