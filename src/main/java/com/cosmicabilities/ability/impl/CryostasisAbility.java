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
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Frost nova: rings of ice spikes burst out of the ground and freeze enemies solid inside ice blocks. */
public class CryostasisAbility extends CosmicAbility {
    public CryostasisAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.CRYOSTASIS); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location center = player.getLocation();
        World w = center.getWorld();
        double radius = 6.5;
        int freezeTicks = bonus(slot) ? 70 : 40;
        double shatter = bonus(slot) ? dmg(6, slot) : 0;
        double damage = dmg(4, slot);
        Set<UUID> hitSet = new HashSet<>();

        w.playSound(center, Sound.BLOCK_POWDER_SNOW_BREAK, 1.4f, 0.6f);
        w.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.7f);
        w.playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);

        every(8, t -> {
            double r = radius * (t + 1) / 8.0;
            Location ring = center.clone().add(0, 0.15, 0);
            Fx.ring(w, ring, r, (int) (r * 9), t * 0.3, Particle.SNOWFLAKE);
            Fx.ring(w, ring, r - 0.3, (int) (r * 5), 0, Fx.dust(0xcaf0f8, 1.3f));
            if (t % 2 == 1) spikes(center, r, 7 + t);
            for (LivingEntity e : Targets.enemies(player, center, r + 0.6)) {
                if (!hitSet.add(e.getUniqueId())) continue;
                hit(player, e, damage);
                status().freeze(e, freezeTicks, player, shatter);
            }
            return true;
        });
        return true;
    }

    /** A ring of jagged ice shards that grow out of the ground, then crack apart. */
    private void spikes(Location center, double r, int count) {
        World w = center.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + rnd.nextDouble(-0.15, 0.15);
            Location at = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Block ground = at.getBlock();
            if (!ground.isPassable()) at.add(0, 1, 0);
            else if (ground.getRelative(0, -1, 0).isPassable()) continue;
            float h = (float) rnd.nextDouble(0.8, 1.6);
            float wdt = (float) rnd.nextDouble(0.18, 0.32);
            Quaternionf tilt = new Quaternionf().rotateY((float) -a).rotateZ((float) rnd.nextDouble(-0.45, -0.15));
            BlockDisplay spike = Fx.block(at.clone().add(0, 0.05, 0), rnd.nextBoolean() ? Material.PACKED_ICE : Material.BLUE_ICE,
                    new Vector3f(wdt, 0.01f, wdt), tilt, false);
            // grow upward from the base: translate so the bottom stays planted
            Fx.animate(spike, 3, new org.bukkit.util.Transformation(new Vector3f(-wdt / 2, 0, -wdt / 2), tilt,
                    new Vector3f(wdt, h, wdt), new Quaternionf()), 1);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!spike.isValid()) return;
                w.spawnParticle(Particle.BLOCK, at.clone().add(0, h / 2, 0), 8, 0.1, h / 3, 0.1, 0, Material.PACKED_ICE.createBlockData());
                spike.remove();
            }, 26 + rnd.nextInt(10));
        }
    }
}
