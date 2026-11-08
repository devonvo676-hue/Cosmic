package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.status.StatusManager;
import com.cosmicabilities.util.Targets;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Reflect shield: a sun crown over your head, three orbiting flares, and beams back at whoever hits you. */
public class SolarAbility extends CosmicAbility {
    public SolarAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.SOLAR); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        int duration = 100;
        boolean evolved = bonus(slot);
        double reflect = evolved ? 1.0 : 0.5;
        status().setShield(player.getUniqueId(),
                new StatusManager.Shield(0.3, reflect, System.currentTimeMillis() + duration * 50L, evolved));
        if (evolved) player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0, false, true, true));
        World w = player.getWorld();
        org.bukkit.entity.ItemDisplay halo = Fx.model(player.getLocation().add(0, 2.6, 0), "planet_sun", 0.05f);
        halo.setTeleportDuration(1);
        Fx.animate(halo, 5, Fx.spin(0.42f, new org.joml.Quaternionf()), 1);
        w.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.7f);
        w.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.8f);

        every(duration, t -> {
            if (!player.isOnline() || player.isDead()) { status().clearShield(player.getUniqueId()); halo.remove(); return false; }
            Location base = player.getLocation();
            halo.teleport(base.clone().add(0, 2.6, 0));
            if (t % 3 == 0) Fx.animate(halo, 3, Fx.spin(0.42f, new org.joml.Quaternionf().rotateY(t * 0.12f).rotateX(0.3f)), 1);
            // crown: a small ring of points above the head that slowly turns
            Location crown = base.clone().add(0, 2.35, 0);
            if (t % 2 == 0) {
                for (int i = 0; i < 8; i++) {
                    double a = t * 0.06 + i * Math.PI / 4;
                    double h = i % 2 == 0 ? 0.18 : 0;
                    w.spawnParticle(Particle.DUST, crown.getX() + Math.cos(a) * 0.42, crown.getY() + h,
                            crown.getZ() + Math.sin(a) * 0.42, 1, 0, 0, 0, 0, Fx.dust(i % 2 == 0 ? 0xffd166 : 0xff8c42, 0.8f));
                }
            }
            // three flares orbiting the body on tilted paths
            for (int k = 0; k < 3; k++) {
                double a = t * 0.22 + k * 2.094;
                double y = 1.0 + Math.sin(a * 1.5 + k) * 0.6;
                Location p = base.clone().add(Math.cos(a) * 1.05, y, Math.sin(a) * 1.05);
                w.spawnParticle(Particle.FLAME, p, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0, Fx.dust(0xffe8a3, 1.0f));
            }
            if (t >= duration) {
                status().clearShield(player.getUniqueId());
                halo.remove();
                burst(player, slot);
                return false;
            }
            return true;
        });
        return true;
    }

    private void burst(Player player, AbilitySlot slot) {
        Location c = player.getLocation().add(0, 1, 0);
        World w = c.getWorld();
        double r = bonus(slot) ? 4.5 : 3.5;
        w.playSound(c, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.9f);
        Fx.burst(w, c, Particle.FLAME, 40, 0.3);
        Fx.ring(w, c, r, 40, 0, Fx.dust(0xffd166, 1.4f));
        Fx.ring(w, c.clone().subtract(0, 0.6, 0), r * 0.7, 30, 0.2, Particle.FLAME);
        for (LivingEntity e : Targets.enemies(player, c, r)) {
            hit(player, e, dmg(5, slot));
            Targets.knockAway(c, e, 0.6, 0.35);
            if (bonus(slot)) e.setFireTicks(Math.max(e.getFireTicks(), 80));
        }
    }
}
