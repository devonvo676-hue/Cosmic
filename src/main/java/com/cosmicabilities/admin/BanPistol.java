package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Admin-only pistol: any player it hits is banned. Non-admins can't fire, keep, or pick it up. */
public class BanPistol implements Listener {
    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Long> lastShot = new ConcurrentHashMap<>();

    public BanPistol(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "ban_pistol");
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
                plugin.mm("<!italic><dark_gray>Admin only")));
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
        long cd = plugin.getConfig().getLong("ban-pistol.cooldown-ms", 1000);
        Long last = lastShot.get(shooter.getUniqueId());
        if (last != null && now - last < cd) return;
        lastShot.put(shooter.getUniqueId(), now);
        fire(shooter);
    }

    private void fire(Player shooter) {
        World world = shooter.getWorld();
        Location eye = shooter.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double range = plugin.getConfig().getDouble("ban-pistol.range", 64);

        RayTraceResult hit = world.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.3,
                e -> e instanceof Player && !e.equals(shooter));
        double dist = range;
        if (hit != null) dist = eye.toVector().distance(hit.getHitPosition());

        world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.6f);
        world.playSound(eye, Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 2f);
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(230, 57, 70), 0.9f);
        Location p = eye.clone().add(dir.clone().multiply(0.8));
        for (double d = 0.8; d < dist; d += 0.4) {
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, red);
            p.add(dir.clone().multiply(0.4));
        }

        if (hit == null || !(hit.getHitEntity() instanceof Player target)) return;
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
        at.getWorld().playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.2f);
        target.ban(reason, expires, shooter.getName(), true);

        if (plugin.getConfig().getBoolean("ban-pistol.broadcast", true)) {
            Bukkit.broadcast(plugin.mm(plugin.prefix() + "<red>" + name + " <gray>was banned by <white>"
                    + shooter.getName() + "<gray>'s Ban Pistol" + (minutes > 0 ? " for " + minutes + " minutes" : "") + "."));
        }
        plugin.getLogger().info(shooter.getName() + " banned " + name + " with the Ban Pistol (" + reason + ")");
    }

    /** Non-admins can't pick the pistol up; the dropped item is deleted. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (!isPistol(item.getItemStack())) return;
        if (event.getEntity() instanceof Player p && isStaff(p)) return;
        event.setCancelled(true);
        item.remove();
    }
}
