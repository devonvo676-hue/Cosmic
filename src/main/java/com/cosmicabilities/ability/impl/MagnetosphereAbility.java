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
 * Forcefield dome for 15 seconds at the cast spot. Nobody but the caster can place blocks, cobwebs or liquids inside,
 * and any cobwebs already inside dissolve. Tier III: enemies are pushed out and their projectiles bounce back.
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
        // the dome assembles itself ring by ring from the ground up (hidden from the caster unless configured)
        java.util.List<Player> viewers = Fx.viewers(center, 80,
                plugin.getConfig().getBoolean("magnetosphere.caster-sees-dome", false) ? null : player.getUniqueId());
        every(10, t -> {
            double lat = t / 10.0 * Math.PI / 2;
            double rr = Math.cos(lat) * radius;
            double y = center.getY() + Math.sin(lat) * radius;
            int pts = (int) (rr * 9) + 4;
            for (int i = 0; i < pts; i++) {
                double ang = t * 0.3 + i * Math.PI * 2 / pts;
                Fx.dustFor(viewers, center.getX() + Math.cos(ang) * rr, y, center.getZ() + Math.sin(ang) * rr, Fx.dust(0xcaf0f8, 1.2f));
            }
            if (t % 2 == 0) w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 0.8f + t * 0.1f);
            return true;
        });
        status().addField(center.clone(), radius, seconds * 20, player.getUniqueId(), bonus(slot));
        return true;
    }
}
