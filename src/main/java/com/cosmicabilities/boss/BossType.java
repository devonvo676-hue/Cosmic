package com.cosmicabilities.boss;

import com.cosmicabilities.item.CosmicMaterial;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.EntityType;

import java.util.Locale;

public enum BossType {
    VOID_EMPEROR("Void Emperor", "#9d4edd", EntityType.WITHER_SKELETON, 2.45, CosmicMaterial.VOID_SHARD, BarColor.PURPLE),
    SOLAR_TITAN("Solar Titan", "#ffb703", EntityType.ZOGLIN, 4.2, CosmicMaterial.SOLAR_CORE, BarColor.YELLOW),
    FROST_LEVIATHAN("Frost Leviathan", "#8ecae6", EntityType.IRON_GOLEM, 2.0, CosmicMaterial.FROZEN_STARDUST, BarColor.BLUE),
    NEBULA_SERPENT("Nebula Drake", "#f72585", EntityType.HOGLIN, 2.5, CosmicMaterial.NEBULA_ESSENCE, BarColor.PINK),
    COSMIC_DESTROYER("Cosmic Destroyer", "#e0e1dd", EntityType.RAVAGER, 1.55, CosmicMaterial.ASTRAL_FRAGMENT, BarColor.WHITE);

    public final String displayName;
    public final String color;
    public final EntityType entityType;
    /** entity scale that makes the hitbox match the model at size 1.0 */
    public final double scale;
    public final CosmicMaterial drop;
    public final BarColor barColor;

    BossType(String displayName, String color, EntityType entityType, double scale, CosmicMaterial drop, BarColor barColor) {
        this.displayName = displayName;
        this.color = color;
        this.entityType = entityType;
        this.scale = scale;
        this.drop = drop;
        this.barColor = barColor;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String colored() {
        return "<" + color + "><bold>" + displayName + "</bold></" + color + ">";
    }

    public static BossType fromId(String id) {
        if (id == null) return null;
        for (BossType t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
