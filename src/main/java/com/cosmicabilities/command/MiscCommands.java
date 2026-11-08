package com.cosmicabilities.command;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.admin.RankTags;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /ability &lt;1|2&gt;, /adminpanel, /tag &lt;admin|owner|off&gt; */
public class MiscCommands implements CommandExecutor, TabCompleter {
    private final CosmicAbilitiesPlugin plugin;

    public MiscCommands(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("unban")) return unban(sender, args);
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.mm("<red>Players only."));
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "ability" -> ability(player, args);
            case "adminpanel" -> {
                if (!BanPistol.isStaff(player)) {
                    player.sendMessage(plugin.mm(plugin.prefix() + "<red>Only admins can open the admin panel."));
                } else {
                    plugin.adminPanel().open(player);
                }
            }
            case "tag" -> tag(player, args);
            default -> { }
        }
        return true;
    }

    /** /unban works from console too. */
    public boolean unban(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !BanPistol.isStaff(p)) {
            sender.sendMessage(plugin.mm(plugin.prefix() + "<red>Only admins can unban players."));
            return true;
        }
        if (args.length < 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
            sender.sendMessage(plugin.mm(plugin.prefix() + "<gray>Usage: <yellow>/unban <player>"));
            return true;
        }
        String name = args[0];
        // Uses the vanilla pardon so it clears any ban (Ban Pistol or otherwise).
        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "minecraft:pardon " + name);
        sender.sendMessage(plugin.mm(plugin.prefix() + "<green>" + name + " has been unbanned."));
        plugin.getLogger().info(sender.getName() + " unbanned " + name);
        return true;
    }

    private void ability(Player player, String[] args) {
        if (args.length == 0) {
            PlayerData d = plugin.data().get(player.getUniqueId());
            player.sendMessage(plugin.mm(plugin.prefix() + "<gray>Use <yellow>/ability 1 <gray>or <yellow>/ability 2<gray>."));
            if (d != null) {
                for (int i = 1; i <= PlayerData.SLOTS; i++) {
                    AbilitySlot s = d.slot(i);
                    player.sendMessage(plugin.mm("<gray>Slot " + i + ": "
                            + (s.isEmpty() ? "<dark_gray>empty" : s.ability().colored(s.level()) + " <gray>Lv " + s.level())));
                }
            }
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            slot = -1;
        }
        if (slot < 1 || slot > PlayerData.SLOTS) {
            player.sendMessage(plugin.mm(plugin.prefix() + "<red>Use /ability 1 or /ability 2."));
            return;
        }
        plugin.engine().tryCast(player, slot);
    }

    private void tag(Player player, String[] args) {
        String which = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (which) {
            case "admin", "owner" -> {
                RankTags.Rank rank = which.equals("owner") ? RankTags.Rank.OWNER : RankTags.Rank.ADMIN;
                if (plugin.tags().apply(player, rank)) {
                    player.sendMessage(plugin.mm(plugin.prefix() + "<gray>You are now wearing the "
                            + (rank == RankTags.Rank.OWNER ? "<gold><bold>OWNER" : "<red><bold>ADMIN") + "</bold> <gray>tag."));
                } else {
                    player.sendMessage(plugin.mm(plugin.prefix() + "<red>You need "
                            + (rank == RankTags.Rank.OWNER ? "OP" : "admin or OP") + " to wear that tag."));
                }
            }
            case "off", "remove", "none" -> {
                plugin.tags().clear(player);
                player.sendMessage(plugin.mm(plugin.prefix() + "<gray>Name tag removed."));
            }
            default -> player.sendMessage(plugin.mm(plugin.prefix() + "<gray>Usage: <yellow>/tag <admin|owner|off>"));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1 || !(sender instanceof Player p)) return out;
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "ability" -> out.addAll(List.of("1", "2"));
            case "tag" -> {
                if (RankTags.canUse(p, RankTags.Rank.ADMIN)) out.add("admin");
                if (RankTags.canUse(p, RankTags.Rank.OWNER)) out.add("owner");
                out.add("off");
            }
            default -> { }
        }
        out.removeIf(o -> !o.startsWith(args[0].toLowerCase(Locale.ROOT)));
        return out;
    }
}
