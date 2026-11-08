package com.cosmicabilities.keybind;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import com.cosmicabilities.gui.Icons;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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

/**
 * /keybinds: two rows, one per ability slot. Click an option to bind it to that slot.
 * Picking a key the other slot uses swaps them.
 */
public class KeybindPanel implements Listener {
    private static final int ROW1 = 10, ROW2 = 28;

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;

    public KeybindPanel(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 45, plugin.mm("<dark_aqua><bold>Ability Keybinds"));
        holder.inventory = inv;
        render(p, inv);
        p.openInventory(inv);
    }

    private void render(Player p, Inventory inv) {
        ItemStack pane = Icons.button(plugin, Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        for (int slot = 1; slot <= 2; slot++) {
            AbilitySlot s = d.slot(slot);
            int row = slot == 1 ? ROW1 : ROW2;
            ItemStack head = s.isEmpty()
                    ? Icons.button(plugin, Material.BARRIER, "<gray>Slot " + slot + ": <dark_gray>empty")
                    : Icons.button(plugin, Material.PAPER, "<white>Slot " + slot + ": " + s.ability().colored(),
                    "<gray>Currently: <yellow>" + d.bind(slot).label, "<gray>Command: <yellow>/ability " + slot);
            if (!s.isEmpty()) {
                ItemMeta meta = head.getItemMeta();
                meta.setItemModel(new NamespacedKey("cosmic", s.ability().id()));
                head.setItemMeta(meta);
            }
            inv.setItem(row - 1, head);
            Keybind[] all = Keybind.values();
            for (int i = 0; i < all.length; i++) {
                Keybind k = all[i];
                boolean chosen = d.bind(slot) == k;
                ItemStack opt = Icons.button(plugin, k.icon, (chosen ? "<green><bold>" : "<white>") + k.label,
                        "<gray>" + k.description, "", chosen ? "<green>Selected for slot " + slot : "<yellow>Click to use for slot " + slot);
                if (chosen) {
                    ItemMeta meta = opt.getItemMeta();
                    meta.setEnchantmentGlintOverride(true);
                    opt.setItemMeta(meta);
                }
                inv.setItem(row + 1 + i, opt);
            }
        }
        inv.setItem(40, Icons.button(plugin, Material.BOOK, "<aqua>Tip",
                "<gray>/ability 1 and /ability 2 always work,",
                "<gray>whatever keybind you pick."));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof Holder)) return;
        int raw = event.getRawSlot();
        int slot;
        int index;
        if (raw > ROW1 && raw <= ROW1 + Keybind.values().length) { slot = 1; index = raw - ROW1 - 1; }
        else if (raw > ROW2 && raw <= ROW2 + Keybind.values().length) { slot = 2; index = raw - ROW2 - 1; }
        else return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        Keybind k = Keybind.values()[index];
        d.setBind(slot, k);
        plugin.data().save(d);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
        p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Slot " + slot + " now casts with <yellow>" + k.label + "<gray>."));
        render(p, event.getInventory());
    }
}
