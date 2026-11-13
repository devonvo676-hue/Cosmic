package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.fx.Fx;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v2tou's Nova Mech case. Right-click a block: the case lands, pops open and a giant robot mech unfolds out of it,
 * transformer-style, and kneels there waiting. Right-click the mech to climb into the cockpit.
 *   WASD walk (it turns where you look)   SPRINT run   SPACE thruster jump (hold to hover)
 *   LEFT CLICK arm cannon                 F (or right click with an item) missile salvo
 *   SNEAK climb out (it kneels and lets you down)
 * Sneak + right-click the empty mech to fold it back into the case.
 */
public class ArmorBay implements Listener {
    private static final String TAG = "cosmic_mech";
    private static final float S = 4f;                    // model scale: 4 model units per block
    private static final double HIP = 4.6, THIGH = 2.35, LEG_X = 1.25, ARM_X = 3.0, SHOULDER = 3.6;
    private static final Vector3f SEAT = new Vector3f(0, 4.2f, 0.5f);

    private final class Mech {
        String id;
        Location root;                 // ground under its feet
        float yaw;
        ItemDisplay torso, thighL, thighR, shinL, shinR, armL, cannon, seat;
        Interaction hitbox;
        UUID pilot;
        double kneel = 1, phase, move, vy, lastSin;
        boolean fwd, back, left, right, sprint, jump, busy, scripted;
        int aimTicks, t, lastShot = -100, lastSalvo = -100, thrust;
        float aim;                     // cannon arm angle while aiming (radians)

        List<ItemDisplay> parts() { return List.of(torso, thighL, thighR, shinL, shinR, armL, cannon); }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey itemKey, mechKey;
    private final File file;
    private final Map<String, Mech> mechs = new ConcurrentHashMap<>();
    private final Map<UUID, Mech> piloting = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> leaving = ConcurrentHashMap.newKeySet();

    public ArmorBay(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "armor_bay_item");
        this.mechKey = new NamespacedKey(plugin, "mech_id");
        File old = new File(plugin.getDataFolder(), "armor-bays.yml");
        if (old.exists() && !old.delete()) plugin.getLogger().warning("Couldn't delete old armor-bays.yml");
        this.file = new File(plugin.getDataFolder(), "mechs.yml");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 1L);
    }

    public ItemStack create() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(plugin.mm("<!italic><gradient:#7b2cbf:#00e5ff><bold>Nova Mech Case"));
        m.lore(List.of(plugin.mm("<!italic><gray>Right click a block: a giant mech"), plugin.mm("<!italic><gray>unfolds out of the case."),
                plugin.mm("<!italic><gray>Right click the mech: climb in."), plugin.mm("<!italic><gray>Sneak + right click it: pack it up."),
                plugin.mm("<!italic><dark_red>v2tou only")));
        m.setItemModel(new NamespacedKey("cosmic", "armor_bay_item"));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isCase(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ saving

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Mech m : mechs.values()) {
            Location l = m.root.clone();
            l.setYaw(m.yaw);
            yml.set(m.id, l);
        }
        try { yml.save(file); } catch (IOException e) { plugin.getLogger().warning("Couldn't save mechs.yml"); }
    }

    /** Puts every parked mech back after a restart. */
    public void rebuildAll() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String id : yml.getKeys(false)) {
            Location l = yml.getLocation(id);
            if (l == null || l.getWorld() == null) continue;
            Mech m = spawnMech(id, l, l.getYaw());
            m.kneel = 1;
            pose(m, 1);
        }
    }

    // ------------------------------------------------------------------ building the model

    private static ItemStack model(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("cosmic", name));
        it.setItemMeta(m);
        return it;
    }

    private ItemDisplay part(Location at, String name) {
        Location l = at.clone(); l.setYaw(0); l.setPitch(0);
        return at.getWorld().spawn(l, ItemDisplay.class, d -> {
            if (name != null) d.setItemStack(model(name));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.addScoreboardTag(Fx.KEEP);
            d.addScoreboardTag(TAG);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setDisplayWidth(9);
            d.setDisplayHeight(12);
            d.setViewRange(6f);
        });
    }

    private Mech spawnMech(String id, Location root, float yaw) {
        Mech m = new Mech();
        m.id = id;
        m.root = root.clone();
        m.root.setYaw(0); m.root.setPitch(0);
        m.yaw = yaw;
        m.torso = part(root, "boss_mech_torso");
        m.thighL = part(root, "boss_mech_thigh");
        m.thighR = part(root, "boss_mech_thigh");
        m.shinL = part(root, "boss_mech_shin");
        m.shinR = part(root, "boss_mech_shin");
        m.armL = part(root, "boss_mech_arm");
        m.cannon = part(root, "boss_mech_cannon");
        m.seat = part(root.clone().add(0, HIP, 0), null);
        m.hitbox = root.getWorld().spawn(m.root, Interaction.class, i -> {
            i.setInteractionWidth(3.6f);
            i.setInteractionHeight(9.5f);
            i.setResponsive(true);
            i.setPersistent(false);
            i.addScoreboardTag(Fx.TAG);
            i.addScoreboardTag(TAG);
            i.getPersistentDataContainer().set(mechKey, PersistentDataType.STRING, id);
        });
        mechs.put(id, m);
        return m;
    }

    private void despawn(Mech m) {
        for (ItemDisplay d : m.parts()) if (d != null && d.isValid()) d.remove();
        if (m.seat != null && m.seat.isValid()) { m.seat.eject(); m.seat.remove(); }
        if (m.hitbox != null && m.hitbox.isValid()) m.hitbox.remove();
        mechs.remove(m.id);
    }

    private Quaternionf yawQ(Mech m) { return new Quaternionf().rotateY((float) Math.toRadians(-m.yaw)); }

    /** Where every part sits this tick (walk cycle, kneeling, aiming), interpolated over {@code interp} ticks. */
    private void pose(Mech m, int interp) {
        Quaternionf q = yawQ(m);
        double k = m.kneel, mv = m.move;
        double sin = Math.sin(m.phase), cos = Math.cos(m.phase);
        double bob = -0.14 * mv * Math.abs(sin) + (mv < 0.1 ? Math.sin(m.t * 0.06) * 0.04 : 0);
        double hip = HIP - 2.0 * k + bob;
        float lean = (float) Math.toRadians(12 * k + 4 * mv);
        float swing = (float) Math.toRadians(26 * mv);
        float aL = (float) (-sin * swing - Math.toRadians(60) * k);
        float aR = (float) (sin * swing - Math.toRadians(60) * k);
        float bL = (float) (Math.max(0, cos) * Math.toRadians(40) * mv + Math.toRadians(110) * k);
        float bR = (float) (Math.max(0, -cos) * Math.toRadians(40) * mv + Math.toRadians(110) * k);
        Vector3f torsoAt = new Vector3f(0, (float) hip, 0);
        Quaternionf torsoQ = new Quaternionf(q).rotateX(lean);
        set(m.torso, torsoAt, torsoQ, interp);
        for (int side = 0; side < 2; side++) {
            float a = side == 0 ? aL : aR, b = side == 0 ? bL : bR;
            Vector3f hipAt = q.transform(new Vector3f((float) (side == 0 ? LEG_X : -LEG_X), 0, 0)).add(0, (float) hip, 0);
            Quaternionf thighQ = new Quaternionf(q).rotateX(a);
            Vector3f knee = new Vector3f(hipAt).add(thighQ.transform(new Vector3f(0, (float) -THIGH, 0)));
            set(side == 0 ? m.thighL : m.thighR, hipAt, thighQ, interp);
            set(side == 0 ? m.shinL : m.shinR, knee, new Quaternionf(q).rotateX(a + b), interp);
        }
        Vector3f shoulderL = new Vector3f(torsoAt).add(torsoQ.transform(new Vector3f((float) ARM_X, (float) SHOULDER, 0)));
        Vector3f shoulderR = new Vector3f(torsoAt).add(torsoQ.transform(new Vector3f((float) -ARM_X, (float) SHOULDER, 0)));
        set(m.armL, shoulderL, new Quaternionf(q).rotateX((float) (sin * Math.toRadians(16) * mv - Math.toRadians(20) * k)), interp);
        float cannonA = m.aimTicks > 0 ? m.aim : (float) (-sin * Math.toRadians(16) * mv - Math.toRadians(20) * k);
        set(m.cannon, shoulderR, new Quaternionf(q).rotateX(cannonA), interp);
    }

    private void set(ItemDisplay d, Vector3f at, Quaternionf rot, int interp) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(interp);
        d.setTransformation(new Transformation(at, rot, new Vector3f(S, S, S), new Quaternionf()));
    }

    private Location seatAt(Mech m) {
        Quaternionf torsoQ = new Quaternionf(yawQ(m)).rotateX((float) Math.toRadians(12 * m.kneel + 4 * m.move));
        Vector3f s = torsoQ.transform(new Vector3f(SEAT));
        double hip = HIP - 2.0 * m.kneel;
        return m.root.clone().add(s.x, hip + s.y, s.z);
    }

    private Location shoulderR(Mech m) {
        Quaternionf q = yawQ(m);
        Vector3f s = q.transform(new Vector3f((float) -ARM_X, 0, 0));
        return m.root.clone().add(s.x, HIP - 2.0 * m.kneel + SHOULDER, s.z);
    }

    private Location podL(Mech m) {
        Quaternionf q = yawQ(m);
        Vector3f s = q.transform(new Vector3f((float) ARM_X + 0.9f, 0, 0.9f));
        return m.root.clone().add(s.x, HIP - 2.0 * m.kneel + SHOULDER + 0.6, s.z);
    }

    /** Moves the whole rig to its root (all parts live at the root and are offset by their transformation). */
    private void place(Mech m) {
        Location r = m.root.clone(); r.setYaw(0); r.setPitch(0);
        for (ItemDisplay d : m.parts()) if (d.isValid()) d.teleport(r);
        if (m.hitbox.isValid()) m.hitbox.teleport(r);
        if (m.seat.isValid()) {
            Location s = seatAt(m); s.setYaw(0); s.setPitch(0);
            m.seat.teleport(s, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
    }

    // ------------------------------------------------------------------ case -> mech

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        if (!isCase(p.getInventory().getItemInMainHand())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        event.setCancelled(true);
        if (!Owner.allowed(plugin, p)) { p.getInventory().setItemInMainHand(null); return; }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            p.sendActionBar(plugin.mm("<gray>Right click the <white>ground <gray>to deploy the mech."));
            return;
        }
        Block on = event.getClickedBlock();
        if (event.getBlockFace() != BlockFace.UP) on = p.getWorld().getHighestBlockAt(on.getLocation());
        Location root = on.getLocation().add(0.5, 1, 0.5);
        for (int y = 0; y < 6; y++) if (!root.clone().add(0, y, 0).getBlock().isPassable()) {
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>Not enough room here for the mech."));
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
        unfold(p, root, Math.round(p.getLocation().getYaw() / 45f) * 45f + 180f);
    }

    private void unfold(Player p, Location root, float yaw) {
        World w = root.getWorld();
        String id = UUID.randomUUID().toString().substring(0, 8);
        // the case lands and pops open
        Quaternionf cq = new Quaternionf().rotateY((float) Math.toRadians(-yaw + 180));
        ItemDisplay base = part(root, "boss_fx_nova_case"), lid = part(root, "boss_fx_nova_lid");
        base.setTransformation(new Transformation(new Vector3f(), cq, new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
        lid.setTransformation(lidShape(cq, 0));
        w.playSound(root, Sound.BLOCK_ANVIL_LAND, 0.8f, 1.4f);
        Mech m = spawnMech(id, root, yaw);
        m.busy = true;
        m.scripted = true;
        m.kneel = 1;
        // every part starts as a speck inside the case, spun round, and unfolds into place
        List<ItemDisplay> order = List.of(m.shinL, m.shinR, m.thighL, m.thighR, m.torso, m.armL, m.cannon);
        for (ItemDisplay d : order) d.setTransformation(new Transformation(new Vector3f(0, 0.3f, 0),
                new Quaternionf().rotateY((float) Math.PI).rotateX(1.2f), new Vector3f(0.02f, 0.02f, 0.02f), new Quaternionf()));
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                t++;
                if (t == 6) {
                    lid.setInterpolationDelay(0); lid.setInterpolationDuration(8);
                    lid.setTransformation(lidShape(cq, 115));
                    w.playSound(root, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.2f, 0.6f);
                    w.playSound(root, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.8f);
                }
                if (t > 8 && t < 50 && t % 2 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, root.clone().add(0, 0.6, 0), 6, 0.5, 0.3, 0.5, 0.2);
                for (int i = 0; i < order.size(); i++) {
                    if (t != 14 + i * 5) continue;
                    // pose the full kneeling rig, then only let this one part interpolate there
                    Transformation keep[] = new Transformation[order.size()];
                    for (int j = 0; j < order.size(); j++) keep[j] = order.get(j).getTransformation();
                    pose(m, 10);
                    for (int j = 0; j < order.size(); j++) if (j != i) {
                        order.get(j).setInterpolationDuration(0);
                        order.get(j).setTransformation(keep[j]);
                    }
                    order.get(i).setInterpolationDelay(0);
                    order.get(i).setInterpolationDuration(10);
                    w.playSound(root, Sound.BLOCK_PISTON_EXTEND, 1.4f, 0.5f + i * 0.08f);
                    w.playSound(root, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.4f, 0.6f);
                    w.spawnParticle(Particle.CLOUD, root.clone().add(0, 1, 0), 10, 0.6, 0.6, 0.6, 0.05);
                }
                if (t == 52) {   // case folds into its back
                    lid.setInterpolationDelay(0); lid.setInterpolationDuration(6);
                    lid.setTransformation(new Transformation(new Vector3f(0, 2.5f, 0), cq, new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
                    base.setInterpolationDelay(0); base.setInterpolationDuration(6);
                    base.setTransformation(new Transformation(new Vector3f(0, 2.5f, 0), cq, new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
                    w.playSound(root, Sound.BLOCK_PISTON_CONTRACT, 1.4f, 0.6f);
                }
                if (t == 60) {
                    base.remove(); lid.remove();
                    w.playSound(root, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.7f);
                    w.playSound(root, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.6f);
                    w.spawnParticle(Particle.END_ROD, root.clone().add(0, 4, 0), 60, 1.2, 2, 1.2, 0.08);
                    m.busy = false;
                    m.scripted = false;
                    save();
                    if (p.isOnline()) p.showTitle(Title.title(plugin.mm("<gradient:#7b2cbf:#00e5ff><bold>NOVA MECH"), plugin.mm("<gray>right click it to climb in"),
                            Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2000), Duration.ofMillis(400))));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Transformation lidShape(Quaternionf cq, float open) {
        float sc = 1.6f;
        Vector3f hinge = new Quaternionf(cq).transform(new Vector3f(0, 3.0f / 16 * sc, -4.8f / 16 * sc));
        return new Transformation(hinge, new Quaternionf(cq).rotateX((float) Math.toRadians(-open)), new Vector3f(sc, sc, sc), new Quaternionf());
    }

    /** Folds a parked mech back into the case and hands it back. */
    private void fold(Player p, Mech m) {
        m.busy = true;
        m.scripted = true;
        World w = m.root.getWorld();
        Location root = m.root.clone();
        List<ItemDisplay> order = List.of(m.cannon, m.armL, m.torso, m.thighR, m.thighL, m.shinR, m.shinL);
        w.playSound(root, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.7f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                t++;
                for (int i = 0; i < order.size(); i++) if (t == 4 + i * 4) {
                    ItemDisplay d = order.get(i);
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(8);
                    d.setTransformation(new Transformation(new Vector3f(0, 0.3f, 0), new Quaternionf().rotateY((float) Math.PI).rotateX(1.2f),
                            new Vector3f(0.02f, 0.02f, 0.02f), new Quaternionf()));
                    w.playSound(root, Sound.BLOCK_PISTON_CONTRACT, 1.2f, 0.6f + i * 0.08f);
                }
                if (t == 40) {
                    despawn(m);
                    save();
                    w.playSound(root, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.2f, 0.8f);
                    w.spawnParticle(Particle.CLOUD, root.clone().add(0, 0.4, 0), 12, 0.4, 0.2, 0.4, 0.04);
                    if (p.isOnline()) for (ItemStack left : p.getInventory().addItem(create()).values()) w.dropItemNaturally(p.getLocation(), left);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ getting in and out

    @EventHandler(priority = EventPriority.HIGH)
    public void onClickMech(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction i) || !i.getScoreboardTags().contains(TAG)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        String id = i.getPersistentDataContainer().get(mechKey, PersistentDataType.STRING);
        Mech m = id == null ? null : mechs.get(id);
        Player p = event.getPlayer();
        if (m == null || m.busy || m.pilot != null || piloting.containsKey(p.getUniqueId())) return;
        if (!Owner.allowed(plugin, p)) { p.sendActionBar(plugin.mm("<red>Only v2tou can pilot this.")); return; }
        if (p.isSneaking()) { fold(p, m); return; }
        board(p, m);
    }

    @EventHandler
    public void onHitMech(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        Mech m = piloting.get(event.getPlayer().getUniqueId());
        if (m != null) {   // punching from the cockpit fires the cannon instead
            event.setCancelled(true);
            if (!m.busy) cannon(event.getPlayer(), m);
            return;
        }
        if (event.getAttacked().getScoreboardTags().contains(TAG)) event.setCancelled(true);
    }

    private void board(Player p, Mech m) {
        m.busy = true;
        m.pilot = p.getUniqueId();
        piloting.put(p.getUniqueId(), m);
        p.hideEntity(plugin, m.hitbox);   // he sits inside it - it would swallow all his clicks
        World w = m.root.getWorld();
        w.playSound(m.root, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.2f, 0.6f);
        Location s = seatAt(m);
        s.setYaw(m.yaw); s.setPitch(0);
        p.teleport(s);
        m.seat.addPassenger(p);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                t++;
                m.kneel = Math.max(0, 1 - t / 16.0);
                if (t == 1) w.playSound(m.root, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.2f, 0.6f);
                if (t % 4 == 0) w.playSound(m.root, Sound.BLOCK_PISTON_EXTEND, 0.8f, 0.5f + t * 0.02f);
                if (t >= 16) {
                    m.busy = false;
                    w.playSound(m.root, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.9f);
                    if (p.isOnline()) p.showTitle(Title.title(plugin.mm("<#00e5ff><bold>MECH ONLINE"),
                            plugin.mm("<gray>WASD walk  SPACE jump  CLICK cannon  F missiles  SNEAK exit  <dark_gray>(F5 for 3rd person)"),
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(3000), Duration.ofMillis(500))));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        Mech m = piloting.get(p.getUniqueId());
        if (m == null || leaving.contains(p.getUniqueId())) return;
        if (!p.isOnline() || p.isDead()) { release(p, m, false); return; }
        event.setCancelled(true);   // it kneels first, then lets him down
        if (m.busy) return;
        m.busy = true;
        m.fwd = m.back = m.left = m.right = m.jump = m.sprint = false;
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                t++;
                m.kneel = Math.min(1, t / 14.0);
                if (t % 4 == 0) m.root.getWorld().playSound(m.root, Sound.BLOCK_PISTON_CONTRACT, 0.8f, 0.9f - t * 0.02f);
                if (t >= 16) { release(p, m, true); cancel(); }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void release(Player p, Mech m, boolean putDown) {
        leaving.add(p.getUniqueId());
        try {
            if (m.seat.isValid()) m.seat.removePassenger(p);
        } finally {
            leaving.remove(p.getUniqueId());
        }
        piloting.remove(p.getUniqueId());
        if (p.isOnline() && m.hitbox.isValid()) p.showEntity(plugin, m.hitbox);
        m.pilot = null;
        m.busy = false;
        m.move = 0;
        m.kneel = 1;
        if (putDown && p.isOnline()) {
            Vector front = new Vector(-Math.sin(Math.toRadians(m.yaw)), 0, Math.cos(Math.toRadians(m.yaw))).multiply(3.2);
            Location out = m.root.clone().add(front);
            out = out.getWorld().getHighestBlockAt(out).getLocation().add(0.5, 1, 0.5);
            if (Math.abs(out.getY() - m.root.getY()) > 6) out = m.root.clone().add(0, 0.1, 0);
            out.setYaw(m.yaw + 180); out.setPitch(10);
            p.teleport(out);
            p.setFallDistance(0);
            m.root.getWorld().playSound(m.root, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.2f, 0.7f);
        }
        save();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Mech m = piloting.get(event.getPlayer().getUniqueId());
        if (m != null) release(event.getPlayer(), m, true);
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Mech m = piloting.get(event.getPlayer().getUniqueId());
        if (m != null) release(event.getPlayer(), m, false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPilotDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !piloting.containsKey(p.getUniqueId())) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID && event.getCause() != EntityDamageEvent.DamageCause.KILL) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ controls

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Mech m = piloting.get(event.getPlayer().getUniqueId());
        if (m == null || m.busy) return;
        Input in = event.getInput();
        m.fwd = in.isForward(); m.back = in.isBackward(); m.left = in.isLeft(); m.right = in.isRight();
        m.sprint = in.isSprint(); m.jump = in.isJump();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPilotClick(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        Mech m = piloting.get(p.getUniqueId());
        if (m == null || event.getHand() == EquipmentSlot.OFF_HAND) return;
        event.setCancelled(true);
        if (m.busy) return;
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) cannon(p, m);
        else if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) salvo(p, m);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPilotSwap(PlayerSwapHandItemsEvent event) {
        Mech m = piloting.get(event.getPlayer().getUniqueId());
        if (m == null) return;
        event.setCancelled(true);
        if (!m.busy) salvo(event.getPlayer(), m);
    }

    private Location aimPoint(Player p, Mech m, double range) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = p.getWorld().rayTrace(eye.clone().add(dir.clone().multiply(2.5)), dir, range, org.bukkit.FluidCollisionMode.NEVER, true, 0.4,
                e -> e instanceof LivingEntity && !e.equals(p) && !e.getScoreboardTags().contains(TAG));
        return hit != null ? hit.getHitPosition().toLocation(p.getWorld()) : eye.clone().add(dir.multiply(range));
    }

    private void cannon(Player p, Mech m) {
        if (m.t - m.lastShot < plugin.getConfig().getInt("mech.cannon-cooldown-ticks", 14)) return;
        m.lastShot = m.t;
        World w = p.getWorld();
        Location target = aimPoint(p, m, 120);
        Location shoulder = shoulderR(m);
        Vector dir = target.toVector().subtract(shoulder.toVector()).normalize();
        // point the cannon arm along the shot: arm hangs down, so tilt it forward by (90 - pitch)
        double pitch = -Math.asin(Math.max(-1, Math.min(1, dir.getY())));
        m.aim = (float) (pitch - Math.PI / 2);
        m.aimTicks = 30;
        Vector armDir = new Vector(-Math.sin(Math.toRadians(m.yaw)) * Math.cos(pitch), -Math.sin(pitch), Math.cos(Math.toRadians(m.yaw)) * Math.cos(pitch));
        Location muzzle = shoulder.clone().add(armDir.multiply(5.4));
        Fx.ray(muzzle, target, "beam_cyan", 0.7f, 6);
        Fx.safe(w, Particle.FLASH, muzzle, 1, 0, 0, 0, 0, Color.fromRGB(0x00e5ff));
        w.spawnParticle(Particle.END_ROD, muzzle, 14, 0.2, 0.2, 0.2, 0.15);
        w.playSound(muzzle, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 1.3f);
        w.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.6f);
        double dmg = plugin.getConfig().getDouble("mech.cannon-damage", 14);
        for (Entity e : w.getNearbyEntities(target, 1.5, 1.5, 1.5)) if (e instanceof LivingEntity le && !e.equals(p)) {
            le.setNoDamageTicks(0);
            le.damage(dmg, p);
        }
        w.createExplosion(target, (float) plugin.getConfig().getDouble("mech.cannon-power", 3.5), false,
                plugin.getConfig().getBoolean("mech.break-blocks", true), p);
    }

    private void salvo(Player p, Mech m) {
        if (m.t - m.lastSalvo < plugin.getConfig().getInt("mech.missile-cooldown-ticks", 60)) return;
        m.lastSalvo = m.t;
        Location target = aimPoint(p, m, 150);
        int n = plugin.getConfig().getInt("mech.missiles", 6);
        for (int i = 0; i < n; i++) {
            int k = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> missile(p, m, target, k), i * 3L);
        }
    }

    private void missile(Player p, Mech m, Location target, int k) {
        World w = m.root.getWorld();
        Location start = podL(m);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector vel = new Vector(r.nextDouble(-0.3, 0.3), 0.9, r.nextDouble(-0.3, 0.3))
                .add(new Vector(-Math.sin(Math.toRadians(m.yaw)), 0, Math.cos(Math.toRadians(m.yaw))).multiply(0.4));
        Location aim = target.clone().add(r.nextDouble(-1.5, 1.5), 0, r.nextDouble(-1.5, 1.5));
        Location s0 = start.clone(); s0.setYaw(0); s0.setPitch(0);
        ItemDisplay rocket = w.spawn(s0, ItemDisplay.class, d -> {
            d.setItemStack(model("rpg_rocket"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setTeleportDuration(1);
        });
        w.playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.6f, 0.7f + k * 0.05f);
        double power = plugin.getConfig().getDouble("mech.missile-power", 2.5);
        boolean breakBlocks = plugin.getConfig().getBoolean("mech.break-blocks", true);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                t++;
                Location cur = rocket.getLocation();
                Vector want = aim.toVector().subtract(cur.toVector());
                if (t > 6) vel.add(want.clone().normalize().multiply(0.35)).normalize().multiply(Math.min(2.2, 0.9 + t * 0.06));
                Location next = cur.clone().add(vel);
                Vector d = vel.clone().normalize();
                rocket.setInterpolationDelay(0);
                rocket.setInterpolationDuration(1);
                rocket.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationTo(new Vector3f(0, 0, 1),
                        new Vector3f((float) d.getX(), (float) d.getY(), (float) d.getZ())), new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
                w.spawnParticle(Particle.FLAME, cur, 2, 0.05, 0.05, 0.05, 0.01);
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, cur, 1, 0.05, 0.05, 0.05, 0.01);
                boolean hit = next.getBlock().getType().isSolid() || t > 100 || want.lengthSquared() < 2.5;
                if (!hit && t > 4) for (Entity e : w.getNearbyEntities(next, 1, 1, 1)) {
                    if (e instanceof LivingEntity && !e.equals(p) && !e.getScoreboardTags().contains(TAG)) { hit = true; break; }
                }
                if (hit) {
                    rocket.remove();
                    w.createExplosion(next, (float) power, false, breakBlocks, p);
                    cancel();
                    return;
                }
                rocket.teleport(next);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ walking

    private void tick() {
        for (Mech m : mechs.values()) {
            m.t++;
            if (!m.torso.isValid() || m.scripted) continue;
            Player p = m.pilot == null ? null : Bukkit.getPlayer(m.pilot);
            if (m.pilot != null && (p == null || !p.isOnline())) { m.pilot = null; piloting.values().remove(m); }
            if (p != null && !m.busy && !m.seat.getPassengers().contains(p)) {
                // something pulled him out of the seat (teleport etc.)
                release(p, m, false);
                p = null;
            }
            if (m.aimTicks > 0) m.aimTicks--;
            if (p != null && !m.busy) drive(p, m);
            else if (m.pilot == null) m.move *= 0.8;
            pose(m, 2);
            place(m);
        }
    }

    private void drive(Player p, Mech m) {
        World w = m.root.getWorld();
        // turn towards where he looks
        float want = p.getLocation().getYaw();
        float d = wrap(want - m.yaw);
        m.yaw += Math.max(-5f, Math.min(5f, d));
        double f = (m.fwd ? 1 : 0) - (m.back ? 1 : 0), s = (m.left ? 1 : 0) - (m.right ? 1 : 0);
        double speed = plugin.getConfig().getDouble(m.sprint ? "mech.run-speed" : "mech.walk-speed", m.sprint ? 0.45 : 0.24);
        double yr = Math.toRadians(m.yaw);
        Vector fw = new Vector(-Math.sin(yr), 0, Math.cos(yr)), left = new Vector(Math.cos(yr), 0, Math.sin(yr));
        Vector step = fw.multiply(f).add(left.multiply(s * 0.6));
        if (step.lengthSquared() > 1) step.normalize();
        step.multiply(speed);
        boolean onGround = m.vy == 0 && groundBelow(w, m.root, 0.05);
        boolean moved = false;
        if (step.lengthSquared() > 1.0E-4) {
            Location next = m.root.clone().add(step);
            double g = ground(w, next, m.root.getY());
            boolean blocked = g - m.root.getY() > 2.6;
            for (int y = 3; y <= 9 && !blocked; y += 2) if (!next.clone().add(step.clone().normalize().multiply(1.4)).add(0, y, 0).getBlock().isPassable()) blocked = true;
            if (!blocked) {
                moved = true;
                m.root = next;
                if (onGround && g > m.root.getY()) m.root.setY(Math.min(g, m.root.getY() + 0.6));
            }
        }
        m.move += ((moved ? Math.min(1, step.length() / 0.24) : 0) - m.move) * 0.25;
        double oldSin = Math.sin(m.phase);
        m.phase += 0.16 * m.move * (m.sprint ? 1.6 : 1.0);
        double newSin = Math.sin(m.phase);
        if (m.move > 0.3 && Math.signum(oldSin) != Math.signum(newSin) && m.vy == 0) footstep(m, newSin > 0);
        // gravity, thruster jump and hover
        double g = ground(w, m.root, m.root.getY());
        boolean grounded = m.root.getY() - g < 0.05;
        if (m.jump && grounded && m.vy == 0) { m.vy = plugin.getConfig().getDouble("mech.jump-power", 1.0); m.thrust = 12; w.playSound(m.root, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2f, 0.5f); }
        if (!grounded || m.vy > 0) {
            m.vy -= 0.07;
            if (m.jump && m.vy < -0.12) { m.vy = -0.12; m.thrust = Math.max(m.thrust, 2); }
            double ny = m.root.getY() + m.vy;
            // don't fly up into a ceiling
            if (m.vy > 0 && !m.root.clone().add(0, 10 + m.vy, 0).getBlock().isPassable()) { m.vy = 0; ny = m.root.getY(); }
            if (ny <= g) {
                ny = g;
                if (m.vy < -0.55) stomp(p, m);
                m.vy = 0;
            }
            m.root.setY(ny);
        }
        if (m.thrust > 0) {
            m.thrust--;
            Quaternionf q = yawQ(m);
            for (int side = -1; side <= 1; side += 2) {
                Vector3f o = q.transform(new Vector3f(0.9f * side, 0, -1.95f));
                Location fl = m.root.clone().add(o.x, HIP - 2.0 * m.kneel + 1.3, o.z);
                w.spawnParticle(Particle.FLAME, fl, 6, 0.1, 0.1, 0.1, 0.03);
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, fl.clone().subtract(0, 0.5, 0), 2, 0.1, 0.1, 0.1, 0.02);
            }
            if (m.thrust % 4 == 0) w.playSound(m.root, Sound.ITEM_ELYTRA_FLYING, 0.8f, 1.5f);
        }
        // shove small things out of the way
        for (Entity e : w.getNearbyEntities(m.root.clone().add(0, 2, 0), 1.9, 2.5, 1.9)) {
            if (e.equals(p) || !(e instanceof LivingEntity) || e.getScoreboardTags().contains(TAG)) continue;
            Vector away = e.getLocation().toVector().subtract(m.root.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = fw.clone();
            e.setVelocity(away.normalize().multiply(0.7).setY(0.35));
        }
        if (m.t % 10 == 0) p.sendActionBar(plugin.mm("<#00e5ff>NOVA MECH  <gray>| <white>CLICK <gray>cannon <white>F <gray>missiles"
                + (m.t - m.lastSalvo < plugin.getConfig().getInt("mech.missile-cooldown-ticks", 60) ? " <dark_gray>(reloading)" : "")
                + " <white>SPACE <gray>jump/hover <white>SNEAK <gray>exit"));
    }

    private void footstep(Mech m, boolean leftFoot) {
        World w = m.root.getWorld();
        Quaternionf q = yawQ(m);
        Vector3f o = q.transform(new Vector3f(leftFoot ? 1.25f : -1.25f, 0, 0.4f));
        Location foot = m.root.clone().add(o.x, 0.1, o.z);
        w.playSound(foot, Sound.ENTITY_IRON_GOLEM_STEP, 2.5f, 0.45f);
        w.playSound(foot, Sound.ENTITY_RAVAGER_STEP, 1.8f, 0.5f);
        Block under = foot.clone().subtract(0, 0.5, 0).getBlock();
        if (!under.getType().isAir()) w.spawnParticle(Particle.BLOCK, foot, 16, 0.5, 0.05, 0.5, 0.1, under.getBlockData());
    }

    private void stomp(Player p, Mech m) {
        World w = m.root.getWorld();
        w.playSound(m.root, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
        w.playSound(m.root, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2f, 0.4f);
        w.spawnParticle(Particle.EXPLOSION, m.root, 6, 2, 0.2, 2, 0);
        Fx.wave(m.root.clone().add(0, 0.3, 0), "ring_white", 1f, 14f, 10);
        for (Entity e : w.getNearbyEntities(m.root, 5, 3, 5)) {
            if (e.equals(p) || !(e instanceof LivingEntity le) || e.getScoreboardTags().contains(TAG)) continue;
            le.damage(plugin.getConfig().getDouble("mech.stomp-damage", 8), p);
            Vector away = e.getLocation().toVector().subtract(m.root.toVector()).setY(0);
            if (away.lengthSquared() > 0.01) le.setVelocity(away.normalize().multiply(1.2).setY(0.8));
        }
    }

    private static boolean groundBelow(World w, Location root, double eps) {
        return !root.clone().subtract(0, eps, 0).getBlock().isPassable();
    }

    /** Top of the highest solid block under (x, z), searching from a bit above {@code fromY} downwards. */
    private static double ground(World w, Location at, double fromY) {
        int x = at.getBlockX(), z = at.getBlockZ();
        int top = (int) Math.floor(fromY + 2.6);
        for (int y = top; y > Math.max(w.getMinHeight(), (int) fromY - 40); y--) {
            Block b = w.getBlockAt(x, y, z);
            if (!b.isPassable() && b.getType().isSolid()) return y + 1;
        }
        return w.getMinHeight();
    }

    private static float wrap(float a) {
        a %= 360;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    public void shutdown() {
        for (Map.Entry<UUID, Mech> e : new ArrayList<>(piloting.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) release(p, e.getValue(), true);
        }
        save();
        for (Mech m : new ArrayList<>(mechs.values())) despawn(m);
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
    }
}
