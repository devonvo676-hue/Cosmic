package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import org.bukkit.entity.Player;

/**
 * v2tou's personal items and abilities. Hard-coded on purpose: no config setting, command or admin panel
 * can give them to anyone else.
 */
public final class Owner {
    public static final String NAME = "v2tou";

    private Owner() {}

    public static boolean allowed(CosmicAbilitiesPlugin plugin, Player p) {
        return p != null && NAME.equalsIgnoreCase(p.getName());
    }
}
