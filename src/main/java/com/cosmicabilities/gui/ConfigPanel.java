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

    private static final List<Setting> GENERAL = List.of(
            new Setting("abilities.damage-multiplier", "All ability damage", Kind.DOUBLE, 0.1, 0.1, 5, Material.IRON_SWORD, 1),
            new Setting("abilities.cooldown-multiplier", "All ability cooldowns", Kind.DOUBLE, 0.1, 0.1, 5, Material.CLOCK, 1),
            new Setting("upgrades.cooldown-reduction", "Upgrade cooldown cut", Kind.DOUBLE, 0.05, 0, 0.9, Material.EXPERIENCE_BOTTLE, 0.3),
            new Setting("abilities.bonus-mechanics", "Signature bonus mechanics", Kind.BOOL, 0, 0, 0, Material.NETHER_STAR, 1),
            new Setting("abilities.hit-players", "Abilities hit players", Kind.BOOL, 0, 0, 0, Material.PLAYER_HEAD, 1),
            new Setting("abilities.hit-passive-mobs", "Abilities hit passive mobs", Kind.BOOL, 0, 0, 0, Material.LEAD, 0),
            new Setting("effects.damage-numbers", "Floating damage numbers", Kind.BOOL, 0, 0, 0, Material.NAME_TAG, 1),
            new Setting("magnetosphere.hide-from-caster", "Hide forcefield from its caster", Kind.BOOL, 0, 0, 0, Material.GLASS, 0),
            new Setting("magnetosphere.duration-seconds", "Forcefield duration (s)", Kind.INT, 1, 3, 60, Material.BEACON, 15),
            new Setting("death.lose-upgrade", "Lose upgrade on death", Kind.BOOL, 0, 0, 0, Material.SKELETON_SKULL, 1),
            new Setting("ability-drops.from-bosses", "Bosses drop ability orbs", Kind.BOOL, 0, 0, 0, Material.HEART_OF_THE_SEA, 1),
            new Setting("ability-drops.lose-on-any-death", "Lose gained ability on any death", Kind.BOOL, 0, 0, 0, Material.BONE, 0),
            new Setting("bosses.min-players-to-damage", "Default players needed for bosses", Kind.INT, 1, 1, 50, Material.WITHER_SKELETON_SKULL, 3),
            new Setting("upgrades.cutscene", "Upgrade cutscene", Kind.BOOL, 0, 0, 0, Material.ENDER_EYE, 1),
            new Setting("afk.minutes-per-token", "Minutes per AFK token", Kind.INT, 5, 5, 600, Material.COMPASS, 30),
            new Setting("afk.invulnerable", "AFK players invulnerable", Kind.BOOL, 0, 0, 0, Material.TOTEM_OF_UNDYING, 1),
            new Setting("ban-pistol.duration-minutes", "Ban Pistol length (min, 0 = forever)", Kind.INT, 10, 0, 525600, Material.IRON_HORSE_ARMOR, 0),
            new Setting("reroll.craftable", "Cosmic Reroll craftable", Kind.BOOL, 0, 0, 0, Material.ECHO_SHARD, 1),
            new Setting("abilities.armor-piercing", "Abilities pierce armor (magic dmg)", Kind.BOOL, 0, 0, 0, Material.NETHERITE_CHESTPLATE, 1),
            new Setting("magnetosphere.caster-can-pass", "Caster can pass own forcefield", Kind.BOOL, 0, 0, 0, Material.LIGHT_BLUE_STAINED_GLASS, 1),
            new Setting("magnetosphere.radius", "Forcefield radius", Kind.INT, 1, 3, 15, Material.HEART_OF_THE_SEA, 6),
            new Setting("cosmic-tools.max-tree-blocks", "Cosmic Axe max logs per tree", Kind.INT, 25, 10, 2000, Material.OAK_LOG, 200));

    /** The combat buffs an ability can give on cast (PvP-relevant only). */
    private static final String[] BUFFS = {"strength", "speed", "resistance", "regeneration", "absorption", "fire_resistance", "health_boost"};
    private static final Material[] BUFF_ICONS = {Material.BLAZE_POWDER, Material.SUGAR, Material.IRON_CHESTPLATE,
            Material.GHAST_TEAR, Material.GOLDEN_APPLE, Material.MAGMA_CREAM, Material.GLISTERING_MELON_SLICE};

    private static final int[] ABILITY_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        AbilityType ability;   // null = main page
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
        Inventory inv = Bukkit.createInventory(h, 54, plugin.mm("<dark_aqua><bold>Cosmic Config</bold> <dark_gray>(saves instantly)"));
        h.inventory = inv;
        renderMain(inv);
        p.openInventory(inv);
    }

    private void renderMain(Inventory inv) {
        fill(inv);
        for (int i = 0; i < GENERAL.size(); i++) inv.setItem(i, settingItem(GENERAL.get(i)));
        inv.setItem(22, Icons.button(plugin, Material.WRITABLE_BOOK, "<aqua><bold>Abilities below",
                "<gray>Click an ability to change its cooldown,", "<gray>damage and the effects it gives on cast."));
        AbilityType[] types = AbilityType.values();
        for (int i = 0; i < types.length; i++) {
            AbilityType t = types[i];
            ItemStack it = Icons.button(plugin, Material.PAPER, "<bold>" + t.colored(),
                    "<gray>Cooldown: <white>" + fmt(cooldown(t)) + "s",
                    "<gray>Damage: <white>x" + fmt(damage(t)),
                    "<gray>On cast: <white>" + plugin.engine().buffText(t),
                    "", "<yellow>Click to edit");
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey("cosmic", t.id()));
            it.setItemMeta(meta);
            inv.setItem(ABILITY_SLOTS[i], it);
        }
        inv.setItem(49, Icons.button(plugin, Material.REPEATER, "<green><bold>Reload from file",
                "<gray>Re-reads config.yml and the AFK shop", "<gray>(same as /creload)"));
        inv.setItem(53, Icons.button(plugin, Material.ARROW, "<yellow>Back to admin panel"));
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
        lore.add("<dark_gray>" + s.path());
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
        inv.setItem(20, Icons.button(plugin, Material.CLOCK, "<white>Cooldown: <aqua>" + fmt(cooldown(t)) + "s",
                "<gray>Default " + fmt(t.baseCooldown) + "s (before upgrade / global multiplier)", "",
                "<yellow>Left <gray>+1s  <yellow>Right <gray>-1s  <yellow>Shift <gray>x5"));
        inv.setItem(24, Icons.button(plugin, Material.IRON_SWORD, "<white>Damage: <red>x" + fmt(damage(t)),
                "<gray>Multiplies only this ability's damage", "",
                "<yellow>Left <gray>+0.1  <yellow>Right <gray>-0.1  <yellow>Shift <gray>x5"));
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
        inv.setItem(45, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        inv.setItem(49, Icons.button(plugin, Material.LAVA_BUCKET, "<red>Reset this ability to defaults"));
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

        if (h.ability == null) {
            if (raw < GENERAL.size()) {
                Setting s = GENERAL.get(raw);
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
            for (int i = 0; i < ABILITY_SLOTS.length; i++) {
                if (raw == ABILITY_SLOTS[i]) { openAbility(p, AbilityType.values()[i]); return; }
            }
            if (raw == 49) {
                plugin.reloadAll();
                p.sendMessage(plugin.mm(plugin.prefix() + "<green>Config reloaded from file."));
                renderMain(event.getInventory());
            } else if (raw == 53) {
                plugin.adminHub().open(p);
            }
            return;
        }

        AbilityType t = h.ability;
        String base = "ability-settings." + t.id();
        switch (raw) {
            case 20 -> {
                double v = Math.max(1, Math.min(600, cooldown(t) + (click.isRightClick() ? -1 : 1) * mult));
                set(base + ".cooldown", v);
            }
            case 24 -> {
                double v = Math.max(0, Math.min(10, Math.round((damage(t) + (click.isRightClick() ? -0.1 : 0.1) * mult) * 100) / 100.0));
                set(base + ".damage-multiplier", v);
            }
            case 45 -> { open(p); return; }
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
        ItemStack pane = Icons.button(plugin, Material.GRAY_STAINED_GLASS_PANE, " ");
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
