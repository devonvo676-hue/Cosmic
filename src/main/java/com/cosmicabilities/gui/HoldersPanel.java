package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** /abilityholders: every online player's head; hover to see their cosmic abilities. */
public class HoldersPanel implements Listener {
    private static final int PER_PAGE = 45, PREV = 45, NEXT = 53;

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        int page;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;

    public HoldersPanel(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, int page) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int pages = Math.max(1, (players.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        Holder holder = new Holder();
        holder.page = page;
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.mm("<dark_purple><bold>Ability Holders</bold> <dark_gray>("
                + players.size() + " online)"));
        holder.inventory = inv;

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= players.size()) break;
            inv.setItem(i, head(players.get(idx)));
        }
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        if (page > 0) inv.setItem(PREV, Icons.gui(plugin, "back", "<yellow>Previous page"));
        if (page < pages - 1) inv.setItem(NEXT, Icons.gui(plugin, "next", "<yellow>Next page"));
        inv.setItem(49, Icons.button(plugin, Material.BOOK, "<gray>Page " + (page + 1) + "/" + pages,
                "<dark_gray>Hover a head to see that player's abilities."));
        viewer.openInventory(inv);
    }

    private ItemStack head(Player p) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(p);
        meta.displayName(plugin.mm("<!italic><white><bold>" + p.getName()));
        List<Component> lore = new ArrayList<>();
        PlayerData d = plugin.data().get(p.getUniqueId());
        lore.add(line("Joined with", d == null ? null : d.slot(1)));
        lore.add(line("Gained", d == null ? null : d.slot(2)));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private Component line(String label, AbilitySlot s) {
        if (s == null || s.isEmpty()) return plugin.mm("<!italic><gray>" + label + ": <dark_gray>none");
        return plugin.mm("<!italic><gray>" + label + ": " + s.ability().colored()
                + (s.upgraded() ? " <aqua>(" + AbilityType.tierName(s.tier()) + ")" : "") + " <dark_gray>" + s.ability().role);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getRawSlot() == PREV) open(p, holder.page - 1);
        else if (event.getRawSlot() == NEXT) open(p, holder.page + 1);
    }
}
