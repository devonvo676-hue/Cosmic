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

/** Frost Leviathan drop: huge crystals crash down one after another; each impact sends a shockwave (2 hearts, max 6). */
public class ShatterfallAbility extends CosmicAbility {
    public ShatterfallAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.SHATTERFALL); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location c = Targets.targetPoint(player, plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 30));
        World w = c.getWorld();
        c.setY(w.getHighestBlockYAt(c) + 1);
        Map<UUID, Integer> hits = new HashMap<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int count = 6;
        Fx.ring(w, c.clone().add(0, 0.1, 0), 4.5, 40, 0, Fx.dust(type.rgb(), 1.5f));
        w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2f, 0.5f);
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble(Math.PI * 2), d = i == 0 ? 0 : r.nextDouble(1.2, 4);
            Location spot = c.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            spot.setY(w.getHighestBlockYAt(spot) + 1);
            Location sky = spot.clone().add(0, 18, 0);
            sky.setYaw(0); sky.setPitch(0);
            Quaternionf tilt = new Quaternionf().rotateY(r.nextFloat() * 6f).rotateX((r.nextFloat() - 0.5f) * 0.5f);
            List<BlockDisplay> cluster = new ArrayList<>();
            float big = 1.1f + r.nextFloat() * 0.6f;
            cluster.add(Fx.block(sky, Material.PACKED_ICE, new Vector3f(big, big * 3.2f, big), tilt, true));
            cluster.add(Fx.block(sky, Material.LIGHT_BLUE_STAINED_GLASS, new Vector3f(big * 0.7f, big * 2.2f, big * 0.7f), new Quaternionf(tilt).rotateZ(0.5f), true));
            cluster.add(Fx.block(sky, Material.BLUE_ICE, new Vector3f(big * 0.6f, big * 1.8f, big * 0.6f), new Quaternionf(tilt).rotateZ(-0.6f), true));
            for (BlockDisplay b : cluster) { b.setTeleportDuration(1); b.setGlowing(true); b.setGlowColorOverride(org.bukkit.Color.fromRGB(0xbdf0ff)); }
            int delay = 10 + i * 7;
            later(delay - 10, () -> w.playSound(sky, Sound.BLOCK_GLASS_HIT, 1.5f, 0.5f));
            every(delay + 1, t -> {
                if (t < delay - 8) return true;
                double prog = (t - (delay - 8)) / 8.0;
                Location at = sky.clone().subtract(0, 18 * prog * prog, 0);
                for (BlockDisplay b : cluster) if (b.isValid()) b.teleport(at);
                w.spawnParticle(Particle.SNOWFLAKE, at, 4, 0.4, 0.8, 0.4, 0.02);
                if (t >= delay) {
                    cluster.forEach(b -> {
                        Fx.animate(b, 6, Fx.centred(0.01f, 0.01f, 0.01f, new Quaternionf()), 4);
                        Fx.removeLater(b, 11);
                    });
                    w.playSound(spot, Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
                    w.playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.6f);
                    w.spawnParticle(Particle.ITEM_SNOWBALL, spot, 40, 1, 0.4, 1, 0.2);
                    w.spawnParticle(Particle.BLOCK, spot, 40, 1, 0.2, 1, 0.1, Material.PACKED_ICE.createBlockData());
                    Fx.ring(w, spot.clone().add(0, 0.2, 0), 2.5, 24, 0, Fx.dust(0xbdf0ff, 1.5f));
                    for (LivingEntity e : Targets.enemies(player, spot, 3)) {
                        int n = hits.merge(e.getUniqueId(), 1, Integer::sum);
                        if (n > 3) continue;   // max 6 hearts per enemy
                        hearts(player, e, 2);
                        Targets.knockAway(spot, e, 0.4, 0.3);
                        if (bonus(slot)) e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true, true));
                    }
                    return false;
                }
                return true;
            });
        }
        return true;
    }
}
