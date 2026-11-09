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
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Frost nova. A freezing shockwave rolls out across the ground, clear ice crystals burst up along it,
 * and anyone caught is sealed inside a crystal prison.
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
        Fx.flare(center.clone().add(0, 1, 0), "flare_white", 3f, 10);
        Fx.wave(center, "ring_frost", 0.5f, (float) RADIUS * 2.2f, STEPS);
        Fx.wave(center.clone().add(0, 0.02, 0), "ring_white", 0.5f, (float) RADIUS * 1.6f, STEPS + 4);
        w.spawnParticle(Particle.CLOUD, center.clone().add(0, 0.3, 0), 30, 1.2, 0.1, 1.2, 0.04);
        Fx.burst(w, center.clone().add(0, 1, 0), Particle.SNOWFLAKE, 40, 0.25);

        every(STEPS, t -> {
            double r = RADIUS * (t + 1) / STEPS;
            Location ring = center.clone().add(0, 0.15, 0);
            Fx.ring(w, ring, r, (int) (r * 8), t * 0.3, Particle.SNOWFLAKE);
            Fx.ring(w, ring, Math.max(0.2, r - 0.3), (int) (r * 6), 0.15, Fx.dust(0xcaf0f8, 1.2f));
            if (t % 2 == 0) crystals(center, r, 4 + t);
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

    /** Clear crystal clusters that burst out of the ground along the wavefront, then crack apart. */
    private void crystals(Location center, double r, int count) {
        World w = center.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + rnd.nextDouble(-0.25, 0.25);
            Block top = surface(center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            if (top == null) continue;
            float h = (float) rnd.nextDouble(1.0, 1.8);
            Location base = top.getLocation().add(0.5, 1.0 + h / 2, 0.5);
            ItemDisplay c = Fx.sprite(base, "ice_cluster", 0.05f, Display.Billboard.VERTICAL);
            Fx.animate(c, 3, Fx.sized(h * 0.8f, h, 0), 1);
            int life = 26 + rnd.nextInt(14);
            Fx.animate(c, 4, Fx.sized(0.01f, 0.01f, 0), life);
            Fx.removeLater(c, life + 6);
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    w.spawnParticle(Particle.BLOCK, base, 8, 0.2, h / 3, 0.2, 0, Material.PACKED_ICE.createBlockData()), life);
        }
    }

    private static Block surface(Location at) {
        Block b = at.getBlock();
        for (int dy = 2; dy >= -3; dy--) {
            Block c = b.getRelative(0, dy, 0);
            if (c.getType().isSolid() && c.getRelative(0, 1, 0).isPassable()) return c;
        }
        return null;
    }
}
