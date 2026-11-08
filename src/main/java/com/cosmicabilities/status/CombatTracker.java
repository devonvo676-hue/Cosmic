package com.cosmicabilities.status;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Remembers when each player last dealt or took combat damage (used to block /afk and upgrades mid-fight). */
public class CombatTracker implements Listener {
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    public void tag(Player p) {
        last.put(p.getUniqueId(), System.currentTimeMillis());
    }

    public boolean inCombat(Player p, int seconds) {
        Long t = last.get(p.getUniqueId());
        return t != null && System.currentTimeMillis() - t < seconds * 1000L;
    }

    public void clear(UUID uuid) {
        last.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) damager = shooter;
        if (damager instanceof Player attacker) tag(attacker);
        if (event.getEntity() instanceof Player victim) tag(victim);
    }
}
