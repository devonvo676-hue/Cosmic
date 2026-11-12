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

/** Cosmic Destroyer drop: marks a big circle; after 2 seconds a gigantic celestial object crashes into it (7 hearts). */
public class ApocalypseAbility extends CosmicAbility {
    public ApocalypseAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.APOCALYPSE); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location c = Targets.targetPoint(player, plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 32));
        World w = c.getWorld();
        c.setY(w.getHighestBlockYAt(c) + 1);
        c.setYaw(0); c.setPitch(0);
        double radius = plugin.getConfig().getDouble("ability-settings." + type.id() + ".radius", 7);
        Location sky = c.clone().add(0, 28, 0);
        // the celestial object: a glowing core inside a cracked rocky shell, built from a few rotated blocks
        List<BlockDisplay> rock = new ArrayList<>();
        Material[] shell = {Material.MAGMA_BLOCK, Material.BLACKSTONE, Material.CRYING_OBSIDIAN, Material.MAGMA_BLOCK};
        for (int i = 0; i < 4; i++) {
            rock.add(Fx.block(sky, shell[i], new Vector3f(0.2f, 0.2f, 0.2f), new Quaternionf().rotateXYZ(i * 0.7f, i * 0.4f, i * 0.9f), true));
        }
        rock.add(Fx.block(sky, Material.SEA_LANTERN, new Vector3f(0.2f, 0.2f, 0.2f), new Quaternionf(), true));
        for (BlockDisplay b : rock) { b.setTeleportDuration(1); b.setGlowing(true); b.setGlowColorOverride(org.bukkit.Color.fromRGB(type.rgb())); }
        w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
        for (org.bukkit.entity.Player p : w.getPlayers()) if (p.getLocation().distanceSquared(c) < 60 * 60) p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.6f);
        int charge = 40, fall = 8;
        every(charge + fall + 1, t -> {
            Fx.ring(w, c.clone().add(0, 0.15, 0), radius, (int) (radius * 9), t * 0.05, Fx.dust(0xff2a2a, 1.6f));
            if (t % 4 == 0) Fx.ring(w, c.clone().add(0, 0.15, 0), radius * (t % 20) / 20.0, 30, 0, Fx.dust(type.rgb(), 1.2f));
            if (t < charge) {
                float s = 0.4f + 5.5f * (t / (float) charge);
                for (int i = 0; i < rock.size(); i++) {
                    float k = i == rock.size() - 1 ? 0.75f : 1f;
                    Fx.animate(rock.get(i), 2, Fx.centred(s * k, s * k, s * k, new Quaternionf().rotateXYZ(i * 0.7f + t * 0.05f, i * 0.4f + t * 0.08f, i * 0.9f)), 0);
                }
                for (int i = 0; i < 4; i++) {   // energy gathering in from all around
                    double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                    Location from = sky.clone().add(Math.cos(a) * 9, ThreadLocalRandom.current().nextDouble(-5, 5), Math.sin(a) * 9);
                    Vector v = sky.toVector().subtract(from.toVector()).multiply(0.12);
                    w.spawnParticle(Particle.END_ROD, from, 0, v.getX(), v.getY(), v.getZ(), 1);
                }
                if (t % 10 == 0) w.playSound(sky, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2f, 0.5f + t * 0.02f);
                return true;
            }
            double prog = (t - charge) / (double) fall;
            Location at = sky.clone().subtract(0, 28 * prog, 0);
            for (BlockDisplay b : rock) b.teleport(at);
            w.spawnParticle(Particle.FLAME, at, 20, 2, 2, 2, 0.05);
            w.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 3, 0), 10, 1.5, 1.5, 1.5, 0.02);
            if (t >= charge + fall) {
                rock.forEach(Entity::remove);
                w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.5f);
                w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.6f);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 5, radius / 2, 0.5, radius / 2, 0);
                w.spawnParticle(Particle.FLAME, c, 200, radius / 2, 1, radius / 2, 0.2);
                w.spawnParticle(Particle.END_ROD, c, 80, 1, 1, 1, 0.6);
                for (int i = 0; i < 10; i++) Fx.decal(c.clone().add(ThreadLocalRandom.current().nextDouble(-radius, radius), 0.02,
                        ThreadLocalRandom.current().nextDouble(-radius, radius)), Material.MAGMA_BLOCK, 1.5f, (float) Math.random() * 6, 100);
                for (LivingEntity e : Targets.enemies(player, c, radius)) {
                    hearts(player, e, 7);
                    Targets.knockAway(c, e, 1.2, 0.8);
                }
                if (bonus(slot)) every(80, k -> {
                    if (k % 4 == 0) w.spawnParticle(Particle.FLAME, c, 12, radius / 2, 0.1, radius / 2, 0.01);
                    if (k % 20 == 0) for (LivingEntity e : Targets.enemies(player, c, radius * 0.8)) e.setFireTicks(Math.max(e.getFireTicks(), 40));
                    return true;
                });
                return false;
            }
            return true;
        });
        return true;
    }
}
