package com.cosmicabilities.ability;

import com.cosmicabilities.item.CosmicMaterial;

import java.util.Locale;

/**
 * The 14 cosmic abilities. Each has a signature "bonus" mechanic (bonusName) built in,
 * and can be upgraded once, which shortens its cooldown.
 */
public enum AbilityType {
    SINGULARITY("Singularity", "Control / Damage", "#9b5de5", CosmicMaterial.VOID_SHARD, 18,
            "Opens a 3D black hole with a spinning accretion disk where you aim. It drags enemies into its core, crushing and slowing them.",
            "Event Horizon", "The black hole collapses at the end, then detonates and hurls everyone out."),
    SUPERNOVA("Supernova", "Burst Damage", "#ff8c42", CosmicMaterial.SOLAR_CORE, 15,
            "Charge for a moment, then explode. Heavy damage, knockback and burning around you.",
            "Stellar Cataclysm", "A second, wider aftershock goes off a second later."),
    METEOR("Meteor", "Dash / Offense", "#06d6a0", CosmicMaterial.ASTRAL_FRAGMENT, 10,
            "Dash forward like a comet, slamming through every enemy in your path. Rock walls burst out of the ground on both sides.",
            "Comet Breaker", "The dash ends in a crater slam that sets enemies on fire."),
    SOLAR("Solar", "Defense / Reflect", "#ffd166", CosmicMaterial.SOLAR_CORE, 20,
            "A solar shield that cuts incoming damage and reflects half of it back at your attacker.",
            "Solar Ascension", "Reflects all damage taken, burns attackers and bursts when it ends."),
    CRYOSTASIS("Cryostasis", "Freeze", "#8ecae6", CosmicMaterial.FROZEN_STARDUST, 18,
            "A frost nova that freezes enemies solid in ice. Frozen targets can't move or attack.",
            "Absolute Zero", "Freezes for longer, and the ice shatters for extra damage."),
    VOIDWALKER("Voidwalker", "Blink / Escape", "#7209b7", CosmicMaterial.VOID_SHARD, 9,
            "Blink forward. A void rift is left behind and detonates on anyone near it.",
            "Dimensional Rift", "A second rift detonates where you land and you briefly resist damage."),
    PULSAR("Pulsar", "Ranged Damage", "#4cc9f0", CosmicMaterial.ASTRAL_FRAGMENT, 12,
            "Fire an instant energy beam that pierces through two enemies.",
            "Quasar", "Fires three beams in a spread."),
    NEBULA("Nebula", "Support / Zone", "#f72585", CosmicMaterial.NEBULA_ESSENCE, 22,
            "Creates a nebula cloud. Allies inside get Speed and Regeneration; enemies glow and are weakened.",
            "Cosmic Veil", "Allies inside also resist damage, and mobs lose track of them."),
    ORBITAL("Orbital", "Area Damage", "#e0e1dd", CosmicMaterial.ASTRAL_FRAGMENT, 18,
            "Glowing orbs circle you and smash into anything that gets close.",
            "Planetary Orbit", "When it ends, every orb hunts down the nearest enemy."),
    CELESTIAL("Celestial", "Healing", "#b8f2e6", CosmicMaterial.NEBULA_ESSENCE, 24,
            "A beam of starlight heals you instantly.",
            "Astral Blessing", "Also heals nearby allies and removes negative effects."),
    PARADOX("Paradox", "Disruption", "#3a86ff", CosmicMaterial.FROZEN_STARDUST, 20,
            "A mirrored shockwave that inverts enemy players' movement controls.",
            "Time Fracture", "Inverts for longer and spins victims around to face away."),
    ENTROPY("Entropy", "Disruption", "#ef233c", CosmicMaterial.NEBULA_ESSENCE, 22,
            "A chaos pulse that scrambles enemy players' hotbars and disorients them.",
            "Total Collapse", "Also knocks the item out of their offhand (goodbye totem)."),
    MAGNETOSPHERE("Magnetosphere", "Forcefield", "#48cae4", CosmicMaterial.FROZEN_STARDUST, 40,
            "Raises a hexagon forcefield dome for 15 seconds. Anyone inside is trapped, anyone outside is kept out, and nobody else can place blocks, cobwebs or liquids inside.",
            "Repulsor Field", "Enemy projectiles bounce off and enemies who hit the wall get shocked."),
    UMBRA("Umbra", "Stealth / Ambush", "#ba68ff", CosmicMaterial.VOID_SHARD, 20,
            "Burrow underground, hidden from everyone - only a trail of crumbling ground gives you away - then erupt beneath your enemies.",
            "Shadow Eruption", "Burrow lasts longer and the eruption hits harder and blinds.");

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
