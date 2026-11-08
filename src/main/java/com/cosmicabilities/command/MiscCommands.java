package com.cosmicabilities.command;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.admin.RankTags;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /ability, /abilities, /abilityholders, /keybinds, /bossfight, /adminpanel, /tag, /unban, /announce, /afk, /afkshop */
public class MiscCommands implements CommandExecutor, TabCompleter {
    private final CosmicAbilitiesPlugin plugin;

    public MiscCommands(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender to, String mini) {
        to.sendMessage(plugin.mm(plugin.prefix() + mini));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "unban" -> { return unban(sender, args); }
            case "announce" -> { return announce(sender, args); }
            case "creload" -> {
                if (sender instanceof Player p && !BanPistol.isStaff(p)) { send(sender, "<red>Only admins can reload."); return true; }
                plugin.reloadAll();
                send(sender, "<green>Cosmic config, AFK shop and recipes reloaded - changes are live.");
                return true;
            }
            default -> { }
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.mm("<red>Players only."));
            return true;
        }
        switch (name) {
            case "ability" -> ability(player, args);
            case "abilities" -> plugin.abilitiesPanel().open(player);
            case "abilityholders" -> plugin.holdersPanel().open(player, 0);
            case "keybinds" -> plugin.keybindPanel().open(player);
            case "trust" -> trust(player, args, true);
            case "untrust" -> trust(player, args, false);
            case "cconfig" -> {
                if (!BanPistol.isStaff(player)) send(player, "<red>Only admins can edit the config.");
                else plugin.configPanel().open(player);
            }
            case "bossfight" -> {
                if (!BanPistol.isStaff(player)) send(player, "<red>Only admins can start boss fights.");
                else plugin.bossFightPanel().open(player);
            }
            case "adminpanel" -> {
                if (!BanPistol.isStaff(player)) send(player, "<red>Only admins can open the admin panel.");
                else plugin.adminPanel().open(player);
            }
            case "tag" -> tag(player, args);
            case "afk" -> plugin.afk().toggle(player);
            case "afkshop" -> afkshop(player, args);
            default -> { }
        }
        return true;
    }

    private void ability(Player player, String[] args) {
        if (args.length == 0) {
            PlayerData d = plugin.data().get(player.getUniqueId());
            send(player, "<gray>Use <yellow>/ability 1 <gray>or <yellow>/ability 2<gray>. See <yellow>/abilities <gray>for what they do.");
            if (d != null) {
                for (int i = 1; i <= PlayerData.SLOTS; i++) {
                    AbilitySlot s = d.slot(i);
                    player.sendMessage(plugin.mm("<gray>Slot " + i + ": " + (s.isEmpty() ? "<dark_gray>empty"
                            : s.ability().colored() + " <gray>(" + com.cosmicabilities.ability.AbilityType.tierName(s.tier()) + ")")));
                }
            }
            return;
        }
        int slot = args[0].trim().equals("2") ? 2 : args[0].trim().equals("1") ? 1 : -1;
        if (slot < 0) { send(player, "<red>Use /ability 1 or /ability 2."); return; }
        plugin.engine().tryCast(player, slot);
    }

    /** /announce <text> or /announce <title> | <subtitle>. Shows on every player's screen. */
    private boolean announce(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !BanPistol.isStaff(p)) { send(sender, "<red>Only admins can announce."); return true; }
        if (args.length == 0) {
            send(sender, "<gray>Usage: <yellow>/announce <message> <gray>or <yellow>/announce <title> | <subtitle>");
            return true;
        }
        String text = String.join(" ", args);
        Component title, subtitle;
        if (text.contains("|")) {
            String[] parts = text.split("\\|", 2);
            title = plugin.mm("<gold><bold>" + parts[0].trim());
            subtitle = plugin.mm("<white>" + parts[1].trim());
        } else if (text.length() <= 24) {
            title = plugin.mm("<gold><bold>" + text);
            subtitle = Component.empty();
        } else {
            title = plugin.mm("<gold><bold>" + plugin.getConfig().getString("announce.header", "ANNOUNCEMENT"));
            subtitle = plugin.mm("<white>" + text);
        }
        int seconds = plugin.getConfig().getInt("announce.seconds", 5);
        Title shown = Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(seconds), Duration.ofMillis(800)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(shown);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
        }
        Bukkit.broadcast(plugin.mm("<gold><bold>[Announcement]</bold> <white>" + text.replace("|", "-")));
        return true;
    }

    public boolean unban(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !BanPistol.isStaff(p)) { send(sender, "<red>Only admins can unban players."); return true; }
        if (args.length < 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            send(sender, "<gray>Usage: <yellow>/unban <player>");
            return true;
        }
        String name = args[0];
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:pardon " + name);
        send(sender, "<green>" + name + " has been unbanned.");
        plugin.getLogger().info(sender.getName() + " unbanned " + name);
        return true;
    }

    private void tag(Player player, String[] args) {
        String which = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (which) {
            case "admin", "owner" -> {
                RankTags.Rank rank = which.equals("owner") ? RankTags.Rank.OWNER : RankTags.Rank.ADMIN;
                if (plugin.tags().apply(player, rank)) {
                    send(player, "<gray>You are now shown as " + (rank == RankTags.Rank.OWNER ? "<gold><bold>[OWNER]" : "<red><bold>[ADMIN]")
                            + "</bold> <white>" + player.getName() + "<gray>.");
                } else {
                    send(player, "<red>You need " + (rank == RankTags.Rank.OWNER ? "OP" : "admin or OP") + " to wear that tag.");
                }
            }
            case "off", "remove", "none" -> {
                plugin.tags().clear(player);
                send(player, "<gray>Tag removed.");
            }
            default -> send(player, "<gray>Usage: <yellow>/tag <admin|owner|off>");
        }
    }

    /** /trust <player> | /untrust <player> | /trust list - your abilities never affect players you trust. */
    @SuppressWarnings("deprecation")
    private void trust(Player player, String[] args, boolean add) {
        PlayerData d = plugin.data().get(player.getUniqueId());
        if (d == null) return;
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            if (d.trusted().isEmpty()) {
                send(player, "<gray>You don't trust anyone yet. <yellow>/trust <player>");
                return;
            }
            List<String> names = new ArrayList<>();
            for (java.util.UUID id : d.trusted()) {
                String n = Bukkit.getOfflinePlayer(id).getName();
                names.add(n == null ? id.toString().substring(0, 8) : n);
            }
            send(player, "<gray>Trusted (your abilities won't affect them): <white>" + String.join(", ", names));
            return;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) { send(player, "<red>You can't trust yourself."); return; }
        if (!target.hasPlayedBefore() && target.getPlayer() == null) { send(player, "<red>Unknown player " + args[0] + "."); return; }
        String name = target.getName() == null ? args[0] : target.getName();
        if (add) {
            if (d.trust(target.getUniqueId())) send(player, "<green>You now trust <white>" + name + "<green>. Your abilities won't affect them.");
            else send(player, "<gray>You already trust " + name + ".");
            if (target.getPlayer() != null) send(target.getPlayer(), "<white>" + player.getName() + " <gray>now trusts you - their abilities won't hit you.");
        } else {
            if (d.untrust(target.getUniqueId())) send(player, "<yellow>You no longer trust <white>" + name + "<yellow>.");
            else send(player, "<gray>You weren't trusting " + name + ".");
        }
        plugin.data().save(d);
    }

    private void afkshop(Player player, String[] args) {
        if (args.length == 0) { plugin.afkShop().open(player); return; }
        if (!BanPistol.isStaff(player)) { plugin.afkShop().open(player); return; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 2) { send(player, "<gray>Hold an item, then <yellow>/afkshop add <price> [slot 0-44]"); return; }
                int price = parse(args[1], -1);
                if (price < 0) { send(player, "<red>Price must be a number."); return; }
                int slot = args.length > 2 ? parse(args[2], -1) : -1;
                String id = plugin.afkShop().add(player, price, slot);
                if (id == null) send(player, "<red>Hold the item you want to sell.");
                else send(player, "<green>Added <white>" + id + " <green>to the AFK shop for <white>" + price + " tokens<green>.");
            }
            case "reload" -> {
                plugin.afkShop().reload();
                send(player, "<green>AFK shop reloaded.");
            }
            default -> send(player, "<gray>/afkshop <dark_gray>| <gray>/afkshop add <price> [slot] <dark_gray>| <gray>/afkshop reload "
                    + "<dark_gray>| <gray>shift-right-click an item in the shop to remove it");
        }
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1 || !(sender instanceof Player p)) return out;
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "ability" -> out.addAll(List.of("1", "2"));
            case "trust" -> {
                out.add("list");
                for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) out.add(o.getName());
            }
            case "untrust" -> {
                PlayerData d = plugin.data().get(p.getUniqueId());
                if (d != null) for (java.util.UUID id : d.trusted()) {
                    String n = Bukkit.getOfflinePlayer(id).getName();
                    if (n != null) out.add(n);
                }
            }
            case "tag" -> {
                if (RankTags.canUse(p, RankTags.Rank.ADMIN)) out.add("admin");
                if (RankTags.canUse(p, RankTags.Rank.OWNER)) out.add("owner");
                out.add("off");
            }
            case "afkshop" -> { if (BanPistol.isStaff(p)) out.addAll(List.of("add", "reload")); }
            default -> { }
        }
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)));
        return out;
    }
}
