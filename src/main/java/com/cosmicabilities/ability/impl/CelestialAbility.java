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
    private static final int LIFE = 34;

    public CelestialAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.CELESTIAL); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        double heal = dmg(10, slot);
        Location feet = player.getLocation();
        Location mid = feet.clone().add(0, HEIGHT / 2, 0);
        // wide soft glow + narrow bright core, both always turned toward the viewer (vertical billboard)
        ItemDisplay glow = Fx.sprite(mid, "beam_gold", 0.05f, Display.Billboard.VERTICAL);
        ItemDisplay core = Fx.sprite(mid, "beam_white", 0.05f, Display.Billboard.VERTICAL);
        glow.setDisplayHeight(HEIGHT + 4);
        core.setDisplayHeight(HEIGHT + 4);
        Fx.animate(glow, 5, Fx.sized(3.2f, HEIGHT, 0), 1);
        Fx.animate(core, 4, Fx.sized(1.0f, HEIGHT, 0), 1);
        // the beam is always cleaned up, whatever happens to the caster
        Fx.animate(glow, 8, Fx.sized(0.01f, HEIGHT, 0), LIFE - 8);
        Fx.animate(core, 8, Fx.sized(0.01f, HEIGHT, 0), LIFE - 10);
        Fx.removeLater(glow, LIFE + 2);
        Fx.removeLater(core, LIFE + 2);
        ItemDisplay sigil = Fx.flat(feet.clone().add(0, 0.05, 0), "sigil", 0.1f, 0);
        Fx.animate(sigil, 6, Fx.flatShape(4.5f, 0.6f), 4);
        Fx.animate(sigil, LIFE, Fx.flatShape(4.2f, 3.0f), 11);
        Fx.animate(sigil, 6, Fx.flatShape(0.01f, 3.5f), LIFE + 8);
        Fx.removeLater(sigil, LIFE + 16);
        w.playSound(feet, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.4f);
        w.playSound(feet, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);

        every(LIFE, t -> {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            // light rains down the column
            for (int i = 0; i < 7; i++) {
                double y = r.nextDouble(HEIGHT * Math.min(1, t / 5.0));
                w.spawnParticle(Particle.END_ROD, feet.getX() + r.nextDouble(-0.6, 0.6), feet.getY() + HEIGHT - y,
                        feet.getZ() + r.nextDouble(-0.6, 0.6), 0, 0, -1, 0, 0.7);
            }
            if (t % 3 == 0) {
                double y = r.nextDouble(2, HEIGHT);
                Fx.ring(w, feet.clone().add(0, y, 0), 0.8, 12, t * 0.4, Fx.dust(0xfff3c4, 0.8f));
            }
            if (t == 5) {
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
