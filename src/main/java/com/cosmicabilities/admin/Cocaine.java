package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.fx.Fx;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Admin-only "cocaine": right click to sniff it. You bulk up (grow bigger, Strength / Speed / Resistance),
 * your eyes glow red and your fists pulse with veins. While it lasts, right-click someone to grab them,
 * shake them violently and slam them down through the ground. Everything is tunable under cocaine.* in config.
 */
public class Cocaine implements Listener {
    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey key, scaleKey;
    private final Map<UUID, Rage> raging = new ConcurrentHashMap<>();
    private final Set<UUID> grabbed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastGrab = new HashMap<>();

    private static final class Rage {
        long until;
        BlockDisplay[] eyes;
        BukkitRunnable task;
    }

    public Cocaine(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "cocaine");
        this.scaleKey = new NamespacedKey(plugin, "cocaine_scale");
    }

    public ItemStack create() {
        ItemStack it = new ItemStack(Material.SUGAR);
        ItemMeta m = it.getItemMeta();
        m.displayName(plugin.mm("<!italic><white><bold>Cocaine"));
        m.lore(java.util.List.of(plugin.mm("<!italic><gray>Right click: sniff it and get huge"),
                plugin.mm("<!italic><gray>Then right click someone to grab them,"),
                plugin.mm("<!italic><gray>shake them and slam them through the ground."),
                plugin.mm("<!italic><dark_red>Owner only")));
        m.setItemModel(new NamespacedKey("cosmic", "cocaine"));
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isCocaine(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public boolean isRaging(UUID id) {
        Rage r = raging.get(id);
        return r != null && r.until > System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ sniff

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isCocaine(hand)) return;
        event.setCancelled(true);
        if (!Owner.allowed(plugin, p)) {
            p.getInventory().setItemInMainHand(null);
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>You can't use that."));
            return;
        }
        if (isRaging(p.getUniqueId())) return;
        if (plugin.getConfig().getBoolean("cocaine.consume", false)) hand.setAmount(hand.getAmount() - 1);
        sniff(p);
    }

    private void sniff(Player p) {
        World w = p.getWorld();
        int seconds = Math.max(5, plugin.getConfig().getInt("cocaine.duration-seconds", 30));
        double grow = plugin.getConfig().getDouble("cocaine.size", 0.4);
        Rage rage = new Rage();
        rage.until = System.currentTimeMillis() + seconds * 1000L + 2000;
        raging.put(p.getUniqueId(), rage);

        // the sniff
        Location nose = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.3)).subtract(0, 0.1, 0);
        w.spawnParticle(Particle.ITEM, nose, 25, 0.08, 0.05, 0.08, 0.02, new ItemStack(Material.SUGAR));
        w.spawnParticle(Particle.WHITE_ASH, nose, 40, 0.15, 0.1, 0.15, 0.01);
        w.playSound(p.getLocation(), Sound.ENTITY_SNIFFER_SNIFFING, 1.5f, 1.4f);
        w.playSound(p.getLocation(), Sound.ENTITY_FOX_SNIFF, 1.5f, 0.8f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));

        // red eyes (hidden from the user themselves so they don't block the view)
        rage.eyes = new BlockDisplay[2];
        for (int i = 0; i < 2; i++) {
            BlockDisplay eye = w.spawn(p.getEyeLocation(), BlockDisplay.class, d -> {
                d.setBlock(Material.RED_CONCRETE.createBlockData());
                d.setBrightness(new Display.Brightness(15, 15));
                d.setGlowing(true);
                d.setGlowColorOverride(org.bukkit.Color.fromRGB(0xff0000));
                d.setPersistent(false);
                d.addScoreboardTag(Fx.TAG);
                d.setTeleportDuration(1);
                d.setTransformation(new Transformation(new Vector3f(-0.035f, -0.025f, -0.035f), new Quaternionf(), new Vector3f(0.07f, 0.05f, 0.07f), new Quaternionf()));
            });
            p.hideEntity(plugin, eye);
            rage.eyes[i] = eye;
        }
        PotionEffectType[] buffs = {PotionEffectType.STRENGTH, PotionEffectType.SPEED, PotionEffectType.RESISTANCE, PotionEffectType.JUMP_BOOST, PotionEffectType.HASTE};
        int[] levels = {plugin.getConfig().getInt("cocaine.strength", 3), 2, 2, 1, 2};
        rage.task = new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) { end(p); return; }
                long left = rage.until - System.currentTimeMillis();
                // bulk up over 2 seconds, shrink back over the last second
                double prog = Math.min(1, t / 40.0);
                double shrink = left < 1000 ? Math.max(0, left / 1000.0) : 1;
                setScale(p, grow * smooth(prog) * shrink);
                if (t == 10) {
                    w.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.2f, 1.3f);
                    p.showTitle(Title.title(plugin.mm("<dark_red><bold>ROID RAGE"), plugin.mm("<red>Right click someone to grab them"),
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(400))));
                }
                if (t < 40 && t % 4 == 0) {
                    w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.4f);
                    w.spawnParticle(Particle.DUST, p.getLocation().add(0, p.getHeight() * 0.6, 0), 12, 0.4, 0.5, 0.4, 0, Fx.dust(0xb00000, 1.4f));
                    w.spawnParticle(Particle.ANGRY_VILLAGER, p.getEyeLocation().add(0, 0.4, 0), 1, 0.2, 0.1, 0.2, 0);
                }
                if (t == 40) for (int i = 0; i < buffs.length; i++) {
                    p.addPotionEffect(new PotionEffect(buffs[i], seconds * 20, Math.max(0, levels[i] - 1), false, false, true));
                }
                if (t % 30 == 0 && t > 40) w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.6f);
                // eyes follow the face
                Location eye = p.getEyeLocation();
                Vector f = eye.getDirection().normalize();
                Vector right = f.clone().crossProduct(new Vector(0, 1, 0));
                if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
                right.normalize();
                double s = 1 + grow * smooth(prog) * shrink;
                for (int i = 0; i < 2; i++) {
                    Location at = eye.clone().add(f.clone().multiply(0.255 * s)).add(right.clone().multiply((i == 0 ? 0.11 : -0.11) * s));
                    at.setYaw(0); at.setPitch(0);
                    if (rage.eyes[i].isValid()) rage.eyes[i].teleport(at);
                }
                // veiny fists: throbbing red veins around both hands
                if (t % 2 == 0) {
                    Location chest = p.getLocation().add(0, p.getHeight() * 0.5, 0);
                    Vector bf = p.getLocation().getDirection().setY(0);
                    if (bf.lengthSquared() < 1e-4) bf = new Vector(0, 0, 1);
                    bf.normalize();
                    Vector br = bf.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                    for (int side = -1; side <= 1; side += 2) {
                        Location fist = chest.clone().add(br.clone().multiply(side * 0.42 * s)).add(bf.clone().multiply(0.2 * s)).subtract(0, 0.12 * s, 0);
                        w.spawnParticle(Particle.DUST, fist, 3, 0.08 * s, 0.12 * s, 0.08 * s, 0, Fx.dust(t % 8 < 4 ? 0x8b0000 : 0xd00000, 0.7f));
                        w.spawnParticle(Particle.DUST, fist, 1, 0.05, 0.05, 0.05, 0, Fx.dust(0x4b0082, 0.6f));
                    }
                }
                if (left <= 0) { end(p); return; }
                t++;
            }
        };
        rage.task.runTaskTimer(plugin, 1L, 1L);
    }

    private static double smooth(double x) { return x * x * (3 - 2 * x); }

    private void setScale(Player p, double add) {
        AttributeInstance inst = p.getAttribute(Attribute.SCALE);
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(scaleKey);
        if (old != null) inst.removeModifier(old);
        if (add > 0.001) inst.addModifier(new AttributeModifier(scaleKey, add, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
    }

    private void end(Player p) {
        Rage r = raging.remove(p.getUniqueId());
        if (r == null) return;
        if (r.task != null) r.task.cancel();
        if (r.eyes != null) for (BlockDisplay e : r.eyes) if (e != null && e.isValid()) e.remove();
        setScale(p, 0);
        if (p.isOnline()) {
            for (PotionEffectType t : new PotionEffectType[]{PotionEffectType.STRENGTH, PotionEffectType.SPEED, PotionEffectType.RESISTANCE,
                    PotionEffectType.JUMP_BOOST, PotionEffectType.HASTE}) p.removePotionEffect(t);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1f, 0.7f);
        }
    }

    // ------------------------------------------------------------------ grab, shake, slam

    @EventHandler(priority = EventPriority.HIGH)
    public void onGrab(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        if (!isRaging(p.getUniqueId())) return;
        if (!(event.getRightClicked() instanceof LivingEntity target) || target instanceof org.bukkit.entity.ArmorStand) return;
        event.setCancelled(true);
        if (grabbed.contains(target.getUniqueId()) || grabbed.contains(p.getUniqueId())) return;
        if (target instanceof Player tp && (plugin.afk().isAfk(tp.getUniqueId()) || tp.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || tp.getGameMode() == org.bukkit.GameMode.CREATIVE)) return;
        long now = System.currentTimeMillis();
        long cd = (long) (plugin.getConfig().getDouble("cocaine.grab-cooldown-seconds", 3) * 1000);
        if (now - lastGrab.getOrDefault(p.getUniqueId(), 0L) < cd) return;
        lastGrab.put(p.getUniqueId(), now);
        grab(p, target);
    }

    private void grab(Player p, LivingEntity target) {
        World w = p.getWorld();
        grabbed.add(target.getUniqueId());
        int shake = Math.max(10, (int) (plugin.getConfig().getDouble("cocaine.shake-seconds", 1.6) * 20));
        double shakeDmg = plugin.getConfig().getDouble("cocaine.shake-damage", 1);
        w.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 1.4f);
        if (target instanceof Player tp) tp.showTitle(Title.title(plugin.mm("<dark_red><bold>GRABBED"), plugin.mm(""),
                Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(800), Duration.ofMillis(200))));
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !target.isValid() || target.isDead() || !target.getWorld().equals(p.getWorld())) {
                    grabbed.remove(target.getUniqueId());
                    cancel();
                    return;
                }
                Location eye = p.getEyeLocation();
                Vector f = p.getLocation().getDirection().setY(0);
                if (f.lengthSquared() < 1e-4) f = new Vector(0, 0, 1);
                f.normalize();
                AttributeInstance sc = p.getAttribute(Attribute.SCALE);
                double s = sc == null ? 1 : sc.getValue();
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double amp = 0.45;
                Location hold = eye.clone().add(f.clone().multiply(1.3 * s)).subtract(0, target.getHeight() * 0.55, 0)
                        .add(r.nextDouble(-amp, amp), r.nextDouble(-amp, amp) * 0.8, r.nextDouble(-amp, amp));
                Location tl = target.getLocation();
                hold.setYaw(tl.getYaw() + (float) r.nextDouble(-25, 25));
                hold.setPitch(Math.max(-90, Math.min(90, tl.getPitch() + (float) r.nextDouble(-20, 20))));
                target.teleport(hold);
                target.setFallDistance(0);
                target.setVelocity(new Vector());
                if (t % 3 == 0) {
                    w.playSound(hold, Sound.ENTITY_PLAYER_HURT, 0.7f, 0.6f + r.nextFloat() * 0.6f);
                    w.spawnParticle(Particle.SWEEP_ATTACK, hold.clone().add(0, target.getHeight() / 2, 0), 1);
                }
                if (t % 8 == 0 && shakeDmg > 0) {
                    target.setNoDamageTicks(0);
                    target.damage(shakeDmg, p);
                }
                if (t >= shake) {
                    cancel();
                    slam(p, target);
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void slam(Player p, LivingEntity target) {
        World w = target.getWorld();
        Location at = target.getLocation();
        // find the ground under them
        Block ground = at.getBlock();
        for (int i = 0; i < 12 && ground.isPassable() && ground.getY() > w.getMinHeight(); i++) ground = ground.getRelative(0, -1, 0);
        int depth = Math.max(0, plugin.getConfig().getInt("cocaine.slam-depth", 4));
        boolean breakBlocks = plugin.getConfig().getBoolean("cocaine.break-blocks", true);
        Location bottom = ground.getLocation().add(0.5, 1, 0.5);
        if (breakBlocks && depth > 0) {
            for (int y = 0; y < depth; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                if (Math.abs(x) + Math.abs(z) == 2 && y > 1) continue;   // rounder hole as it goes down
                Block b = ground.getRelative(x, -y, z);
                Material m = b.getType();
                if (m.isAir() || m == Material.BEDROCK || m == Material.BARRIER || m == Material.END_PORTAL_FRAME
                        || b.getState() instanceof org.bukkit.block.Container || m.getHardness() < 0) continue;
                w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 12, 0.4, 0.4, 0.4, 0.1, b.getBlockData());
                b.setType(Material.AIR, false);
            }
            Block floor = ground.getRelative(0, -depth, 0);
            if (floor.getType() != Material.BEDROCK) bottom = floor.getLocation().add(0.5, 1, 0.5);
        }
        bottom.setYaw(at.getYaw());
        bottom.setPitch(at.getPitch());
        target.teleport(bottom);
        target.setVelocity(new Vector(0, -1.5, 0));
        target.setFallDistance(0);
        target.setNoDamageTicks(0);
        target.damage(plugin.getConfig().getDouble("cocaine.slam-damage", 14), p);
        Location rim = ground.getLocation().add(0.5, 1.05, 0.5);
        w.playSound(rim, Sound.BLOCK_ANVIL_LAND, 2f, 0.5f);
        w.playSound(rim, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        w.playSound(rim, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2f, 0.6f);
        w.spawnParticle(Particle.EXPLOSION, rim, 4, 1, 0.2, 1, 0);
        w.spawnParticle(Particle.BLOCK, rim, 80, 1.5, 0.3, 1.5, 0.3, ground.getBlockData().getMaterial().isAir()
                ? Material.DIRT.createBlockData() : ground.getBlockData());
        for (int r = 1; r <= 3; r++) Fx.ring(w, rim, r * 1.2, 16 + r * 8, r, Fx.dust(0x8b0000, 1.6f));
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            Fx.decal(rim.clone().add(Math.cos(a) * 1.8, -0.03, Math.sin(a) * 1.8), Material.COARSE_DIRT, 1.3f, (float) a, 120);
        }
        for (Entity e : w.getNearbyEntities(rim, 3.5, 2, 3.5)) {
            if (e.equals(p) || e.equals(target) || !(e instanceof LivingEntity le)) continue;
            Vector v = e.getLocation().toVector().subtract(rim.toVector()).setY(0);
            if (v.lengthSquared() > 0.01) le.setVelocity(v.normalize().multiply(0.8).setY(0.5));
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> grabbed.remove(target.getUniqueId()), 10L);
    }

    // ------------------------------------------------------------------ cleanup

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer());
        grabbed.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        end(event.getPlayer());
    }

    /** If the server stopped mid-rage, take the size boost back off. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        setScale(event.getPlayer(), 0);
    }

    public void shutdown() {
        for (UUID id : new java.util.ArrayList<>(raging.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) end(p);
        }
    }
}
