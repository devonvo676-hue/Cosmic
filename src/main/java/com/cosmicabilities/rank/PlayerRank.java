package com.cosmicabilities.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

import java.util.Locale;

/** Store ranks, lowest to highest. Each one shows [NAME] beside the player's name and grows their ender chest. */
public enum PlayerRank {
    NONE("none", "", 0xffffff, Material.SHULKER_BOX, 3),
    STARLIGHT("starlight", "STARLIGHT", 0x7ee8fa, Material.LIGHT_BLUE_SHULKER_BOX, 4),
    COSMOS("cosmos", "COSMOS", 0xb15cff, Material.PURPLE_SHULKER_BOX, 5),
    SUPERNOVA("supernova", "SUPERNOVA", 0xffb703, Material.ORANGE_SHULKER_BOX, 6);

    public final String id;
    public final String tag;
    public final int rgb;
    public final Material box;
    /** default ender chest rows (config ranks.<id>.echest-rows overrides) */
    public final int defaultRows;

    PlayerRank(String id, String tag, int rgb, Material box, int defaultRows) {
        this.id = id; this.tag = tag; this.rgb = rgb; this.box = box; this.defaultRows = defaultRows;
    }

    public String hex() {
        return String.format("#%06x", rgb);
    }

    /** "[SUPERNOVA] " in the rank colour, bold */
    public Component prefix() {
        if (this == NONE) return Component.empty();
        return Component.text("[" + tag + "]", TextColor.color(rgb)).decorate(TextDecoration.BOLD)
                .append(Component.text(" ").decoration(TextDecoration.BOLD, false));
    }

    public String mini() {
        return this == NONE ? "<gray>No rank" : "<" + hex() + "><bold>[" + tag + "]</bold>";
    }

    public static PlayerRank parse(String s) {
        if (s == null) return NONE;
        for (PlayerRank r : values()) if (r.id.equalsIgnoreCase(s.trim()) || r.name().equalsIgnoreCase(s.trim())) return r;
        return null;
    }

    public String title() {
        return this == NONE ? "None" : tag.charAt(0) + tag.substring(1).toLowerCase(Locale.ROOT);
    }
}
