package com.cosmicabilities.ability;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.impl.*;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import com.cosmicabilities.fx.Fx;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Casting, per-slot cooldowns, timed combat buffs and the icon HUD. */
public class AbilityEngine {
    private static final char GLYPH_READY = '';
    private static final char GLYPH_COOLDOWN = '';

    /** Old v1 passive effects (infinite) that must be stripped from players. */
    private static final PotionEffectType[] OLD_PASSIVES = {
            PotionEffectType.NIGHT_VISION, PotionEffectType.STRENGTH, PotionEffectType.JUMP_BOOST,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.WATER_BREATHING, PotionEffectType.SPEED,
            PotionEffectType.HASTE, PotionEffectType.LUCK, PotionEffectType.RESISTANCE, PotionEffectType.HEALTH_BOOST};

    private final CosmicAbilitiesPlugin plugin;
    private final Map<AbilityType, CosmicAbility> abilities = new EnumMap<>(AbilityType.class);
    private final Map<UUID, long[]> cooldownEnd = new ConcurrentHashMap<>();
    private final Map<UUID, boolean[]> readyPinged = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hudPausedUntil = new ConcurrentHashMap<>();

    public AbilityEngine(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        abilities.put(AbilityType.SINGULARITY, new SingularityAbility(plugin));
        abilities.put(AbilityType.SUPERNOVA, new SupernovaAbility(plugin));
        abilities.put(AbilityType.METEOR, new MeteorAbility(plugin));
        abilities.put(AbilityType.SOLAR, new SolarAbility(plugin));
        abilities.put(AbilityType.CRYOSTASIS, new CryostasisAbility(plugin));
        abilities.put(AbilityType.VOIDWALKER, new VoidwalkerAbility(plugin));
        abilities.put(AbilityType.PULSAR, new PulsarAbility(plugin));
        abilities.put(AbilityType.NEBULA, new NebulaAbility(plugin));
        abilities.put(AbilityType.ORBITAL, new OrbitalAbility(plugin));
        abilities.put(AbilityType.CELESTIAL, new CelestialAbility(plugin));
        abilities.put(AbilityType.PARADOX, new ParadoxAbility(plugin));
        abilities.put(AbilityType.ENTROPY, new EntropyAbility(plugin));
        abilities.put(AbilityType.MAGNETOSPHERE, new MagnetosphereAbility(plugin));
        abilities.put(AbilityType.UMBRA, new UmbraAbility(plugin));
        abilities.put(AbilityType.DRAGON_WRATH, new DragonWrathAbility(plugin));
        abilities.put(AbilityType.VOID_GRASP, new VoidGraspAbility(plugin));
        abilities.put(AbilityType.TITAN_FIST, new TitanFistAbility(plugin));
        abilities.put(AbilityType.GLACIAL_MAW, new GlacialMawAbility(plugin));
        abilities.put(AbilityType.NEBULA_WINGS, new NebulaWingsAbility(plugin));
        abilities.put(AbilityType.STAR_OVERDRIVE, new StarOverdriveAbility(plugin));
    }

    /** The single upgrade cuts the cooldown by upgrades.cooldown-reduction (default 30%). */
    public double cooldownSeconds(AbilityType type, int tier) {
        double base = plugin.getConfig().getDouble("ability-settings." + type.id() + ".cooldown", type.baseCooldown);
        double cd = base * plugin.getConfig().getDouble("abilities.cooldown-multiplier", 1.0);
        if (plugin.events() != null) cd *= plugin.events().cooldownMultiplier();
        if (tier >= 2) cd *= 1 - Math.max(0, Math.min(0.9, plugin.getConfig().getDouble("upgrades.cooldown-reduction", 0.3)));
        return Math.max(plugin.getConfig().getDouble("abilities.minimum-cooldown", 4.0), cd);
    }

    private long[] ends(UUID uuid) {
        return cooldownEnd.computeIfAbsent(uuid, k -> new long[PlayerData.SLOTS]);
    }

    public double remainingCooldown(UUID uuid, int slot) {
        long[] e = cooldownEnd.get(uuid);
        if (e == null) return 0;
        return Math.max(0, (e[slot - 1] - System.currentTimeMillis()) / 1000.0);
    }

    public void tryCast(Player player, int slot) {
        if (!player.hasPermission("cosmicabilities.use")) return;
        if (plugin.cutscenes().isPlaying(player.getUniqueId())) return;
        PlayerData data = plugin.data().get(player.getUniqueId());
        AbilitySlot s = data == null ? null : data.slot(slot);
        if (s == null || s.isEmpty()) {
            notify(player, plugin.mm("<red>You have no ability in slot " + slot + "."));
            return;
        }
        if (plugin.status().isFrozen(player.getUniqueId())) {
            notify(player, plugin.mm("<aqua>You are frozen solid!"));
            return;
        }
        double remaining = remainingCooldown(player.getUniqueId(), slot);
        if (remaining > 0) {
            notify(player, plugin.mm(s.ability().colored() + " <gray>recharging: <white>" + String.format("%.1f", remaining) + "s"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f);
            return;
        }
        plugin.afk().exit(player, false);
        if (s.ability() != AbilityType.UMBRA && plugin.status().isBurrowed(player.getUniqueId())) {
            plugin.status().endBurrow(player, true);
        }
        boolean fired;
        try {
            fired = abilities.get(s.ability()).cast(player, s);
        } catch (Exception e) {
            plugin.getLogger().warning("Error casting " + s.ability() + " for " + player.getName() + ": " + e);
            fired = false;
        }
        if (!fired) return;
        ends(player.getUniqueId())[slot - 1] = System.currentTimeMillis() + (long) (cooldownSeconds(s.ability(), s.tier()) * 1000);
        readyPinged.computeIfAbsent(player.getUniqueId(), k -> new boolean[PlayerData.SLOTS])[slot - 1] = false;
        applyBuffs(player, s.ability());
        castRing(player, s);
    }

    /** Small ground ring in the ability colour every cast (each ability adds its own bigger effects). */
    private void castRing(Player player, AbilitySlot s) {
        Location feet = player.getLocation().add(0, 0.1, 0);
        Fx.ring(player.getWorld(), feet, 0.9, 18, 0, Fx.dust(s.ability().rgb(), 1.0f));
        if (s.upgraded()) Fx.ring(player.getWorld(), feet, 1.25, 14, 0.2, org.bukkit.Particle.END_ROD);
    }

    // ------------------------------------------------------------------ timed combat buffs

    /** Parses "effect:level:seconds" entries, e.g. "strength:1:6" = Strength I for 6 seconds. */
    public List<PotionEffect> buffsFor(AbilityType type) {
        List<PotionEffect> out = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("cast-buffs." + type.id())) {
            String[] parts = raw.trim().toLowerCase(Locale.ROOT).split(":");
            PotionEffectType effect = Registry.EFFECT.get(NamespacedKey.minecraft(parts[0]));
            if (effect == null) continue;
            int level = parts.length > 1 ? parseInt(parts[1], 1) : 1;
            if (effect == PotionEffectType.STRENGTH) level = Math.min(level, 1);   // no ability ever gives Strength II
            int seconds = parts.length > 2 ? parseInt(parts[2], 5) : 5;
            out.add(new PotionEffect(effect, seconds * 20, Math.max(0, level - 1), false, true, true));
        }
        return out;
    }

    public String buffText(AbilityType type) {
        StringBuilder sb = new StringBuilder();
        for (String raw : plugin.getConfig().getStringList("cast-buffs." + type.id())) {
            String[] parts = raw.trim().split(":");
            if (sb.length() > 0) sb.append(", ");
            StringBuilder name = new StringBuilder();
            for (String w : parts[0].split("_")) {
                if (!w.isEmpty()) name.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT)).append(' ');
            }
            int level = parts.length > 1 ? parseInt(parts[1], 1) : 1;
            if (parts[0].trim().equalsIgnoreCase("strength")) level = Math.min(level, 1);
            sb.append(name.toString().trim()).append(' ').append(roman(level));
            if (parts.length > 2) sb.append(" (").append(parts[2]).append("s)");
        }
        return sb.length() == 0 ? "None" : sb.toString();
    }

    private void applyBuffs(Player player, AbilityType type) {
        for (PotionEffect e : buffsFor(type)) player.addPotionEffect(e);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); };
    }

    /** Removes the infinite passive effects that v1.0 gave out. */
    public void stripOldPassives(Player p) {
        for (PotionEffectType t : OLD_PASSIVES) {
            PotionEffect e = p.getPotionEffect(t);
            if (e != null && e.isInfinite() && e.isAmbient()) p.removePotionEffect(t);
        }
    }

    // ------------------------------------------------------------------ HUD

    public void resetCooldown(UUID uuid) {
        cooldownEnd.remove(uuid);
    }

    public void notify(Player player, Component message) {
        hudPausedUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1500);
        player.sendActionBar(message);
    }

    public void tickHud() {
        if (!plugin.getConfig().getBoolean("hud.enabled", true)) return;
        boolean icons = plugin.getConfig().getBoolean("hud.use-icons", true);
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (plugin.afk().isAfk(id) || plugin.status().isBurrowed(id) || plugin.cutscenes().isPlaying(id)) continue;
            PlayerData data = plugin.data().get(id);
            if (data == null || !data.hasAnyAbility()) continue;

            StringBuilder hud = new StringBuilder();
            boolean[] pinged = readyPinged.computeIfAbsent(id, k -> new boolean[PlayerData.SLOTS]);
            for (int slot = 1; slot <= PlayerData.SLOTS; slot++) {
                AbilitySlot s = data.slot(slot);
                if (s.isEmpty()) continue;
                double remaining = remainingCooldown(id, slot);
                boolean ready = remaining <= 0;
                if (hud.length() > 0) hud.append("<reset>   ");
                if (icons) {
                    char glyph = (char) ((ready ? GLYPH_READY : GLYPH_COOLDOWN) + s.ability().ordinal());
                    hud.append("<white><font:cosmic:hud>").append(glyph).append("</font>");
                } else {
                    hud.append(ready ? s.ability().colored() : "<dark_gray>" + s.ability().displayName);
                }
                if (!ready) {
                    hud.append(" <gray>").append((int) Math.ceil(remaining)).append("s");
                } else if (!pinged[slot - 1]) {
                    pinged[slot - 1] = true;
                    long[] e = cooldownEnd.get(id);
                    if (e != null && e[slot - 1] != 0) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.8f);
                }
            }
            Long paused = hudPausedUntil.get(id);
            if (paused != null && paused > now) continue;
            player.sendActionBar(plugin.mm(hud.toString()));
        }
    }

    public void clear(UUID uuid) {
        readyPinged.remove(uuid);
        hudPausedUntil.remove(uuid);
    }
}
