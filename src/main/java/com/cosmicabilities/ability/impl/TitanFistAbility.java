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

/** Solar Titan drop: a huge molten fist with copper bracers falls from the sky where you aim. */
public class TitanFistAbility extends CosmicAbility {
    public TitanFistAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.TITAN_FIST); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location aim = Targets.targetPoint(player, 26);
        World w = aim.getWorld();
        aim.setY(w.getHighestBlockYAt(aim.getBlockX(), aim.getBlockZ()) + 1);
        smash(player, aim, 9);
        if (bonus(slot)) {
            Vector side = Targets.flatDirection(player).crossProduct(new Vector(0, 1, 0)).multiply(3.5);
            Location second = aim.clone().add(side);
            second.setY(w.getHighestBlockYAt(second.getBlockX(), second.getBlockZ()) + 1);
            later(18, () -> smash(player, second, 6));
        }
        return true;
    }

    private void smash(Player player, Location at, double damage) {
        World w = at.getWorld();
        Location top = at.clone().add(0, 22, 0);
        top.setYaw(0); top.setPitch(0);
        List<BlockDisplay> fist = new ArrayList<>();
        // knuckle block, copper bands and a forearm with a lava seam
        fist.add(part(top, Material.MAGMA_BLOCK, -1.4f, 0, -1.4f, 2.8f, 2.4f, 2.8f));
        fist.add(part(top, Material.WAXED_CUT_COPPER, -1.5f, 0.6f, -1.5f, 3.0f, 0.4f, 3.0f));
        fist.add(part(top, Material.WAXED_CUT_COPPER, -1.5f, 1.5f, -1.5f, 3.0f, 0.4f, 3.0f));
        fist.add(part(top, Material.NETHERRACK, -1.0f, 2.4f, -1.0f, 2.0f, 4.5f, 2.0f));
        fist.add(part(top, Material.SHROOMLIGHT, -1.02f, 3.4f, -0.3f, 2.04f, 2.5f, 0.6f));
        fist.add(part(top, Material.WAXED_EXPOSED_CUT_COPPER, -1.2f, 2.9f, -1.2f, 2.4f, 0.5f, 2.4f));
        w.playSound(at, Sound.ENTITY_BLAZE_AMBIENT, 1.5f, 0.5f);
        every(16, t -> {
            double y = top.getY() - (22 * Math.pow(t / 16.0, 2));
            Location p = top.clone(); p.setY(y);
            for (BlockDisplay b : fist) if (b.isValid()) b.teleport(p);
            Fx.ring(w, at.clone().add(0, 0.1, 0), 3.2 - t * 0.1, 18, t, Fx.dust(type.rgb(), 1.3f));
            w.spawnParticle(Particle.FLAME, p.clone().add(0, 2, 0), 6, 0.8, 1, 0.8, 0.02);
            if (t >= 16) {
                w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
                w.playSound(at, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.5f, 0.5f);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
                w.spawnParticle(Particle.LAVA, at, 30, 2, 0.3, 2, 0);
                w.spawnParticle(Particle.BLOCK, at, 80, 2.5, 0.3, 2.5, 0.2, at.clone().subtract(0, 1, 0).getBlock().getBlockData());
                for (int i = 0; i < 6; i++) Fx.decal(at.clone().add(Math.random() * 4 - 2, 0.02, Math.random() * 4 - 2), Material.MAGMA_BLOCK, 1.2f, (float) Math.random() * 6, 80);
                for (LivingEntity e : Targets.enemies(player, at, 4.5)) {
                    hit(player, e, damage);
                    e.setFireTicks(Math.max(e.getFireTicks(), 80));
                    Targets.knockAway(at, e, 0.9, 0.7);
                }
                later(10, () -> fist.forEach(b -> {
                    Fx.animate(b, 8, new Transformation(new Vector3f(0, -2, 0), new Quaternionf(), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()), 0);
                    Fx.removeLater(b, 9);
                }));
                return false;
            }
            return true;
        });
    }

    private BlockDisplay part(Location at, Material m, float x, float y, float z, float sx, float sy, float sz) {
        return at.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setTeleportDuration(1);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf()));
        });
    }
}
