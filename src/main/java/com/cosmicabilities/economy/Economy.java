package com.cosmicabilities.economy;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.data.PlayerData;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Cosmic cash. Everyone starts with economy.starting-cash (10,000) and gets economy.daily-reward (20,000) the first
 * time they log in each day. /balance, /pay, and for admins /eco give|take|set.
 */
public class Economy implements Listener, CommandExecutor, TabCompleter {
    private final CosmicAbilitiesPlugin plugin;

    public Economy(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public String format(long amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    private PlayerData data(UUID id) {
        PlayerData d = plugin.data().get(id);
        if (d == null) d = plugin.data().loadOffline(id);
        if (d.cash() < 0) d.setCash(plugin.getConfig().getLong("economy.starting-cash", 10000));
        return d;
    }

    public long balance(UUID id) {
        return data(id).cash();
    }

    public void add(UUID id, long amount) {
        PlayerData d = data(id);
        d.setCash(Math.max(0, d.cash() + amount));
        if (plugin.data().get(id) == null) plugin.data().save(d);
    }

    /** Takes money if they have enough. */
    public boolean take(UUID id, long amount) {
        PlayerData d = data(id);
        if (d.cash() < amount) return false;
        d.setCash(d.cash() - amount);
        if (plugin.data().get(id) == null) plugin.data().save(d);
        return true;
    }

    private LocalDate today() {
        String zone = plugin.getConfig().getString("economy.timezone", "America/Denver");
        try {
            return LocalDate.now(ZoneId.of(zone));
        } catch (RuntimeException e) {
            return LocalDate.now();
        }
    }

    /** Starting cash on the first ever join, and the daily login reward once per day. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        boolean first = d.cash() < 0;
        if (first) d.setCash(plugin.getConfig().getLong("economy.starting-cash", 10000));
        String day = today().toString();
        long reward = plugin.getConfig().getLong("economy.daily-reward", 20000);
        boolean daily = reward > 0 && !day.equals(d.lastDaily());
        if (daily) {
            d.setLastDaily(day);
            d.setCash(d.cash() + reward);
        }
        plugin.data().save(d);
        if (!first && !daily) return;
        long start = plugin.getConfig().getLong("economy.starting-cash", 10000);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (first) msg(p, "<gold>Welcome! <gray>You start with <yellow>$" + format(start) + "<gray>.");
            if (daily) {
                msg(p, "<gold>Daily login reward: <yellow>+$" + format(reward) + " <gray>(balance <yellow>$" + format(d.cash()) + "<gray>)");
                p.showTitle(Title.title(plugin.mm("<gold><bold>+$" + format(reward)), plugin.mm("<gray>daily login reward"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500))));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
            }
        }, 100L);
    }

    private void msg(CommandSender to, String mini) {
        to.sendMessage(plugin.mm(plugin.prefix() + mini));
    }

    /** Parses 5000, 5k, 2.5m, 1,000 */
    public static long parse(String s) {
        try {
            String t = s.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "");
            double mult = 1;
            if (t.endsWith("k")) { mult = 1_000; t = t.substring(0, t.length() - 1); }
            else if (t.endsWith("m")) { mult = 1_000_000; t = t.substring(0, t.length() - 1); }
            else if (t.endsWith("b")) { mult = 1_000_000_000; t = t.substring(0, t.length() - 1); }
            double v = Double.parseDouble(t) * mult;
            return v < 0 || Double.isNaN(v) || v > 9.0E15 ? -1 : (long) Math.floor(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "balance" -> {
                if (args.length > 0) {
                    OfflinePlayer t = Bukkit.getOfflinePlayer(args[0]);
                    if (!t.hasPlayedBefore() && !t.isOnline()) { msg(sender, "<red>Unknown player."); return true; }
                    msg(sender, "<white>" + t.getName() + " <gray>has <yellow>$" + format(balance(t.getUniqueId())));
                } else if (sender instanceof Player p) {
                    msg(p, "<gray>Your cash: <yellow>$" + format(balance(p.getUniqueId())));
                }
            }
            case "pay" -> {
                if (!(sender instanceof Player p)) return true;
                if (args.length < 2) { msg(p, "<gray>Usage: <yellow>/pay <player> <amount>"); return true; }
                Player to = Bukkit.getPlayerExact(args[0]);
                long amount = parse(args[1]);
                if (to == null) { msg(p, "<red>That player isn't online."); return true; }
                if (to.equals(p)) { msg(p, "<red>You can't pay yourself."); return true; }
                if (amount <= 0) { msg(p, "<red>Enter an amount like 5000 or 5k."); return true; }
                if (!take(p.getUniqueId(), amount)) { msg(p, "<red>You only have $" + format(balance(p.getUniqueId())) + "."); return true; }
                add(to.getUniqueId(), amount);
                msg(p, "<gray>Sent <yellow>$" + format(amount) + " <gray>to <white>" + to.getName());
                msg(to, "<white>" + p.getName() + " <gray>sent you <yellow>$" + format(amount));
                to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            }
            case "eco" -> {
                if (!BanPistol.isAdmin(sender)) return true;
                if (args.length < 3) { msg(sender, "<gray>Usage: <yellow>/eco <give|take|set> <player> <amount>"); return true; }
                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                long amount = parse(args[2]);
                if (amount < 0 || (!t.hasPlayedBefore() && !t.isOnline())) { msg(sender, "<red>Check the player and amount."); return true; }
                UUID id = t.getUniqueId();
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "give" -> add(id, amount);
                    case "take" -> add(id, -amount);
                    case "set" -> { PlayerData d = data(id); d.setCash(amount); if (plugin.data().get(id) == null) plugin.data().save(d); }
                    default -> { msg(sender, "<red>give, take or set"); return true; }
                }
                msg(sender, "<white>" + t.getName() + " <gray>now has <yellow>$" + format(balance(id)));
            }
            default -> { }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("eco") && !BanPistol.isAdmin(sender)) return out;
        if (name.equals("eco") && args.length == 1) out.addAll(List.of("give", "take", "set"));
        else if ((name.equals("pay") || name.equals("balance")) && args.length == 1
                || name.equals("eco") && args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (name.equals("pay") && args.length == 2) out.addAll(List.of("1000", "5000", "10k"));
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
