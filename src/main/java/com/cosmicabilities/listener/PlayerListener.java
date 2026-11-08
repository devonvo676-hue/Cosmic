package com.cosmicabilities.listener;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityEngine;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.data.PlayerData;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

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
        sendPack(player);
        if (data.primary().isEmpty()) {
            AbilityType[] all = AbilityType.values();
            AbilityType chosen = all[ThreadLocalRandom.current().nextInt(all.length)];
            data.primary().equip(chosen, 1);
            plugin.data().save(data);
            // Delay so the title shows after the join screen / pack prompt.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> awaken(player, chosen), 60L);
        }
    }

    /** Sends the Cosmic Abilities resource pack (stacks on top of the server's own pack). */
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
                plugin.mm("<gray>You have awakened " + type.colored(1)),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))));
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 1.4f);
        player.sendMessage(plugin.mm(plugin.prefix() + "<gray>Your cosmic ability is " + type.colored(1)
                + " <dark_gray>(" + type.role + ")"));
        player.sendMessage(plugin.mm("<gray>" + type.description));
        player.sendMessage(plugin.mm("<gray>Cast it with <yellow>/ability 1 <gray>or <yellow>Sneak + F<gray>. See <yellow>/cosmic help<gray>."));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.engine().clear(event.getPlayer().getUniqueId());
        plugin.data().unload(event.getPlayer().getUniqueId());
    }

    /** Sneak + swap-hands (F) casts slot 1. */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!plugin.getConfig().getBoolean("activation.sneak-swap-hand", true)) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        event.setCancelled(true);
        plugin.engine().tryCast(player, 1);
    }

    /** Sneak + left-click air with an empty hand casts slot 2. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("activation.sneak-left-click-empty-hand", true)) return;
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.LEFT_CLICK_AIR) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.getInventory().getItemInMainHand().getType().isAir()) return;
        PlayerData d = plugin.data().get(player.getUniqueId());
        if (d == null || d.slot(2).isEmpty()) return;
        plugin.engine().tryCast(player, 2);
    }

    /** Solar shield damage reduction, Meteor fall immunity. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && plugin.engine().isFallImmune(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        AbilityEngine.Shield shield = plugin.engine().activeShield(player.getUniqueId());
        if (shield == null) return;
        event.setDamage(event.getDamage() * (1 - shield.reduction()));
        if (shield.evolved() && event instanceof EntityDamageByEntityEvent byEntity) {
            // Solar Ascension: burn attackers.
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity shooter) damager = shooter;
            if (damager instanceof LivingEntity attacker && !attacker.equals(player)) {
                attacker.setFireTicks(Math.max(attacker.getFireTicks(), 60));
            }
        }
    }
}
