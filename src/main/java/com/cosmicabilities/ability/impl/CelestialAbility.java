package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A soft pillar of starlight pours down from the sky onto you (always facing the camera so it looks volumetric),
 * light rains down inside it, a golden sigil burns into the ground and you're healed.
 */
public class CelestialAbility extends CosmicAbility {
    private static final List<PotionEffectType> NEGATIVE = List.of(
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
            PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.DARKNESS);
    private static final float HEIGHT = 24f;
    private static final int LIFE = 40, DROP = 7;

    /** A beam hanging from its entity (high in the sky) down `length` blocks, `width` wide. */
    private static org.bukkit.util.Transformation hanging(float width, float length) {
        return new org.bukkit.util.Transformation(new org.joml.Vector3f(0, -length / 2, 0), new org.joml.Quaternionf(),
                new org.joml.Vector3f(width, length, 1), new org.joml.Quaternionf());
    }

    public CelestialAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.CELESTIAL); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        double heal = dmg(10, slot);
        Location feet = player.getLocation();
        // a beam of light falls from the sky and lands on you: it grows downward from high above until it
        // strikes you, stays locked on you while you move, then pulls back up into the sky
        Location top = feet.clone().add(0, HEIGHT, 0);
        ItemDisplay glow = Fx.sprite(top, "beam_gold", 0.05f, Display.Billboard.VERTICAL);
        ItemDisplay core = Fx.sprite(top, "beam_white", 0.05f, Display.Billboard.VERTICAL);
        for (ItemDisplay d : new ItemDisplay[]{glow, core}) {
            d.setDisplayHeight(HEIGHT * 2 + 4);
            d.setTeleportDuration(2);
            d.setTransformation(hanging(0.6f, 0.01f));
        }
        Fx.animate(glow, DROP, hanging(3.0f, HEIGHT), 1);
        Fx.animate(core, DROP, hanging(0.9f, HEIGHT), 1);
        // retreat: the bottom lifts off you and the beam vanishes into the sky
        Fx.animate(glow, 8, hanging(1.2f, 0.01f), LIFE - 8);
        Fx.animate(core, 8, hanging(0.4f, 0.01f), LIFE - 8);
        Fx.removeLater(glow, LIFE + 2);
        Fx.removeLater(core, LIFE + 2);
        w.playSound(feet, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
        w.playSound(feet, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.6f);
        ItemDisplay sigil = Fx.flat(feet.clone().add(0, 0.05, 0), "sigil", 0.1f, 0);
        Fx.animate(sigil, 6, Fx.flatShape(4.5f, 0.6f), DROP);
        Fx.animate(sigil, LIFE, Fx.flatShape(4.2f, 3.0f), DROP + 7);
        Fx.animate(sigil, 6, Fx.flatShape(0.01f, 3.5f), LIFE + 4);
        Fx.removeLater(sigil, LIFE + 12);

        every(LIFE, t -> {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            // the beam stays locked on the caster
            Location on = player.isOnline() ? player.getLocation() : feet;
            if (t % 2 == 0 && player.isOnline()) {
                Location newTop = on.clone().add(0, HEIGHT, 0);
                newTop.setYaw(0);
                newTop.setPitch(0);
                if (glow.isValid()) glow.teleport(newTop);
                if (core.isValid()) core.teleport(newTop);
                feet.setX(on.getX()); feet.setY(on.getY()); feet.setZ(on.getZ());
            }
            // light rains down the column, only as far as the beam has reached
            double reach = HEIGHT * Math.min(1, t / (double) DROP);
            for (int i = 0; i < 7; i++) {
                double y = r.nextDouble(Math.max(0.1, reach));
                w.spawnParticle(Particle.END_ROD, feet.getX() + r.nextDouble(-0.6, 0.6), feet.getY() + HEIGHT - y,
                        feet.getZ() + r.nextDouble(-0.6, 0.6), 0, 0, -1, 0, 0.7);
            }
            if (t < DROP && t % 2 == 0) w.playSound(feet, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.2f + t * 0.1f);
            if (t % 3 == 0 && t >= DROP) {
                double y = r.nextDouble(2, HEIGHT);
                Fx.ring(w, feet.clone().add(0, y, 0), 0.8, 12, t * 0.4, Fx.dust(0xfff3c4, 0.8f));
            }
            if (t == DROP) {
                if (player.isOnline()) apply(player, heal, bonus(slot));
                w.playSound(feet, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 1.6f);
                w.playSound(feet, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 2f);
                Fx.flare(feet.clone().add(0, 1, 0), "flare_gold", 4f, 12);
                Fx.wave(feet, "ring_white", 0.5f, 7f, 10);
                Fx.burst(w, feet.clone().add(0, 0.3, 0), Particle.END_ROD, 40, 0.25);
                if (bonus(slot) && player.isOnline()) {
                    for (Player ally : Targets.allies(player, feet, 7)) {
                        if (ally.equals(player)) continue;
                        apply(ally, heal * 0.6, true);
                        Fx.ray(feet.clone().add(0, 1.2, 0), ally.getLocation().add(0, 1.2, 0), "beam_gold", 0.5f, 10);
                        Fx.flare(ally.getLocation().add(0, 1, 0), "flare_gold", 2f, 10);
                    }
                }
            }
            return true;
        });
        return true;
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
