package com.cosmicabilities.recipe;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.gui.Icons;
import com.cosmicabilities.item.CosmicMaterial;
import com.cosmicabilities.item.CosmicTools;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Craftable cosmic items (Cosmic Reroll, Cosmic Pickaxe, Cosmic Axe). Every recipe lives in config.yml under
 * recipes.&lt;id&gt; (enabled / shape / ingredients) and can be edited in game: /adminpanel -> Recipes.
 * Ingredients are Minecraft item names (DIAMOND) or cosmic fragments (cosmic:astral_fragment).
 */
public class RecipeManager implements Listener {
    public static final String REROLL = "reroll", PICKAXE = "cosmic_pickaxe", AXE = "cosmic_axe";
    public static final List<String> IDS = List.of(REROLL, PICKAXE, AXE);
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT = 24, ARROW = 23;

    /** A parsed recipe: 9 cells (null = empty) as config ingredient names. */
    public record Grid(String[] cells) {}

    public static final class View implements InventoryHolder {
        Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    public static final class Editor implements InventoryHolder {
        Inventory inventory;
        final String id;
        final String[] cells = new String[9];
        Editor(String id) { this.id = id; }
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    public static final class Picker implements InventoryHolder {
        Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final Map<String, Supplier<ItemStack>> results = new LinkedHashMap<>();
    private final Map<String, NamespacedKey> keys = new LinkedHashMap<>();

    public RecipeManager(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        results.put(REROLL, () -> plugin.orbs().reroll());
        results.put(PICKAXE, () -> plugin.tools().create(CosmicTools.Tool.PICKAXE));
        results.put(AXE, () -> plugin.tools().create(CosmicTools.Tool.AXE));
        for (String id : IDS) keys.put(id, new NamespacedKey(plugin, id));
    }

    public ItemStack result(String id) {
        return results.get(id).get();
    }

    public String title(String id) {
        return switch (id) {
            case REROLL -> "<gradient:#f72585:#4cc9f0><bold>Cosmic Reroll";
            case PICKAXE -> CosmicTools.Tool.PICKAXE.name;
            default -> CosmicTools.Tool.AXE.name;
        };
    }

    public boolean enabled(String id) {
        if (id.equals(REROLL) && !plugin.getConfig().getBoolean("reroll.craftable", true)) return false;
        return plugin.getConfig().getBoolean("recipes." + id + ".enabled", true);
    }

    // ------------------------------------------------------------------ config <-> grid

    public Grid grid(String id) {
        String[] cells = new String[9];
        List<String> shape = plugin.getConfig().getStringList("recipes." + id + ".shape");
        ConfigurationSection ing = plugin.getConfig().getConfigurationSection("recipes." + id + ".ingredients");
        for (int row = 0; row < 3 && row < shape.size(); row++) {
            String line = shape.get(row);
            for (int col = 0; col < 3 && col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == ' ' || ing == null) continue;
                String v = ing.getString(String.valueOf(c));
                if (v != null && choice(v) != null) cells[row * 3 + col] = v;
            }
        }
        return new Grid(cells);
    }

    private void saveGrid(String id, String[] cells) {
        Map<String, Character> letters = new LinkedHashMap<>();
        StringBuilder[] rows = {new StringBuilder(), new StringBuilder(), new StringBuilder()};
        for (int i = 0; i < 9; i++) {
            String v = cells[i];
            if (v == null) { rows[i / 3].append(' '); continue; }
            Character c = letters.get(v);
            if (c == null) {
                c = (char) ('A' + letters.size());
                letters.put(v, c);
            }
            rows[i / 3].append(c);
        }
        String base = "recipes." + id;
        plugin.getConfig().set(base + ".shape", List.of(rows[0].toString(), rows[1].toString(), rows[2].toString()));
        plugin.getConfig().set(base + ".ingredients", null);
        for (Map.Entry<String, Character> e : letters.entrySet()) {
            plugin.getConfig().set(base + ".ingredients." + e.getValue(), e.getKey());
        }
        plugin.saveConfig();
        registerAll();
    }

    /** "DIAMOND" -> material choice; "cosmic:astral_fragment" -> that exact fragment. */
    private RecipeChoice choice(String v) {
        if (v.toLowerCase(Locale.ROOT).startsWith("cosmic:")) {
            CosmicMaterial m = CosmicMaterial.fromId(v.substring(7));
            return m == null ? null : new RecipeChoice.ExactChoice(m.create(1));
        }
        Material m = Material.matchMaterial(v);
        return m == null || m.isAir() || !m.isItem() ? null : new RecipeChoice.MaterialChoice(m);
    }

    private ItemStack display(String v) {
        if (v == null) return null;
        if (v.toLowerCase(Locale.ROOT).startsWith("cosmic:")) {
            CosmicMaterial m = CosmicMaterial.fromId(v.substring(7));
            return m == null ? null : m.create(1);
        }
        Material m = Material.matchMaterial(v);
        return m == null ? null : new ItemStack(m);
    }

    public String pretty(String v) {
        if (v == null) return "";
        if (v.toLowerCase(Locale.ROOT).startsWith("cosmic:")) {
            CosmicMaterial m = CosmicMaterial.fromId(v.substring(7));
            return m == null ? v : m.displayName;
        }
        StringBuilder sb = new StringBuilder();
        for (String w : v.toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** Ingredient name for an item someone put in the editor grid. */
    private String ingredientOf(ItemStack it) {
        CosmicMaterial cm = CosmicMaterial.of(it);
        if (cm != null) return "cosmic:" + cm.id();
        return it.getType().name();
    }

    // ------------------------------------------------------------------ registering

    public void registerAll() {
        for (String id : IDS) {
            NamespacedKey key = keys.get(id);
            Bukkit.removeRecipe(key);
            if (!enabled(id)) continue;
            Grid g = grid(id);
            String[] shape = new String[3];
            Map<Character, RecipeChoice> map = new LinkedHashMap<>();
            Map<String, Character> letters = new LinkedHashMap<>();
            boolean any = false;
            for (int row = 0; row < 3; row++) {
                StringBuilder sb = new StringBuilder();
                for (int col = 0; col < 3; col++) {
                    String v = g.cells()[row * 3 + col];
                    if (v == null) { sb.append(' '); continue; }
                    Character c = letters.computeIfAbsent(v, k -> (char) ('A' + letters.size()));
                    map.putIfAbsent(c, choice(v));
                    sb.append(c);
                    any = true;
                }
                shape[row] = sb.toString();
            }
            if (!any) continue;
            try {
                ShapedRecipe r = new ShapedRecipe(key, result(id));
                r.shape(shape);
                for (Map.Entry<Character, RecipeChoice> e : map.entrySet()) r.setIngredient(e.getKey(), e.getValue());
                Bukkit.addRecipe(r);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Recipe '" + id + "' is invalid: " + e.getMessage());
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) discover(p);
    }

    private void discover(Player p) {
        for (String id : IDS) if (enabled(id)) p.discoverRecipe(keys.get(id));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discover(event.getPlayer());
    }

    // ------------------------------------------------------------------ lore + views

    /**
     * Hover text for a craftable item in a menu: the crafting recipe if the player doesn't have one yet,
     * otherwise just what it does.
     */
    public List<String> recipeLore(String id) {
        List<String> out = new ArrayList<>();
        if (!enabled(id)) {
            out.add("<red>Not craftable on this server.");
            return out;
        }
        Grid g = grid(id);
        Map<String, Character> letters = new LinkedHashMap<>();
        out.add("<gold>Recipe <gray>(crafting table):");
        for (int row = 0; row < 3; row++) {
            StringBuilder sb = new StringBuilder("  ");
            for (int col = 0; col < 3; col++) {
                String v = g.cells()[row * 3 + col];
                if (v == null) { sb.append("<dark_gray>[ ]"); continue; }
                char c = letters.computeIfAbsent(v, k -> (char) ('A' + letters.size()));
                sb.append("<dark_gray>[<aqua>").append(c).append("<dark_gray>]");
            }
            out.add(sb.toString());
        }
        for (Map.Entry<String, Character> e : letters.entrySet()) {
            out.add("  <aqua>" + e.getValue() + " <gray>= <white>" + pretty(e.getKey()));
        }
        out.add("<yellow>Click <gray>to see it in a crafting grid");
        return out;
    }

    public List<String> details(String id) {
        return switch (id) {
            case REROLL -> List.of("<gray>Right-click it to spin the wheel and", "<gray>swap the ability you joined with",
                    "<gray>(slot 1) for a new random one.", "<dark_gray>Announced to the whole server.");
            case PICKAXE -> List.of("<gray>Netherite pickaxe that mines a <white>3x3", "<gray>area. <yellow>Shift + right-click <gray>toggles it.");
            default -> List.of("<gray>Netherite axe that chops down the", "<gray>whole tree. <yellow>Shift + right-click <gray>toggles it.");
        };
    }

    public boolean owns(Player p, String id) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (id.equals(REROLL) && plugin.orbs().isReroll(it)) return true;
            CosmicTools.Tool t = plugin.tools().toolOf(it);
            if (t != null && t.id.equals(id)) return true;
        }
        return false;
    }

    /** A menu icon for this craftable item: recipe on hover if they don't own one yet, details once they do. */
    public ItemStack icon(Player viewer, String id) {
        ItemStack it = result(id);
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = new ArrayList<>();
        boolean owned = owns(viewer, id);
        for (String l : owned ? details(id) : recipeLore(id)) lore.add(plugin.mm("<!italic>" + l));
        if (owned) {
            lore.add(Component.empty());
            lore.add(plugin.mm("<!italic><green>You have one."));
        }
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    /** A crafting-table style view of one recipe. */
    public void openView(Player p, String id) {
        View v = new View();
        Inventory inv = Bukkit.createInventory(v, 45, plugin.mm("<dark_gray>Recipe: " + title(id)));
        v.inventory = inv;
        frame(inv);
        Grid g = grid(id);
        for (int i = 0; i < 9; i++) {
            ItemStack shown = display(g.cells()[i]);
            inv.setItem(GRID[i], shown);
        }
        inv.setItem(ARROW, Icons.button(plugin, Material.ARROW, "<gray>makes"));
        ItemStack res = result(id);
        ItemMeta meta = res.getItemMeta();
        List<Component> lore = new ArrayList<>();
        for (String l : details(id)) lore.add(plugin.mm("<!italic>" + l));
        if (!enabled(id)) lore.add(plugin.mm("<!italic><red>Not craftable on this server."));
        meta.lore(lore);
        res.setItemMeta(meta);
        inv.setItem(RESULT, res);
        inv.setItem(40, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        p.openInventory(inv);
    }

    private void frame(Inventory inv) {
        ItemStack pane = Icons.button(plugin, Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        for (int s : GRID) inv.setItem(s, null);
        inv.setItem(RESULT, null);
    }

    // ------------------------------------------------------------------ admin: recipe picker + editor

    public void openPicker(Player admin) {
        Picker h = new Picker();
        Inventory inv = Bukkit.createInventory(h, 27, plugin.mm("<dark_red><bold>Recipes</bold> <dark_gray>- click to edit"));
        h.inventory = inv;
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        int[] at = {11, 13, 15};
        for (int i = 0; i < IDS.size(); i++) {
            String id = IDS.get(i);
            ItemStack it = result(id);
            ItemMeta meta = it.getItemMeta();
            List<Component> lore = new ArrayList<>();
            for (String l : recipeLore(id)) if (!l.startsWith("<yellow>Click")) lore.add(plugin.mm("<!italic>" + l));
            lore.add(Component.empty());
            lore.add(plugin.mm("<!italic>" + (enabled(id) ? "<green>Craftable" : "<red>Disabled")));
            lore.add(plugin.mm("<!italic><yellow>Left-click <gray>edit the recipe"));
            lore.add(plugin.mm("<!italic><yellow>Right-click <gray>turn crafting on/off"));
            lore.add(plugin.mm("<!italic><yellow>Shift-click <gray>get one"));
            meta.lore(lore);
            it.setItemMeta(meta);
            inv.setItem(at[i], it);
        }
        inv.setItem(22, Icons.button(plugin, Material.ARROW, "<yellow>Back to admin panel"));
        admin.openInventory(inv);
    }

    public void openEditor(Player admin, String id) {
        Editor e = new Editor(id);
        Inventory inv = Bukkit.createInventory(e, 45, plugin.mm("<dark_red>Edit recipe: " + title(id)));
        e.inventory = inv;
        System.arraycopy(grid(id).cells(), 0, e.cells, 0, 9);
        renderEditor(e);
        admin.openInventory(inv);
    }

    private void renderEditor(Editor e) {
        Inventory inv = e.inventory;
        frame(inv);
        for (int i = 0; i < 9; i++) inv.setItem(GRID[i], display(e.cells[i]));
        inv.setItem(ARROW, Icons.button(plugin, Material.ARROW, "<gray>makes"));
        inv.setItem(RESULT, result(e.id));
        inv.setItem(4, Icons.button(plugin, Material.BOOK, "<gold><bold>How to edit",
                "<gray>Pick up any item from <white>your inventory<gray>,",
                "<gray>then click a grid slot to put it there",
                "<gray>(your item is not used up).",
                "<gray>Click a grid slot with an empty hand to clear it.",
                "<gray>Fragments work too (cosmic materials)."));
        inv.setItem(38, Icons.button(plugin, Material.LIME_CONCRETE, "<green><bold>Save recipe", "<gray>Takes effect immediately"));
        inv.setItem(40, Icons.button(plugin, Material.LAVA_BUCKET, "<red>Reset to default recipe"));
        inv.setItem(42, Icons.button(plugin, Material.BARRIER, "<yellow>Cancel"));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder h = event.getInventory().getHolder();
        if (h instanceof View || h instanceof Picker) { event.setCancelled(true); return; }
        if (h instanceof Editor) {
            for (int raw : event.getRawSlots()) if (raw < event.getInventory().getSize()) { event.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder h = event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (h instanceof View) {
            event.setCancelled(true);
            if (event.getRawSlot() == 40) plugin.menu().open(p);
            return;
        }
        if (h instanceof Picker) {
            event.setCancelled(true);
            if (!BanPistol.isStaff(p)) { p.closeInventory(); return; }
            int raw = event.getRawSlot();
            if (raw == 22) { plugin.adminHub().open(p); return; }
            int idx = raw == 11 ? 0 : raw == 13 ? 1 : raw == 15 ? 2 : -1;
            if (idx < 0) return;
            String id = IDS.get(idx);
            ClickType click = event.getClick();
            if (click.isShiftClick()) {
                p.getInventory().addItem(result(id));
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            } else if (click.isRightClick()) {
                boolean on = !enabled(id);
                plugin.getConfig().set("recipes." + id + ".enabled", on);
                if (id.equals(REROLL)) plugin.getConfig().set("reroll.craftable", on);
                plugin.saveConfig();
                registerAll();
                openPicker(p);
            } else {
                openEditor(p, id);
            }
            return;
        }
        if (!(h instanceof Editor e)) return;
        int raw = event.getRawSlot();
        if (raw >= event.getInventory().getSize()) {
            // their own inventory: allow picking items up (shift-click would move them into the menu, so block that)
            if (event.getClick().isShiftClick()) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!BanPistol.isStaff(p)) { p.closeInventory(); return; }
        for (int i = 0; i < 9; i++) {
            if (raw != GRID[i]) continue;
            ItemStack cursor = event.getCursor();
            e.cells[i] = cursor == null || cursor.getType().isAir() ? null : ingredientOf(cursor);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
            renderEditor(e);
            return;
        }
        switch (raw) {
            case 38 -> {
                boolean any = false;
                for (String c : e.cells) any |= c != null;
                if (!any) { p.sendMessage(plugin.mm(plugin.prefix() + "<red>The recipe needs at least one item.")); return; }
                saveGrid(e.id, e.cells);
                p.sendMessage(plugin.mm(plugin.prefix() + "<green>Saved the " + title(e.id) + " <green>recipe."));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
                returnCursorThen(p, () -> openPicker(p));
            }
            case 40 -> {
                plugin.getConfig().set("recipes." + e.id, null);
                ConfigurationSection def = plugin.getConfig().getDefaults() == null ? null
                        : plugin.getConfig().getDefaults().getConfigurationSection("recipes." + e.id);
                if (def != null) {
                    plugin.getConfig().set("recipes." + e.id + ".enabled", def.getBoolean("enabled", true));
                    plugin.getConfig().set("recipes." + e.id + ".shape", def.getStringList("shape"));
                    ConfigurationSection ing = def.getConfigurationSection("ingredients");
                    if (ing != null) for (String k : ing.getKeys(false)) plugin.getConfig().set("recipes." + e.id + ".ingredients." + k, ing.getString(k));
                }
                plugin.saveConfig();
                registerAll();
                System.arraycopy(grid(e.id).cells(), 0, e.cells, 0, 9);
                renderEditor(e);
            }
            case 42 -> returnCursorThen(p, () -> openPicker(p));
            default -> { }
        }
    }

    /** Opening a new menu while holding an item on the cursor would drop it; put it back in their inventory first. */
    private void returnCursorThen(Player p, Runnable next) {
        ItemStack cursor = p.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            p.setItemOnCursor(null);
            for (ItemStack left : p.getInventory().addItem(cursor).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        plugin.getServer().getScheduler().runTask(plugin, next);
    }
}
