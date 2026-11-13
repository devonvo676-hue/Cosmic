package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.fx.Fx;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v2tou's fighter jet. Right-click the jet item to climb in. It flies where you look:
 * W = throttle up, S = throttle down, SPACE = drop a bomb, SNEAK = bail out (you get slow falling).
 * Bombs leave a gargantuan crater and blow up everything around them (jet.* in config).
 */
public class FighterJet implements Listener {
    private static final float MODEL_SCALE = 3f;

    private static final class Jet {
        ItemDisplay body;
        BukkitRunnable task;
        double speed = 0.9;
        boolean up, down;
        long lastBomb;
        float roll;
        float lastYaw;
    }

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Jet> jets = new ConcurrentHashMap<>();

    public FighterJet(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "fighter_jet");
    }

    public ItemStack create() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(plugin.mm("<!italic><gradient:#9aa5b1:#00bbf9><bold>FIGHTER JET"));
        m.lore(List.of(plugin.mm("<!italic><gray>Right click: climb in"), plugin.mm("<!italic><gray>Look to steer, W/S throttle"),
                plugin.mm("<!italic><gray>SPACE: drop a bomb, SNEAK: bail out"), plugin.mm("<!italic><dark_red>v2tou only")));
        m.setItemModel(new NamespacedKey("cosmic", "fighter_jet_item"));
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isJet(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public boolean flying(UUID id) { return jets.containsKey(id); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!isJet(p.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        if (!Owner.allowed(plugin, p)) {
            p.getInventory().setItemInMainHand(null);
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>You can't use that."));
            return;
        }
        if (jets.containsKey(p.getUniqueId()) || p.isInsideVehicle()) return;
        launch(p);
    }

    private void launch(Player p) {
        World w = p.getWorld();
        Location at = p.getLocation().add(0, 0.2, 0);
        at.setPitch(0);
        Jet jet = new Jet();
        ItemStack model = new ItemStack(Material.PAPER);
        ItemMeta mm = model.getItemMeta();
        mm.setItemModel(new NamespacedKey("cosmic", "boss_fx_jet"));
        model.setItemMeta(mm);
        jet.body = w.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(model);
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
        jet.body.addPassenger(p);
        jet.lastYaw = p.getLocation().getYaw();
        jets.put(p.getUniqueId(), jet);
        w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.5f);
        w.playSound(at, Sound.ITEM_ELYTRA_FLYING, 1.5f, 1.2f);
        p.sendActionBar(plugin.mm("<aqua>Look to steer  <gray>|  <white>W/S <gray>throttle  <gray>|  <white>SPACE <gray>bomb  <gray>|  <white>SNEAK <gray>bail out"));
        jet.task = new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !jet.body.isValid() || !jet.body.getPassengers().contains(p)) { land(p, false); return; }
                t++;
                // throttle
                double max = plugin.getConfig().getDouble("jet.max-speed", 3.0);
                if (jet.up) jet.speed = Math.min(max, jet.speed + 0.06);
                if (jet.down) jet.speed = Math.max(0.25, jet.speed - 0.08);
                Location look = p.getEyeLocation();
                Vector dir = look.getDirection().normalize();
                // takes off nose-up from the ground before it can dive
                if (t < 20) dir.setY(Math.max(dir.getY(), 0.35)).normalize();
                Location cur = jet.body.getLocation();
                Location next = cur.clone().add(dir.clone().multiply(jet.speed));
                // crash into terrain
                Block hit = next.getBlock();
                Block nose = next.clone().add(dir.clone().multiply(2.5)).getBlock();
                if (t > 10 && (hit.getType().isSolid() || nose.getType().isSolid())) {
                    w.createExplosion(next, 4f, false, false, p);
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, next, 2);
                    land(p, true);
                    return;
                }
                float yaw = look.getYaw(), pitch = look.getPitch();
                // bank into turns
                float dyaw = wrap(yaw - jet.lastYaw);
                jet.lastYaw = yaw;
                jet.roll += (Math.max(-45, Math.min(45, -dyaw * 4)) - jet.roll) * 0.2f;
                next.setYaw(0);
                next.setPitch(0);
                jet.body.teleport(next, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(-yaw))
                        .rotateX((float) Math.toRadians(pitch)).rotateZ((float) Math.toRadians(jet.roll));
                jet.body.setInterpolationDelay(0);
                jet.body.setInterpolationDuration(2);
                jet.body.setTransformation(new Transformation(new Vector3f(0, -0.6f, 0), rot,
                        new Vector3f(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE), new Quaternionf()));
                // engine exhaust + sound
                Vector back = dir.clone().multiply(-MODEL_SCALE * 0.9);
                Location ex = next.clone().add(back).add(0, -0.4, 0);
                w.spawnParticle(Particle.FLAME, ex, 4, 0.1, 0.1, 0.1, 0.02);
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, ex, 2, 0.1, 0.1, 0.1, 0.01);
                if (jet.speed > 2) w.spawnParticle(Particle.CLOUD, ex, 2, 0.3, 0.1, 0.3, 0.02);
                if (t % 10 == 0) w.playSound(next, Sound.ITEM_ELYTRA_FLYING, 1.5f, (float) (0.6 + jet.speed * 0.25));
                if (t % 20 == 0) p.sendActionBar(plugin.mm("<aqua>Speed <white>" + Math.round(jet.speed * 20 * 3.6) + " km/h  <gray>| Altitude <white>"
                        + (next.getBlockY() - w.getHighestBlockYAt(next)) + "m"));
                p.setFallDistance(0);
            }
        };
        jet.task.runTaskTimer(plugin, 1L, 1L);
    }

    private static float wrap(float a) {
        a %= 360;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    private void land(Player p, boolean crashed) {
        Jet jet = jets.remove(p.getUniqueId());
        if (jet == null) return;
        if (jet.task != null) jet.task.cancel();
        if (jet.body.isValid()) {
            jet.body.eject();
            jet.body.remove();
        }
        if (p.isOnline()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false, true));
            p.setFallDistance(0);
            if (crashed) p.sendMessage(plugin.mm(plugin.prefix() + "<red>You crashed! <gray>(you bailed out just in time)"));
        }
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Jet jet = jets.get(event.getPlayer().getUniqueId());
        if (jet == null) return;
        Input in = event.getInput();
        jet.up = in.isForward();
        jet.down = in.isBackward();
        if (in.isJump()) bomb(event.getPlayer(), jet);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player p && jets.containsKey(p.getUniqueId())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> land(p, false));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && jets.containsKey(p.getUniqueId())
                && (event.getCause() == EntityDamageEvent.DamageCause.FALL || event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        land(event.getPlayer(), false);
    }

    // ------------------------------------------------------------------ bombs

    private void bomb(Player p, Jet jet) {
        long now = System.currentTimeMillis();
        if (now - jet.lastBomb < (long) (plugin.getConfig().getDouble("jet.bomb-cooldown-seconds", 1.5) * 1000)) return;
        jet.lastBomb = now;
        World w = p.getWorld();
        Location start = jet.body.getLocation().subtract(0, 1.4, 0);
        Vector vel = p.getEyeLocation().getDirection().normalize().multiply(jet.speed * 0.8);
        ItemStack model = new ItemStack(Material.PAPER);
        ItemMeta mm = model.getItemMeta();
        mm.setItemModel(new NamespacedKey("cosmic", "boss_fx_bomb"));
        model.setItemMeta(mm);
        Location s0 = start.clone(); s0.setYaw(0); s0.setPitch(0);
        ItemDisplay bomb = w.spawn(s0, ItemDisplay.class, d -> {
            d.setItemStack(model);
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
                for (Entity e : w.getNearbyEntities(next, 1, 1, 1)) if (e instanceof LivingEntity && !e.equals(p)) { hitMob = true; break; }
                if (hitGround || hitMob) {
                    cancel();
                    bomb.remove();
                    detonate(next, p);
                    return;
                }
                bomb.teleport(next);
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
            if (e.equals(by) || jets.containsKey(e.getUniqueId())) continue;
            double d = e.getLocation().distance(at);
            if (d > kill) continue;
            if (e instanceof LivingEntity le) {
                le.setNoDamageTicks(0);
                le.damage(Math.max(10, 400 * (1 - d / kill)), by);
                Vector v = e.getLocation().toVector().subtract(at.toVector());
                if (v.lengthSquared() > 0.01) le.setVelocity(v.normalize().multiply(2.5 * (1 - d / kill) + 0.5).setY(1.2));
            } else if (!(e instanceof Display) && !(e instanceof Player)) {
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
            if (p != null) land(p, false);
        }
    }
}
