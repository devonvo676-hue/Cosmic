package com.cosmicabilities.keybind;

import org.bukkit.Material;

import java.util.Locale;

/** The ways a player can trigger an ability slot. /ability 1 and /ability 2 always work too. */
public enum Keybind {
    SNEAK_SWAP("Sneak + F", Material.SHIELD, "Hold sneak and press your swap-hands key (F)."),
    SWAP("F (swap hands)", Material.TOTEM_OF_UNDYING, "Press swap-hands (F). You lose the normal offhand swap."),
    SNEAK_LEFT_CLICK("Sneak + Left-click", Material.IRON_SWORD, "Hold sneak and left-click the air with an empty hand."),
    DOUBLE_SNEAK("Double-tap Sneak", Material.LEATHER_BOOTS, "Tap sneak twice quickly."),
    SNEAK_DROP("Sneak + Q", Material.HOPPER, "Hold sneak and press drop (Q) while holding any item. The item isn't dropped."),
    COMMAND_ONLY("Command only", Material.COMMAND_BLOCK, "Only /ability 1 or /ability 2.");

    public final String label;
    public final Material icon;
    public final String description;

    Keybind(String label, Material icon, String description) {
        this.label = label;
        this.icon = icon;
        this.description = description;
    }

    public static Keybind parse(String s, Keybind def) {
        if (s == null) return def;
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
