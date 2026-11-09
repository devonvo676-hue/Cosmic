package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.fx.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin-only pistol. Fires a real, visible bullet (a fast projectile with a tracer); any player it hits is banned.
 * Non-admins can't fire, keep, or pick it up. /unban lifts the ban.
 */
public class BanPistol implements Listener {
    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey key;
    private final NamespacedKey bulletKey;
    private final NamespacedKey ammoKey;
    private final Map<UUID, Long> lastShot = new ConcurrentHashMap<>();

    public BanPistol(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "ban_pistol");
        this.bulletKey = new NamespacedKey(plugin, "ban_bullet");
        this.ammoKey = new NamespacedKey(plugin, "ban_pistol_ammo");
    }

    public static boolean isStaff(Player p) {
        return p.isOp() || p.hasPermission("cosmicabilities.admin");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.IRON_HORSE_ARMOR);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", "ban_pistol"));
        meta.displayName(plugin.mm("<!italic><red><bold>Ban Pistol"));
        meta.lore(List.of(
                plugin.mm("<!italic><gray>Right-click to aim, left-click to fire."),
                plugin.mm("<!italic><gray>Q drops the magazine and reloads."),
                plugin.mm("<!italic><dark_red>Any player hit is banned."),
                plugin.mm("<!italic><dark_gray>Admin only - /unban <player> to undo")));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "1");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPistol(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING) != null;
    }

    // ------------------------------------------------------------------ handling: aim, shoot, rack, reload

    private enum Look { HIP, HIP_SLIDE, NO_MAG, AIM, AIM_SLIDE }

    /** Per-player gun state (aiming / reloading); ammo lives on the item itself. */
    private static final class GunState {
        boolean aiming, reloading;
        long busyUntil;
    }

    private final Map<UUID, GunState> states = new ConcurrentHashMap<>();

    private GunState state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new GunState());
    }

    private int magSize() {
        return Math.max(1, plugin.getConfig().getInt("ban-pistol.magazine-size", 7));
    }

    private int ammo(ItemStack gun) {
        Integer a = gun.getItemMeta().getPersistentDataContainer().get(ammoKey, PersistentDataType.INTEGER);
        return a == null ? magSize() : a;
    }

    private void setAmmo(ItemStack gun, int ammo) {
        ItemMeta meta = gun.getItemMeta();
        meta.getPersistentDataContainer().set(ammoKey, PersistentDataType.INTEGER, ammo);
        gun.setItemMeta(meta);
    }

    /** Swaps the gun's model in the player's hand (hand_animation_on_swap is off, so it doesn't re-equip). */
    private void look(Player p, Look look) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isPistol(hand)) return;
        String model = switch (look) {
            case HIP -> "ban_pistol";
            case HIP_SLIDE -> "ban_pistol_slide";
            case NO_MAG -> "ban_pistol_nomag";
            case AIM -> "ban_pistol_aim";
            case AIM_SLIDE -> "ban_pistol_aim_slide";
        };
        ItemMeta meta = hand.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", model));
        hand.setItemMeta(meta);
    }

    private void later(long ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks);
    }

    private boolean holding(Player p) {
        return isPistol(p.getInventory().getItemInMainHand());
    }

    private void ammoBar(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isPistol(hand)) return;
        int a = ammo(hand), max = magSize();
        StringBuilder sb = new StringBuilder("<red><bold>BAN PISTOL</bold> ");
        for (int i = 0; i < max; i++) sb.append(i < a ? "<gold>▮" : "<dark_gray>▮");
        GunState st = state(p);
        sb.append(st.reloading ? "  <yellow>reloading..." : st.aiming ? "  <gray>aiming - <white>left-click <gray>to fire" : "  <gray>right-click to aim");
        p.sendActionBar(plugin.mm(sb.toString()));
    }

    /** Non-admins can't use it at all. */
    private boolean allowed(Player p) {
        if (isStaff(p)) return true;
        p.getInventory().setItemInMainHand(null);
        p.sendMessage(plugin.mm(plugin.prefix() + "<red>Only admins can use the Ban Pistol. It has been removed."));
        return false;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        if (!holding(p)) return;
        Action action = event.getAction();
        if (action == Action.PHYSICAL) return;
        event.setCancelled(true);
        if (!allowed(p)) return;
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) toggleAim(p);
        else trigger(p);
    }

    /** Left-clicking a mob/player while aiming fires instead of punching. */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = true)
    public void onMelee(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || !holding(p)) return;
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        event.setCancelled(true);
        if (allowed(p)) trigger(p);
    }

    /** Never break blocks with the gun (creative left-clicks). */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (holding(event.getPlayer())) event.setCancelled(true);
    }

    /** Q drops the magazine and reloads instead of throwing the gun away. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (!isPistol(dropped) || !isStaff(p)) return;
        if (p.getInventory().getItemInMainHand().getType() != Material.AIR) return;   // dropped from the inventory screen
        event.setCancelled(true);
        later(1, () -> reload(p));
    }

    /** Lower the gun when you switch away from it. */
    @EventHandler
    public void onSwitch(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        GunState st = states.get(p.getUniqueId());
        if (st == null || !st.aiming) return;
        ItemStack old = p.getInventory().getItem(event.getPreviousSlot());
        setAim(p, false, old);
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player p = event.getPlayer();
        GunState st = states.remove(p.getUniqueId());
        if (st != null && st.aiming) p.removePotionEffect(PotionEffectType.SLOWNESS);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isPistol(hand)) {
            ItemMeta meta = hand.getItemMeta();
            meta.setItemModel(new NamespacedKey("cosmic", "ban_pistol"));
            hand.setItemMeta(meta);
        }
    }

    private void toggleAim(Player p) {
        GunState st = state(p);
        if (st.reloading) return;
        setAim(p, !st.aiming, p.getInventory().getItemInMainHand());
    }

    private void setAim(Player p, boolean aim, ItemStack gun) {
        GunState st = state(p);
        st.aiming = aim;
        if (gun != null && isPistol(gun)) {
            ItemMeta meta = gun.getItemMeta();
            meta.setItemModel(new NamespacedKey("cosmic", aim ? "ban_pistol_aim" : "ban_pistol"));
            gun.setItemMeta(meta);
        }
        if (aim) {
            // slowness narrows the field of view a little: a light zoom while you aim
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 1, false, false, false));
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.6f);
        } else {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.6f, 1.3f);
        }
        ammoBar(p);
    }

    /** Left click: fires only while aiming. */
    private void trigger(Player p) {
        GunState st = state(p);
        if (!st.aiming) {
            p.sendActionBar(plugin.mm("<gray>Right-click to aim, then <white>left-click <gray>to fire"));
            return;
        }
        long now = System.currentTimeMillis();
        if (st.reloading || now < st.busyUntil) return;
        ItemStack gun = p.getInventory().getItemInMainHand();
        int ammo = ammo(gun);
        if (ammo <= 0) { reload(p); return; }
        st.busyUntil = now + plugin.getConfig().getLong("ban-pistol.cooldown-ms", 300);
        setAmmo(gun, ammo - 1);
        fire(p);
        // the slide kicks back and springs forward; on the last round it locks back and you reload
        look(p, Look.AIM_SLIDE);
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 2f);
        if (ammo - 1 <= 0) {
            later(6, () -> reload(p));
        } else {
            later(2, () -> { if (state(p).aiming && !state(p).reloading) look(p, Look.AIM); });
        }
        ammoBar(p);
    }

    /** Drops the empty magazine (you can see it fall), slaps in a new one and racks the slide. */
    private void reload(Player p) {
        GunState st = state(p);
        if (st.reloading || !holding(p)) return;
        ItemStack gun = p.getInventory().getItemInMainHand();
        if (ammo(gun) >= magSize() && !st.aiming) {
            p.sendActionBar(plugin.mm("<gray>Magazine is already full"));
        }
        boolean wasAiming = st.aiming;
        if (wasAiming) setAim(p, false, gun);
        st.reloading = true;
        look(p, Look.NO_MAG);
        dropMagazine(p);
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.5f, 2f);
        ammoBar(p);
        later(18, () -> {
            if (!p.isOnline()) return;
            if (!holding(p)) { st.reloading = false; return; }
            look(p, Look.HIP_SLIDE);
            p.playSound(p.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.5f, 2f);
        });
        later(26, () -> {
            if (!p.isOnline()) return;
            st.reloading = false;
            if (!holding(p)) return;
            // rack the slide: it snaps forward, chambering a round
            ItemStack g = p.getInventory().getItemInMainHand();
            setAmmo(g, magSize());
            look(p, Look.HIP);
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.8f, 1.8f);
            p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.8f, 1.4f);
            if (wasAiming) setAim(p, true, g);
            ammoBar(p);
        });
    }

    /** A real dropped magazine falls out of the grip and clatters on the ground (it can't be picked up). */
    private void dropMagazine(Player p) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(0, 0, 1);
        dir.normalize();
        Location from = p.getLocation().add(0, 0.9, 0).add(dir.clone().multiply(0.45)).add(rightOf(dir).multiply(0.35));
        ItemStack mag = new ItemStack(Material.IRON_INGOT);
        ItemMeta meta = mag.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", "ban_pistol_mag"));
        meta.displayName(plugin.mm("<!italic><gray>Empty magazine"));
        mag.setItemMeta(meta);
        Item item = p.getWorld().dropItem(from, mag, it -> {
            it.setPickupDelay(Integer.MAX_VALUE);
            it.setCanMobPickup(false);
            it.setPersistent(false);
            it.setVelocity(new Vector(0, -0.15, 0).add(rightOf(dir).multiply(0.05)));
        });
        later(plugin.getConfig().getLong("ban-pistol.magazine-despawn-seconds", 8) * 20L, () -> { if (item.isValid()) item.remove(); });
        later(9, () -> { if (item.isValid()) item.getWorld().playSound(item.getLocation(), Sound.BLOCK_CHAIN_FALL, 0.8f, 1.6f); });
    }

    private void fire(Player shooter) {
        World world = shooter.getWorld();
        Location eye = shooter.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        boolean aiming = state(shooter).aiming;
        Location muzzle = eye.clone().add(dir.clone().multiply(0.9)).add(rightOf(dir).multiply(aiming ? 0.0 : 0.3)).add(0, aiming ? -0.08 : -0.2, 0);

        // gunshot: custom sound from the resource pack, layered with vanilla sounds so it's loud even without the pack
        float volume = (float) plugin.getConfig().getDouble("ban-pistol.volume", 4.0);
        world.playSound(muzzle, "cosmic:ban_pistol.shot", SoundCategory.PLAYERS, volume, 1f);
        if (plugin.getConfig().getBoolean("ban-pistol.vanilla-sound-layer", true)) {
            world.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 1.9f);
            world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.2f, 0.6f);
        }
        // muzzle flash + smoke
        world.spawnParticle(Particle.FLAME, muzzle, 6, 0.05, 0.05, 0.05, 0.03);
        world.spawnParticle(Particle.SMOKE, muzzle, 8, 0.05, 0.05, 0.05, 0.02);
        world.spawnParticle(Particle.DUST, muzzle, 4, 0.05, 0.05, 0.05, 0, Fx.dust(0xffd166, 1.4f));
        double speed = plugin.getConfig().getDouble("ban-pistol.bullet-speed", 3.5);
        Snowball bullet = world.spawn(muzzle, Snowball.class, b -> {
            b.setShooter(shooter);
            b.setItem(bulletItem());
            b.setGravity(false);
            b.setSilent(true);
            b.setVelocity(dir.clone().multiply(speed));
            b.getPersistentDataContainer().set(bulletKey, PersistentDataType.STRING, shooter.getUniqueId().toString());
        });
        int lifetime = (int) Math.ceil(plugin.getConfig().getDouble("ban-pistol.range", 64) / speed) + 2;
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!bullet.isValid() || t++ > lifetime) {
                    if (bullet.isValid()) bullet.remove();
                    cancel();
                    return;
                }
                // red tracer along the bullet's path this tick
                Location p = bullet.getLocation();
                Vector v = bullet.getVelocity();
                for (double d = 0; d < 1; d += 0.2) {
                    world.spawnParticle(Particle.DUST, p.clone().subtract(v.clone().multiply(d)), 1, 0, 0, 0, 0, Fx.dust(0xff3b3b, 0.6f));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private ItemStack bulletItem() {
        ItemStack it = new ItemStack(Material.IRON_NUGGET);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", "bullet"));
        it.setItemMeta(meta);
        return it;
    }

    private static Vector rightOf(Vector dir) {
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        return right.lengthSquared() < 1.0E-4 ? new Vector(1, 0, 0) : right.normalize();
    }

    @EventHandler
    public void onBulletHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        String owner = proj.getPersistentDataContainer().get(bulletKey, PersistentDataType.STRING);
        if (owner == null) return;
        event.setCancelled(true);
        Location at = proj.getLocation();
        proj.remove();
        World w = at.getWorld();
        if (event.getHitBlock() != null) {
            w.spawnParticle(Particle.BLOCK, at, 12, 0.1, 0.1, 0.1, 0, event.getHitBlock().getBlockData());
            w.spawnParticle(Particle.ELECTRIC_SPARK, at, 6, 0.05, 0.05, 0.05, 0.1);
            w.playSound(at, Sound.BLOCK_STONE_HIT, 1f, 1.4f);
            return;
        }
        Player shooter = Bukkit.getPlayer(UUID.fromString(owner));
        if (shooter == null) return;
        if (event.getHitEntity() instanceof LivingEntity animal && !(event.getHitEntity() instanceof Player)) {
            woundAnimal(shooter, animal, proj, at);
            return;
        }
        if (!(event.getHitEntity() instanceof Player target)) return;
        if (plugin.getConfig().getBoolean("ban-pistol.exempt-staff", true) && isStaff(target)) {
            shooter.sendMessage(plugin.mm(plugin.prefix() + "<yellow>" + target.getName() + " is staff and can't be banned with the pistol."));
            return;
        }
        ban(shooter, target);
    }

    // ------------------------------------------------------------------ shooting animals: damage + bleeding bullet hole

    private void woundAnimal(Player shooter, LivingEntity victim, Projectile bullet, Location at) {
        double dmg = plugin.getConfig().getDouble("ban-pistol.animal-damage", 8);
        victim.setNoDamageTicks(0);
        victim.damage(dmg, shooter);
        World w = victim.getWorld();
        Vector v = bullet.getVelocity();
        Vector dir = v.lengthSquared() < 1.0E-4 ? shooter.getLocation().getDirection() : v.clone().normalize();
        // where exactly the bullet entered the body
        Vector start = at.toVector().subtract(dir.clone().multiply(2));
        org.bukkit.util.RayTraceResult ray = victim.getBoundingBox().rayTrace(start, dir, 4);
        Location hole = ray != null ? ray.getHitPosition().toLocation(w) : at.clone();
        Location centre = victim.getLocation().add(0, victim.getHeight() / 2, 0);
        Vector offset = hole.toVector().subtract(victim.getLocation().toVector());
        float yaw0 = victim.getBodyYaw();
        w.spawnParticle(Particle.BLOCK, hole, 18, 0.08, 0.08, 0.08, 0.15, Material.REDSTONE_BLOCK.createBlockData());
        w.spawnParticle(Particle.DUST, hole, 10, 0.1, 0.1, 0.1, 0, Fx.dust(0x8a0303, 1.1f));
        w.playSound(hole, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.6f);
        // a splash of blood on the ground below
        Location ground = victim.getLocation().add(dir.clone().setY(0).multiply(0.6));
        ground.setY(Math.floor(victim.getLocation().getY()) + 0.02);
        org.bukkit.entity.ItemDisplay splat = Fx.flat(ground, "blood_splat", 0.05f, (float) Math.random() * 6.28f);
        Fx.animate(splat, 6, Fx.flatShape(1.1f, (float) Math.random() * 6.28f), 1);
        int life = plugin.getConfig().getInt("ban-pistol.wound-seconds", 12) * 20;
        Fx.removeLater(splat, life + 40);
        Vector normal0 = hole.toVector().subtract(centre.toVector());
        org.bukkit.entity.ItemDisplay wound = Fx.disc(hole, "bullet_hole", 0.3f, normal0.lengthSquared() < 1.0E-4 ? dir.clone().multiply(-1) : normal0);
        wound.setTeleportDuration(1);
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!wound.isValid() || t++ > life || !victim.isValid() || victim.isDead()) {
                    if (wound.isValid()) wound.remove();
                    cancel();
                    return;
                }
                // the hole stays stuck to the body as it walks and turns
                double d = Math.toRadians(victim.getBodyYaw() - yaw0);
                double cos = Math.cos(d), sin = Math.sin(d);
                Vector o = new Vector(offset.getX() * cos - offset.getZ() * sin, offset.getY(), offset.getX() * sin + offset.getZ() * cos);
                Location now = victim.getLocation().add(o);
                Vector n = o.clone().subtract(new Vector(0, victim.getHeight() / 2, 0));
                if (n.lengthSquared() < 1.0E-4) n = dir.clone().multiply(-1);
                now.add(n.clone().normalize().multiply(0.02));
                now.setYaw(0);
                now.setPitch(0);
                wound.teleport(now);
                wound.setTransformation(Fx.discShape(0.3f, n, 0));
                // blood seeping out and dripping down
                if (t % 3 == 0) w.spawnParticle(Particle.DUST, now, 1, 0.03, 0.03, 0.03, 0, Fx.dust(0x8a0303, 0.7f));
                if (t % 5 == 0) w.spawnParticle(Particle.FALLING_DUST, now, 1, 0.02, 0, 0.02, 0, Material.RED_CONCRETE.createBlockData());
                if (t % 40 == 0) w.spawnParticle(Particle.BLOCK, now, 3, 0.03, 0.03, 0.03, 0, Material.REDSTONE_BLOCK.createBlockData());
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void ban(Player shooter, Player target) {
        String reason = plugin.getConfig().getString("ban-pistol.reason", "Shot by the Ban Pistol");
        long minutes = plugin.getConfig().getLong("ban-pistol.duration-minutes", 0);
        Date expires = minutes > 0 ? new Date(System.currentTimeMillis() + minutes * 60_000L) : null;
        String name = target.getName();
        Location at = target.getLocation().add(0, 1, 0);

        at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
        at.getWorld().spawnParticle(Particle.DUST, at, 30, 0.3, 0.6, 0.3, 0, Fx.dust(0xd00000, 1.5f));
        at.getWorld().strikeLightningEffect(target.getLocation());
        target.ban(reason, expires, shooter.getName(), true);

        if (plugin.getConfig().getBoolean("ban-pistol.broadcast", true)) {
            Bukkit.broadcast(plugin.mm(plugin.prefix() + "<red>" + name + " <gray>was banned by <white>"
                    + shooter.getName() + "<gray>'s Ban Pistol" + (minutes > 0 ? " for " + minutes + " minutes" : "") + "."));
        }
        plugin.getLogger().info(shooter.getName() + " banned " + name + " with the Ban Pistol (" + reason + ")");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (!isPistol(item.getItemStack())) return;
        if (event.getEntity() instanceof Player p && isStaff(p)) return;
        event.setCancelled(true);
        item.remove();
    }
}
