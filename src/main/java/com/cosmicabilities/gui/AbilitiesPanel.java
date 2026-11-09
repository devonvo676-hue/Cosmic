package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /abilities: a read-only guide to every ability (hover for details), your own slots and how progression works. */
public class AbilitiesPanel implements Listener {
    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;

    public AbilitiesPanel(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.mm("<dark_purple><bold>Cosmic Abilities</bold> <dark_gray>- hover for details"));
        holder.inventory = inv;
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        PlayerData d = plugin.data().get(p.getUniqueId());
        AbilityType[] types = AbilityType.values();
        for (int i = 0; i < types.length; i++) {
            AbilityType t = types[i];
            List<String> extra = new ArrayList<>();
            int owned = ownedSlot(d, t);
            if (owned > 0) extra.add("<gold><bold>YOURS</bold> <gray>(slot " + owned
                    + (d.slot(owned).upgraded() ? ", upgraded" : "") + ")");
            ItemStack icon = Icons.ability(plugin, t, extra);
            if (owned > 0) {
                ItemMeta meta = icon.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                icon.setItemMeta(meta);
            }
            inv.setItem(SLOTS[i], icon);
        }

        // how it all works
        int pct = (int) Math.round(plugin.getConfig().getDouble("upgrades.cooldown-reduction", 0.3) * 100);
        inv.setItem(38, Icons.button(plugin, Material.EXPERIENCE_BOTTLE, "<aqua><bold>Upgrading",
                "<gray>Each ability can be upgraded <white>once<gray>:",
                "<white>" + pct + "% shorter cooldown<gray>.",
                "<gray>Craft an <white>Upgrade Token <gray>(see <yellow>/recipes<gray>),",
                "<gray>right-click it and pick the ability."));
        inv.setItem(40, Icons.button(plugin, Material.NETHER_STAR, "<gold><bold>Your two slots",
                "<white>Slot 1<gray>: the ability the cosmic wheel gave",
                "<gray>you when you first joined. You keep it -",
                "<gray>a <light_purple>Cosmic Reroll <gray>spins it into a new one.",
                "<white>Slot 2<gray>: a second ability, only from",
                "<gray>boss fight orbs (or taking it from a kill).",
                "<red>If someone kills you, it drops for them.",
                "",
                "<yellow>/keybinds <gray>to choose how you cast."));
        inv.setItem(42, plugin.recipes().icon(p, com.cosmicabilities.recipe.RecipeManager.REROLL));
        p.openInventory(inv);
    }

    private static int ownedSlot(PlayerData d, AbilityType t) {
        if (d == null) return 0;
        for (int i = 1; i <= PlayerData.SLOTS; i++) {
            AbilitySlot s = d.slot(i);
            if (!s.isEmpty() && s.ability() == t) return i;
        }
        return 0;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 42 && event.getWhoClicked() instanceof Player p) {
            plugin.recipes().openView(p, com.cosmicabilities.recipe.RecipeManager.REROLL);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
