package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
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
 * Owner-only joke ability: a fat poop meteor crashes onto the player you aim at, explodes brown everywhere,
 * covers their screen in brown (a helmet with a camera overlay) and leaves them stuck in a poop pile.
 */
public class PoopAbility extends CosmicAbility implements Listener {
    private static final int BROWN = 0x6b4220;
    private final NamespacedKey hatKey, slowKey, jumpKey;
    private final Map<UUID, ItemStack> savedHelmet = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stuckUntil = new ConcurrentHashMap<>();

    public PoopAbility(CosmicAbilitiesPlugin plugin) {
        super(plugin, AbilityType.POOP);
        hatKey = new NamespacedKey(plugin, "poop_screen");
        slowKey = new NamespacedKey(plugin, "poop_stuck");
        jumpKey = new NamespacedKey(plugin, "poop_nojump");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        double range = plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 40);
        // always the closest other player
        Player target = null;
        double best = range * range;
        for (Player o : w.getPlayers()) {
            if (o.equals(player) || o.isDead() || o.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (plugin.afk().isAfk(o.getUniqueId())) continue;
            double d = o.getLocation().distanceSquared(player.getLocation());
            if (d < best) { best = d; target = o; }
        }
        if (target == null) {
            plugin.engine().notify(player, plugin.mm("<#7a4a1e>Nobody close enough to poop on."));
            return false;
        }
        Location aim = target.getLocation();
        boolean chunky = bonus(slot);
        float size = chunky ? 3.6f : 2.8f;
        Location sky = aim.clone().add(0, 26, 0);
        sky.setYaw(0); sky.setPitch(0);
        ItemDisplay poop = Fx.model(sky, "boss_fx_poop", size);
        poop.setTeleportDuration(1);
        w.playSound(aim, Sound.ENTITY_PLAYER_BURP, 2f, 0.5f);
        w.playSound(aim, Sound.ITEM_TRIDENT_THUNDER, 1f, 1.8f);
        int fall = 16;
        final Player victim = target;
        every(fall + 1, t -> {
            Location goal = victim.isValid() ? victim.getLocation() : aim;
            double k = Math.min(1, t / (double) fall);
            Location at = sky.clone();
            at.setX(goal.getX()); at.setZ(goal.getZ());
            at.setY(goal.getY() + 26 * (1 - k * k));
            poop.teleport(at);
            poop.setInterpolationDelay(0);
            poop.setInterpolationDuration(1);
            poop.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(t * 0.4f), new Vector3f(size, size, size), new Quaternionf()));
            w.spawnParticle(Particle.DUST, at.clone().add(0, 0.5, 0), 10, 0.6, 0.6, 0.6, 0, new Particle.DustOptions(Color.fromRGB(BROWN), 2.2f));
            w.spawnParticle(Particle.FALLING_DRIPSTONE_LAVA, at, 2, 0.5, 0.2, 0.5, 0);
            if (t % 4 == 0) w.playSound(at, Sound.BLOCK_HONEY_BLOCK_SLIDE, 1.5f, 0.5f);
            if (t >= fall) {
                poop.remove();
                splat(player, goal, victim, chunky);
                return false;
            }
            return true;
        });
        return true;
    }

    private void splat(Player caster, Location at, LivingEntity target, boolean chunky) {
        World w = at.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double radius = chunky ? 5 : 3.5;
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
        w.playSound(at, Sound.ENTITY_SLIME_SQUISH, 3f, 0.4f);
        w.playSound(at, Sound.BLOCK_MUD_BREAK, 3f, 0.5f);
        w.playSound(at, Sound.ENTITY_SNIFFER_DIGGING, 2f, 0.6f);
        crater(at, chunky ? 4.5 : 3.3);
        w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 250, radius / 2, 1.2, radius / 2, 0, new Particle.DustOptions(Color.fromRGB(BROWN), 2.8f));
        w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 120, radius / 2, 1.5, radius / 2, 0, new Particle.DustOptions(Color.fromRGB(0x3d2410), 2f));
        w.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 150, radius / 2, 0.8, radius / 2, 0.4, Material.MUD.createBlockData());
        w.spawnParticle(Particle.ITEM, at.clone().add(0, 1, 0), 60, 0.5, 0.5, 0.5, 0.35, new ItemStack(Material.COCOA_BEANS));
        for (int i = 0; i < (chunky ? 18 : 12); i++) {
            Location d = at.clone().add(r.nextDouble(-radius, radius), 0.02, r.nextDouble(-radius, radius));
            d.setY(w.getHighestBlockYAt(d) + 1.02);
            Fx.decal(d, r.nextBoolean() ? Material.BROWN_CONCRETE : Material.MUD, (float) r.nextDouble(0.6, 1.6), (float) r.nextDouble(6), 200);
        }
        int seconds = (int) plugin.getConfig().getDouble("ability-settings." + type.id() + ".stuck-seconds", 5) + (chunky ? 3 : 0);
        List<LivingEntity> hit = new ArrayList<>();
        if (target != null && target.isValid()) hit.add(target);
        for (Entity e : w.getNearbyEntities(at, radius, 2.5, radius)) {
            if (e instanceof LivingEntity le && !e.equals(caster) && !hit.contains(le) && Targets.isEnemy(caster, le)) hit.add(le);
        }
        for (LivingEntity e : hit) {
            hearts(caster, e, e.equals(target) ? 4 : 2);
            stick(e, seconds);
            if (e instanceof Player p) brownScreen(p, seconds);
        }
    }

    /** A bowl-shaped crater lined with brown poo. It goes back to normal after ability-settings.poop.crater-seconds (0 = stays). */
    private void crater(Location at, double radius) {
        World w = at.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        org.bukkit.block.Block center = at.getBlock();
        while (center.isPassable() && center.getY() > w.getMinHeight()) center = center.getRelative(0, -1, 0);
        List<org.bukkit.block.BlockState> original = new ArrayList<>();
        Material[] poo = {Material.MUD, Material.BROWN_CONCRETE, Material.BROWN_TERRACOTTA, Material.PACKED_MUD, Material.BROWN_CONCRETE_POWDER, Material.COARSE_DIRT};
        int R = (int) Math.ceil(radius) + 1;
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) for (int y = -R; y <= 2; y++) {
            org.bukkit.block.Block b = center.getRelative(x, y, z);
            Material m = b.getType();
            if (m == Material.BEDROCK || m == Material.BARRIER || m.getHardness() < 0 || b.getState() instanceof org.bukkit.block.Container
                    || m == Material.END_PORTAL_FRAME || m == Material.END_PORTAL || m == Material.NETHER_PORTAL) continue;
            double depth = radius * 0.55 * (1 - (x * x + z * z) / (radius * radius));   // bowl
            double dist = Math.sqrt(x * x + z * z);
            boolean inBowl = dist <= radius && y > -depth && y <= 1;
            boolean lining = dist <= radius + 0.8 && !inBowl && y <= 0 && y > -depth - 1.6 && !m.isAir() && m.isSolid();
            if (inBowl && !m.isAir()) {
                original.add(b.getState());
                b.setType(Material.AIR, false);
            } else if (lining) {
                original.add(b.getState());
                b.setType(poo[r.nextInt(poo.length)], false);
            }
        }
        double keep = plugin.getConfig().getDouble("ability-settings." + type.id() + ".crater-seconds", 60);
        if (keep > 0) later((long) (keep * 20), () -> {
            for (int i = original.size() - 1; i >= 0; i--) original.get(i).update(true, false);
        });
    }

    /** Stuck in a poop pile: can't walk or jump, with a brown mound around their legs. */
    private void stick(LivingEntity e, int seconds) {
        UUID id = e.getUniqueId();
        long until = System.currentTimeMillis() + seconds * 1000L;
        stuckUntil.put(id, until);
        modifier(e, Attribute.MOVEMENT_SPEED, slowKey, true);
        modifier(e, Attribute.JUMP_STRENGTH, jumpKey, true);
        Location base = e.getLocation();
        List<BlockDisplay> pile = new ArrayList<>();
        float wdt = (float) Math.max(1.2, e.getWidth() * 1.8);
        float[][] tiers = {{wdt, 0.45f}, {wdt * 0.78f, 0.4f}, {wdt * 0.5f, 0.35f}, {wdt * 0.22f, 0.3f}};
        float y = 0;
        for (int i = 0; i < tiers.length; i++) {
            float s = tiers[i][0], h = tiers[i][1];
            BlockDisplay b = Fx.block(base, i % 2 == 0 ? Material.MUD : Material.BROWN_CONCRETE, new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf(), false);
            final float yy = y;
            Fx.animate(b, 5, new Transformation(new Vector3f(-s / 2, yy, -s / 2), new Quaternionf().rotateY(i * 0.4f), new Vector3f(s, h, s), new Quaternionf()), 1);
            pile.add(b);
            y += h * 0.85f;
        }
        every(seconds * 20 + 2, t -> {
            if (!e.isValid() || e.isDead()) { pile.forEach(Entity::remove); unstick(e); return false; }
            for (BlockDisplay b : pile) b.teleport(e.getLocation());
            if (t % 10 == 0) e.getWorld().spawnParticle(Particle.DUST, e.getLocation().add(0, 0.6, 0), 6, 0.4, 0.3, 0.4, 0, new Particle.DustOptions(Color.fromRGB(BROWN), 1.5f));
            if (t % 15 == 0) e.getWorld().spawnParticle(Particle.ASH, e.getLocation().add(0, 1.6, 0), 8, 0.6, 0.4, 0.6, 0);   // flies
            if (t % 30 == 0) e.getWorld().playSound(e.getLocation(), Sound.BLOCK_MUD_STEP, 1f, 0.6f);
            if (System.currentTimeMillis() >= stuckUntil.getOrDefault(e.getUniqueId(), 0L)) {
                pile.forEach(b -> { Fx.animate(b, 6, new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()), 0); Fx.removeLater(b, 7); });
                unstick(e);
                return false;
            }
            return true;
        });
    }

    private void unstick(LivingEntity e) {
        stuckUntil.remove(e.getUniqueId());
        modifier(e, Attribute.MOVEMENT_SPEED, slowKey, false);
        modifier(e, Attribute.JUMP_STRENGTH, jumpKey, false);
    }

    private static void modifier(LivingEntity e, Attribute attr, NamespacedKey key, boolean on) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(key);
        if (old != null) inst.removeModifier(old);
        if (on) inst.addModifier(new AttributeModifier(key, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    /** Covers their screen in brown: a temporary helmet with a camera overlay (their real helmet comes back after). */
    private void brownScreen(Player p, int seconds) {
        ItemStack hat = new ItemStack(Material.BROWN_DYE);
        ItemMeta m = hat.getItemMeta();
        m.displayName(plugin.mm("<!italic><#7a4a1e>Poop"));
        EquippableComponent eq = m.getEquippable();
        eq.setSlot(EquipmentSlot.HEAD);
        eq.setCameraOverlay(new NamespacedKey("cosmic", "misc/poop_overlay"));
        eq.setDispensable(false);
        eq.setSwappable(false);
        m.setEquippable(eq);
        m.getPersistentDataContainer().set(hatKey, PersistentDataType.BYTE, (byte) 1);
        hat.setItemMeta(m);
        if (!savedHelmet.containsKey(p.getUniqueId())) {
            ItemStack old = p.getInventory().getHelmet();
            savedHelmet.put(p.getUniqueId(), old == null ? new ItemStack(Material.AIR) : old.clone());
        }
        p.getInventory().setHelmet(hat);
        p.playSound(p.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 0.5f);
        later(seconds * 20L, () -> restore(p));
    }

    private boolean isHat(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(hatKey, PersistentDataType.BYTE);
    }

    private void restore(Player p) {
        ItemStack old = savedHelmet.remove(p.getUniqueId());
        if (old == null) return;
        if (isHat(p.getInventory().getHelmet()) || p.getInventory().getHelmet() == null) {
            p.getInventory().setHelmet(old.getType().isAir() ? null : old);
        } else if (!old.getType().isAir()) {
            p.getInventory().addItem(old).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
        p.getInventory().forEach(it -> { if (isHat(it)) it.setAmount(0); });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (isHat(event.getCurrentItem()) || isHat(event.getCursor())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        event.getDrops().removeIf(this::isHat);
        ItemStack old = savedHelmet.remove(p.getUniqueId());
        if (old != null && !old.getType().isAir()) {
            if (event.getKeepInventory()) plugin.getServer().getScheduler().runTask(plugin, () -> p.getInventory().setHelmet(old));
            else event.getDrops().add(old);
        }
        unstick(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restore(event.getPlayer());
        unstick(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        unstick(p);
        if (isHat(p.getInventory().getHelmet())) p.getInventory().setHelmet(null);
    }
}
