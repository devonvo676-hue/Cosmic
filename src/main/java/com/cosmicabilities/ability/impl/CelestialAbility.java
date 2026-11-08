package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class CelestialAbility extends CosmicAbility {
    private static final List<PotionEffectType> NEGATIVE = List.of(
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
            PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.DARKNESS);

    public CelestialAbility(CosmicAbilitiesPlugin plugin) { super(plugin); }

    @Override
    public void cast(Player player, AbilitySlot data) {
        int lvl = data.level();
        double heal = scale(3, 0.4, lvl);
        int speedTicks = ticks(scale(2, 0.2, lvl) + (data.isPathB() ? 2 : 0));
        int speedAmp = data.isPathB() ? 1 : 0;
        boolean evolved = data.evolved();

        World world = player.getWorld();
        Location c = player.getLocation().add(0, 1, 0);
        world.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
        world.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        ring(world, player.getLocation().add(0, 0.2, 0), 1.5, Particle.END_ROD, 24);
        dustRing(world, c, 2.5, Color.fromRGB(184, 242, 230), 1.6f, 30);

        apply(player, heal, speedTicks, speedAmp, evolved);

        if (data.isPathA()) {
            // Mender: nearby players (excluding caster) also get the pulse at 70%.
            for (Player other : player.getWorld().getPlayers()) {
                if (other.equals(player) || other.isDead()) continue;
                if (other.getLocation().distanceSquared(player.getLocation()) > 36) continue;
                apply(other, heal * 0.7, speedTicks, speedAmp, evolved);
            }
        }
    }

    private void apply(Player target, double heal, int speedTicks, int speedAmp, boolean evolved) {
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max == null ? 20 : max.getValue();
        target.setHealth(Math.min(maxHp, target.getHealth() + heal));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedTicks, speedAmp, true, true));
        target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 2, 0), 3, 0.4, 0.2, 0.4, 0);
        if (evolved) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0, true, true));
            for (PotionEffectType type : NEGATIVE) target.removePotionEffect(type);
            target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.2);
        }
    }
}
