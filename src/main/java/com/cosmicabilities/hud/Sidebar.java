package com.cosmicabilities.hud;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.data.PlayerData;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The COSMIC panel on the right of the screen: your name, cash and kills, then ping and players online.
 * /sidebar toggles it per player. Each player gets their own scoreboard, with the name-tag teams copied in so
 * [RANK] tags still show.
 */
public class Sidebar implements Listener, CommandExecutor {
    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public Sidebar(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private static Component icon(String ns, String path, boolean block) {
        return Component.object(ObjectContents.sprite(Key.key("minecraft", block ? "blocks" : "items"), Key.key(ns, path)));
    }

    private boolean wants(Player p) {
        if (!plugin.getConfig().getBoolean("sidebar.enabled", true)) return false;
        PlayerData d = plugin.data().get(p.getUniqueId());
        return d == null || d.sidebar();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> update(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    /** Called every second. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p);
            tab(p);
        }
    }

    /** The custom tab list: COSMIC banner on top, team / cash / season / events underneath, names show [RANK] and team. */
    public void tab(Player p) {
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) return;
        com.cosmicabilities.team.Teams.Team team = plugin.teams().teamOf(p.getUniqueId());
        StringBuilder events = new StringBuilder();
        for (var e : plugin.events().activeEvents()) {
            if (events.length() > 0) events.append(" <dark_gray>| ");
            events.append(e.color).append("<bold>").append(e.title).append("</bold>");
        }
        String head = plugin.getConfig().getString("tablist.header-line",
                "<gray>Money SMP with cosmic abilities");
        Component header = plugin.mm("\n<gradient:#9b5de5:#f15bb5:#00bbf9><bold>✦ C O S M I C ✦</bold></gradient>\n" + head
                + "\n<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");
        String footer = "\n<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                + "\n<#f15bb5>Team: <white>" + (team == null ? "<gray>none <dark_gray>(/team create)" : team.name + " <gray>| Bank <gold>$" + plugin.economy().format(team.bank))
                + "\n<#f15bb5>Cash: <gold>$" + plugin.economy().format(plugin.economy().balance(p.getUniqueId()))
                + "  <#f15bb5>Ping: <white>" + p.getPing() + "ms  <#f15bb5>Online: <white>" + Bukkit.getOnlinePlayers().size()
                + "\n<#00bbf9>Season " + plugin.teams().season() + " <gray>ends in <white>" + plugin.teams().timeLeft()
                + (events.length() > 0 ? "\n<yellow>Events: " + events : "")
                + "\n" + plugin.getConfig().getString("tablist.footer-line", "<dark_gray>play.cosmic.net") + "\n";
        p.sendPlayerListHeaderAndFooter(header, plugin.mm(footer));
        Component rank = p.playerListName();
        String teamTag = team == null ? "" : " <dark_gray>[<aqua>" + team.name + "<dark_gray>]";
        Component name = p.displayName().append(plugin.mm(teamTag));
        if (!name.equals(rank)) p.playerListName(name);
    }

    public void update(Player p) {
        if (!p.isOnline()) return;
        if (!wants(p)) {
            if (boards.remove(p.getUniqueId()) != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }
        Scoreboard board = boards.computeIfAbsent(p.getUniqueId(), k -> {
            Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
            copyTeams(b);
            return b;
        });
        if (p.getScoreboard() != board) p.setScoreboard(board);
        Objective obj = board.getObjective("cosmic_side");
        if (obj == null) {
            obj = board.registerNewObjective("cosmic_side", Criteria.DUMMY,
                    plugin.mm("<gradient:#9b5de5:#f15bb5:#00bbf9><bold>✦ COSMIC ✦</bold></gradient>"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
        }
        PlayerData d = plugin.data().get(p.getUniqueId());
        long cash = plugin.economy().balance(p.getUniqueId());
        int kills = p.getStatistic(Statistic.PLAYER_KILLS);
        com.cosmicabilities.team.Teams.Team team = plugin.teams().teamOf(p.getUniqueId());
        String accent = "<#f15bb5>", value = "<#ffd6f0>";
        List<Component> lines = List.of(
                Component.empty(),
                plugin.mm(accent + "<bold>" + p.getName()),
                icon("minecraft", "item/netherite_sword", false).append(plugin.mm(" <white>Kills: " + value + kills)),
                icon("cosmic", "item/coin", false).append(plugin.mm(" <white>Cash: " + value + "$" + plugin.economy().format(cash))),
                Component.text(" "),
                plugin.mm(accent + "<bold>Team"),
                icon("minecraft", "item/white_banner", false).append(plugin.mm(" <white>" + (team == null ? "<gray>None" : team.name))),
                icon("minecraft", "item/gold_ingot", false).append(plugin.mm(" <white>Bank: " + value + (team == null ? "-" : "$" + plugin.economy().format(team.bank)))),
                icon("minecraft", "item/clock_00", false).append(plugin.mm(" <white>Season " + plugin.teams().season() + ": " + value + plugin.teams().timeLeft())),
                Component.text("   "),
                plugin.mm(accent + "<bold>Server"),
                icon("minecraft", "item/redstone", false).append(plugin.mm(" <white>Ping: " + value + p.getPing() + " ms")),
                icon("minecraft", "item/ender_pearl", false).append(plugin.mm(" <white>Online: " + value + Bukkit.getOnlinePlayers().size()
                        + "/" + Bukkit.getMaxPlayers())),
                Component.text("  "));
        for (int i = 0; i < lines.size(); i++) {
            var score = obj.getScore("l" + i);
            score.setScore(lines.size() - i);
            score.customName(lines.get(i));
        }
    }

    /** Mirror the name-tag teams ([ADMIN], [SUPERNOVA]...) from the main scoreboard onto every personal one. */
    public void syncTeams() {
        for (Scoreboard b : boards.values()) copyTeams(b);
    }

    private void copyTeams(Scoreboard target) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team src : main.getTeams()) {
            Team t = target.getTeam(src.getName());
            if (t == null) t = target.registerNewTeam(src.getName());
            t.prefix(src.prefix());
            t.suffix(src.suffix());
            try {
                if (src.hasColor() && src.color() instanceof net.kyori.adventure.text.format.NamedTextColor n) t.color(n);
            } catch (RuntimeException ignored) { }
            for (String e : t.getEntries()) if (!src.hasEntry(e)) t.removeEntry(e);
            for (String e : src.getEntries()) if (!t.hasEntry(e)) t.addEntry(e);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return true;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return true;
        boolean on = args.length > 0 ? args[0].equalsIgnoreCase("on") : !d.sidebar();
        d.setSidebar(on);
        update(p);
        p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Sidebar " + (on ? "<green>on" : "<red>off") + "<gray>. <yellow>/sidebar <gray>to toggle."));
        return true;
    }
}
