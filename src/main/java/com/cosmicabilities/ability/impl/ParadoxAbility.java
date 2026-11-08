package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Two mirrored rings (teal and magenta) spin outward in opposite directions; anyone they cross has their controls reversed. */
public class ParadoxAbility extends CosmicAbility {
    public ParadoxAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.PARADOX); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location center = player.getLocation().add(0, 1, 0);
        World w = center.getWorld();
        double radius = 7;
        int invertTicks = bonus(slot) ? 120 : 80;
        Set<UUID> hitSet = new HashSet<>();
        w.playSound(center, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.4f, 0.7f);
        java.util.List<org.bukkit.entity.BlockDisplay> mirrors = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            org.bukkit.entity.BlockDisplay m = Fx.block(center, i % 2 == 0 ? org.bukkit.Material.CYAN_STAINED_GLASS
                    : org.bukkit.Material.MAGENTA_STAINED_GLASS, new org.joml.Vector3f(1.1f, 1.6f, 0.06f), new org.joml.Quaternionf(), true);
            m.setTeleportDuration(1);
            mirrors.add(m);
        }
        w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.8f);

        every(10, t -> {
            double r = radius * (t + 1) / 10.0;
            double spin = t * 0.5;
            for (int i = 0; i < mirrors.size(); i++) {
                org.bukkit.entity.BlockDisplay m = mirrors.get(i);
                double a = i * Math.PI / 3 + (i % 2 == 0 ? spin : -spin) * 0.4;
                Location mp = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                mp.setYaw(0);
                mp.setPitch(0);
                m.teleport(mp);
                Fx.animate(m, 1, Fx.centred(1.1f, 1.6f, 0.06f, new org.joml.Quaternionf().rotateY((float) (-a + Math.PI / 2))), 1);
                if (t == 10) { Fx.animate(m, 4, Fx.centred(0.01f, 0.01f, 0.01f, new org.joml.Quaternionf()), 2); Fx.removeLater(m, 7); }
            }
            // the two rings travel at slightly different heights and spin opposite ways
            Fx.ring(w, center.clone().add(0, 0.25, 0), r, (int) (r * 7), spin, Fx.dust(0x00f5d4, 1.3f));
            Fx.ring(w, center.clone().add(0, -0.25, 0), r, (int) (r * 7), -spin, Fx.dust(0xf72585, 1.3f));
            if (t % 2 == 0) {
                // "mirror shards": short vertical streaks on the wavefront
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + spin;
                    Location base = center.clone().add(Math.cos(a) * r, -0.8, Math.sin(a) * r);
                    for (double y = 0; y < 1.6; y += 0.25) {
                        w.spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 1, 0, 0, 0, 0,
                                Fx.dust(y < 0.8 ? 0x00f5d4 : 0xf72585, 0.6f));
                    }
                }
            }
            for (LivingEntity e : Targets.enemies(player, center, r + 0.5)) {
                if (!hitSet.add(e.getUniqueId())) continue;
                hit(player, e, dmg(4, slot));
                if (e instanceof Player victim) {
                    status().invert(victim, invertTicks);
                    victim.showTitle(Title.title(Component.text("⇄", TextColor.color(0x00f5d4)),
                            Component.text("Your controls are inverted!", TextColor.color(0xf72585)),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(300))));
                    victim.playSound(victim.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.5f);
                    if (bonus(slot)) {  // Time Fracture: spin them around
                        Location turned = victim.getLocation();
                        turned.setYaw(turned.getYaw() + 180);
                        victim.teleport(turned);
                    }
                } else {
                    if (e instanceof Mob mob) mob.setTarget(null);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, invertTicks, 1, false, true, true));
                    Location turned = e.getLocation();
                    turned.setYaw(turned.getYaw() + 180);
                    e.teleport(turned);
                }
                w.spawnParticle(Particle.REVERSE_PORTAL, e.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
            }
            return true;
        });
        return true;
    }
}
