package com.cosmicabilities.ability;

import com.cosmicabilities.item.CosmicMaterial;

import java.util.Locale;

/**
 * The 14 cosmic abilities. Each has a signature "bonus" mechanic (bonusName) built in,
 * and can be upgraded once, which shortens its cooldown.
 */
public enum AbilityType {
    SINGULARITY("Singularity", "Control / Damage", "#9b5de5", CosmicMaterial.VOID_SHARD, 180,
            "Opens a 3D black hole with a spinning accretion disk where you aim. It drags enemies into its core, crushing and slowing them.",
            "Event Horizon", "The black hole collapses at the end, then detonates and hurls everyone out."),
    SUPERNOVA("Supernova", "Burst Damage", "#ff8c42", CosmicMaterial.SOLAR_CORE, 180,
            "Charge for a moment, then explode. Heavy damage, knockback and burning around you.",
            "Stellar Cataclysm", "A second, wider aftershock goes off a second later."),
    METEOR("Meteor", "Dash / Offense", "#06d6a0", CosmicMaterial.ASTRAL_FRAGMENT, 180,
            "Dash forward like a comet, slamming through every enemy in your path. Rock walls burst out of the ground on both sides.",
            "Comet Breaker", "The dash ends in a crater slam that sets enemies on fire."),
    SOLAR("Solar", "Defense / Reflect", "#ffd166", CosmicMaterial.SOLAR_CORE, 180,
            "A solar shield that cuts incoming damage and reflects half of it back at your attacker.",
            "Solar Ascension", "Reflects all damage taken, burns attackers and bursts when it ends."),
    CRYOSTASIS("Cryostasis", "Freeze", "#8ecae6", CosmicMaterial.FROZEN_STARDUST, 180,
            "A frost nova that freezes enemies solid in ice. Frozen targets can't move or attack.",
            "Absolute Zero", "Freezes for longer, and the ice shatters for extra damage."),
    VOIDWALKER("Voidwalker", "Blink / Escape", "#7209b7", CosmicMaterial.VOID_SHARD, 180,
            "Blink forward. A void rift is left behind and detonates on anyone near it.",
            "Dimensional Rift", "A second rift detonates where you land and you briefly resist damage."),
    PULSAR("Pulsar", "Ranged Damage", "#4cc9f0", CosmicMaterial.ASTRAL_FRAGMENT, 180,
            "Fire an instant energy beam that pierces through two enemies.",
            "Quasar", "Fires three beams in a spread."),
    NEBULA("Nebula", "Support / Zone", "#f72585", CosmicMaterial.NEBULA_ESSENCE, 180,
            "Creates a nebula cloud. Allies inside get Speed and Regeneration; enemies glow and are weakened.",
            "Cosmic Veil", "Allies inside also resist damage, and mobs lose track of them."),
    ORBITAL("Orbital", "Area Damage", "#e0e1dd", CosmicMaterial.ASTRAL_FRAGMENT, 180,
            "Glowing orbs circle you and smash into anything that gets close.",
            "Planetary Orbit", "When it ends, every orb hunts down the nearest enemy."),
    CELESTIAL("Celestial", "Healing", "#b8f2e6", CosmicMaterial.NEBULA_ESSENCE, 180,
            "A beam of starlight heals you instantly.",
            "Astral Blessing", "Also heals nearby allies and removes negative effects."),
    PARADOX("Paradox", "Disruption", "#3a86ff", CosmicMaterial.FROZEN_STARDUST, 180,
            "A mirrored shockwave that inverts enemy players' movement controls.",
            "Time Fracture", "Inverts for longer and spins victims around to face away."),
    ENTROPY("Entropy", "Disruption", "#ef233c", CosmicMaterial.NEBULA_ESSENCE, 180,
            "A chaos pulse that scrambles enemy players' hotbars and disorients them.",
            "Total Collapse", "Also knocks the item out of their offhand (goodbye totem)."),
    MAGNETOSPHERE("Magnetosphere", "Forcefield", "#48cae4", CosmicMaterial.FROZEN_STARDUST, 180,
            "Raises a hexagon forcefield dome for 15 seconds. Anyone inside is trapped, anyone outside is kept out, and nobody else can place blocks, cobwebs or liquids inside.",
            "Repulsor Field", "Enemy projectiles bounce off and enemies who hit the wall get shocked."),
    UMBRA("Umbra", "Stealth / Ambush", "#ba68ff", CosmicMaterial.VOID_SHARD, 180,
            "Burrow underground, hidden from everyone - only a trail of crumbling ground gives you away - then erupt beneath your enemies.",
            "Shadow Eruption", "Burrow lasts longer and the eruption hits harder and blinds."),

    // ---- exclusive abilities: never rolled, only dropped by the Ender Dragon or a specific boss ----
    DRAGON_WRATH("Dragon's Wrath", "Legendary - Ender Dragon", "#c77dff", CosmicMaterial.VOID_SHARD, 180,
            "Summon the spirit of the Ender Dragon: its head roars beside you and breathes a cone of dragon fire that burns through enemies.",
            "Wing Gust", "A beat of spectral wings hurls everyone in front of you away and leaves a lingering breath cloud."),
    VOID_GRASP("Void Grasp", "Boss - Void Emperor", "#9d4edd", CosmicMaterial.VOID_SHARD, 180,
            "Clawed void hands burst from the ground under nearby enemies, dragging them toward you and blinding them.",
            "Emperor's Hold", "The hands hold them in place for a moment."),
    TITAN_FIST("Titan's Fist", "Boss - Solar Titan", "#ff7b00", CosmicMaterial.SOLAR_CORE, 180,
            "The Solar Titan's molten fist falls from the sky where you aim and smashes the ground, burning everyone near it.",
            "Twin Fists", "A second fist slams down a moment later."),
    GLACIAL_MAW("Glacial Maw", "Boss - Frost Leviathan", "#8ecae6", CosmicMaterial.FROZEN_STARDUST, 180,
            "The Frost Leviathan's head bursts out of the ice in front of you and snaps its jaws shut, freezing what it bites.",
            "Deep Freeze", "Bitten enemies are frozen solid for longer."),
    NEBULA_WINGS("Nebula Wings", "Boss - Nebula Serpent", "#f72585", CosmicMaterial.NEBULA_ESSENCE, 180,
            "Nebula wings sprout from your back and carry you into the sky; land with a starburst that hits everyone around you.",
            "Star Glide", "You glide slowly down and the landing hits harder."),
    STAR_OVERDRIVE("Star Overdrive", "Boss - Cosmic Destroyer", "#00e5ff", CosmicMaterial.ASTRAL_FRAGMENT, 180,
            "Overload a star core above you: three shockwaves blast outward, knocking enemies into the air.",
            "Supercharge", "Each shockwave is wider and the last one hits harder.");

    public enum Source { NORMAL, DRAGON, BOSS }

    /** Where this ability comes from: NORMAL abilities can be rolled; the rest only drop from the dragon / a boss. */
    public Source source() {
        return switch (this) {
            case DRAGON_WRATH -> Source.DRAGON;
            case VOID_GRASP, TITAN_FIST, GLACIAL_MAW, NEBULA_WINGS, STAR_OVERDRIVE -> Source.BOSS;
            default -> Source.NORMAL;
        };
    }

    public boolean exclusive() {
        return source() != Source.NORMAL;
    }

    /** The boss that drops this exclusive ability (null for dragon / normal). */
    public String bossId() {
        return switch (this) {
            case VOID_GRASP -> "void_emperor";
            case TITAN_FIST -> "solar_titan";
            case GLACIAL_MAW -> "frost_leviathan";
            case NEBULA_WINGS -> "nebula_serpent";
            case STAR_OVERDRIVE -> "cosmic_destroyer";
            default -> null;
        };
    }

    public static AbilityType forBoss(String bossId) {
        for (AbilityType t : values()) if (bossId.equals(t.bossId())) return t;
        return null;
    }

    public static java.util.List<AbilityType> rollable() {
        java.util.List<AbilityType> out = new java.util.ArrayList<>();
        for (AbilityType t : values()) if (!t.exclusive()) out.add(t);
        return out;
    }

    public final String displayName;
    public final String role;
    public final String color;
    public final CosmicMaterial material;
    public final double baseCooldown;
    public final String description;
    public final String bonusName;
    public final String bonusDesc;

    AbilityType(String displayName, String role, String color, CosmicMaterial material, double baseCooldown,
                String description, String bonusName, String bonusDesc) {
        this.displayName = displayName;
        this.role = role;
        this.color = color;
        this.material = material;
        this.baseCooldown = baseCooldown;
        this.description = description;
        this.bonusName = bonusName;
        this.bonusDesc = bonusDesc;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int rgb() {
        return Integer.parseInt(color.substring(1), 16);
    }

    public String colored() {
        return "<" + color + ">" + displayName + "</" + color + ">";
    }

    /** "Upgraded" / "Base" label for a tier (1 = base, 2 = upgraded). */
    public static String tierName(int tier) {
        return tier >= 2 ? "Upgraded" : "Base";
    }

    public static AbilityType fromId(String id) {
        if (id == null) return null;
        for (AbilityType t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
