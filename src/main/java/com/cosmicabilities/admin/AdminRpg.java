package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.fx.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The admin RPG: right click fires a rocket that flies straight and blows a giant crater wherever it hits.
 * Only admins can fire it (anyone else holding one loses it). Power and crater size are in config (rpg.*).
 */
public class AdminRpg implements Listener {
    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey key, rocketKey, mk2Key;
    private final Map<UUID, Long> lastShot = new HashMap<>();

    public AdminRpg(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "admin_rpg");
        this.rocketKey = new NamespacedKey(plugin, "rpg_rocket");
        this.mk2Key = new NamespacedKey(plugin, "admin_rpg_mk2");
    }

    public ItemStack create() {
        ItemStack it = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta m = it.getItemMeta();
        m.displayName(plugin.mm("<!italic><red><bold>ADMIN RPG"));
        m.lore(java.util.List.of(plugin.mm("<!italic><gray>Right click: fire a rocket"), plugin.mm("<!italic><gray>Leaves a giant crater"),
                plugin.mm("<!italic><dark_red>Admins only")));
        m.setItemModel(new NamespacedKey("cosmic", "admin_rpg"));
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    /** The Mk II: bigger crater, much louder blast. Owner only (owner-items.players). */
    public ItemStack createMk2() {
        ItemStack it = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta m = it.getItemMeta();
        m.displayName(plugin.mm("<!italic><gradient:#ffd166:#e5383b><bold>RPG MK II"));
        m.lore(java.util.List.of(plugin.mm("<!italic><gray>Right click: fire a heavy rocket"), plugin.mm("<!italic><gray>Leaves an enormous crater"),
                plugin.mm("<!italic><dark_red>Owner only")));
        m.setItemModel(new NamespacedKey("cosmic", "admin_rpg_mk2"));
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        m.getPersistentDataContainer().set(mk2Key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isMk2(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(mk2Key, PersistentDataType.BYTE);
    }

    public boolean isRpg(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isRpg(hand)) return;
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        boolean mk2 = isMk2(hand);
        if (mk2 ? !Owner.allowed(plugin, p) : !BanPistol.isAdmin(p)) {
            p.getInventory().setItemInMainHand(null);
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>You can't use that."));
            return;
        }
        long now = System.currentTimeMillis();
        long cd = mk2 ? 0 : (long) (plugin.getConfig().getDouble("rpg.cooldown-seconds", 1.5) * 1000);   // the Mk II has no cooldown
        if (now - lastShot.getOrDefault(p.getUniqueId(), 0L) < cd) return;
        lastShot.put(p.getUniqueId(), now);
        fire(p, mk2);
    }

    private void fire(Player p, boolean mk2) {
        String cfg = mk2 ? "rpg-mk2." : "rpg.";
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location muzzle = eye.clone().add(dir.clone().multiply(1.2)).add(0, -0.15, 0);
        double speed = plugin.getConfig().getDouble(cfg + "speed", mk2 ? 2.6 : 2.2);
        Snowball carrier = w.spawn(muzzle, Snowball.class, s -> {
            s.setShooter(p);
            s.setGravity(false);
            s.setVelocity(dir.clone().multiply(speed));
            ItemStack hidden = new ItemStack(Material.PAPER);
            ItemMeta hm = hidden.getItemMeta();
            hm.setItemModel(new NamespacedKey("cosmic", "empty"));
            hidden.setItemMeta(hm);
            s.setItem(hidden);
            s.getPersistentDataContainer().set(rocketKey, PersistentDataType.BYTE, (byte) (mk2 ? 2 : 1));
            s.setPersistent(false);
        });
        ItemStack rocketItem = new ItemStack(Material.PAPER);
        ItemMeta rm = rocketItem.getItemMeta();
        rm.setItemModel(new NamespacedKey("cosmic", "rpg_rocket"));
        rocketItem.setItemMeta(rm);
        Location start = muzzle.clone();
        start.setYaw(0); start.setPitch(0);
        ItemDisplay rocket = w.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(rocketItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            // point the rocket's nose (+Z in the model) along the flight direction
            Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()));
            float sc = mk2 ? 2.2f : 1.2f;
            d.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(sc, sc, sc), new Quaternionf()));
        });
        carrier.addPassenger(rocket);
        w.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.5f);
        w.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.8f);
        w.spawnParticle(Particle.CLOUD, muzzle, 20, 0.2, 0.2, 0.2, 0.08);
        p.setVelocity(p.getVelocity().subtract(dir.clone().multiply(0.35)));
        new BukkitRunnable() {
            int t = 0;
            double age = 0;   // advances slower while Lightspeed has time slowed
            @Override
            public void run() {
                t++;
                age += com.cosmicabilities.ability.impl.LightspeedAbility.timeScale();
                if (!carrier.isValid()) { if (rocket.isValid()) rocket.remove(); cancel(); return; }
                Location at = carrier.getLocation();
                w.spawnParticle(Particle.FLAME, at, 3, 0.05, 0.05, 0.05, 0.02);
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 2, 0.05, 0.05, 0.05, 0.01);
                if (t % 4 == 0) w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, 0.6f, 0.6f);
                if (age > 120) {   // out of range: explode in the air
                    carrier.remove();
                    rocket.remove();
                    crater(at, p, mk2);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball s) || !s.getPersistentDataContainer().has(rocketKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        Location at = s.getLocation();
        if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            at = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        } else if (event.getHitEntity() != null) {
            at = event.getHitEntity().getLocation();
        }
        Player shooter = s.getShooter() instanceof Player pl ? pl : null;
        Byte kind = s.getPersistentDataContainer().get(rocketKey, PersistentDataType.BYTE);
        s.getPassengers().forEach(org.bukkit.entity.Entity::remove);
        s.remove();
        crater(at, shooter, kind != null && kind == 2);
    }

    private void crater(Location at, Player by, boolean mk2) {
        World w = at.getWorld();
        String cfg = mk2 ? "rpg-mk2." : "rpg.";
        float power = (float) plugin.getConfig().getDouble(cfg + "explosion-power", mk2 ? 16 : 8);
        double radius = plugin.getConfig().getDouble(cfg + "crater-radius", mk2 ? 15 : 8);
        boolean breakBlocks = plugin.getConfig().getBoolean(cfg + "break-blocks", true);
        if (mk2) {
            // heard (and felt) by everyone within 400 blocks
            for (Player pl : w.getPlayers()) {
                double d = pl.getLocation().distance(at);
                if (d > 400) continue;
                float vol = (float) Math.max(0.4, 3.0 - d / 120);
                pl.playSound(pl.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, vol, 0.4f);
                pl.playSound(pl.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, vol, 0.5f);
                pl.playSound(pl.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, vol, 0.4f);
                if (d < 120) for (int i = 0; i < 4; i++) plugin.getServer().getScheduler().runTaskLater(plugin, () -> pl.sendHurtAnimation((float) Math.random() * 360), i * 3L);
            }
            Fx.safe(w, Particle.FLASH, at, 3, 2, 2, 2, 0, org.bukkit.Color.WHITE);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 12, radius / 2, radius / 3, radius / 2, 0);
            w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, at, 80, radius / 2, 2, radius / 2, 0.08);
        }
        w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 4, 1.5, 1.5, 1.5, 0);
        w.spawnParticle(Particle.FLAME, at, 150, radius / 2, radius / 3, radius / 2, 0.15);
        w.spawnParticle(Particle.LARGE_SMOKE, at, 120, radius / 2, radius / 3, radius / 2, 0.08);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
        w.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.5f);
        if (breakBlocks && radius > 0) {
            // carve a bowl-shaped crater (deeper in the middle), leaving bedrock, and scorch the rim
            ThreadLocalRandom r = ThreadLocalRandom.current();
            int R = (int) Math.ceil(radius);
            for (int x = -R; x <= R; x++) for (int y = -R; y <= R / 2; y++) for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + (y * 1.6) * (y * 1.6) + z * z) + r.nextDouble() * 1.2;
                if (d > radius) continue;
                Block b = at.getBlock().getRelative(x, y, z);
                Material m = b.getType();
                if (m.isAir() || m == Material.BEDROCK || m == Material.END_PORTAL_FRAME || m == Material.BARRIER || m == Material.END_PORTAL) continue;
                if (b.getState() instanceof org.bukkit.block.Container) continue;   // never wipe chests
                b.setType(Material.AIR, false);
            }
            for (int i = 0; i < R * 6; i++) {
                double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(radius * 0.4, radius + 1);
                Block top = w.getHighestBlockAt(at.getBlockX() + (int) (Math.cos(a) * d), at.getBlockZ() + (int) (Math.sin(a) * d));
                if (top.getY() > at.getBlockY() + R || top.getType() == Material.BEDROCK) continue;
                Material scorch = r.nextInt(4) == 0 ? Material.MAGMA_BLOCK : r.nextBoolean() ? Material.BLACKSTONE : Material.COAL_BLOCK;
                if (top.getType().isSolid() && !(top.getState() instanceof org.bukkit.block.Container)) top.setType(scorch, false);
                if (r.nextInt(3) == 0 && top.getRelative(0, 1, 0).isEmpty()) top.getRelative(0, 1, 0).setType(Material.FIRE);
            }
        }
        w.createExplosion(at, power, plugin.getConfig().getBoolean("rpg.fire", true), breakBlocks, by);
    }
}
