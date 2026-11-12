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
/**
 * Cosmic Destroyer drop: a burning sigil marks a big circle, a meteor forms high above it, then crashes down
 * diagonally and blows the whole area apart. (7 hearts)
 */
public class ApocalypseAbility extends CosmicAbility {
    public ApocalypseAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.APOCALYPSE); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location c = Targets.targetPoint(player, plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 32));
        World w = c.getWorld();
        c.setY(w.getHighestBlockYAt(c) + 1);
        c.setYaw(0); c.setPitch(0);
        double radius = plugin.getConfig().getDouble("ability-settings." + type.id() + ".radius", 7);
        double hearts = plugin.getConfig().getDouble("ability-settings." + type.id() + ".hearts", 7);
        Location ground = c.clone().add(0, 0.05, 0);
        // the mark
        ItemDisplay sigil = Fx.flat(ground, "sigil", 0.1f, 0);
        Fx.animate(sigil, 10, Fx.flatShape((float) radius * 2.2f, 0), 1);
        ItemDisplay ring = Fx.flat(ground.clone().add(0, 0.02, 0), "ring_red", (float) radius * 2.1f, 0);
        Vector side = Targets.flatDirection(player).crossProduct(new Vector(0, 1, 0)).normalize();
        Location sky = c.clone().add(side.clone().multiply(14)).add(0, 34, 0);
        ItemDisplay meteor = Fx.model(sky, "boss_fx_meteor", 0.1f);
        meteor.setTeleportDuration(1);
        meteor.setGlowing(true);
        meteor.setGlowColorOverride(org.bukkit.Color.fromRGB(0xff5a1f));
        ItemDisplay aura = Fx.sprite(sky, "flare_gold", 0.1f, Display.Billboard.CENTER);
        aura.setTeleportDuration(1);
        w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(c) < 80 * 80) p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.8f);
        int charge = 40, fall = 10;
        Vector fallVec = c.toVector().subtract(sky.toVector());
        every(charge + fall + 1, t -> {
            float spinA = t * 0.05f;
            sigil.setInterpolationDelay(0); sigil.setInterpolationDuration(1);
            if (t > 10) sigil.setTransformation(Fx.flatShape((float) radius * 2.2f, spinA));
            if (t % 6 == 0) Fx.wave(ground.clone().add(0, 0.03, 0), "ring_orange", (float) radius * 2.1f, 0.5f, 10);   // energy pulled inward
            if (t % 2 == 0) for (int i = 0; i < 6; i++) {   // flame pillars around the edge
                double a = i * Math.PI / 3 + t * 0.05;
                Location e = c.clone().add(Math.cos(a) * radius, 0.2 + (t % 10) * 0.3, Math.sin(a) * radius);
                w.spawnParticle(Particle.FLAME, e, 2, 0.1, 0.2, 0.1, 0.01);
                w.spawnParticle(Particle.DUST, e, 1, 0, 0, 0, 0, Fx.dust(0xff2a2a, 1.6f));
            }
            if (t < charge) {
                float s = 0.3f + 5.2f * (t / (float) charge);
                meteor.setInterpolationDelay(0); meteor.setInterpolationDuration(2);
                meteor.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateXYZ(t * 0.04f, t * 0.07f, t * 0.03f),
                        new Vector3f(s, s, s), new Quaternionf()));
                aura.setInterpolationDelay(0); aura.setInterpolationDuration(2);
                aura.setTransformation(Fx.sized(s * 1.9f, s * 1.9f, t * 0.1f));
                for (int i = 0; i < 3; i++) {
                    double a = Math.random() * Math.PI * 2, b = Math.random() * Math.PI - Math.PI / 2;
                    Location from = sky.clone().add(Math.cos(a) * Math.cos(b) * 12, Math.sin(b) * 12, Math.sin(a) * Math.cos(b) * 12);
                    Vector v = sky.toVector().subtract(from.toVector()).multiply(0.1);
                    w.spawnParticle(Particle.END_ROD, from, 0, v.getX(), v.getY(), v.getZ(), 1);
                }
                if (t % 10 == 0) w.playSound(sky, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 3f, 0.5f + t * 0.02f);
                if (t == charge - 1) for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(c) < 90 * 90)
                    p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.2f, 0.5f);
                return true;
            }
            double prog = (t - charge) / (double) fall;
            Location at = sky.clone().add(fallVec.clone().multiply(prog * prog));
            meteor.teleport(at);
            aura.teleport(at);
            meteor.setInterpolationDelay(0); meteor.setInterpolationDuration(1);
            meteor.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateXYZ(t * 0.3f, t * 0.2f, 0), new Vector3f(5.5f, 5.5f, 5.5f), new Quaternionf()));
            ItemDisplay trail = Fx.sprite(at, "flare_gold", 4f, Display.Billboard.CENTER);
            Fx.animate(trail, 8, Fx.sized(0.2f, 0.2f, 0), 1);
            Fx.removeLater(trail, 10);
            w.spawnParticle(Particle.LARGE_SMOKE, at, 20, 1.5, 1.5, 1.5, 0.05);
            w.spawnParticle(Particle.FLAME, at, 30, 2, 2, 2, 0.1);
            if (t >= charge + fall) {
                meteor.remove(); aura.remove();
                Fx.animate(sigil, 6, Fx.flatShape(0.01f, spinA), 2);
                Fx.removeLater(sigil, 10);
                ring.remove();
                impact(player, c, radius, hearts, slot);
                return false;
            }
            return true;
        });
        return true;
    }

    private void impact(Player player, Location c, double radius, double hearts, AbilitySlot slot) {
        World w = c.getWorld();
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
        w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.5f);
        w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.6f);
        ItemDisplay flash = Fx.sprite(c.clone().add(0, 1.5, 0), "sun", 2f, Display.Billboard.CENTER);
        Fx.animate(flash, 4, Fx.sized((float) radius * 2.6f, (float) radius * 2.6f, 0), 1);
        Fx.animate(flash, 12, Fx.sized(0.1f, 0.1f, 1), 6);
        Fx.removeLater(flash, 20);
        Fx.wave(c.clone().add(0, 0.1, 0), "ring_orange", 1f, (float) radius * 3.2f, 10);
        Fx.wave(c.clone().add(0, 0.4, 0), "ring_white", 1f, (float) radius * 2.4f, 8);
        Fx.wave(c.clone().add(0, 0.8, 0), "ring_red", 1f, (float) radius * 2.8f, 12);
        ItemDisplay scorch = Fx.flat(c.clone().add(0, 0.03, 0), "scorch", (float) radius * 2.2f, (float) Math.random() * 6);
        Fx.animate(scorch, 20, Fx.flatShape(0.01f, 0), 100);
        Fx.removeLater(scorch, 125);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 6, radius / 2, 0.5, radius / 2, 0);
        w.spawnParticle(Particle.FLAME, c, 250, radius / 2, 1, radius / 2, 0.25);
        w.spawnParticle(Particle.LAVA, c, 60, radius / 2, 0.5, radius / 2, 0);
        // debris thrown out of the crater
        ThreadLocalRandom r = ThreadLocalRandom.current();
        org.bukkit.block.data.BlockData groundData = c.clone().subtract(0, 1, 0).getBlock().getBlockData();
        for (int i = 0; i < 14; i++) {
            Material m = i % 3 == 0 ? Material.MAGMA_BLOCK : (i % 3 == 1 ? Material.BLACKSTONE : groundData.getMaterial().isBlock() && groundData.getMaterial().isSolid() ? groundData.getMaterial() : Material.DIRT);
            BlockDisplay chunk = Fx.block(c.clone().add(0, 0.5, 0), m, new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf().rotateXYZ(r.nextFloat() * 3, r.nextFloat() * 3, 0), false);
            chunk.setTeleportDuration(1);
            double a = r.nextDouble(Math.PI * 2);
            Vector v = new Vector(Math.cos(a) * r.nextDouble(0.3, 0.7), r.nextDouble(0.5, 0.95), Math.sin(a) * r.nextDouble(0.3, 0.7));
            every(30, k -> {
                if (!chunk.isValid()) return false;
                v.setY(v.getY() - 0.06);
                Location nl = chunk.getLocation().add(v);
                if (nl.getBlock().getType().isSolid() || k >= 30) { chunk.remove(); return false; }
                chunk.teleport(nl);
                if (k % 3 == 0) w.spawnParticle(Particle.SMOKE, nl, 1, 0, 0, 0, 0);
                return true;
            });
        }
        for (Player p : w.getPlayers()) {
            double d2 = p.getLocation().distanceSquared(c);
            if (d2 < Math.pow(radius * 3, 2)) for (int i = 0; i < 3; i++) later(i * 3L, () -> p.sendHurtAnimation((float) Math.random() * 360));
        }
        for (LivingEntity e : Targets.enemies(player, c, radius)) {
            hearts(player, e, hearts);
            Targets.knockAway(c, e, 1.3, 0.9);
            e.setFireTicks(Math.max(e.getFireTicks(), 60));
        }
        if (bonus(slot)) every(100, k -> {
            if (k % 3 == 0) w.spawnParticle(Particle.FLAME, c, 14, radius / 2, 0.1, radius / 2, 0.01);
            if (k % 6 == 0) w.spawnParticle(Particle.LARGE_SMOKE, c, 4, radius / 2, 0.2, radius / 2, 0.02);
            if (k % 20 == 0) for (LivingEntity e : Targets.enemies(player, c, radius * 0.8)) e.setFireTicks(Math.max(e.getFireTicks(), 40));
            return true;
        });
    }
}
