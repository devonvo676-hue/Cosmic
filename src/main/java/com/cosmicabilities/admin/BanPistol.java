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
    private final Map<UUID, Long> lastShot = new ConcurrentHashMap<>();

    public BanPistol(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "ban_pistol");
        this.bulletKey = new NamespacedKey(plugin, "ban_bullet");
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
                plugin.mm("<!italic><gray>Right-click to fire."),
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

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player shooter = event.getPlayer();
        ItemStack hand = shooter.getInventory().getItemInMainHand();
        if (!isPistol(hand)) return;
        event.setCancelled(true);

        if (!isStaff(shooter)) {
            shooter.getInventory().setItemInMainHand(null);
            shooter.sendMessage(plugin.mm(plugin.prefix() + "<red>Only admins can use the Ban Pistol. It has been removed."));
            return;
        }
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("ban-pistol.cooldown-ms", 800);
        Long last = lastShot.get(shooter.getUniqueId());
        if (last != null && now - last < cd) return;
        lastShot.put(shooter.getUniqueId(), now);
        fire(shooter);
    }

    private void fire(Player shooter) {
        World world = shooter.getWorld();
        Location eye = shooter.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location muzzle = eye.clone().add(dir.clone().multiply(0.9)).add(rightOf(dir).multiply(0.3)).add(0, -0.2, 0);

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
        if (!(event.getHitEntity() instanceof Player target) || shooter == null) return;
        if (plugin.getConfig().getBoolean("ban-pistol.exempt-staff", true) && isStaff(target)) {
            shooter.sendMessage(plugin.mm(plugin.prefix() + "<yellow>" + target.getName() + " is staff and can't be banned with the pistol."));
            return;
        }
        ban(shooter, target);
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
