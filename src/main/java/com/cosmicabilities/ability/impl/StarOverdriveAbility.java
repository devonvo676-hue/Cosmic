package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Cosmic Destroyer drop: a star core overloads above you and releases three shockwaves. */
public class StarOverdriveAbility extends CosmicAbility {
    public StarOverdriveAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.STAR_OVERDRIVE); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        boolean big = bonus(slot);
        Location core = player.getLocation().add(0, 3.2, 0);
        BlockDisplay star = Fx.block(core, Material.SEA_LANTERN, new Vector3f(0.2f, 0.2f, 0.2f), new Quaternionf(), true);
        star.setGlowing(true);
        Fx.animate(star, 20, Fx.centred(1.2f, 1.2f, 1.2f, new Quaternionf().rotateXYZ(0.8f, 0.8f, 0.8f)), 1);
        w.playSound(core, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.6f);
        for (int wave = 0; wave < 3; wave++) {
            final int n = wave;
            later(20 + wave * 14L, () -> {
                if (!player.isOnline()) return;
                Location c = player.getLocation().add(0, 0.2, 0);
                double radius = (big ? 6 : 5) + n * 1.5;
                w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.2f + n * 0.2f);
                Fx.safe(w, Particle.FLASH, core, 1, 0, 0, 0, 0, org.bukkit.Color.fromRGB(type.rgb()));
                every(8, t -> {
                    double r = radius * (t + 1) / 9.0;
                    Fx.ring(w, c, r, (int) (12 + r * 6), t, Fx.dust(type.rgb(), 1.6f));
                    Fx.ring(w, c.clone().add(0, 0.5, 0), r, (int) (8 + r * 3), t + 0.5, Particle.END_ROD);
                    return true;
                });
                for (LivingEntity e : Targets.enemies(player, c, radius)) {
                    hit(player, e, n == 2 ? (big ? 6 : 5) : 3);
                    Targets.knockAway(c, e, 0.6, 0.8 + n * 0.1);
                }
                if (n == 2) {
                    Fx.animate(star, 5, Fx.centred(0.01f, 0.01f, 0.01f, new Quaternionf()), 0);
                    Fx.removeLater(star, 6);
                }
            });
        }
        every(60, t -> {
            if (!star.isValid() || !player.isOnline()) return false;
            Location p = player.getLocation().add(0, 3.2, 0);
            star.teleport(p);
            if (t % 2 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, p, 4, 0.5, 0.5, 0.5, 0.05);
            return true;
        });
        return true;
    }
}
