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
            d.addScoreboardTag(Fx.TAG);
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

    // ------------------------------------------------------------------ boss entrance

    /**
     * Boss entrance, shown to EVERY player online (they're brought to the boss's world for it and sent back after):
     * each boss arrives its own way - the Void Emperor descends from a black hole, the Solar Titan erupts out of a
     * lava ring, the Frost Leviathan bursts through a ring of ice, the Nebula Drake swoops down from the sky and the
     * Cosmic Destroyer crashes down like a meteor. The boss can't move, attack or be hurt until it's over.
     */
    public void playBossIntro(org.bukkit.entity.LivingEntity boss, com.cosmicabilities.boss.BossType type) {
        int length = Math.max(60, (int) (plugin.getConfig().getDouble("bosses.cutscene-seconds", 7) * 20));
        Location ground = boss.getLocation().clone();
        World w = ground.getWorld();
        double height = Math.max(2.0, boss.getHeight());
        double width = Math.max(1.5, boss.getWidth());
        boss.setAI(false);
        boss.setInvulnerable(true);
        boss.setGravity(false);
        float yaw = ground.getYaw();
        double facing = Math.toRadians(yaw + 90);
        Vector front = new Vector(Math.cos(facing), 0, Math.sin(facing));
        java.util.List<Player> viewers = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sessions.containsKey(p.getUniqueId()) || p.isDead()) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (plugin.afk().isAfk(p.getUniqueId())) continue;
            viewers.add(p);
        }
        Location focus0 = ground.clone().add(0, height * 0.6, 0);
        ArmorStand cam = viewers.isEmpty() ? null : w.spawn(introCam(type, focus0, front, 0, height, width), ArmorStand.class, a -> {
            a.setVisible(false);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setMarker(true);
            a.setPersistent(false);
        });
        for (Player p : viewers) {
            p.getPersistentDataContainer().set(restoreKey, PersistentDataType.STRING, serialize(p.getLocation(), p.getGameMode()));
            sessions.put(p.getUniqueId(), new Session(cam, null, p.getGameMode(), p.getLocation().clone()));
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(cam.getLocation());
            p.setSpectatorTarget(cam);
            p.hideEntity(plugin, boss);   // spectators see invisible mobs as see-through ghosts: hide the base mob
        }
        int rgb = Integer.parseInt(type.color.substring(1), 16);
        java.util.List<org.bukkit.entity.Display> props = new java.util.ArrayList<>();
        // per-boss props
        switch (type) {
            case VOID_EMPEROR -> {
                org.bukkit.entity.ItemDisplay hole = Fx.sprite(ground.clone().add(0, height + 14, 0), "blackhole", 0.1f, org.bukkit.entity.Display.Billboard.CENTER);
                Fx.animate(hole, 30, Fx.sized((float) height * 3, (float) height * 3, 2f), 1);
                props.add(hole);
            }
            case FROST_LEVIATHAN -> {
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    Location at = ground.clone().add(Math.cos(a) * width * 1.3, -0.5, Math.sin(a) * width * 1.3);
                    org.bukkit.entity.BlockDisplay spike = Fx.block(at, i % 2 == 0 ? Material.PACKED_ICE : Material.BLUE_ICE,
                            new org.joml.Vector3f(0.6f, 0.01f, 0.6f), new org.joml.Quaternionf(), true);
                    props.add(spike);
                }
            }
            default -> { }
        }
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                double prog = t / (double) length;
                Location bossAt = introBoss(type, ground, front, prog, height, width);
                if (boss.isValid()) boss.teleport(bossAt);
                Location focus = bossAt.clone().add(0, height * 0.6, 0);
                if (cam != null && cam.isValid()) {
                    cam.teleport(introCam(type, focus, front, prog, height, width));
                    for (Player p : viewers) if (p.isOnline() && sessions.containsKey(p.getUniqueId()) && p.getSpectatorTarget() == null) {
                        if (!p.getWorld().equals(cam.getWorld())) p.teleport(cam.getLocation());
                        p.setSpectatorTarget(cam);
                    }
                }
                introEffects(type, w, ground, bossAt, front, t, length, height, width, rgb, props);
                if (t == (int) (length * 0.62)) {
                    w.playSound(bossAt, roar(type), 4f, 0.6f);
                    w.playSound(ground, Sound.ENTITY_WITHER_SPAWN, 2f, 0.7f);
                    for (int r = 1; r <= 8; r++) Fx.ring(w, ground.clone().add(0, 0.3, 0), r * width * 0.6, 20 + r * 8, r, Fx.dust(rgb, 2.2f));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(Title.title(plugin.mm(type.colored()), plugin.mm(subtitle(type)),
                                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2600), Duration.ofMillis(700))));
                        if (!p.getWorld().equals(w) || p.getLocation().distanceSquared(ground) > 150 * 150) p.playSound(p.getLocation(), roar(type), 1f, 0.6f);
                    }
                }
                if (t >= length || !boss.isValid()) {
                    cancel();
                    for (Player p : viewers) if (p.isOnline()) { finish(p); p.showEntity(plugin, boss); }
                    if (cam != null && cam.isValid()) cam.remove();
                    for (org.bukkit.entity.Display d : props) if (d.isValid()) d.remove();
                    if (boss.isValid()) {
                        boss.teleport(ground);
                        boss.setGravity(true);
                        boss.setInvulnerable(false);
                        boss.setAI(true);
                    }
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Sound roar(com.cosmicabilities.boss.BossType type) {
        return switch (type) {
            case VOID_EMPEROR -> Sound.ENTITY_WITHER_AMBIENT;
            case SOLAR_TITAN -> Sound.ENTITY_RAVAGER_ROAR;
            case FROST_LEVIATHAN -> Sound.ENTITY_ELDER_GUARDIAN_CURSE;
            case NEBULA_SERPENT -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            case COSMIC_DESTROYER -> Sound.ENTITY_WARDEN_ROAR;
        };
    }

    private static String subtitle(com.cosmicabilities.boss.BossType type) {
        return switch (type) {
            case VOID_EMPEROR -> "<gray>descends from the void";
            case SOLAR_TITAN -> "<gray>erupts from the earth";
            case FROST_LEVIATHAN -> "<gray>breaks through the ice";
            case NEBULA_SERPENT -> "<gray>darkens the sky";
            case COSMIC_DESTROYER -> "<gray>has crashed into the world";
        };
    }

    private static double ease(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    /** Where the boss is at this point of its entrance. */
    private static Location introBoss(com.cosmicabilities.boss.BossType type, Location ground, Vector front, double prog, double height, double width) {
        Location at = ground.clone();
        switch (type) {
            case VOID_EMPEROR -> at.add(0, (height + 12) * (1 - ease(prog / 0.6)), 0);                     // descends from above
            case SOLAR_TITAN, FROST_LEVIATHAN -> at.subtract(0, (height + 1) * (1 - ease((prog - 0.15) / 0.45)), 0);   // rises out of the ground
            case NEBULA_SERPENT -> {                                                                      // spirals down from the sky
                double k = 1 - ease(prog / 0.62);
                double a = k * Math.PI * 1.5;
                at.add(Math.cos(a) * 30 * k, 26 * k, Math.sin(a) * 30 * k);
                at.setYaw((float) Math.toDegrees(Math.atan2(-(-Math.sin(a)), Math.cos(a))) + 90f);
            }
            case COSMIC_DESTROYER -> {                                                                    // falls like a meteor
                double k = prog < 0.45 ? 1 : 1 - Math.min(1, (prog - 0.45) / 0.12);
                at.add(front.clone().multiply(-25 * k)).add(0, 60 * k, 0);
            }
        }
        return at;
    }

    /** The camera for each boss's entrance. */
    private static Location introCam(com.cosmicabilities.boss.BossType type, Location focus, Vector front, double prog, double height, double width) {
        double e = ease(prog);
        Vector side = new Vector(-front.getZ(), 0, front.getX());
        Location cam;
        switch (type) {
            case VOID_EMPEROR -> cam = focus.clone().add(front.clone().multiply(height * 3.2 + 6 - e * height * 1.5)).add(side.clone().multiply(3 - e * 3))
                    .add(0, -height * 0.4 + e * height * 0.3, 0);
            case NEBULA_SERPENT -> cam = focus.clone().add(front.clone().multiply(height * 2.6 + 10)).add(side.clone().multiply(8 - e * 8)).add(0, 2 - e * 1.5, 0);
            case COSMIC_DESTROYER -> cam = focus.clone().add(front.clone().multiply(height * 4 + 12 - e * 6)).add(side.clone().multiply(10)).add(0, 3, 0);
            default -> {
                double angle = Math.atan2(front.getZ(), front.getX()) + 1.2 - e * 1.2;
                double dist = height * 3 + 8 - e * (height * 1.6 + 4);
                cam = focus.clone().add(Math.cos(angle) * dist, -height * 0.3 + e * height * 0.4 + 1, Math.sin(angle) * dist);
            }
        }
        cam.setDirection(focus.toVector().subtract(cam.toVector()));
        return cam;
    }

    private void introEffects(com.cosmicabilities.boss.BossType type, World w, Location ground, Location bossAt, Vector front, int t, int length,
                              double height, double width, int rgb, java.util.List<org.bukkit.entity.Display> props) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        switch (type) {
            case VOID_EMPEROR -> {
                if (t % 2 == 0) w.spawnParticle(Particle.REVERSE_PORTAL, bossAt.clone().add(0, height / 2, 0), 30, width, height / 2, width, 0.2);
                if (t % 15 == 5 && t < length * 0.7) {
                    double a = r.nextDouble(Math.PI * 2);
                    w.strikeLightningEffect(ground.clone().add(Math.cos(a) * width * 3, 0, Math.sin(a) * width * 3));
                }
                if (t % 10 == 0) Fx.wave(ground.clone().add(0, 0.1, 0), "ring_purple", 1f, (float) width * 6, 12);
                if (t == (int) (length * 0.62)) for (org.bukkit.entity.Display d : props) Fx.animate(d, 12, Fx.sized(0.01f, 0.01f, 6f), 0);
            }
            case SOLAR_TITAN -> {
                if (t < length * 0.75) for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI / 5 + t * 0.05;
                    Location c = ground.clone().add(Math.cos(a) * width * 1.4, 0.2, Math.sin(a) * width * 1.4);
                    w.spawnParticle(Particle.FLAME, c, 3, 0.2, 0.6, 0.2, 0.05);
                    if (t % 4 == 0) w.spawnParticle(Particle.LAVA, c, 1, 0.2, 0.2, 0.2, 0);
                }
                if (t % 6 == 0) w.playSound(ground, Sound.BLOCK_LAVA_POP, 2f, 0.6f);
                if (t == 4) Fx.flat(ground.clone().add(0, 0.05, 0), "scorch", (float) width * 3.5f, 0);
                if (t == (int) (length * 0.45)) {
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, ground, 3, width, 0.5, width, 0);
                    w.spawnParticle(Particle.FLAME, ground, 200, width, height / 2, width, 0.25);
                    w.playSound(ground, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
                }
                if (t < length * 0.6) w.spawnParticle(Particle.BLOCK, ground.clone().add(0, 0.2, 0), 15, width * 0.6, 0.2, width * 0.6, 0.2,
                        ground.clone().subtract(0, 1, 0).getBlock().getBlockData());
            }
            case FROST_LEVIATHAN -> {
                w.spawnParticle(Particle.SNOWFLAKE, ground.clone().add(0, height, 0), 25, width * 3, height, width * 3, 0.05);
                if (t == 6) for (int i = 0; i < props.size(); i++) {
                    float hh = (float) (height * (0.5 + r.nextDouble() * 0.5));
                    Fx.animate(props.get(i), 8, Fx.centred(0.7f, hh, 0.7f, new org.joml.Quaternionf().rotateZ((float) (r.nextDouble() - 0.5) * 0.6f)), i);
                }
                if (t % 8 == 0) w.playSound(ground, Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
                if (t == (int) (length * 0.45)) {
                    w.spawnParticle(Particle.BLOCK, ground.clone().add(0, 1, 0), 200, width, height / 2, width, 0.3, Material.PACKED_ICE.createBlockData());
                    w.playSound(ground, Sound.BLOCK_GLASS_BREAK, 3f, 0.4f);
                    for (org.bukkit.entity.Display d : props) Fx.animate(d, 6, Fx.centred(0.01f, 0.01f, 0.01f, new org.joml.Quaternionf()), 0);
                }
            }
            case NEBULA_SERPENT -> {
                w.spawnParticle(Particle.DUST, bossAt.clone().add(0, height / 2, 0), 20, width, height / 3, width, 0, Fx.dust(rgb, 2.5f));
                if (t % 5 == 0) w.playSound(bossAt, Sound.ENTITY_ENDER_DRAGON_FLAP, 4f, 0.7f);
                if (t == (int) (length * 0.62)) {
                    w.spawnParticle(Particle.CLOUD, ground, 150, width * 2, 0.5, width * 2, 0.3);
                    Fx.wave(ground.clone().add(0, 0.1, 0), "ring_magenta", 1f, (float) width * 8, 14);
                }
            }
            case COSMIC_DESTROYER -> {
                double prog = t / (double) length;
                if (prog < 0.57) {
                    w.spawnParticle(Particle.FLAME, bossAt.clone().add(0, height / 2, 0), 40, width, height / 2, width, 0.15);
                    w.spawnParticle(Particle.LARGE_SMOKE, bossAt.clone().add(0, height, 0), 20, width, height / 2, width, 0.05);
                    if (t % 6 == 0) w.playSound(bossAt, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 4f, 0.4f);
                    if (prog < 0.45) Fx.ring(w, ground.clone().add(0, 0.2, 0), width * 2.5, 40, t * 0.1, Fx.dust(0xff2a2a, 2f));
                }
                if (t == (int) (length * 0.57)) {
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, ground, 8, width * 1.5, 1, width * 1.5, 0);
                    w.spawnParticle(Particle.FLAME, ground, 300, width * 2, 1, width * 2, 0.3);
                    Fx.wave(ground.clone().add(0, 0.1, 0), "ring_orange", 1f, (float) width * 10, 12);
                    Fx.flat(ground.clone().add(0, 0.04, 0), "scorch", (float) width * 5, 1);
                    w.playSound(ground, Sound.ENTITY_GENERIC_EXPLODE, 5f, 0.4f);
                    w.playSound(ground, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 5f, 0.5f);
                    for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(ground) < 80 * 80) p.sendHurtAnimation((float) Math.random() * 360);
                }
            }
        }
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
        if (s.emblem() != null && s.emblem().isValid()) s.emblem().remove();
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
