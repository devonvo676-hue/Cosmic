package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.fx.Fx;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v2tou's fighter jets. Right-click the jet item and you're strapped into the cockpit with a third-person chase
 * camera behind the plane.
 *   W / S       climb / dive              A / D   turn
 *   SPRINT      afterburner              SPACE   drop a bomb
 *   SNEAK       eject - you're thrown out with a parachute and the empty jet blows up 3 seconds later
 * Flying into anything (ground, buildings, mobs, players) makes the jet explode and blow up the area it hit.
 * Mk I bombs explode on impact. Mk II bombs stick into the ground and count down 5 seconds before they go off.
 */
public class FighterJet implements Listener {
    private static final float MODEL_SCALE = 3f;
    /** Where the pilot's head sits inside the model (blocks, before rotation; nose is +Z). */
    private static final Vector3f COCKPIT = new Vector3f(0, 0.4f, 0.3f);

    private static final class Jet {
        int mk;
        ItemDisplay body, pilotHead;
        ArmorStand cam;
        BukkitRunnable task;
        GameMode mode;
        double speed = 1.2;
        float yaw, pitch, roll;
        boolean fwd, back, left, right, boost;
        long lastBomb;
        int ejectedAt = -1;   // tick the pilot ejected, -1 while someone is flying it
        int t;
        Location camAt;
    }

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey key, key2, restoreKey;
    private final Map<UUID, Jet> jets = new ConcurrentHashMap<>();          // pilot -> jet
    private final Set<Jet> pilotless = ConcurrentHashMap.newKeySet();     // jets flying on after an ejection
    private final Set<UUID> safe = ConcurrentHashMap.newKeySet();         // no fall / blast damage (parachuting)

    public FighterJet(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "fighter_jet");
        this.key2 = new NamespacedKey(plugin, "fighter_jet_mk2");
        this.restoreKey = new NamespacedKey(plugin, "jet_restore");
    }

    public ItemStack create() { return item(1); }
    public ItemStack createMk2() { return item(2); }

    private ItemStack item(int mk) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(plugin.mm(mk == 2 ? "<!italic><gradient:#2b2d42:#ef233c><bold>FIGHTER JET MK II"
                : "<!italic><gradient:#9aa5b1:#00bbf9><bold>FIGHTER JET"));
        m.lore(List.of(plugin.mm("<!italic><gray>Right click: climb into the cockpit"),
                plugin.mm("<!italic><gray>W/S climb/dive, A/D turn, SPRINT afterburner"),
                plugin.mm("<!italic><gray>SPACE: drop a bomb, SNEAK: eject"),
                plugin.mm(mk == 2 ? "<!italic><red>Bombs stick in the ground and blow after 5s" : "<!italic><gold>Bombs explode on impact"),
                plugin.mm("<!italic><dark_red>v2tou only")));
        m.setItemModel(new NamespacedKey("cosmic", mk == 2 ? "fighter_jet2_item" : "fighter_jet_item"));
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(mk == 2 ? key2 : key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private int mkOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        return pdc.has(key2, PersistentDataType.BYTE) ? 2 : pdc.has(key, PersistentDataType.BYTE) ? 1 : 0;
    }

    public boolean isJet(ItemStack it) { return mkOf(it) != 0; }

    public boolean flying(UUID id) { return jets.containsKey(id); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        int mk = mkOf(p.getInventory().getItemInMainHand());
        if (mk == 0) return;
        event.setCancelled(true);
        if (!Owner.allowed(plugin, p)) {
            p.getInventory().setItemInMainHand(null);
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>You can't use that."));
            return;
        }
        if (jets.containsKey(p.getUniqueId()) || p.isInsideVehicle() || p.getGameMode() == GameMode.SPECTATOR) return;
        launch(p, mk);
    }

    private static ItemStack model(String name) {
        ItemStack model = new ItemStack(Material.PAPER);
        ItemMeta mm = model.getItemMeta();
        mm.setItemModel(new NamespacedKey("cosmic", name));
        model.setItemMeta(mm);
        return model;
    }

    // ------------------------------------------------------------------ flying

    private void launch(Player p, int mk) {
        World w = p.getWorld();
        Location at = p.getLocation().add(0, 1.2, 0);
        Jet jet = new Jet();
        jet.mk = mk;
        jet.yaw = at.getYaw();
        jet.pitch = -10;
        at.setYaw(0);
        at.setPitch(0);
        jet.body = w.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(model(mk == 2 ? "boss_fx_jet2" : "boss_fx_jet"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.addScoreboardTag(Fx.KEEP);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setViewRange(8f);
            d.setDisplayWidth(6);
            d.setDisplayHeight(4);
        });
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta sm) { sm.setOwningPlayer(p); head.setItemMeta(sm); }
        jet.pilotHead = w.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(head);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setViewRange(4f);
        });
        jet.camAt = camTarget(jet, at);
        jet.cam = w.spawn(jet.camAt, ArmorStand.class, a -> {
            a.setVisible(false);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setMarker(true);
            a.setPersistent(false);
            a.addScoreboardTag(Fx.TAG);
        });
        jet.mode = p.getGameMode();
        // if the server stops mid-flight, put them back on the ground in their own game mode
        p.getPersistentDataContainer().set(restoreKey, PersistentDataType.STRING, jet.mode.name());
        jets.put(p.getUniqueId(), jet);
        p.setGameMode(GameMode.SPECTATOR);
        p.setSpectatorTarget(jet.cam);
        w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.5f);
        w.playSound(at, Sound.ITEM_ELYTRA_FLYING, 1.5f, 1.2f);
        w.playSound(at, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 0.6f);   // canopy shuts
        jet.task = new BukkitRunnable() {
            @Override
            public void run() { tick(p, jet); }
        };
        jet.task.runTaskTimer(plugin, 1L, 1L);
    }

    private Location camTarget(Jet jet, Location at) {
        double yr = Math.toRadians(jet.yaw);
        Vector flat = new Vector(-Math.sin(yr), 0, Math.cos(yr));
        double dist = plugin.getConfig().getDouble("jet.camera-distance", 11);
        Location c = at.clone().subtract(flat.multiply(dist)).add(0, dist * 0.32 - jet.pitch / 90.0 * dist * 0.4, 0);
        c.setYaw(jet.yaw);
        c.setPitch(12 + jet.pitch * 0.55f);
        return c;
    }

    private Vector forward(Jet jet) {
        double yr = Math.toRadians(jet.yaw), pr = Math.toRadians(jet.pitch);
        return new Vector(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
    }

    private Quaternionf rotation(Jet jet) {
        return new Quaternionf().rotateY((float) Math.toRadians(-jet.yaw))
                .rotateX((float) Math.toRadians(jet.pitch)).rotateZ((float) Math.toRadians(jet.roll));
    }

    private void tick(Player p, Jet jet) {
        boolean piloted = jet.ejectedAt < 0;
        if (piloted && (!p.isOnline() || p.isDead())) { abandon(p, jet); return; }
        if (!jet.body.isValid()) { cleanup(jet); return; }
        jet.t++;
        World w = jet.body.getWorld();
        double max = plugin.getConfig().getDouble("jet.max-speed", 3.0);
        double cruise = Math.min(max, plugin.getConfig().getDouble("jet.cruise-speed", 1.6));
        boolean invert = plugin.getConfig().getBoolean("jet.invert-pitch", false);
        if (piloted) {
            // controls
            float turn = (jet.left ? -1 : 0) + (jet.right ? 1 : 0);
            float climb = (jet.fwd ? 1 : 0) - (jet.back ? 1 : 0);
            if (invert) climb = -climb;
            jet.yaw += turn * (float) (4.2 - Math.min(2, jet.speed * 0.5));
            if (climb != 0) jet.pitch = Math.max(-75, Math.min(75, jet.pitch - climb * 2.8f));
            else jet.pitch *= 0.97f;   // levels out by itself
            jet.roll += (turn * 50 - jet.roll) * 0.18f;
            double target = jet.boost ? max : cruise;
            jet.speed += (target - jet.speed) * (jet.boost ? 0.05 : 0.03);
        } else {
            // nobody at the controls: it drifts on, slowly nosing down and rolling over
            jet.pitch = Math.min(70, jet.pitch + 1.2f);
            jet.roll += 3f;
            if (jet.t - jet.ejectedAt >= 60) { crash(jet, jet.body.getLocation(), null); return; }
        }
        if (jet.t < 15) jet.pitch = Math.min(jet.pitch, -15);   // takes off nose-up
        Vector dir = forward(jet);
        Location cur = jet.body.getLocation();
        Location next = cur.clone().add(dir.clone().multiply(jet.speed));
        // flying into anything = boom
        if (jet.t > 6) {
            Location hit = collision(jet, cur, dir, piloted ? p : null);
            if (hit != null) { crash(jet, hit, piloted ? p : null); return; }
        }
        next.setYaw(0);
        next.setPitch(0);
        jet.body.teleport(next, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        Quaternionf rot = rotation(jet);
        jet.body.setInterpolationDelay(0);
        jet.body.setInterpolationDuration(2);
        jet.body.setTransformation(new Transformation(new Vector3f(0, -0.6f, 0), rot,
                new Vector3f(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE), new Quaternionf()));
        if (jet.pilotHead != null && jet.pilotHead.isValid()) {
            Vector3f off = rot.transform(new Vector3f(COCKPIT)).add(0, -0.6f, 0);
            Location h = next.clone().add(off.x, off.y, off.z);
            jet.pilotHead.teleport(h);
            jet.pilotHead.setInterpolationDelay(0);
            jet.pilotHead.setInterpolationDuration(2);
            jet.pilotHead.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(0.85f, 0.85f, 0.85f), new Quaternionf()));
        }
        // chase camera, trailing a little behind so turns feel smooth
        if (piloted && jet.cam != null && jet.cam.isValid()) {
            Location want = camTarget(jet, next);
            Vector d = want.toVector().subtract(jet.camAt.toVector());
            jet.camAt = jet.camAt.clone().add(d.multiply(0.55));
            jet.camAt.setYaw(want.getYaw());
            jet.camAt.setPitch(want.getPitch());
            jet.cam.teleport(jet.camAt);
            if (p.getSpectatorTarget() == null || !p.getSpectatorTarget().equals(jet.cam)) p.setSpectatorTarget(jet.cam);
        }
        // engine exhaust + sound
        Vector back = dir.clone().multiply(-MODEL_SCALE * 1.25);
        Location ex = next.clone().add(back).add(0, -0.55, 0);
        w.spawnParticle(Particle.FLAME, ex, jet.boost ? 10 : 4, 0.12, 0.12, 0.12, 0.02);
        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, ex, 2, 0.1, 0.1, 0.1, 0.01);
        if (jet.speed > 2) w.spawnParticle(Particle.CLOUD, ex, 2, 0.3, 0.1, 0.3, 0.02);
        if (!piloted) w.spawnParticle(Particle.LARGE_SMOKE, next, 6, 0.6, 0.3, 0.6, 0.02);
        if (jet.t % 10 == 0) w.playSound(next, Sound.ITEM_ELYTRA_FLYING, 1.5f, (float) (0.6 + jet.speed * 0.25));
        if (piloted && jet.t % 10 == 0) {
            int alt = next.getBlockY() - w.getHighestBlockYAt(next);
            p.sendActionBar(plugin.mm((jet.boost ? "<gold>AFTERBURNER  " : "<aqua>") + "Speed <white>" + Math.round(jet.speed * 20 * 3.6)
                    + " km/h  <gray>| Alt <white>" + alt + "m  <gray>| <white>W/S <gray>climb/dive <white>A/D <gray>turn <white>SPACE <gray>bomb <white>SNEAK <gray>eject"));
        }
    }

    /** Checks the space the jet is about to fly through: blocks under the nose and wings, and anything alive. */
    private Location collision(Jet jet, Location cur, Vector dir, Player pilot) {
        World w = cur.getWorld();
        Vector side = new Vector(dir.getZ(), 0, -dir.getX());
        if (side.lengthSquared() < 1.0E-4) side = new Vector(1, 0, 0);
        side.normalize();
        double reach = jet.speed + MODEL_SCALE * 1.3;
        for (double d = 0.5; d <= reach; d += 0.5) {
            Location c = cur.clone().add(dir.clone().multiply(d));
            if (c.getY() <= w.getMinHeight() + 1) return c;
            if (solid(c.getBlock())) return c;
            if (d > reach - 1.6) for (int s = -1; s <= 1; s += 2) {   // wing tips
                Location tip = c.clone().add(side.clone().multiply(s * MODEL_SCALE * 1.15)).add(dir.clone().multiply(-MODEL_SCALE * 0.6));
                if (solid(tip.getBlock())) return tip;
            }
        }
        Location front = cur.clone().add(dir.clone().multiply(MODEL_SCALE * 0.8));
        for (Entity e : w.getNearbyEntities(front, 2.2, 1.6, 2.2)) {
            if (e instanceof Display || e instanceof ArmorStand || e.equals(pilot)) continue;
            if (e instanceof Player pl && (pl.getGameMode() == GameMode.SPECTATOR || jets.containsKey(pl.getUniqueId()) || safe.contains(pl.getUniqueId()))) continue;
            if (e instanceof LivingEntity || e instanceof org.bukkit.entity.Vehicle || e instanceof org.bukkit.entity.Hanging) return e.getLocation();
        }
        return null;
    }

    private static boolean solid(Block b) {
        Material m = b.getType();
        return m.isSolid() || m == Material.WATER || m == Material.LAVA;
    }

    /** The jet hits something: a big explosion that blows up the area it hit. */
    private void crash(Jet jet, Location at, Player pilot) {
        World w = at.getWorld();
        float power = (float) plugin.getConfig().getDouble("jet.crash-power", 9);
        boolean breakBlocks = plugin.getConfig().getBoolean("jet.break-blocks", true);
        Player source = pilot;
        if (pilot != null) {
            // he gets flung clear of the fireball, parachute out
            Location bail = jet.body.getLocation().clone().add(0, 4, 0);
            exitCockpit(pilot, jet, bail, new Vector(0, 1.4, 0));
            pilot.sendMessage(plugin.mm(plugin.prefix() + "<red>You crashed! <gray>The ejector seat fired just in time."));
        }
        cleanup(jet);
        w.createExplosion(at, power, breakBlocks, breakBlocks, source);
        w.createExplosion(at.clone().add(0, 1.5, 0), power * 0.6f, true, breakBlocks, source);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 4, 1.5, 1, 1.5, 0);
        w.spawnParticle(Particle.FLAME, at, 160, 2.5, 2, 2.5, 0.15);
        w.spawnParticle(Particle.LAVA, at, 40, 2, 1, 2, 0);
        w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, at, 50, 2, 1.5, 2, 0.06);
        Fx.wave(at.clone().add(0, 0.4, 0), "ring_orange", 1.5f, power * 5, 12);
        for (Player pl : w.getPlayers()) {
            double d = pl.getLocation().distance(at);
            if (d > 250) continue;
            float vol = (float) Math.max(0.6, 3 - d / 80);
            pl.playSound(pl.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, vol, 0.45f);
            pl.playSound(pl.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, vol * 0.6f, 0.5f);
        }
        // burning wreckage
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            Block b = w.getHighestBlockAt(at.getBlockX() + rnd.nextInt(-4, 5), at.getBlockZ() + rnd.nextInt(-4, 5)).getRelative(0, 1, 0);
            if (b.isEmpty() && b.getRelative(0, -1, 0).getType().isSolid()) b.setType(Material.FIRE);
        }
    }

    /** Pulls the pilot out of the cockpit, puts their game mode back and opens a parachute. */
    private void exitCockpit(Player p, Jet jet, Location out, Vector fling) {
        jets.remove(p.getUniqueId());
        if (jet.cam != null && jet.cam.isValid()) jet.cam.remove();
        jet.cam = null;
        if (jet.pilotHead != null && jet.pilotHead.isValid()) jet.pilotHead.remove();
        jet.pilotHead = null;
        p.getPersistentDataContainer().remove(restoreKey);
        if (!p.isOnline()) return;
        p.setSpectatorTarget(null);
        p.setGameMode(jet.mode == null || jet.mode == GameMode.SPECTATOR ? GameMode.SURVIVAL : jet.mode);
        out.setYaw(jet.yaw);
        out.setPitch(20);
        p.teleport(out);
        p.setFallDistance(0);
        p.setVelocity(fling);
        parachute(p);
    }

    private void parachute(Player p) {
        UUID id = p.getUniqueId();
        safe.add(id);
        World w = p.getWorld();
        ItemDisplay chute = w.spawn(p.getLocation().add(0, 2.2, 0), ItemDisplay.class, d -> {
            d.setItemStack(model("boss_fx_chute"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(6);
            d.setTransformation(new Transformation(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(0.2f, 0.2f, 0.2f), new Quaternionf()));
        });
        w.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.5f, 0.7f);
        w.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.5f, 0.6f);
        new BukkitRunnable() {
            int t = 0, landed = -1;
            @Override
            public void run() {
                t++;
                if (!p.isOnline() || p.isDead() || !chute.isValid() || t > 20 * 120) { chute.remove(); safe.remove(id); cancel(); return; }
                if (t == 6) {   // canopy pops open
                    chute.setInterpolationDelay(0);
                    chute.setInterpolationDuration(6);
                    chute.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
                    w.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.2f, 1.2f);
                }
                if (t > 6 && landed < 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 10, 0, false, false, false));
                    p.setFallDistance(0);
                }
                Location at = p.getLocation().add(0, 2.4, 0);
                at.setYaw(0); at.setPitch(0);
                chute.teleport(at);
                if (landed < 0 && t > 10 && ((Entity) p).isOnGround()) {
                    landed = t;
                    chute.setInterpolationDelay(0);
                    chute.setInterpolationDuration(14);
                    chute.setTransformation(new Transformation(new Vector3f(0, -1.6f, 0), new Quaternionf().rotateX(1.3f),
                            new Vector3f(1.2f, 0.05f, 1.2f), new Quaternionf()));
                }
                if (landed >= 0 && t - landed > 16) { chute.remove(); safe.remove(id); cancel(); }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** SNEAK: the canopy blows off, the pilot shoots up and out on a parachute, and the empty jet blows up 3 s later. */
    private void eject(Player p) {
        Jet jet = jets.get(p.getUniqueId());
        if (jet == null || jet.ejectedAt >= 0) return;
        jet.ejectedAt = jet.t;
        World w = jet.body.getWorld();
        Location seat = jet.body.getLocation().add(0, 1.2, 0);
        w.playSound(seat, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2f, 0.6f);
        w.playSound(seat, Sound.BLOCK_PISTON_EXTEND, 1.5f, 0.5f);
        w.spawnParticle(Particle.CLOUD, seat, 30, 0.4, 0.3, 0.4, 0.15);
        w.spawnParticle(Particle.FLAME, seat.clone().subtract(0, 0.6, 0), 20, 0.2, 0.1, 0.2, 0.05);
        Vector fling = forward(jet).multiply(Math.min(1.2, jet.speed * 0.3)).setY(1.6);
        exitCockpit(p, jet, seat, fling);
        pilotless.add(jet);
        p.sendMessage(plugin.mm(plugin.prefix() + "<yellow>Ejected! <gray>The jet blows in 3 seconds."));
        jet.boost = false;
    }

    private void abandon(Player p, Jet jet) {
        // pilot logged out / died: put them back and let the plane crash
        if (jets.get(p.getUniqueId()) == jet) {
            jets.remove(p.getUniqueId());
            if (p.isOnline()) {
                p.setSpectatorTarget(null);
                p.setGameMode(jet.mode == null ? GameMode.SURVIVAL : jet.mode);
            }
            p.getPersistentDataContainer().remove(restoreKey);
        }
        if (jet.cam != null && jet.cam.isValid()) jet.cam.remove();
        if (jet.pilotHead != null && jet.pilotHead.isValid()) jet.pilotHead.remove();
        jet.cam = null;
        jet.pilotHead = null;
        jet.ejectedAt = jet.t;
        pilotless.add(jet);
    }

    private void cleanup(Jet jet) {
        if (jet.task != null) { try { jet.task.cancel(); } catch (IllegalStateException ignored) {} }
        if (jet.body != null && jet.body.isValid()) jet.body.remove();
        if (jet.pilotHead != null && jet.pilotHead.isValid()) jet.pilotHead.remove();
        if (jet.cam != null && jet.cam.isValid()) jet.cam.remove();
        pilotless.remove(jet);
        jets.values().removeIf(j -> j == jet);
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Jet jet = jets.get(event.getPlayer().getUniqueId());
        if (jet == null || jet.ejectedAt >= 0) return;
        Input in = event.getInput();
        jet.fwd = in.isForward();
        jet.back = in.isBackward();
        jet.left = in.isLeft();
        jet.right = in.isRight();
        jet.boost = in.isSprint();
        if (in.isJump()) bomb(event.getPlayer(), jet);
        if (in.isSneak()) eject(event.getPlayer());
    }

    /** Sneaking would normally stop spectating the camera - eject instead. */
    @EventHandler(ignoreCancelled = true)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        Player p = event.getPlayer();
        if (!jets.containsKey(p.getUniqueId())) return;
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> eject(p));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && jets.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!jets.containsKey(p.getUniqueId()) && !safe.contains(p.getUniqueId())) return;
        EntityDamageEvent.DamageCause c = event.getCause();
        if (c == EntityDamageEvent.DamageCause.FALL || c == EntityDamageEvent.DamageCause.FLY_INTO_WALL
                || c == EntityDamageEvent.DamageCause.SUFFOCATION || c == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || c == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || c == EntityDamageEvent.DamageCause.FIRE
                || c == EntityDamageEvent.DamageCause.FIRE_TICK) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        Jet jet = jets.get(p.getUniqueId());
        if (jet != null) abandon(p, jet);
        safe.remove(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String mode = p.getPersistentDataContainer().get(restoreKey, PersistentDataType.STRING);
        if (mode == null) return;
        p.getPersistentDataContainer().remove(restoreKey);
        try { p.setGameMode(GameMode.valueOf(mode)); } catch (IllegalArgumentException e) { p.setGameMode(GameMode.SURVIVAL); }
        Location safeSpot = p.getWorld().getHighestBlockAt(p.getLocation()).getLocation().add(0.5, 1, 0.5);
        p.teleport(safeSpot);
    }

    // ------------------------------------------------------------------ bombs

    private void bomb(Player p, Jet jet) {
        long now = System.currentTimeMillis();
        if (now - jet.lastBomb < (long) (plugin.getConfig().getDouble("jet.bomb-cooldown-seconds", 1.5) * 1000)) return;
        jet.lastBomb = now;
        World w = jet.body.getWorld();
        Location start = jet.body.getLocation().subtract(0, 1.4, 0);
        Vector vel = forward(jet).multiply(jet.speed * 0.8);
        boolean sticky = jet.mk == 2;
        Location s0 = start.clone(); s0.setYaw(0); s0.setPitch(0);
        ItemDisplay bomb = w.spawn(s0, ItemDisplay.class, d -> {
            d.setItemStack(model(sticky ? "boss_fx_bomb2" : "boss_fx_bomb"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setTeleportDuration(1);
            d.setViewRange(8f);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.2f, 2.2f, 2.2f), new Quaternionf()));
        });
        w.playSound(start, Sound.BLOCK_PISTON_CONTRACT, 2f, 0.5f);
        w.playSound(start, Sound.ENTITY_TNT_PRIMED, 2f, 0.6f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                t++;
                vel.setY(vel.getY() - 0.06);
                vel.multiply(0.99);
                Location cur = bomb.getLocation();
                Location next = cur.clone().add(vel);
                Vector d = vel.clone().normalize();
                Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) d.getX(), (float) d.getY(), (float) d.getZ()));
                bomb.setInterpolationDelay(0);
                bomb.setInterpolationDuration(1);
                bomb.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(2.2f, 2.2f, 2.2f), new Quaternionf()));
                if (t % 3 == 0) w.playSound(next, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 0.4f + t * 0.01f);
                w.spawnParticle(Particle.SMOKE, cur, 2, 0.05, 0.05, 0.05, 0);
                boolean hitGround = next.getBlock().getType().isSolid() || next.getY() <= w.getMinHeight() + 1 || t > 400;
                boolean hitMob = false;
                if (!sticky) for (Entity e : w.getNearbyEntities(next, 1, 1, 1)) if (e instanceof LivingEntity && !e.equals(p)) { hitMob = true; break; }
                if (hitGround || hitMob) {
                    cancel();
                    if (sticky && hitGround && t <= 400) stick(bomb, next, d, p);
                    else { bomb.remove(); detonate(next, p); }
                    return;
                }
                bomb.teleport(next);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Mk II: the bomb spears into the ground nose-first, then counts down before it goes off. */
    private void stick(ItemDisplay bomb, Location impact, Vector dir, Player by) {
        World w = impact.getWorld();
        Location stuck = impact.clone().subtract(dir.clone().multiply(0.35));
        stuck.setYaw(0); stuck.setPitch(0);
        bomb.setTeleportDuration(2);
        bomb.teleport(stuck);
        w.playSound(stuck, Sound.BLOCK_ANVIL_LAND, 2f, 0.5f);
        w.playSound(stuck, Sound.ITEM_TRIDENT_HIT_GROUND, 2f, 0.6f);
        w.spawnParticle(Particle.BLOCK, impact, 60, 0.6, 0.3, 0.6, 0.2, impact.getBlock().getBlockData());
        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, impact, 12, 0.5, 0.2, 0.5, 0.02);
        int seconds = Math.max(1, plugin.getConfig().getInt("jet.mk2-fuse-seconds", 5));
        TextDisplay timer = w.spawn(stuck.clone().add(0, 2.2, 0), TextDisplay.class, t -> {
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(false);
            t.addScoreboardTag(Fx.TAG);
            t.setSeeThrough(true);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(3, 3, 3), new Quaternionf()));
            t.text(plugin.mm("<red><bold>" + seconds));
        });
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                int left = seconds * 20 - t;
                if (left <= 0) {
                    cancel();
                    timer.remove();
                    bomb.remove();
                    detonate(stuck, by);
                    return;
                }
                // beeps get faster as it gets closer
                int every = left > 40 ? 20 : left > 20 ? 10 : 4;
                if (t % every == 0) {
                    w.playSound(stuck, Sound.BLOCK_NOTE_BLOCK_PLING, 2.5f, 1.2f + (float) t / (seconds * 20));
                    w.spawnParticle(Particle.DUST, stuck.clone().add(0, 0.6, 0), 12, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(Color.RED, 2f));
                    Fx.safe(w, Particle.FLASH, stuck.clone().add(0, 0.6, 0), 1, 0, 0, 0, 0, Color.RED);
                }
                if (t % 20 == 0) timer.text(plugin.mm((left > 40 ? "<gold>" : "<red>") + "<bold>" + (int) Math.ceil(left / 20.0)));
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** The big one: a gargantuan crater carved over a few ticks and everything around it blown up. */
    private void detonate(Location at, Player by) {
        World w = at.getWorld();
        double radius = plugin.getConfig().getDouble("jet.crater-radius", 20);
        double kill = plugin.getConfig().getDouble("jet.damage-radius", 30);
        // heard across the whole world, felt nearby
        for (Player pl : w.getPlayers()) {
            double d = pl.getLocation().distance(at);
            float vol = (float) Math.max(0.5, 4 - d / 150);
            pl.playSound(pl.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, vol, 0.3f);
            pl.playSound(pl.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, vol, 0.4f);
            pl.playSound(pl.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, vol, 0.3f);
            if (d < 200) for (int i = 0; i < 6; i++) plugin.getServer().getScheduler().runTaskLater(plugin, () -> pl.sendHurtAnimation((float) Math.random() * 360), i * 3L);
        }
        Fx.safe(w, Particle.FLASH, at, 4, 3, 3, 3, 0, org.bukkit.Color.WHITE);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 30, radius / 2, radius / 3, radius / 2, 0);
        w.spawnParticle(Particle.FLAME, at, 600, radius / 2, radius / 3, radius / 2, 0.3);
        w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, at, 200, radius / 2, radius / 4, radius / 2, 0.12);
        Fx.wave(at.clone().add(0, 0.5, 0), "ring_orange", 2f, (float) radius * 4, 16);
        Fx.wave(at.clone().add(0, 1.5, 0), "ring_white", 2f, (float) radius * 3, 12);
        ItemDisplay sun = Fx.sprite(at.clone().add(0, radius / 3, 0), "sun", 2f, Display.Billboard.CENTER);
        Fx.animate(sun, 6, Fx.sized((float) radius * 3, (float) radius * 3, 0), 1);
        Fx.animate(sun, 30, Fx.sized(0.1f, 0.1f, 1), 10);
        Fx.removeLater(sun, 42);
        // everything around it dies / gets launched
        for (Entity e : w.getNearbyEntities(at, kill, kill, kill)) {
            if (e.equals(by) || jets.containsKey(e.getUniqueId()) || safe.contains(e.getUniqueId())) continue;
            double d = e.getLocation().distance(at);
            if (d > kill) continue;
            if (e instanceof LivingEntity le) {
                le.setNoDamageTicks(0);
                le.damage(Math.max(10, 400 * (1 - d / kill)), by);
                Vector v = e.getLocation().toVector().subtract(at.toVector());
                if (v.lengthSquared() > 0.01) le.setVelocity(v.normalize().multiply(2.5 * (1 - d / kill) + 0.5).setY(1.2));
            } else if (!(e instanceof Display) && !(e instanceof Player) && !(e instanceof ArmorStand)) {
                e.remove();
            }
        }
        // carve the crater a few layers per tick so the server doesn't freeze
        if (!plugin.getConfig().getBoolean("jet.break-blocks", true)) return;
        int R = (int) Math.ceil(radius);
        Block c = at.getBlock();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        new BukkitRunnable() {
            int y = R / 2;
            @Override
            public void run() {
                for (int step = 0; step < 3 && y >= -R; step++, y--) {
                    for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
                        double dist = Math.sqrt(x * x + (y * 1.5) * (y * 1.5) + z * z) + rnd.nextDouble() * 2;
                        if (dist > radius) continue;
                        Block b = c.getRelative(x, y, z);
                        Material m = b.getType();
                        if (m.isAir() || m == Material.BEDROCK || m == Material.BARRIER || m == Material.END_PORTAL_FRAME
                                || m == Material.END_PORTAL || m.getHardness() < 0 || b.getState() instanceof org.bukkit.block.Container) continue;
                        b.setType(Material.AIR, false);
                    }
                }
                if (y < -R) {
                    cancel();
                    // scorched rim with fire
                    for (int i = 0; i < R * 12; i++) {
                        double a = rnd.nextDouble(Math.PI * 2), d = rnd.nextDouble(radius * 0.3, radius + 3);
                        Block top = w.getHighestBlockAt(c.getX() + (int) (Math.cos(a) * d), c.getZ() + (int) (Math.sin(a) * d));
                        if (top.getType() == Material.BEDROCK || top.getState() instanceof org.bukkit.block.Container || !top.getType().isSolid()) continue;
                        top.setType(rnd.nextInt(4) == 0 ? Material.MAGMA_BLOCK : rnd.nextBoolean() ? Material.BLACKSTONE : Material.COAL_BLOCK, false);
                        if (rnd.nextInt(3) == 0 && top.getRelative(0, 1, 0).isEmpty()) top.getRelative(0, 1, 0).setType(Material.FIRE);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(jets.keySet())) {
            Player p = Bukkit.getPlayer(id);
            Jet jet = jets.get(id);
            if (p != null && jet != null) {
                Location down = p.getWorld().getHighestBlockAt(jet.body.getLocation()).getLocation().add(0.5, 1, 0.5);
                exitCockpit(p, jet, down, new Vector());
                cleanup(jet);
            }
        }
        for (Jet j : new ArrayList<>(pilotless)) cleanup(j);
    }
}
