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
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Nebula Serpent drop: a spectral cosmic serpent winds across the battlefield, then coils and bursts (5 hearts). */
public class SerpentsWrathAbility extends CosmicAbility {
    public SerpentsWrathAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.SERPENTS_WRATH); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        Vector dir = Targets.flatDirection(player);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        double length = plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 24);
        Location origin = player.getLocation().add(0, 0.8, 0);
        int segs = 16;
        List<BlockDisplay> body = new ArrayList<>();
        for (int i = 0; i < segs; i++) {
            float s = i == 0 ? 1.5f : 1.3f - i * 0.055f;
            Material m = i == 0 ? Material.MAGENTA_GLAZED_TERRACOTTA : (i % 2 == 0 ? Material.MAGENTA_STAINED_GLASS : Material.PINK_STAINED_GLASS);
            BlockDisplay b = Fx.block(origin, m, new Vector3f(s, s, s), new Quaternionf(), true);
            b.setTeleportDuration(1);
            b.setGlowing(true);
            b.setGlowColorOverride(org.bukkit.Color.fromRGB(type.rgb()));
            body.add(b);
        }
        List<Location> path = new ArrayList<>();
        Set<UUID> touched = new HashSet<>();
        int travel = 40, coil = 20;
        w.playSound(origin, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.5f, 1.4f);
        every(travel + coil + 10, t -> {
            Location head;
            if (t <= travel) {
                double prog = t / (double) travel;
                double d = length * prog;
                head = origin.clone().add(dir.clone().multiply(d)).add(side.clone().multiply(Math.sin(d * 0.55) * 2.6));
                head.setY(w.getHighestBlockYAt(head) + 1.1 + Math.sin(t * 0.4) * 0.3);
            } else {
                // coil: spiral around the end point
                Location end = path.get(0);
                double a = (t - travel) * 0.6;
                double r = 2.2 - (t - travel) * 0.06;
                head = end.clone().add(Math.cos(a) * Math.max(0.4, r), (t - travel) * 0.05, Math.sin(a) * Math.max(0.4, r));
                if (t == travel + 1) path.add(0, end.clone());
            }
            if (t <= travel) path.add(0, head.clone());
            else path.add(1, head.clone());
            for (int i = 0; i < body.size(); i++) {
                Location p = path.get(Math.min(path.size() - 1, i * 2));
                body.get(i).teleport(i == 0 ? head : p);
            }
            if (t % 2 == 0) w.spawnParticle(Particle.DUST, head, 6, 0.5, 0.4, 0.5, 0, Fx.dust(type.rgb(), 1.8f));
            if (t % 3 == 0) w.spawnParticle(Particle.END_ROD, head, 2, 0.3, 0.3, 0.3, 0.02);
            if (t % 8 == 0) w.playSound(head, Sound.ENTITY_PHANTOM_FLAP, 1f, 0.6f);
            if (t <= travel) {
                for (int i = 0; i < body.size(); i += 3) {
                    for (LivingEntity e : Targets.enemies(player, body.get(i).getLocation(), 1.6)) {
                        if (!touched.add(e.getUniqueId())) continue;
                        hearts(player, e, 3);
                        e.setVelocity(side.clone().multiply(0.6).setY(0.4));
                    }
                }
            }
            if (t == travel + coil) {
                Location c = path.get(0);
                w.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2f, 0.7f);
                w.playSound(c, Sound.ENTITY_EVOKER_CAST_SPELL, 1.5f, 0.8f);
                w.spawnParticle(Particle.FIREWORK, c, 80, 0.3, 0.3, 0.3, 0.35);
                for (int r = 1; r <= 4; r++) Fx.ring(w, c, r, 14 + r * 6, r, Fx.dust(type.rgb(), 2f));
                for (LivingEntity e : Targets.enemies(player, c, 4.5)) {
                    hearts(player, e, 2);
                    Targets.knockAway(c, e, 0.9, 0.6);
                    if (bonus(slot)) e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true, true));
                }
                for (BlockDisplay b : body) {
                    Fx.animate(b, 6, Fx.centred(0.01f, 0.01f, 0.01f, new Quaternionf()), 0);
                    Fx.removeLater(b, 7);
                }
                return false;
            }
            return true;
        });
        return true;
    }
}
