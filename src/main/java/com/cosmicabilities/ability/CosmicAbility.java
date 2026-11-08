package com.cosmicabilities.ability;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.data.AbilitySlot;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Base class for every ability.
 * Each ability scales from level 1 to 10, picks up a specialization path at level 5,
 * and evolves at level 10. Damage/utility values are kept on a shared "power budget":
 * mobility and support abilities trade damage for utility.
 */
public abstract class CosmicAbility {
    protected final CosmicAbilitiesPlugin plugin;

    protected CosmicAbility(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public abstract void cast(Player player, AbilitySlot data);

    /** base + perLevel for every level above 1. */
    protected static double scale(double base, double perLevel, int level) {
        return base + perLevel * (level - 1);
    }

    protected static int ticks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }

    protected static void dust(World world, Location loc, Color color, float size, int count, double spread) {
        world.spawnParticle(Particle.DUST, loc, count, spread, spread, spread, 0, new Particle.DustOptions(color, size));
    }

    protected static void ring(World world, Location center, double radius, Particle particle, int points) {
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location p = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            world.spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }

    protected static void dustRing(World world, Location center, double radius, Color color, float size, int points) {
        Particle.DustOptions opts = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location p = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, opts);
        }
    }
}
