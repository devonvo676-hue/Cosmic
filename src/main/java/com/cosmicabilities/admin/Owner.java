package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import org.bukkit.entity.Player;

/** Items only specific people may use (owner-items.players in config; v2tou by default). */
public final class Owner {
    private Owner() {}

    public static boolean allowed(CosmicAbilitiesPlugin plugin, Player p) {
        if (p == null) return false;
        for (String name : plugin.getConfig().getStringList("owner-items.players")) {
            if (name.equalsIgnoreCase(p.getName())) return true;
        }
        return false;
    }
}
