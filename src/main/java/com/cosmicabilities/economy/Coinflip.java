package com.cosmicabilities.economy;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.gui.Icons;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /cf - player coinflips. Put up a bet (the money is held until someone takes it or you cancel). Anyone who opens /cf
 * clicks your head to accept: the coin spins between both heads and lands on the winner, who takes the whole pot.
 */
public class Coinflip implements Listener, CommandExecutor, TabCompleter {
    private static final int[] LIST = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int CREATE = 45, MINE = 46, REFRESH = 47, CLOSE = 49, CASH = 53, HELP = 4;

    enum Page { LIST, BET, ROLL }

    public static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Page page;
        final Map<Integer, UUID> slots = new HashMap<>();
        Holder(Page page) { this.page = page; }
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private record Listing(UUID owner, String name, long bet, long created) {}

    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();   // keyed by listing id (owner + n)
    private final Map<UUID, Long> pendingBet = new ConcurrentHashMap<>();
    private final Set<UUID> typing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> rolling = ConcurrentHashMap.newKeySet();

    public Coinflip(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private Economy eco() { return plugin.economy(); }
    private long min() { return plugin.getConfig().getLong("coinflip.min-bet", 1000); }
    private long max() { return plugin.getConfig().getLong("coinflip.max-bet", 0); }   // 0 = no limit
    private String $(long v) { return "$" + eco().format(v); }

    private void msg(CommandSender p, String mini) {
        p.sendMessage(plugin.mm(plugin.prefix() + mini));
    }

    // ------------------------------------------------------------------ items

    public ItemStack coin(String name, String... lore) {
        ItemStack it = Icons.button(plugin, Material.SUNFLOWER, name, lore);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", "coin"));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack head(OfflinePlayer who, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(who);
        meta.displayName(plugin.mm("<!italic>" + name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(plugin.mm("<!italic>" + s));
        meta.lore(l);
        head.setItemMeta(meta);
        return head;
    }

    private Inventory frame(Holder h, int size, String title) {
        Inventory inv = Bukkit.createInventory(h, size, plugin.mm(title));
        h.inventory = inv;
        ItemStack pink = Icons.button(plugin, Material.PINK_STAINED_GLASS_PANE, " ");
        ItemStack inner = Icons.button(plugin, Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int r = i / 9, c = i % 9;
            inv.setItem(i, r == 0 || r == rows - 1 || c == 0 || c == 8 ? pink : inner);
        }
        return inv;
    }

    // ------------------------------------------------------------------ list page

    public void open(Player p) {
        Holder h = new Holder(Page.LIST);
        Inventory inv = frame(h, 54, "<#ff8fc7>Coinflip");
        long cash = eco().balance(p.getUniqueId());
        inv.setItem(HELP, Icons.button(plugin, Material.BOOK, "<#ff8fc7><bold>How coinflips work",
                "<gray>1. Click the <white>sign <gray>to put up a bet.",
                "<gray>   Your money is held until someone",
                "<gray>   accepts or you cancel.",
                "<gray>2. Click someone's <white>head <gray>to flip them",
                "<gray>   for the same amount.",
                "<gray>3. The coin lands on the winner's head.",
                "<gray>   They take the whole pot.",
                "", "<gray>Min bet <yellow>" + $(min()) + (max() > 0 ? " <gray>max <yellow>" + $(max()) : " <gray>- no max")));
        int i = 0;
        for (Map.Entry<UUID, Listing> e : listings.entrySet()) {
            if (i >= LIST.length) break;
            Listing l = e.getValue();
            boolean mine = l.owner().equals(p.getUniqueId());
            OfflinePlayer owner = Bukkit.getOfflinePlayer(l.owner());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Bet: <yellow><bold>" + $(l.bet()));
            lore.add("<gray>Pot: <gold>" + $(l.bet() * 2));
            lore.add("");
            if (mine) lore.add("<red>Click to cancel <gray>(refunds you)");
            else if (cash >= l.bet()) lore.add("<green><bold>Click to flip!");
            else lore.add("<red>You need " + $(l.bet()));
            inv.setItem(LIST[i], head(owner, "<#ff8fc7><bold>" + l.name(), lore));
            h.slots.put(LIST[i], e.getKey());
            i++;
        }
        if (i == 0) inv.setItem(22, Icons.button(plugin, Material.BARRIER, "<red>No coinflips right now",
                "<gray>Click the sign to start one!"));
        inv.setItem(CREATE, Icons.button(plugin, Material.OAK_SIGN, "<#ff8fc7><bold>Create a coinflip",
                "<gray>Choose how much to bet", "<gray>Min <yellow>" + $(min())));
        inv.setItem(MINE, Icons.button(plugin, Material.WRITABLE_BOOK, "<white>Cancel all my coinflips",
                "<gray>You have <white>" + count(p.getUniqueId()) + " <gray>up right now"));
        inv.setItem(REFRESH, Icons.button(plugin, Material.CLOCK, "<white>Refresh"));
        inv.setItem(CLOSE, Icons.button(plugin, Material.BARRIER, "<red>Close"));
        inv.setItem(CASH, coin("<gold><bold>Your cash: <yellow>" + $(cash),
                "<gray>You can bet <yellow>" + $(min()) + " <gray>up to <yellow>" + $(maxBetFor(cash)),
                "<gray>+<yellow>" + $(plugin.economy().killReward()) + " <gray>for every player kill"));
        p.openInventory(inv);
    }

    private long maxBetFor(long cash) {
        return max() > 0 ? Math.min(max(), cash) : cash;
    }

    private int count(UUID owner) {
        int n = 0;
        for (Listing l : listings.values()) if (l.owner().equals(owner)) n++;
        return n;
    }

    // ------------------------------------------------------------------ bet page

    private static final int[] DELTA_SLOTS = {19, 20, 21, 23, 24, 25, 28, 29, 30, 32, 33, 34};
    private static final long[] DELTAS = {-1000, -10000, -100000, 1000, 10000, 100000, -1000000, -10000000, -100000000, 1000000, 10000000, 100000000};

    private void openBet(Player p) {
        Holder h = new Holder(Page.BET);
        Inventory inv = frame(h, 54, "<#ff8fc7>Coinflip - choose your bet");
        long cash = eco().balance(p.getUniqueId());
        long bet = Math.max(min(), pendingBet.getOrDefault(p.getUniqueId(), min()));
        pendingBet.put(p.getUniqueId(), bet);
        inv.setItem(13, coin("<gold><bold>Bet: <yellow>" + $(bet), "<gray>Your cash: <yellow>" + $(cash), "<gray>Pot if accepted: <gold>" + $(bet * 2)));
        for (int i = 0; i < DELTA_SLOTS.length; i++) {
            long d = DELTAS[i];
            boolean up = d > 0;
            inv.setItem(DELTA_SLOTS[i], Icons.button(plugin, up ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                    (up ? "<green>+" : "<red>-") + $(Math.abs(d))));
        }
        inv.setItem(22, Icons.button(plugin, Material.GOLD_BLOCK, "<gold><bold>All in", "<gray>Bet everything: <yellow>" + $(maxBetFor(cash))));
        inv.setItem(31, Icons.button(plugin, Material.NAME_TAG, "<white><bold>Type an amount", "<gray>e.g. <white>25000<gray>, <white>25k<gray>, <white>1.5m"));
        inv.setItem(40, Icons.button(plugin, Material.LIME_CONCRETE, "<green><bold>Put up coinflip",
                "<gray>Bet <yellow>" + $(bet), cash < bet ? "<red>You don't have enough" : "<gray>The money is held until it's flipped"));
        inv.setItem(45, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        inv.setItem(CASH, coin("<gold><bold>Your cash: <yellow>" + $(cash), "<gray>Min bet <yellow>" + $(min())
                + (max() > 0 ? " <gray>max <yellow>" + $(max()) : " <gray>- no max")));
        p.openInventory(inv);
    }

    private void create(Player p, long bet) {
        if (bet < min()) { msg(p, "<red>The minimum bet is " + $(min()) + "."); return; }
        if (max() > 0 && bet > max()) { msg(p, "<red>The maximum bet is " + $(max()) + "."); return; }
        int maxListings = plugin.getConfig().getInt("coinflip.max-per-player", 3);
        if (count(p.getUniqueId()) >= maxListings) { msg(p, "<red>You can only have " + maxListings + " coinflips up at once."); return; }
        if (!eco().take(p.getUniqueId(), bet)) { msg(p, "<red>You only have " + $(eco().balance(p.getUniqueId())) + "."); return; }
        listings.put(UUID.randomUUID(), new Listing(p.getUniqueId(), p.getName(), bet, System.currentTimeMillis()));
        msg(p, "<#ff8fc7>Coinflip up for <yellow>" + $(bet) + "<gray>. Waiting for someone to accept...");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        if (bet >= plugin.getConfig().getLong("coinflip.announce-min", 50000)) {
            Bukkit.broadcast(plugin.mm(plugin.prefix() + "<white>" + p.getName() + " <gray>put up a <#ff8fc7>coinflip <gray>for <yellow>"
                    + $(bet) + "<gray>! <yellow>/cf"));
        }
        open(p);
    }

    private void cancel(UUID listingId, boolean refund) {
        Listing l = listings.remove(listingId);
        if (l != null && refund) eco().add(l.owner(), l.bet());
    }

    private void cancelAll(UUID owner) {
        for (UUID id : new ArrayList<>(listings.keySet())) if (listings.get(id).owner().equals(owner)) cancel(id, true);
    }

    // ------------------------------------------------------------------ the flip

    private void accept(Player challenger, UUID listingId) {
        Listing l = listings.get(listingId);
        if (l == null) { open(challenger); return; }
        Player owner = Bukkit.getPlayer(l.owner());
        if (owner == null) { cancel(listingId, true); open(challenger); return; }
        if (!eco().take(challenger.getUniqueId(), l.bet())) {
            msg(challenger, "<red>You need " + $(l.bet()) + " to accept that coinflip.");
            return;
        }
        listings.remove(listingId);
        boolean ownerWins = ThreadLocalRandom.current().nextBoolean();
        roll(owner, challenger, l.bet(), ownerWins ? owner : challenger);
    }

    /** Both players watch the coin flip between their heads, slowing down until it lands on the winner. */
    private void roll(Player a, Player b, long bet, Player winner) {
        Holder h = new Holder(Page.ROLL);
        Inventory inv = frame(h, 27, "<#ff8fc7>Coinflip - " + $(bet * 2) + " pot");
        inv.setItem(11, head(a, "<white>" + a.getName(), List.of("<gray>Bet " + $(bet))));
        inv.setItem(15, head(b, "<white>" + b.getName(), List.of("<gray>Bet " + $(bet))));
        rolling.add(a.getUniqueId());
        rolling.add(b.getUniqueId());
        for (Player v : List.of(a, b)) v.openInventory(inv);
        ItemStack headA = head(a, "<#ff8fc7><bold>" + a.getName(), List.of());
        ItemStack headB = head(b, "<#ff8fc7><bold>" + b.getName(), List.of());
        // the final face must be the winner: an even number of flips lands on a, odd on b
        int flips = 18 + ThreadLocalRandom.current().nextInt(3) * 2 + (winner.equals(a) ? 0 : 1);
        new BukkitRunnable() {
            int n = 0, wait = 0;
            @Override
            public void run() {
                if (wait-- > 0) return;
                boolean showA = n % 2 == 0;
                inv.setItem(13, showA ? headA : headB);
                for (Player v : List.of(a, b)) {
                    if (v.isOnline()) v.playSound(v.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, showA ? 1.4f : 1.1f);
                }
                if (n >= flips) {
                    cancel();
                    finish(inv, a, b, bet, winner);
                    return;
                }
                n++;
                double prog = n / (double) flips;
                wait = (int) Math.round(prog * prog * 7);
            }
        }.runTaskTimer(plugin, 10L, 1L);
    }

    private void finish(Inventory inv, Player a, Player b, long bet, Player winner) {
        Player loser = winner.equals(a) ? b : a;
        double tax = Math.max(0, Math.min(0.5, plugin.getConfig().getDouble("coinflip.tax", 0.0)));
        long pot = bet * 2, prize = pot - Math.round(pot * tax);
        eco().add(winner.getUniqueId(), prize);
        rolling.remove(a.getUniqueId());
        rolling.remove(b.getUniqueId());
        ItemStack gold = Icons.button(plugin, Material.YELLOW_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) if (i != 13) inv.setItem(i, gold);
        inv.setItem(13, head(winner, "<gold><bold>" + winner.getName() + " WINS", List.of("<yellow>+" + $(prize))));
        if (winner.isOnline()) {
            winner.showTitle(Title.title(plugin.mm("<gold><bold>YOU WON"), plugin.mm("<yellow>+" + $(prize)),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
        }
        if (loser.isOnline()) {
            loser.showTitle(Title.title(plugin.mm("<red><bold>You lost"), plugin.mm("<gray>-" + $(bet)),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
        }
        if (pot >= plugin.getConfig().getLong("coinflip.announce-min", 50000)) {
            Bukkit.broadcast(plugin.mm(plugin.prefix() + "<white>" + winner.getName() + " <gray>won a <yellow>" + $(pot)
                    + " <#ff8fc7>coinflip <gray>against <white>" + loser.getName() + "<gray>!"));
        } else {
            for (Player v : List.of(a, b)) if (v.isOnline()) msg(v, "<white>" + winner.getName() + " <gray>won the coinflip (<yellow>" + $(prize) + "<gray>).");
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player v : List.of(a, b)) {
                if (v.isOnline() && v.getOpenInventory().getTopInventory() == inv) v.closeInventory();
            }
        }, 60L);
    }

    // ------------------------------------------------------------------ clicks / chat / quit

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int raw = event.getRawSlot();
        if (raw >= event.getInventory().getSize()) return;
        switch (h.page) {
            case LIST -> {
                UUID id = h.slots.get(raw);
                if (id != null) {
                    Listing l = listings.get(id);
                    if (l == null) { open(p); return; }
                    if (l.owner().equals(p.getUniqueId())) {
                        cancel(id, true);
                        msg(p, "<gray>Coinflip cancelled, <yellow>" + $(l.bet()) + " <gray>refunded.");
                        open(p);
                    } else {
                        accept(p, id);
                    }
                    return;
                }
                switch (raw) {
                    case CREATE -> openBet(p);
                    case MINE -> { cancelAll(p.getUniqueId()); open(p); }
                    case REFRESH -> open(p);
                    case CLOSE -> p.closeInventory();
                    default -> { }
                }
            }
            case BET -> {
                long cash = eco().balance(p.getUniqueId());
                long bet = pendingBet.getOrDefault(p.getUniqueId(), min());
                for (int i = 0; i < DELTA_SLOTS.length; i++) {
                    if (raw != DELTA_SLOTS[i]) continue;
                    bet = Math.max(min(), bet + DELTAS[i]);
                    if (max() > 0) bet = Math.min(max(), bet);
                    pendingBet.put(p.getUniqueId(), bet);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                    openBet(p);
                    return;
                }
                switch (raw) {
                    case 22 -> { pendingBet.put(p.getUniqueId(), Math.max(min(), maxBetFor(cash))); openBet(p); }
                    case 31 -> {
                        p.closeInventory();
                        typing.add(p.getUniqueId());
                        msg(p, "<#ff8fc7>Type your bet in chat <gray>(e.g. 25000, 25k, 1.5m) or <white>cancel<gray>.");
                    }
                    case 40 -> create(p, bet);
                    case 45 -> open(p);
                    default -> { }
                }
            }
            case ROLL -> { }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!typing.remove(p.getUniqueId())) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel")) { msg(p, "<gray>Cancelled."); return; }
            long amount = Economy.parse(text);
            if (amount <= 0) { msg(p, "<red>That's not an amount. Try 25000 or 25k."); return; }
            pendingBet.put(p.getUniqueId(), amount);
            openBet(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancelAll(id);
        typing.remove(id);
        pendingBet.remove(id);
    }

    /** Refund every open coinflip (server stopping). */
    public void shutdown() {
        for (UUID id : new ArrayList<>(listings.keySet())) cancel(id, true);
    }

    // ------------------------------------------------------------------ /cf

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { msg(sender, "<red>Players only."); return true; }
        if (rolling.contains(p.getUniqueId())) return true;
        if (args.length == 0) { open(p); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create", "new", "bet" -> {
                if (args.length < 2) { openBet(p); return true; }
                long amount = Economy.parse(args[1]);
                if (amount <= 0) { msg(p, "<red>Usage: /cf create <amount>  (e.g. 5000 or 5k)"); return true; }
                create(p, amount);
            }
            case "cancel" -> { cancelAll(p.getUniqueId()); msg(p, "<gray>Your coinflips were cancelled and refunded."); }
            default -> {
                long amount = Economy.parse(args[0]);
                if (amount > 0) create(p, amount); else open(p);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) out.addAll(List.of("create", "cancel"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("create")) out.addAll(List.of("1000", "5000", "10k", "50k"));
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.startsWith(last));
        return out;
    }
}
