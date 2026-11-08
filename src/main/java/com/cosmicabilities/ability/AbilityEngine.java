package com.cosmicabilities.ability;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.impl.*;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Handles casting, per-slot cooldowns, the icon HUD, and short-lived ability state (shields, fall immunity). */
public class AbilityEngine {
    public record Shield(double reduction, long expiresAt, boolean evolved) {}

    /** Resource-pack glyphs (see the resource pack's assets/cosmic/font/hud.json). */
    private static final char GLYPH_READY = '';
    private static final char GLYPH_COOLDOWN = '';

    private final CosmicAbilitiesPlugin plugin;
    private final Map<AbilityType, CosmicAbility> abilities = new EnumMap<>(AbilityType.class);
    /** uuid -> [slot1 end millis, slot2 end millis] */
    private final Map<UUID, long[]> cooldownEnd = new ConcurrentHashMap<>();
    private final Map<UUID, boolean[]> readyPinged = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hudPausedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Shield> shields = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> fallImmune = ConcurrentHashMap.newKeySet();
    /** Passive effect types this plugin applied last tick, per player, so they can be removed when unequipped. */
    private final Map<UUID, Set<PotionEffectType>> appliedPassives = new ConcurrentHashMap<>();

    /** Slot number currently being cast (abilities run synchronously), so refundCooldown knows which slot. */
    private int castingSlot = 1;

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
    }

    /** Cooldown in seconds: base, minus 1.5s at levels 4 and 8, Voidwalker gets extra reduction per level. */
    public double cooldownSeconds(AbilityType type, int level) {
        double cd = type.baseCooldown * plugin.getConfig().getDouble("abilities.cooldown-multiplier", 1.0);
        if (level >= 4) cd -= 1.5;
        if (level >= 8) cd -= 1.5;
        if (type == AbilityType.VOIDWALKER) cd -= 0.25 * (level - 1);
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

    /** Casts the ability equipped in slot (1 or 2). */
    public void tryCast(Player player, int slot) {
        if (!player.hasPermission("cosmicabilities.use")) return;
        PlayerData data = plugin.data().get(player.getUniqueId());
        AbilitySlot s = data == null ? null : data.slot(slot);
        if (s == null || s.isEmpty()) {
            notify(player, plugin.mm("<red>You have no ability in slot " + slot + "."));
            return;
        }
        double remaining = remainingCooldown(player.getUniqueId(), slot);
        if (remaining > 0) {
            notify(player, plugin.mm(s.ability().colored(s.level()) + " <gray>recharging: <white>"
                    + String.format("%.1f", remaining) + "s"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f);
            return;
        }
        long cdMillis = (long) (cooldownSeconds(s.ability(), s.level()) * 1000);
        ends(player.getUniqueId())[slot - 1] = System.currentTimeMillis() + cdMillis;
        readyPinged.computeIfAbsent(player.getUniqueId(), k -> new boolean[PlayerData.SLOTS])[slot - 1] = false;
        castingSlot = slot;
        try {
            abilities.get(s.ability()).cast(player, s);
            castFx(player, s);
        } catch (Exception e) {
            plugin.getLogger().warning("Error casting " + s.ability() + " for " + player.getName() + ": " + e);
            refundCooldown(player.getUniqueId());
        } finally {
            castingSlot = 1;
        }
    }

    /** Lets an ability give back the cooldown when it failed to do anything. */
    public void refundCooldown(UUID uuid) {
        long[] e = cooldownEnd.get(uuid);
        if (e != null) e[castingSlot - 1] = 0;
    }

    public void resetCooldown(UUID uuid) {
        cooldownEnd.remove(uuid);
    }

    /** Shows a short action-bar message, pausing the icon HUD so it isn't overwritten immediately. */
    public void notify(Player player, Component message) {
        hudPausedUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1500);
        player.sendActionBar(message);
    }

    /** Called every few ticks: draws the Infuse-style ability icons above the hotbar. */
    public void tickHud() {
        if (!plugin.getConfig().getBoolean("hud.enabled", true)) return;
        boolean icons = plugin.getConfig().getBoolean("hud.use-icons", true);
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            PlayerData data = plugin.data().get(id);
            if (data == null || !data.hasAnyAbility()) continue;

            StringBuilder hud = new StringBuilder();
            boolean[] pinged = readyPinged.computeIfAbsent(id, k -> new boolean[PlayerData.SLOTS]);
            for (int slot = 1; slot <= PlayerData.SLOTS; slot++) {
                AbilitySlot s = data.slot(slot);
                if (s.isEmpty()) continue;
                double remaining = remainingCooldown(id, slot);
                boolean ready = remaining <= 0;
                if (hud.length() > 0) hud.append("<reset>  ");
                if (icons) {
                    char glyph = (char) ((ready ? GLYPH_READY : GLYPH_COOLDOWN) + s.ability().ordinal());
                    hud.append("<white><font:cosmic:hud>").append(glyph).append("</font>");
                } else {
                    hud.append(ready ? s.ability().colored(s.level()) : "<dark_gray>" + s.ability().displayName);
                }
                if (!ready) {
                    hud.append(" <gray>").append((int) Math.ceil(remaining)).append("s");
                } else if (!pinged[slot - 1]) {
                    pinged[slot - 1] = true;
                    long[] e = cooldownEnd.get(id);
                    if (e != null && e[slot - 1] != 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.8f);
                    }
                }
            }
            Long paused = hudPausedUntil.get(id);
            if (paused != null && paused > now) continue;
            player.sendActionBar(plugin.mm(hud.toString()));
        }
    }

    /** Shared activation effect: a helix of the ability's color spirals up around the caster. */
    private void castFx(Player player, AbilitySlot s) {
        Color color = Color.fromRGB(Integer.parseInt(s.ability().color.substring(1), 16));
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.3f);
        boolean evolved = s.evolved();
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.9f);
        if (evolved) world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t > 10 || !player.isOnline()) { cancel(); return; }
                Location base = player.getLocation();
                for (int strand = 0; strand < 2; strand++) {
                    double a = t * 0.7 + strand * Math.PI;
                    Location p = base.clone().add(Math.cos(a) * 0.8, t * 0.2, Math.sin(a) * 0.8);
                    world.spawnParticle(Particle.DUST, p, 2, 0.02, 0.02, 0.02, 0, dust);
                    if (evolved) world.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---------------------------------------------------------------- passives

    /** Parses "effect:amplifier" from config (e.g. "night_vision:0"). */
    public PotionEffect passiveFor(AbilityType type) {
        String raw = plugin.getConfig().getString("passives." + type.id(), "");
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("none")) return null;
        String[] parts = raw.trim().toLowerCase(Locale.ROOT).split(":");
        PotionEffectType effect = Registry.EFFECT.get(NamespacedKey.minecraft(parts[0]));
        if (effect == null) return null;
        int amp = 0;
        if (parts.length > 1) {
            try { amp = Math.max(0, Integer.parseInt(parts[1])); } catch (NumberFormatException ignored) { }
        }
        return new PotionEffect(effect, PotionEffect.INFINITE_DURATION, amp, true, false, true);
    }

    public String passiveName(AbilityType type) {
        String raw = plugin.getConfig().getString("passives." + type.id(), "");
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("none")) return "None";
        String[] parts = raw.split(":");
        StringBuilder out = new StringBuilder();
        for (String w : parts[0].split("_")) {
            if (w.isEmpty()) continue;
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT)).append(' ');
        }
        int amp = 0;
        if (parts.length > 1) {
            try { amp = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) { }
        }
        return out.toString().trim() + (amp > 0 ? " " + (amp + 1) : "");
    }

    /** Runs every second: keeps each equipped ability's passive effect active, removes ones no longer equipped. */
    public void tickPassives() {
        boolean enabled = plugin.getConfig().getBoolean("passives.enabled", true);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            PlayerData data = plugin.data().get(id);
            Map<PotionEffectType, PotionEffect> desired = new HashMap<>();
            if (enabled && data != null && !player.isDead()) {
                for (int i = 1; i <= PlayerData.SLOTS; i++) {
                    AbilitySlot s = data.slot(i);
                    if (s.isEmpty()) continue;
                    PotionEffect e = passiveFor(s.ability());
                    if (e == null) continue;
                    PotionEffect have = desired.get(e.getType());
                    if (have == null || have.getAmplifier() < e.getAmplifier()) desired.put(e.getType(), e);
                }
            }
            Set<PotionEffectType> previous = appliedPassives.getOrDefault(id, Set.of());
            for (PotionEffectType type : previous) {
                if (desired.containsKey(type)) continue;
                PotionEffect cur = player.getPotionEffect(type);
                // Only remove what we gave (ambient + infinite) - never a potion the player drank.
                if (cur != null && cur.isAmbient() && cur.isInfinite()) {
                    player.removePotionEffect(type);
                }
            }
            for (PotionEffect e : desired.values()) {
                PotionEffect cur = player.getPotionEffect(e.getType());
                // Added once (infinite) so Night Vision never flickers and Health Boost hearts aren't reset.
                boolean apply = cur == null || cur.getAmplifier() < e.getAmplifier()
                        || (cur.getAmplifier() == e.getAmplifier() && !cur.isInfinite());
                if (apply) player.addPotionEffect(e);
            }
            appliedPassives.put(id, new HashSet<>(desired.keySet()));
        }
    }

    public void setShield(UUID uuid, Shield shield) { shields.put(uuid, shield); }
    public void clearShield(UUID uuid) { shields.remove(uuid); }

    public Shield activeShield(UUID uuid) {
        Shield s = shields.get(uuid);
        if (s == null) return null;
        if (s.expiresAt() < System.currentTimeMillis()) {
            shields.remove(uuid);
            return null;
        }
        return s;
    }

    public void addFallImmunity(UUID uuid) { fallImmune.add(uuid); }
    public void removeFallImmunity(UUID uuid) { fallImmune.remove(uuid); }
    public boolean isFallImmune(UUID uuid) { return fallImmune.contains(uuid); }

    public void clear(UUID uuid) {
        shields.remove(uuid);
        fallImmune.remove(uuid);
        readyPinged.remove(uuid);
        hudPausedUntil.remove(uuid);
        appliedPassives.remove(uuid);
    }
}
