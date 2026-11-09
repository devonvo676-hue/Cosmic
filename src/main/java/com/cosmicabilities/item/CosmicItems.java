package com.cosmicabilities.item;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import com.cosmicabilities.gui.Icons;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Cosmic Shard (crafting material for the cosmic tools and Upgrade Tokens) and the Upgrade Token:
 * right-click a token, pick which of your abilities to upgrade, done.
 */
public class CosmicItems implements Listener {
    public static final String SHARD = "cosmic_shard", TOKEN = "upgrade_token";

    public static final class Picker implements InventoryHolder {
        Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey key;

    public CosmicItems(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "cosmic_item");
    }

    public ItemStack shard(int amount) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", SHARD));
        meta.displayName(plugin.mm("<!italic><gradient:#b15cff:#4cc9f0><bold>Cosmic Shard"));
        meta.lore(List.of(plugin.mm("<!italic><gray>A sliver of a fallen star."),
                plugin.mm("<!italic><dark_gray>Used to craft Cosmic tools and Upgrade Tokens")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, SHARD);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack token(int amount) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", TOKEN));
        meta.displayName(plugin.mm("<!italic><gradient:#7b2ff7:#00e5ff><bold>Upgrade Token"));
        int pct = (int) Math.round(plugin.getConfig().getDouble("upgrades.cooldown-reduction", 0.3) * 100);
        meta.lore(List.of(plugin.mm("<!italic><gray>Right-click and choose which of your"),
                plugin.mm("<!italic><gray>cosmic abilities to upgrade."),
                plugin.mm("<!italic><aqua>Upgrade: " + pct + "% shorter cooldown")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, TOKEN);
        it.setItemMeta(meta);
        return it;
    }

    public String idOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public boolean isShard(ItemStack it) { return SHARD.equals(idOf(it)); }
    public boolean isToken(ItemStack it) { return TOKEN.equals(idOf(it)); }

    // ------------------------------------------------------------------ upgrade tokens

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isShard(hand)) { event.setCancelled(true); return; }   // don't let shards act as amethyst
        if (!isToken(hand)) return;
        event.setCancelled(true);
        openPicker(p);
    }

    public void openPicker(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        Picker h = new Picker();
        Inventory inv = Bukkit.createInventory(h, 27, plugin.mm("<dark_purple><bold>Upgrade which ability?"));
        h.inventory = inv;
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        for (int slot = 1; slot <= PlayerData.SLOTS; slot++) {
            AbilitySlot s = d.slot(slot);
            int at = slot == 1 ? 11 : 15;
            if (s.isEmpty()) {
                inv.setItem(at, Icons.button(plugin, Material.GRAY_DYE, "<gray>Slot " + slot + ": <dark_gray>empty"));
                continue;
            }
            List<String> extra = new ArrayList<>();
            extra.add("<gold><bold>SLOT " + slot);
            extra.add(s.upgraded() ? "<aqua>Already upgraded" : "<green><bold>Click to upgrade with your token");
            ItemStack icon = Icons.ability(plugin, s.ability(), extra);
            ItemMeta m = icon.getItemMeta();
            m.setEnchantmentGlintOverride(!s.upgraded());
            icon.setItemMeta(m);
            inv.setItem(at, icon);
        }
        inv.setItem(13, token(1));
        inv.setItem(22, Icons.button(plugin, Material.BARRIER, "<red>Cancel"));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Picker) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Picker)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int raw = event.getRawSlot();
        if (raw == 22) { p.closeInventory(); return; }
        int slot = raw == 11 ? 1 : raw == 15 ? 2 : 0;
        if (slot == 0) return;
        p.closeInventory();
        upgradeWithToken(p, slot);
    }

    /** Uses one Upgrade Token from the player's inventory to upgrade that slot. Returns true if it worked. */
    public boolean upgradeWithToken(Player p, int slot) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return false;
        AbilitySlot s = d.slot(slot);
        if (s.isEmpty()) { msg(p, "<red>Slot " + slot + " is empty."); return false; }
        AbilityType t = s.ability();
        if (s.upgraded()) { msg(p, t.colored() + " <gray>is already upgraded."); return false; }
        if (plugin.combat().inCombat(p, 10)) { msg(p, "<red>You can't upgrade during combat."); return false; }
        if (!takeToken(p)) {
            msg(p, "<red>You need an <white>Upgrade Token<red>. See how to craft one in <yellow>/recipes<red>.");
            return false;
        }
        s.setTier(AbilitySlot.MAX_TIER);
        plugin.data().save(d);
        if (plugin.getConfig().getBoolean("upgrades.cutscene", true)) plugin.cutscenes().playUpgrade(p, t, 2);
        else msg(p, t.colored() + " <gray>is now <aqua>upgraded<gray>!");
        Bukkit.broadcast(plugin.mm(plugin.prefix() + "<white>" + p.getName() + " <gray>upgraded " + t.colored() + "<gray>!"));
        return true;
    }

    private boolean takeToken(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!isToken(it)) continue;
            it.setAmount(it.getAmount() - 1);
            p.getInventory().setItem(i, it.getAmount() <= 0 ? null : it);
            return true;
        }
        return false;
    }

    private void msg(Player p, String mini) {
        p.sendMessage(plugin.mm(plugin.prefix() + mini));
    }

    public static Component none() { return Component.empty(); }
}
