package com.cosmicabilities.team;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.admin.BanPistol;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Teams (Money-SMP style): /team create, invite, accept, leave, kick, deposit, info, top.
 * Teammates can't hurt each other (weapons, projectiles or abilities). Kills made by a team member pay into the
 * team bank. Seasons last season.days (14); the team with the biggest bank when the season ends wins a rare
 * ability for every member, then all banks reset.
 */
public class Teams implements Listener, CommandExecutor, TabCompleter {
    public static final class Team {
        public final String name;
        public UUID leader;
        public final Set<UUID> members = new LinkedHashSet<>();
        public long bank;
        Team(String name, UUID leader) { this.name = name; this.leader = leader; members.add(leader); }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final File file;
    private final Map<String, Team> teams = new HashMap<>();          // lower-case name -> team
    private final Map<UUID, Team> byPlayer = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();
    /** season rewards waiting for players who were offline when the season ended */
    private final Map<UUID, List<String>> pendingRewards = new HashMap<>();
    private long seasonStart;
    private int season = 1;

    public Teams(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        boolean fresh = !file.exists();
        load();
        if (fresh) save();   // remember when season 1 started
    }

    // ------------------------------------------------------------------ storage

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        season = yml.getInt("season.number", 1);
        seasonStart = yml.getLong("season.start", System.currentTimeMillis());
        ConfigurationSection ts = yml.getConfigurationSection("teams");
        if (ts != null) for (String key : ts.getKeys(false)) {
            ConfigurationSection s = ts.getConfigurationSection(key);
            try {
                Team t = new Team(s.getString("name", key), UUID.fromString(s.getString("leader")));
                for (String m : s.getStringList("members")) t.members.add(UUID.fromString(m));
                t.bank = s.getLong("bank");
                teams.put(t.name.toLowerCase(Locale.ROOT), t);
                for (UUID m : t.members) byPlayer.put(m, t);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping broken team " + key);
            }
        }
        ConfigurationSection pr = yml.getConfigurationSection("pending-rewards");
        if (pr != null) for (String k : pr.getKeys(false)) pendingRewards.put(UUID.fromString(k), new ArrayList<>(pr.getStringList(k)));
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("season.number", season);
        yml.set("season.start", seasonStart);
        for (Team t : teams.values()) {
            String b = "teams." + t.name.toLowerCase(Locale.ROOT) + ".";
            yml.set(b + "name", t.name);
            yml.set(b + "leader", t.leader.toString());
            List<String> m = new ArrayList<>();
            for (UUID u : t.members) m.add(u.toString());
            yml.set(b + "members", m);
            yml.set(b + "bank", t.bank);
        }
        for (Map.Entry<UUID, List<String>> e : pendingRewards.entrySet()) yml.set("pending-rewards." + e.getKey(), e.getValue());
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Couldn't save teams.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ queries

    public Team teamOf(UUID player) { return byPlayer.get(player); }

    public boolean sameTeam(UUID a, UUID b) {
        Team t = byPlayer.get(a);
        return t != null && t == byPlayer.get(b);
    }

    public List<Team> ranking() {
        List<Team> list = new ArrayList<>(teams.values());
        list.sort(Comparator.comparingLong((Team t) -> t.bank).reversed());
        return list;
    }

    public int season() { return season; }

    public long seasonEndsAt() {
        return seasonStart + plugin.getConfig().getLong("season.days", 14) * 86_400_000L;
    }

    public String timeLeft() {
        long ms = Math.max(0, seasonEndsAt() - System.currentTimeMillis());
        long d = ms / 86_400_000L, h = ms / 3_600_000L % 24, m = ms / 60_000L % 60;
        return d > 0 ? d + "d " + h + "h" : h + "h " + m + "m";
    }

    public void resetBanks() {
        for (Team t : teams.values()) t.bank = 0;
        save();
    }

    public void addToBank(Team t, long amount) {
        t.bank = Math.max(0, t.bank + amount);
        save();
    }

    private void msg(CommandSender s, String mini) { s.sendMessage(plugin.mm(plugin.prefix() + mini)); }

    private void teamMsg(Team t, String mini) {
        for (UUID u : t.members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) msg(p, "<aqua>[" + t.name + "] </aqua>" + mini);
        }
    }

    private String name(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return op.getName() == null ? "?" : op.getName();
    }

    // ------------------------------------------------------------------ friendly fire

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Entity d = event.getDamager();
        Player attacker = d instanceof Player p ? p : d instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (attacker == null || attacker.equals(victim)) return;
        if (sameTeam(attacker.getUniqueId(), victim.getUniqueId())) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ seasons

    /** Every minute: end the season automatically when its time is up. */
    public void tick() {
        if (plugin.getConfig().getBoolean("season.auto-end", true) && System.currentTimeMillis() >= seasonEndsAt()) endSeason(null);
    }

    /** Crowns the richest team: every member gets a rare (boss/dragon) ability orb. Then every bank resets. */
    public void endSeason(CommandSender by) {
        List<Team> rank = ranking();
        Team winner = rank.isEmpty() || rank.get(0).bank <= 0 ? null : rank.get(0);
        if (winner != null) {
            String rewardId = plugin.getConfig().getString("season.reward", "random-rare");
            for (UUID u : winner.members) {
                AbilityType reward = rewardFor(rewardId);
                Player p = Bukkit.getPlayer(u);
                if (p != null) giveReward(p, reward);
                else pendingRewards.computeIfAbsent(u, k -> new ArrayList<>()).add(reward.id());
            }
            Bukkit.broadcast(plugin.mm(plugin.prefix() + "<gold><bold>SEASON " + season + " IS OVER!</bold> <gray>Team <aqua><bold>" + winner.name
                    + "</bold> <gray>wins with <yellow>$" + plugin.economy().format(winner.bank) + "<gray>! Every member gets a rare cosmic ability."));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(Title.title(plugin.mm("<gold><bold>Season " + season + " over"), plugin.mm("<aqua>" + winner.name + " <gray>wins!"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(4000), Duration.ofMillis(800))));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
            }
        } else {
            Bukkit.broadcast(plugin.mm(plugin.prefix() + "<gold>Season " + season + " is over - no team had any money, so nobody wins."));
        }
        for (Team t : teams.values()) t.bank = 0;
        season++;
        seasonStart = System.currentTimeMillis();
        save();
        Bukkit.broadcast(plugin.mm(plugin.prefix() + "<green><bold>Season " + season + " has begun!</bold> <gray>Team banks are back to $0. <yellow>/team top"));
        if (by != null) msg(by, "<gray>Season ended.");
    }

    private AbilityType rewardFor(String id) {
        AbilityType fixed = AbilityType.fromId(id);
        if (fixed != null) return fixed;
        List<AbilityType> rare = new ArrayList<>();
        for (AbilityType t : AbilityType.values()) if (t.exclusive()) rare.add(t);
        return rare.get(ThreadLocalRandom.current().nextInt(rare.size()));
    }

    private void giveReward(Player p, AbilityType type) {
        for (var left : p.getInventory().addItem(plugin.orbs().orb(type, 1)).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        msg(p, "<gold>Season reward: <white>a " + type.colored() + " <white>ability orb! <gray>Right-click it to absorb it.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        List<String> rewards = pendingRewards.remove(event.getPlayer().getUniqueId());
        if (rewards == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (String id : rewards) {
                AbilityType t = AbilityType.fromId(id);
                if (t != null) giveReward(event.getPlayer(), t);
            }
        }, 60L);
        save();
    }

    // ------------------------------------------------------------------ /team

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("endseason")) {
            if (BanPistol.isAdmin(sender)) endSeason(sender);
            return true;
        }
        if (sub.equals("top") || sub.equals("list")) { top(sender); return true; }
        if (!(sender instanceof Player p)) return true;
        Team mine = teamOf(p.getUniqueId());
        switch (sub) {
            case "create" -> {
                if (mine != null) { msg(p, "<red>You're already in a team. <gray>/team leave first."); return true; }
                if (args.length < 2 || !args[1].matches("[A-Za-z0-9_]{2,16}")) { msg(p, "<red>/team create <name> <gray>(2-16 letters/numbers)"); return true; }
                if (teams.containsKey(args[1].toLowerCase(Locale.ROOT))) { msg(p, "<red>That name is taken."); return true; }
                Team t = new Team(args[1], p.getUniqueId());
                teams.put(t.name.toLowerCase(Locale.ROOT), t);
                byPlayer.put(p.getUniqueId(), t);
                save();
                msg(p, "<green>Team <aqua><bold>" + t.name + "</bold> <green>created! <gray>Invite people with <yellow>/team invite <player>");
            }
            case "invite" -> {
                if (mine == null) { msg(p, "<red>Create a team first: /team create <name>"); return true; }
                if (!mine.leader.equals(p.getUniqueId())) { msg(p, "<red>Only the team leader can invite."); return true; }
                Player to = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (to == null) { msg(p, "<red>/team invite <online player>"); return true; }
                if (teamOf(to.getUniqueId()) != null) { msg(p, "<red>" + to.getName() + " is already in a team."); return true; }
                int max = plugin.getConfig().getInt("teams.max-size", 4);
                if (mine.members.size() >= max) { msg(p, "<red>Your team is full (" + max + ")."); return true; }
                invites.put(to.getUniqueId(), mine.name.toLowerCase(Locale.ROOT));
                msg(p, "<gray>Invited <white>" + to.getName() + "<gray>.");
                to.sendMessage(plugin.mm(plugin.prefix() + "<white>" + p.getName() + " <gray>invited you to team <aqua><bold>" + mine.name
                        + "</bold><gray>. <click:run_command:'/team accept'><green><bold>[ACCEPT]</bold></click> <gray>or <yellow>/team accept"));
                to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
            }
            case "accept", "join" -> {
                String key = invites.remove(p.getUniqueId());
                Team t = key == null ? null : teams.get(key);
                if (t == null) { msg(p, "<red>You don't have a team invite."); return true; }
                if (mine != null) { msg(p, "<red>Leave your team first."); return true; }
                if (t.members.size() >= plugin.getConfig().getInt("teams.max-size", 4)) { msg(p, "<red>That team is full."); return true; }
                t.members.add(p.getUniqueId());
                byPlayer.put(p.getUniqueId(), t);
                save();
                teamMsg(t, "<white>" + p.getName() + " <green>joined the team!");
            }
            case "leave" -> {
                if (mine == null) { msg(p, "<red>You're not in a team."); return true; }
                if (mine.leader.equals(p.getUniqueId()) && mine.members.size() > 1) {
                    msg(p, "<red>You're the leader - /team kick everyone or /team disband first."); return true;
                }
                if (mine.members.size() == 1) { disband(mine); msg(p, "<gray>Team disbanded."); return true; }
                mine.members.remove(p.getUniqueId());
                byPlayer.remove(p.getUniqueId());
                save();
                teamMsg(mine, "<white>" + p.getName() + " <gray>left the team.");
                msg(p, "<gray>You left " + mine.name + ".");
            }
            case "kick" -> {
                if (mine == null || !mine.leader.equals(p.getUniqueId())) { msg(p, "<red>Only the team leader can kick."); return true; }
                OfflinePlayer t = args.length > 1 ? Bukkit.getOfflinePlayer(args[1]) : null;
                if (t == null || !mine.members.contains(t.getUniqueId()) || t.getUniqueId().equals(p.getUniqueId())) { msg(p, "<red>/team kick <member>"); return true; }
                mine.members.remove(t.getUniqueId());
                byPlayer.remove(t.getUniqueId());
                save();
                teamMsg(mine, "<white>" + t.getName() + " <gray>was kicked from the team.");
                if (t.getPlayer() != null) msg(t.getPlayer(), "<red>You were kicked from " + mine.name + ".");
            }
            case "disband" -> {
                if (mine == null || !mine.leader.equals(p.getUniqueId())) { msg(p, "<red>Only the team leader can disband."); return true; }
                teamMsg(mine, "<red>The team was disbanded.");
                disband(mine);
            }
            case "deposit" -> {
                if (mine == null) { msg(p, "<red>You're not in a team."); return true; }
                long amt = args.length > 1 ? com.cosmicabilities.economy.Economy.parse(args[1]) : -1;
                if (amt <= 0) { msg(p, "<red>/team deposit <amount>"); return true; }
                if (!plugin.economy().take(p.getUniqueId(), amt)) { msg(p, "<red>You don't have that much cash."); return true; }
                addToBank(mine, amt);
                teamMsg(mine, "<white>" + p.getName() + " <gray>deposited <yellow>$" + plugin.economy().format(amt) + " <gray>into the team bank.");
            }
            default -> info(p, mine);
        }
        return true;
    }

    private void disband(Team t) {
        teams.remove(t.name.toLowerCase(Locale.ROOT));
        for (UUID u : t.members) byPlayer.remove(u);
        save();
    }

    private void info(Player p, Team t) {
        if (t == null) {
            msg(p, "<gray>You're not in a team. <yellow>/team create <name><gray>, then <yellow>/team invite <player><gray>.");
            msg(p, "<gray>Teammates can't hurt each other, and every kill puts <yellow>$" + plugin.economy().format(plugin.economy().killReward())
                    + " <gray>into your team bank. Richest team at the end of the season wins a rare ability.");
            return;
        }
        int place = ranking().indexOf(t) + 1;
        p.sendMessage(plugin.mm("<aqua><bold>" + t.name + "</bold> <gray>- #" + place + " this season"));
        p.sendMessage(plugin.mm("<gray>Bank: <yellow>$" + plugin.economy().format(t.bank) + "   <gray>Season " + season + " ends in <white>" + timeLeft()));
        List<String> names = new ArrayList<>();
        for (UUID u : t.members) names.add((u.equals(t.leader) ? "<gold>★" : "<white>") + name(u));
        p.sendMessage(plugin.mm("<gray>Members: " + String.join("<gray>, ", names)));
    }

    private void top(CommandSender s) {
        s.sendMessage(plugin.mm("<gold><bold>Season " + season + " leaderboard</bold> <gray>(ends in " + timeLeft() + ")"));
        List<Team> r = ranking();
        for (int i = 0; i < Math.min(10, r.size()); i++) {
            Team t = r.get(i);
            s.sendMessage(plugin.mm("<gray>" + (i + 1) + ". <aqua>" + t.name + " <yellow>$" + plugin.economy().format(t.bank)
                    + " <dark_gray>(" + t.members.size() + " members)"));
        }
        if (r.isEmpty()) s.sendMessage(plugin.mm("<gray>No teams yet. <yellow>/team create <name>"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("create", "invite", "accept", "leave", "kick", "disband", "deposit", "info", "top"));
            if (BanPistol.isAdmin(sender)) out.add("endseason");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
