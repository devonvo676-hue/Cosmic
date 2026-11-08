package com.cosmicabilities.cutscene;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.fx.Fx;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upgrade cutscene: the camera leaves the player's body and slowly orbits them while their ability's emblem rises,
 * spins and erupts into light. The player is restored exactly where they were afterwards (even after a crash/logout).
 */
public class CutsceneManager implements Listener {
    private static final int LENGTH = 110;

    private record Session(ArmorStand camera, ItemDisplay emblem, GameMode mode, Location origin) {}

    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final NamespacedKey restoreKey;

    public CutsceneManager(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.restoreKey = new NamespacedKey(plugin, "cutscene_restore");
    }

    public boolean isPlaying(UUID id) {
        return sessions.containsKey(id);
    }

    public void playUpgrade(Player p, AbilityType type, int tier) {
        if (sessions.containsKey(p.getUniqueId())) return;
        boolean camera = plugin.getConfig().getBoolean("upgrades.cutscene-camera", true);
        Location origin = p.getLocation().clone();
        GameMode originalMode = p.getGameMode();
        World w = origin.getWorld();
        Location focus = origin.clone().add(0, 1.2, 0);

        ArmorStand cam = null;
        if (camera) {
            cam = w.spawn(cameraPos(focus, 0), ArmorStand.class, a -> {
                a.setVisible(false);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setSilent(true);
                a.setMarker(true);
                a.setPersistent(false);
            });
            // remember how to restore them in case the server stops mid-cutscene
            p.getPersistentDataContainer().set(restoreKey, PersistentDataType.STRING, serialize(origin, p.getGameMode()));
            p.setGameMode(GameMode.SPECTATOR);
            p.setSpectatorTarget(cam);
        }
        ItemDisplay emblem = w.spawn(origin.clone().add(0, 0.3, 0), ItemDisplay.class, d -> {
            d.setItemStack(icon(type));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
        });
        Session session = new Session(cam, emblem, originalMode, origin);
        sessions.put(p.getUniqueId(), session);
        int rgb = type.rgb();
        int pct = (int) Math.round(plugin.getConfig().getDouble("upgrades.cooldown-reduction", 0.3) * 100);

        w.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.6f);
        p.playSound(origin, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.4f);

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline()) { cancel(); return; }
                double prog = t / (double) LENGTH;
                // camera: slow orbit that pulls back and rises as the scene builds
                if (session.camera() != null && session.camera().isValid()) {
                    session.camera().teleport(cameraPos(focus, prog));
                    if (p.getSpectatorTarget() == null) p.setSpectatorTarget(session.camera());
                }
                if (t == 2) Fx.animate(emblem, 30, emblemAt(1.3f, 1.6f, t), 1);
                if (t < 40) {
                    // threads of light converge on the player
                    for (int i = 0; i < 5; i++) {
                        double a = i * 1.2566 + t * 0.15;
                        double r = 3.5 * (1 - t / 40.0) + 0.3;
                        w.spawnParticle(Particle.DUST, origin.getX() + Math.cos(a) * r, origin.getY() + 0.2 + t * 0.04,
                                origin.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, Fx.dust(rgb, 1.3f));
                    }
                    if (t % 8 == 0) w.playSound(origin, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.6f + t * 0.025f);
                }
                if (t >= 10 && t < 60 && t % 6 == 0) {
                    Fx.animate(emblem, 6, emblemAt(1.4f + t * 0.01f, 1.6f + (t - 10) * 0.012f, t), 1);
                }
                if (t >= 20 && t < 60 && t % 4 == 0) {
                    Fx.ring(w, origin.clone().add(0, 0.1 + (t - 20) * 0.05, 0), 1.2 + Math.sin(t * 0.2) * 0.3, 20, t, Fx.dust(rgb, 1.1f));
                }
                if (t == 60) {
                    // climax
                    Location peak = origin.clone().add(0, 2.2, 0);
                    Fx.animate(emblem, 4, emblemAt(2.4f, 2.2f, t), 1);
                    Fx.burst(w, peak, Particle.END_ROD, 70, 0.4);
                    Fx.burst(w, peak, Particle.FIREWORK, 40, 0.3);
                    w.spawnParticle(Particle.DUST, peak, 60, 1.2, 1.2, 1.2, 0, Fx.dust(rgb, 2.0f));
                    w.playSound(origin, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    w.playSound(origin, Sound.ITEM_TOTEM_USE, 0.7f, 1.3f);
                    w.strikeLightningEffect(origin.clone().add(3, 0, 0));
                    p.showTitle(Title.title(
                            plugin.mm("<" + type.color + "><bold>UPGRADED"),
                            plugin.mm("<white>" + type.displayName + " <gray>- cooldown <green>-" + pct + "%"),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(600))));
                }
                if (t > 60 && t % 3 == 0) {
                    Fx.ring(w, origin.clone().add(0, 0.15, 0), (t - 60) * 0.12, 30, 0, Fx.dust(rgb, 1.2f));
                }
                if (t == 85) Fx.animate(emblem, 18, emblemAt(0.01f, 0.6f, t), 1);
                if (t >= LENGTH) {
                    finish(p);
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Spins the emblem around Y at a given scale and height above the origin. */
    private static Transformation emblemAt(float scale, float height, int t) {
        return new Transformation(new Vector3f(0, height, 0), new Quaternionf().rotateY(t * 0.18f),
                new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private static Location cameraPos(Location focus, double prog) {
        double angle = -0.6 + prog * 2.4;
        double dist = 3.2 + prog * 2.2;
        double height = 0.4 + prog * 1.6;
        Location cam = focus.clone().add(Math.cos(angle) * dist, height, Math.sin(angle) * dist);
        Vector look = focus.toVector().add(new Vector(0, 0.6, 0)).subtract(cam.toVector());
        cam.setDirection(look);
        return cam;
    }

    private ItemStack icon(AbilityType type) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", type.id()));
        it.setItemMeta(meta);
        return it;
    }

    private void finish(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        if (s.emblem().isValid()) s.emblem().remove();
        if (s.camera() != null) {
            p.setSpectatorTarget(null);
            if (s.camera().isValid()) s.camera().remove();
            p.teleport(s.origin());
            p.setGameMode(s.mode() == null ? GameMode.SURVIVAL : s.mode());
            p.getPersistentDataContainer().remove(restoreKey);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 4, false, false, false));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.6f);
    }

    private static String serialize(Location l, GameMode mode) {
        return mode.name() + ";" + l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    /** If the player logged out or the server stopped mid-cutscene, put them back. */
    private void restoreFromData(Player p) {
        String raw = p.getPersistentDataContainer().get(restoreKey, PersistentDataType.STRING);
        if (raw == null) return;
        p.getPersistentDataContainer().remove(restoreKey);
        try {
            String[] s = raw.split(";");
            World w = Bukkit.getWorld(s[1]);
            if (w != null) {
                p.teleport(new Location(w, Double.parseDouble(s[2]), Double.parseDouble(s[3]), Double.parseDouble(s[4]),
                        Float.parseFloat(s[5]), Float.parseFloat(s[6])));
            }
            p.setGameMode(GameMode.valueOf(s[0]));
        } catch (RuntimeException e) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        restoreFromData(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        finish(event.getPlayer());
    }

    /** Pressing sneak would normally leave the camera; keep them locked in. */
    @EventHandler(ignoreCancelled = true)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    public void shutdown() {
        for (UUID id : sessions.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) finish(p);
        }
    }
}
