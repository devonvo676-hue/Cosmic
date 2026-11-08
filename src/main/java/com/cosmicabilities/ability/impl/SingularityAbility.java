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
import org.bukkit.util.Vector;

public class SingularityAbility extends CosmicAbility {
    public SingularityAbility(CosmicAbilitiesPlugin plugin) { super(plugin); }

    @Override
    public void cast(Player player, AbilitySlot data) {
        int lvl = data.level();
        Location center = Targets.targetPoint(player, 12);
        World world = center.getWorld();
        double radius = scale(5, 0.3, lvl) + (data.isPathB() ? 1.5 : 0);
        int duration = ticks(scale(3, 0.2, lvl) + (data.isPathB() ? 1.5 : 0));
        double dps = scale(1.0, 0.15, lvl) + (data.isPathA() ? 1.5 : 0);
        double pull = 0.16 + (data.isPathA() ? 0.06 : 0);
        boolean evolved = data.evolved();

        world.playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.8f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= duration || !player.isOnline()) {
                    if (evolved && player.isOnline()) collapse(player, center, radius, lvl);
                    cancel();
                    return;
                }
                // Visual: dark core + swirling portal particles.
                dust(world, center, Color.fromRGB(20, 0, 40), 2.2f, 6, 0.25);
                double spin = t * 0.35;
                for (int i = 0; i < 4; i++) {
                    double a = spin + i * Math.PI / 2;
                    double r = radius * (1 - (t % 20) / 20.0);
                    Location p = center.clone().add(Math.cos(a) * r, Math.sin(spin) * 0.4, Math.sin(a) * r);
                    world.spawnParticle(Particle.REVERSE_PORTAL, p, 2, 0.05, 0.05, 0.05, 0);
                }
                world.spawnParticle(Particle.PORTAL, center, 15, radius * 0.4, radius * 0.4, radius * 0.4, 0.6);

                for (LivingEntity e : Targets.enemies(player, center, radius)) {
                    Vector toCenter = center.toVector().subtract(e.getLocation().toVector());
                    if (toCenter.lengthSquared() > 0.5) {
                        Vector v = toCenter.normalize().multiply(pull);
                        v.setY(Math.max(e.getVelocity().getY(), v.getY() * 0.5));
                        e.setVelocity(v);
                    }
                    if (t % 10 == 0) {
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, true, false));
                        Targets.damage(player, e, dps / 2.0);
                    }
                }
                if (t % 20 == 0) world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 0.5f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Event Horizon: collapse into a heavy implosion. */
    private void collapse(Player player, Location center, double radius, int lvl) {
        World world = center.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 120, 0.3, 0.3, 0.3, 1.2);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);
        for (LivingEntity e : Targets.enemies(player, center, radius)) {
            Targets.damage(player, e, scale(6, 0.4, lvl));
            Targets.knockAway(center, e, 0.9, 0.5);
        }
    }
}
