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

/** Void Emperor drop: a dimensional crack races along the ground, then erupts into purple cosmic energy (6 hearts). */
public class RiftbreakerAbility extends CosmicAbility {
    public RiftbreakerAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.RIFTBREAKER); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        Vector dir = Targets.flatDirection(player);
        double length = plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 20);
        Location origin = player.getLocation().add(dir.clone().multiply(1.5));
        Set<UUID> fractured = new HashSet<>();
        List<Location> line = new ArrayList<>();
        w.playSound(origin, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 0.6f);
        w.playSound(origin, Sound.BLOCK_DEEPSLATE_BREAK, 2f, 0.5f);
        int steps = 24;
        every(steps + 6, t -> {
            if (t < steps) {
                for (int k = 0; k < 2; k++) {
                    double d = length * (t * 2 + k) / (steps * 2.0);
                    Location c = origin.clone().add(dir.clone().multiply(d));
                    c.add(dir.getZ() * Math.sin(d * 1.3) * 0.35, 0, -dir.getX() * Math.sin(d * 1.3) * 0.35);  // jagged
                    c.setY(w.getHighestBlockYAt(c) + 1.02);
                    line.add(c);
                    float yaw = (float) Math.atan2(-dir.getX(), dir.getZ()) + (float) (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.7f;
                    Fx.decal(c, Material.BLACK_CONCRETE, 1.1f, yaw, 60);
                    Fx.decal(c.clone().add(0, 0.01, 0), Material.PURPLE_CONCRETE, 0.45f, yaw, 60);
                    w.spawnParticle(Particle.BLOCK, c, 8, 0.4, 0.1, 0.4, 0.1, c.clone().subtract(0, 1, 0).getBlock().getBlockData());
                    w.spawnParticle(Particle.REVERSE_PORTAL, c, 6, 0.2, 0.1, 0.2, 0.05);
                    for (LivingEntity e : Targets.enemies(player, c, 1.6)) {
                        if (!fractured.add(e.getUniqueId())) continue;
                        hearts(player, e, 3);
                        e.setVelocity(new Vector(0, 0.35, 0));
                    }
                }
                if (t % 3 == 0) w.playSound(line.get(line.size() - 1), Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.6f);
                return true;
            }
            if (t == steps + 5) {
                // the whole crack erupts
                w.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 0.7f);
                w.playSound(origin, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.4f);
                Set<UUID> erupted = new HashSet<>();
                for (int i = 0; i < line.size(); i += 2) {
                    Location c = line.get(i);
                    w.spawnParticle(Particle.DUST, c.clone().add(0, 1.2, 0), 10, 0.25, 1.2, 0.25, 0, Fx.dust(type.rgb(), 2f));
                    w.spawnParticle(Particle.END_ROD, c, 4, 0.1, 0.1, 0.1, 0.25);
                    BlockDisplay pillar = Fx.block(c, Material.PURPLE_STAINED_GLASS, new Vector3f(0.5f, 0.01f, 0.5f), new Quaternionf(), true);
                    Fx.animate(pillar, 4, Fx.centred(0.6f, 3.5f + ThreadLocalRandom.current().nextFloat() * 2, 0.6f, new Quaternionf().rotateY(i)), 1);
                    Fx.animate(pillar, 8, Fx.centred(0.01f, 0.01f, 0.01f, new Quaternionf()), 12);
                    Fx.removeLater(pillar, 22);
                    for (LivingEntity e : Targets.enemies(player, c, 1.8)) {
                        if (!erupted.add(e.getUniqueId())) continue;
                        hearts(player, e, 3);
                        if (bonus(slot)) e.setVelocity(new Vector(0, 0.9, 0));
                    }
                }
                return false;
            }
            return true;
        });
        return true;
    }
}
