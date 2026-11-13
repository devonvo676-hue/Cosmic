package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every single setting in config.yml, browsable in game: folders are sections (ability-settings, bosses, ...),
 * on/off settings toggle, numbers go up/down, text is typed in chat. Saved instantly.
 */
public class ConfigBrowser implements Listener {
    private static final int PER_PAGE = 45;

    public static final class Holder implements InventoryHolder {
        Inventory inventory;
        String path;
        int page;
        final List<String> keys = new ArrayList<>();
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, String[]> typing = new ConcurrentHashMap<>();   // player -> {path, returnPath}

    public ConfigBrowser(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private static String nice(String key) {
        String s = key.replace('-', ' ').replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }

    public void open(Player p, String path, int page) {
        ConfigurationSection sec = path.isEmpty() ? plugin.getConfig() : plugin.getConfig().getConfigurationSection(path);
        if (sec == null) { open(p, "", 0); return; }
        Holder h = new Holder();
        h.path = path;
        List<String> keys = new ArrayList<>(sec.getKeys(false));
        keys.removeIf(k -> path.isEmpty() && (k.equals("migrations") || k.equals("owner-items")));
        if (path.equals("economy")) keys.remove("death-values");
        // folders first
        keys.sort((a, b) -> {
            boolean fa = sec.isConfigurationSection(a), fb = sec.isConfigurationSection(b);
            return fa == fb ? 0 : fa ? -1 : 1;
        });
        int pages = Math.max(1, (keys.size() + PER_PAGE - 1) / PER_PAGE);
        h.page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(h, 54, plugin.mm("<dark_gray>Config: <white>" + (path.isEmpty() ? "everything" : path)
                + (pages > 1 ? " <dark_gray>(" + (h.page + 1) + "/" + pages + ")" : "")));
        h.inventory = inv;
        for (int i = 0; i < PER_PAGE && h.page * PER_PAGE + i < keys.size(); i++) {
            String key = keys.get(h.page * PER_PAGE + i);
            h.keys.add(key);
            inv.setItem(i, item(sec, key, path.isEmpty() ? key : path + "." + key));
        }
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        inv.setItem(45, Icons.gui(plugin, "back", path.isEmpty() ? "<yellow>Back to admin panel" : "<yellow>Up a folder"));
        if (h.page > 0) inv.setItem(48, Icons.gui(plugin, "back", "<yellow>Previous page"));
        if (h.page < pages - 1) inv.setItem(50, Icons.gui(plugin, "next", "<yellow>Next page"));
        inv.setItem(49, Icons.gui(plugin, "info", "<aqua><bold>" + (path.isEmpty() ? "All config" : path),
                "<gray>Folders hold more settings.", "<gray>Changes save to config.yml instantly.",
                "<gray>Ability settings: <white>ability-settings", "<gray>Bosses: <white>bosses"));
        inv.setItem(53, Icons.gui(plugin, "reload", "<green>Reload config.yml from file"));
        p.openInventory(inv);
    }

    private ItemStack item(ConfigurationSection sec, String key, String full) {
        if (sec.isConfigurationSection(key)) {
            int n = sec.getConfigurationSection(key).getKeys(false).size();
            return Icons.button(plugin, Material.CHEST, "<gold><bold>" + nice(key), "<gray>" + n + " settings inside", "", "<yellow>Click to open", "<dark_gray>" + full);
        }
        Object v = sec.get(key);
        if (v instanceof Boolean b) {
            return Icons.gui(plugin, b ? "on" : "off", (b ? "<green>" : "<gray>") + nice(key) + ": " + (b ? "ON" : "OFF"), "", "<yellow>Click to toggle", "<dark_gray>" + full);
        }
        if (v instanceof Number num) {
            double d = num.doubleValue();
            double[] st = steps(num);
            return Icons.button(plugin, Material.PAPER, "<white>" + nice(key) + ": <aqua>" + fmt(d), "",
                    "<yellow>Left <gray>+" + fmt(st[0]) + "  <yellow>Right <gray>-" + fmt(st[0]),
                    "<yellow>Shift <gray>+/- " + fmt(st[1]), "<yellow>Middle / Q <gray>type a value", "<dark_gray>" + full);
        }
        if (v instanceof List<?> list) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + list.size() + " entries:");
            for (int i = 0; i < Math.min(12, list.size()); i++) lore.add("<white>- " + String.valueOf(list.get(i)).replace("<", "‹"));
            if (list.size() > 12) lore.add("<gray>...");
            lore.add("");
            lore.add("<yellow>Left <gray>add an entry (type it)");
            lore.add("<yellow>Right <gray>remove the last entry");
            lore.add("<dark_gray>" + full);
            return Icons.button(plugin, Material.BOOK, "<white>" + nice(key), lore.toArray(new String[0]));
        }
        String s = String.valueOf(v).replace("<", "‹");
        return Icons.button(plugin, Material.NAME_TAG, "<white>" + nice(key) + ": <aqua>" + (s.length() > 30 ? s.substring(0, 30) + "…" : s),
                "", "<yellow>Click <gray>type a new value in chat", "<dark_gray>" + full);
    }

    private static double[] steps(Number n) {
        double v = Math.abs(n.doubleValue());
        boolean whole = n instanceof Integer || n instanceof Long;
        if (whole) {
            if (v >= 10000) return new double[]{1000, 10000};
            if (v >= 1000) return new double[]{100, 1000};
            if (v >= 100) return new double[]{10, 100};
            return new double[]{1, 10};
        }
        if (v >= 100) return new double[]{10, 100};
        if (v >= 10) return new double[]{1, 10};
        if (v >= 2) return new double[]{0.5, 5};
        return new double[]{0.05, 0.5};
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !BanPistol.isAdmin(p)) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= 54) return;
        ClickType click = event.getClick();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
        if (raw == 45) {
            if (h.path.isEmpty()) plugin.adminHub().open(p);
            else open(p, h.path.contains(".") ? h.path.substring(0, h.path.lastIndexOf('.')) : "", 0);
            return;
        }
        if (raw == 48) { open(p, h.path, h.page - 1); return; }
        if (raw == 50) { open(p, h.path, h.page + 1); return; }
        if (raw == 53) { plugin.reloadAll(); p.sendMessage(plugin.mm(plugin.prefix() + "<green>Config reloaded.")); open(p, h.path, h.page); return; }
        if (raw >= h.keys.size()) return;
        String key = h.keys.get(raw);
        String full = h.path.isEmpty() ? key : h.path + "." + key;
        Object v = plugin.getConfig().get(full);
        if (plugin.getConfig().isConfigurationSection(full)) { open(p, full, 0); return; }
        if (v instanceof Boolean b) {
            set(full, !b);
        } else if (v instanceof Number num) {
            if (click == ClickType.MIDDLE || click == ClickType.DROP || click == ClickType.CONTROL_DROP) { ask(p, full, h.path); return; }
            double[] st = steps(num);
            double step = (click.isShiftClick() ? st[1] : st[0]) * (click.isRightClick() ? -1 : 1);
            if (num instanceof Integer || num instanceof Long) set(full, (long) Math.round(num.doubleValue() + step));
            else set(full, Math.round((num.doubleValue() + step) * 1000) / 1000.0);
        } else if (v instanceof List<?> list) {
            if (click.isRightClick()) {
                List<Object> copy = new ArrayList<>(list);
                if (!copy.isEmpty()) copy.remove(copy.size() - 1);
                set(full, copy);
            } else { ask(p, full, h.path); return; }
        } else {
            ask(p, full, h.path);
            return;
        }
        open(p, h.path, h.page);
    }

    private void set(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }

    private void ask(Player p, String full, String back) {
        typing.put(p.getUniqueId(), new String[]{full, back});
        p.closeInventory();
        Object v = plugin.getConfig().get(full);
        p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Type the " + (v instanceof List ? "entry to add to" : "new value for")
                + " <white>" + full + " <gray>in chat (or <white>cancel<gray>)."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String[] t = typing.remove(event.getPlayer().getUniqueId());
        if (t == null) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!text.equalsIgnoreCase("cancel")) {
                Object old = plugin.getConfig().get(t[0]);
                Object value = text;
                if (old instanceof List<?> list) {
                    List<Object> copy = new ArrayList<>(list);
                    copy.add(text);
                    value = copy;
                } else if (old instanceof Integer || old instanceof Long) {
                    try { value = Long.parseLong(text.replace(",", "")); } catch (NumberFormatException e) { p.sendMessage(plugin.mm(plugin.prefix() + "<red>That's not a whole number.")); open(p, t[1], 0); return; }
                } else if (old instanceof Number) {
                    try { value = Double.parseDouble(text.replace(",", "")); } catch (NumberFormatException e) { p.sendMessage(plugin.mm(plugin.prefix() + "<red>That's not a number.")); open(p, t[1], 0); return; }
                }
                set(t[0], value);
                p.sendMessage(plugin.mm(plugin.prefix() + "<green>Saved <white>" + t[0] + "<green>."));
            }
            open(p, t[1], 0);
        });
    }
}
