package com.cosmicabilities.stats;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.gui.Icons;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every player kill is logged (who, when, how much cash it paid) so admins can spot kill farming:
 * /killlog <player> or /adminpanel -> Players -> player -> Kill Log. Kept in killlog.yml (last 200 per player).
 */
public class KillLog implements CommandExecutor, Listener {
    public record Entry(long time, UUID victim, String victimName, long paid) {}

    public static final class Holder implements InventoryHolder {
        Inventory inventory;
        UUID back;
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final File file;
    private final Map<UUID, List<Entry>> log = new HashMap<>();
    private boolean dirty;

    public KillLog(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "killlog.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            try {
                List<Entry> list = new ArrayList<>();
                for (String line : yml.getStringList(k)) {
                    String[] p = line.split("\\|", 4);
                    list.add(new Entry(Long.parseLong(p[0]), UUID.fromString(p[1]), p[2], Long.parseLong(p[3])));
                }
                log.put(UUID.fromString(k), list);
            } catch (RuntimeException ignored) { }
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::save, 1200L, 1200L);
    }

    public void record(Player killer, Player victim, long paid) {
        List<Entry> list = log.computeIfAbsent(killer.getUniqueId(), k -> new ArrayList<>());
        list.add(new Entry(System.currentTimeMillis(), victim.getUniqueId(), victim.getName(), paid));
        while (list.size() > 200) list.remove(0);
        dirty = true;
    }

    public List<Entry> kills(UUID killer) { return log.getOrDefault(killer, List.of()); }

    /** How many times `killer` killed `victim` in the last `minutes` minutes. */
    public int recentKills(UUID killer, UUID victim, double minutes) {
        long since = System.currentTimeMillis() - (long) (minutes * 60_000);
        int n = 0;
        for (Entry e : kills(killer)) if (e.victim().equals(victim) && e.time() >= since) n++;
        return n;
    }

    public void save() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, List<Entry>> e : log.entrySet()) {
            List<String> lines = new ArrayList<>();
            for (Entry x : e.getValue()) lines.add(x.time() + "|" + x.victim() + "|" + x.victimName() + "|" + x.paid());
            yml.set(e.getKey().toString(), lines);
        }
        try { yml.save(file); } catch (IOException ex) { plugin.getLogger().warning("Couldn't save killlog.yml: " + ex.getMessage()); }
    }

    /** A GUI: one head per victim (how many times, total paid, last time), newest kills listed in the book. */
    public void open(Player admin, OfflinePlayer target, UUID backTo) {
        Holder h = new Holder();
        h.back = backTo;
        Inventory inv = Bukkit.createInventory(h, 54, plugin.mm("<dark_gray>Kill log: <white>" + target.getName()));
        h.inventory = inv;
        List<Entry> kills = kills(target.getUniqueId());
        Map<UUID, int[]> counts = new LinkedHashMap<>();
        Map<UUID, long[]> paid = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (int i = kills.size() - 1; i >= 0; i--) {
            Entry e = kills.get(i);
            counts.computeIfAbsent(e.victim(), k -> new int[1])[0]++;
            long[] p = paid.computeIfAbsent(e.victim(), k -> new long[]{0, e.time()});
            p[0] += e.paid();
            names.putIfAbsent(e.victim(), e.victimName());
        }
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d HH:mm");
        List<UUID> victims = new ArrayList<>(counts.keySet());
        victims.sort((a, b) -> counts.get(b)[0] - counts.get(a)[0]);
        int slot = 0;
        for (UUID v : victims) {
            if (slot >= 45) break;
            int n = counts.get(v)[0];
            int lastHour = recentKills(target.getUniqueId(), v, 60);
            ItemStack head = Icons.button(plugin, Material.PLAYER_HEAD, (n >= 5 || lastHour >= 3 ? "<red><bold>" : "<white>") + names.get(v),
                    "<gray>Killed <white>" + n + "x", "<gray>In the last hour: " + (lastHour >= 3 ? "<red>" : "<white>") + lastHour,
                    "<gray>Cash paid: <yellow>$" + plugin.economy().format(paid.get(v)[0]),
                    "<gray>Last kill: <white>" + fmt.format(new Date(paid.get(v)[1])),
                    lastHour >= 3 ? "<red>⚠ Same person over and over" : "");
            if (head.getItemMeta() instanceof SkullMeta sm) { sm.setOwningPlayer(Bukkit.getOfflinePlayer(v)); head.setItemMeta(sm); }
            inv.setItem(slot++, head);
        }
        List<String> recent = new ArrayList<>();
        recent.add("<gray>Total kills logged: <white>" + kills.size());
        recent.add("");
        for (int i = kills.size() - 1; i >= Math.max(0, kills.size() - 15); i--) {
            Entry e = kills.get(i);
            recent.add("<dark_gray>" + fmt.format(new Date(e.time())) + " <white>" + e.victimName() + " <yellow>+$" + plugin.economy().format(e.paid()));
        }
        inv.setItem(49, Icons.button(plugin, Material.WRITABLE_BOOK, "<gold><bold>Latest kills", recent.toArray(new String[0])));
        if (backTo != null) inv.setItem(45, Icons.gui(plugin, "back", "<yellow>Back"));
        if (kills.isEmpty()) inv.setItem(22, Icons.button(plugin, Material.BARRIER, "<gray>No kills logged yet"));
        admin.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 45 && h.back != null && event.getWhoClicked() instanceof Player p) plugin.adminHub().openPlayer(p, h.back);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!BanPistol.isAdmin(sender) || !(sender instanceof Player p)) return true;
        if (args.length < 1) { p.sendMessage(plugin.mm(plugin.prefix() + "<red>/killlog <player>")); return true; }
        OfflinePlayer t = Bukkit.getOfflinePlayer(args[0]);
        open(p, t, null);
        return true;
    }
}
