package com.cosmicabilities.discord;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sends public game chat to a Discord channel through a webhook (Discord: channel settings -> Integrations ->
 * Webhooks -> New Webhook -> Copy URL, then /discordbridge set <url>). Private messages (/msg, /tell, /w...) are
 * commands, not chat, so they never reach Discord. @everyone / @here / role pings are disabled.
 */
public class DiscordBridge implements Listener, CommandExecutor, TabCompleter {
    private final CosmicAbilitiesPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public DiscordBridge(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private String url() {
        String u = plugin.getConfig().getString("discord.webhook-url", "");
        return u == null ? "" : u.trim();
    }

    private boolean on() {
        return plugin.getConfig().getBoolean("discord.enabled", true) && url().startsWith("https://");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!on()) return;
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        Player p = event.getPlayer();
        send(p.getName(), avatar(p), text);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (on() && plugin.getConfig().getBoolean("discord.join-leave", true)) {
            serverLine("**" + event.getPlayer().getName() + "** joined the server");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (on() && plugin.getConfig().getBoolean("discord.join-leave", true)) {
            serverLine("**" + event.getPlayer().getName() + "** left the server");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!on() || !plugin.getConfig().getBoolean("discord.deaths", true)) return;
        Component msg = event.deathMessage();
        if (msg != null) serverLine(":skull: " + PlainTextComponentSerializer.plainText().serialize(msg));
    }

    private String avatar(Player p) {
        return "https://mc-heads.net/avatar/" + p.getUniqueId() + "/128";
    }

    private void serverLine(String text) {
        send(plugin.getConfig().getString("discord.server-name", "Minecraft"), null, text);
    }

    /** Posts asynchronously so chat never waits on Discord. */
    public void send(String username, String avatarUrl, String content) {
        String url = url();
        if (!url.startsWith("https://")) return;
        String body = "{\"username\":" + json(username.length() > 80 ? username.substring(0, 80) : username)
                + (avatarUrl != null ? ",\"avatar_url\":" + json(avatarUrl) : "")
                + ",\"content\":" + json(content.length() > 1900 ? content.substring(0, 1900) : content)
                + ",\"allowed_mentions\":{\"parse\":[]}}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("User-Agent", "CosmicAbilities-DiscordBridge")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((res, err) -> {
            if (err != null) plugin.getLogger().warning("Discord bridge: " + err.getMessage());
            else if (res.statusCode() >= 400) plugin.getLogger().warning("Discord bridge: Discord answered HTTP " + res.statusCode()
                    + " - check discord.webhook-url");
        });
    }

    private static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> { }
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }

    // /discordbridge set <url> | off | on | test
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!BanPistol.isAdmin(sender)) return true;
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> {
                if (args.length < 2 || !args[1].startsWith("https://discord")) {
                    sender.sendMessage(plugin.mm(plugin.prefix() + "<red>Paste the full webhook URL: /discordbridge set https://discord.com/api/webhooks/..."));
                    return true;
                }
                plugin.getConfig().set("discord.webhook-url", args[1]);
                plugin.getConfig().set("discord.enabled", true);
                plugin.saveConfig();
                sender.sendMessage(plugin.mm(plugin.prefix() + "<green>Discord bridge connected. Sending a test message..."));
                serverLine(":satellite: Minecraft chat is now linked to this channel.");
            }
            case "off" -> { plugin.getConfig().set("discord.enabled", false); plugin.saveConfig(); sender.sendMessage(plugin.mm(plugin.prefix() + "<gray>Discord bridge off.")); }
            case "on" -> { plugin.getConfig().set("discord.enabled", true); plugin.saveConfig(); sender.sendMessage(plugin.mm(plugin.prefix() + "<green>Discord bridge on.")); }
            case "test" -> { serverLine(":white_check_mark: Test message from the Minecraft server."); sender.sendMessage(plugin.mm(plugin.prefix() + "<gray>Sent a test message.")); }
            default -> sender.sendMessage(plugin.mm(plugin.prefix() + "<gray>Discord bridge: " + (on() ? "<green>connected" : "<red>not set up")
                    + "<gray>. <yellow>/discordbridge set <webhook url> <gray>| on | off | test"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (BanPistol.isAdmin(sender) && args.length == 1) {
            for (String s : List.of("set", "on", "off", "test")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        }
        return out;
    }
}
