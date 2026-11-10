package com.cosmicabilities.auction;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.economy.Economy;
import com.cosmicabilities.gui.Icons;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /ah - the auction house. Hold an item and /ah sell <price> to list it; anyone can buy it with cash (the same cash
 * as coinflips). Unsold items expire after auction.expire-hours and go to the seller's "Collect" bin.
 * Sellers are paid even when offline.
 */
public class AuctionHouse implements Listener, CommandExecutor, TabCompleter {
    enum Page { BROWSE, MINE, COLLECT, CONFIRM }

    public static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Page page;
        final int pageNo;
        final Map<Integer, String> slots = new HashMap<>();
        String confirm;
        Holder(Page page, int pageNo) { this.page = page; this.pageNo = pageNo; }
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    record Listing(String id, UUID seller, String sellerName, long price, ItemStack item, long listed) {}

    private final CosmicAbilitiesPlugin plugin;
    private final File file;
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    /** expired / cancelled items waiting for their owner */
    private final Map<UUID, List<ItemStack>> bins = new HashMap<>();

    public AuctionHouse(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        load();
    }

    private Economy eco() { return plugin.economy(); }
    private String $(long v) { return "$" + eco().format(v); }
    private void msg(CommandSender p, String mini) { p.sendMessage(plugin.mm(plugin.prefix() + mini)); }

    // ------------------------------------------------------------------ storage

    private static String enc(ItemStack it) { return Base64.getEncoder().encodeToString(it.serializeAsBytes()); }
    private static ItemStack dec(String s) { return ItemStack.deserializeBytes(Base64.getDecoder().decode(s)); }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ls = yml.getConfigurationSection("listings");
        if (ls != null) for (String id : ls.getKeys(false)) {
            try {
                ConfigurationSection s = ls.getConfigurationSection(id);
                listings.put(id, new Listing(id, UUID.fromString(s.getString("seller")), s.getString("name", "?"), s.getLong("price"),
                        dec(s.getString("item")), s.getLong("listed")));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping broken auction listing " + id);
            }
        }
        ConfigurationSection bs = yml.getConfigurationSection("bins");
        if (bs != null) for (String uuid : bs.getKeys(false)) {
            List<ItemStack> items = new ArrayList<>();
            for (String s : bs.getStringList(uuid)) {
                try { items.add(dec(s)); } catch (RuntimeException ignored) { }
            }
            bins.put(UUID.fromString(uuid), items);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Listing l : listings.values()) {
            String b = "listings." + l.id() + ".";
            yml.set(b + "seller", l.seller().toString());
            yml.set(b + "name", l.sellerName());
            yml.set(b + "price", l.price());
            yml.set(b + "item", enc(l.item()));
            yml.set(b + "listed", l.listed());
        }
        for (Map.Entry<UUID, List<ItemStack>> e : bins.entrySet()) {
            List<String> enc = new ArrayList<>();
            for (ItemStack it : e.getValue()) enc.add(enc(it));
            if (!enc.isEmpty()) yml.set("bins." + e.getKey(), enc);
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Couldn't save auctions.yml: " + e.getMessage());
        }
    }

    /** Runs every minute: expired listings go back to their seller's collection bin. */
    public void expire() {
        long max = plugin.getConfig().getLong("auction.expire-hours", 48) * 3600_000L;
        if (max <= 0) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Listing l : new ArrayList<>(listings.values())) {
            if (now - l.listed() < max) continue;
            listings.remove(l.id());
            bins.computeIfAbsent(l.seller(), k -> new ArrayList<>()).add(l.item());
            Player s = Bukkit.getPlayer(l.seller());
            if (s != null) msg(s, "<gray>Your auction expired. Get it back in <yellow>/ah <gray>-> Collect.");
            changed = true;
        }
        if (changed) save();
    }

    // ------------------------------------------------------------------ menus

    private static final int PER_PAGE = 45;

    private Inventory frame(Holder h, String title) {
        Inventory inv = Bukkit.createInventory(h, 54, plugin.mm(title));
        h.inventory = inv;
        ItemStack bar = Icons.button(plugin, Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, bar);
        return inv;
    }

    private ItemStack shown(Listing l, Player viewer) {
        ItemStack it = l.item().clone();
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(plugin.mm("<!italic><gray>Price: <gold><bold>" + $(l.price())));
        lore.add(plugin.mm("<!italic><gray>Seller: <white>" + l.sellerName()));
        long hours = Math.max(0, plugin.getConfig().getLong("auction.expire-hours", 48) * 3600_000L - (System.currentTimeMillis() - l.listed())) / 3600_000L;
        lore.add(plugin.mm("<!italic><gray>Expires in: <white>" + hours + "h"));
        lore.add(Component.empty());
        if (l.seller().equals(viewer.getUniqueId())) lore.add(plugin.mm("<!italic><red>Your listing <gray>- click to take it down"));
        else lore.add(plugin.mm("<!italic><green><bold>Click to buy"));
        if (BanPistol.isStaff(viewer) && !l.seller().equals(viewer.getUniqueId())) lore.add(plugin.mm("<!italic><dark_red>Admin: shift + right-click to remove"));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public void open(Player p, int page) {
        List<Listing> all = new ArrayList<>(listings.values());
        java.util.Collections.reverse(all);   // newest first
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder(Page.BROWSE, page);
        Inventory inv = frame(h, "<gold><bold>Auction House <dark_gray>(" + (page + 1) + "/" + pages + ")");
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < all.size(); i++) {
            Listing l = all.get(page * PER_PAGE + i);
            inv.setItem(i, shown(l, p));
            h.slots.put(i, l.id());
        }
        if (all.isEmpty()) inv.setItem(22, Icons.button(plugin, Material.BARRIER, "<gray>Nothing for sale yet",
                "<gray>Hold an item and type", "<yellow>/ah sell <price>"));
        if (page > 0) inv.setItem(45, Icons.button(plugin, Material.ARROW, "<yellow>Previous page"));
        inv.setItem(47, Icons.button(plugin, Material.CHEST, "<white><bold>My listings", "<gray>See or take down what you're selling"));
        int binSize = bins.getOrDefault(p.getUniqueId(), List.of()).size();
        inv.setItem(48, Icons.button(plugin, Material.HOPPER, "<white><bold>Collect" + (binSize > 0 ? " <red>(" + binSize + ")" : ""),
                "<gray>Expired and taken-down items"));
        inv.setItem(49, plugin.coinflip().coin("<gold><bold>Your cash: <yellow>" + $(eco().balance(p.getUniqueId())),
                "<gray>Sell: hold an item, <yellow>/ah sell <price>"));
        inv.setItem(50, Icons.button(plugin, Material.BOOK, "<gold><bold>How it works",
                "<gray>Hold the item you want to sell and type", "<yellow>/ah sell <price> <gray>(e.g. /ah sell 5000 or 5k).",
                "<gray>Buyers pay with cash; you get paid right away,", "<gray>even if you're offline.",
                "<gray>Unsold items come back after <white>" + plugin.getConfig().getLong("auction.expire-hours", 48) + "h <gray>(Collect)."));
        inv.setItem(51, Icons.button(plugin, Material.CLOCK, "<white>Refresh"));
        if (page < pages - 1) inv.setItem(53, Icons.button(plugin, Material.ARROW, "<yellow>Next page"));
        p.openInventory(inv);
    }

    private void openMine(Player p) {
        Holder h = new Holder(Page.MINE, 0);
        Inventory inv = frame(h, "<gold>My listings <dark_gray>- click to take down");
        int i = 0;
        for (Listing l : listings.values()) {
            if (!l.seller().equals(p.getUniqueId()) || i >= PER_PAGE) continue;
            inv.setItem(i, shown(l, p));
            h.slots.put(i++, l.id());
        }
        inv.setItem(49, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        p.openInventory(inv);
    }

    private void openCollect(Player p) {
        Holder h = new Holder(Page.COLLECT, 0);
        Inventory inv = frame(h, "<gold>Collect <dark_gray>- click to take back");
        List<ItemStack> bin = bins.getOrDefault(p.getUniqueId(), List.of());
        for (int i = 0; i < bin.size() && i < PER_PAGE; i++) inv.setItem(i, bin.get(i));
        inv.setItem(48, Icons.button(plugin, Material.HOPPER, "<green>Take everything"));
        inv.setItem(49, Icons.button(plugin, Material.ARROW, "<yellow>Back"));
        p.openInventory(inv);
    }

    private void openConfirm(Player p, Listing l) {
        Holder h = new Holder(Page.CONFIRM, 0);
        h.confirm = l.id();
        Inventory inv = Bukkit.createInventory(h, 27, plugin.mm("<gold>Buy for " + $(l.price()) + "?"));
        h.inventory = inv;
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        inv.setItem(13, shown(l, p));
        inv.setItem(11, Icons.button(plugin, Material.LIME_CONCRETE, "<green><bold>Buy for " + $(l.price()),
                "<gray>You have " + $(eco().balance(p.getUniqueId()))));
        inv.setItem(15, Icons.button(plugin, Material.RED_CONCRETE, "<red>Cancel"));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ actions

    private void sell(Player p, long price) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { msg(p, "<red>Hold the item you want to sell."); return; }
        if (plugin.banPistol().isPistol(hand)) { msg(p, "<red>You can't sell that."); return; }
        long min = plugin.getConfig().getLong("auction.min-price", 1);
        if (price < min) { msg(p, "<red>The minimum price is " + $(min) + "."); return; }
        int max = plugin.getConfig().getInt("auction.max-listings", 10);
        int mine = 0;
        for (Listing l : listings.values()) if (l.seller().equals(p.getUniqueId())) mine++;
        if (mine >= max) { msg(p, "<red>You can only have " + max + " items listed at once."); return; }
        ItemStack item = hand.clone();
        p.getInventory().setItemInMainHand(null);
        String id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
        listings.put(id, new Listing(id, p.getUniqueId(), p.getName(), price, item, System.currentTimeMillis()));
        save();
        msg(p, "<green>Listed <white>" + item.getAmount() + "x " + name(item) + " <green>for <gold>" + $(price) + "<green>.");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        if (plugin.getConfig().getBoolean("auction.announce", true)) {
            Bukkit.broadcast(plugin.mm(plugin.prefix() + "<white>" + p.getName() + " <gray>is selling <white>" + item.getAmount() + "x "
                    + name(item) + " <gray>for <gold>" + $(price) + " <gray>- <yellow>/ah"));
        }
    }

    private String name(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(m.displayName());
        }
        StringBuilder sb = new StringBuilder();
        for (String w : it.getType().name().toLowerCase(Locale.ROOT).split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private void buy(Player p, String id) {
        Listing l = listings.get(id);
        if (l == null) { msg(p, "<red>Someone already bought that."); open(p, 0); return; }
        if (l.seller().equals(p.getUniqueId())) { open(p, 0); return; }
        if (p.getInventory().firstEmpty() < 0) { msg(p, "<red>Make room in your inventory first."); return; }
        if (!eco().take(p.getUniqueId(), l.price())) { msg(p, "<red>You need " + $(l.price()) + "."); return; }
        listings.remove(id);
        double tax = Math.max(0, Math.min(0.5, plugin.getConfig().getDouble("auction.tax", 0.0)));
        long paid = l.price() - Math.round(l.price() * tax);
        eco().add(l.seller(), paid);
        p.getInventory().addItem(l.item().clone());
        save();
        msg(p, "<green>Bought <white>" + l.item().getAmount() + "x " + name(l.item()) + " <green>for <gold>" + $(l.price()) + "<green>.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        Player seller = Bukkit.getPlayer(l.seller());
        if (seller != null) {
            msg(seller, "<white>" + p.getName() + " <gray>bought your <white>" + name(l.item()) + " <gray>for <gold>" + $(paid) + "<gray>!");
            seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }
    }

    private void takeDown(Player p, String id, boolean byAdmin) {
        Listing l = listings.remove(id);
        if (l == null) return;
        bins.computeIfAbsent(l.seller(), k -> new ArrayList<>()).add(l.item());
        save();
        if (byAdmin) msg(p, "<gray>Removed the listing; it went to " + l.sellerName() + "'s Collect bin.");
        else msg(p, "<gray>Taken down. Get it from <yellow>Collect<gray>.");
    }

    private void collect(Player p, int index) {
        List<ItemStack> bin = bins.get(p.getUniqueId());
        if (bin == null || bin.isEmpty()) return;
        List<ItemStack> take = index < 0 ? new ArrayList<>(bin) : index < bin.size() ? List.of(bin.get(index)) : List.of();
        for (ItemStack it : take) {
            if (p.getInventory().firstEmpty() < 0) { msg(p, "<red>Your inventory is full."); break; }
            p.getInventory().addItem(it);
            bin.remove(it);
        }
        save();
    }

    // ------------------------------------------------------------------ events

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
        ClickType click = event.getClick();
        switch (h.page) {
            case BROWSE -> {
                String id = h.slots.get(raw);
                if (id != null) {
                    Listing l = listings.get(id);
                    if (l == null) { open(p, h.pageNo); return; }
                    if (l.seller().equals(p.getUniqueId())) { takeDown(p, id, false); open(p, h.pageNo); }
                    else if (click == ClickType.SHIFT_RIGHT && BanPistol.isStaff(p)) { takeDown(p, id, true); open(p, h.pageNo); }
                    else openConfirm(p, l);
                    return;
                }
                switch (raw) {
                    case 45 -> open(p, h.pageNo - 1);
                    case 53 -> open(p, h.pageNo + 1);
                    case 47 -> openMine(p);
                    case 48 -> openCollect(p);
                    case 51 -> open(p, h.pageNo);
                    default -> { }
                }
            }
            case MINE -> {
                String id = h.slots.get(raw);
                if (id != null) { takeDown(p, id, false); openMine(p); }
                else if (raw == 49) open(p, 0);
            }
            case COLLECT -> {
                if (raw < PER_PAGE) { collect(p, raw); openCollect(p); }
                else if (raw == 48) { collect(p, -1); openCollect(p); }
                else if (raw == 49) open(p, 0);
            }
            case CONFIRM -> {
                if (raw == 11) { buy(p, h.confirm); open(p, 0); }
                else if (raw == 15) open(p, 0);
            }
        }
    }

    // ------------------------------------------------------------------ /ah

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length == 0) { open(p, 0); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell", "list" -> {
                long price = args.length > 1 ? Economy.parse(args[1]) : -1;
                if (price <= 0) { msg(p, "<gray>Usage: <yellow>/ah sell <price> <gray>(hold the item; e.g. 5000 or 5k)"); return true; }
                sell(p, price);
            }
            case "collect" -> openCollect(p);
            case "mine", "listings" -> openMine(p);
            default -> open(p, 0);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) out.addAll(List.of("sell", "mine", "collect"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("sell")) out.addAll(List.of("1000", "5000", "10k"));
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.startsWith(last));
        return out;
    }
}
