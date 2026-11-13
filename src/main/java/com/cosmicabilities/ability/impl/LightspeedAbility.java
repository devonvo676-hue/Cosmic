package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.admin.Suits;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2tou only. First cast: lightning strikes and he suits up in the Lightspeed suit, then runs at incredible speed
 * with a lightning trail. Cast again while suited: time freezes for everyone else (the world stops, other players
 * can't move or act) while he keeps moving.
 */
public class LightspeedAbility extends CosmicAbility implements Listener {
    public static final String SUIT = "lightspeed";
    private final NamespacedKey speedKey, stepKey, frozenKey, frozenJumpKey;
    private final Map<UUID, Long> runningUntil = new ConcurrentHashMap<>();
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    private long lastFreeze;
    private boolean timeFrozen;

    public LightspeedAbility(CosmicAbilitiesPlugin plugin) {
        super(plugin, AbilityType.LIGHTSPEED);
        speedKey = new NamespacedKey(plugin, "lightspeed_speed");
        stepKey = new NamespacedKey(plugin, "lightspeed_step");
        frozenKey = new NamespacedKey(plugin, "timefreeze_still");
        frozenJumpKey = new NamespacedKey(plugin, "timefreeze_nojump");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private String cfg(String k) { return "ability-settings." + type.id() + "." + k; }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        if (runningUntil.containsKey(player.getUniqueId())) return freezeTime(player, slot);
        suitUp(player, slot);
        return true;
    }

    // ------------------------------------------------------------------ suit up + run

    private void suitUp(Player p, AbilitySlot slot) {
        World w = p.getWorld();
        boolean over = bonus(slot);
        int seconds = (int) plugin.getConfig().getDouble(cfg("run-seconds"), 45);
        runningUntil.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L + 1500);
        w.strikeLightningEffect(p.getLocation());
        w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.4f);
        // 1.5 s suit-up: a spiral of lightning wraps him from the feet up, each piece snaps on
        Suits.Piece[] order = {Suits.Piece.BOOTS, Suits.Piece.LEGS, Suits.Piece.CHEST, Suits.Piece.HELMET};
        every(32, t -> {
            if (!p.isOnline()) return false;
            Location base = p.getLocation();
            double h = Math.min(2.0, t / 30.0 * 2.1);
            for (int i = 0; i < 3; i++) {
                double a = t * 0.9 + i * Math.PI * 2 / 3;
                Location at = base.clone().add(Math.cos(a) * 0.7, h, Math.sin(a) * 0.7);
                w.spawnParticle(Particle.ELECTRIC_SPARK, at, 4, 0.05, 0.05, 0.05, 0.05);
                w.spawnParticle(Particle.DUST, at, 2, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0xffe14d), 1.4f));
            }
            if (t % 8 == 0 && t / 8 < order.length) {
                plugin.suits().equip(p, SUIT, "<#ffe14d><bold>Lightspeed Suit", order[t / 8]);
                w.playSound(base, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 1.6f);
                w.playSound(base, Sound.ENTITY_BEE_STING, 0.6f, 2f);
            }
            if (t == 31) {
                w.strikeLightningEffect(base);
                Fx.safe(w, Particle.FLASH, base.clone().add(0, 1, 0), 1, 0, 0, 0, 0, Color.WHITE);
                p.showTitle(Title.title(plugin.mm("<gradient:#ffe14d:#ffffff><bold>LIGHTSPEED"), plugin.mm("<gray>cast again to freeze time"),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(400))));
                startRunning(p, over, seconds);
            }
            return true;
        });
    }

    private void startRunning(Player p, boolean over, int seconds) {
        UUID id = p.getUniqueId();
        double speed = plugin.getConfig().getDouble(cfg("speed-multiplier"), over ? 5 : 4);
        modifier(p, Attribute.MOVEMENT_SPEED, speedKey, speed - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        modifier(p, Attribute.STEP_HEIGHT, stepKey, 1.0, AttributeModifier.Operation.ADD_NUMBER);
        status().addFallImmunity(id);
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, seconds * 20, 1, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, seconds * 20, 2, false, false, false));
        World w = p.getWorld();
        Location[] last = {p.getLocation()};
        every(seconds * 20 + 1, t -> {
            if (!p.isOnline() || p.isDead() || !runningUntil.containsKey(id)) { stopRunning(p); return false; }
            Location now = p.getLocation();
            double moved = now.distance(last[0]);
            if (moved > 0.15) {
                // lightning trail behind him, crackling arcs
                Vector step = now.toVector().subtract(last[0].toVector());
                for (double k = 0; k < 1; k += 0.25) {
                    Location tr = last[0].clone().add(step.clone().multiply(k)).add(0, 0.9 + Math.sin((t + k) * 3) * 0.35, 0);
                    w.spawnParticle(Particle.DUST, tr, 2, 0.15, 0.25, 0.15, 0, new Particle.DustOptions(Color.fromRGB(0xffe14d), 1.3f));
                    w.spawnParticle(Particle.DUST, tr, 1, 0.1, 0.2, 0.1, 0, new Particle.DustOptions(Color.fromRGB(0xfff7c2), 0.9f));
                }
                w.spawnParticle(Particle.ELECTRIC_SPARK, now.clone().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.1);
                if (t % 6 == 0) w.playSound(now, Sound.BLOCK_BEEHIVE_WORK, 0.4f, 2f);
                if (t % 10 == 0 && moved > 0.6) w.playSound(now, Sound.ENTITY_BREEZE_WIND_BURST, 0.5f, 1.6f);
            }
            last[0] = now;
            if (System.currentTimeMillis() >= runningUntil.getOrDefault(id, 0L)) { stopRunning(p); return false; }
            return true;
        });
    }

    private void stopRunning(Player p) {
        UUID id = p.getUniqueId();
        if (runningUntil.remove(id) == null) return;
        modifier(p, Attribute.MOVEMENT_SPEED, speedKey, 0, null);
        modifier(p, Attribute.STEP_HEIGHT, stepKey, 0, null);
        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
        p.removePotionEffect(PotionEffectType.HASTE);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> status().removeFallImmunity(id), 60L);
        if (p.isOnline()) {
            plugin.suits().remove(p);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.2);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.6f);
        }
    }

    private static void modifier(Player p, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(key);
        if (old != null) inst.removeModifier(old);
        if (op != null) inst.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    // ------------------------------------------------------------------ freeze time

    private boolean freezeTime(Player p, AbilitySlot slot) {
        long now = System.currentTimeMillis();
        double cd = plugin.getConfig().getDouble(cfg("freeze-cooldown-seconds"), 30);
        if (timeFrozen || now - lastFreeze < cd * 1000) {
            plugin.engine().notify(p, plugin.mm("<#ffe14d>Time freeze recharging: <white>"
                    + Math.max(0, Math.round(cd - (now - lastFreeze) / 1000.0)) + "s"));
            return false;
        }
        lastFreeze = now;
        timeFrozen = true;
        int seconds = (int) plugin.getConfig().getDouble(cfg("freeze-seconds"), 8) + (bonus(slot) ? 4 : 0);
        World w = p.getWorld();
        Bukkit.getServerTickManager().setFrozen(true);
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.playSound(o.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.5f);
            o.playSound(o.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
            if (o.equals(p)) continue;
            frozenPlayers.add(o.getUniqueId());
            modifier(o, Attribute.MOVEMENT_SPEED, frozenKey, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            modifier(o, Attribute.JUMP_STRENGTH, frozenJumpKey, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            o.setVelocity(new Vector());
        }
        p.showTitle(Title.title(plugin.mm("<#9bd7ff><bold>TIME FROZEN"), plugin.mm("<gray>" + seconds + " seconds"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(300))));
        Fx.wave(p.getLocation().add(0, 0.2, 0), "ring_cyan", 1f, 60f, 20);
        Fx.wave(p.getLocation().add(0, 1.2, 0), "ring_white", 1f, 40f, 14);
        every(seconds * 20, t -> {
            // frozen-time shimmer around everyone else
            for (UUID id : frozenPlayers) {
                Player o = Bukkit.getPlayer(id);
                if (o != null && t % 10 == 0) o.getWorld().spawnParticle(Particle.DUST, o.getLocation().add(0, 1, 0), 8, 0.4, 0.6, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(0x9bd7ff), 1f));
            }
            if (t % 20 == 0) for (Player o : Bukkit.getOnlinePlayers()) o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.5f);
            if (t >= seconds * 20 - 1) { unfreeze(); return false; }
            return true;
        });
        return true;
    }

    public void unfreeze() {
        if (!timeFrozen) return;
        timeFrozen = false;
        Bukkit.getServerTickManager().setFrozen(false);
        for (UUID id : frozenPlayers) {
            Player o = Bukkit.getPlayer(id);
            if (o == null) continue;
            modifier(o, Attribute.MOVEMENT_SPEED, frozenKey, 0, null);
            modifier(o, Attribute.JUMP_STRENGTH, frozenJumpKey, 0, null);
            o.playSound(o.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        }
        frozenPlayers.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFrozenMove(PlayerMoveEvent event) {
        if (!frozenPlayers.contains(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFrozenAct(PlayerInteractEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFrozenHit(EntityDamageByEntityEvent event) {
        if (frozenPlayers.contains(event.getDamager().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && runningUntil.containsKey(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        stopRunning(p);
        if (frozenPlayers.remove(p.getUniqueId())) {
            modifier(p, Attribute.MOVEMENT_SPEED, frozenKey, 0, null);
            modifier(p, Attribute.JUMP_STRENGTH, frozenJumpKey, 0, null);
        }
    }

    public void shutdown() {
        unfreeze();
        for (UUID id : runningUntil.keySet().toArray(new UUID[0])) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stopRunning(p);
        }
    }
}
