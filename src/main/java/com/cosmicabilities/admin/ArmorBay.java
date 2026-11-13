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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2tou's Nova Armor case. The case IS the suit: right-click it and it drops in front of him, pops open, and the
 * armor flies out piece by piece and locks onto his body (everyone around sees it), then the case itself folds
 * into the thruster pack on his back. Sneak + right-click with an empty hand to send the suit back into the case.
 * Suited he can fly (double-tap jump), fire repulsor blasts from his hands (right-click with an empty hand),
 * take far less damage and shrug off falls.
 */
public class ArmorBay implements Listener {
    public static final String SUIT = "nova_armor";
    private static final String NAME = "<gradient:#7b2cbf:#00e5ff><bold>Nova Armor";
    private static final float CASE_SCALE = 1.4f;
    private static final Suits.Piece[] ORDER = {Suits.Piece.BOOTS, Suits.Piece.LEGS, Suits.Piece.CHEST, Suits.Piece.HELMET};
    private static final double[] HEIGHT = {0.15, 0.75, 1.3, 1.8};
    private static final String[] PIECE_NAMES = {"Boots", "Leg armor", "Chest plate", "Helmet"};

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey itemKey;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Set<UUID> suited = ConcurrentHashMap.newKeySet();
    private final Set<UUID> owedCase = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemDisplay> packs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBlast = new HashMap<>();

    public ArmorBay(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "armor_bay_item");
        // the old placeable stations are gone - the case is the suit now
        File old = new File(plugin.getDataFolder(), "armor-bays.yml");
        if (old.exists() && !old.delete()) plugin.getLogger().warning("Couldn't delete old armor-bays.yml");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 2L);
    }

    public ItemStack create() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(plugin.mm("<!italic><gradient:#7b2cbf:#00e5ff><bold>Nova Armor Case"));
        m.lore(List.of(plugin.mm("<!italic><gray>Right click: the case becomes your suit."),
                plugin.mm("<!italic><gray>Suited: double-tap jump to fly,"), plugin.mm("<!italic><gray>right click an empty hand: repulsor."),
                plugin.mm("<!italic><gray>Sneak + right click an empty hand:"), plugin.mm("<!italic><gray>the suit packs back into the case."),
                plugin.mm("<!italic><dark_red>v2tou only")));
        m.setItemModel(new NamespacedKey("cosmic", "armor_bay_item"));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isCase(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    /** Kept for the plugin's start-up hook; there are no placed stations anymore. */
    public void rebuildAll() {}

    public boolean suited(UUID id) { return suited.contains(id); }

    // ------------------------------------------------------------------ using the case

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isCase(hand)) {
            event.setCancelled(true);
            if (!Owner.allowed(plugin, p)) { p.getInventory().setItemInMainHand(null); return; }
            if (busy.contains(p.getUniqueId()) || suited.contains(p.getUniqueId()) || p.getGameMode() == GameMode.SPECTATOR) return;
            hand.setAmount(hand.getAmount() - 1);
            suitUp(p);
            return;
        }
        if (hand.getType().isAir() && p.isSneaking() && suited.contains(p.getUniqueId()) && !busy.contains(p.getUniqueId())) {
            event.setCancelled(true);
            suitDown(p);
        }
    }

    private static ItemStack model(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("cosmic", name));
        it.setItemMeta(m);
        return it;
    }

    private ItemDisplay display(Location at, ItemStack item, float scale, int tp) {
        Location l = at.clone(); l.setYaw(0); l.setPitch(0);
        return at.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setTeleportDuration(tp);
            d.setInterpolationDuration(tp);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }

    private static Transformation shape(Quaternionf rot, float scale, Vector3f translation) {
        return new Transformation(translation, rot, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private static void animate(Display d, int ticks, Transformation to) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(to);
    }

    /** The case lies on the ground in front of him, handle towards him. Lid hinge sits at the back top edge. */
    private record CaseParts(ItemDisplay base, ItemDisplay lid, Quaternionf yaw, Location ground) {}

    private CaseParts spawnCase(Location ground, float yawDeg, Location from) {
        Quaternionf yaw = new Quaternionf().rotateY((float) Math.toRadians(-yawDeg));
        ItemDisplay base = display(from, model("boss_fx_nova_case"), 0.05f, 6);
        ItemDisplay lid = display(from, model("boss_fx_nova_lid"), 0.05f, 6);
        base.setTransformation(shape(yaw, 0.05f, new Vector3f()));
        lid.setTransformation(shape(yaw, 0.05f, new Vector3f()));
        return new CaseParts(base, lid, yaw, ground);
    }

    /** Lid local transform: open = degrees the lid is swung up around its hinge. */
    private static Transformation lidShape(CaseParts c, float open, float scale) {
        Vector3f hinge = new Quaternionf(c.yaw()).transform(new Vector3f(0, 3.0f / 16 * scale, -4.8f / 16 * scale));
        Quaternionf r = new Quaternionf(c.yaw()).rotateX((float) Math.toRadians(-open));
        return new Transformation(hinge, r, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private void suitUp(Player p) {
        UUID id = p.getUniqueId();
        busy.add(id);
        World w = p.getWorld();
        Location stand = p.getLocation().clone();
        Vector look = flatLook(stand);
        Location ground = spot(stand, look);
        float caseYaw = stand.getYaw() + 180f;   // handle towards him, hinge on the far side
        CaseParts c = spawnCase(ground, caseYaw, p.getLocation().add(0, 1.0, 0).add(look.clone().multiply(0.5)));
        List<Entity> mine = new ArrayList<>(List.of(c.base(), c.lid()));
        w.playSound(stand, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.6f);
        p.showTitle(Title.title(plugin.mm(NAME), plugin.mm("<gray>deploying..."),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2600), Duration.ofMillis(400))));
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    // quitting gives the case back in onQuit; dying gives it back on respawn
                    mine.forEach(Entity::remove);
                    if (p.isOnline() && busy.contains(id)) owedCase.add(id);
                    busy.remove(id);
                    cancel();
                    return;
                }
                holdStill(p, stand);
                if (t == 1) {   // tossed out of his hand, lands on the ground and grows to full size
                    Location g = ground.clone(); g.setYaw(0); g.setPitch(0);
                    c.base().teleport(g);
                    c.lid().teleport(g);
                    animate(c.base(), 6, shape(c.yaw(), CASE_SCALE, new Vector3f()));
                    animate(c.lid(), 6, lidShape(c, 0, CASE_SCALE));
                }
                if (t == 7) {
                    w.playSound(ground, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.6f);
                    w.spawnParticle(Particle.BLOCK, ground, 14, 0.4, 0.05, 0.4, 0, ground.clone().subtract(0, 0.5, 0).getBlock().getBlockData());
                }
                if (t == 12) {   // clasps release, lid swings open, light spills out
                    w.playSound(ground, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 0.7f);
                    w.playSound(ground, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
                    animate(c.lid(), 8, lidShape(c, 115, CASE_SCALE));
                }
                if (t > 14 && t < 20) w.spawnParticle(Particle.END_ROD, ground.clone().add(0, 0.4, 0), 3, 0.3, 0.1, 0.3, 0.04);
                // the four pieces leap out of the case, spin and clamp on, feet first
                for (int i = 0; i < ORDER.length; i++) {
                    int t0 = 20 + i * 9;
                    if (t == t0) {
                        ItemDisplay piece = display(ground.clone().add(0, 0.35, 0), plugin.suits().piece(SUIT, "", ORDER[i]), 0.55f, 7);
                        mine.add(piece);
                        Location target = stand.clone().add(0, HEIGHT[i], 0);
                        piece.setTransformation(shape(new Quaternionf(), 0.55f, new Vector3f()));
                        int idx = i;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (!piece.isValid()) return;
                            Location tl = target.clone(); tl.setYaw(0); tl.setPitch(0);
                            piece.teleport(tl);
                            animate(piece, 7, shape(new Quaternionf().rotateY((float) Math.toRadians(-stand.getYaw()) + (float) Math.PI * 2),
                                    idx == 3 ? 0.62f : 0.75f, new Vector3f()));
                        }, 1L);
                        w.playSound(ground, Sound.BLOCK_PISTON_EXTEND, 0.9f, 1.3f + i * 0.1f);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, ground.clone().add(0, 0.4, 0), 10, 0.2, 0.1, 0.2, 0.15);
                    }
                    if (t == t0 + 8) {
                        for (Entity e : new ArrayList<>(mine)) if (e instanceof ItemDisplay d && d != c.base() && d != c.lid()) { e.remove(); mine.remove(e); }
                        plugin.suits().equip(p, SUIT, NAME + " <gray>" + PIECE_NAMES[i], ORDER[i]);
                        Location at = stand.clone().add(0, HEIGHT[i], 0);
                        w.playSound(at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 0.8f);
                        w.playSound(at, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 0.6f + i * 0.1f);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, at, 14, 0.3, 0.15, 0.3, 0.12);
                        w.spawnParticle(Particle.DUST, at, 10, 0.3, 0.1, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0x00e5ff), 1.1f));
                    }
                }
                if (t == 58) {   // the empty case folds up and flies onto his back as the thruster pack
                    w.playSound(ground, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 1.2f);
                    animate(c.lid(), 5, lidShape(c, 0, CASE_SCALE));
                }
                if (t == 63) {
                    Location back = stand.clone().add(0, 1.2, 0).subtract(look.clone().multiply(0.3)); back.setYaw(0); back.setPitch(0);
                    c.base().teleport(back);
                    c.lid().teleport(back);
                    animate(c.base(), 6, shape(c.yaw(), 0.5f, new Vector3f()));
                    animate(c.lid(), 6, lidShape(c, 0, 0.5f));
                    w.playSound(stand, Sound.BLOCK_PISTON_CONTRACT, 1f, 1.5f);
                }
                if (t == 70) {
                    mine.forEach(Entity::remove);
                    w.spawnParticle(Particle.END_ROD, stand.clone().add(0, 1, 0), 50, 0.3, 0.8, 0.3, 0.08);
                    w.spawnParticle(Particle.DUST, stand.clone().add(0, 1.1, 0), 40, 0.25, 0.5, 0.25, 0, new Particle.DustOptions(Color.fromRGB(0x00e5ff), 1.2f));
                    w.playSound(stand, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
                    w.playSound(stand, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.6f);
                    p.showTitle(Title.title(plugin.mm("<#00e5ff><bold>SYSTEMS ONLINE"), plugin.mm("<gray>double-tap jump: fly  |  empty hand: repulsor  |  sneak+click: pack up"),
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2400), Duration.ofMillis(500))));
                    busy.remove(id);
                    suited.add(id);
                    powersOn(p);
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void suitDown(Player p) {
        UUID id = p.getUniqueId();
        busy.add(id);
        World w = p.getWorld();
        Location stand = p.getLocation().clone();
        Vector look = flatLook(stand);
        Location groundF = spot(stand, look);
        removePack(id);
        // the pack on his back flies off and unfolds into the open case in front of him
        CaseParts c = spawnCase(groundF, stand.getYaw() + 180f, stand.clone().add(0, 1.2, 0).subtract(look.clone().multiply(0.3)));
        List<Entity> mine = new ArrayList<>(List.of(c.base(), c.lid()));
        w.playSound(stand, Sound.BLOCK_PISTON_EXTEND, 1f, 1.2f);
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!p.isOnline()) { mine.forEach(Entity::remove); busy.remove(id); cancel(); return; }
                holdStill(p, stand);
                if (t == 1) {
                    Location g = groundF.clone(); g.setYaw(0); g.setPitch(0);
                    c.base().teleport(g);
                    c.lid().teleport(g);
                    animate(c.base(), 6, shape(c.yaw(), CASE_SCALE, new Vector3f()));
                    animate(c.lid(), 6, lidShape(c, 115, CASE_SCALE));
                }
                // pieces come off top-down and drop back into the case
                for (int k = 0; k < ORDER.length; k++) {
                    int i = ORDER.length - 1 - k;
                    int t0 = 8 + k * 7;
                    if (t == t0) {
                        Location from = stand.clone().add(0, HEIGHT[i], 0);
                        ItemDisplay piece = display(from, plugin.suits().piece(SUIT, "", ORDER[i]), i == 3 ? 0.62f : 0.75f, 6);
                        piece.setTransformation(shape(new Quaternionf().rotateY((float) Math.toRadians(-stand.getYaw())), i == 3 ? 0.62f : 0.75f, new Vector3f()));
                        mine.add(piece);
                        plugin.suits().unequip(p, ORDER[i]);
                        w.playSound(from, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 0.7f + i * 0.1f);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, from, 10, 0.3, 0.15, 0.3, 0.1);
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (!piece.isValid()) return;
                            Location into = groundF.clone().add(0, 0.3, 0); into.setYaw(0); into.setPitch(0);
                            piece.teleport(into);
                            animate(piece, 6, shape(new Quaternionf().rotateY((float) Math.PI * 2), 0.2f, new Vector3f()));
                        }, 1L);
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (piece.isValid()) piece.remove(); }, 8L);
                    }
                }
                if (t == 40) {
                    animate(c.lid(), 6, lidShape(c, 0, CASE_SCALE));
                    w.playSound(groundF, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 0.8f);
                }
                if (t == 47) {   // the case hops back up into his hand
                    Location hand = stand.clone().add(0, 1.1, 0).add(look.clone().multiply(0.4)); hand.setYaw(0); hand.setPitch(0);
                    c.base().teleport(hand);
                    c.lid().teleport(hand);
                    animate(c.base(), 6, shape(c.yaw(), 0.05f, new Vector3f()));
                    animate(c.lid(), 6, lidShape(c, 0, 0.05f));
                    w.playSound(stand, Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
                }
                if (t == 54) {
                    mine.forEach(Entity::remove);
                    takeOff(p);
                    giveCase(p);
                    busy.remove(id);
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Vector flatLook(Location stand) {
        Vector look = stand.getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) return new Vector(0, 0, 1);
        return look.normalize();
    }

    /** Where the case lands: in front of him, or at his feet if that spot is blocked. */
    private static Location spot(Location stand, Vector look) {
        Location ground = stand.clone().add(look.clone().multiply(1.4));
        return ground.getBlock().isPassable() ? ground : stand.clone();
    }

    private void holdStill(Player p, Location stand) {
        if (p.getLocation().distanceSquared(stand) > 0.04) {
            Location keep = stand.clone(); keep.setYaw(p.getLocation().getYaw()); keep.setPitch(p.getLocation().getPitch());
            p.teleport(keep);
        }
        p.setVelocity(new Vector());
    }

    private void giveCase(Player p) {
        if (!p.isOnline()) { owedCase.add(p.getUniqueId()); return; }
        for (ItemStack left : p.getInventory().addItem(create()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    private void powersOn(Player p) {
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) p.setAllowFlight(true);
        p.setFlySpeed(0.12f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, true));
    }

    private void takeOff(Player p) {
        removePack(p.getUniqueId());
        if (!suited.remove(p.getUniqueId())) { plugin.suits().remove(p); return; }
        plugin.suits().remove(p);
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) { p.setFlying(false); p.setAllowFlight(false); }
        p.setFlySpeed(0.1f);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
    }

    // ------------------------------------------------------------------ thruster pack + flight

    private void removePack(UUID id) {
        ItemDisplay d = packs.remove(id);
        if (d != null && d.isValid()) d.remove();
    }

    private void tick() {
        for (UUID id : suited) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) { removePack(id); continue; }
            if ((p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) && !p.getAllowFlight()) p.setAllowFlight(true);
            ItemDisplay pack = packs.get(id);
            if (pack == null || !pack.isValid() || !p.getPassengers().contains(pack)) {
                if (pack != null && pack.isValid()) pack.remove();
                pack = display(p.getLocation().add(0, 1.8, 0), model("boss_fx_nova_pack"), 1f, 0);
                pack.setInterpolationDuration(2);
                p.addPassenger(pack);
                packs.put(id, pack);
            }
            // sits on his back and turns with his body
            float yaw = p.getBodyYaw();
            Quaternionf r = new Quaternionf().rotateY((float) Math.toRadians(-yaw));
            Vector3f back = r.transform(new Vector3f(0, 0, -0.31f));
            double sneak = p.isSneaking() ? -0.25 : 0;
            pack.setInterpolationDelay(0);
            pack.setInterpolationDuration(2);
            pack.setTransformation(new Transformation(new Vector3f(back.x, (float) (-0.62 + sneak), back.z), r, new Vector3f(1, 1, 1), new Quaternionf()));
            if (p.isFlying()) {
                Location nozzle = p.getLocation().add(back.x, 0.75 + sneak, back.z);
                Vector side = new Vector(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw))).multiply(0.13);
                for (int s = -1; s <= 1; s += 2) {
                    Location n = nozzle.clone().add(side.clone().multiply(s));
                    p.getWorld().spawnParticle(Particle.FLAME, n, 2, 0.02, 0.02, 0.02, 0.01);
                    p.getWorld().spawnParticle(Particle.DUST, n, 1, 0.02, 0.02, 0.02, 0, new Particle.DustOptions(Color.fromRGB(0x00e5ff), 0.8f));
                }
                Location feet = p.getLocation();
                p.getWorld().spawnParticle(Particle.FLAME, feet, 2, 0.12, 0.02, 0.12, 0.01);
            }
        }
    }

    @EventHandler
    public void onRepulsor(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!suited.contains(p.getUniqueId()) || event.getHand() != EquipmentSlot.HAND || p.isSneaking()) return;
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

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && suited.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        boolean had = suited.contains(p.getUniqueId()) || busy.contains(p.getUniqueId());
        takeOff(p);
        busy.remove(p.getUniqueId());
        if (had) for (ItemStack left : p.getInventory().addItem(create()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player p = event.getPlayer();
        removePack(p.getUniqueId());
        if (suited.remove(p.getUniqueId())) {
            p.setFlySpeed(0.1f);
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) p.setAllowFlight(false);
            owedCase.add(p.getUniqueId());   // the case comes back when he respawns
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (owedCase.remove(p.getUniqueId())) plugin.getServer().getScheduler().runTask(plugin, () -> giveCase(p));
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (owedCase.remove(p.getUniqueId())) plugin.getServer().getScheduler().runTask(plugin, () -> giveCase(p));
    }

    public void shutdown() {
        for (UUID id : suited.toArray(new UUID[0])) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            takeOff(p);
            for (ItemStack left : p.getInventory().addItem(create()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        for (ItemDisplay d : packs.values()) if (d.isValid()) d.remove();
        packs.clear();
    }
}
