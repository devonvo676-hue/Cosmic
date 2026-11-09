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
 * Admins edit it in game from /adminpanel -> AFK Shop Editor (or /afkshop edit), or hold an item and /afkshop add &lt;price&gt;.
 */
public final class AfkShop implements Listener {
    /** The admin editor: same layout as the shop, but clicks edit instead of buy. */
    public static final class Editor implements InventoryHolder {
        private Inventory inventory;
        final Map<Integer, String> slots = new HashMap<>();
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

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
        String special = sec.getString("special");
        if ("reroll".equalsIgnoreCase(special)) return plugin.orbs().reroll();
        if ("cosmic_pickaxe".equalsIgnoreCase(special)) return plugin.tools().create(com.cosmicabilities.item.CosmicTools.Tool.PICKAXE);
        if ("cosmic_axe".equalsIgnoreCase(special)) return plugin.tools().create(com.cosmicabilities.item.CosmicTools.Tool.AXE);
        if ("cosmic_shard".equalsIgnoreCase(special)) return plugin.items().shard(Math.max(1, sec.getInt("amount", 1)));
        if ("upgrade_token".equalsIgnoreCase(special)) return plugin.items().token(Math.max(1, sec.getInt("amount", 1)));
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
            String craft = craftId(e.display());
            if (craft != null) {
                lore.clear();
                if (plugin.recipes().owns(p, craft)) {
                    for (String l : plugin.recipes().details(craft)) lore.add(plugin.mm("<!italic>" + l));
                } else {
                    lore.addAll(plugin.recipes().recipeComponents(craft, false));
                }
            }
            lore.add(Component.empty());
            lore.add(plugin.mm("<!italic><gray>Price: " + (tokens >= e.price() ? "<green>" : "<red>") + e.price() + " AFK tokens"));
            lore.add(plugin.mm("<!italic><yellow>Click to buy"));
            if (admin) lore.add(plugin.mm("<!italic><dark_red>Admins: edit this shop in /adminpanel"));
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

    private String craftId(ItemStack it) {
        if (plugin.orbs().isReroll(it)) return com.cosmicabilities.recipe.RecipeManager.REROLL;
        if (plugin.items().idOf(it) != null) return plugin.items().idOf(it);
        com.cosmicabilities.item.CosmicTools.Tool t = plugin.tools().toolOf(it);
        return t == null ? null : t.id;
    }

    // ------------------------------------------------------------------ admin editor

    public void openEditor(Player admin) {
        Editor ed = new Editor();
        Inventory inv = Bukkit.createInventory(ed, 54, plugin.mm("<dark_red><bold>AFK Shop Editor"));
        ed.inventory = inv;
        for (Entry e : entries.values()) {
            ItemStack shown = e.display().clone();
            ItemMeta meta = shown.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(plugin.mm("<!italic><gold>Price: <white>" + e.price() + " AFK tokens"));
            lore.add(plugin.mm("<!italic><yellow>Left <gray>+1   <yellow>Right <gray>-1   <yellow>Shift-left <gray>+10"));
            lore.add(plugin.mm("<!italic><red>Shift-right <gray>remove from the shop"));
            if (!e.commands().isEmpty()) lore.add(plugin.mm("<!italic><dark_gray>Runs " + e.commands().size() + " command(s)"));
            meta.lore(lore);
            shown.setItemMeta(meta);
            inv.setItem(e.slot(), shown);
            ed.slots.put(e.slot(), e.id());
        }
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        inv.setItem(45, button(Material.ARROW, "<yellow>Back to admin panel"));
        inv.setItem(46, button(Material.ECHO_SHARD, "<light_purple>Add Cosmic Reroll", "<gray>Price 10, change it after"));
        inv.setItem(47, button(Material.NETHERITE_PICKAXE, "<light_purple>Add Cosmic Pickaxe", "<gray>Price 20, change it after"));
        inv.setItem(48, button(Material.NETHERITE_AXE, "<light_purple>Add Cosmic Axe", "<gray>Price 20, change it after"));
        inv.setItem(49, button(Material.BOOK, "<gold><bold>How to edit",
                "<white>Add: <gray>click any item in your own inventory",
                "<gray>(the exact item, enchants and name are saved;",
                "<gray>you keep yours). It goes in the first free spot.",
                "<white>Price: <gray>left/right-click an entry.",
                "<white>Remove: <gray>shift + right-click an entry.",
                "<white>Move: <gray>press Q on an entry, then click an empty spot."));
        inv.setItem(50, button(Material.EMERALD, "<green>Open the shop as a player"));
        inv.setItem(51, button(Material.AMETHYST_SHARD, "<light_purple>Add Cosmic Shard", "<gray>Price 2, change it after"));
        inv.setItem(52, button(Material.PAPER, "<light_purple>Add Upgrade Token", "<gray>Price 15, change it after"));
        inv.setItem(53, button(Material.REPEATER, "<green>Reload afkshop.yml", "<gray>After editing the file by hand"));
        admin.openInventory(inv);
    }

    private ItemStack button(Material m, String name, String... lore) {
        return com.cosmicabilities.gui.Icons.button(plugin, m, name, lore);
    }

    private final Map<java.util.UUID, String> moving = new HashMap<>();

    @EventHandler
    public void onEditorClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Editor ed)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!BanPistol.isStaff(p)) { p.closeInventory(); return; }
        int raw = event.getRawSlot();
        org.bukkit.event.inventory.ClickType click = event.getClick();
        if (raw >= event.getInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            int slot = freeSlot();
            if (slot < 0) { p.sendMessage(plugin.mm(plugin.prefix() + "<red>The shop is full (45 items).")); return; }
            String id = addItem(clicked.clone(), 1, slot);
            p.sendMessage(plugin.mm(plugin.prefix() + "<green>Added <white>" + id + " <green>for 1 token. <gray>Left-click it to raise the price."));
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            openEditor(p);
            return;
        }
        if (raw >= 45) {
            switch (raw) {
                case 45 -> plugin.adminHub().open(p);
                case 46 -> addSpecial(p, "reroll", 10);
                case 47 -> addSpecial(p, "cosmic_pickaxe", 20);
                case 48 -> addSpecial(p, "cosmic_axe", 20);
                case 50 -> open(p);
                case 51 -> addSpecial(p, "cosmic_shard", 2);
                case 52 -> addSpecial(p, "upgrade_token", 15);
                case 53 -> { reload(); openEditor(p); }
                default -> { }
            }
            return;
        }
        String id = ed.slots.get(raw);
        if (id == null) {
            String mv = moving.remove(p.getUniqueId());
            if (mv != null && yml.contains("items." + mv)) {
                yml.set("items." + mv + ".slot", raw);
                save();
                reload();
                openEditor(p);
            }
            return;
        }
        if (click == org.bukkit.event.inventory.ClickType.DROP || click == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
            moving.put(p.getUniqueId(), id);
            p.sendActionBar(plugin.mm("<yellow>Now click an empty spot to move <white>" + id));
            return;
        }
        Entry e = entries.get(id);
        if (e == null) return;
        if (click.isShiftClick() && click.isRightClick()) {
            remove(id);
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Removed <white>" + id + " <gray>from the AFK shop."));
        } else {
            int delta = click.isRightClick() ? -1 : click.isShiftClick() ? 10 : 1;
            yml.set("items." + id + ".price", Math.max(0, e.price() + delta));
            save();
            reload();
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
        openEditor(p);
    }

    @EventHandler
    public void onEditorDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Editor) event.setCancelled(true);
    }

    private void addSpecial(Player p, String special, int price) {
        int slot = freeSlot();
        if (slot < 0) return;
        String unique = special;
        for (int n = 2; yml.contains("items." + unique); n++) unique = special + "_" + n;
        yml.set("items." + unique + ".price", price);
        yml.set("items." + unique + ".slot", slot);
        yml.set("items." + unique + ".special", special);
        save();
        reload();
        openEditor(p);
    }

    private int freeSlot() {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Entry e : entries.values()) used.add(e.slot());
        for (int i = 0; i < 45; i++) if (!used.contains(i)) return i;
        return -1;
    }

    /** Saves an exact item as a new shop entry. */
    public String addItem(ItemStack item, int price, int slot) {
        String id = item.getType().name().toLowerCase(Locale.ROOT);
        String unique = id;
        for (int n = 2; yml.contains("items." + unique); n++) unique = id + "_" + n;
        yml.set("items." + unique + ".price", price);
        if (slot >= 0) yml.set("items." + unique + ".slot", slot);
        yml.set("items." + unique + ".item", item);
        save();
        reload();
        return unique;
    }

    /** /afkshop add &lt;price&gt; [slot]: saves the held item (with all its enchants/name) as a new entry. */
    public String add(Player admin, int price, int slot) {
        ItemStack held = admin.getInventory().getItemInMainHand();
        if (held.getType().isAir()) return null;
        return addItem(held.clone(), price, slot);
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
