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

/** /afk: stand still to earn AFK tokens (one per afk.minutes-per-token). Moving or fighting ends it. */
public class AfkManager implements Listener {
    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, Location> afk = new ConcurrentHashMap<>();

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
        p.playerListName(null);
        if (announce || plugin.getConfig().getBoolean("afk.broadcast", true)) {
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>You are no longer AFK."));
        }
        if (plugin.getConfig().getBoolean("afk.broadcast", true)) {
            plugin.getServer().broadcast(plugin.mm("<gray>* " + p.getName() + " is no longer AFK"));
        }
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
                d.addAfkTokens(1);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                p.sendMessage(plugin.mm(plugin.prefix() + "<aqua>+1 AFK token! <gray>You now have <white>" + d.afkTokens()
                        + "<gray>. Spend them in <yellow>/afkshop<gray>."));
            }
            d.setAfkProgress(progress);
            int left = per - progress;
            p.sendActionBar(plugin.mm("<gray>AFK <dark_gray>| <aqua>" + d.afkTokens() + " tokens <dark_gray>| <gray>next in <white>"
                    + (left / 60) + "m " + (left % 60) + "s"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location start = afk.get(event.getPlayer().getUniqueId());
        if (start == null) return;
        Location to = event.getTo();
        if (!to.getWorld().equals(start.getWorld()) || to.distanceSquared(start) > 0.25) exit(event.getPlayer(), true);
    }

    /** AFK players can't be hurt (optional), and attacking ends AFK. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent by) {
            Entity damager = by.getDamager();
            if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) damager = shooter;
            if (damager instanceof Player attacker && isAfk(attacker.getUniqueId())) exit(attacker, true);
        }
        if (event.getEntity() instanceof Player p && isAfk(p.getUniqueId())
                && plugin.getConfig().getBoolean("afk.invulnerable", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        afk.remove(event.getPlayer().getUniqueId());
    }
}
