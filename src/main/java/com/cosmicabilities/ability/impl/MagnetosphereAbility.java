package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * A dome of translucent hexagon panels for 15 seconds at the cast spot. Whoever is inside when it goes up is trapped
 * until it drops; nobody outside can get in (walking, pearls, teleports). Nobody but the caster can place blocks,
 * cobwebs or liquids inside, and cobwebs already inside dissolve.
 * Repulsor (bonus): enemy projectiles bounce off and enemies who hit the wall are shocked.
 */
public class MagnetosphereAbility extends CosmicAbility {
    public MagnetosphereAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.MAGNETOSPHERE); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location center = player.getLocation();
        World w = center.getWorld();
        double radius = plugin.getConfig().getDouble("magnetosphere.radius", 6);
        int seconds = plugin.getConfig().getInt("magnetosphere.duration-seconds", 15);

        // dissolve cobwebs (and other web-like traps) already inside
        int r = (int) Math.ceil(radius);
        int cleared = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (x * x + y * y + z * z > radius * radius) continue;
            Block b = center.getBlock().getRelative(x, y, z);
            if (b.getType() == Material.COBWEB) {
                w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0, b.getBlockData());
                b.setType(Material.AIR);
                cleared++;
            }
        }
        if (cleared > 0) w.playSound(center, Sound.BLOCK_WOOL_BREAK, 1f, 0.8f);

        w.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 1.2f);
        w.playSound(center, Sound.BLOCK_CONDUIT_ACTIVATE, 1.2f, 1f);
        // the dome of hexagon panels builds itself from the ground up (see StatusManager.buildDome)
        every(12, t -> {
            if (t % 2 == 0) w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.7f, 0.8f + t * 0.08f);
            return true;
        });
        status().addField(center.clone(), radius, seconds * 20, player.getUniqueId(), bonus(slot));
        // glowing boundary ring on the ground for the whole duration
        if (!plugin.getConfig().getBoolean("magnetosphere.hide-from-caster", false)) {
            org.bukkit.entity.ItemDisplay edge = Fx.flat(center.clone().add(0, 0.06, 0), "ring_cyan", 0.5f, 0);
            Fx.animate(edge, 10, Fx.flatShape((float) radius * 2.3f, 0), 1);
            Fx.animate(edge, 10, Fx.flatShape(0.01f, 1f), seconds * 20L);
            Fx.removeLater(edge, seconds * 20L + 12);
            Fx.flare(center.clone().add(0, 1.2, 0), "flare_cyan", 4f, 12);
        }
        return true;
    }
}
