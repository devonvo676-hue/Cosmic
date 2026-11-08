package com.cosmicabilities.afk;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.data.PlayerData;
import com.cosmicabilities.item.CosmicMaterial;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AFK token shop. Everything sold is defined in plugins/CosmicAbilities/afkshop.yml.
 * Admins can also add the item in their hand with /afkshop add &lt;price&gt;, and shift-right-click an entry to remove it.
 */
public final class AfkShop implements Listener {
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        final Map<Integer, String> slots = new HashMap<>();
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private record Entry(String id, int slot, int price, ItemStack display, ItemStack give, List<String> commands) {}

    private final CosmicAbilitiesPlugin plugin;
    private final File file;
    private YamlConfiguration yml;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public AfkShop(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "afkshop.yml");
        if (!file.exists()) plugin.saveResource("afkshop.yml", false);
        reload();
    }

    public void reload() {
        yml = YamlConfiguration.loadConfiguration(file);
        entries.clear();
        ConfigurationSection items = yml.getConfigurationSection("items");
        if (items == null) return;
        int auto = 10;
        for (String id : items.getKeys(false)) {
            ConfigurationSection sec = items.getConfigurationSection(id);
            if (sec == null) continue;
            ItemStack give = buildItem(sec);
            if (give == null && sec.getStringList("commands").isEmpty()) {
                plugin.getLogger().warning("afkshop.yml: entry '" + id + "' has no item or commands, skipped.");
                continue;
            }
            ItemStack display = give != null ? give.clone() : new ItemStack(material(sec.getString("icon", "PAPER")));
            if (sec.getString("name") != null) {
                ItemMeta meta = display.getItemMeta();
                meta.displayName(plugin.mm("<!italic>" + sec.getString("name")));
                display.setItemMeta(meta);
                if (give != null && sec.getBoolean("rename-given-item", false)) give.setItemMeta(meta);
            }
            int slot = sec.getInt("slot", -1);
            if (slot < 0 || slot >= 45) {
                while (auto % 9 == 0 || auto % 9 == 8) auto++;
                slot = auto++;
            }
            boolean giveItem = sec.getBoolean("give-item", true);
            entries.put(id, new Entry(id, slot, Math.max(0, sec.getInt("price", 1)), display,
                    giveItem ? give : null, sec.getStringList("commands")));
        }
    }

    /** Supports: item (saved via /afkshop add), cosmic: fragment, or material + amount + name + lore + enchantments. */
    private ItemStack buildItem(ConfigurationSection sec) {
        if (sec.isItemStack("item")) return sec.getItemStack("item");
        if ("reroll".equalsIgnoreCase(sec.getString("special"))) return plugin.orbs().reroll();
        int amount = Math.max(1, sec.getInt("amount", 1));
        if (sec.getString("cosmic") != null) {
            CosmicMaterial m = CosmicMaterial.fromId(sec.getString("cosmic"));
            return m == null ? null : m.create(amount);
        }
        if (sec.getString("material") == null) return null;
        Material mat = material(sec.getString("material"));
        if (mat == null || mat.isAir()) return null;
        ItemStack item = new ItemStack(mat, Math.min(amount, mat.getMaxStackSize()));
        ItemMeta meta = item.getItemMeta();
        List<String> lore = sec.getStringList("lore");
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(plugin.mm("<!italic>" + l));
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        ConfigurationSection ench = sec.getConfigurationSection("enchantments");
        if (ench != null) {
            for (String key : ench.getKeys(false)) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (e != null) item.addUnsafeEnchantment(e, ench.getInt(key, 1));
            }
        }
        return item;
    }

    private static Material material(String name) {
        return name == null ? null : Material.matchMaterial(name);
    }

    public void open(Player p) {
        Holder holder = new Holder();
        String title = yml.getString("title", "<dark_aqua><bold>AFK Shop");
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.mm(title));
        holder.inventory = inv;
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);

        PlayerData d = plugin.data().get(p.getUniqueId());
        int tokens = d == null ? 0 : d.afkTokens();
        ItemStack info = new ItemStack(Material.CLOCK);
        ItemMeta im = info.getItemMeta();
        im.displayName(plugin.mm("<!italic><aqua><bold>Your AFK tokens: <white>" + tokens));
        im.lore(List.of(plugin.mm("<!italic><gray>Earn 1 token every <white>" + plugin.getConfig().getInt("afk.minutes-per-token", 30)
                + " minutes <gray>while <yellow>/afk")));
        info.setItemMeta(im);
        inv.setItem(49, info);

        boolean admin = BanPistol.isStaff(p);
        for (Entry e : entries.values()) {
            ItemStack shown = e.display().clone();
            ItemMeta meta = shown.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(plugin.mm("<!italic><gray>Price: " + (tokens >= e.price() ? "<green>" : "<red>") + e.price() + " AFK tokens"));
            lore.add(plugin.mm("<!italic><yellow>Click to buy"));
            if (admin) lore.add(plugin.mm("<!italic><dark_red>Shift + right-click to remove (admin)"));
            meta.lore(lore);
            shown.setItemMeta(meta);
            inv.setItem(e.slot(), shown);
            holder.slots.put(e.slot(), e.id());
        }
        p.openInventory(inv);
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
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof Holder)) return;
        String id = holder.slots.get(event.getRawSlot());
        Entry e = id == null ? null : entries.get(id);
        if (e == null) return;

        if (event.getClick().isShiftClick() && event.getClick().isRightClick() && BanPistol.isStaff(p)) {
            remove(id);
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Removed <white>" + id + " <gray>from the AFK shop."));
            open(p);
            return;
        }
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        if (d.afkTokens() < e.price()) {
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>You need " + e.price() + " AFK tokens (you have " + d.afkTokens() + ")."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        d.setAfkTokens(d.afkTokens() - e.price());
        if (e.give() != null) {
            for (ItemStack left : p.getInventory().addItem(e.give().clone()).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
        for (String cmd : e.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }
        plugin.data().save(d);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
        p.sendMessage(plugin.mm(plugin.prefix() + "<green>Purchased! <gray>You have <white>" + d.afkTokens() + " <gray>AFK tokens left."));
        open(p);
    }

    /** /afkshop add &lt;price&gt; [slot]: saves the held item (with all its enchants/name) as a new entry. */
    public String add(Player admin, int price, int slot) {
        ItemStack held = admin.getInventory().getItemInMainHand();
        if (held.getType().isAir()) return null;
        String id = held.getType().name().toLowerCase(Locale.ROOT);
        String unique = id;
        for (int n = 2; yml.contains("items." + unique); n++) unique = id + "_" + n;
        yml.set("items." + unique + ".price", price);
        if (slot >= 0) yml.set("items." + unique + ".slot", slot);
        yml.set("items." + unique + ".item", held.clone());
        save();
        reload();
        return unique;
    }

    public void remove(String id) {
        yml.set("items." + id, null);
        save();
        reload();
    }

    private void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save afkshop.yml: " + e.getMessage());
        }
    }
}
