package com.cosmicabilities.command;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.boss.BossType;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import com.cosmicabilities.item.CosmicMaterial;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
            case "upgrade" -> upgrade(sender, args.length > 1 ? slotArg(args[1]) : 1);
            case "abilities", "list" -> {
                if (sender instanceof Player p) plugin.abilitiesPanel().open(p);
            }
            case "help" -> help(sender);
            case "admin" -> {
                if (!sender.hasPermission("cosmicabilities.admin")) { send(sender, "<red>You don't have permission."); return true; }
                admin(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(plugin.mm("<gradient:#9b5de5:#00bbf9><bold>Cosmic Abilities</bold></gradient>"));
        s.sendMessage(plugin.mm("<yellow>/ability 1 <gray>/ <yellow>/ability 2 <gray>- cast (or your keybind, see <yellow>/keybinds<gray>)"));
        s.sendMessage(plugin.mm("<yellow>/abilities <gray>- what every ability does"));
        s.sendMessage(plugin.mm("<yellow>/abilityholders <gray>- who has which ability"));
        s.sendMessage(plugin.mm("<yellow>/cosmic <gray>- your abilities   <yellow>/cosmic upgrade [1|2] <gray>- shorter cooldown"));
        s.sendMessage(plugin.mm("<yellow>/afk <gray>- earn AFK tokens   <yellow>/afkshop <gray>- spend them"));
        s.sendMessage(plugin.mm("<gray>Slot 1 is the ability you joined with (never lost). Slot 2 is a gained ability:"));
        s.sendMessage(plugin.mm("<gray>bosses drop them, and you drop yours if someone kills you. A <light_purple>Cosmic Reroll <gray>gets it back."));
        if (s.hasPermission("cosmicabilities.admin")) {
            s.sendMessage(plugin.mm("<red>/bossfight  /adminpanel  /announce <msg>  /tag <admin|owner|off>  /unban <player>"));
            s.sendMessage(plugin.mm("<red>/cosmic admin <gray>set | reroll | settier | give | giveorb | givereroll | tokens | boss | resetcd | reload"));
        }
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) { help(sender); return; }
        PlayerData d = plugin.data().get(player.getUniqueId());
        if (d == null || !d.hasAnyAbility()) { send(player, "<red>You have no cosmic ability yet."); return; }
        player.sendMessage(plugin.mm("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        for (int i = 1; i <= PlayerData.SLOTS; i++) {
            AbilitySlot s = d.slot(i);
            String label = i == 1 ? "Joined with" : "Gained";
            if (s.isEmpty()) {
                player.sendMessage(plugin.mm("<white>[" + i + "] <gray>" + label + ": <dark_gray>none <gray>(beat a boss or win a fight)"));
                continue;
            }
            AbilityType t = s.ability();
            player.sendMessage(plugin.mm("<white>[" + i + "] <gray>" + label + ": " + t.colored()
                    + (s.upgraded() ? " <aqua>(Upgraded)" : "") + " <dark_gray>| <gray>CD <white>"
                    + String.format("%.1f", plugin.engine().cooldownSeconds(t, s.tier())) + "s <dark_gray>| <gray>key <yellow>" + d.bind(i).label));
            player.sendMessage(plugin.mm("  <gray>" + t.description));
            player.sendMessage(plugin.mm("  <light_purple>" + t.bonusName + ": <white>" + t.bonusDesc));
            player.sendMessage(plugin.mm("  <green>On cast: <white>" + plugin.engine().buffText(t)));
            if (!s.upgraded()) {
                int frag = fragCost(), astral = astralCost();
                int needOwn = frag + (t.material == CosmicMaterial.ASTRAL_FRAGMENT ? astral : 0);
                String line = "  <gray>Upgrade: " + col(t.material.count(player) >= needOwn)
                        + t.material.count(player) + "/" + needOwn + " " + t.material.colored();
                if (astral > 0 && t.material != CosmicMaterial.ASTRAL_FRAGMENT) {
                    int have = CosmicMaterial.ASTRAL_FRAGMENT.count(player);
                    line += " <gray>+ " + col(have >= astral) + have + "/" + astral + " " + CosmicMaterial.ASTRAL_FRAGMENT.colored();
                }
                player.sendMessage(plugin.mm(line + " <dark_gray>(/cosmic upgrade " + i + ")"));
            }
        }
        if (d.lostAbility() != null) {
            player.sendMessage(plugin.mm("<gray>Lost: " + d.lostAbility().colored() + " <gray>- a <light_purple>Cosmic Reroll <gray>brings it back."));
        }
        player.sendMessage(plugin.mm("<gray>AFK tokens: <aqua>" + d.afkTokens()));
        player.sendMessage(plugin.mm("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private static String col(boolean ok) { return ok ? "<green>" : "<red>"; }

    private int fragCost() {
        return plugin.getConfig().getInt("upgrades.cost.fragments", 16);
    }

    private int astralCost() {
        return plugin.getConfig().getInt("upgrades.cost.astral-fragments", 2);
    }

    private static int slotArg(String s) {
        return s.trim().equals("2") ? 2 : 1;
    }

    private void upgrade(CommandSender sender, int slotNumber) {
        if (!(sender instanceof Player player)) { send(sender, "<red>Players only."); return; }
        PlayerData pd = plugin.data().get(player.getUniqueId());
        if (pd == null) return;
        AbilitySlot s = pd.slot(slotNumber);
        if (s.isEmpty()) { send(player, "<red>Slot " + slotNumber + " is empty."); return; }
        AbilityType t = s.ability();
        if (s.upgraded()) { send(player, t.colored() + " <gray>is already upgraded."); return; }
        if (plugin.combat().inCombat(player, 10)) { send(player, "<red>You can't upgrade during combat."); return; }
        int frag = fragCost(), astral = astralCost();
        int needOwn = frag + (t.material == CosmicMaterial.ASTRAL_FRAGMENT ? astral : 0);
        if (t.material.count(player) < needOwn) {
            send(player, "<red>You need <white>" + needOwn + "x " + t.material.colored() + "<red>. (" + t.material.source + ")");
            return;
        }
        if (astral > 0 && t.material != CosmicMaterial.ASTRAL_FRAGMENT && CosmicMaterial.ASTRAL_FRAGMENT.count(player) < astral) {
            send(player, "<red>You also need <white>" + astral + "x " + CosmicMaterial.ASTRAL_FRAGMENT.colored() + "<red>.");
            return;
        }
        t.material.take(player, needOwn);
        if (astral > 0 && t.material != CosmicMaterial.ASTRAL_FRAGMENT) CosmicMaterial.ASTRAL_FRAGMENT.take(player, astral);
        s.setTier(2);
        plugin.data().save(pd);
        if (plugin.getConfig().getBoolean("upgrades.cutscene", true)) plugin.cutscenes().playUpgrade(player, t, 2);
        else send(player, t.colored() + " <gray>is now <aqua>upgraded<gray>!");
        Bukkit.broadcast(plugin.mm(plugin.prefix() + "<white>" + player.getName() + " <gray>upgraded " + t.colored() + "<gray>!"));
    }

    // ---------------------------------------------------------------- admin

    private void admin(CommandSender s, String[] a) {
        if (a.length == 0) { help(s); return; }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.afkShop().reload();
                send(s, "<green>Config and AFK shop reloaded.");
            }
            case "boss" -> {
                if (!(s instanceof Player p)) { send(s, "<red>Run in-game (spawns at your location)."); return; }
                BossType type = a.length > 1 ? BossType.fromId(a[1]) : null;
                if (type == null) { send(s, "<red>Bosses: " + String.join(", ", ids(BossType.values()))); return; }
                plugin.bosses().spawn(type, p.getLocation());
            }
            case "set", "reroll", "settier", "give", "giveorb", "givereroll", "tokens", "resetcd" -> {
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
                d.equip(t, d.isEmpty() ? 1 : d.tier());
                send(s, "<green>Set " + name + "'s slot " + slot + " to " + t.displayName + ".");
                if (online != null) send(online, "<gray>Your slot " + slot + " ability is now " + t.colored());
            }
            case "reroll" -> {
                AbilityType t = AbilityType.values()[ThreadLocalRandom.current().nextInt(AbilityType.values().length)];
                pd.primary().equip(t, 1);
                send(s, "<green>Rerolled " + name + " into " + t.displayName + " (Tier I).");
                if (online != null) plugin.playerListener().awaken(online, t);
            }
            case "settier" -> {
                int tier = a.length > 2 ? parse(a[2], 1) : 1;
                AbilitySlot d = pd.slot(a.length > 3 ? slotArg(a[3]) : 1);
                d.setTier(tier);
                send(s, "<green>" + name + "'s ability is now " + AbilityType.tierName(d.tier()) + ".");
            }
            case "give" -> {
                CosmicMaterial m = a.length > 2 ? CosmicMaterial.fromId(a[2]) : null;
                if (m == null || online == null) {
                    send(s, "<red>Usage: /cosmic admin give <online player> <" + String.join("|", ids(CosmicMaterial.values())) + "> [amount]");
                    return;
                }
                int amt = a.length > 3 ? parse(a[3], 1) : 1;
                m.give(online, amt);
                send(s, "<green>Gave " + amt + "x " + m.displayName + " to " + name + ".");
            }
            case "giveorb" -> {
                AbilityType t = a.length > 2 ? AbilityType.fromId(a[2]) : null;
                if (t == null || online == null) { send(s, "<red>Usage: /cosmic admin giveorb <online player> <ability>"); return; }
                online.getInventory().addItem(plugin.orbs().orb(t, 1));
                send(s, "<green>Gave " + name + " a " + t.displayName + " orb.");
            }
            case "givereroll" -> {
                if (online == null) { send(s, "<red>Player must be online."); return; }
                online.getInventory().addItem(plugin.orbs().reroll());
                send(s, "<green>Gave " + name + " a Cosmic Reroll.");
            }
            case "tokens" -> {
                int amt = a.length > 2 ? parse(a[2], 0) : 0;
                pd.addAfkTokens(amt);
                send(s, "<green>" + name + " now has " + pd.afkTokens() + " AFK tokens.");
            }
            case "resetcd" -> {
                plugin.engine().resetCooldown(target.getUniqueId());
                send(s, "<green>Cooldowns reset for " + name + ".");
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission("cosmicabilities.admin");
        if (args.length == 1) {
            options.addAll(List.of("info", "upgrade", "abilities", "help"));
            if (admin) options.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("upgrade")) {
            options.addAll(List.of("1", "2"));
        } else if (admin && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) {
                options.addAll(List.of("set", "reroll", "settier", "give", "giveorb", "givereroll", "tokens", "boss", "resetcd", "reload"));
            } else if (args.length == 3) {
                if (args[1].equalsIgnoreCase("boss")) options.addAll(ids(BossType.values()));
                else for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            } else if (args.length == 4) {
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "set", "giveorb" -> options.addAll(ids(AbilityType.values()));
                    case "give" -> options.addAll(ids(CosmicMaterial.values()));
                    case "settier" -> options.addAll(List.of("1", "2"));
                    default -> { }
                }
            } else if (args.length == 5 && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("settier"))) {
                options.addAll(List.of("1", "2"));
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return options;
    }
}
