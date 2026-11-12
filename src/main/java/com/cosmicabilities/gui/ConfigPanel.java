package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.admin.BanPistol;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /cosmic config (admins): edit the config in game. Every change is saved to config.yml and applied instantly.
 * Main page: general settings + every ability. Ability page: its cooldown, damage and the buffs it gives on cast.
 */
public class ConfigPanel implements Listener {
    private enum Kind { BOOL, INT, DOUBLE }

    private record Setting(String path, String label, Kind kind, double step, double min, double max, Material icon, double def) {}

    // sections of the "All Settings" page: each row is one group, its label on the left
    private static final String[][] SECTIONS = {
            {"sec_game", "<#c77dff><bold>ABILITIES", "<gray>Damage, cooldowns, PvP rules"},
            {"sec_server", "<#9a9ab0><bold>EFFECTS", "<gray>Visuals and the forcefield"},
            {"sec_eco", "<#ffb703><bold>DROPS & DEATH", "<gray>Orbs, upgrades, crafting, bosses"},
            {"sec_players", "<#48cae4><bold>PLAYERS", "<gray>AFK, cash, staff tools"}};

    private static final List<List<Setting>> GROUPS = List.of(
            List.of(new Setting("abilities.damage-multiplier", "All ability damage", Kind.DOUBLE, 0.1, 0.1, 5, Material.IRON_SWORD, 0.8),
                    new Setting("abilities.cooldown-multiplier", "All ability cooldowns", Kind.DOUBLE, 0.1, 0.1, 5, Material.CLOCK, 1),
                    new Setting("abilities.combo-damage-cap", "Combo limit (0 = off)", Kind.INT, 2, 0, 100, Material.SHIELD, 0),
                    new Setting("upgrades.cooldown-reduction", "Upgrade Token cooldown cut", Kind.DOUBLE, 0.05, 0, 0.9, Material.EXPERIENCE_BOTTLE, 0.3),
                    new Setting("abilities.bonus-mechanics", "Signature bonus mechanics", Kind.BOOL, 0, 0, 0, Material.NETHER_STAR, 1),
                    new Setting("abilities.hit-players", "Abilities hit players", Kind.BOOL, 0, 0, 0, Material.PLAYER_HEAD, 1),
                    new Setting("abilities.armor-piercing", "Abilities pierce armor", Kind.BOOL, 0, 0, 0, Material.NETHERITE_CHESTPLATE, 1)),
            List.of(new Setting("effects.damage-numbers", "Floating damage numbers", Kind.BOOL, 0, 0, 0, Material.NAME_TAG, 1),
                    new Setting("upgrades.cutscene", "Upgrade cutscene", Kind.BOOL, 0, 0, 0, Material.ENDER_EYE, 1),
                    new Setting("abilities.hit-passive-mobs", "Abilities hit passive mobs", Kind.BOOL, 0, 0, 0, Material.LEAD, 0),
                    new Setting("magnetosphere.duration-seconds", "Forcefield duration (s)", Kind.INT, 1, 3, 60, Material.BEACON, 15),
                    new Setting("magnetosphere.radius", "Forcefield radius", Kind.INT, 1, 3, 15, Material.HEART_OF_THE_SEA, 6),
                    new Setting("magnetosphere.hide-from-caster", "Hide forcefield from caster", Kind.BOOL, 0, 0, 0, Material.GLASS, 0),
                    new Setting("magnetosphere.caster-can-pass", "Caster passes own forcefield", Kind.BOOL, 0, 0, 0, Material.LIGHT_BLUE_STAINED_GLASS, 1)),
            List.of(new Setting("ability-drops.from-bosses", "Bosses give ability orbs", Kind.BOOL, 0, 0, 0, Material.HEART_OF_THE_SEA, 1),
                    new Setting("ability-drops.lose-on-any-death", "Lose slot 2 on any death", Kind.BOOL, 0, 0, 0, Material.BONE, 0),
                    new Setting("death.lose-upgrade", "Lose upgrade on death", Kind.BOOL, 0, 0, 0, Material.SKELETON_SKULL, 1),
                    new Setting("reroll.craftable", "Cosmic Reroll craftable", Kind.BOOL, 0, 0, 0, Material.ECHO_SHARD, 1),
                    new Setting("bosses.min-players-to-damage", "Players needed for bosses", Kind.INT, 1, 1, 50, Material.WITHER_SKELETON_SKULL, 3),
                    new Setting("ability-drops.dragon-chance", "Dragon gives Dragon's Awakening", Kind.DOUBLE, 0.1, 0, 1, Material.DRAGON_EGG, 1),
                    new Setting("cosmic-tools.max-tree-blocks", "Cosmic Axe max logs", Kind.INT, 25, 10, 2000, Material.OAK_LOG, 200)),
            List.of(new Setting("afk.minutes-per-token", "Minutes per AFK token", Kind.INT, 5, 5, 600, Material.COMPASS, 30),
                    new Setting("afk.invulnerable", "AFK players protected", Kind.BOOL, 0, 0, 0, Material.TOTEM_OF_UNDYING, 1),
                    new Setting("economy.kill-reward", "Cash per kill ($)", Kind.INT, 100, 0, 1000000, Material.GOLD_INGOT, 1000),
                    new Setting("economy.starting-cash", "Starting cash ($)", Kind.INT, 1000, 0, 100000000, Material.GOLD_NUGGET, 10000),
                    new Setting("teams.max-size", "Max team size", Kind.INT, 1, 1, 20, Material.WHITE_BANNER, 4),
                    new Setting("ban-pistol.duration-minutes", "Ban Pistol length (0 = forever)", Kind.INT, 10, 0, 525600, Material.IRON_HORSE_ARMOR, 0),
                    new Setting("tablist.enabled", "Custom tab list", Kind.BOOL, 0, 0, 0, Material.OAK_SIGN, 1)));

    /** Plain-English explanations shown on a setting. */
    private static final Map<String, String[]> HELP = Map.of(
            "abilities.combo-damage-cap", new String[]{"<gray>The most ability damage (in HP, 2 = 1 heart)",
                    "<gray>one player can take from abilities in 4", "<gray>seconds. Stops chaining abilities into a",
                    "<gray>one-shot. Hits past the limit do nothing.", "<gray>0 = off: abilities always do full damage."},
            "abilities.damage-multiplier", new String[]{"<gray>Multiplies every normal ability's damage.", "<gray>1.0 = normal, 2.0 = double."},
            "upgrades.cooldown-reduction", new String[]{"<gray>How much shorter cooldowns get with an", "<gray>Upgrade Token. 0.30 = 30% shorter."},
            "afk.minutes-per-token", new String[]{"<gray>How long someone has to be in /afk", "<gray>to earn 1 AFK token."},
            "economy.kill-reward", new String[]{"<gray>Default cash for a kill. Set a different", "<gray>amount per player in Players -> player."});

    private static String pct(double v) { return Math.round(v * 100) + "%"; }

    /** The combat buffs an ability can give on cast (PvP-relevant only). */
    private static final String[] BUFFS = {"strength", "speed", "resistance", "regeneration", "absorption", "fire_resistance", "health_boost"};
    private static final Material[] BUFF_ICONS = {Material.BLAZE_POWDER, Material.SUGAR, Material.IRON_CHESTPLATE,
            Material.GHAST_TEAR, Material.GOLDEN_APPLE, Material.MAGMA_CREAM, Material.GLISTERING_MELON_SLICE};

    private static final int[] ABILITY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        AbilityType ability;   // null = main page / ability list
        boolean list;          // the ability list page
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;

    public ConfigPanel(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ main page

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, plugin.mm("<dark_gray>✦ <aqua><bold>All Settings</bold> <dark_gray>(saves instantly)"));
        h.inventory = inv;
        renderMain(inv);
        p.openInventory(inv);
    }

    private static int groupSlot(int row, int i) { return (row + 1) * 9 + 2 + i; }

    private void renderMain(Inventory inv) {
        fill(inv);
        ItemStack row = Icons.button(plugin, Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int r = 1; r <= 4; r++) for (int c = 1; c <= 8; c++) inv.setItem(r * 9 + c, row);
        inv.setItem(4, Icons.gui(plugin, "config", "<aqua><bold>All Settings", "<gray>Every change is saved to config.yml", "<gray>and works right away."));
        for (int r = 0; r < GROUPS.size(); r++) {
            inv.setItem((r + 1) * 9, Icons.gui(plugin, SECTIONS[r][0], SECTIONS[r][1], SECTIONS[r][2]));
            List<Setting> g = GROUPS.get(r);
            for (int i = 0; i < g.size(); i++) inv.setItem(groupSlot(r, i), settingItem(g.get(i)));
        }
        inv.setItem(47, Icons.gui(plugin, "cooldowns", "<aqua><bold>Ability Cooldowns & Damage", "<gray>Each ability on its own page.", "", "<yellow>Click to open"));
        inv.setItem(49, Icons.gui(plugin, "reload", "<green><bold>Reload from file", "<gray>Re-reads config.yml and the AFK shop"));
        inv.setItem(45, Icons.gui(plugin, "back", "<yellow>Back to admin panel"));
    }

    /** The ability list: every ability's cooldown at a glance + change them all at once. */
    public void openAbilities(Player p) {
        Holder h = new Holder();
        h.list = true;
        Inventory inv = Bukkit.createInventory(h, 54, plugin.mm("<dark_gray>✦ <aqua><bold>Ability Cooldowns & Damage"));
        h.inventory = inv;
        renderAbilities(inv);
        p.openInventory(inv);
    }

    private void renderAbilities(Inventory inv) {
        fill(inv);
        AbilityType[] types = AbilityType.values();
        for (int i = 0; i < types.length && i < ABILITY_SLOTS.length; i++) {
            AbilityType t = types[i];
            ItemStack it = Icons.button(plugin, Material.PAPER, "<bold>" + t.colored() + (t.exclusive() ? " <gold>★" : ""),
                    "<gray>Cooldown: <white>" + mins(cooldown(t)) + (cooldown(t) != t.baseCooldown ? " <dark_gray>(default " + mins(t.baseCooldown) + ")" : ""),
                    "<gray>Damage: <white>x" + fmt(damage(t)),
                    "<gray>On cast: <white>" + plugin.engine().buffText(t),
                    "", "<yellow>Click to edit");
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey("cosmic", t.id()));
            it.setItemMeta(meta);
            inv.setItem(ABILITY_SLOTS[i], it);
        }
        inv.setItem(4, Icons.gui(plugin, "cooldowns", "<aqua><bold>Ability Cooldowns & Damage", "<gray>Click an ability to change its cooldown,",
                "<gray>damage and the effects it gives on cast.", "<gold>★ <gray>= boss / dragon ability"));
        inv.setItem(48, Icons.gui(plugin, "minus", "<red><bold>ALL cooldowns -5s", "<yellow>Shift <gray>-30s"));
        inv.setItem(49, Icons.gui(plugin, "reset", "<aqua><bold>Reset ALL cooldowns", "<gray>Back to each ability's default.", "", "<red>Shift-click to confirm"));
        inv.setItem(50, Icons.gui(plugin, "plus", "<green><bold>ALL cooldowns +5s", "<yellow>Shift <gray>+30s"));
        inv.setItem(45, Icons.gui(plugin, "back", "<yellow>Back"));
    }

    private ItemStack settingItem(Setting s) {
        List<String> lore = new ArrayList<>();
        if (s.kind() == Kind.BOOL) {
            boolean v = plugin.getConfig().getBoolean(s.path(), s.def() != 0);
            lore.add("<gray>Currently: " + (v ? "<green>ON" : "<red>OFF"));
            lore.add("");
            lore.add("<yellow>Click to toggle");
        } else {
            double v = plugin.getConfig().getDouble(s.path(), s.def());
            lore.add("<gray>Currently: <white>" + (s.kind() == Kind.INT ? String.valueOf((int) v) : fmt(v)));
            lore.add("");
            lore.add("<yellow>Left-click <gray>+" + fmt(s.step()) + "   <yellow>Right-click <gray>-" + fmt(s.step()));
            lore.add("<yellow>Shift-click <gray>for x5 steps");
        }
        String[] help = HELP.get(s.path());
        if (help != null) { lore.add(""); java.util.Collections.addAll(lore, help); }
        if (s.path().equals("upgrades.cooldown-reduction")) lore.add(1, "<gray>= <white>" + pct(plugin.getConfig().getDouble(s.path(), s.def())) + " shorter cooldowns");
        lore.add("<dark_gray>" + s.path());
        if (s.kind() == Kind.BOOL) {
            boolean v = plugin.getConfig().getBoolean(s.path(), s.def() != 0);
            return Icons.gui(plugin, v ? "on" : "off", (v ? "<green>" : "<gray>") + s.label(), lore.toArray(new String[0]));
        }
        return Icons.button(plugin, s.icon(), "<white>" + s.label(), lore.toArray(new String[0]));
    }

    // ------------------------------------------------------------------ ability page

    private void openAbility(Player p, AbilityType t) {
        Holder h = new Holder();
        h.ability = t;
        Inventory inv = Bukkit.createInventory(h, 54, plugin.mm("<dark_aqua>Config: <bold>" + t.colored()));
        h.inventory = inv;
        renderAbility(inv, t);
        p.openInventory(inv);
    }

    private void renderAbility(Inventory inv, AbilityType t) {
        fill(inv);
        inv.setItem(4, Icons.ability(plugin, t, List.of()));
        inv.setItem(20, Icons.button(plugin, Material.CLOCK, "<white>Cooldown: <aqua>" + fmt(cooldown(t)) + "s <gray>(" + mins(cooldown(t)) + ")",
                "<gray>Upgrade Token makes it " + pct(plugin.getConfig().getDouble("upgrades.cooldown-reduction", 0.3)) + " shorter",
                "<gray>Default " + fmt(t.baseCooldown) + "s (before upgrade / global multiplier)", "",
                "<yellow>Left <gray>+5s  <yellow>Right <gray>-5s  <yellow>Shift <gray>±1 minute"));
        inv.setItem(22, Icons.button(plugin, Material.IRON_SWORD, "<white>Damage multiplier: <red>x" + fmt(damage(t)),
                "<gray>Higher = more damage. 1.0 = normal,", "<gray>2.0 = double, 0.5 = half.", "",
                "<yellow>Left <gray>+0.1  <yellow>Right <gray>-0.1  <yellow>Shift <gray>x5"));
        double hearts = plugin.getConfig().getDouble("ability-settings." + t.id() + ".hearts", 0);
        inv.setItem(24, Icons.button(plugin, Material.RED_DYE, "<white>Damage in hearts: " + (hearts > 0 ? "<red>" + fmt(hearts) + " ❤" : "<gray>default"),
                t.defaultHearts() > 0 ? "<gray>Total hearts this ability does" : "<gray>Hearts EVERY hit of this ability does",
                t.defaultHearts() > 0 ? "<gray>(default " + fmt(t.defaultHearts()) + " ❤). 0 = default." : "<gray>(ignores the multipliers). 0 = default.", "",
                "<yellow>Left <gray>+0.5❤  <yellow>Right <gray>-0.5❤  <yellow>Shift <gray>±2❤"));
        inv.setItem(31, Icons.button(plugin, Material.POTION, "<green><bold>Effects given on cast",
                "<gray>Set the level and length of each buff below.", "<gray>Level 0 = not given."));
        Map<String, int[]> buffs = buffs(t);
        for (int i = 0; i < BUFFS.length; i++) {
            int[] lv = buffs.getOrDefault(BUFFS[i], new int[]{0, 5});
            boolean on = lv[0] > 0;
            ItemStack it = Icons.button(plugin, BUFF_ICONS[i], (on ? "<green>" : "<gray>") + pretty(BUFFS[i])
                            + (on ? " " + roman(lv[0]) : " (off)"),
                    "<gray>Level: <white>" + lv[0] + "   <gray>Length: <white>" + lv[1] + "s", "",
                    BUFFS[i].equals("strength") ? "<yellow>Left-click <gray>on/off (Strength is capped at I)" : "<yellow>Left-click <gray>level up (wraps to off after III)",
                    "<yellow>Right-click <gray>+1s   <yellow>Shift-right <gray>-1s");
            if (on) {
                ItemMeta m = it.getItemMeta();
                m.setEnchantmentGlintOverride(true);
                it.setItemMeta(m);
            }
            inv.setItem(37 + i, it);
        }
        inv.setItem(45, Icons.gui(plugin, "back", "<yellow>Back"));
        inv.setItem(49, Icons.gui(plugin, "reset", "<red>Reset this ability to defaults"));
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !BanPistol.isStaff(p)) return;
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof Holder)) return;
        int raw = event.getRawSlot();
        ClickType click = event.getClick();
        int mult = click.isShiftClick() ? 5 : 1;

        if (h.list) {
            AbilityType[] types = AbilityType.values();
            for (int i = 0; i < types.length && i < ABILITY_SLOTS.length; i++) {
                if (raw == ABILITY_SLOTS[i]) { openAbility(p, types[i]); return; }
            }
            if (raw == 48 || raw == 50 || raw == 49) {
                if (raw == 49 && !click.isShiftClick()) { p.sendActionBar(plugin.mm("<red>Shift-click to reset every cooldown")); return; }
                double step = (raw == 48 ? -1 : 1) * (click.isShiftClick() ? 30 : 5);
                for (AbilityType t : types) {
                    double v = raw == 49 ? t.baseCooldown : Math.max(1, Math.min(3600, cooldown(t) + step));
                    plugin.getConfig().set("ability-settings." + t.id() + ".cooldown", v);
                }
                plugin.saveConfig();
                click(p);
                renderAbilities(event.getInventory());
                p.sendActionBar(plugin.mm("<aqua>All ability cooldowns updated"));
            } else if (raw == 45) {
                plugin.adminHub().open(p);
            }
            return;
        }
        if (h.ability == null) {
            for (int r = 0; r < GROUPS.size(); r++) {
                List<Setting> g = GROUPS.get(r);
                for (int i = 0; i < g.size(); i++) {
                    if (raw != groupSlot(r, i)) continue;
                    Setting s = g.get(i);
                    if (s.kind() == Kind.BOOL) {
                        set(s.path(), !plugin.getConfig().getBoolean(s.path(), s.def() != 0));
                    } else {
                        double v = plugin.getConfig().getDouble(s.path(), s.def());
                        v += (click.isRightClick() ? -1 : 1) * s.step() * mult;
                        v = Math.max(s.min(), Math.min(s.max(), Math.round(v * 100) / 100.0));
                        set(s.path(), s.kind() == Kind.INT ? (Object) (int) Math.round(v) : (Object) v);
                    }
                    click(p);
                    renderMain(event.getInventory());
                    return;
                }
            }
            if (raw == 47) openAbilities(p);
            else if (raw == 49) {
                plugin.reloadAll();
                p.sendMessage(plugin.mm(plugin.prefix() + "<green>Config reloaded from file."));
                renderMain(event.getInventory());
            } else if (raw == 45) {
                plugin.adminHub().open(p);
            }
            return;
        }

        AbilityType t = h.ability;
        String base = "ability-settings." + t.id();
        switch (raw) {
            case 20 -> {
                double v = Math.max(1, Math.min(3600, cooldown(t) + (click.isRightClick() ? -1 : 1) * (click.isShiftClick() ? 60 : 5)));
                set(base + ".cooldown", v);
            }
            case 22 -> {
                double v = Math.max(0, Math.min(10, Math.round((damage(t) + (click.isRightClick() ? -0.1 : 0.1) * mult) * 100) / 100.0));
                set(base + ".damage-multiplier", v);
            }
            case 24 -> {
                double cur = plugin.getConfig().getDouble(base + ".hearts", 0);
                double step = click.isShiftClick() ? 2 : 0.5;
                set(base + ".hearts", Math.max(0, Math.min(100, cur + (click.isRightClick() ? -step : step))));
            }
            case 45 -> { openAbilities(p); return; }
            case 49 -> {
                set(base, null);
                set("cast-buffs." + t.id(), plugin.getConfig().getDefaults() == null ? null
                        : plugin.getConfig().getDefaults().getStringList("cast-buffs." + t.id()));
            }
            default -> {
                int idx = raw - 37;
                if (idx < 0 || idx >= BUFFS.length) return;
                Map<String, int[]> buffs = buffs(t);
                int[] lv = buffs.getOrDefault(BUFFS[idx], new int[]{0, 5});
                if (click.isLeftClick() && !click.isShiftClick()) lv[0] = (lv[0] + 1) % (BUFFS[idx].equals("strength") ? 2 : 4);
                else if (click.isRightClick() && !click.isShiftClick()) lv[1] = Math.min(120, lv[1] + 1);
                else if (click.isRightClick()) lv[1] = Math.max(1, lv[1] - 1);
                buffs.put(BUFFS[idx], lv);
                saveBuffs(t, buffs);
            }
        }
        click(p);
        renderAbility(event.getInventory(), t);
    }

    // ------------------------------------------------------------------ helpers

    private static String mins(double s) {
        int m = (int) s / 60, r = (int) s % 60;
        return m > 0 ? m + "m" + (r > 0 ? " " + r + "s" : "") : r + "s";
    }

    private double cooldown(AbilityType t) {
        return plugin.getConfig().getDouble("ability-settings." + t.id() + ".cooldown", t.baseCooldown);
    }

    private double damage(AbilityType t) {
        return plugin.getConfig().getDouble("ability-settings." + t.id() + ".damage-multiplier", 1.0);
    }

    /** effect -> {level, seconds}, read from cast-buffs.<id> ("strength:1:6"). */
    private Map<String, int[]> buffs(AbilityType t) {
        Map<String, int[]> out = new LinkedHashMap<>();
        for (String raw : plugin.getConfig().getStringList("cast-buffs." + t.id())) {
            String[] parts = raw.trim().toLowerCase(Locale.ROOT).split(":");
            int level = parts.length > 1 ? parse(parts[1], 1) : 1;
            int secs = parts.length > 2 ? parse(parts[2], 5) : 5;
            out.put(parts[0], new int[]{level, secs});
        }
        return out;
    }

    private void saveBuffs(AbilityType t, Map<String, int[]> buffs) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, int[]> e : buffs.entrySet()) {
            if (e.getValue()[0] > 0) list.add(e.getKey() + ":" + e.getValue()[0] + ":" + e.getValue()[1]);
        }
        set("cast-buffs." + t.id(), list);
    }

    private void set(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        plugin.applyConfig();
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.5f);
    }

    private void fill(Inventory inv) {
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0$", "");
    }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("_")) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; default -> String.valueOf(n); };
    }
}
