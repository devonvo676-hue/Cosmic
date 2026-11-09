package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.admin.RankTags;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.item.CosmicMaterial;
import com.cosmicabilities.item.CosmicTools;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /adminpanel: one place for everything an admin can do. Every button opens a page or does the thing directly:
 * abilities, boss fights, config, AFK shop editor, recipes, players (set/spin/upgrade/orbs/tokens), give items,
 * announce, reload, unban, name tags, cooldowns and clean-up.
 */
public class AdminHub implements Listener {
    enum Page { HUB, GIVE, PLAYERS, PLAYER, PICK, UNBAN }
    enum PickMode { SLOT1, SLOT2, ORB }

    public static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Page page;
        UUID target;
        PickMode mode;
        final Map<Integer, Object> slots = new HashMap<>();
        Holder(Page page) { this.page = page; }
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private static final int H_ABILITIES = 10, H_BOSS = 11, H_CONFIG = 12, H_SHOP = 13, H_RECIPES = 14, H_PLAYERS = 15,
            H_GIVE = 16, H_ANNOUNCE = 28, H_RELOAD = 29, H_UNBAN = 30, H_TAG_ADMIN = 32, H_TAG_OWNER = 33, H_TAG_OFF = 34,
            H_RESET = 37, H_SWEEP = 38, H_DESPAWN = 39, H_SPIN = 40, H_CLOSE = 49;

    private final CosmicAbilitiesPlugin plugin;
    private final Set<UUID> announcing = ConcurrentHashMap.newKeySet();

    public AdminHub(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private Inventory create(Holder h, int size, String title) {
        Inventory inv = Bukkit.createInventory(h, size, plugin.mm(title));
        h.inventory = inv;
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) inv.setItem(i, pane);
        return inv;
    }

    // ------------------------------------------------------------------ hub

    public void open(Player admin) {
        Holder h = new Holder(Page.HUB);
        Inventory inv = create(h, 54, "<dark_red><bold>Cosmic Admin Panel");
        inv.setItem(4, Icons.button(plugin, Material.NETHER_STAR, "<gradient:#9b5de5:#00bbf9><bold>Cosmic Abilities " + plugin.getPluginMeta().getVersion(),
                "<gray>Everything you can do as an admin is here.", "<gray>Online: <white>" + Bukkit.getOnlinePlayers().size()));
        inv.setItem(H_ABILITIES, Icons.button(plugin, Material.AMETHYST_CLUSTER, "<light_purple><bold>Abilities",
                "<gray>Equip any ability on yourself,", "<gray>test-cast, materials, Ban Pistol."));
        inv.setItem(H_BOSS, Icons.button(plugin, Material.WITHER_SKELETON_SKULL, "<red><bold>Boss Fights",
                "<gray>Pick a boss, countdown and how many", "<gray>players must be there to start."));
        inv.setItem(H_CONFIG, Icons.button(plugin, Material.COMPARATOR, "<aqua><bold>Config",
                "<gray>Global settings + every ability's", "<gray>cooldown, damage and cast effects."));
        inv.setItem(H_SHOP, Icons.button(plugin, Material.EMERALD, "<green><bold>AFK Shop Editor",
                "<gray>Click items in your inventory to add them,", "<gray>change prices, remove entries."));
        inv.setItem(H_RECIPES, Icons.button(plugin, Material.CRAFTING_TABLE, "<gold><bold>Recipes",
                "<gray>Change how the Cosmic Reroll, Pickaxe", "<gray>and Axe are crafted, or turn crafting off."));
        inv.setItem(H_PLAYERS, Icons.button(plugin, Material.PLAYER_HEAD, "<yellow><bold>Players",
                "<gray>Set or spin someone's abilities, upgrades,", "<gray>orbs, AFK tokens, cooldowns."));
        inv.setItem(H_GIVE, Icons.button(plugin, Material.CHEST, "<white><bold>Give Items",
                "<gray>Rerolls, cosmic tools, shards, upgrade", "<gray>tokens, orbs, fragments, cash, Ban Pistol."));
        inv.setItem(H_ANNOUNCE, Icons.button(plugin, Material.BELL, "<gold><bold>Announce",
                "<gray>Type your message in chat next.", "<gray>Use <white>title | subtitle <gray>for two lines."));
        inv.setItem(H_RELOAD, Icons.button(plugin, Material.REPEATER, "<green><bold>Reload",
                "<gray>Re-read config.yml + afkshop.yml now."));
        inv.setItem(H_UNBAN, Icons.button(plugin, Material.IRON_BARS, "<red><bold>Banned Players",
                "<gray>Click someone to unban them."));
        inv.setItem(H_TAG_ADMIN, Icons.button(plugin, Material.RED_BANNER, "<red><bold>[ADMIN] tag", "<gray>Requires admin or OP"));
        inv.setItem(H_TAG_OWNER, Icons.button(plugin, Material.YELLOW_BANNER, "<gold><bold>[OWNER] tag", "<gray>Requires OP"));
        inv.setItem(H_TAG_OFF, Icons.button(plugin, Material.WHITE_BANNER, "<white>Remove my tag"));
        inv.setItem(H_RESET, Icons.button(plugin, Material.CLOCK, "<aqua>Reset my cooldowns",
                "<gray>Shift-click: everyone's"));
        inv.setItem(H_SWEEP, Icons.button(plugin, Material.BRUSH, "<aqua>Clean up effects",
                "<gray>Removes any stuck ability visuals."));
        inv.setItem(H_DESPAWN, Icons.button(plugin, Material.TNT, "<red>Despawn all bosses"));
        inv.setItem(H_SPIN, Icons.button(plugin, Material.ECHO_SHARD, "<light_purple>Test the spin wheel",
                "<gray>Spins a new slot 1 ability for yourself."));
        inv.setItem(H_CLOSE, Icons.button(plugin, Material.BARRIER, "<red>Close"));
        admin.openInventory(inv);
        admin.playSound(admin.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.3f);
    }

    // ------------------------------------------------------------------ give items

    private void openGive(Player admin) {
        Holder h = new Holder(Page.GIVE);
        Inventory inv = create(h, 54, "<dark_red>Give Items <dark_gray>- shift = a stack");
        put(h, 10, plugin.orbs().reroll(), "reroll");
        put(h, 11, plugin.tools().create(CosmicTools.Tool.PICKAXE), "pickaxe");
        put(h, 12, plugin.tools().create(CosmicTools.Tool.AXE), "axe");
        put(h, 13, plugin.banPistol().create(), "pistol");
        put(h, 14, plugin.items().shard(16), "shard");
        put(h, 15, plugin.items().token(1), "token");
        put(h, 16, plugin.coinflip().coin("<gold><bold>+$100,000", "<gray>Click: give yourself $100,000", "<gray>Shift: $1,000,000"), "cash");
        CosmicMaterial[] mats = CosmicMaterial.values();
        for (int i = 0; i < mats.length; i++) put(h, 19 + i, mats[i].create(16), mats[i]);
        AbilityType[] types = AbilityType.values();
        for (int i = 0; i < types.length; i++) put(h, 28 + i + (i >= 7 ? 2 : 0), plugin.orbs().orb(types[i], 1), types[i]);
        inv.setItem(49, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        admin.openInventory(inv);
    }

    private void put(Holder h, int slot, ItemStack it, Object key) {
        h.inventory.setItem(slot, it);
        h.slots.put(slot, key);
    }

    // ------------------------------------------------------------------ players

    private void openPlayers(Player admin) {
        Holder h = new Holder(Page.PLAYERS);
        Inventory inv = create(h, 54, "<dark_red>Players <dark_gray>- click one");
        int i = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (i >= 45) break;
            PlayerData d = plugin.data().get(online.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(online);
            meta.displayName(plugin.mm("<!italic><yellow>" + online.getName()));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.mm("<!italic><gray>Slot 1: " + slotText(d, 1)));
            lore.add(plugin.mm("<!italic><gray>Slot 2: " + slotText(d, 2)));
            lore.add(plugin.mm("<!italic><gray>AFK tokens: <white>" + (d == null ? 0 : d.afkTokens())));
            meta.lore(lore);
            head.setItemMeta(meta);
            put(h, i++, head, online.getUniqueId());
        }
        inv.setItem(49, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        admin.openInventory(inv);
    }

    private static String slotText(PlayerData d, int slot) {
        if (d == null || d.slot(slot).isEmpty()) return "<dark_gray>empty";
        AbilitySlot s = d.slot(slot);
        return s.ability().colored() + " <gray>(" + AbilityType.tierName(s.tier()) + ")";
    }

    private void openPlayer(Player admin, UUID target) {
        Player t = Bukkit.getPlayer(target);
        if (t == null) { openPlayers(admin); return; }
        PlayerData d = plugin.data().get(target);
        Holder h = new Holder(Page.PLAYER);
        h.target = target;
        Inventory inv = create(h, 54, "<dark_red>Manage <white>" + t.getName());
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(t);
        sm.displayName(plugin.mm("<!italic><yellow><bold>" + t.getName()));
        sm.lore(List.of(plugin.mm("<!italic><gray>Slot 1: " + slotText(d, 1)), plugin.mm("<!italic><gray>Slot 2: " + slotText(d, 2)),
                plugin.mm("<!italic><gray>AFK tokens: <white>" + (d == null ? 0 : d.afkTokens()))));
        head.setItemMeta(sm);
        inv.setItem(4, head);
        inv.setItem(19, Icons.button(plugin, Material.ECHO_SHARD, "<light_purple><bold>Spin the wheel",
                "<gray>Rolls a new random slot 1 ability", "<gray>(same as a Cosmic Reroll, not announced)."));
        inv.setItem(20, Icons.button(plugin, Material.NETHER_STAR, "<white>Set slot 1", "<gray>Current: " + slotText(d, 1)));
        inv.setItem(21, Icons.button(plugin, Material.HEART_OF_THE_SEA, "<white>Set slot 2", "<gray>Current: " + slotText(d, 2)));
        inv.setItem(22, Icons.button(plugin, Material.LAVA_BUCKET, "<red>Clear slot 2"));
        inv.setItem(23, Icons.button(plugin, Material.EXPERIENCE_BOTTLE, "<aqua>Toggle upgrade",
                "<yellow>Left <gray>slot 1   <yellow>Right <gray>slot 2"));
        inv.setItem(24, Icons.button(plugin, Material.CLOCK, "<aqua>Reset their cooldowns"));
        inv.setItem(25, Icons.button(plugin, Material.ENDER_PEARL, "<green>Teleport to them"));
        inv.setItem(29, Icons.button(plugin, Material.SUNFLOWER, "<gold>AFK tokens: <white>" + (d == null ? 0 : d.afkTokens()),
                "<yellow>Left <gray>+1  <yellow>Right <gray>-1  <yellow>Shift <gray>x10"));
        inv.setItem(30, Icons.button(plugin, Material.ENDER_EYE, "<light_purple>Give an ability orb", "<gray>Pick which ability"));
        inv.setItem(31, Icons.button(plugin, Material.AMETHYST_SHARD, "<light_purple>Give a Cosmic Reroll"));
        inv.setItem(32, Icons.button(plugin, Material.NETHERITE_PICKAXE, "<light_purple>Give Cosmic Pickaxe"));
        inv.setItem(33, Icons.button(plugin, Material.NETHERITE_AXE, "<light_purple>Give Cosmic Axe"));
        inv.setItem(49, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        admin.openInventory(inv);
    }

    private void openPick(Player admin, UUID target, PickMode mode) {
        Holder h = new Holder(Page.PICK);
        h.target = target;
        h.mode = mode;
        Inventory inv = create(h, 54, "<dark_red>" + switch (mode) {
            case SLOT1 -> "Set slot 1";
            case SLOT2 -> "Set slot 2";
            case ORB -> "Give an orb";
        });
        AbilityType[] types = AbilityType.values();
        for (int i = 0; i < types.length; i++) put(h, AbilitiesPanel.SLOTS[i], Icons.ability(plugin, types[i], List.of("<yellow>Click to choose")), types[i]);
        inv.setItem(49, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        admin.openInventory(inv);
    }

    private void openUnban(Player admin) {
        Holder h = new Holder(Page.UNBAN);
        Inventory inv = create(h, 54, "<dark_red>Banned players <dark_gray>- click to unban");
        int i = 0;
        for (OfflinePlayer op : Bukkit.getBannedPlayers()) {
            if (i >= 45 || op.getName() == null) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(op);
            meta.displayName(plugin.mm("<!italic><red>" + op.getName()));
            meta.lore(List.of(plugin.mm("<!italic><yellow>Click to unban")));
            head.setItemMeta(meta);
            put(h, i++, head, op.getName());
        }
        if (i == 0) inv.setItem(22, Icons.button(plugin, Material.LIME_DYE, "<green>Nobody is banned"));
        inv.setItem(49, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        admin.openInventory(inv);
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
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (event.getClickedInventory() == null || event.getRawSlot() >= event.getInventory().getSize()) return;
        if (!BanPistol.isStaff(admin)) { admin.closeInventory(); return; }
        int raw = event.getRawSlot();
        ClickType click = event.getClick();
        admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
        switch (h.page) {
            case HUB -> hubClick(admin, raw, click);
            case GIVE -> giveClick(admin, h, raw, click);
            case PLAYERS -> {
                if (raw == 49) { open(admin); return; }
                if (h.slots.get(raw) instanceof UUID id) openPlayer(admin, id);
            }
            case PLAYER -> playerClick(admin, h, raw, click);
            case PICK -> pickClick(admin, h, raw);
            case UNBAN -> {
                if (raw == 49) { open(admin); return; }
                if (h.slots.get(raw) instanceof String name) {
                    plugin.miscCommands().unban(admin, new String[]{name});
                    openUnban(admin);
                }
            }
        }
    }

    private void hubClick(Player admin, int raw, ClickType click) {
        switch (raw) {
            case H_ABILITIES -> plugin.adminPanel().open(admin);
            case H_BOSS -> plugin.bossFightPanel().open(admin);
            case H_CONFIG -> plugin.configPanel().open(admin);
            case H_SHOP -> plugin.afkShop().openEditor(admin);
            case H_RECIPES -> plugin.recipes().openPicker(admin);
            case H_PLAYERS -> openPlayers(admin);
            case H_GIVE -> openGive(admin);
            case H_ANNOUNCE -> {
                admin.closeInventory();
                announcing.add(admin.getUniqueId());
                admin.sendMessage(plugin.mm(plugin.prefix() + "<gold>Type your announcement in chat <gray>(or <white>cancel<gray>)."
                        + " Use <white>title | subtitle <gray>for two lines."));
            }
            case H_RELOAD -> {
                plugin.reloadAll();
                admin.sendMessage(plugin.mm(plugin.prefix() + "<green>Config, AFK shop and recipes reloaded - changes are live."));
            }
            case H_UNBAN -> openUnban(admin);
            case H_TAG_ADMIN -> tag(admin, RankTags.Rank.ADMIN);
            case H_TAG_OWNER -> tag(admin, RankTags.Rank.OWNER);
            case H_TAG_OFF -> {
                plugin.tags().clear(admin);
                admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>Tag removed."));
            }
            case H_RESET -> {
                if (click.isShiftClick()) {
                    for (Player p : Bukkit.getOnlinePlayers()) plugin.engine().resetCooldown(p.getUniqueId());
                    admin.sendMessage(plugin.mm(plugin.prefix() + "<aqua>Everyone's cooldowns were reset."));
                } else {
                    plugin.engine().resetCooldown(admin.getUniqueId());
                    admin.sendMessage(plugin.mm(plugin.prefix() + "<aqua>Your cooldowns were reset."));
                }
            }
            case H_SWEEP -> admin.sendMessage(plugin.mm(plugin.prefix() + "<aqua>Removed " + Fx.sweep(true) + " effect entities."));
            case H_DESPAWN -> admin.sendMessage(plugin.mm(plugin.prefix() + "<red>Despawned " + plugin.bosses().despawnAll() + " bosses."));
            case H_SPIN -> {
                admin.closeInventory();
                plugin.roll().spin(admin, RollPanel.Reason.ADMIN);
            }
            case H_CLOSE -> admin.closeInventory();
            default -> { }
        }
    }

    private void giveClick(Player admin, Holder h, int raw, ClickType click) {
        if (raw == 49) { open(admin); return; }
        Object key = h.slots.get(raw);
        if (key == null) return;
        int n = click.isShiftClick() ? 64 : 1;
        ItemStack it;
        if (key instanceof CosmicMaterial m) it = m.create(click.isShiftClick() ? 64 : 16);
        else if (key instanceof AbilityType t) it = plugin.orbs().orb(t, 1);
        else it = switch ((String) key) {
            case "reroll" -> { ItemStack r = plugin.orbs().reroll(); r.setAmount(click.isShiftClick() ? 16 : 1); yield r; }
            case "pickaxe" -> plugin.tools().create(CosmicTools.Tool.PICKAXE);
            case "axe" -> plugin.tools().create(CosmicTools.Tool.AXE);
            case "shard" -> plugin.items().shard(click.isShiftClick() ? 64 : 16);
            case "token" -> plugin.items().token(click.isShiftClick() ? 16 : 1);
            case "cash" -> {
                long amt = click.isShiftClick() ? 1_000_000 : 100_000;
                plugin.economy().add(admin.getUniqueId(), amt);
                admin.sendMessage(plugin.mm(plugin.prefix() + "<gold>+$" + plugin.economy().format(amt)));
                yield null;
            }
            default -> plugin.banPistol().create();
        };
        if (it != null) for (ItemStack left : admin.getInventory().addItem(it).values()) admin.getWorld().dropItemNaturally(admin.getLocation(), left);
        admin.playSound(admin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        if (n > 1 && key instanceof AbilityType) admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>Orbs don't stack - gave one."));
    }

    private void playerClick(Player admin, Holder h, int raw, ClickType click) {
        if (raw == 49) { openPlayers(admin); return; }
        Player t = Bukkit.getPlayer(h.target);
        PlayerData d = plugin.data().get(h.target);
        if (t == null || d == null) { admin.sendMessage(plugin.mm(plugin.prefix() + "<red>They went offline.")); openPlayers(admin); return; }
        switch (raw) {
            case 19 -> {
                admin.closeInventory();
                if (plugin.roll().spin(t, RollPanel.Reason.ADMIN)) {
                    admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>Spinning the wheel for <white>" + t.getName() + "<gray>."));
                }
                return;
            }
            case 20 -> { openPick(admin, h.target, PickMode.SLOT1); return; }
            case 21 -> { openPick(admin, h.target, PickMode.SLOT2); return; }
            case 30 -> { openPick(admin, h.target, PickMode.ORB); return; }
            case 22 -> d.gained().clear();
            case 23 -> {
                AbilitySlot s = d.slot(click.isRightClick() ? 2 : 1);
                if (!s.isEmpty()) s.setTier(s.upgraded() ? 1 : AbilitySlot.MAX_TIER);
            }
            case 24 -> plugin.engine().resetCooldown(h.target);
            case 25 -> { admin.teleport(t); return; }
            case 29 -> d.addAfkTokens((click.isRightClick() ? -1 : 1) * (click.isShiftClick() ? 10 : 1));
            case 31 -> t.getInventory().addItem(plugin.orbs().reroll());
            case 32 -> t.getInventory().addItem(plugin.tools().create(CosmicTools.Tool.PICKAXE));
            case 33 -> t.getInventory().addItem(plugin.tools().create(CosmicTools.Tool.AXE));
            default -> { return; }
        }
        plugin.data().save(d);
        openPlayer(admin, h.target);
    }

    private void pickClick(Player admin, Holder h, int raw) {
        if (raw == 49) { openPlayer(admin, h.target); return; }
        if (!(h.slots.get(raw) instanceof AbilityType type)) return;
        Player t = Bukkit.getPlayer(h.target);
        PlayerData d = plugin.data().get(h.target);
        if (t == null || d == null) { openPlayers(admin); return; }
        switch (h.mode) {
            case ORB -> {
                for (ItemStack left : t.getInventory().addItem(plugin.orbs().orb(type, 1)).values()) t.getWorld().dropItemNaturally(t.getLocation(), left);
                admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>Gave <white>" + t.getName() + " <gray>a " + type.colored() + " <gray>orb."));
            }
            case SLOT1, SLOT2 -> {
                int slot = h.mode == PickMode.SLOT1 ? 1 : 2;
                AbilitySlot other = d.slot(slot == 1 ? 2 : 1);
                if (other.ability() == type) other.clear();
                AbilitySlot s = d.slot(slot);
                s.equip(type, s.isEmpty() ? 1 : s.tier());
                plugin.data().save(d);
                plugin.engine().resetCooldown(h.target);
                admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>Set <white>" + t.getName() + "<gray>'s slot " + slot + " to " + type.colored() + "<gray>."));
                t.sendMessage(plugin.mm(plugin.prefix() + "<gray>An admin set your slot " + slot + " ability to " + type.colored() + "<gray>."));
            }
        }
        openPlayer(admin, h.target);
    }

    private void tag(Player admin, RankTags.Rank rank) {
        if (plugin.tags().apply(admin, rank)) {
            admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>You are now wearing the " + rank.name() + " tag."));
        } else {
            admin.sendMessage(plugin.mm(plugin.prefix() + "<red>You need " + (rank == RankTags.Rank.OWNER ? "OP" : "admin or OP") + " to wear that tag."));
        }
    }

    // ------------------------------------------------------------------ announce prompt

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!announcing.remove(id)) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel") || text.isEmpty()) {
                p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Announcement cancelled."));
                return;
            }
            plugin.miscCommands().announce(p, text.split(" "));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        announcing.remove(event.getPlayer().getUniqueId());
    }
}
