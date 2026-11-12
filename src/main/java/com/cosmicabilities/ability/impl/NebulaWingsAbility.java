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

/** Nebula Serpent drop: nebula wings carry you up, then you dive down in a starburst. */
public class NebulaWingsAbility extends CosmicAbility {
    public NebulaWingsAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.NEBULA_WINGS); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        UUID id = player.getUniqueId();
        status().addFallImmunity(id);
        boolean glide = bonus(slot);
        w.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.5f, 0.6f);
        w.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.4f);
        player.setVelocity(player.getLocation().getDirection().setY(0).normalize().multiply(0.6).setY(1.6));
        List<BlockDisplay> wings = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < 3; f++) {
                Material m = f == 0 ? Material.MAGENTA_STAINED_GLASS : f == 1 ? Material.PURPLE_STAINED_GLASS : Material.PINK_STAINED_GLASS;
                BlockDisplay b = Fx.block(player.getLocation(), m, new Vector3f(1.6f - f * 0.3f, 0.05f, 0.6f), new Quaternionf(), true);
                b.setTeleportDuration(1);
                b.addScoreboardTag("side" + side);
                b.addScoreboardTag("f" + f);
                wings.add(b);
            }
        }
        final boolean[] landed = {false};
        every(glide ? 120 : 70, t -> {
            if (!player.isOnline() || player.isDead()) { wings.forEach(BlockDisplay::remove); status().removeFallImmunity(id); return false; }
            Location at = player.getLocation();
            float yaw = (float) Math.toRadians(-at.getYaw());
            double flap = Math.sin(t * 0.5) * 0.5;
            for (BlockDisplay b : wings) {
                int side = b.getScoreboardTags().contains("side1") ? 1 : -1;
                int f = b.getScoreboardTags().contains("f0") ? 0 : b.getScoreboardTags().contains("f1") ? 1 : 2;
                Location p = at.clone().add(0, 1.2, 0); p.setYaw(0); p.setPitch(0);
                b.teleport(p);
                Quaternionf rot = new Quaternionf().rotateY(yaw).rotateZ((float) (side * (0.3 + flap + f * 0.25)));
                Vector3f tr = new Vector3f(side > 0 ? 0.15f : -(1.6f - f * 0.3f) - 0.15f, -0.1f * f, -0.4f + f * 0.25f);
                rot.transform(tr);
                b.setInterpolationDelay(0);
                b.setInterpolationDuration(1);
                b.setTransformation(new Transformation(tr, rot, new Vector3f(1.6f - f * 0.3f, 0.05f, 0.6f), new Quaternionf()));
            }
            if (t % 2 == 0) w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 3, 0.6, 0.3, 0.6, 0, Fx.dust(type.rgb(), 1.3f));
            if (t % 3 == 0) w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 1, 0.4, 0.2, 0.4, 0.01);
            if (glide && t > 14 && player.getVelocity().getY() < -0.15) {
                Vector v = player.getVelocity(); v.setY(-0.15); player.setVelocity(v);
            }
            if (t > 10 && player.isOnGround() && !landed[0]) {
                landed[0] = true;
                land(player, glide);
                wings.forEach(BlockDisplay::remove);
                later(10, () -> status().removeFallImmunity(id));
                return false;
            }
            if (t >= (glide ? 120 : 70)) {
                wings.forEach(BlockDisplay::remove);
                later(60, () -> status().removeFallImmunity(id));
                return false;
            }
            return true;
        });
        return true;
    }

    private void land(Player player, boolean strong) {
        Location at = player.getLocation();
        World w = at.getWorld();
        w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 0.8f);
        w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 0.8f);
        for (int r = 1; r <= 4; r++) Fx.ring(w, at.clone().add(0, 0.2, 0), r, 10 + r * 6, r, Fx.dust(type.rgb(), 1.5f));
        w.spawnParticle(Particle.FIREWORK, at.clone().add(0, 0.5, 0), 50, 0.3, 0.3, 0.3, 0.3);
        for (LivingEntity e : Targets.enemies(player, at, 4.5)) {
            hit(player, e, strong ? 8 : 6);
            Targets.knockAway(at, e, 1.0, 0.6);
        }
    }
}
