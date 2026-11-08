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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** A shaft of starlight falls on you, an eight-point star sigil turns under your feet, and you're healed. */
public class CelestialAbility extends CosmicAbility {
    private static final List<PotionEffectType> NEGATIVE = List.of(
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
            PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.DARKNESS);

    public CelestialAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.CELESTIAL); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        double heal = dmg(8, slot);
        w.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);

        every(24, t -> {
            if (!player.isOnline()) return false;
            Location feet = player.getLocation();
            if (t < 6) {  // the shaft of light falls from the sky
                double top = 7 - t * 1.15;
                for (double y = top; y < top + 1.4; y += 0.2) {
                    w.spawnParticle(Particle.END_ROD, feet.clone().add(0, y, 0), 1, 0.06, 0, 0.06, 0);
                }
                Fx.ring(w, feet.clone().add(0, top, 0), 0.35, 8, t, Fx.dust(0xffffff, 0.9f));
            }
            if (t == 6) {
                apply(player, heal, bonus(slot));
                w.playSound(feet, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
                w.playSound(feet, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 2f);
                Fx.burst(w, feet.clone().add(0, 1, 0), Particle.END_ROD, 25, 0.15);
                if (bonus(slot)) {
                    for (Player ally : Targets.allies(player, feet, 7)) {
                        if (!ally.equals(player)) apply(ally, heal * 0.6, true);
                    }
                }
            }
            if (t >= 6) star(w, feet.clone().add(0, 0.08, 0), 2.1, t * 0.05, bonus(slot));
            return true;
        });
        return true;
    }

    /** Eight-point star drawn with straight edges (not a circle), slowly turning. */
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
