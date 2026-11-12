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
    enum Page { HUB, GIVE, PLAYERS, PLAYER, PICK, UNBAN, RANKS, EVENTS, STATS, BOSSES, BOSS_EDIT, TEAMS, SUPPLY }
    enum PickMode { SLOT1, SLOT2, ORB }

    public static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Page page;
        UUID target;
        String key;
        PickMode mode;
        final Map<Integer, Object> slots = new HashMap<>();
        Holder(Page page) { this.page = page; }
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    // layout: one row per section, the section's label on the left
    private static final int H_ABILITIES = 11, H_BOSS = 12, H_BOSSES = 13, H_EVENTS = 14, H_SUPPLY = 15, H_TEAMS = 16, H_COOLDOWNS = 17,
            H_PLAYERS = 20, H_STATS = 21, H_RANKS = 22, H_GIVE = 23, H_UNBAN = 24, H_ANNOUNCE = 25, H_SPIN = 26,
            H_KILL = 29, H_AH = 30, H_SHOP = 31, H_RECIPES = 32, H_RPG = 33, H_CONFIG = 34, H_RESET = 35,
            H_RELOAD = 38, H_SWEEP = 39, H_DESPAWN = 40, H_TAG_ADMIN = 42, H_TAG_OWNER = 43, H_TAG_OFF = 44, H_CLOSE = 49;

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
        Inventory inv = create(h, 54, "<dark_gray>✦ <gradient:#9b5de5:#00bbf9><bold>Cosmic Admin</bold></gradient>");
        ItemStack row = Icons.button(plugin, Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int r = 1; r <= 4; r++) for (int c = 1; c <= 8; c++) inv.setItem(r * 9 + c, row);
        inv.setItem(4, Icons.gui(plugin, "logo", "<gradient:#9b5de5:#00bbf9><bold>Cosmic Abilities " + plugin.getPluginMeta().getVersion(),
                "<gray>Online: <white>" + Bukkit.getOnlinePlayers().size() + "  <gray>Bosses alive: <white>" + plugin.bosses().aliveCount(),
                "<gray>Season <white>" + plugin.teams().season() + " <gray>ends in <white>" + plugin.teams().timeLeft(),
                "<gray>Events on: <white>" + plugin.events().activeEvents().size()));
        inv.setItem(9, Icons.gui(plugin, "sec_game", "<#c77dff><bold>GAME", "<gray>Abilities, bosses, events, seasons"));
        inv.setItem(18, Icons.gui(plugin, "sec_players", "<#48cae4><bold>PLAYERS", "<gray>Players, stats, ranks, items"));
        inv.setItem(27, Icons.gui(plugin, "sec_eco", "<#ffb703><bold>ECONOMY & ITEMS", "<gray>Cash, auction house, shop, recipes"));
        inv.setItem(36, Icons.gui(plugin, "sec_server", "<#9a9ab0><bold>SERVER", "<gray>Reload, clean up, name tags"));

        inv.setItem(H_ABILITIES, Icons.gui(plugin, "abilities", "<#c77dff><bold>Abilities", "<gray>Equip any ability on yourself,", "<gray>test-cast, materials, Ban Pistol.", "", "<yellow>Click to open"));
        inv.setItem(H_BOSS, Icons.gui(plugin, "boss_fight", "<red><bold>Start a Boss Fight", "<gray>Pick a boss, a countdown and how", "<gray>many players must be there.", "", "<yellow>Click to open"));
        inv.setItem(H_BOSSES, Icons.gui(plugin, "boss_settings", "<red><bold>Boss Settings", "<gray>Health, damage, special attacks, speed,", "<gray>size, armor, drops, spawn cutscene.", "", "<yellow>Click to open"));
        inv.setItem(H_EVENTS, Icons.gui(plugin, "events", "<yellow><bold>Daily Events", "<gray>2x kill money, 2x AFK tokens, ability", "<gray>frenzy, supply rain, boss fights.",
                "<gray>Running now: <white>" + plugin.events().activeEvents().size(), "", "<yellow>Click to open"));
        inv.setItem(H_SUPPLY, Icons.gui(plugin, "supply", "<green><bold>Supply Drops", "<gray>Spawn one at a random spot, edit the", "<gray>loot and where they can land.", "", "<yellow>Click to open"));
        inv.setItem(H_TEAMS, Icons.gui(plugin, "teams", "<aqua><bold>Teams & Season", "<gray>Bank leaderboard, season length,", "<gray>end the season and give rewards.", "", "<yellow>Click to open"));
        inv.setItem(H_COOLDOWNS, Icons.gui(plugin, "cooldowns", "<aqua><bold>Ability Cooldowns & Damage", "<gray>Every ability's cooldown, damage", "<gray>and cast effects, one by one or all.", "", "<yellow>Click to open"));

        inv.setItem(H_PLAYERS, Icons.gui(plugin, "players", "<#48cae4><bold>Players", "<gray>Set or spin abilities, upgrades,", "<gray>orbs, AFK tokens, ranks, teleport.", "", "<yellow>Click to open"));
        inv.setItem(H_STATS, Icons.gui(plugin, "stats", "<#48cae4><bold>Player Stats", "<gray>Refresh everyone's sidebar and tab,", "<gray>or reset kills / cash.", "", "<yellow>Click to open"));
        inv.setItem(H_RANKS, Icons.gui(plugin, "ranks", "<#ffb703><bold>Ranks", "<gray>Starlight, Cosmos, Supernova.", "<gray>Test them, get rank boxes, Tebex help.", "", "<yellow>Click to open"));
        inv.setItem(H_GIVE, Icons.gui(plugin, "give", "<white><bold>Give Items", "<gray>Rerolls, tools, shards, tokens, orbs,", "<gray>cash, Ban Pistol, RPG and more.", "", "<yellow>Click to open"));
        inv.setItem(H_UNBAN, Icons.gui(plugin, "unban", "<red><bold>Banned Players", "<gray>Click someone to unban them.", "", "<yellow>Click to open"));
        inv.setItem(H_ANNOUNCE, Icons.gui(plugin, "announce", "<gold><bold>Announce", "<gray>Type your message in chat next.", "<gray>Use <white>title | subtitle <gray>for two lines."));
        inv.setItem(H_SPIN, Icons.gui(plugin, "spin", "<light_purple><bold>Test the Spin Wheel", "<gray>Spins a new slot 1 ability for yourself."));

        long kr = plugin.economy().killReward();
        inv.setItem(H_KILL, Icons.gui(plugin, "kill_reward", "<gold><bold>Cash per Kill: <yellow>$" + plugin.economy().format(kr),
                "<gray>Goes into the killer's team bank", "<gray>(or their own cash with no team).", "",
                "<yellow>Left <gray>+$100   <yellow>Right <gray>-$100", "<yellow>Shift <gray>+/- $1,000"));
        inv.setItem(H_AH, Icons.gui(plugin, "auction", "<yellow><bold>Auction House", "<gray>Open /ah. Shift + right-click a", "<gray>listing to remove it.", "", "<yellow>Click to open"));
        inv.setItem(H_SHOP, Icons.gui(plugin, "shop", "<green><bold>AFK Shop Editor", "<gray>Add items, set prices, move them.", "", "<yellow>Click to open"));
        inv.setItem(H_RECIPES, Icons.gui(plugin, "recipes", "<gold><bold>Recipes", "<gray>Change how cosmic items are crafted.", "", "<yellow>Click to open"));
        inv.setItem(H_RPG, Icons.gui(plugin, "rpg", "<red><bold>Admin RPG", "<gray>Rocket launcher that leaves a crater.", "", "<yellow>Click to get one"));
        inv.setItem(H_CONFIG, Icons.gui(plugin, "config", "<gray><bold>All Settings", "<gray>Every other setting, grouped.", "", "<yellow>Click to open"));
        inv.setItem(H_RESET, Icons.gui(plugin, "reset", "<aqua><bold>Reset Cooldowns", "<yellow>Click <gray>yours", "<yellow>Shift-click <gray>everyone's"));

        inv.setItem(H_RELOAD, Icons.gui(plugin, "reload", "<green><bold>Reload Config", "<gray>Re-read config.yml + afkshop.yml now."));
        inv.setItem(H_SWEEP, Icons.gui(plugin, "sweep", "<aqua><bold>Clean Up Effects", "<gray>Removes any stuck ability visuals."));
        inv.setItem(H_DESPAWN, Icons.gui(plugin, "despawn", "<red><bold>Despawn All Bosses", "<gray>No drops."));
        inv.setItem(H_TAG_ADMIN, Icons.gui(plugin, "tag_admin", "<red><bold>Wear [ADMIN]", "<gray>Needs admin or OP"));
        inv.setItem(H_TAG_OWNER, Icons.gui(plugin, "tag_owner", "<gold><bold>Wear [OWNER]", "<gray>Needs OP"));
        inv.setItem(H_TAG_OFF, Icons.gui(plugin, "tag_off", "<gray><bold>Remove My Tag"));
        inv.setItem(H_CLOSE, Icons.gui(plugin, "close", "<red>Close"));
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
        put(h, 9, plugin.cocaine().create(), "cocaine");
        put(h, 16, plugin.coinflip().coin("<gold><bold>+$100,000", "<gray>Click: give yourself $100,000", "<gray>Shift: $1,000,000"), "cash");
        CosmicMaterial[] mats = CosmicMaterial.values();
        for (int i = 0; i < mats.length; i++) put(h, 19 + i, mats[i].create(16), mats[i]);
        AbilityType[] types = AbilityType.values();
        int[] orbSlots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43, 46, 47, 48, 50, 51, 52};
        for (int i = 0; i < types.length && i < orbSlots.length; i++) put(h, orbSlots[i], plugin.orbs().orb(types[i], 1), types[i]);
        inv.setItem(49, Icons.gui(plugin, "back", "<yellow>Back"));
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
        inv.setItem(49, Icons.gui(plugin, "back", "<yellow>Back"));
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
        inv.setItem(49, Icons.gui(plugin, "back", "<yellow>Back"));
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
        inv.setItem(49, Icons.gui(plugin, "back", "<yellow>Back"));
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
        inv.setItem(49, Icons.gui(plugin, "back", "<yellow>Back"));
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
            case BOSS_EDIT -> bossEditClick(admin, h, raw, click);
            case SUPPLY -> supplyClick(admin, raw, click);
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
            case H_SUPPLY -> openSupply(admin);
            case H_TEAMS -> openTeams(admin);
            case H_STATS -> openStats(admin);
            case H_BOSSES -> openBosses(admin);
            case H_RPG -> admin.getInventory().addItem(plugin.rpg().create());
            case H_COOLDOWNS -> plugin.configPanel().openAbilities(admin);
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
            case "cocaine" -> plugin.cocaine().create();
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
        inv.setItem(49, Icons.gui(plugin, "back", "<yellow>Back"));
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
        inv.setItem(31, Icons.gui(plugin, "cooldowns", "<aqua><bold>Event length: <white>" + mins + " min",
                "<yellow>Left <gray>+15 min  <yellow>Right <gray>-15 min", "<yellow>Shift <gray>+/- 60 min",
                "<gray>Supply rain drops one every <white>" + plugin.getConfig().getInt("events.supply-rain-minutes", 5) + " min"));
        inv.setItem(40, Icons.gui(plugin, "back", "<yellow>Back"));
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
        inv.setItem(10, Icons.gui(plugin, "reload", "<green><bold>Refresh everyone's stats",
                "<gray>Re-saves everyone's data and redraws", "<gray>every sidebar, tab list and name tag."));
        inv.setItem(12, Icons.button(plugin, Material.IRON_SWORD, "<red><bold>Reset everyone's kills",
                "<gray>Kills and deaths back to 0", "<gray>(online and offline players).", "", "<red>Shift-click to confirm"));
        inv.setItem(14, plugin.coinflip().coin("<gold><bold>Reset everyone's cash",
                "<gray>Everyone back to the starting", "<gray>$" + plugin.economy().format(plugin.getConfig().getLong("economy.starting-cash", 10000)) + ".",
                "", "<red>Shift-click to confirm"));
        inv.setItem(16, Icons.button(plugin, Material.TNT_MINECART, "<dark_red><bold>Reset ALL stats",
                "<gray>Kills, deaths, cash and team banks.", "", "<red>Shift-click to confirm"));
        inv.setItem(40, Icons.gui(plugin, "back", "<yellow>Back"));
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

    // ------------------------------------------------------------------ boss settings

    private record BossSetting(String key, String label, Material icon, double step, double big, double min, double max, String note) {}

    private static final List<BossSetting> BOSS_SETTINGS = List.of(
            new BossSetting("health", "Health", Material.GOLDEN_APPLE, 50, 250, 50, 20000, "Server max is 1024 unless raised in spigot.yml"),
            new BossSetting("damage", "Melee damage", Material.NETHERITE_SWORD, 1, 5, 0, 200, "Per hit, before armor"),
            new BossSetting("special-damage", "Special attack damage", Material.BLAZE_POWDER, 1, 5, 0, 200, "Damage of its special move"),
            new BossSetting("special-attack-interval", "Special attack every (s)", Material.CLOCK, 1, 5, 2, 120, "Seconds between special moves"),
            new BossSetting("special-radius", "Special attack radius", Material.TARGET, 1, 5, 2, 64, "Blocks"),
            new BossSetting("speed", "Speed", Material.SUGAR, 0.01, 0.05, 0.05, 1.0, "Movement speed (0.3 = normal mob)"),
            new BossSetting("size", "Size", Material.SCAFFOLDING, 0.1, 0.5, 0.2, 5, "1 = normal. Model and hitbox grow together"),
            new BossSetting("armor", "Armor", Material.NETHERITE_CHESTPLATE, 1, 5, 0, 30, "Armor points (diamond set = 20)"),
            new BossSetting("armor-toughness", "Armor toughness", Material.NETHERITE_INGOT, 1, 4, 0, 20, ""),
            new BossSetting("knockback-resistance", "Knockback resistance", Material.ANVIL, 0.05, 0.25, 0, 1, "1 = can't be knocked back"),
            new BossSetting("ability-chance", "Boss ability chance", Material.NETHER_STAR, 0.05, 0.25, 0, 1,
                    "Chance top damage gets THIS boss's ability (else a random one)"),
            new BossSetting("drops.min", "Fragments min", Material.AMETHYST_SHARD, 1, 5, 0, 64, "Per player who helped"),
            new BossSetting("drops.max", "Fragments max", Material.AMETHYST_CLUSTER, 1, 5, 0, 64, ""));
    private static final int[] BOSS_SETTING_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};
    private static final int[] BOSS_PICK_SLOTS = {20, 21, 22, 23, 24};

    private void openBosses(Player admin) {
        Holder h = new Holder(Page.BOSSES);
        Inventory inv = create(h, 54, "<dark_red>Boss Settings");
        com.cosmicabilities.boss.BossType[] types = com.cosmicabilities.boss.BossType.values();
        Material[] heads = {Material.WITHER_SKELETON_SKULL, Material.MAGMA_BLOCK, Material.BLUE_ICE, Material.DRAGON_HEAD, Material.NETHERITE_BLOCK};
        for (int i = 0; i < types.length; i++) {
            var t = types[i];
            inv.setItem(BOSS_PICK_SLOTS[i], Icons.button(plugin, heads[i], t.colored(),
                    "<gray>Health <white>" + fmtNum(plugin.bosses().cfg(t, "health", 600)) + " <gray>| Damage <white>" + fmtNum(plugin.bosses().cfg(t, "damage", 10)),
                    "<gray>Size <white>x" + fmtNum(plugin.bosses().cfg(t, "size", 1.0)) + " <gray>| Speed <white>" + fmtNum(plugin.bosses().cfg(t, "speed", 0.3)),
                    "", "<yellow>Click <gray>to change everything about it"));
        }
        String[] names = {"Health", "Damage", "Size"};
        Material[] icons = {Material.GOLDEN_APPLE, Material.NETHERITE_SWORD, Material.SCAFFOLDING};
        String[] keys = {"health-multiplier", "damage-multiplier", "size-multiplier"};
        for (int i = 0; i < 3; i++) {
            inv.setItem(37 + i, Icons.button(plugin, icons[i], "<red>ALL bosses " + names[i] + ": <white>x" + fmtNum(plugin.getConfig().getDouble("bosses." + keys[i], 1.0)),
                    "<gray>Multiplies every boss's own value.", "<yellow>Left <gray>+0.1  <yellow>Right <gray>-0.1", "<yellow>Shift <gray>+/- 0.5"));
        }
        boolean cut = plugin.getConfig().getBoolean("bosses.cutscene", true);
        inv.setItem(41, Icons.gui(plugin, cut ? "on" : "off", "<aqua>Spawn cutscene: " + (cut ? "<green>ON" : "<red>OFF"),
                "<gray>Length: <white>" + fmtNum(plugin.getConfig().getDouble("bosses.cutscene-seconds", 6)) + "s",
                "<yellow>Click <gray>toggle", "<yellow>Right <gray>+1s  <yellow>Shift-right <gray>-1s"));
        inv.setItem(42, Icons.button(plugin, Material.PLAYER_HEAD, "<aqua>Players needed: <white>" + plugin.getConfig().getInt("bosses.min-players-to-damage", 3),
                "<gray>The boss is shielded unless this many", "<gray>players are fighting it (1 = off).", "<yellow>Left <gray>+1  <yellow>Right <gray>-1"));
        inv.setItem(43, Icons.gui(plugin, "boss_fight", "<red><bold>Start a boss fight", "<gray>Opens the boss fight panel."));
        inv.setItem(4, Icons.gui(plugin, "info", "<gold>Boss settings",
                "<gray>Pick a boss to change its health, damage,", "<gray>special attack, speed, size, armor, drops...",
                "<gray>Changes apply to bosses spawned after."));
        inv.setItem(49, Icons.gui(plugin, "back", "<yellow>Back"));
        admin.openInventory(inv);
    }

    private static String fmtNum(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.2f", v);
    }

    private void bossesClick(Player admin, int raw, ClickType click) {
        com.cosmicabilities.boss.BossType[] types = com.cosmicabilities.boss.BossType.values();
        for (int i = 0; i < types.length; i++) if (raw == BOSS_PICK_SLOTS[i]) { openBossEdit(admin, types[i]); return; }
        String[] keys = {"health-multiplier", "damage-multiplier", "size-multiplier"};
        if (raw >= 37 && raw <= 39) {
            String k = "bosses." + keys[raw - 37];
            double step = click.isShiftClick() ? 0.5 : 0.1;
            double v = plugin.getConfig().getDouble(k, 1.0) + (click.isRightClick() ? -step : step);
            plugin.getConfig().set(k, Math.max(0.1, Math.min(raw == 39 ? 4 : 50, Math.round(v * 100) / 100.0)));
        } else if (raw == 41) {
            if (click.isRightClick()) {
                double v = plugin.getConfig().getDouble("bosses.cutscene-seconds", 6) + (click.isShiftClick() ? -1 : 1);
                plugin.getConfig().set("bosses.cutscene-seconds", Math.max(2, Math.min(20, v)));
            } else {
                plugin.getConfig().set("bosses.cutscene", !plugin.getConfig().getBoolean("bosses.cutscene", true));
            }
        } else if (raw == 42) {
            int v = plugin.getConfig().getInt("bosses.min-players-to-damage", 3) + (click.isRightClick() ? -1 : 1);
            plugin.getConfig().set("bosses.min-players-to-damage", Math.max(1, Math.min(20, v)));
        } else if (raw == 43) {
            plugin.bossFightPanel().open(admin);
            return;
        } else if (raw == 49) {
            open(admin);
            return;
        } else {
            return;
        }
        plugin.saveConfig();
        openBosses(admin);
    }

    private void openBossEdit(Player admin, com.cosmicabilities.boss.BossType type) {
        Holder h = new Holder(Page.BOSS_EDIT);
        h.key = type.name();
        Inventory inv = create(h, 45, "<dark_red>" + type.displayName + " settings");
        for (int i = 0; i < BOSS_SETTINGS.size(); i++) {
            BossSetting st = BOSS_SETTINGS.get(i);
            double def = st.key().equals("size") ? 1.0 : 0;
            double v = plugin.bosses().cfg(type, st.key(), def);
            inv.setItem(BOSS_SETTING_SLOTS[i], Icons.button(plugin, st.icon(), "<red><bold>" + st.label() + ": <white>" + fmtNum(v),
                    st.note().isEmpty() ? "<dark_gray>" + st.key() : "<gray>" + st.note(),
                    "<yellow>Left <gray>+" + fmtNum(st.step()) + "  <yellow>Right <gray>-" + fmtNum(st.step()),
                    "<yellow>Shift <gray>+/- " + fmtNum(st.big())));
        }
        inv.setItem(4, Icons.button(plugin, Material.BOOK, type.colored(), "<gray>Saved to config.yml -> bosses." + type.id(),
                "<gray>Applies to the next one you spawn."));
        inv.setItem(40, Icons.gui(plugin, "back", "<yellow>Back"));
        admin.openInventory(inv);
    }

    private void bossEditClick(Player admin, Holder h, int raw, ClickType click) {
        com.cosmicabilities.boss.BossType type = com.cosmicabilities.boss.BossType.valueOf(h.key);
        if (raw == 40) { openBosses(admin); return; }
        for (int i = 0; i < BOSS_SETTINGS.size(); i++) {
            if (raw != BOSS_SETTING_SLOTS[i]) continue;
            BossSetting st = BOSS_SETTINGS.get(i);
            double def = st.key().equals("size") ? 1.0 : 0;
            double step = click.isShiftClick() ? st.big() : st.step();
            double v = plugin.bosses().cfg(type, st.key(), def) + (click.isRightClick() ? -step : step);
            v = Math.max(st.min(), Math.min(st.max(), Math.round(v * 1000) / 1000.0));
            plugin.getConfig().set("bosses." + type.id() + "." + st.key(), v);
            plugin.saveConfig();
            openBossEdit(admin, type);
            return;
        }
    }

    // ------------------------------------------------------------------ supply drops

    private void openSupply(Player admin) {
        Holder h = new Holder(Page.SUPPLY);
        Inventory inv = create(h, 45, "<dark_green>Supply Drops");
        double radius = plugin.getConfig().getDouble("supply-drops.random-radius", 800);
        double minR = plugin.getConfig().getDouble("supply-drops.random-min-radius", 100);
        inv.setItem(11, Icons.gui(plugin, "random", "<green><bold>Spawn supply drop",
                "<gray>Falls at a random spot <white>" + fmtNum(minR) + "-" + fmtNum(radius), "<gray>blocks from spawn and is announced.",
                "", "<yellow>Shift-click <gray>drop it where you stand instead"));
        inv.setItem(13, Icons.gui(plugin, "loot", "<gold><bold>Edit the loot",
                "<gray>Opens a chest: put in any items", "<gray>(stacks, enchanted gear, cosmic items...).",
                "<gray>Items in it: <white>" + plugin.supplyDrops().lootCount(),
                plugin.supplyDrops().lootCount() == 0 ? "<dark_gray>Empty = uses supply-drops.loot in config" : ""));
        double chance = plugin.getConfig().getDouble("supply-drops.item-chance", 0.7);
        inv.setItem(15, Icons.button(plugin, Material.RABBIT_FOOT, "<aqua>Chance per item: <white>" + Math.round(chance * 100) + "%",
                "<gray>Each stack in the loot chest has this", "<gray>chance to be in a drop (100% = all of it).",
                "<yellow>Left <gray>+10%  <yellow>Right <gray>-10%"));
        inv.setItem(29, Icons.gui(plugin, "radius", "<aqua>Random radius: <white>" + fmtNum(radius) + " blocks",
                "<gray>How far from world spawn drops can land.", "<yellow>Left <gray>+100  <yellow>Right <gray>-100"));
        inv.setItem(30, Icons.button(plugin, Material.RECOVERY_COMPASS, "<aqua>Minimum distance: <white>" + fmtNum(minR) + " blocks",
                "<yellow>Left <gray>+50  <yellow>Right <gray>-50"));
        int despawn = plugin.getConfig().getInt("supply-drops.despawn-minutes", 10);
        inv.setItem(32, Icons.button(plugin, Material.CLOCK, "<aqua>Chest disappears after: <white>" + (despawn <= 0 ? "never" : despawn + " min"),
                "<yellow>Left <gray>+5  <yellow>Right <gray>-5"));
        inv.setItem(33, Icons.button(plugin, Material.CHEST, "<aqua>Supply Rain every: <white>" + plugin.getConfig().getInt("events.supply-rain-minutes", 5) + " min",
                "<gray>During the Supply Drop Rain event.", "<yellow>Left <gray>+1  <yellow>Right <gray>-1"));
        inv.setItem(40, Icons.gui(plugin, "back", "<yellow>Back"));
        admin.openInventory(inv);
    }

    private void supplyClick(Player admin, int raw, ClickType click) {
        int sign = click.isRightClick() ? -1 : 1;
        switch (raw) {
            case 11 -> {
                admin.closeInventory();
                if (click.isShiftClick()) plugin.supplyDrops().call(admin.getLocation(), admin);
                else plugin.supplyDrops().callRandom(admin.getWorld(), admin);
                return;
            }
            case 13 -> { plugin.supplyDrops().openEditor(admin); return; }
            case 15 -> plugin.getConfig().set("supply-drops.item-chance",
                    Math.max(0.1, Math.min(1.0, Math.round((plugin.getConfig().getDouble("supply-drops.item-chance", 0.7) + sign * 0.1) * 10) / 10.0)));
            case 29 -> plugin.getConfig().set("supply-drops.random-radius",
                    Math.max(100, Math.min(30000, plugin.getConfig().getDouble("supply-drops.random-radius", 800) + sign * 100)));
            case 30 -> plugin.getConfig().set("supply-drops.random-min-radius",
                    Math.max(0, Math.min(29000, plugin.getConfig().getDouble("supply-drops.random-min-radius", 100) + sign * 50)));
            case 32 -> plugin.getConfig().set("supply-drops.despawn-minutes",
                    Math.max(0, Math.min(240, plugin.getConfig().getInt("supply-drops.despawn-minutes", 10) + sign * 5)));
            case 33 -> plugin.getConfig().set("events.supply-rain-minutes",
                    Math.max(1, Math.min(120, plugin.getConfig().getInt("events.supply-rain-minutes", 5) + sign)));
            case 40 -> { open(admin); return; }
            default -> { return; }
        }
        plugin.saveConfig();
        openSupply(admin);
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
        inv.setItem(47, Icons.gui(plugin, auto ? "on" : "off", "<aqua>Auto-end season: " + (auto ? "<green>ON" : "<red>OFF"),
                "<gray>When on, the season ends by itself", "<gray>and the richest team gets rewarded."));
        inv.setItem(51, Icons.gui(plugin, "teams", "<gold><bold>End season now",
                "<gray>The richest team's members each get", "<gray>a rare boss/dragon ability orb,", "<gray>then every bank resets to $0.",
                "", "<red>Shift-click to confirm"));
        inv.setItem(49, Icons.gui(plugin, "back", "<yellow>Back"));
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
