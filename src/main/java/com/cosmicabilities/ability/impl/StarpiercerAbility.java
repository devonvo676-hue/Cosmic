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

/** Solar Titan drop: an enormous golden spear forms above you and is hurled at your target (5 hearts). */
public class StarpiercerAbility extends CosmicAbility {
    public StarpiercerAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.STARPIERCER); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        double range = plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 40);
        Location target;
        var ray = w.rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), range, 1.2,
                e -> e instanceof LivingEntity && Targets.isEnemy(player, e));
        if (ray != null && ray.getHitEntity() != null) target = ray.getHitEntity().getLocation();
        else target = Targets.targetPoint(player, range);
        Location from = player.getLocation().add(0, 6.5, 0);
        from.setYaw(0); from.setPitch(0);
        // spear: shaft, two golden blades and a glowing tip; built along +Y then aimed with a rotation
        List<BlockDisplay> spear = new ArrayList<>();
        float[][] partsDef = {
                {-0.12f, -3.5f, -0.12f, 0.24f, 6.0f, 0.24f},   // shaft
                {-0.35f, 2.2f, -0.35f, 0.7f, 0.35f, 0.7f},      // guard
                {-0.28f, 2.55f, -0.28f, 0.56f, 1.6f, 0.56f},    // blade
                {-0.16f, 4.15f, -0.16f, 0.32f, 0.9f, 0.32f},    // tip
                {-0.3f, -3.8f, -0.3f, 0.6f, 0.4f, 0.6f}};       // pommel
        Material[] mats = {Material.GOLD_BLOCK, Material.RAW_GOLD_BLOCK, Material.GOLD_BLOCK, Material.SHROOMLIGHT, Material.RAW_GOLD_BLOCK};
        for (int i = 0; i < partsDef.length; i++) {
            float[] p = partsDef[i];
            BlockDisplay b = from.getWorld().spawn(from, BlockDisplay.class, d -> {
                d.setBlock(mats[spear.size()].createBlockData());
                d.setPersistent(false);
                d.addScoreboardTag(Fx.TAG);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setGlowing(true);
                d.setGlowColorOverride(org.bukkit.Color.fromRGB(0xffd166));
                d.setTeleportDuration(1);
            });
            b.setTransformation(new Transformation(new Vector3f(p[0], p[1], p[2]), new Quaternionf(), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
            spear.add(b);
        }
        w.playSound(from, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.8f);
        w.playSound(from, Sound.ITEM_TRIDENT_RETURN, 1.5f, 0.6f);
        final Location tgt = target.clone();
        int form = 12, fly = 10;
        every(form + fly + 2, t -> {
            Vector to = tgt.toVector().subtract(from.toVector());
            Quaternionf aim = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) to.getX(), (float) to.getY(), (float) to.getZ()).normalize());
            float grow = Math.min(1f, (t + 1) / (float) form);
            Location at;
            if (t <= form) {
                at = from.clone();
                w.spawnParticle(Particle.DUST, from, 6, 0.6, 1.5, 0.6, 0, Fx.dust(0xffd166, 1.6f));
            } else {
                double prog = Math.min(1, (t - form) / (double) fly);
                at = from.clone().add(to.clone().multiply(prog * prog));
                w.spawnParticle(Particle.END_ROD, at, 4, 0.1, 0.1, 0.1, 0.02);
                w.spawnParticle(Particle.DUST, at, 6, 0.2, 0.2, 0.2, 0, Fx.dust(0xffb703, 1.8f));
            }
            for (int i = 0; i < spear.size(); i++) {
                float[] p = partsDef[i];
                BlockDisplay b = spear.get(i);
                b.teleport(at);
                Matrix4f m = new Matrix4f().rotate(aim).translate(p[0] * grow, p[1] * grow, p[2] * grow).scale(p[3] * grow, p[4] * grow, p[5] * grow);
                b.setInterpolationDelay(0);
                b.setInterpolationDuration(1);
                b.setTransformationMatrix(m);
            }
            if (t == form) w.playSound(from, Sound.ITEM_TRIDENT_THROW, 2f, 0.5f);
            if (t >= form + fly) {
                spear.forEach(Entity::remove);
                impact(player, tgt, slot);
                return false;
            }
            return true;
        });
        return true;
    }

    private void impact(Player player, Location at, AbilitySlot slot) {
        World w = at.getWorld();
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 1.3f);
        w.playSound(at, Sound.BLOCK_BELL_RESONATE, 2f, 1.6f);
        Fx.safe(w, Particle.FLASH, at, 1, 0, 0, 0, 0, org.bukkit.Color.fromRGB(0xffd166));
        for (int r = 1; r <= 4; r++) Fx.ring(w, at.clone().add(0, 0.3, 0), r, 14 + r * 8, r, Fx.dust(0xffd166, 2f));
        w.spawnParticle(Particle.END_ROD, at, 60, 0.4, 0.4, 0.4, 0.4);
        for (LivingEntity e : Targets.enemies(player, at, 4.5)) {
            hearts(player, e, 5);
            Targets.knockAway(at, e, 0.8, 0.5);
            if (bonus(slot)) e.setFireTicks(Math.max(e.getFireTicks(), 80));
        }
    }
}
