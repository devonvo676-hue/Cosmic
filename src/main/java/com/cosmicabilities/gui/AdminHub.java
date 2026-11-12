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
    enum Page { HUB, GIVE, PLAYERS, PLAYER, PICK, UNBAN, RANKS, EVENTS, STATS, BOSSES, TEAMS }
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
            H_RESET = 37, H_SWEEP = 38, H_DESPAWN = 39, H_SPIN = 40, H_KILL = 41, H_RANKS = 42, H_AH = 43, H_CLOSE = 49,
            H_EVENTS = 19, H_SUPPLY = 20, H_TEAMS = 21, H_STATS = 22, H_BOSSES = 23, H_RPG = 24, H_COOLDOWNS = 25;

    private final CosmicAbilitiesPlugin plugin;
    private final Set<UUID> announcing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> eventMinutes = new ConcurrentHashMap<>();

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
        long kr = plugin.economy().killReward();
        inv.setItem(H_KILL, plugin.coinflip().coin("<gold><bold>Cash per kill: <yellow>$" + plugin.economy().format(kr),
                "<gray>Every player kill pays the killer this.",
                "<yellow>Left <gray>+$100  <yellow>Right <gray>-$100", "<yellow>Shift-left <gray>+$1,000  <yellow>Shift-right <gray>-$1,000"));
        inv.setItem(H_RANKS, Icons.button(plugin, Material.ORANGE_SHULKER_BOX, "<#ffb703><bold>Ranks",
                "<gray>Starlight, Cosmos, Supernova.", "<gray>Give yourself a rank or rank box to test,",
                "<gray>and see how to hook up Tebex."));
        inv.setItem(H_AH, Icons.button(plugin, Material.GOLD_INGOT, "<yellow><bold>Auction House",
                "<gray>Open /ah. Admins can remove any", "<gray>listing with shift + right-click."));
        inv.setItem(H_SPIN, Icons.button(plugin, Material.ECHO_SHARD, "<light_purple>Test the spin wheel",
                "<gray>Spins a new slot 1 ability for yourself."));
        inv.setItem(H_EVENTS, Icons.button(plugin, Material.FIREWORK_ROCKET, "<yellow><bold>Daily Events",
                "<gray>2x kill money, 2x AFK tokens, ability", "<gray>frenzy, supply drop rain, boss fights.",
                "<gray>Active: <white>" + plugin.events().activeEvents().size()));
        inv.setItem(H_SUPPLY, Icons.button(plugin, Material.CHEST_MINECART, "<green><bold>Supply Drop",
                "<yellow>Click <gray>drop one where you stand", "<yellow>Right <gray>drop one 100-300 blocks away",
                "<gray>Or <white>/supplydrop <x> <y> <z>"));
        inv.setItem(H_TEAMS, Icons.button(plugin, Material.WHITE_BANNER, "<aqua><bold>Teams & Season",
                "<gray>Team bank leaderboard, season length,", "<gray>end the season and hand out rewards.",
                "<gray>Season <white>" + plugin.teams().season() + " <gray>ends in <white>" + plugin.teams().timeLeft()));
        inv.setItem(H_STATS, Icons.button(plugin, Material.WRITABLE_BOOK, "<#f15bb5><bold>Player Stats",
                "<gray>Refresh everyone's sidebar / tab / tags,", "<gray>or reset kills, deaths and cash."));
        inv.setItem(H_BOSSES, Icons.button(plugin, Material.DRAGON_HEAD, "<red><bold>Boss Strength",
                "<gray>Health, damage and size multipliers", "<gray>for every boss."));
        inv.setItem(H_RPG, plugin.rpg().create());
        inv.setItem(H_COOLDOWNS, Icons.button(plugin, Material.CLOCK, "<aqua><bold>Ability Cooldowns",
                "<gray>Change every ability's cooldown,", "<gray>all at once or one by one."));
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
        put(h, 24, plugin.tools().create(CosmicTools.Tool.SHOVEL), "shovel");
        put(h, 25, plugin.items().afkToken(1), "afk");
        put(h, 15, plugin.items().token(1), "token");
        put(h, 17, plugin.rpg().create(), "rpg");
        put(h, 16, plugin.coinflip().coin("<gold><bold>+$100,000", "<gray>Click: give yourself $100,000", "<gray>Shift: $1,000,000"), "cash");
        CosmicMaterial[] mats = CosmicMaterial.values();
        for (int i = 0; i < mats.length; i++) put(h, 19 + i, mats[i].create(16), mats[i]);
        AbilityType[] types = AbilityType.values();
        int[] orbSlots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43, 46, 47, 48, 50, 51, 52};
        for (int i = 0; i < types.length && i < orbSlots.length; i++) put(h, orbSlots[i], plugin.orbs().orb(types[i], 1), types[i]);
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
            lore.add(plugin.mm("<!italic><gray>AFK Tokens: <white>" + plugin.items().afkTokens(online)));
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
                plugin.mm("<!italic><gray>AFK Tokens: <white>" + plugin.items().afkTokens(t))));
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
        inv.setItem(29, Icons.button(plugin, Material.SUNFLOWER, "<gold>AFK Tokens: <white>" + plugin.items().afkTokens(t),
                "<yellow>Left <gray>+1  <yellow>Right <gray>-1  <yellow>Shift <gray>x10"));
        inv.setItem(30, Icons.button(plugin, Material.ENDER_EYE, "<light_purple>Give an ability orb", "<gray>Pick which ability"));
        inv.setItem(31, Icons.button(plugin, Material.AMETHYST_SHARD, "<light_purple>Give a Cosmic Reroll"));
        inv.setItem(32, Icons.button(plugin, Material.NETHERITE_PICKAXE, "<light_purple>Give Cosmic Pickaxe"));
        inv.setItem(33, Icons.button(plugin, Material.NETHERITE_AXE, "<light_purple>Give Cosmic Axe"));
        com.cosmicabilities.rank.PlayerRank pr = plugin.ranks().rankOf(target);
        inv.setItem(34, Icons.button(plugin, pr.box, "<white>Rank: " + pr.mini(), "<yellow>Left <gray>next rank  <yellow>Right <gray>previous"));
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
            case RANKS -> ranksClick(admin, raw);
            case EVENTS -> eventsClick(admin, raw, click);
            case STATS -> statsClick(admin, raw, click);
            case BOSSES -> bossesClick(admin, raw, click);
            case TEAMS -> teamsClick(admin, raw, click);
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
            case H_KILL -> {
                long step = click.isShiftClick() ? 1000 : 100;
                plugin.economy().setKillReward(plugin.economy().killReward() + (click.isRightClick() ? -step : step));
                open(admin);
            }
            case H_RANKS -> openRanks(admin);
            case H_AH -> plugin.auctions().open(admin, 0);
            case H_SPIN -> {
                admin.closeInventory();
                plugin.roll().spin(admin, RollPanel.Reason.ADMIN);
            }
            case H_EVENTS -> openEvents(admin);
            case H_SUPPLY -> {
                admin.closeInventory();
                if (click.isRightClick()) {
                    double a = Math.random() * Math.PI * 2, d = 100 + Math.random() * 200;
                    plugin.supplyDrops().call(admin.getLocation().add(Math.cos(a) * d, 0, Math.sin(a) * d), admin);
                } else {
                    plugin.supplyDrops().call(admin.getLocation(), admin);
                }
            }
            case H_TEAMS -> openTeams(admin);
            case H_STATS -> openStats(admin);
            case H_BOSSES -> openBosses(admin);
            case H_RPG -> admin.getInventory().addItem(plugin.rpg().create());
            case H_COOLDOWNS -> plugin.configPanel().open(admin);
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
            case "shovel" -> plugin.tools().create(CosmicTools.Tool.SHOVEL);
            case "afk" -> plugin.items().afkToken(click.isShiftClick() ? 64 : 1);
            case "token" -> plugin.items().token(click.isShiftClick() ? 16 : 1);
            case "cash" -> {
                long amt = click.isShiftClick() ? 1_000_000 : 100_000;
                plugin.economy().add(admin.getUniqueId(), amt);
                admin.sendMessage(plugin.mm(plugin.prefix() + "<gold>+$" + plugin.economy().format(amt)));
                yield null;
            }
            case "rpg" -> plugin.rpg().create();
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
            case 29 -> {
                int n = click.isShiftClick() ? 10 : 1;
                if (click.isRightClick()) plugin.items().takeAfkTokens(t, Math.min(n, plugin.items().afkTokens(t)));
                else plugin.items().giveAfkTokens(t, n);
            }
            case 31 -> t.getInventory().addItem(plugin.orbs().reroll());
            case 32 -> t.getInventory().addItem(plugin.tools().create(CosmicTools.Tool.PICKAXE));
            case 33 -> t.getInventory().addItem(plugin.tools().create(CosmicTools.Tool.AXE));
            case 34 -> {
                com.cosmicabilities.rank.PlayerRank[] all = com.cosmicabilities.rank.PlayerRank.values();
                int i = plugin.ranks().rankOf(h.target).ordinal() + (click.isRightClick() ? -1 : 1);
                plugin.ranks().setRank(h.target, all[(i + all.length) % all.length]);
            }
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

    // ------------------------------------------------------------------ ranks

    private static final int[] RANK_COLS = {11, 13, 15};

    private void openRanks(Player admin) {
        Holder h = new Holder(Page.RANKS);
        Inventory inv = create(h, 54, "<dark_red>Ranks <dark_gray>- test them on yourself");
        com.cosmicabilities.rank.PlayerRank mine = plugin.ranks().rankOf(admin.getUniqueId());
        com.cosmicabilities.rank.PlayerRank[] ranks = {com.cosmicabilities.rank.PlayerRank.STARLIGHT,
                com.cosmicabilities.rank.PlayerRank.COSMOS, com.cosmicabilities.rank.PlayerRank.SUPERNOVA};
        for (int i = 0; i < 3; i++) {
            com.cosmicabilities.rank.PlayerRank r = ranks[i];
            inv.setItem(RANK_COLS[i], Icons.button(plugin, r.box, r.mini() + (mine == r ? " <green>(yours)" : ""),
                    "<gray>[" + r.tag + "] beside the name", "<gray>Ender chest: <white>" + plugin.ranks().rows(r) * 9 + " slots",
                    "", "<yellow>Click <gray>to give yourself this rank"));
            inv.setItem(RANK_COLS[i] + 18, plugin.ranks().voucher(r));
        }
        inv.setItem(40, Icons.button(plugin, Material.BARRIER, "<red>Remove my rank"));
        inv.setItem(4, Icons.button(plugin, Material.BOOK, "<gold><bold>Selling ranks on Tebex",
                "<gray>For each rank package in your Tebex panel,",
                "<gray>add a <white>command <gray>(run when the player is online):",
                "<white>cosmicrank give {username} starlight",
                "<gray>(or cosmos / supernova). They get an enchanted",
                "<gray>rank box; placing it unlocks the rank.",
                "<gray>Click a box below to get one yourself.",
                "", "<dark_gray>A worn [OWNER]/[ADMIN] tag shows instead of a",
                "<dark_gray>rank - take it off to see your rank tag."));
        inv.setItem(49, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        admin.openInventory(inv);
    }

    private void ranksClick(Player admin, int raw) {
        com.cosmicabilities.rank.PlayerRank[] ranks = {com.cosmicabilities.rank.PlayerRank.STARLIGHT,
                com.cosmicabilities.rank.PlayerRank.COSMOS, com.cosmicabilities.rank.PlayerRank.SUPERNOVA};
        for (int i = 0; i < 3; i++) {
            if (raw == RANK_COLS[i]) { plugin.ranks().setRank(admin.getUniqueId(), ranks[i]); openRanks(admin); return; }
            if (raw == RANK_COLS[i] + 18) { admin.getInventory().addItem(plugin.ranks().voucher(ranks[i])); return; }
        }
        if (raw == 40) { plugin.ranks().setRank(admin.getUniqueId(), com.cosmicabilities.rank.PlayerRank.NONE); openRanks(admin); }
        else if (raw == 49) open(admin);
    }

    private void tag(Player admin, RankTags.Rank rank) {
        if (plugin.tags().apply(admin, rank)) {
            admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>You are now wearing the " + rank.name() + " tag."));
        } else {
            admin.sendMessage(plugin.mm(plugin.prefix() + "<red>You need " + (rank == RankTags.Rank.OWNER ? "OP" : "admin or OP") + " to wear that tag."));
        }
    }


    // ------------------------------------------------------------------ daily events

    private static final int[] EVENT_SLOTS = {11, 12, 13, 14, 15};

    private void openEvents(Player admin) {
        Holder h = new Holder(Page.EVENTS);
        Inventory inv = create(h, 45, "<dark_red>Daily Events");
        int mins = eventMinutes.getOrDefault(admin.getUniqueId(), 60);
        com.cosmicabilities.events.ServerEvents.Type[] types = com.cosmicabilities.events.ServerEvents.Type.values();
        for (int i = 0; i < types.length; i++) {
            var t = types[i];
            boolean on = plugin.events().isActive(t);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + t.desc);
            lore.add("");
            if (on) {
                long left = Math.max(0, (plugin.events().endsAt(t) - System.currentTimeMillis()) / 60000);
                lore.add("<green><bold>ON</bold> <gray>- " + left + " min left");
                lore.add("<yellow>Click <gray>to stop it");
            } else {
                lore.add("<red>OFF");
                lore.add("<yellow>Click <gray>to start it for <white>" + mins + " min");
            }
            ItemStack it = Icons.button(plugin, t.icon, t.color + "<bold>" + t.title, lore.toArray(new String[0]));
            if (on) { ItemMeta m = it.getItemMeta(); m.setEnchantmentGlintOverride(true); it.setItemMeta(m); }
            inv.setItem(EVENT_SLOTS[i], it);
        }
        inv.setItem(31, Icons.button(plugin, Material.CLOCK, "<aqua><bold>Event length: <white>" + mins + " min",
                "<yellow>Left <gray>+15 min  <yellow>Right <gray>-15 min", "<yellow>Shift <gray>+/- 60 min",
                "<gray>Supply rain drops one every <white>" + plugin.getConfig().getInt("events.supply-rain-minutes", 5) + " min"));
        inv.setItem(40, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        admin.openInventory(inv);
    }

    private void eventsClick(Player admin, int raw, ClickType click) {
        if (raw == 40) { open(admin); return; }
        if (raw == 31) {
            int step = click.isShiftClick() ? 60 : 15;
            int m = eventMinutes.getOrDefault(admin.getUniqueId(), 60) + (click.isRightClick() ? -step : step);
            eventMinutes.put(admin.getUniqueId(), Math.max(5, Math.min(24 * 60, m)));
            openEvents(admin);
            return;
        }
        com.cosmicabilities.events.ServerEvents.Type[] types = com.cosmicabilities.events.ServerEvents.Type.values();
        for (int i = 0; i < types.length; i++) {
            if (raw != EVENT_SLOTS[i]) continue;
            var t = types[i];
            if (plugin.events().isActive(t)) {
                plugin.events().stop(t);
                openEvents(admin);
            } else {
                plugin.events().start(t, eventMinutes.getOrDefault(admin.getUniqueId(), 60));
                if (t == com.cosmicabilities.events.ServerEvents.Type.BOSS_FIGHT) plugin.bossFightPanel().open(admin);
                else openEvents(admin);
            }
            return;
        }
    }

    // ------------------------------------------------------------------ stats

    private void openStats(Player admin) {
        Holder h = new Holder(Page.STATS);
        Inventory inv = create(h, 45, "<dark_red>Player Stats");
        inv.setItem(10, Icons.button(plugin, Material.SUNFLOWER, "<green><bold>Refresh everyone's stats",
                "<gray>Re-saves everyone's data and redraws", "<gray>every sidebar, tab list and name tag."));
        inv.setItem(12, Icons.button(plugin, Material.IRON_SWORD, "<red><bold>Reset everyone's kills",
                "<gray>Kills and deaths back to 0", "<gray>(online and offline players).", "", "<red>Shift-click to confirm"));
        inv.setItem(14, plugin.coinflip().coin("<gold><bold>Reset everyone's cash",
                "<gray>Everyone back to the starting", "<gray>$" + plugin.economy().format(plugin.getConfig().getLong("economy.starting-cash", 10000)) + ".",
                "", "<red>Shift-click to confirm"));
        inv.setItem(16, Icons.button(plugin, Material.TNT_MINECART, "<dark_red><bold>Reset ALL stats",
                "<gray>Kills, deaths, cash and team banks.", "", "<red>Shift-click to confirm"));
        inv.setItem(40, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        admin.openInventory(inv);
    }

    private void statsClick(Player admin, int raw, ClickType click) {
        switch (raw) {
            case 10 -> {
                refreshAll();
                admin.sendMessage(plugin.mm(plugin.prefix() + "<green>Refreshed everyone's stats."));
            }
            case 12, 14, 16 -> {
                if (!click.isShiftClick()) {
                    admin.sendMessage(plugin.mm(plugin.prefix() + "<red>Shift-click to confirm - this can't be undone."));
                    return;
                }
                boolean kills = raw != 14, cash = raw != 12;
                int n = 0;
                long start = plugin.getConfig().getLong("economy.starting-cash", 10000);
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    n++;
                    if (kills) {
                        try {
                            op.setStatistic(org.bukkit.Statistic.PLAYER_KILLS, 0);
                            op.setStatistic(org.bukkit.Statistic.DEATHS, 0);
                        } catch (RuntimeException ignored) { }
                    }
                    if (cash) {
                        long bal = plugin.economy().balance(op.getUniqueId());
                        plugin.economy().add(op.getUniqueId(), start - bal);
                    }
                }
                if (raw == 16) plugin.teams().resetBanks();
                plugin.data().saveAll();
                refreshAll();
                Bukkit.broadcast(plugin.mm(plugin.prefix() + "<gold>An admin reset " + (raw == 12 ? "everyone's kills" : raw == 14 ? "everyone's cash" : "all stats") + "."));
                admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>Reset <white>" + n + " <gray>players."));
            }
            case 40 -> open(admin);
            default -> { }
        }
    }

    private void refreshAll() {
        plugin.data().saveAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.tags().refresh(p);
            plugin.sidebar().update(p);
            plugin.sidebar().tab(p);
        }
    }

    // ------------------------------------------------------------------ boss strength

    private static final String[] BOSS_KEYS = {"health-multiplier", "damage-multiplier", "size-multiplier"};

    private void openBosses(Player admin) {
        Holder h = new Holder(Page.BOSSES);
        Inventory inv = create(h, 45, "<dark_red>Boss Strength");
        Material[] icons = {Material.GOLDEN_APPLE, Material.NETHERITE_SWORD, Material.SCAFFOLDING};
        String[] names = {"Health", "Damage", "Size"};
        for (int i = 0; i < 3; i++) {
            double v = plugin.getConfig().getDouble("bosses." + BOSS_KEYS[i], 1.0);
            inv.setItem(11 + i * 2, Icons.button(plugin, icons[i], "<red><bold>" + names[i] + ": <white>x" + String.format("%.2f", v),
                    "<yellow>Left <gray>+0.25  <yellow>Right <gray>-0.25", "<yellow>Shift <gray>+/- 1.0",
                    i == 2 ? "<gray>Applies to bosses spawned from now on" : "<gray>Applies to bosses spawned from now on"));
        }
        inv.setItem(31, Icons.button(plugin, Material.BOOK, "<gray>Per-boss health / damage",
                "<gray>Each boss also has its own values in", "<white>config.yml -> bosses.<boss>.health / damage",
                "<gray>The multipliers above scale all of them."));
        inv.setItem(40, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        admin.openInventory(inv);
    }

    private void bossesClick(Player admin, int raw, ClickType click) {
        if (raw == 40) { open(admin); return; }
        for (int i = 0; i < 3; i++) {
            if (raw != 11 + i * 2) continue;
            double step = click.isShiftClick() ? 1.0 : 0.25;
            double v = plugin.getConfig().getDouble("bosses." + BOSS_KEYS[i], 1.0) + (click.isRightClick() ? -step : step);
            v = Math.max(i == 2 ? 0.25 : 0.25, Math.min(i == 2 ? 4 : 50, Math.round(v * 100) / 100.0));
            plugin.getConfig().set("bosses." + BOSS_KEYS[i], v);
            plugin.saveConfig();
            openBosses(admin);
        }
    }

    // ------------------------------------------------------------------ teams & season

    private void openTeams(Player admin) {
        Holder h = new Holder(Page.TEAMS);
        Inventory inv = create(h, 54, "<dark_red>Teams & Season " + plugin.teams().season());
        List<com.cosmicabilities.team.Teams.Team> rank = plugin.teams().ranking();
        for (int i = 0; i < rank.size() && i < 28; i++) {
            var t = rank.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Bank: <gold>$" + plugin.economy().format(t.bank));
            OfflinePlayer leader = Bukkit.getOfflinePlayer(t.leader);
            lore.add("<gray>Leader: <white>" + leader.getName());
            lore.add("<gray>Members: <white>" + t.members.size());
            inv.setItem(10 + (i / 7) * 9 + i % 7, Icons.button(plugin, i == 0 ? Material.GOLD_BLOCK : i < 3 ? Material.IRON_BLOCK : Material.STONE,
                    "<aqua><bold>#" + (i + 1) + " " + t.name, lore.toArray(new String[0])));
        }
        int days = plugin.getConfig().getInt("season.days", 14);
        inv.setItem(46, Icons.button(plugin, Material.CLOCK, "<aqua>Season length: <white>" + days + " days",
                "<yellow>Left <gray>+1 day  <yellow>Right <gray>-1 day", "<gray>Ends in <white>" + plugin.teams().timeLeft()));
        boolean auto = plugin.getConfig().getBoolean("season.auto-end", true);
        inv.setItem(47, Icons.button(plugin, auto ? Material.LIME_DYE : Material.GRAY_DYE, "<aqua>Auto-end season: " + (auto ? "<green>ON" : "<red>OFF"),
                "<gray>When on, the season ends by itself", "<gray>and the richest team gets rewarded."));
        inv.setItem(51, Icons.button(plugin, Material.NETHER_STAR, "<gold><bold>End season now",
                "<gray>The richest team's members each get", "<gray>a rare boss/dragon ability orb,", "<gray>then every bank resets to $0.",
                "", "<red>Shift-click to confirm"));
        inv.setItem(49, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        admin.openInventory(inv);
    }

    private void teamsClick(Player admin, int raw, ClickType click) {
        switch (raw) {
            case 46 -> {
                int d = plugin.getConfig().getInt("season.days", 14) + (click.isRightClick() ? -1 : 1);
                plugin.getConfig().set("season.days", Math.max(1, Math.min(90, d)));
                plugin.saveConfig();
                openTeams(admin);
            }
            case 47 -> {
                plugin.getConfig().set("season.auto-end", !plugin.getConfig().getBoolean("season.auto-end", true));
                plugin.saveConfig();
                openTeams(admin);
            }
            case 51 -> {
                if (!click.isShiftClick()) { admin.sendMessage(plugin.mm(plugin.prefix() + "<red>Shift-click to end the season.")); return; }
                admin.closeInventory();
                plugin.teams().endSeason(admin);
            }
            case 49 -> open(admin);
            default -> { }
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
