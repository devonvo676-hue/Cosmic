package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.fx.Fx;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2tou's armor bay: place it, walk into it, and robotic arms build the Nova Armor onto him piece by piece
 * (an animation only he can see). Walk in again to take it off. In the suit he can fly (double-tap jump),
 * fire repulsor blasts from his hands (right-click with an empty hand) and shrug off falls.
 * Sneak + left-click the bay to pick it back up.
 */
public class ArmorBay implements Listener {
    public static final String SUIT = "nova_armor";
    private static final String TAG = "cosmic_armor_bay";

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey itemKey, bayKey;
    private final File file;
    private final Map<String, Location> bays = new HashMap<>();          // id -> centre
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> insideBay = new ConcurrentHashMap<>();
    private final Set<UUID> suited = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastBlast = new HashMap<>();

    public ArmorBay(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "armor_bay_item");
        this.bayKey = new NamespacedKey(plugin, "armor_bay");
        this.file = new File(plugin.getDataFolder(), "armor-bays.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String id : yml.getKeys(false)) {
            Location l = yml.getLocation(id);
            if (l != null) bays.put(id, l);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 4L);
    }

    public ItemStack create() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(plugin.mm("<!italic><gradient:#7b2cbf:#00e5ff><bold>Nova Armor Bay"));
        m.lore(List.of(plugin.mm("<!italic><gray>Right click a block to place it."), plugin.mm("<!italic><gray>Walk into it to suit up / take the suit off."),
                plugin.mm("<!italic><gray>Sneak + hit it to pick it back up."), plugin.mm("<!italic><dark_red>v2tou only")));
        m.setItemModel(new NamespacedKey("cosmic", "armor_bay_item"));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isBayItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Location> e : bays.entrySet()) yml.set(e.getKey(), e.getValue());
        try { yml.save(file); } catch (IOException ex) { plugin.getLogger().warning("Couldn't save armor-bays.yml"); }
    }

    // ------------------------------------------------------------------ placing / removing

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player p = event.getPlayer();
        if (!isBayItem(p.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        if (!Owner.allowed(plugin, p)) { p.getInventory().setItemInMainHand(null); return; }
        Location at = event.getClickedBlock().getRelative(event.getBlockFace() == BlockFace.UP ? BlockFace.UP : BlockFace.UP).getLocation().add(0.5, 0, 0.5);
        float yaw = Math.round(p.getLocation().getYaw() / 90f) * 90f + 180f;   // faces the player
        at.setYaw(yaw);
        at.setPitch(0);
        String id = UUID.randomUUID().toString().substring(0, 8);
        bays.put(id, at);
        save();
        build(id, at);
        p.getInventory().getItemInMainHand().setAmount(p.getInventory().getItemInMainHand().getAmount() - 1);
        p.playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.4f);
        p.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
    }

    /** The bay itself: a glowing platform, two pillars with an arch, and a hitbox. Rebuilt on every restart. */
    private void build(String id, Location at) {
        World w = at.getWorld();
        if (w == null) return;
        float yaw = (float) Math.toRadians(-at.getYaw());
        Location base = at.clone(); base.setYaw(0); base.setPitch(0);
        part(id, base, Material.POLISHED_BLACKSTONE, -1.1f, 0, -1.1f, 2.2f, 0.18f, 2.2f, yaw, false);
        part(id, base, Material.CYAN_STAINED_GLASS, -0.8f, 0.18f, -0.8f, 1.6f, 0.04f, 1.6f, yaw, true);
        for (int side = -1; side <= 1; side += 2) {
            part(id, base, Material.NETHERITE_BLOCK, side * 1.0f - 0.15f, 0.18f, 0.55f, 0.3f, 2.6f, 0.3f, yaw, false);
            part(id, base, Material.PURPLE_STAINED_GLASS, side * 1.0f - 0.05f, 0.4f, 0.5f, 0.1f, 2.2f, 0.05f, yaw, true);
        }
        part(id, base, Material.NETHERITE_BLOCK, -1.15f, 2.75f, 0.5f, 2.3f, 0.3f, 0.35f, yaw, false);
        part(id, base, Material.SEA_LANTERN, -0.5f, 2.62f, 0.55f, 1.0f, 0.12f, 0.2f, yaw, true);
        w.spawn(base.clone().add(0, 0, 0), Interaction.class, i -> {
            i.setInteractionWidth(2.2f);
            i.setInteractionHeight(0.3f);
            i.setPersistent(false);
            i.addScoreboardTag(TAG);
            i.getPersistentDataContainer().set(bayKey, PersistentDataType.STRING, id);
        });
    }

    private void part(String id, Location at, Material m, float x, float y, float z, float sx, float sy, float sz, float yaw, boolean glow) {
        at.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.addScoreboardTag(Fx.KEEP);
            d.getPersistentDataContainer().set(bayKey, PersistentDataType.STRING, id);
            if (glow) d.setBrightness(new Display.Brightness(15, 15));
            Quaternionf r = new Quaternionf().rotateY(yaw);
            Vector3f tr = r.transform(new Vector3f(x, y, z));
            d.setTransformation(new Transformation(tr, r, new Vector3f(sx, sy, sz), new Quaternionf()));
        });
    }

    /** Respawns every saved bay (call once the worlds are loaded). */
    public void rebuildAll() {
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        for (Map.Entry<String, Location> e : bays.entrySet()) build(e.getKey(), e.getValue());
    }

    @EventHandler
    public void onHitBay(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction i) || !i.getScoreboardTags().contains(TAG)) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (!p.isSneaking() || !Owner.allowed(plugin, p)) return;
        String id = i.getPersistentDataContainer().get(bayKey, PersistentDataType.STRING);
        if (id == null) return;
        bays.remove(id);
        save();
        for (Entity e : i.getWorld().getEntities()) {
            if (id.equals(e.getPersistentDataContainer().get(bayKey, PersistentDataType.STRING))) e.remove();
        }
        p.getInventory().addItem(create());
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------ walking in

    private void tick() {
        thrusters();
        Player p = Bukkit.getPlayerExact(Owner.NAME);
        if (p == null || busy.contains(p.getUniqueId())) return;
        String in = null;
        for (Map.Entry<String, Location> e : bays.entrySet()) {
            Location c = e.getValue();
            if (c.getWorld() == null || !c.getWorld().equals(p.getWorld())) continue;
            if (p.getLocation().distanceSquared(c) < 0.55 * 0.55 + 0.3 && Math.abs(p.getLocation().getY() - c.getY() - 0.2) < 0.6) { in = e.getKey(); break; }
        }
        String was = insideBay.get(p.getUniqueId());
        if (in == null) { insideBay.remove(p.getUniqueId()); return; }
        if (in.equals(was)) return;   // still standing in it since last time
        insideBay.put(p.getUniqueId(), in);
        if (suited.contains(p.getUniqueId())) suitDown(p, bays.get(in));
        else suitUp(p, bays.get(in));
    }

    /** The suit-up sequence. Every effect here is only sent to (or only visible to) v2tou. */
    private void suitUp(Player p, Location bay) {
        busy.add(p.getUniqueId());
        Location stand = bay.clone().add(0, 0.2, 0);
        stand.setYaw(bay.getYaw() + 180f);   // facing out of the bay
        stand.setPitch(0);
        p.teleport(stand);
        p.setVelocity(new Vector());
        World w = p.getWorld();
        float yaw = (float) Math.toRadians(-bay.getYaw());
        Vector right = new Vector(Math.cos(yaw), 0, -Math.sin(yaw));   // bay's local X
        Vector back = new Vector(Math.sin(yaw), 0, Math.cos(yaw)).multiply(-1);
        String[] names = {"Boots", "Leg plates", "Chest plate", "Helmet"};
        com.cosmicabilities.admin.Suits.Piece[] pieces = {Suits.Piece.BOOTS, Suits.Piece.LEGS, Suits.Piece.CHEST, Suits.Piece.HELMET};
        double[] heights = {0.15, 0.7, 1.3, 1.75};
        List<Entity> mine = new ArrayList<>();
        // two robotic arms that reach in from the pillars
        BlockDisplay[] arms = new BlockDisplay[2];
        for (int s = 0; s < 2; s++) {
            int side = s == 0 ? -1 : 1;
            Location a = bay.clone().add(right.clone().multiply(side * 1.0)).add(0, 1.2, 0);
            a.setYaw(0); a.setPitch(0);
            arms[s] = privateBlock(p, a, Material.NETHERITE_BLOCK, new Vector3f(0.12f, 0.12f, 0.9f));
            mine.add(arms[s]);
        }
        p.showTitle(Title.title(plugin.mm("<gradient:#7b2cbf:#00e5ff><bold>NOVA ARMOR"), plugin.mm("<gray>assembling..."),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2400), Duration.ofMillis(400))));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.8f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline()) { mine.forEach(Entity::remove); busy.remove(p.getUniqueId()); cancel(); return; }
                // keep him in place
                if (p.getLocation().distanceSquared(stand) > 0.04) {
                    Location keep = stand.clone(); keep.setYaw(p.getLocation().getYaw()); keep.setPitch(p.getLocation().getPitch());
                    p.teleport(keep);
                }
                int stage = t / 22, k = t % 22;
                if (stage < pieces.length) {
                    double h = heights[stage];
                    // arms swing to the height of the piece being fitted
                    for (int s = 0; s < 2; s++) {
                        int side = s == 0 ? -1 : 1;
                        Location a = bay.clone().add(right.clone().multiply(side * (1.0 - Math.min(1, k / 8.0) * 0.55))).add(0, h + 0.1, 0);
                        a.setYaw(0); a.setPitch(0);
                        arms[s].teleport(a);
                    }
                    if (k == 0) {
                        // the piece slides out from the back of the bay
                        ItemDisplay piece = privateItem(p, bay.clone().add(back.clone().multiply(0.9)).add(0, h, 0),
                                plugin.suits().piece(SUIT, "", pieces[stage]));
                        mine.add(piece);
                        Location target = stand.clone().add(0, h, 0);
                        target.setYaw(0); target.setPitch(0);
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (piece.isValid()) piece.teleport(target); }, 2L);
                        p.playSound(p.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.8f, 1.4f);
                    }
                    if (k > 8 && k < 18 && k % 2 == 0) {   // welding sparks, only for him
                        Location spark = stand.clone().add(right.clone().multiply((k % 4 == 0 ? 0.35 : -0.35))).add(0, h, 0);
                        p.spawnParticle(Particle.ELECTRIC_SPARK, spark, 8, 0.05, 0.05, 0.05, 0.15);
                        p.spawnParticle(Particle.LAVA, spark, 1, 0, 0, 0, 0);
                        p.playSound(spark, Sound.BLOCK_SMITHING_TABLE_USE, 0.5f, 1.8f);
                    }
                    if (k == 18) {
                        plugin.suits().equip(p, SUIT, "<gradient:#7b2cbf:#00e5ff><bold>Nova Armor</bold> <gray>" + names[stage], pieces[stage]);
                        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.8f);
                        p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 0.6f);
                        for (Entity e : new ArrayList<>(mine)) if (e instanceof ItemDisplay) { e.remove(); mine.remove(e); }
                    }
                } else if (t == pieces.length * 22 + 2) {
                    // power-up: visor lights, glow lines, a pulse only he sees
                    p.spawnParticle(Particle.END_ROD, stand.clone().add(0, 1, 0), 60, 0.3, 0.8, 0.3, 0.08);
                    p.spawnParticle(Particle.DUST, stand.clone().add(0, 1.1, 0), 40, 0.25, 0.5, 0.25, 0, new Particle.DustOptions(Color.fromRGB(0x00e5ff), 1.2f));
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
                    p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.6f);
                    p.showTitle(Title.title(plugin.mm("<#00e5ff><bold>SYSTEMS ONLINE"), plugin.mm("<gray>double-tap jump to fly  |  right-click empty hand: repulsor"),
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2200), Duration.ofMillis(500))));
                } else if (t > pieces.length * 22 + 6) {
                    mine.forEach(Entity::remove);
                    cancel();
                    suited.add(p.getUniqueId());
                    busy.remove(p.getUniqueId());
                    if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) p.setAllowFlight(true);
                    p.setFlySpeed(0.12f);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false, true));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, true));
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void suitDown(Player p, Location bay) {
        busy.add(p.getUniqueId());
        Location stand = bay.clone().add(0, 0.2, 0);
        p.playSound(p.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 1f, 0.8f);
        p.spawnParticle(Particle.ELECTRIC_SPARK, stand.clone().add(0, 1, 0), 40, 0.3, 0.7, 0.3, 0.1);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            takeOff(p);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.6f);
            busy.remove(p.getUniqueId());
        }, 20L);
    }

    private void takeOff(Player p) {
        if (!suited.remove(p.getUniqueId())) return;
        plugin.suits().remove(p);
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) { p.setFlying(false); p.setAllowFlight(false); }
        p.setFlySpeed(0.1f);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }

    private BlockDisplay privateBlock(Player viewer, Location at, Material m, Vector3f scale) {
        BlockDisplay d = at.getWorld().spawn(at, BlockDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setBlock(m.createBlockData());
            e.setPersistent(false);
            e.addScoreboardTag(Fx.TAG);
            e.setTeleportDuration(4);
            e.setTransformation(new Transformation(new Vector3f(-scale.x / 2, -scale.y / 2, -scale.z / 2), new Quaternionf(), scale, new Quaternionf()));
        });
        viewer.showEntity(plugin, d);
        return d;
    }

    private ItemDisplay privateItem(Player viewer, Location at, ItemStack item) {
        Location l = at.clone(); l.setYaw(0); l.setPitch(0);
        ItemDisplay d = at.getWorld().spawn(l, ItemDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setPersistent(false);
            e.addScoreboardTag(Fx.TAG);
            e.setTeleportDuration(14);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf()));
        });
        viewer.showEntity(plugin, d);
        return d;
    }

    // ------------------------------------------------------------------ suit powers

    @EventHandler
    public void onRepulsor(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!suited.contains(p.getUniqueId()) || event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!p.getInventory().getItemInMainHand().getType().isAir()) return;
        long now = System.currentTimeMillis();
        if (now - lastBlast.getOrDefault(p.getUniqueId(), 0L) < 600) return;
        lastBlast.put(p.getUniqueId(), now);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location hand = eye.clone().add(dir.clone().multiply(0.6)).add(new Vector(-dir.getZ(), 0, dir.getX()).normalize().multiply(0.35)).subtract(0, 0.35, 0);
        RayTraceResult hit = w.rayTrace(hand, dir, 40, org.bukkit.FluidCollisionMode.NEVER, true, 0.6, e -> e instanceof LivingEntity && !e.equals(p));
        Location end = hit != null ? hit.getHitPosition().toLocation(w) : hand.clone().add(dir.clone().multiply(40));
        Fx.ray(hand, end, "beam_cyan", 0.3f, 4);
        w.spawnParticle(Particle.END_ROD, end, 20, 0.2, 0.2, 0.2, 0.1);
        w.playSound(hand, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 2f);
        w.playSound(hand, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 2f);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity le) {
            le.setNoDamageTicks(0);
            le.damage(plugin.getConfig().getDouble("nova-armor.repulsor-damage", 8), p);
            le.setVelocity(dir.clone().multiply(1.2).setY(0.4));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        if (suited.contains(p.getUniqueId()) && event.isFlying()) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.6f);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 20, 0.2, 0.05, 0.2, 0.05);
        }
    }

    /** Boot thrusters while flying. */
    public void thrusters() {
        for (UUID id : suited) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isFlying()) continue;
            Location feet = p.getLocation();
            p.getWorld().spawnParticle(Particle.FLAME, feet, 3, 0.12, 0.02, 0.12, 0.01);
            p.getWorld().spawnParticle(Particle.DUST, feet, 2, 0.1, 0.02, 0.1, 0, new Particle.DustOptions(Color.fromRGB(0x00e5ff), 1f));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && suited.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        takeOff(event.getPlayer());
        insideBay.remove(event.getPlayer().getUniqueId());
        busy.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player p = event.getPlayer();
        if (suited.remove(p.getUniqueId())) {
            p.setFlySpeed(0.1f);
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) p.setAllowFlight(false);
        }
    }

    public void shutdown() {
        for (UUID id : suited.toArray(new UUID[0])) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) takeOff(p);
        }
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
    }
}
