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
 * Nebula Drake drop: a huge spectral serpent (the drake's own head and neck models) bursts out of the ground,
 * weaves forward through the battlefield biting anyone it touches, then coils up and explodes. (5 hearts)
 */
public class SerpentsWrathAbility extends CosmicAbility {
    public SerpentsWrathAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.SERPENTS_WRATH); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        Vector dir = Targets.flatDirection(player);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        double length = plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 26);
        Location origin = player.getLocation().add(dir.clone().multiply(2));
        float S = 2.2f;      // model scale
        int segs = 14;
        List<ItemDisplay> body = new ArrayList<>();
        body.add(Fx.model(origin, "boss_nebula_serpent_head", 0.01f));
        for (int i = 0; i < segs; i++) body.add(Fx.model(origin, i < segs - 3 ? "boss_nebula_serpent_neck" : "boss_nebula_serpent_tail3", 0.01f));
        for (ItemDisplay d : body) {
            d.setTeleportDuration(1);
            d.setGlowing(true);
            d.setGlowColorOverride(org.bukkit.Color.fromRGB(type.rgb()));
            d.setViewRange(4f);
        }
        List<Location> path = new ArrayList<>();
        Set<UUID> touched = new HashSet<>();
        int travel = 46, coil = 24;
        double spacing = 6.0 / 16 * S;          // one neck model length
        w.playSound(origin, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 1.5f);
        w.playSound(origin, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.6f);
        w.spawnParticle(Particle.BLOCK, origin, 60, 1, 0.3, 1, 0.2, origin.clone().subtract(0, 1, 0).getBlock().getBlockData());
        // target: whoever you aim at (or the nearest enemy in front of you)
        LivingEntity target = aimed(player, length + 6);
        Location[] headPos = {origin.clone().add(0, -1.5, 0)};
        double[] circleStart = {-1};
        Location[] end = new Location[1];
        double speed = 0.75;
        every(travel + coil + 40, t -> {
            Location head;
            Location cur = headPos[0];
            boolean hasTarget = target != null && target.isValid() && !target.isDead();
            Location goal = hasTarget ? target.getLocation() : origin.clone().add(dir.clone().multiply(length));
            Vector to = goal.toVector().subtract(cur.toVector()).setY(0);
            double dist = to.length();
            if (circleStart[0] < 0 && (dist < 3.6 || t >= travel + 20)) { circleStart[0] = t; end[0] = goal.clone(); }
            if (circleStart[0] < 0) {
                // slither toward the target: forward plus a side-to-side wave
                Vector fwd = dist > 0.01 ? to.clone().normalize() : dir.clone();
                Vector sideV = new Vector(-fwd.getZ(), 0, fwd.getX());
                head = cur.clone().add(fwd.clone().multiply(speed)).add(sideV.multiply(Math.cos(t * 0.45) * 0.55));
                double floor = w.getHighestBlockYAt(head) + 1.0;
                head.setY(floor + 0.5 + Math.sin(t * 0.35) * 0.4 + (t < 6 ? -1.5 + t * 0.25 : 0));
            } else {
                // circle around them, tightening and rising into a coil
                Location c = hasTarget ? target.getLocation() : end[0];
                end[0] = c.clone();
                double k = Math.min(1, (t - circleStart[0]) / (double) coil);
                double a = k * Math.PI * 3.4;
                double r = 3.4 - k * 1.6;
                head = c.clone().add(Math.cos(a) * r, 0.6 + k * 2.4, Math.sin(a) * r);
            }
            headPos[0] = head.clone();
            path.add(head.clone());
            // place every segment along the path, one model length apart, each facing the next one
            Location prev = head;
            int idx = path.size() - 1;
            for (int i = 0; i < body.size(); i++) {
                Location p = prev;
                if (i > 0) {
                    while (idx > 0 && path.get(idx).distance(prev) < spacing) idx--;
                    p = path.get(idx);
                }
                Location ahead = i == 0 ? (path.size() > 1 ? path.get(path.size() - 2) : p) : prev;
                Vector fwd = i == 0 ? head.toVector().subtract(ahead.toVector()) : ahead.toVector().subtract(p.toVector());
                if (fwd.lengthSquared() < 1e-6) fwd = dir.clone();
                fwd.normalize();
                float sc = i == 0 ? S * 1.1f : S * (1f - i * 0.035f);
                Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) fwd.getX(), (float) fwd.getY(), (float) fwd.getZ()));
                ItemDisplay d = body.get(i);
                Location at = p.clone(); at.setYaw(0); at.setPitch(0);
                d.teleport(at);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(1);
                d.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(sc, sc, sc), new Quaternionf()));
                prev = p;
            }
            if (t % 2 == 0) w.spawnParticle(Particle.DUST, head, 8, 0.8, 0.6, 0.8, 0, Fx.dust(type.rgb(), 2f));
            if (t % 3 == 0) w.spawnParticle(Particle.END_ROD, head, 2, 0.5, 0.5, 0.5, 0.02);
            if (t % 7 == 0) w.playSound(head, Sound.ENTITY_PHANTOM_FLAP, 1.2f, 0.5f);
            if (circleStart[0] < 0 || t % 5 == 0) {
                for (int i = 0; i < body.size(); i += 2) {
                    for (LivingEntity e : Targets.enemies(player, body.get(i).getLocation(), 1.9)) {
                        if (!touched.add(e.getUniqueId())) continue;
                        hearts(player, e, 3);
                        e.setVelocity(side.clone().multiply(Math.random() < 0.5 ? 0.8 : -0.8).setY(0.55));
                        w.playSound(e.getLocation(), Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.5f, 0.7f);
                    }
                }
            }
            if (circleStart[0] >= 0 && t - circleStart[0] >= coil) {
                Location c = end[0].clone().add(0, 1.2, 0);
                w.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2.5f, 0.6f);
                w.playSound(c, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1.2f);
                ItemDisplay burst = Fx.sprite(c, "nebula_pink", 1f, Display.Billboard.CENTER);
                Fx.animate(burst, 6, Fx.sized(9f, 9f, 1), 1);
                Fx.animate(burst, 10, Fx.sized(0.1f, 0.1f, 2), 8);
                Fx.removeLater(burst, 20);
                Fx.wave(end[0].clone().add(0, 0.1, 0), "ring_magenta", 1f, 10f, 10);
                w.spawnParticle(Particle.FIREWORK, c, 90, 0.3, 0.3, 0.3, 0.4);
                for (LivingEntity e : Targets.enemies(player, c, 5)) {
                    hearts(player, e, 2);
                    Targets.knockAway(c, e, 1.0, 0.7);
                    if (bonus(slot)) e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0, false, true, true));
                }
                for (ItemDisplay d : body) {
                    Fx.animate(d, 6, new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()), 0);
                    Fx.removeLater(d, 8);
                }
                return false;
            }
            return true;
        });
        return true;
    }

    private LivingEntity aimed(Player player, double range) {
        var ray = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), range, 2.0,
                e -> e instanceof LivingEntity && Targets.isEnemy(player, e));
        if (ray != null && ray.getHitEntity() instanceof LivingEntity le) return le;
        Vector look = player.getEyeLocation().getDirection().normalize();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : Targets.enemies(player, player.getLocation(), range)) {
            Vector to = e.getLocation().toVector().subtract(player.getLocation().toVector());
            if (to.clone().normalize().dot(look) < 0.7) continue;
            if (to.length() < bestD) { bestD = to.length(); best = e; }
        }
        return best;
    }
}
