package com.cosmicabilities.status;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/** Enforces freeze, inverted controls, burrow, forcefields, Solar reflect and fall immunity. */
public class StatusListener implements Listener {
    private final CosmicAbilitiesPlugin plugin;

    public StatusListener(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    private StatusManager s() {
        return plugin.status();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Location from = event.getFrom(), to = event.getTo();
        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        if (!moved) return;

        if (s().isFrozen(p.getUniqueId())) {
            Location pin = s().frozenAt(p.getUniqueId()).clone();
            pin.setYaw(to.getYaw());
            pin.setPitch(to.getPitch());
            event.setTo(pin);
            return;
        }
        if (s().isInverted(p.getUniqueId())) {
            double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
            if (dx * dx + dz * dz > 1.0E-4) {
                Location back = from.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                back.setY(to.getY());
                event.setTo(back);
                p.setVelocity(new Vector(-dx * 1.15, p.getVelocity().getY(), -dz * 1.15));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && s().isFallImmune(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        StatusManager.Shield shield = s().activeShield(player.getUniqueId());
        if (shield == null) return;
        double raw = event.getDamage();
        event.setDamage(raw * (1 - shield.reduction()));
        player.getWorld().spawnParticle(Particle.WAX_OFF, player.getLocation().add(0, 1, 0), 6, 0.4, 0.5, 0.4, 0.5);
        if (s().isReflecting() || !(event instanceof EntityDamageByEntityEvent byEntity)) return;
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity shooter) damager = shooter;
        if (damager instanceof LivingEntity attacker && !attacker.equals(player)) {
            s().reflect(player, attacker, raw * shield.reflect(), shield.evolved());
        }
    }

    /** Frozen entities can't attack; burrowed players surface when they attack. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) damager = shooter;
        if (s().isFrozen(damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (damager instanceof Player p && s().isBurrowed(p.getUniqueId())) {
            s().endBurrow(p, true);
        }
    }

    /** Mobs lose track of burrowed players. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player p && s().isBurrowed(p.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (denied(event.getPlayer(), event.getBlockPlaced())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (denied(event.getPlayer(), event.getBlock())) event.setCancelled(true);
    }

    private boolean denied(Player p, Block block) {
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        if (s().blockingField(at, p.getUniqueId()) == null) return false;
        p.getWorld().spawnParticle(Particle.DUST, at, 12, 0.3, 0.3, 0.3, 0, Fx.dust(0xff3b3b, 1.2f));
        p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 6, 0.2, 0.2, 0.2, 0.05);
        p.playSound(at, Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.6f);
        p.sendActionBar(Component.text("A Magnetosphere forcefield blocks placing here", NamedTextColor.RED));
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        s().hideBurrowedFrom(event.getPlayer());
    }
}
