package com.cosmicabilities.status;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived ability states: frozen, inverted controls, burrowed, reflect shields, forcefields, fall immunity. */
public class StatusManager {
    public record Shield(double reduction, double reflect, long expiresAt, boolean evolved) {}

    public static final class Frozen {
        final Location at;
        final long until;
        final List<org.bukkit.entity.Display> shell;
        final Player caster;
        final double shatterDamage;
        Frozen(Location at, long until, List<org.bukkit.entity.Display> shell, Player caster, double shatterDamage) {
            this.at = at; this.until = until; this.shell = shell; this.caster = caster; this.shatterDamage = shatterDamage;
        }
    }

    public static final class Inverted {
        final long until;
        final TextDisplay marker;
        Inverted(long until, TextDisplay marker) { this.until = until; this.marker = marker; }
    }

    public static final class Burrow {
        public final long until;
        public final Runnable onEnd;
        Location lastWall;
        Burrow(long until, Runnable onEnd) { this.until = until; this.onEnd = onEnd; }
    }

    /** A scrambled hotbar: perm[i] = original slot of the item now in slot i. */
    private record Scramble(int[] perm, int heldSlot, long until) {}

    public static final class Field {
        public final Location center;
        public final double radius;
        public final long until;
        public final UUID owner;
        public final boolean repel;
        /** Everyone standing inside when it went up: they can't get out until it drops. Everyone else can't get in. */
        public final Set<UUID> trapped = new java.util.HashSet<>();
        final List<org.bukkit.entity.ItemDisplay> tiles = new ArrayList<>();
        final Map<UUID, Long> lastFlash = new java.util.HashMap<>();
        Field(Location center, double radius, long until, UUID owner, boolean repel) {
            this.center = center; this.radius = radius; this.until = until; this.owner = owner; this.repel = repel;
        }
        public boolean contains(Location l) {
            return l.getWorld() != null && l.getWorld().equals(center.getWorld()) && l.distanceSquared(center) <= radius * radius;
        }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, Frozen> frozen = new ConcurrentHashMap<>();
    private final Map<UUID, Inverted> inverted = new ConcurrentHashMap<>();
    private final Map<UUID, Burrow> burrowed = new ConcurrentHashMap<>();
    private final Map<UUID, Shield> shields = new ConcurrentHashMap<>();
    private final Set<UUID> fallImmune = ConcurrentHashMap.newKeySet();
    private final List<Field> fields = new ArrayList<>();
    private final Map<UUID, Scramble> scrambled = new ConcurrentHashMap<>();
    /** Guards against two Solar shields reflecting damage back and forth forever. */
    private boolean reflecting = false;

    public StatusManager(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ freeze

    /**
     * Encases the target in a crystal prison: a clear ice core around the body, jagged blue-ice shards jutting out
     * at angles, and a frost patch spreading on the ground. Players are pinned by the move listener; mobs by tick().
     */
    public void freeze(LivingEntity target, int ticks, Player caster, double shatterDamage) {
        unfreeze(target.getUniqueId(), false);
        Location at = target.getLocation();
        float w = (float) Math.max(0.8, target.getWidth() + 0.3);
        float h = (float) target.getHeight() + 0.25f;
        Location mid = at.clone().add(0, h / 2 - 0.1, 0);
        mid.setYaw(0);
        mid.setPitch(0);
        List<org.bukkit.entity.Display> parts = new ArrayList<>();
        // a tall clear crystal around the body that always faces the viewer, plus frost on the ground
        org.bukkit.entity.ItemDisplay crystal = Fx.sprite(mid, "ice_prison", 0.05f, org.bukkit.entity.Display.Billboard.VERTICAL);
        Fx.animate(crystal, 3, Fx.sized(w * 2.0f, h * 1.25f, 0), 1);
        parts.add(crystal);
        Location ground = at.clone().add(0, 0.06, 0);
        org.bukkit.entity.ItemDisplay frost = Fx.flat(ground, "ring_frost", 0.1f, 0);
        Fx.animate(frost, 5, Fx.flatShape(w * 2.6f, 0.5f), 1);
        parts.add(frost);
        frozen.put(target.getUniqueId(), new Frozen(at, System.currentTimeMillis() + ticks * 50L, parts, caster, shatterDamage));
        target.setVelocity(new Vector());
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false, false));
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), target.getMaxFreezeTicks()));
        if (target instanceof Mob mob) mob.setAware(false);
        target.getWorld().playSound(at, Sound.BLOCK_GLASS_PLACE, 1f, 0.7f);
        target.getWorld().playSound(at, Sound.BLOCK_POWDER_SNOW_PLACE, 1f, 0.6f);
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, mid, 25, w * 0.5, h * 0.4, w * 0.5, 0.03);
    }

    public boolean isFrozen(UUID id) {
        Frozen f = frozen.get(id);
        return f != null && f.until > System.currentTimeMillis();
    }

    public Location frozenAt(UUID id) {
        Frozen f = frozen.get(id);
        return f == null ? null : f.at;
    }

    private void unfreeze(UUID id, boolean shatter) {
        Frozen f = frozen.remove(id);
        if (f == null) return;
        LivingEntity e = (LivingEntity) plugin.getServer().getEntity(id);
        World w = f.at.getWorld();
        // the prison shatters: a burst of light and ice shards
        for (org.bukkit.entity.Display part : f.shell) {
            if (!part.isValid()) continue;
            Fx.animate(part, 4, Fx.sized(0.01f, 0.01f, 0.4f), 1);
            Fx.removeLater(part, 6);
        }
        Fx.flare(f.at.clone().add(0, 1, 0), "flare_white", 2.4f, 8);
        Location c = f.at.clone().add(0, 1, 0);
        w.spawnParticle(Particle.BLOCK, c, 40, 0.35, 0.6, 0.35, 0, Material.ICE.createBlockData());
        w.spawnParticle(Particle.SNOWFLAKE, c, 15, 0.4, 0.6, 0.4, 0.05);
        w.playSound(c, Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
        if (e != null && e.isValid()) {
            e.removePotionEffect(PotionEffectType.SLOWNESS);
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true));
            if (e instanceof Mob mob) mob.setAware(true);
            if (shatter && f.shatterDamage > 0 && f.caster.isOnline()) {
                Targets.damage(f.caster, e, f.shatterDamage, 0x8ecae6);
            }
        }
    }

    // ------------------------------------------------------------------ inverted controls

    public void invert(Player target, int ticks) {
        Inverted old = inverted.remove(target.getUniqueId());
        if (old != null && old.marker.isValid()) old.marker.remove();
        TextDisplay marker = target.getWorld().spawn(target.getLocation().add(0, 2.4, 0), TextDisplay.class, d -> {
            d.text(Component.text("⇄", TextColor.color(0x00f5d4)).decorate(TextDecoration.BOLD));
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new Quaternionf(), new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
        });
        target.addPassenger(marker);
        inverted.put(target.getUniqueId(), new Inverted(System.currentTimeMillis() + ticks * 50L, marker));
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, ticks + 40, 0, false, false, true));
    }

    /**
     * Inverted controls, using the keys the player is actually holding (Paper 1.21.3+ input API):
     * W walks you backwards, S forwards, A right, D left. You can move freely - just mirrored.
     */
    private void steerInverted(Player p) {
        org.bukkit.Input in = p.getCurrentInput();
        int forward = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
        int left = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        if (forward == 0 && left == 0) return;
        forward = -forward;
        left = -left;
        double yaw = Math.toRadians(p.getLocation().getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // facing direction
        double lx = Math.cos(yaw), lz = Math.sin(yaw);    // your left-hand side
        double vx = fx * forward + lx * left, vz = fz * forward + lz * left;
        double len = Math.sqrt(vx * vx + vz * vz);
        double speed = in.isSprint() ? 0.42 : 0.32;
        p.setVelocity(new Vector(vx / len * speed, p.getVelocity().getY(), vz / len * speed));
    }

    public boolean isInverted(UUID id) {
        Inverted i = inverted.get(id);
        return i != null && i.until > System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ hotbar scramble (Entropy)

    /** Shuffles the hotbar for `ticks`, then puts every item back where it was (amounts that changed are kept). */
    public void scrambleHotbar(Player p, int ticks) {
        if (scrambled.containsKey(p.getUniqueId())) return;   // already scrambled: don't stack
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 9; i++) order.add(i);
        java.util.Collections.shuffle(order);
        org.bukkit.inventory.ItemStack[] bar = new org.bukkit.inventory.ItemStack[9];
        for (int i = 0; i < 9; i++) bar[i] = inv.getItem(i);
        int[] perm = new int[9];
        for (int i = 0; i < 9; i++) {
            perm[i] = order.get(i);
            inv.setItem(i, bar[perm[i]]);
        }
        int held = inv.getHeldItemSlot();
        inv.setHeldItemSlot(java.util.concurrent.ThreadLocalRandom.current().nextInt(9));
        scrambled.put(p.getUniqueId(), new Scramble(perm, held, System.currentTimeMillis() + ticks * 50L));
    }

    public void unscramble(Player p) {
        Scramble s = scrambled.remove(p.getUniqueId());
        if (s == null) return;
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        org.bukkit.inventory.ItemStack[] now = new org.bukkit.inventory.ItemStack[9];
        for (int i = 0; i < 9; i++) now[i] = inv.getItem(i);
        for (int i = 0; i < 9; i++) inv.setItem(s.perm()[i], now[i]);
        inv.setHeldItemSlot(s.heldSlot());
        p.sendActionBar(Component.text("Your hotbar is back to normal", TextColor.color(0x00f5d4)));
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------ burrow (Umbra)

    public void burrow(Player p, int ticks, Runnable onEnd) {
        burrowed.put(p.getUniqueId(), new Burrow(System.currentTimeMillis() + ticks * 50L, onEnd));
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(p)) other.hidePlayer(plugin, p);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ticks + 5, 0, false, false, true));
    }

    public boolean isBurrowed(UUID id) {
        return burrowed.containsKey(id);
    }

    /** Ends the burrow early (attacking, casting) or on expiry. Runs the eruption callback. */
    public void endBurrow(Player p, boolean runCallback) {
        Burrow b = burrowed.remove(p.getUniqueId());
        if (b == null) return;
        for (Player other : plugin.getServer().getOnlinePlayers()) other.showPlayer(plugin, p);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (runCallback && p.isOnline()) b.onEnd.run();
    }

    /**
     * Umbra's burrow trail: the ground you're tunnelling under crumbles - a dense spray of crumbs of that exact block
     * kicked up around and behind you, like something moving just under the surface.
     */
    private void crumbleTrail(Player p, Burrow b) {
        Location feet = p.getLocation();
        org.bukkit.block.Block ground = feet.clone().subtract(0, 0.2, 0).getBlock();
        if (!ground.getType().isSolid()) ground = ground.getRelative(0, -1, 0);
        if (!ground.getType().isSolid()) return;
        org.bukkit.block.data.BlockData data = ground.getBlockData();
        World w = p.getWorld();
        Location surface = feet.clone();
        surface.setY(ground.getY() + 1.02);
        Vector back = new Vector();
        if (b.lastWall != null && b.lastWall.getWorld().equals(feet.getWorld())) {
            back = b.lastWall.toVector().subtract(feet.toVector()).setY(0);
        }
        b.lastWall = feet.clone();
        boolean moving = back.lengthSquared() > 0.0025;
        // crumbs right on top of you, popping up
        w.spawnParticle(Particle.BLOCK, surface, moving ? 26 : 10, 0.35, 0.04, 0.35, 0.12, data);
        // a scattered tail of crumbs along the path behind you
        if (moving) {
            Vector step = back.clone().normalize();
            for (int i = 1; i <= 4; i++) {
                Location tail = surface.clone().add(step.clone().multiply(i * 0.45));
                w.spawnParticle(Particle.BLOCK, tail, 8, 0.28 + i * 0.05, 0.02, 0.28 + i * 0.05, 0.05, data);
            }
            w.spawnParticle(Particle.FALLING_DUST, surface, 3, 0.4, 0.05, 0.4, 0, data);
        }
    }

    /** Hide currently burrowed players from someone who just joined. */
    public void hideBurrowedFrom(Player joiner) {
        for (UUID id : burrowed.keySet()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && !p.equals(joiner)) joiner.hidePlayer(plugin, p);
        }
    }

    // ------------------------------------------------------------------ shields (Solar)

    public void setShield(UUID uuid, Shield shield) { shields.put(uuid, shield); }
    public void clearShield(UUID uuid) { shields.remove(uuid); }

    public Shield activeShield(UUID uuid) {
        Shield s = shields.get(uuid);
        if (s == null) return null;
        if (s.expiresAt() < System.currentTimeMillis()) {
            shields.remove(uuid);
            return null;
        }
        return s;
    }

    public boolean isReflecting() { return reflecting; }

    public void reflect(Player owner, LivingEntity attacker, double amount, boolean burn) {
        reflecting = true;
        try {
            Targets.damage(owner, attacker, amount, 0xffd166);
        } finally {
            reflecting = false;
        }
        if (burn) attacker.setFireTicks(Math.max(attacker.getFireTicks(), 80));
        Location from = owner.getLocation().add(0, 1.1, 0), to = attacker.getLocation().add(0, attacker.getHeight() * 0.6, 0);
        Fx.ray(from, to, "beam_gold", 0.7f, 6);
        Fx.ray(from, to, "beam_white", 0.22f, 4);
        Fx.flare(to, "flare_gold", 1.8f, 7);
        owner.getWorld().playSound(owner.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.9f, 1.4f);
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 2f);
    }

    // ------------------------------------------------------------------ forcefields (Magnetosphere)

    public void addField(Location center, double radius, int ticks, UUID owner, boolean repel) {
        Field f = new Field(center, radius, System.currentTimeMillis() + ticks * 50L, owner, repel);
        for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity && !(e instanceof org.bukkit.entity.ArmorStand) && f.contains(e.getLocation())) {
                f.trapped.add(e.getUniqueId());
            }
        }
        buildDome(f);
        fields.add(f);
    }

    /**
     * The forcefield itself: a real 3D dome of translucent blue hexagon panels (no particles), built band by band
     * from the ground up. Each panel faces outward and is curved into place on the sphere.
     */
    private void buildDome(Field f) {
        double r = f.radius;
        boolean hide = plugin.getConfig().getBoolean("magnetosphere.hide-from-caster", false);
        Player owner = plugin.getServer().getPlayer(f.owner);
        int bands = 6;
        double dLat = Math.PI / 2 / bands;
        for (int k = 0; k < bands; k++) {
            double lat = (k + 0.5) * dLat;
            double ring = Math.cos(lat) * r;
            float h = (float) (r * dLat * 1.05);
            int n = Math.max(3, (int) Math.round(2 * Math.PI * ring / (h * 1.15)));
            float w = (float) (2 * Math.PI * ring / n * 1.05);
            double offset = (k % 2) * Math.PI / n;
            for (int i = 0; i < n; i++) {
                double a = offset + i * 2 * Math.PI / n;
                Location at = f.center.clone().add(Math.cos(a) * ring, Math.sin(lat) * r, Math.sin(a) * ring);
                Quaternionf rot = new Quaternionf().rotateY((float) (Math.PI / 2 - a)).rotateX((float) -lat);
                org.bukkit.entity.ItemDisplay tile = Fx.sprite(at, "hex_tile", 0.01f, Display.Billboard.FIXED);
                tile.setViewRange(2.5f);
                tile.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
                Fx.animate(tile, 5, new Transformation(new Vector3f(), new Quaternionf(rot), new Vector3f(w, h, 1), new Quaternionf()), 1 + k * 2L);
                if (hide && owner != null) owner.hideEntity(plugin, tile);
                f.tiles.add(tile);
            }
        }
        // a small cap closes the top
        Location top = f.center.clone().add(0, r - 0.02, 0);
        org.bukkit.entity.ItemDisplay cap = Fx.flat(top, "hex_tile", 0.01f, 0);
        cap.setViewRange(2.5f);
        Fx.animate(cap, 5, Fx.flatShape((float) (r * dLat * 1.6), 0), 1 + bands * 2L);
        if (hide && owner != null) owner.hideEntity(plugin, cap);
        f.tiles.add(cap);
    }

    /** The active field covering this location that the player does NOT own, or null. */
    public Field blockingField(Location l, UUID who) {
        long now = System.currentTimeMillis();
        for (Field f : fields) {
            if (f.until > now && !f.owner.equals(who) && f.contains(l)) return f;
        }
        return null;
    }

    private boolean bypassBarrier = false;

    /**
     * The field this move would illegally cross (a trapped entity leaving, or an outsider entering), or null.
     * The caster can pass freely unless magnetosphere.caster-can-pass is false.
     */
    public Field barrierCrossed(org.bukkit.entity.Entity e, Location from, Location to) {
        if (bypassBarrier || fields.isEmpty()) return null;
        if (e instanceof Player p && (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || plugin.cutscenes().isPlaying(p.getUniqueId()))) return null;
        boolean casterPasses = plugin.getConfig().getBoolean("magnetosphere.caster-can-pass", true);
        long now = System.currentTimeMillis();
        for (Field f : fields) {
            if (f.until <= now) continue;
            if (casterPasses && f.owner.equals(e.getUniqueId())) continue;
            boolean was = f.contains(from), is = f.contains(to);
            if (was == is) continue;
            if (f.trapped.contains(e.getUniqueId()) ? (was && !is) : (!was && is)) return f;
        }
        return null;
    }

    /** Bounce someone off the dome wall: a ripple of light where they hit it, a zap, and a shove back. */
    public void bounce(Field f, org.bukkit.entity.Entity e, Location hitAt) {
        boolean wasInside = f.trapped.contains(e.getUniqueId());
        Vector out = hitAt.toVector().subtract(f.center.toVector());
        if (out.lengthSquared() < 0.01) out = new Vector(1, 0, 0);
        out.normalize();
        Vector push = out.clone().multiply(wasInside ? -0.45 : 0.55);
        push.setY(wasInside ? 0.05 : 0.25);
        e.setVelocity(push);
        long now = System.currentTimeMillis();
        Long last = f.lastFlash.get(e.getUniqueId());
        if (last != null && now - last < 400) return;
        f.lastFlash.put(e.getUniqueId(), now);
        Location wall = f.center.clone().add(out.clone().multiply(f.radius));
        wall.setY(Math.max(f.center.getY() + 0.3, Math.min(hitAt.getY() + 1, wall.getY())));
        Fx.flare(wall, "flare_cyan", 1.8f, 6);
        org.bukkit.entity.ItemDisplay ripple = Fx.sprite(wall, "hex_tile", 0.3f, Display.Billboard.CENTER);
        Fx.animate(ripple, 6, Fx.sized(2.2f, 2.2f, 0), 1);
        Fx.animate(ripple, 4, Fx.sized(0.01f, 0.01f, 0), 7);
        Fx.removeLater(ripple, 13);
        wall.getWorld().playSound(wall, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 2f);
        wall.getWorld().playSound(wall, Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.5f);
        if (e instanceof Player p) {
            p.sendActionBar(Component.text(wasInside ? "You're trapped inside the Magnetosphere" : "A Magnetosphere forcefield blocks the way",
                    TextColor.color(0x48cae4)));
        }
        Player owner = plugin.getServer().getPlayer(f.owner);
        if (f.repel && owner != null && e instanceof LivingEntity le && Targets.isEnemy(owner, le)) {
            Targets.damage(owner, le, plugin.getConfig().getDouble("magnetosphere.repulsor-shock-damage", 3), 0x48cae4);
        }
    }

    /** Safety net (knockback, mobs, lag): put anyone on the wrong side of a wall back where they belong. */
    private void enforceBarrier(Field f) {
        boolean casterPasses = plugin.getConfig().getBoolean("magnetosphere.caster-can-pass", true);
        double r = f.radius;
        for (org.bukkit.entity.Entity e : f.center.getWorld().getNearbyEntities(f.center, r + 2, r + 2, r + 2)) {
            if (!(e instanceof LivingEntity) || e instanceof org.bukkit.entity.ArmorStand) continue;
            if (casterPasses && e.getUniqueId().equals(f.owner)) continue;
            if (e instanceof Player p && (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || plugin.cutscenes().isPlaying(p.getUniqueId()))) continue;
            Location l = e.getLocation();
            boolean in = f.contains(l);
            boolean trapped = f.trapped.contains(e.getUniqueId());
            if (in == trapped) continue;
            Vector out = l.toVector().subtract(f.center.toVector());
            if (out.lengthSquared() < 0.01) out = new Vector(1, 0, 0);
            out.normalize();
            Location fix = f.center.clone().add(out.clone().multiply(trapped ? r - 0.8 : r + 0.8));
            fix.setY(Math.max(fix.getY(), l.getY()));
            if (trapped) fix.setY(Math.min(fix.getY(), f.center.getY() + Math.sqrt(Math.max(0, r * r - fix.clone().subtract(f.center).toVector().setY(0).lengthSquared())) - 0.3));
            fix.setYaw(l.getYaw());
            fix.setPitch(l.getPitch());
            bypassBarrier = true;
            try {
                e.teleport(fix);
            } finally {
                bypassBarrier = false;
            }
            bounce(f, e, l);
        }
    }

    // ------------------------------------------------------------------ fall immunity

    public void addFallImmunity(UUID uuid) { fallImmune.add(uuid); }
    public void removeFallImmunity(UUID uuid) { fallImmune.remove(uuid); }
    public boolean isFallImmune(UUID uuid) { return fallImmune.contains(uuid); }

    // ------------------------------------------------------------------ tick (every tick)

    private int tick = 0;

    public void tick() {
        tick++;
        long now = System.currentTimeMillis();

        List<UUID> thawed = new ArrayList<>();
        for (Map.Entry<UUID, Frozen> en : frozen.entrySet()) {
            Frozen f = en.getValue();
            LivingEntity e = (LivingEntity) plugin.getServer().getEntity(en.getKey());
            if (e == null || !e.isValid() || e.isDead() || f.until <= now) {
                thawed.add(en.getKey());
                continue;
            }
            if (!(e instanceof Player)) {
                Location keep = f.at.clone();
                keep.setYaw(e.getLocation().getYaw());
                keep.setPitch(e.getLocation().getPitch());
                e.teleport(keep);
                e.setVelocity(new Vector());
            }
            if (tick % 8 == 0) {
                e.getWorld().spawnParticle(Particle.SNOWFLAKE, e.getLocation().add(0, e.getHeight() / 2, 0), 2, 0.3, 0.5, 0.3, 0);
            }
        }

        for (UUID id : thawed) {
            org.bukkit.entity.Entity e = plugin.getServer().getEntity(id);
            unfreeze(id, e != null && e.isValid() && !e.isDead());
        }

        for (Map.Entry<UUID, Scramble> en : new ArrayList<>(scrambled.entrySet())) {
            if (en.getValue().until() > now) continue;
            Player p = plugin.getServer().getPlayer(en.getKey());
            if (p != null) unscramble(p); else scrambled.remove(en.getKey());
        }

        for (Iterator<Map.Entry<UUID, Inverted>> it = inverted.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Inverted> en = it.next();
            Player p = plugin.getServer().getPlayer(en.getKey());
            if (p == null || en.getValue().until <= now) {
                if (en.getValue().marker.isValid()) en.getValue().marker.remove();
                it.remove();
                continue;
            }
            steerInverted(p);
            if (tick % 4 == 0) {
                Location head = p.getLocation().add(0, 2.1, 0);
                double a = tick * 0.4;
                Fx.ring(p.getWorld(), head, 0.45, 3, a, Fx.dust(0x00f5d4, 0.7f));
                Fx.ring(p.getWorld(), head, 0.45, 3, -a, Fx.dust(0xf72585, 0.7f));
            }
        }

        List<Player> surfacing = new ArrayList<>();
        for (Map.Entry<UUID, Burrow> en : burrowed.entrySet()) {
            Player p = plugin.getServer().getPlayer(en.getKey());
            if (p == null) continue;
            if (en.getValue().until <= now) {
                surfacing.add(p);
                continue;
            }
            // crumbling-ground trail: the only clue other players get
            crumbleTrail(p, en.getValue());
            Location feet = p.getLocation();
            if (tick % 10 == 0) p.getWorld().playSound(feet, Sound.BLOCK_GRAVEL_STEP, 0.4f, 0.6f);
            if (tick % 10 == 0) p.sendActionBar(Component.text("Burrowed - hidden from everyone", TextColor.color(0xd9b38c)));
        }
        burrowed.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
        for (Player p : surfacing) endBurrow(p, true);

        for (Iterator<Field> it = fields.iterator(); it.hasNext(); ) {
            Field f = it.next();
            if (f.until <= now) {
                it.remove();
                collapse(f);
                continue;
            }
            if (tick % 2 == 0) enforceBarrier(f);
            if (f.repel && tick % 2 == 0) repel(f);
        }
    }

    private void repel(Field f) {
        Player owner = plugin.getServer().getPlayer(f.owner);
        if (owner == null) return;
        for (org.bukkit.entity.Entity e : f.center.getWorld().getNearbyEntities(f.center, f.radius, f.radius, f.radius)) {
            if (!f.contains(e.getLocation())) continue;
            if (e instanceof Projectile proj) {
                if (proj.getShooter() instanceof org.bukkit.entity.Entity shooter && Targets.isEnemy(owner, shooter)) {
                    proj.setVelocity(proj.getVelocity().multiply(-0.8));
                    proj.setShooter(owner);
                    e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation(), 6, 0.1, 0.1, 0.1, 0.1);
                }
            }
        }
    }

    /** The dome dissolves panel by panel from the top down. */
    private void collapse(Field f) {
        World w = f.center.getWorld();
        w.playSound(f.center, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 1.2f);
        w.playSound(f.center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.8f);
        for (org.bukkit.entity.ItemDisplay tile : f.tiles) {
            if (!tile.isValid()) continue;
            Transformation t = tile.getTransformation();
            long delay = 1 + Math.max(0, Math.round((tile.getLocation().getY() - f.center.getY()) / f.radius * -6 + 6));
            Fx.animate(tile, 5, new Transformation(t.getTranslation(), t.getLeftRotation(), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()), delay);
            Fx.removeLater(tile, delay + 7);
        }
        f.tiles.clear();
    }

    /** Clean up everything for a player who logs out. */
    public void clear(UUID uuid) {
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) unscramble(online);
        shields.remove(uuid);
        fallImmune.remove(uuid);
        Inverted i = inverted.remove(uuid);
        if (i != null && i.marker.isValid()) i.marker.remove();
        if (frozen.containsKey(uuid)) unfreeze(uuid, false);
        Burrow b = burrowed.remove(uuid);
        if (b != null) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) for (Player other : plugin.getServer().getOnlinePlayers()) other.showPlayer(plugin, p);
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(scrambled.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) unscramble(p);
        }
        for (Frozen f : frozen.values()) f.shell.forEach(d -> { if (d.isValid()) d.remove(); });
        for (UUID id : new ArrayList<>(frozen.keySet())) unfreeze(id, false);
        for (Inverted i : inverted.values()) if (i.marker.isValid()) i.marker.remove();
        inverted.clear();
        for (UUID id : new ArrayList<>(burrowed.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) endBurrow(p, false);
        }
        for (Field f : fields) f.tiles.forEach(t -> { if (t.isValid()) t.remove(); });
        fields.clear();
    }
}
