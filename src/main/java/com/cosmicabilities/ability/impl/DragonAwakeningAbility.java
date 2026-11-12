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

/** Ender Dragon drop: a massive spectral dragon charges forward and bites everything in its path (8 hearts). */
public class DragonAwakeningAbility extends CosmicAbility {
    public DragonAwakeningAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.DRAGON_AWAKENING); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        Vector dir = Targets.flatDirection(player);
        Location start = player.getLocation().add(0, 1.6, 0).subtract(dir.clone().multiply(2));
        float yaw = (float) Math.atan2(-dir.getX(), dir.getZ());
        ItemDisplay head = w.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.DRAGON_HEAD));
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setGlowing(true);
            d.setGlowColorOverride(org.bukkit.Color.fromRGB(type.rgb()));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(yaw + (float) Math.PI), new Vector3f(3.2f, 3.2f, 3.2f), new Quaternionf()));
        });
        // spectral body (shrinking segments) and two wings
        List<BlockDisplay> body = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            float s = 1.9f - i * 0.15f;
            BlockDisplay b = Fx.block(start, i % 3 == 0 ? Material.MAGENTA_STAINED_GLASS : Material.PURPLE_STAINED_GLASS, new Vector3f(s, s * 0.8f, s), new Quaternionf().rotateY(yaw), true);
            b.setTeleportDuration(1);
            body.add(b);
        }
        BlockDisplay[] wings = new BlockDisplay[2];
        for (int k = 0; k < 2; k++) {
            wings[k] = Fx.block(start, Material.PURPLE_STAINED_GLASS, new Vector3f(5f, 0.1f, 2.2f), new Quaternionf(), true);
            wings[k].setTeleportDuration(1);
        }
        List<Location> trail = new ArrayList<>();
        Set<UUID> bitten = new HashSet<>();
        w.playSound(start, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.9f);
        w.playSound(start, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.8f);
        double range = plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 24);
        int ticks = 30;
        every(ticks + 12, t -> {
            if (t <= ticks) {
                double prog = t / (double) ticks;
                Location h = start.clone().add(dir.clone().multiply(range * prog + 2)).add(0, Math.sin(prog * Math.PI * 3) * 0.6, 0);
                head.teleport(h);
                trail.add(0, h.clone());
                for (int i = 0; i < body.size(); i++) {
                    Location p = trail.get(Math.min(trail.size() - 1, (i + 1) * 1));
                    body.get(i).teleport(p.clone().subtract(dir.clone().multiply(1.2 + i * 0.9)));
                }
                float flap = (float) Math.sin(t * 0.7) * 0.6f;
                for (int k = 0; k < 2; k++) {
                    int side = k == 0 ? 1 : -1;
                    Location wl = h.clone().subtract(dir.clone().multiply(2.5)).add(0, 0.6, 0);
                    wings[k].teleport(wl);
                    Quaternionf rot = new Quaternionf().rotateY(yaw).rotateZ(side * flap);
                    Vector3f tr = new Vector3f(side > 0 ? 0.3f : -5.3f, 0, -1.1f);
                    rot.transform(tr);
                    wings[k].setInterpolationDelay(0);
                    wings[k].setInterpolationDuration(1);
                    wings[k].setTransformation(new Transformation(tr, rot, new Vector3f(5f, 0.1f, 2.2f), new Quaternionf()));
                }
                w.spawnParticle(Particle.DUST, h, 10, 1.2, 0.8, 1.2, 0, Fx.dust(type.rgb(), 2f));
                Fx.safe(w, Particle.DRAGON_BREATH, h, 6, 0.8, 0.5, 0.8, 0.02, 1.0f);
                if (t % 5 == 0) w.playSound(h, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.1f);
                for (LivingEntity e : Targets.enemies(player, h.clone().add(0, -0.8, 0), 3.2)) {
                    if (!bitten.add(e.getUniqueId())) continue;
                    hearts(player, e, 8);
                    w.playSound(e.getLocation(), Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.5f, 0.6f);
                    w.playSound(e.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 1.4f);
                    e.setVelocity(dir.clone().multiply(1.1).setY(0.6));
                }
                if (t == ticks && bonus(slot)) {
                    w.playSound(h, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 0.6f);
                    for (int r = 1; r <= 5; r++) Fx.ring(w, h, r, 12 + r * 6, r, Fx.dust(type.rgb(), 1.6f));
                    for (UUID id : bitten) {
                        Entity e = plugin.getServer().getEntity(id);
                        if (e instanceof LivingEntity le) le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, true, true));
                    }
                }
                return true;
            }
            if (t == ticks + 1) {
                Fx.animate(head, 10, new Transformation(new Vector3f(), new Quaternionf().rotateY(yaw + (float) Math.PI), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()), 0);
                for (BlockDisplay b : body) Fx.animate(b, 10, Fx.centred(0.01f, 0.01f, 0.01f, new Quaternionf()), 0);
                for (BlockDisplay b : wings) Fx.animate(b, 10, Fx.centred(0.01f, 0.01f, 0.01f, new Quaternionf()), 0);
            }
            if (t >= ticks + 11) {
                head.remove();
                body.forEach(Entity::remove);
                for (BlockDisplay b : wings) b.remove();
                return false;
            }
            return true;
        });
        return true;
    }
}
