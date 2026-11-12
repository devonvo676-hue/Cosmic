package com.cosmicabilities.listener;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.boss.BossType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

public class BossListener implements Listener {
    private final CosmicAbilitiesPlugin plugin;

    public BossListener(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Bosses can only be hurt while enough players are nearby to fight them. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBossShield(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss) || plugin.bosses().typeOf(boss) == null) return;
        int min = plugin.bosses().requiredPlayers(boss);
        if (min <= 1) return;
        double r = plugin.getConfig().getDouble("bosses.fight-radius", 32);
        int count = 0;
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead()) continue;
            if (p.getLocation().distanceSquared(boss.getLocation()) <= r * r) count++;
        }
        if (count >= min) return;
        event.setCancelled(true);
        boss.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, boss.getLocation().add(0, 1.5, 0), 20, 0.8, 1, 0.8, 0.5);
        if (event instanceof EntityDamageByEntityEvent by) {
            Entity d = by.getDamager();
            if (d instanceof Projectile proj && proj.getShooter() instanceof Player shooter) d = shooter;
            if (d instanceof Player attacker) {
                attacker.sendActionBar(plugin.mm("<red>The boss is shielded! <gray>It takes <white>" + min
                        + " players <gray>nearby to fight it (" + count + " here)."));
                attacker.playSound(attacker.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossHurtSound(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss)) return;
        com.cosmicabilities.boss.BossType type = plugin.bosses().typeOf(boss);
        if (type != null) plugin.bosses().hurtSound(boss, type);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss)) return;
        if (plugin.bosses().typeOf(boss) == null) return;
        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) damager = shooter;
        if (damager instanceof Player player) {
            plugin.bosses().recordDamage(boss, player, event.getFinalDamage());
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (plugin.bosses().typeOf(entity) == null) return;
        event.getDrops().clear();
        event.setDroppedExp(250);
        plugin.bosses().handleDeath(entity);
    }

    /** Bosses don't burn in daylight (Stray / Wither Skeleton). */
    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (plugin.bosses().typeOf(event.getEntity()) != null && !(event instanceof org.bukkit.event.entity.EntityCombustByEntityEvent)) {
            event.setCancelled(true);
        }
    }

    /** Bosses only fight players - they won't get distracted by other mobs. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        BossType type = plugin.bosses().typeOf(event.getEntity());
        if (type != null && event.getTarget() != null && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }
}
