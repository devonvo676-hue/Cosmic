package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * [ADMIN] / [OWNER] shown right beside the player's username: above their head and in tab (scoreboard team prefix),
 * and in chat (display name). Admin tag: OP or cosmicabilities.admin. Owner tag: OP only. Self-only.
 */
public class RankTags {
    public enum Rank {
        ADMIN("cosmic_tag_admin", '', "[ADMIN]", NamedTextColor.RED),
        OWNER("cosmic_tag_owner", '', "[OWNER]", NamedTextColor.GOLD);

        final String team;
        final char glyph;
        final String text;
        final NamedTextColor color;

        Rank(String team, char glyph, String text, NamedTextColor color) {
            this.team = team;
            this.glyph = glyph;
            this.text = text;
            this.color = color;
        }
    }

    private final CosmicAbilitiesPlugin plugin;

    public RankTags(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean canUse(Player p, Rank rank) {
        return rank == Rank.OWNER ? p.isOp() : (p.isOp() || p.hasPermission("cosmicabilities.admin"));
    }

    public static boolean isTagTeam(Team team) {
        return team != null && team.getName().startsWith("cosmic_tag_");
    }

    private Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    /** "[OWNER] " in gold bold (or the pixel badge if tags.use-icons is on). */
    private Component prefix(Rank rank) {
        if (plugin.getConfig().getBoolean("tags.use-icons", false)) {
            return Component.text(String.valueOf(rank.glyph)).font(Key.key("cosmic", "tags"))
                    .color(NamedTextColor.WHITE).append(Component.text(" "));
        }
        return Component.text(rank.text, rank.color).decorate(TextDecoration.BOLD)
                .append(Component.text(" ").decoration(TextDecoration.BOLD, false));
    }

    private Team team(Rank rank) {
        Team team = board().getTeam(rank.team);
        if (team == null) team = board().registerNewTeam(rank.team);
        team.prefix(prefix(rank));
        team.color(NamedTextColor.WHITE);
        return team;
    }

    /** Applies a tag. Returns false if the player isn't allowed to wear it. */
    public boolean apply(Player p, Rank rank) {
        if (!canUse(p, rank)) return false;
        clear(p);
        team(rank).addEntry(p.getName());
        p.displayName(prefix(rank).append(Component.text(p.getName(), NamedTextColor.WHITE)));
        return true;
    }

    public void clear(Player p) {
        for (Rank r : Rank.values()) {
            Team t = board().getTeam(r.team);
            if (t != null && t.hasEntry(p.getName())) t.removeEntry(p.getName());
        }
        p.displayName(null);
    }

    /** Called on join: strips a tag the player is no longer allowed to wear, and restores the chat name. */
    public void validate(Player p) {
        for (Rank r : Rank.values()) {
            Team t = board().getTeam(r.team);
            if (t == null || !t.hasEntry(p.getName())) continue;
            if (!canUse(p, r)) {
                t.removeEntry(p.getName());
                p.displayName(null);
            } else {
                team(r);
                p.displayName(prefix(r).append(Component.text(p.getName(), NamedTextColor.WHITE)));
            }
        }
    }
}
