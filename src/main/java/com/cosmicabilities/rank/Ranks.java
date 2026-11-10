package com.cosmicabilities.rank;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Store ranks bought on Tebex: Starlight, Cosmos, Supernova.
 * Tebex runs "cosmicrank give {username} <rank>" -> the player gets an enchanted rank shulker box; placing it
 * grants the rank. Each rank shows [RANK] beside their name and gives a bigger ender chest (rows set in config).
 */
public class Ranks implements Listener, CommandExecutor, TabCompleter {
    public static final class Chest implements InventoryHolder {
        Inventory inventory;
        final UUID owner;
        final int rows;
        final List<ItemStack> overflow;
        final Block block;
        Chest(UUID owner, int rows, List<ItemStack> overflow, Block block) { this.owner = owner; this.rows = rows; this.overflow = overflow; this.block = block; }
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey voucherKey;
    private final File chestFolder;
    private final Map<UUID, Chest> open = new HashMap<>();

    public Ranks(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.voucherKey = new NamespacedKey(plugin, "rank_voucher");
        this.chestFolder = new File(plugin.getDataFolder(), "enderchests");
        chestFolder.mkdirs();
    }

    // ------------------------------------------------------------------ rank state

    public PlayerRank rankOf(UUID id) {
        PlayerData d = plugin.data().get(id);
        if (d == null) return PlayerRank.NONE;
        PlayerRank r = PlayerRank.parse(d.rank());
        return r == null ? PlayerRank.NONE : r;
    }

    public void setRank(UUID id, PlayerRank rank) {
        PlayerData d = plugin.data().get(id);
        boolean offline = d == null;
        if (offline) d = plugin.data().loadOffline(id);
        d.setRank(rank.id);
        plugin.data().save(d);
        Player p = Bukkit.getPlayer(id);
        if (p != null) plugin.tags().refresh(p);
    }

    public int rows(PlayerRank rank) {
        return Math.max(1, Math.min(6, plugin.getConfig().getInt("ranks." + rank.id + ".echest-rows", rank.defaultRows)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.tags().refresh(event.getPlayer());
    }

    // ------------------------------------------------------------------ vouchers

    public ItemStack voucher(PlayerRank rank) {
        ItemStack it = new ItemStack(rank.box);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plugin.mm("<!italic>" + rank.mini() + " <white>Rank"));
        meta.lore(List.of(plugin.mm("<!italic><gray>Place this box to unlock the"),
                plugin.mm("<!italic>" + rank.mini() + " <gray>rank:"),
                plugin.mm("<!italic><gray>- <white>[" + rank.tag + "] <gray>beside your name"),
                plugin.mm("<!italic><gray>- <white>" + rows(rank) * 9 + " slot <gray>ender chest"),
                Component.empty(),
                plugin.mm("<!italic><dark_gray>Thank you for supporting the server!")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(voucherKey, PersistentDataType.STRING, rank.id);
        it.setItemMeta(meta);
        return it;
    }

    public PlayerRank voucherRank(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(voucherKey, PersistentDataType.STRING);
        return id == null ? null : PlayerRank.parse(id);
    }

    /** Placing the rank box (anywhere) grants the rank instead of placing a shulker. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack it = event.getItemInHand();
        PlayerRank rank = voucherRank(it);
        if (rank == null) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        PlayerRank current = rankOf(p.getUniqueId());
        if (current.ordinal() >= rank.ordinal()) {
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>You already have " + current.mini() + "<gray>. Keep the box or give it to someone."));
            return;
        }
        it.setAmount(it.getAmount() - 1);
        setRank(p.getUniqueId(), rank);
        p.showTitle(Title.title(plugin.mm(rank.mini()), plugin.mm("<white>Rank unlocked! <gray>Thank you!"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(800))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.3);
        Bukkit.broadcast(plugin.mm(plugin.prefix() + "<white>" + p.getName() + " <gray>unlocked " + rank.mini() + "<gray>!"));
    }

    // ------------------------------------------------------------------ bigger ender chest

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnderChest(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = event.getClickedBlock();
        if (b == null || b.getType() != Material.ENDER_CHEST) return;
        Player p = event.getPlayer();
        if (p.isSneaking() && !p.getInventory().getItemInMainHand().getType().isAir()) return;
        if (!plugin.getConfig().getBoolean("ranks.custom-ender-chest", true)) return;
        event.setCancelled(true);
        openChest(p, b);
    }

    public void openChest(Player p, Block block) {
        int rows = rows(rankOf(p.getUniqueId()));
        List<ItemStack> all = load(p);
        Chest h = new Chest(p.getUniqueId(), rows, new ArrayList<>(), block);
        Inventory inv = Bukkit.createInventory(h, rows * 9, plugin.mm("<dark_purple>Ender Chest <dark_gray>(" + rows * 9 + " slots)"));
        h.inventory = inv;
        for (int i = 0; i < all.size(); i++) {
            if (i < rows * 9) inv.setItem(i, all.get(i));
            else if (all.get(i) != null) h.overflow.add(all.get(i));
        }
        open.put(p.getUniqueId(), h);
        p.openInventory(inv);
        if (block != null && block.getState() instanceof org.bukkit.block.Lidded lid) lid.open();
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1f);
        if (!h.overflow.isEmpty()) {
            p.sendMessage(plugin.mm(plugin.prefix() + "<yellow>" + h.overflow.size() + " stacks are stored past your current ender chest size;"
                    + " they come back when you have a bigger rank again."));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest h)) return;
        open.remove(h.owner);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < h.rows * 9; i++) items.add(event.getInventory().getItem(i));
        items.addAll(h.overflow);
        save(h.owner, items);
        if (h.block != null && h.block.getType() == Material.ENDER_CHEST && h.block.getState() instanceof org.bukkit.block.Lidded lid) lid.close();
        if (event.getPlayer() instanceof Player p) p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.6f, 1f);
    }

    public void saveOpen() {
        for (Chest h : new ArrayList<>(open.values())) {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < h.rows * 9; i++) items.add(h.inventory.getItem(i));
            items.addAll(h.overflow);
            save(h.owner, items);
        }
    }

    private File file(UUID id) {
        return new File(chestFolder, id + ".yml");
    }

    /** Loads the player's stored ender chest; the first time, their vanilla ender chest is moved in. */
    private List<ItemStack> load(Player p) {
        File f = file(p.getUniqueId());
        List<ItemStack> out = new ArrayList<>();
        if (!f.exists()) {
            for (ItemStack it : p.getEnderChest().getContents()) out.add(it);
            p.getEnderChest().clear();
            save(p.getUniqueId(), out);
            return out;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        for (String s : yml.getStringList("items")) {
            if (s == null || s.isEmpty()) { out.add(null); continue; }
            try {
                out.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(s)));
            } catch (RuntimeException e) {
                out.add(null);
                plugin.getLogger().warning("Couldn't read an ender chest item for " + p.getName());
            }
        }
        return out;
    }

    private void save(UUID id, List<ItemStack> items) {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> enc = new ArrayList<>();
        for (ItemStack it : items) enc.add(it == null || it.getType().isAir() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        while (!enc.isEmpty() && enc.get(enc.size() - 1).isEmpty()) enc.remove(enc.size() - 1);
        yml.set("items", enc);
        try {
            yml.save(file(id));
        } catch (IOException e) {
            plugin.getLogger().severe("Couldn't save ender chest " + id + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ /cosmicrank (Tebex + admins)

    @Override
    @SuppressWarnings("deprecation")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!BanPistol.isAdmin(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(plugin.mm(plugin.prefix() + "<yellow>/cosmicrank give <player> <starlight|cosmos|supernova> <gray>- gives the rank box (Tebex uses this)"));
            sender.sendMessage(plugin.mm(plugin.prefix() + "<yellow>/cosmicrank set <player> <none|starlight|cosmos|supernova> <gray>- sets the rank directly"));
            return true;
        }
        PlayerRank rank = PlayerRank.parse(args[2]);
        if (rank == null) { sender.sendMessage(plugin.mm(plugin.prefix() + "<red>Unknown rank.")); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if (rank == PlayerRank.NONE) return true;
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) { sender.sendMessage(plugin.mm(plugin.prefix() + "<red>" + args[1] + " must be online.")); return true; }
                for (ItemStack left : p.getInventory().addItem(voucher(rank)).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                p.sendMessage(plugin.mm(plugin.prefix() + "<gray>You received a " + rank.mini() + " <gray>rank box! <white>Place it <gray>to unlock your rank."));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                sender.sendMessage(plugin.mm(plugin.prefix() + "<green>Gave " + p.getName() + " a " + rank.title() + " rank box."));
            }
            case "set" -> {
                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                if (!t.isOnline() && !t.hasPlayedBefore()) { sender.sendMessage(plugin.mm(plugin.prefix() + "<red>Unknown player.")); return true; }
                setRank(t.getUniqueId(), rank);
                sender.sendMessage(plugin.mm(plugin.prefix() + "<green>" + t.getName() + " is now " + rank.mini()));
            }
            default -> sender.sendMessage(plugin.mm(plugin.prefix() + "<red>give or set"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!BanPistol.isAdmin(sender)) return out;
        if (args.length == 1) out.addAll(List.of("give", "set"));
        else if (args.length == 2) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        else if (args.length == 3) for (PlayerRank r : PlayerRank.values()) if (r != PlayerRank.NONE || args[0].equalsIgnoreCase("set")) out.add(r.id);
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
