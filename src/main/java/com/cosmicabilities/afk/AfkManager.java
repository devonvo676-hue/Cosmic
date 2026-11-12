package com.cosmicabilities.afk;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.data.PlayerData;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /afk: hover in place and earn AFK tokens (one per afk.minutes-per-token). While AFK nothing can hurt or move you -
 * not players, abilities, mobs, lava or digging out the ground under you. Only pressing a movement key yourself
 * (WASD / jump / sneak), attacking or casting an ability ends it.
 */
public class AfkManager implements Listener {
    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, Location> afk = new ConcurrentHashMap<>();
    /** flight state before going AFK: [allowFlight, flying] */
    private final Map<UUID, boolean[]> flightBefore = new ConcurrentHashMap<>();

    public AfkManager(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAfk(UUID id) {
        return afk.containsKey(id);
    }

    private int secondsPerToken() {
        return Math.max(60, plugin.getConfig().getInt("afk.minutes-per-token", 30) * 60);
    }

    public void toggle(Player p) {
        if (isAfk(p.getUniqueId())) {
            exit(p, true);
            return;
        }
        int combat = plugin.getConfig().getInt("afk.combat-cooldown-seconds", 15);
        if (plugin.combat().inCombat(p, combat)) {
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>You can't go AFK while in combat. Wait " + combat + " seconds."));
            return;
        }
        afk.put(p.getUniqueId(), p.getLocation().clone());
        // hover in place: breaking the block under you, water, pistons or other players can't move you
        flightBefore.put(p.getUniqueId(), new boolean[]{p.getAllowFlight(), p.isFlying()});
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setFlySpeed(0f);
        p.setCollidable(false);
        p.setFallDistance(0);
        p.setFireTicks(0);
        p.playerListName(plugin.mm("<gray>[AFK] </gray>").append(p.displayName()));
        p.showTitle(Title.title(plugin.mm("<gray><bold>AFK"), plugin.mm("<white>Earning AFK tokens - move to return"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500))));
        p.sendMessage(plugin.mm(plugin.prefix() + "<gray>You are now AFK. You earn <aqua>1 AFK token <gray>every <white>"
                + secondsPerToken() / 60 + " minutes<gray>. Spend them in <yellow>/afkshop<gray>."));
        if (plugin.getConfig().getBoolean("afk.broadcast", true)) {
            plugin.getServer().broadcast(plugin.mm("<gray>* " + p.getName() + " is now AFK"));
        }
    }

    /** Leaves AFK. Silent when triggered by casting an ability. */
    public void exit(Player p, boolean announce) {
        if (afk.remove(p.getUniqueId()) == null) return;
        restore(p);
        p.playerListName(null);
        if (announce || plugin.getConfig().getBoolean("afk.broadcast", true)) {
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>You are no longer AFK."));
        }
        if (plugin.getConfig().getBoolean("afk.broadcast", true)) {
            plugin.getServer().broadcast(plugin.mm("<gray>* " + p.getName() + " is no longer AFK"));
        }
    }

    private void restore(Player p) {
        boolean[] before = flightBefore.remove(p.getUniqueId());
        p.setFlySpeed(0.1f);
        p.setCollidable(true);
        if (before != null && p.getGameMode() != org.bukkit.GameMode.CREATIVE && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            p.setFlying(before[1]);
            p.setAllowFlight(before[0]);
        }
        // if the ground was dug out from under them, they don't take fall damage when they come back
        p.setFallDistance(0);
        plugin.status().addFallImmunity(p.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.status().removeFallImmunity(p.getUniqueId()), 200L);
    }

    /** Runs once a second. */
    public void tick() {
        int per = secondsPerToken();
        for (UUID id : afk.keySet()) {
            Player p = plugin.getServer().getPlayer(id);
            PlayerData d = p == null ? null : plugin.data().get(id);
            if (p == null || d == null) continue;
            int progress = d.afkProgress() + 1;
            if (progress >= per) {
                progress = 0;
                plugin.items().giveAfkTokens(p, plugin.events().afkMultiplier());
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                p.sendMessage(plugin.mm(plugin.prefix() + "<aqua>+1 AFK Token! <gray>You now have <white>" + plugin.items().afkTokens(p)
                        + "<gray>. Spend them in <yellow>/afkshop<gray>."));
            }
            d.setAfkProgress(progress);
            int left = per - progress;
            p.sendActionBar(plugin.mm("<gray>AFK <dark_gray>| <aqua>" + plugin.items().afkTokens(p) + " tokens <dark_gray>| <gray>next in <white>"
                    + (left / 60) + "m " + (left % 60) + "s"));
        }
    }

    /** Only the player's own key presses (WASD, jump, sneak) take them out of AFK. */
    @EventHandler
    public void onInput(org.bukkit.event.player.PlayerInputEvent event) {
        if (!isAfk(event.getPlayer().getUniqueId())) return;
        org.bukkit.Input in = event.getInput();
        if (in.isForward() || in.isBackward() || in.isLeft() || in.isRight() || in.isJump() || in.isSneak()) {
            exit(event.getPlayer(), true);
        }
    }

    /** Anything else that would move an AFK player (falling, water, pistons, knockback) is undone. Looking around is fine. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Location start = afk.get(p.getUniqueId());
        if (start == null) return;
        Location to = event.getTo();
        if (!to.getWorld().equals(start.getWorld())) { exit(p, true); return; }
        if (to.distanceSquared(start) > 0.0025) {
            Location back = start.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
            p.setVelocity(new org.bukkit.util.Vector());
            p.setFallDistance(0);
            if (!p.isFlying()) { p.setAllowFlight(true); p.setFlying(true); }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent event) {
        if (isAfk(event.getPlayer().getUniqueId()) && !event.isFlying()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && isAfk(p.getUniqueId()) && event.getFoodLevel() < p.getFoodLevel()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        // ender pearls / chorus fruit thrown at them or by plugins move them for real: keep the new spot as their AFK spot
        if (isAfk(event.getPlayer().getUniqueId())) afk.put(event.getPlayer().getUniqueId(), event.getTo().clone());
    }

    /** AFK players can't be hurt by anything (players, abilities, mobs, falling, lava, void...); attacking ends AFK. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent by) {
            Entity damager = by.getDamager();
            if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) damager = shooter;
            if (damager instanceof Player attacker && isAfk(attacker.getUniqueId())) exit(attacker, true);
        }
        if (event.getEntity() instanceof Player p && isAfk(p.getUniqueId())
                && plugin.getConfig().getBoolean("afk.invulnerable", true)) {
            event.setCancelled(true);
            p.setFireTicks(0);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Location start = afk.get(p.getUniqueId());
                if (start != null) p.teleport(start);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (afk.remove(event.getPlayer().getUniqueId()) != null) restore(event.getPlayer());
    }
}
