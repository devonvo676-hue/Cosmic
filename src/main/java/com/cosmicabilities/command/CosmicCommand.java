package com.cosmicabilities.command;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.boss.BossType;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import com.cosmicabilities.item.CosmicMaterial;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class CosmicCommand implements CommandExecutor, TabCompleter {
    private final CosmicAbilitiesPlugin plugin;

    public CosmicCommand(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender to, String mini) {
        to.sendMessage(plugin.mm(plugin.prefix() + mini));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "cast" -> {
                if (sender instanceof Player p) plugin.engine().tryCast(p, args.length > 1 ? slotArg(args[1]) : 1);
                else send(sender, "<red>Players only.");
            }
            case "upgrade" -> upgrade(sender, args.length > 1 ? slotArg(args[1]) : 1);
            case "path" -> path(sender, args);
            case "abilities", "list" -> list(sender);
            case "help" -> help(sender);
            case "admin" -> {
                if (!sender.hasPermission("cosmicabilities.admin")) {
                    send(sender, "<red>You don't have permission.");
                    return true;
                }
                admin(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(plugin.mm("<gradient:#9b5de5:#00bbf9><bold>Cosmic Abilities</bold></gradient>"));
        s.sendMessage(plugin.mm("<yellow>/ability 1 <gray>or <yellow>Sneak + F <gray>- cast slot 1"));
        s.sendMessage(plugin.mm("<yellow>/ability 2 <gray>or <yellow>Sneak + left-click (empty hand) <gray>- cast slot 2"));
        s.sendMessage(plugin.mm("<yellow>/cosmic <gray>- your ability, level and upgrade cost"));
        s.sendMessage(plugin.mm("<yellow>/cosmic upgrade [1|2] <gray>- spend boss materials to level up"));
        s.sendMessage(plugin.mm("<yellow>/cosmic path <a|b> [1|2] <gray>- choose your specialization (level 5)"));
        s.sendMessage(plugin.mm("<yellow>/cosmic abilities <gray>- all 10 cosmic abilities"));
        if (s.hasPermission("cosmicabilities.admin")) {
            s.sendMessage(plugin.mm("<red>/adminpanel <gray>- admin panel   <red>/tag <admin|owner|off>"));
            s.sendMessage(plugin.mm("<red>/cosmic admin <gray>set <player> <ability> [slot] | reroll | setlevel <player> <lvl> [slot] | setpath | mastery | give | boss | resetcd | reload"));
        }
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) { help(sender); return; }
        PlayerData d = plugin.data().get(player.getUniqueId());
        if (d == null || !d.hasAnyAbility()) { send(player, "<red>You have no cosmic ability yet."); return; }
        player.sendMessage(plugin.mm("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(plugin.mm("<gray>Cosmic Mastery: <aqua>" + d.mastery()));
        for (int i = 1; i <= PlayerData.SLOTS; i++) {
            AbilitySlot s = d.slot(i);
            if (s.isEmpty()) continue;
            slotInfo(player, d, i, s);
        }
        player.sendMessage(plugin.mm("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void slotInfo(Player player, PlayerData d, int number, AbilitySlot s) {
        AbilityType t = s.ability();
        player.sendMessage(plugin.mm("<white>[" + number + "] " + t.colored(s.level()) + " <dark_gray>• <gray>" + t.role
                + " <dark_gray>• <gray>Lv <white>" + s.level() + "<gray>/10 <dark_gray>• <gray>CD <white>"
                + String.format("%.1f", plugin.engine().cooldownSeconds(t, s.level())) + "s"));
        player.sendMessage(plugin.mm("  <gray>" + t.description + " <dark_gray>Weakness: " + t.weakness));
        player.sendMessage(plugin.mm("  <gray>Passive: <green>" + plugin.engine().passiveName(t)));
        if (s.path() != 0) {
            player.sendMessage(plugin.mm("  <gray>Specialization: <gold>" + t.pathName(s.path())));
        } else if (s.level() >= 5) {
            player.sendMessage(plugin.mm("  <gold>Choose: <yellow>/cosmic path a " + number + " <gray>(" + t.pathAName
                    + ") <dark_gray>or <yellow>/cosmic path b " + number + " <gray>(" + t.pathBName + ")"));
        }
        if (s.level() < 10) {
            int next = s.level() + 1;
            int mats = materialCost(next);
            int need = masteryRequired(next);
            int have = t.material.count(player);
            String line = "  <gray>Next: " + col(have >= mats) + have + "/" + mats + " " + t.material.colored()
                    + " <gray>• " + col(d.mastery() >= need) + d.mastery() + "/" + need + " Mastery";
            if (next == 10) {
                int astral = astralCost();
                int haveAstral = CosmicMaterial.ASTRAL_FRAGMENT.count(player);
                line += " <gray>• " + col(haveAstral >= astral) + haveAstral + "/" + astral + " " + CosmicMaterial.ASTRAL_FRAGMENT.colored();
            }
            player.sendMessage(plugin.mm(line));
            if (next == 10) player.sendMessage(plugin.mm("  <light_purple>Evolves into <" + t.color + "><bold>"
                    + t.evolutionName + "</bold> <gray>- " + t.evolutionDesc));
        } else {
            player.sendMessage(plugin.mm("  <light_purple><bold>EVOLVED</bold> <gray>- " + t.evolutionDesc));
        }
    }

    private static int slotArg(String s) {
        return s.trim().equals("2") ? 2 : 1;
    }

    private static String col(boolean ok) {
        return ok ? "<green>" : "<red>";
    }

    private int materialCost(int level) {
        return plugin.getConfig().getInt("upgrades.material-cost." + level, level * 3);
    }

    private int masteryRequired(int level) {
        return plugin.getConfig().getInt("upgrades.mastery-required." + level, (level - 2) * 150);
    }

    private int astralCost() {
        return plugin.getConfig().getInt("upgrades.evolution-astral-cost", 5);
    }

    private void upgrade(CommandSender sender, int slotNumber) {
        if (!(sender instanceof Player player)) { send(sender, "<red>Players only."); return; }
        PlayerData pd = plugin.data().get(player.getUniqueId());
        if (pd == null) return;
        AbilitySlot d = pd.slot(slotNumber);
        if (d.isEmpty()) { send(player, "<red>Slot " + slotNumber + " is empty."); return; }
        AbilityType t = d.ability();
        if (d.level() >= 10) { send(player, "<light_purple>Your ability has already reached its Cosmic Evolution."); return; }
        if (d.level() >= 5 && d.path() == 0) {
            send(player, "<gold>Choose a specialization first: <yellow>/cosmic path a " + slotNumber + " <gray>(" + t.pathAName
                    + ") <dark_gray>or <yellow>/cosmic path b " + slotNumber + " <gray>(" + t.pathBName + ")");
            return;
        }
        int next = d.level() + 1;
        int mats = materialCost(next);
        int need = masteryRequired(next);
        int astral = next == 10 ? astralCost() : 0;
        // When the ability's own material IS Astral Fragments, the evolution cost stacks.
        int needOwn = mats + (t.material == CosmicMaterial.ASTRAL_FRAGMENT ? astral : 0);

        if (pd.mastery() < need) {
            send(player, "<red>You need <aqua>" + need + " Mastery <red>(you have " + pd.mastery() + "). Defeat cosmic bosses to earn more.");
            return;
        }
        if (t.material.count(player) < needOwn) {
            send(player, "<red>You need <white>" + needOwn + "x " + t.material.colored() + "<red>. (" + t.material.source + ")");
            return;
        }
        if (astral > 0 && t.material != CosmicMaterial.ASTRAL_FRAGMENT && CosmicMaterial.ASTRAL_FRAGMENT.count(player) < astral) {
            send(player, "<red>Cosmic Evolution also requires <white>" + astral + "x " + CosmicMaterial.ASTRAL_FRAGMENT.colored() + "<red>.");
            return;
        }
        t.material.take(player, needOwn);
        if (astral > 0 && t.material != CosmicMaterial.ASTRAL_FRAGMENT) CosmicMaterial.ASTRAL_FRAGMENT.take(player, astral);
        d.setLevel(next);
        plugin.data().save(pd);

        if (next == 10) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.5f);
            Bukkit.broadcast(plugin.mm(plugin.prefix() + "<white>" + player.getName() + " <gray>has achieved Cosmic Evolution: "
                    + "<" + t.color + "><bold>" + t.evolutionName + "</bold>!"));
            send(player, "<gray>" + t.evolutionDesc);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            send(player, t.colored(next) + " <gray>is now <white>level " + next + "<gray>!");
            if (next == 5) {
                send(player, "<gold>Specialization unlocked! <yellow>/cosmic path a " + slotNumber + " <gray>(" + t.pathAName + ": " + t.pathADesc
                        + ") <dark_gray>or <yellow>/cosmic path b " + slotNumber + " <gray>(" + t.pathBName + ": " + t.pathBDesc + ")");
            }
        }
    }

    private void path(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { send(sender, "<red>Players only."); return; }
        PlayerData pd = plugin.data().get(player.getUniqueId());
        if (pd == null) return;
        AbilitySlot d = pd.slot(args.length > 2 ? slotArg(args[2]) : 1);
        if (d.isEmpty()) { send(player, "<red>That slot is empty."); return; }
        AbilityType t = d.ability();
        if (args.length < 2) {
            send(player, "<gray>Path A - <gold>" + t.pathAName + "<gray>: " + t.pathADesc);
            send(player, "<gray>Path B - <gold>" + t.pathBName + "<gray>: " + t.pathBDesc);
            return;
        }
        if (d.level() < 5) { send(player, "<red>Specializations unlock at level 5."); return; }
        if (d.path() != 0) { send(player, "<red>You already chose <gold>" + t.pathName(d.path()) + "<red>. This choice is permanent."); return; }
        char choice = Character.toUpperCase(args[1].charAt(0));
        if (choice != 'A' && choice != 'B') { send(player, "<red>Use /cosmic path a or /cosmic path b"); return; }
        d.setPath(choice);
        plugin.data().save(pd);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
        send(player, "<gray>You specialized in <gold><bold>" + t.pathName(choice) + "</bold><gray>.");
    }

    private void list(CommandSender s) {
        s.sendMessage(plugin.mm("<gradient:#9b5de5:#00bbf9><bold>The 10 Cosmic Abilities</bold></gradient>"));
        for (AbilityType t : AbilityType.values()) {
            s.sendMessage(plugin.mm(t.colored(1) + " <dark_gray>(" + t.role + ") <gray>- " + t.description
                    + " <dark_gray>Upgrades with " + t.material.displayName + "."));
        }
    }

    // ---------------------------------------------------------------- admin

    private void admin(CommandSender s, String[] a) {
        if (a.length == 0) { help(s); return; }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadConfig();
                send(s, "<green>Config reloaded.");
            }
            case "boss" -> {
                if (!(s instanceof Player p)) { send(s, "<red>Run in-game (spawns at your location)."); return; }
                BossType type = a.length > 1 ? BossType.fromId(a[1]) : null;
                if (type == null) { send(s, "<red>Bosses: " + String.join(", ", ids(BossType.values()))); return; }
                plugin.bosses().spawn(type, p.getLocation());
            }
            case "set", "reroll", "setlevel", "setpath", "mastery", "give", "resetcd" -> {
                if (a.length < 2) { send(s, "<red>Usage: /cosmic admin " + sub + " <player> ..."); return; }
                adminPlayer(s, sub, a);
            }
            default -> help(s);
        }
    }

    @SuppressWarnings("deprecation")
    private void adminPlayer(CommandSender s, String sub, String[] a) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
        Player online = target.getPlayer();
        if (!target.hasPlayedBefore() && online == null) { send(s, "<red>Unknown player " + a[1]); return; }
        PlayerData pd = plugin.data().loadOffline(target.getUniqueId());
        String name = target.getName() == null ? a[1] : target.getName();

        switch (sub) {
            case "set" -> {
                AbilityType t = a.length > 2 ? AbilityType.fromId(a[2]) : null;
                if (t == null) { send(s, "<red>Abilities: " + String.join(", ", ids(AbilityType.values()))); return; }
                int slot = a.length > 3 ? slotArg(a[3]) : 1;
                AbilitySlot d = pd.slot(slot);
                int keep = d.level();
                d.equip(t, keep);
                send(s, "<green>Set " + name + "'s slot " + slot + " to " + t.displayName + " (level kept, path reset).");
                if (online != null) send(online, "<gray>Your slot " + slot + " ability is now " + t.colored(d.level()));
            }
            case "reroll" -> {
                AbilityType t = AbilityType.values()[ThreadLocalRandom.current().nextInt(AbilityType.values().length)];
                pd.primary().equip(t, 1);
                send(s, "<green>Rerolled " + name + " into " + t.displayName + " (reset to level 1).");
                if (online != null) plugin.playerListener().awaken(online, t);
            }
            case "setlevel" -> {
                int lvl = a.length > 2 ? parse(a[2], 1) : 1;
                AbilitySlot d = pd.slot(a.length > 3 ? slotArg(a[3]) : 1);
                d.setLevel(lvl);
                if (d.level() < 5) d.setPath((char) 0);
                send(s, "<green>" + name + "'s ability is now level " + d.level() + ".");
            }
            case "setpath" -> {
                char c = a.length > 2 ? Character.toUpperCase(a[2].charAt(0)) : 0;
                AbilitySlot d = pd.slot(a.length > 3 ? slotArg(a[3]) : 1);
                d.setPath(c);
                send(s, "<green>" + name + "'s path: " + (d.path() == 0 ? "none" : String.valueOf(d.path())));
            }
            case "mastery" -> {
                int amt = a.length > 2 ? parse(a[2], 0) : 0;
                pd.addMastery(amt);
                send(s, "<green>" + name + " now has " + pd.mastery() + " mastery.");
            }
            case "give" -> {
                CosmicMaterial m = a.length > 2 ? CosmicMaterial.fromId(a[2]) : null;
                if (m == null || online == null) { send(s, "<red>Usage: /cosmic admin give <online player> <" + String.join("|", ids(CosmicMaterial.values())) + "> [amount]"); return; }
                int amt = a.length > 3 ? parse(a[3], 1) : 1;
                m.give(online, amt);
                send(s, "<green>Gave " + amt + "x " + m.displayName + " to " + name + ".");
            }
            case "resetcd" -> {
                plugin.engine().resetCooldown(target.getUniqueId());
                send(s, "<green>Cooldown reset for " + name + ".");
            }
            default -> { }
        }
        plugin.data().save(pd);
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static List<String> ids(Enum<?>[] values) {
        List<String> out = new ArrayList<>();
        for (Enum<?> e : values) out.add(e.name().toLowerCase(Locale.ROOT));
        return out;
    }

    // ---------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission("cosmicabilities.admin");
        if (args.length == 1) {
            options.addAll(List.of("info", "upgrade", "path", "abilities", "cast", "help"));
            if (admin) options.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("path")) {
            options.addAll(List.of("a", "b"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("upgrade") || args[0].equalsIgnoreCase("cast"))) {
            options.addAll(List.of("1", "2"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("path")) {
            options.addAll(List.of("1", "2"));
        } else if (admin && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) {
                options.addAll(List.of("set", "reroll", "setlevel", "setpath", "mastery", "give", "boss", "resetcd", "reload"));
            } else if (args.length == 3) {
                if (args[1].equalsIgnoreCase("boss")) options.addAll(ids(BossType.values()));
                else for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            } else if (args.length == 4) {
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "set" -> options.addAll(ids(AbilityType.values()));
                    case "give" -> options.addAll(ids(CosmicMaterial.values()));
                    case "setpath" -> options.addAll(List.of("a", "b", "none"));
                    case "setlevel" -> { for (int i = 1; i <= 10; i++) options.add(String.valueOf(i)); }
                    default -> { }
                }
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return options;
    }
}
