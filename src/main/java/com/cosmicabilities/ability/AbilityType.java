package com.cosmicabilities.ability;

import com.cosmicabilities.item.CosmicMaterial;

import java.util.Locale;

/** The 10 spawnable cosmic abilities, with their identity, upgrade material, paths and evolution. */
public enum AbilityType {
    SINGULARITY("Singularity", "Control / Damage", "#9b5de5", CosmicMaterial.VOID_SHARD, 18,
            "Creates a miniature black hole that pulls nearby enemies inward and slows them.",
            "Low direct damage and a long cooldown.",
            "Crushing Core", "More damage inside the black hole and a stronger pull.",
            "Gravity Well", "Bigger radius and longer duration.",
            "Event Horizon", "The black hole collapses at the end, dealing a heavy burst."),
    SUPERNOVA("Supernova", "Burst Damage", "#ff8c42", CosmicMaterial.SOLAR_CORE, 16,
            "Releases a burst of stellar energy around you, damaging and knocking back enemies.",
            "Enemies must be close.",
            "Flare", "Higher burst damage.",
            "Shockwave", "Larger radius and much stronger knockback.",
            "Stellar Cataclysm", "A second, wider aftershock explodes one second later."),
    METEOR("Meteor", "Offensive / Mobility", "#06d6a0", CosmicMaterial.ASTRAL_FRAGMENT, 14,
            "Launches you forward on a meteor trail and damages enemies where you land.",
            "Predictable movement and landing spot.",
            "Impact", "Much stronger landing damage.",
            "Trailblazer", "Longer launch, and your trail sets enemies on fire.",
            "Comet Breaker", "Wider impact crater that ignites everything it hits."),
    SOLAR("Solar", "Defence / Support", "#ffd166", CosmicMaterial.SOLAR_CORE, 18,
            "Surrounds you with solar energy that reduces incoming damage, then bursts outward.",
            "Limited offensive potential.",
            "Radiance", "Larger and stronger end burst.",
            "Aegis", "Higher damage reduction.",
            "Solar Ascension", "Regeneration while shielded; attackers are set ablaze."),
    CRYOSTASIS("Cryostasis", "Control", "#8ecae6", CosmicMaterial.FROZEN_STARDUST, 15,
            "Releases an expanding freezing wave that slows every enemy it touches.",
            "Low damage output.",
            "Glacier", "Stronger slow.",
            "Frostbite", "The wave deals real damage and freezes targets.",
            "Absolute Zero", "Enemies are frozen nearly solid for a moment before the slow."),
    VOIDWALKER("Voidwalker", "Mobility / Escape", "#7209b7", CosmicMaterial.VOID_SHARD, 12,
            "Teleports you in the direction you face, leaving a void explosion behind.",
            "Minimal damage and no protection.",
            "Phase", "Much longer teleport distance.",
            "Rupture", "Stronger void explosions at both ends of the blink.",
            "Dimensional Rift", "The rift also detonates at your destination and briefly shields you."),
    PULSAR("Pulsar", "Ranged Damage", "#4cc9f0", CosmicMaterial.ASTRAL_FRAGMENT, 13,
            "Fires a concentrated beam of cosmic energy that damages the first enemy hit.",
            "Requires aiming and can be dodged.",
            "Lance", "The beam pierces through up to 3 enemies.",
            "Burst", "The beam explodes on impact, splashing nearby enemies.",
            "Quasar", "Fires three beams in a spread."),
    NEBULA("Nebula", "Support / Utility", "#f72585", CosmicMaterial.NEBULA_ESSENCE, 17,
            "Creates a cosmic mist around you that speeds up allies inside it.",
            "Little direct damage.",
            "Drift", "Stronger speed boost for allies.",
            "Shroud", "Enemies in the mist glow and are weakened.",
            "Cosmic Veil", "Allies in the mist gain Resistance and mobs lose track of them."),
    ORBITAL("Orbital", "Balanced / Area Damage", "#e0e1dd", CosmicMaterial.ASTRAL_FRAGMENT, 16,
            "Summons orbs of energy that circle you and damage enemies who get close.",
            "Short effective range.",
            "Bastion", "Two extra orbs.",
            "Ring", "Wider orbit.",
            "Planetary Orbit", "When it ends, every orb launches outward as a projectile."),
    CELESTIAL("Celestial", "Utility / Recovery", "#b8f2e6", CosmicMaterial.NEBULA_ESSENCE, 18,
            "Releases a cosmic pulse that restores health and grants a burst of speed.",
            "No damage or crowd control.",
            "Mender", "The pulse also heals nearby allies.",
            "Stride", "Stronger and longer speed boost.",
            "Astral Blessing", "Grants Regeneration and cleanses negative effects.");

    public final String displayName;
    public final String role;
    public final String color;
    public final CosmicMaterial material;
    public final double baseCooldown;
    public final String description;
    public final String weakness;
    public final String pathAName, pathADesc, pathBName, pathBDesc;
    public final String evolutionName, evolutionDesc;

    AbilityType(String displayName, String role, String color, CosmicMaterial material, double baseCooldown,
                String description, String weakness,
                String pathAName, String pathADesc, String pathBName, String pathBDesc,
                String evolutionName, String evolutionDesc) {
        this.displayName = displayName;
        this.role = role;
        this.color = color;
        this.material = material;
        this.baseCooldown = baseCooldown;
        this.description = description;
        this.weakness = weakness;
        this.pathAName = pathAName;
        this.pathADesc = pathADesc;
        this.pathBName = pathBName;
        this.pathBDesc = pathBDesc;
        this.evolutionName = evolutionName;
        this.evolutionDesc = evolutionDesc;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Name in its color, using the evolved name at level 10. */
    public String colored(int level) {
        String name = level >= 10 ? evolutionName : displayName;
        return "<" + color + ">" + name + "</" + color + ">";
    }

    public String pathName(char path) {
        return path == 'A' ? pathAName : path == 'B' ? pathBName : "None";
    }

    public static AbilityType fromId(String id) {
        if (id == null) return null;
        for (AbilityType t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
