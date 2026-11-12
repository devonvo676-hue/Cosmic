package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import com.cosmicabilities.recipe.RecipeManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /cosmic: the player menu - your abilities, upgrades, every other menu, and the craftable cosmic items. */
public class MainMenu implements Listener {
    public static final class Holder implements InventoryHolder {
        Inventory inventory;
        final boolean items;
        Holder(boolean items) { this.items = items; }
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private static final int[] ITEM_SLOTS = RecipeManager.MENU_SLOTS;
    private final CosmicAbilitiesPlugin plugin;

    public MainMenu(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Holder h = new Holder(false);
        Inventory inv = Bukkit.createInventory(h, 54, plugin.mm("<gradient:#9b5de5:#00bbf9><bold>Cosmic Abilities"));
        h.inventory = inv;
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);
        PlayerData d = plugin.data().get(p.getUniqueId());

        inv.setItem(11, slotIcon(d, 1));
        inv.setItem(15, slotIcon(d, 2));
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(p);
        sm.displayName(plugin.mm("<!italic><yellow><bold>" + p.getName()));
        sm.lore(List.of(plugin.mm("<!italic><gray>AFK Tokens: <white>" + plugin.items().afkTokens(p)),
                plugin.mm("<!italic><gray>Trusted players: <white>" + (d == null ? 0 : d.trusted().size()))));
        head.setItemMeta(sm);
        inv.setItem(13, head);

        inv.setItem(28, Icons.button(plugin, Material.BOOK, "<light_purple><bold>All Abilities", "<gray>What every ability does"));
        inv.setItem(29, Icons.button(plugin, Material.TRIPWIRE_HOOK, "<aqua><bold>Keybinds", "<gray>Choose how you cast each slot"));
        inv.setItem(30, Icons.button(plugin, Material.PLAYER_HEAD, "<yellow><bold>Ability Holders", "<gray>Who has which ability"));
        inv.setItem(31, Icons.button(plugin, Material.EMERALD, "<green><bold>AFK Shop", "<gray>Spend your AFK tokens"));
        inv.setItem(32, Icons.button(plugin, Material.RED_BED, "<green><bold>Go AFK", "<gray>Earn 1 token every "
                + plugin.getConfig().getInt("afk.minutes-per-token", 30) + " minutes"));
        inv.setItem(33, Icons.button(plugin, Material.SHIELD, "<white><bold>Trusted Players",
                "<gray>Your abilities never hit them.", "<yellow>/trust <player> <gray>/ <yellow>/untrust <player>", "<gray>Click to list them in chat"));
        inv.setItem(34, Icons.button(plugin, Material.CRAFTING_TABLE, "<gold><bold>Cosmic Items",
                "<gray>Everything you can craft: Reroll, Pickaxe,", "<gray>Axe, Cosmic Shard, Upgrade Token <dark_gray>(/recipes)"));
        inv.setItem(39, Icons.button(plugin, Material.GOLD_INGOT, "<yellow><bold>Auction House", "<gray>Buy and sell items for cash <dark_gray>(/ah)"));
        inv.setItem(41, Icons.button(plugin, Material.ITEM_FRAME, "<light_purple><bold>Sidebar: " + (d == null || d.sidebar() ? "<green>ON" : "<red>OFF"),
                "<gray>The COSMIC panel on the right", "<gray>of your screen. Click to toggle."));
        inv.setItem(40, Icons.button(plugin, Material.SUNFLOWER, "<gold><bold>Coinflip", "<gray>Cash: <yellow>$" + plugin.economy().format(plugin.economy().balance(p.getUniqueId())), "<gray>Bet against other players <dark_gray>(/cf)"));
        if (BanPistol.isStaff(p) && p.isOp()) inv.setItem(49, Icons.button(plugin, Material.COMMAND_BLOCK, "<dark_red><bold>Admin Panel"));
        else inv.setItem(49, Icons.gui(plugin, "close", "<red>Close"));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
    }

    private ItemStack slotIcon(PlayerData d, int slot) {
        if (d == null || d.slot(slot).isEmpty()) {
            return Icons.button(plugin, Material.GRAY_DYE, "<gray>Slot " + slot + ": <dark_gray>empty",
                    slot == 1 ? "<gray>Your first ability is on its way." : "<gray>Win a boss fight to gain a second ability.");
        }
        AbilitySlot s = d.slot(slot);
        AbilityType t = s.ability();
        List<String> extra = new ArrayList<>();
        extra.add("<gold><bold>SLOT " + slot + "</bold> <gray>- " + AbilityType.tierName(s.tier()) + " <dark_gray>(/ability " + slot + ")");
        extra.add("<gray>Cast with: <white>" + d.bind(slot).label);
        if (!s.upgraded()) {
            if (plugin.getConfig().getBoolean("upgrades.require-token", true)) {
                extra.add("<yellow>Click to upgrade <gray>(uses 1 Upgrade Token - craft it, see /recipes)");
            } else {
                extra.add("<yellow>Click to upgrade <gray>(" + plugin.getConfig().getInt("upgrades.cost.fragments", 16) + " " + t.material.displayName
                        + "s + " + plugin.getConfig().getInt("upgrades.cost.astral-fragments", 2) + " Astral)");
            }
        } else {
            extra.add("<aqua>Upgraded");
        }
        if (slot == 1) extra.add("<dark_gray>A Cosmic Reroll changes this one.");
        else extra.add("<red>Drops for your killer if you're killed.");
        ItemStack icon = Icons.ability(plugin, t, extra);
        ItemMeta m = icon.getItemMeta();
        m.setEnchantmentGlintOverride(s.upgraded());
        icon.setItemMeta(m);
        return icon;
    }

    public void openItems(Player p) {
        Holder h = new Holder(true);
        Inventory inv = Bukkit.createInventory(h, 27, plugin.mm("<gold><bold>Recipes <dark_gray>- hover / click"));
        h.inventory = inv;
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        for (int i = 0; i < RecipeManager.IDS.size(); i++) inv.setItem(ITEM_SLOTS[i], plugin.recipes().icon(p, RecipeManager.IDS.get(i)));
        inv.setItem(22, Icons.gui(plugin, "back", "<yellow>Back"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int raw = event.getRawSlot();
        if (raw >= event.getInventory().getSize()) return;
        if (h.items) {
            if (raw == 22) { open(p); return; }
            for (int i = 0; i < ITEM_SLOTS.length; i++) if (raw == ITEM_SLOTS[i]) plugin.recipes().openView(p, RecipeManager.IDS.get(i));
            return;
        }
        switch (raw) {
            case 11, 15 -> {
                PlayerData d = plugin.data().get(p.getUniqueId());
                int slot = raw == 11 ? 1 : 2;
                if (d == null || d.slot(slot).isEmpty() || d.slot(slot).upgraded()) return;
                p.closeInventory();
                p.performCommand("cosmic upgrade " + slot);
            }
            case 28 -> plugin.abilitiesPanel().open(p);
            case 29 -> plugin.keybindPanel().open(p);
            case 30 -> plugin.holdersPanel().open(p, 0);
            case 31 -> plugin.afkShop().open(p);
            case 32 -> { p.closeInventory(); plugin.afk().toggle(p); }
            case 33 -> { p.closeInventory(); p.performCommand("trust list"); }
            case 34 -> openItems(p);
            case 40 -> plugin.coinflip().open(p);
            case 39 -> plugin.auctions().open(p, 0);
            case 41 -> { p.performCommand("sidebar"); open(p); }
            case 49 -> {
                if (BanPistol.isStaff(p)) plugin.adminHub().open(p); else p.closeInventory();
            }
            default -> { }
        }
    }

    public static Component none() { return Component.empty(); }
}
