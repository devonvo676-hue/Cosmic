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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A column of starlight pours down from the sky onto you: the beam extends from high above to your feet,
 * light rains down inside it, a star sigil burns into the ground and you're healed.
 */
public class CelestialAbility extends CosmicAbility {
    private static final List<PotionEffectType> NEGATIVE = List.of(
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
            PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.DARKNESS);
    private static final float HEIGHT = 22f;

    public CelestialAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.CELESTIAL); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        double heal = dmg(8, slot);
        Location base = player.getLocation();
        Location top = base.clone().add(0, HEIGHT, 0);
        top.setYaw(0);
        top.setPitch(0);
        w.playSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.4f);
        w.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);

        // outer glow (translucent) and white-hot core, both hanging from the sky and growing downward
        BlockDisplay glow = column(top, Material.LIGHT_BLUE_STAINED_GLASS, 1.3f);
        BlockDisplay core = column(top, Material.WHITE_CONCRETE, 0.35f);
        Fx.animate(glow, 6, hang(1.3f, HEIGHT), 1);
        Fx.animate(core, 6, hang(0.35f, HEIGHT), 1);

        every(36, t -> {
            if (!player.isOnline()) { glow.remove(); core.remove(); return false; }
            Location feet = player.getLocation();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            // rain of light falling down the column
            double reach = Math.min(1, t / 6.0) * HEIGHT;
            for (int i = 0; i < 6; i++) {
                double y = HEIGHT - r.nextDouble(reach);
                w.spawnParticle(Particle.END_ROD, base.getX() + r.nextDouble(-0.5, 0.5), base.getY() + y,
                        base.getZ() + r.nextDouble(-0.5, 0.5), 0, 0, -1, 0, 0.6);
            }
            if (t % 2 == 0) {
                double y = HEIGHT - r.nextDouble(reach);
                Fx.ring(w, base.clone().add(0, y, 0), 0.75, 10, t * 0.4, Fx.dust(0xffffff, 0.8f));
            }
            if (t == 6) {
                // impact
                apply(player, heal, bonus(slot));
                w.playSound(feet, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 1.6f);
                w.playSound(feet, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 2f);
                Fx.burst(w, feet.clone().add(0, 0.2, 0), Particle.END_ROD, 40, 0.25);
                Fx.ring(w, feet.clone().add(0, 0.15, 0), 1.5, 30, 0, Fx.dust(0xb8f2e6, 1.4f));
                if (bonus(slot)) {
                    for (Player ally : Targets.allies(player, feet, 7)) {
                        if (ally.equals(player)) continue;
                        apply(ally, heal * 0.6, true);
                        Fx.beam(feet.clone().add(0, 1.2, 0), ally.getLocation().add(0, 1.2, 0), Material.WHITE_STAINED_GLASS, 0.15f, 8);
                    }
                }
            }
            if (t >= 6) star(w, feet.clone().add(0, 0.08, 0), 2.1, t * 0.05, bonus(slot));
            if (t == 24) {   // the column fades from the top down
                Fx.animate(glow, 10, hang(0.01f, HEIGHT), 1);
                Fx.animate(core, 10, hang(0.01f, HEIGHT), 1);
            }
            if (t >= 36) { glow.remove(); core.remove(); }
            return true;
        });
        return true;
    }

    /** A column whose top sits at the display's location and extends downward by `length`. */
    private static BlockDisplay column(Location top, Material m, float width) {
        return top.getWorld().spawn(top, BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(hang(width, 0.01f));
        });
    }

    private static Transformation hang(float width, float length) {
        return new Transformation(new Vector3f(-width / 2, -length, -width / 2), new Quaternionf(),
                new Vector3f(width, length, width), new Quaternionf());
    }

    /** Eight-point star drawn with straight edges, slowly turning. */
    private static void star(World w, Location c, double r, double rot, boolean gold) {
        int pts = 8;
        Location prev = null;
        for (int i = 0; i <= pts * 2; i++) {
            double a = rot + i * Math.PI / pts;
            double rr = i % 2 == 0 ? r : r * 0.42;
            Location p = c.clone().add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
            if (prev != null) Fx.line(w, prev, p, 0.3, Fx.dust(gold ? 0xffd166 : 0xb8f2e6, 0.8f));
            prev = p;
        }
    }

    private static void apply(Player target, double heal, boolean cleanse) {
        Targets.heal(target, heal);
        World w = target.getWorld();
        w.spawnParticle(Particle.HEART, target.getLocation().add(0, 2.1, 0), 4, 0.4, 0.2, 0.4, 0);
        if (cleanse) {
            for (PotionEffectType type : NEGATIVE) target.removePotionEffect(type);
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, target.getLocation().add(0, 1, 0), 30, 0.4, 0.7, 0.4, 0.25);
        }
    }
}
