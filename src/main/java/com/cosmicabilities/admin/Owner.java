package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * v2tou's personal items and abilities. v2tou is built in; only v2tou can add or remove other players
 * (/adminpanel -> v2tou's Stuff). The list isn't shown in Full Config, so other admins can't change it in game.
 */
public final class Owner {
    public static final String NAME = "v2tou";
    private static final String PATH = "owner-items.also-allowed";

    private Owner() {}

    /** True only for v2tou himself. */
    public static boolean isOwner(Player p) {
        return p != null && NAME.equalsIgnoreCase(p.getName());
    }

    /** v2tou, or someone v2tou has let use his stuff. */
    public static boolean allowed(CosmicAbilitiesPlugin plugin, Player p) {
        if (p == null) return false;
        if (isOwner(p)) return true;
        for (String n : plugin.getConfig().getStringList(PATH)) if (n.equalsIgnoreCase(p.getName())) return true;
        return false;
    }

    public static List<String> extras(CosmicAbilitiesPlugin plugin) {
        return new ArrayList<>(plugin.getConfig().getStringList(PATH));
    }

    public static void setExtras(CosmicAbilitiesPlugin plugin, Player by, List<String> names) {
        if (!isOwner(by)) return;
        plugin.getConfig().set(PATH, names);
        plugin.saveConfig();
    }
}
