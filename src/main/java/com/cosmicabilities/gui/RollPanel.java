package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.data.PlayerData;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The cosmic wheel: a slot-machine reel of every ability scrolls past and slows down onto the one you get.
 * Used for your first ability when you join, and whenever you use a Cosmic Reroll (it replaces slot 1).
 * The result is decided when the spin starts and is always awarded, even if you close the menu or log out.
 */
public class RollPanel implements Listener {
    private static final int CENTER = 13;
    private static final int[] REEL = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    public enum Reason { FIRST_JOIN, REROLL, ADMIN }

    public static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private final class Spin {
        final Player player;
        final AbilityType result;
        final Reason reason;
        final AbilityType previous;
        final List<AbilityType> strip = new ArrayList<>();
        final Holder holder = new Holder();
        BukkitRunnable task;
        boolean done;

        Spin(Player player, AbilityType result, Reason reason, AbilityType previous) {
            this.player = player; this.result = result; this.reason = reason; this.previous = previous;
        }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, Spin> spins = new ConcurrentHashMap<>();

    public RollPanel(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isSpinning(UUID id) {
        return spins.containsKey(id);
    }

    /** Spins for a new slot-1 ability, never landing on one the player already has. Returns false if already spinning. */
    public boolean spin(Player p, Reason reason) {
        if (spins.containsKey(p.getUniqueId())) return false;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return false;
        Set<AbilityType> pool = EnumSet.allOf(AbilityType.class);
        if (!d.primary().isEmpty()) pool.remove(d.primary().ability());
        if (!d.gained().isEmpty()) pool.remove(d.gained().ability());
        if (pool.isEmpty()) pool = EnumSet.allOf(AbilityType.class);
        List<AbilityType> options = new ArrayList<>(pool);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        AbilityType result = options.get(r.nextInt(options.size()));
        Spin s = new Spin(p, result, reason, d.primary().isEmpty() ? null : d.primary().ability());

        // the reel: random abilities, with the result placed so it stops in the middle
        AbilityType[] all = AbilityType.values();
        int steps = 46 + r.nextInt(8);
        for (int i = 0; i < steps + REEL.length; i++) s.strip.add(all[r.nextInt(all.length)]);
        s.strip.set(steps + 4, result);   // slot 13 shows strip[offset + 4]
        spins.put(p.getUniqueId(), s);

        Inventory inv = Bukkit.createInventory(s.holder, 27, plugin.mm(reason == Reason.FIRST_JOIN
                ? "<dark_purple><bold>Your Cosmic Ability" : "<light_purple><bold>Cosmic Reroll"));
        s.holder.inventory = inv;
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        ItemStack pointer = Icons.button(plugin, Material.END_ROD, "<yellow>▼");
        inv.setItem(4, pointer);
        inv.setItem(22, Icons.button(plugin, Material.END_ROD, "<yellow>▲"));
        draw(s, 0);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);

        s.task = new BukkitRunnable() {
            int offset = 0, wait = 0, tick = 0;
            @Override
            public void run() {
                if (!p.isOnline()) { cancel(); return; }
                tick++;
                if (wait-- > 0) return;
                offset++;
                draw(s, offset);
                float pitch = 0.8f + Math.min(1.2f, offset / (float) steps);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, pitch);
                if (offset >= steps) {
                    cancel();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> finish(s), 6L);
                    return;
                }
                // slow down towards the end: 0 ticks between steps at first, up to ~8 at the very end
                double prog = offset / (double) steps;
                wait = (int) Math.round(Math.pow(prog, 3) * 6);
            }
        };
        s.task.runTaskTimer(plugin, 2L, 1L);
        return true;
    }

    private void draw(Spin s, int offset) {
        Inventory inv = s.holder.inventory;
        for (int i = 0; i < REEL.length; i++) {
            AbilityType t = s.strip.get(Math.min(s.strip.size() - 1, offset + i));
            ItemStack icon = Icons.ability(plugin, t, List.of());
            if (REEL[i] == CENTER) {
                ItemMeta m = icon.getItemMeta();
                m.setEnchantmentGlintOverride(true);
                icon.setItemMeta(m);
            }
            inv.setItem(REEL[i], icon);
        }
        ItemStack frame = Icons.button(plugin, Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int slot : new int[]{3, 5, 21, 23}) inv.setItem(slot, frame);
    }

    /** Awards the ability (once), announces it and plays the reveal. */
    private void finish(Spin s) {
        if (s.done) return;
        s.done = true;
        spins.remove(s.player.getUniqueId());
        Player p = s.player;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        int tier = 1;
        d.primary().equip(s.result, tier);
        plugin.data().save(d);
        plugin.engine().resetCooldown(p.getUniqueId());
        if (!p.isOnline()) return;
        if (p.getOpenInventory().getTopInventory().getHolder() == s.holder) {
            ItemStack shown = Icons.ability(plugin, s.result, List.of("<green><bold>THIS IS YOURS NOW"));
            ItemMeta m = shown.getItemMeta();
            m.setEnchantmentGlintOverride(true);
            shown.setItemMeta(m);
            ItemStack gold = Icons.button(plugin, Material.YELLOW_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < 27; i++) if (i != CENTER) s.holder.inventory.setItem(i, gold);
            s.holder.inventory.setItem(CENTER, shown);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == s.holder) p.closeInventory();
            }, 50L);
        }
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.5f);
        if (s.reason == Reason.FIRST_JOIN) {
            plugin.playerListener().awaken(p, s.result);
        } else {
            p.showTitle(Title.title(plugin.mm("<" + s.result.color + "><bold>" + s.result.displayName.toUpperCase()),
                    plugin.mm("<gray>is your new slot 1 ability <dark_gray>(/ability 1)"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(600))));
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>" + s.result.description));
            if (s.reason == Reason.REROLL) {
                for (Player online : Bukkit.getOnlinePlayers()) online.playSound(online.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.6f);
                Bukkit.broadcast(plugin.mm(plugin.prefix() + "<gradient:#f72585:#4cc9f0><bold>REROLL!</bold></gradient> <white>" + p.getName()
                        + " <gray>rerolled " + (s.previous == null ? "" : s.previous.colored() + " <gray>")
                        + "into " + s.result.colored() + "<gray>!"));
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    /** Logging out mid-spin still gives you the result. */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Spin s = spins.get(event.getPlayer().getUniqueId());
        if (s == null) return;
        if (s.task != null) s.task.cancel();
        finish(s);
    }

    public void shutdown() {
        for (Spin s : new ArrayList<>(spins.values())) {
            if (s.task != null) s.task.cancel();
            finish(s);
        }
    }
}
