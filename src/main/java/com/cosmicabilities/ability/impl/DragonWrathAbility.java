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

/** Legendary (Ender Dragon drop): a spectral dragon head roars beside you and breathes a cone of dragon fire. */
public class DragonWrathAbility extends CosmicAbility {
    public DragonWrathAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.DRAGON_WRATH); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Location headAt = eye.clone().add(right.clone().multiply(0.9)).add(0, 0.35, 0);
        headAt.setDirection(dir);
        ItemDisplay head = w.spawn(headAt, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.DRAGON_HEAD));
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY((float) Math.PI), new Vector3f(0.1f, 0.1f, 0.1f), new Quaternionf()));
        });
        Fx.animate(head, 6, new Transformation(new Vector3f(), new Quaternionf().rotateY((float) Math.PI), new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()), 1);
        w.playSound(headAt, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 1.1f);
        double damage = 2.2;    // per tick-pulse, capped per victim by the combo cap
        Set<UUID> burned = new HashSet<>();
        every(40, t -> {
            if (!player.isOnline() || player.isDead()) { head.remove(); return false; }
            Location e2 = player.getEyeLocation();
            Vector d2 = e2.getDirection().normalize();
            Vector r2 = d2.clone().crossProduct(new Vector(0, 1, 0));
            if (r2.lengthSquared() < 1e-4) r2 = new Vector(1, 0, 0);
            Location h = e2.clone().add(r2.normalize().multiply(0.9)).add(0, 0.35, 0);
            h.setDirection(d2);
            head.teleport(h);
            if (t < 8) return true;
            if (t == 8) w.playSound(h, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.5f, 0.8f);
            if (t % 4 == 0) w.playSound(h, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 0.6f);
            Location mouth = h.clone().add(d2.clone().multiply(0.8));
            for (int i = 0; i < 6; i++) {
                Vector spread = d2.clone().add(new Vector(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).multiply(0.35)).normalize();
                Fx.safe(w, Particle.DRAGON_BREATH, mouth, 0, spread.getX(), spread.getY(), spread.getZ(), 0.6, 1.0f);
            }
            w.spawnParticle(Particle.DUST, mouth.clone().add(d2.clone().multiply(2)), 4, 0.5, 0.5, 0.5, 0, Fx.dust(type.rgb(), 1.6f));
            if (t % 5 == 0) {
                for (LivingEntity e : Targets.enemies(player, mouth.clone().add(d2.clone().multiply(4)), 5)) {
                    Vector to = e.getLocation().add(0, 1, 0).toVector().subtract(mouth.toVector());
                    if (to.length() > 9 || to.normalize().dot(d2) < 0.75) continue;
                    hit(player, e, damage);
                    e.setFireTicks(Math.max(e.getFireTicks(), 40));
                    burned.add(e.getUniqueId());
                }
            }
            if (t >= 40) {
                Location end = h.clone();
                Fx.animate(head, 5, new Transformation(new Vector3f(), new Quaternionf().rotateY((float) Math.PI), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()), 0);
                Fx.removeLater(head, 6);
                if (bonus(slot)) gust(player, end, d2);
                return false;
            }
            return true;
        });
        return true;
    }

    private void gust(Player player, Location from, Vector dir) {
        World w = from.getWorld();
        w.playSound(from, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.7f);
        for (int i = 1; i < 8; i++) Fx.ring(w, from.clone().add(dir.clone().multiply(i)), 0.4 + i * 0.25, 14, i, Fx.dust(type.rgb(), 1.2f));
        for (LivingEntity e : Targets.enemies(player, from.clone().add(dir.clone().multiply(3.5)), 4.5)) {
            Vector v = dir.clone().setY(0).normalize().multiply(1.4).setY(0.5);
            e.setVelocity(v);
        }
        Location cloud = from.clone().add(dir.clone().multiply(5));
        cloud.setY(w.getHighestBlockYAt(cloud) + 1);
        every(60, t -> {
            if (t % 3 == 0) Fx.safe(w, Particle.DRAGON_BREATH, cloud, 10, 1.4, 0.2, 1.4, 0.005, 1.0f);
            if (t % 20 == 0) for (LivingEntity e : Targets.enemies(player, cloud, 2.5)) hit(player, e, 2);
            return true;
        });
    }
}
