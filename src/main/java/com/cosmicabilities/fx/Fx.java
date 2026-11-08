package com.cosmicabilities.fx;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/** Shared visual helpers: damage numbers, hitmarkers, particle shapes and animated display entities. */
public final class Fx {
    private static CosmicAbilitiesPlugin plugin;

    private Fx() {}

    public static void init(CosmicAbilitiesPlugin p) {
        plugin = p;
    }

    // ------------------------------------------------------------------ combat feedback

    /** Floating "-6.5" that pops up from the target and fades upward. */
    public static void damageNumber(LivingEntity target, double amount, int rgb) {
        if (!plugin.getConfig().getBoolean("effects.damage-numbers", true)) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location at = target.getLocation().add(r.nextDouble(-0.4, 0.4), target.getHeight() + 0.25, r.nextDouble(-0.4, 0.4));
        String txt = amount >= 10 ? String.valueOf((int) Math.round(amount)) : String.format("%.1f", amount);
        TextDisplay td = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.text(Component.text("-" + txt, TextColor.color(rgb)).decorate(TextDecoration.BOLD)
                    .append(Component.text(" ❤", TextColor.color(0xff4d6d)).decoration(TextDecoration.BOLD, false)));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf()));
        });
        // pop up, then drift and shrink
        animate(td, 3, new Transformation(new Vector3f(0, 0.35f, 0), new Quaternionf(), new Vector3f(1.15f, 1.15f, 1.15f), new Quaternionf()), 1);
        animate(td, 14, new Transformation(new Vector3f(0, 0.9f, 0), new Quaternionf(), new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()), 6);
        removeLater(td, 22);
    }

    /** Hitmarker: a short tick sound for the attacker and a crit flash on the victim. */
    public static void hitmarker(Player attacker, LivingEntity target) {
        attacker.playSound(attacker.getLocation(), org.bukkit.Sound.ENTITY_ARROW_HIT_PLAYER, 0.45f, 1.7f);
        target.getWorld().spawnParticle(Particle.ENCHANTED_HIT, target.getLocation().add(0, target.getHeight() * 0.6, 0),
                10, 0.25, 0.3, 0.25, 0.25);
    }

    // ------------------------------------------------------------------ particle shapes

    public static Particle.DustOptions dust(int rgb, float size) {
        return new Particle.DustOptions(Color.fromRGB(rgb), size);
    }

    public static void dust(World w, Location at, int rgb, float size, int count, double spread) {
        w.spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, dust(rgb, size));
    }

    /** Horizontal ring. phase rotates the start point so successive frames don't line up. */
    public static void ring(World w, Location c, double radius, int points, double phase, Particle.DustOptions d) {
        for (int i = 0; i < points; i++) {
            double a = phase + 2 * Math.PI * i / points;
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * radius, c.getY(), c.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0, d);
        }
    }

    public static void ring(World w, Location c, double radius, int points, double phase, Particle p) {
        for (int i = 0; i < points; i++) {
            double a = phase + 2 * Math.PI * i / points;
            w.spawnParticle(p, c.getX() + Math.cos(a) * radius, c.getY(), c.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    public static void line(World w, Location a, Location b, double step, Particle.DustOptions d) {
        Vector dir = b.toVector().subtract(a.toVector());
        double len = dir.length();
        if (len < 1.0E-3) return;
        dir.multiply(1 / len);
        for (double t = 0; t <= len; t += step) {
            w.spawnParticle(Particle.DUST, a.getX() + dir.getX() * t, a.getY() + dir.getY() * t, a.getZ() + dir.getZ() * t,
                    1, 0, 0, 0, 0, d);
        }
    }

    /** Burst of particles moving outward from a point (uses count=0 directional spawning). */
    public static void burst(World w, Location c, Particle p, int count, double speed) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Vector v = new Vector(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            w.spawnParticle(p, c, 0, v.getX(), v.getY(), v.getZ(), speed);
        }
    }

    // ------------------------------------------------------------------ display entities

    public static BlockDisplay block(Location at, Material material, Vector3f scale, Quaternionf rotation, boolean bright) {
        BlockData data = material.createBlockData();
        return at.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setPersistent(false);
            // centre the block on its location
            d.setTransformation(centred(scale.x, scale.y, scale.z, new Quaternionf(rotation)));
            if (bright) d.setBrightness(new Display.Brightness(15, 15));
        });
    }

    /** Smoothly animates a display to a new transformation after delay ticks, over duration ticks. */
    public static void animate(Display d, int duration, Transformation target, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(duration);
            d.setTransformation(target);
        }, Math.max(1, delay));
    }

    /** Centred transformation helper (scale about the entity's position, with rotation). */
    public static Transformation centred(float sx, float sy, float sz, Quaternionf rot) {
        Vector3f scale = new Vector3f(sx, sy, sz);
        Vector3f t = new Vector3f(-sx / 2f, -sy / 2f, -sz / 2f);
        rot.transform(t);
        return new Transformation(t, rot, scale, new Quaternionf());
    }

    public static void removeLater(Entity e, long ticks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid()) e.remove();
        }, ticks);
    }
}
