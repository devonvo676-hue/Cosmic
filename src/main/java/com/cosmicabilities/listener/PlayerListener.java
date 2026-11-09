package com.cosmicabilities.listener;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerListener implements Listener {
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("cosmicabilities-pack".getBytes(StandardCharsets.UTF_8));

    private final CosmicAbilitiesPlugin plugin;

    public PlayerListener(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data().load(player.getUniqueId());
        plugin.tags().validate(player);
        plugin.engine().stripOldPassives(player);
        sendPack(player);
        if (data.primary().isEmpty()) {
            // first time here: spin the cosmic wheel for their ability (after the resource pack has had time to load)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PlayerData d = plugin.data().get(player.getUniqueId());
                if (player.isOnline() && d != null && d.primary().isEmpty()) {
                    plugin.roll().spin(player, com.cosmicabilities.gui.RollPanel.Reason.FIRST_JOIN);
                }
            }, plugin.getConfig().getLong("first-join.spin-delay-ticks", 80L));
        }
    }

    private void sendPack(Player player) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        byte[] hash = null;
        if (sha1 != null && sha1.length() == 40) {
            try {
                hash = HexFormat.of().parseHex(sha1);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("resource-pack.sha1 is not valid hex; sending without a hash.");
            }
        }
        String prompt = plugin.getConfig().getString("resource-pack.prompt", "Cosmic Abilities icons");
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
        player.addResourcePack(PACK_ID, url, hash, prompt, required);
    }

    public void awaken(Player player, AbilityType type) {
        if (!player.isOnline()) return;
        player.showTitle(Title.title(
                plugin.mm("<gradient:#9b5de5:#00bbf9><bold>COSMIC AWAKENING"),
                plugin.mm("<gray>You have awakened " + type.colored()),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))));
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 1.4f);
        player.sendMessage(plugin.mm(plugin.prefix() + "<gray>Your cosmic ability is " + type.colored() + " <dark_gray>(" + type.role + ")"));
        player.sendMessage(plugin.mm("<gray>" + type.description));
        player.sendMessage(plugin.mm("<gray>Cast it with <yellow>/ability 1 <gray>or <yellow>Sneak + F <gray>(change it in <yellow>/keybinds<gray>). See <yellow>/abilities<gray>."));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.engine().clear(id);
        plugin.status().clear(id);
        plugin.combat().clear(id);
        plugin.data().unload(id);
    }

    /**
     * Death: a gained ability (slot 2) drops for the killer; the joined-with ability is never lost.
     * Optionally the remaining abilities also lose their upgrade (death.lose-upgrade).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        // "Steve was killed by Alex's Pulsar" instead of "Steve was killed by magic"
        com.cosmicabilities.util.Targets.AbilityHit hit = com.cosmicabilities.util.Targets.lastAbilityHit(p.getUniqueId());
        if (hit != null && plugin.getConfig().getBoolean("death-messages.name-ability", true)) {
            boolean byAbility = event.getDamageSource().getDamageType() == org.bukkit.damage.DamageType.MAGIC
                    || System.currentTimeMillis() - hit.at() < 300;
            if (byAbility) {
                event.deathMessage(plugin.mm("<white>" + p.getName() + " <gray>was killed by <white>" + hit.caster()
                        + "<gray>'s " + hit.ability()));
            }
        }
        plugin.status().clear(p.getUniqueId());
        String lost = plugin.orbs().handleDeath(p);
        if (lost != null) {
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>You lost " + lost + "<red>! <gray>Win a boss fight to gain a new second ability."));
        }
        if (!plugin.getConfig().getBoolean("death.lose-upgrade", true)) return;
        if (plugin.getConfig().getBoolean("death.pvp-only", false) && p.getKiller() == null) return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        StringBuilder changed = new StringBuilder();
        for (int i = 1; i <= PlayerData.SLOTS; i++) {
            AbilitySlot s = d.slot(i);
            if (s.isEmpty() || !s.upgraded()) continue;
            s.setTier(1);
            if (changed.length() > 0) changed.append("<gray>, ");
            changed.append(s.ability().colored());
        }
        if (changed.length() > 0) {
            plugin.data().save(d);
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>Your upgrade was lost on: " + changed));
        }
    }
}
