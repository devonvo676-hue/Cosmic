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
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OrbitalAbility extends CosmicAbility {
    public OrbitalAbility(CosmicAbilitiesPlugin plugin) { super(plugin); }

    @Override
    public void cast(Player player, AbilitySlot data) {
        int lvl = data.level();
        int orbs = 3 + (lvl - 1) / 3 + (data.isPathA() ? 2 : 0);
        double orbitRadius = 2.0 + (data.isPathB() ? 1.0 : 0);
        int duration = ticks(scale(4, 0.3, lvl));
        double damage = scale(2, 0.2, lvl);
        boolean evolved = data.evolved();

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.5f);
        Map<UUID, Integer> lastHit = new HashMap<>();

        new BukkitRunnable() {
            int t = 0;
            double angle = 0;
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) { cancel(); return; }
                Location c = player.getLocation().add(0, 1, 0);
                if (t >= duration) {
                    if (evolved) launch(player, c, orbs, orbitRadius, angle, damage * 2);
                    cancel();
                    return;
                }
                angle += 0.22;
                for (int i = 0; i < orbs; i++) {
                    double a = angle + 2 * Math.PI * i / orbs;
                    Location orb = c.clone().add(Math.cos(a) * orbitRadius, Math.sin(angle * 2 + i) * 0.25, Math.sin(a) * orbitRadius);
                    world.spawnParticle(Particle.END_ROD, orb, 1, 0, 0, 0, 0);
                    dust(world, orb, Color.fromRGB(224, 225, 221), 1.5f, 1, 0.05);
                    for (LivingEntity e : Targets.enemies(player, orb, 1.1)) {
                        Integer last = lastHit.get(e.getUniqueId());
                        if (last != null && t - last < 10) continue;
                        lastHit.put(e.getUniqueId(), t);
                        Targets.damage(player, e, damage);
                        world.spawnParticle(Particle.ELECTRIC_SPARK, orb, 6, 0.1, 0.1, 0.1, 0.1);
                    }
                }
                if (t % 15 == 0) world.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4f, 1.8f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Planetary Orbit: fling each orb outward as a short-range projectile. */
    private void launch(Player player, Location c, int orbs, double orbitRadius, double angle, double damage) {
        World world = c.getWorld();
        world.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.2f);
        for (int i = 0; i < orbs; i++) {
            double a = angle + 2 * Math.PI * i / orbs;
            Vector dir = new Vector(Math.cos(a), 0, Math.sin(a));
            Location pos = c.clone().add(dir.clone().multiply(orbitRadius));
            for (double d = 0; d < 8; d += 0.5) {
                pos.add(dir.clone().multiply(0.5));
                if (pos.getBlock().getType().isSolid()) break;
                world.spawnParticle(Particle.END_ROD, pos, 1, 0, 0, 0, 0);
                var hits = Targets.enemies(player, pos, 0.9);
                if (!hits.isEmpty()) {
                    Targets.damage(player, hits.get(0), damage);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, pos, 12, 0.2, 0.2, 0.2, 0.2);
                    break;
                }
            }
        }
    }
}
