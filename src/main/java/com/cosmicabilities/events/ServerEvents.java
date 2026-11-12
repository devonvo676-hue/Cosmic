package com.cosmicabilities.events;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Daily events the admins switch on from /adminpanel -> Events: double kill money, double AFK tokens,
 * ability frenzy (half cooldowns), supply drop rain, and boss fights. Active events show as a bar at the top.
 */
public class ServerEvents {
    public enum Type {
        DOUBLE_KILLS("2x Kill Money", "<gold>", Material.GOLD_INGOT, "Every kill pays double into team banks / cash."),
        DOUBLE_AFK("2x AFK Tokens", "<light_purple>", Material.AMETHYST_CLUSTER, "AFK players earn twice the tokens."),
        ABILITY_FRENZY("Ability Frenzy", "<aqua>", Material.NETHER_STAR, "Every ability cooldown is halved."),
        SUPPLY_RAIN("Supply Drop Rain", "<green>", Material.CHEST, "A supply drop falls near a random player every few minutes."),
        BOSS_FIGHT("Boss Fight", "<red>", Material.WITHER_SKELETON_SKULL, "Pick a boss and start the fight (opens the boss panel).");

        public final String title, color, desc;
        public final Material icon;
        Type(String title, String color, Material icon, String desc) { this.title = title; this.color = color; this.icon = icon; this.desc = desc; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final Map<Type, Long> active = new EnumMap<>(Type.class);
    private final Map<Type, BossBar> bars = new EnumMap<>(Type.class);
    private long lastRain = 0;

    public ServerEvents(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive(Type t) {
        Long end = active.get(t);
        return end != null && end > System.currentTimeMillis();
    }

    public long endsAt(Type t) { return active.getOrDefault(t, 0L); }

    public double killMultiplier() { return isActive(Type.DOUBLE_KILLS) ? 2 : 1; }
    public int afkMultiplier() { return isActive(Type.DOUBLE_AFK) ? 2 : 1; }
    public double cooldownMultiplier() { return isActive(Type.ABILITY_FRENZY) ? 0.5 : 1; }

    public void start(Type t, int minutes) {
        active.put(t, System.currentTimeMillis() + minutes * 60_000L);
        BossBar bar = bars.computeIfAbsent(t, k -> BossBar.bossBar(plugin.mm(""), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            p.showTitle(Title.title(plugin.mm(t.color + "<bold>" + t.title.toUpperCase()), plugin.mm("<gray>" + t.desc),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(700))));
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1.2f);
        }
        Bukkit.broadcast(plugin.mm(plugin.prefix() + t.color + "<bold>" + t.title + "</bold> <gray>is on for <white>" + minutes + " minutes<gray>! " + t.desc));
        tick();
    }

    public void stop(Type t) {
        active.remove(t);
        BossBar bar = bars.remove(t);
        if (bar != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        Bukkit.broadcast(plugin.mm(plugin.prefix() + t.color + t.title + " <gray>has ended."));
    }

    /** Every second. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Type t : new ArrayList<>(active.keySet())) {
            long end = active.get(t);
            if (end <= now) { stop(t); continue; }
            BossBar bar = bars.get(t);
            if (bar == null) continue;
            long left = (end - now) / 1000;
            bar.name(plugin.mm(t.color + "<bold>" + t.title + "</bold> <gray>- " + (left / 60) + "m " + (left % 60) + "s left"));
            for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
        }
        if (isActive(Type.SUPPLY_RAIN)) {
            long every = plugin.getConfig().getLong("events.supply-rain-minutes", 5) * 60_000L;
            if (now - lastRain >= every) {
                lastRain = now;
                List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                online.removeIf(p -> p.getWorld().getEnvironment() != World.Environment.NORMAL);
                if (!online.isEmpty()) {
                    Player near = online.get(ThreadLocalRandom.current().nextInt(online.size()));
                    double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2), r = ThreadLocalRandom.current().nextDouble(25, 60);
                    Location at = near.getLocation().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    plugin.supplyDrops().call(at, null);
                }
            }
        }
    }

    public List<Type> activeEvents() {
        List<Type> out = new ArrayList<>();
        for (Type t : Type.values()) if (isActive(t)) out.add(t);
        return out;
    }

    public void shutdown() {
        for (BossBar bar : bars.values()) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }
}
