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
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Frost nova. A burst of freezing mist leaves you, the ground frosts over in an expanding ring, crystal clusters
 * erupt along the wavefront, and anyone caught is sealed in an ice prison.
 */
public class CryostasisAbility extends CosmicAbility {
    private static final double RADIUS = 6.5;
    private static final int STEPS = 9;

    public CryostasisAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.CRYOSTASIS); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location center = player.getLocation();
        World w = center.getWorld();
        int freezeTicks = bonus(slot) ? 70 : 40;
        double shatter = bonus(slot) ? dmg(9, slot) : 0;
        double damage = dmg(7, slot);
        Set<UUID> hitSet = new HashSet<>();

        w.playSound(center, Sound.BLOCK_POWDER_SNOW_BREAK, 1.4f, 0.6f);
        w.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.7f);
        w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.5f);
        // cold exhale: a white puff that rolls out low along the ground
        Fx.burst(w, center.clone().add(0, 1, 0), Particle.SNOWFLAKE, 40, 0.25);
        w.spawnParticle(Particle.CLOUD, center.clone().add(0, 0.3, 0), 30, 1.2, 0.1, 1.2, 0.04);

        every(STEPS, t -> {
            double r = RADIUS * (t + 1) / STEPS;
            Location ring = center.clone().add(0, 0.12, 0);
            // the frost line: bright white leading edge with pale blue behind it
            Fx.ring(w, ring, r, (int) (r * 10), t * 0.3, Fx.dust(0xffffff, 1.4f));
            Fx.ring(w, ring, Math.max(0.2, r - 0.45), (int) (r * 6), 0.15, Fx.dust(0x90e0ef, 1.1f));
            Fx.ring(w, ring.clone().add(0, 0.3, 0), r, (int) (r * 4), t, Particle.SNOWFLAKE);
            frostGround(center, r, t);
            if (t % 2 == 0) clusters(center, r, 5 + t);
            if (t % 3 == 0) w.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.6f - t * 0.08f);
            for (LivingEntity e : Targets.enemies(player, center, r + 0.6)) {
                if (!hitSet.add(e.getUniqueId())) continue;
                hit(player, e, damage);
                status().freeze(e, freezeTicks, player, shatter);
            }
            return true;
        });
        return true;
    }

    /** Frost patches spreading across real ground (visual only - blocks are untouched). */
    private void frostGround(Location center, double r, int t) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int count = (int) (r * 2.2);
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble(Math.PI * 2);
            Block top = surface(center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            if (top == null) continue;
            Fx.decal(top.getLocation().add(0.5, 1.005, 0.5), i % 3 == 0 ? Material.PACKED_ICE : Material.SNOW_BLOCK,
                    (float) rnd.nextDouble(0.6, 1.1), (float) rnd.nextDouble(6.28), 50 + t * 3);
        }
    }

    /** Clusters of 3 crystals erupting from the ground, angled outward, then cracking apart. */
    private void clusters(Location center, double r, int count) {
        World w = center.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + rnd.nextDouble(-0.2, 0.2);
            Block top = surface(center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            if (top == null) continue;
            Location base = top.getLocation().add(0.5, 1.0, 0.5);
            for (int k = 0; k < 3; k++) {
                float h = (float) rnd.nextDouble(0.6, 1.5) * (k == 0 ? 1.3f : 0.8f);
                float wd = (float) rnd.nextDouble(0.16, 0.3);
                Quaternionf tilt = new Quaternionf().rotateY((float) (-a + (k - 1) * 0.6)).rotateZ((float) -rnd.nextDouble(0.15, 0.55));
                BlockDisplay spike = Fx.block(base, k == 0 ? Material.BLUE_ICE : Material.PACKED_ICE,
                        new Vector3f(wd, 0.01f, wd), tilt, false);
                Fx.animate(spike, 2 + k, new Transformation(new Vector3f(-wd / 2, 0, -wd / 2), tilt,
                        new Vector3f(wd, h, wd), new Quaternionf()), 1);
                int life = 30 + rnd.nextInt(12);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!spike.isValid()) return;
                    w.spawnParticle(Particle.BLOCK, base.clone().add(0, h / 2, 0), 6, 0.1, h / 3, 0.1, 0, Material.PACKED_ICE.createBlockData());
                    Fx.animate(spike, 4, new Transformation(new Vector3f(0, h * 0.2f, 0), tilt,
                            new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()), 1);
                    Fx.removeLater(spike, 6);
                }, life);
            }
        }
    }

    /** The solid block at the surface below a point (searches 3 up / 3 down), or null. */
    private static Block surface(Location at) {
        Block b = at.getBlock();
        for (int dy = 2; dy >= -3; dy--) {
            Block c = b.getRelative(0, dy, 0);
            if (c.getType().isSolid() && c.getRelative(0, 1, 0).isPassable()) return c;
        }
        return null;
    }
}
