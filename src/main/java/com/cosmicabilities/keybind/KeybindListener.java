package com.cosmicabilities.keybind;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Turns each player's chosen keybinds into ability casts. */
public class KeybindListener implements Listener {
    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, Long> lastSneak = new ConcurrentHashMap<>();

    public KeybindListener(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Casts the first slot whose keybind matches. Returns true if something was bound to it. */
    private boolean trigger(Player p, Keybind bind) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return false;
        for (int slot = 1; slot <= PlayerData.SLOTS; slot++) {
            if (d.bind(slot) == bind && !d.slot(slot).isEmpty()) {
                plugin.engine().tryCast(p, slot);
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (trigger(p, p.isSneaking() ? Keybind.SNEAK_SWAP : Keybind.SWAP)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.LEFT_CLICK_AIR) return;
        Player p = event.getPlayer();
        if (!p.isSneaking() || !p.getInventory().getItemInMainHand().getType().isAir()) return;
        trigger(p, Keybind.SNEAK_LEFT_CLICK);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (p.isSneaking() && trigger(p, Keybind.SNEAK_DROP)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastSneak.put(p.getUniqueId(), now);
        if (last != null && now - last < 350) {
            lastSneak.remove(p.getUniqueId());
            trigger(p, Keybind.DOUBLE_SNEAK);
        }
    }
}
