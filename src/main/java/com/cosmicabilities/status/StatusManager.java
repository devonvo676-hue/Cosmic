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
        final BlockDisplay shell;
        final Player caster;
        final double shatterDamage;
        Frozen(Location at, long until, BlockDisplay shell, Player caster, double shatterDamage) {
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
        Burrow(long until, Runnable onEnd) { this.until = until; this.onEnd = onEnd; }
    }

    public static final class Field {
        public final Location center;
        public final double radius;
        public final long until;
        public final UUID owner;
        public final boolean repel;
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
    /** Guards against two Solar shields reflecting damage back and forth forever. */
    private boolean reflecting = false;

    public StatusManager(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ freeze

    /** Encases the target in ice. Players are pinned by the move listener; mobs are pinned by tick(). */
    public void freeze(LivingEntity target, int ticks, Player caster, double shatterDamage) {
        unfreeze(target.getUniqueId(), false);
        Location at = target.getLocation();
        float w = (float) Math.max(0.8, target.getWidth() + 0.35);
        float h = (float) target.getHeight() + 0.3f;
        Location shellAt = at.clone().add(0, h / 2 - 0.1, 0);
        shellAt.setYaw(0);
        shellAt.setPitch(0);
        BlockDisplay shell = Fx.block(shellAt, Material.ICE, new Vector3f(0.1f, 0.1f, 0.1f), new Quaternionf(), false);
        Fx.animate(shell, 3, Fx.centred(w, h, w, new Quaternionf()), 1);
        frozen.put(target.getUniqueId(), new Frozen(at, System.currentTimeMillis() + ticks * 50L, shell, caster, shatterDamage));
        target.setVelocity(new Vector());
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false, false));
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), target.getMaxFreezeTicks()));
        if (target instanceof Mob mob) mob.setAware(false);
        target.getWorld().playSound(at, Sound.BLOCK_GLASS_PLACE, 1f, 0.7f);
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
        if (f.shell.isValid()) f.shell.remove();
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
            d.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new Quaternionf(), new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
        });
        target.addPassenger(marker);
        inverted.put(target.getUniqueId(), new Inverted(System.currentTimeMillis() + ticks * 50L, marker));
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, ticks + 40, 0, false, false, true));
    }

    public boolean isInverted(UUID id) {
        Inverted i = inverted.get(id);
        return i != null && i.until > System.currentTimeMillis();
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
        Fx.line(owner.getWorld(), owner.getLocation().add(0, 1.1, 0), attacker.getLocation().add(0, attacker.getHeight() * 0.6, 0),
                0.25, Fx.dust(0xffb703, 1.1f));
        owner.getWorld().playSound(owner.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.9f, 1.4f);
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 2f);
    }

    // ------------------------------------------------------------------ forcefields (Magnetosphere)

    public void addField(Location center, double radius, int ticks, UUID owner, boolean repel) {
        fields.add(new Field(center, radius, System.currentTimeMillis() + ticks * 50L, owner, repel));
    }

    /** The active field covering this location that the player does NOT own, or null. */
    public Field blockingField(Location l, UUID who) {
        long now = System.currentTimeMillis();
        for (Field f : fields) {
            if (f.until > now && !f.owner.equals(who) && f.contains(l)) return f;
        }
        return null;
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

        for (Iterator<Map.Entry<UUID, Inverted>> it = inverted.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Inverted> en = it.next();
            Player p = plugin.getServer().getPlayer(en.getKey());
            if (p == null || en.getValue().until <= now) {
                if (en.getValue().marker.isValid()) en.getValue().marker.remove();
                it.remove();
                continue;
            }
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
            // "tremor" trail: the only clue other players get
            Location feet = p.getLocation();
            org.bukkit.block.Block ground = feet.clone().subtract(0, 0.2, 0).getBlock();
            if (ground.getType().isSolid()) {
                p.getWorld().spawnParticle(Particle.BLOCK, feet.clone().add(0, 0.1, 0), 6, 0.3, 0.05, 0.3, 0, ground.getBlockData());
            }
            if (tick % 3 == 0) p.getWorld().spawnParticle(Particle.SMOKE, feet.clone().add(0, 0.1, 0), 2, 0.2, 0, 0.2, 0.01);
            if (tick % 10 == 0) p.getWorld().playSound(feet, Sound.BLOCK_GRAVEL_STEP, 0.4f, 0.6f);
            if (tick % 10 == 0) p.sendActionBar(Component.text("Burrowed - hidden from everyone", TextColor.color(0xba68ff)));
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
            drawField(f);
            if (f.repel && tick % 2 == 0) repel(f);
        }
    }

    private void drawField(Field f) {
        World w = f.center.getWorld();
        double r = f.radius;
        Particle.DustOptions edge = Fx.dust(0x48cae4, 0.9f);
        Particle.DustOptions bright = Fx.dust(0xcaf0f8, 1.1f);
        if (tick % 3 == 0) {
            // rotating meridians
            double spin = tick * 0.03;
            for (int m = 0; m < 6; m++) {
                double a = spin + m * Math.PI / 3;
                for (int k = 1; k <= 10; k++) {
                    double lat = k / 10.0 * Math.PI / 2;
                    double y = Math.sin(lat) * r, rr = Math.cos(lat) * r;
                    w.spawnParticle(Particle.DUST, f.center.getX() + Math.cos(a) * rr, f.center.getY() + y,
                            f.center.getZ() + Math.sin(a) * rr, 1, 0, 0, 0, 0, edge);
                }
            }
        }
        if (tick % 6 == 0) {
            // latitude rings + ground ring
            for (double lat : new double[]{0, 0.35, 0.75}) {
                double rr = Math.cos(lat) * r;
                Location ring = f.center.clone().add(0, Math.sin(lat) * r, 0);
                Fx.ring(w, ring, rr, (int) (rr * 9), tick * 0.02, lat == 0 ? bright : edge);
            }
        }
        if (tick % 5 == 0) {
            // a hex panel flickers somewhere on the dome
            java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
            double a = rnd.nextDouble(Math.PI * 2), lat = rnd.nextDouble(0.1, 1.2);
            Location p = f.center.clone().add(Math.cos(a) * Math.cos(lat) * r, Math.sin(lat) * r, Math.sin(a) * Math.cos(lat) * r);
            w.spawnParticle(Particle.END_ROD, p, 3, 0.15, 0.15, 0.15, 0);
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
            } else if (Targets.isEnemy(owner, e)) {
                Targets.knockAway(f.center, (LivingEntity) e, 0.9, 0.35);
                e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.1);
            }
        }
    }

    private void collapse(Field f) {
        World w = f.center.getWorld();
        w.playSound(f.center, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 1.2f);
        for (int i = 0; i < 4; i++) {
            Location ring = f.center.clone().add(0, f.radius * (0.9 - i * 0.25), 0);
            double rr = Math.sqrt(Math.max(0, f.radius * f.radius - Math.pow(ring.getY() - f.center.getY(), 2)));
            Fx.ring(w, ring, rr, (int) (rr * 8) + 4, 0, Particle.END_ROD);
        }
    }

    /** Clean up everything for a player who logs out. */
    public void clear(UUID uuid) {
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
        for (UUID id : new ArrayList<>(frozen.keySet())) unfreeze(id, false);
        for (Inverted i : inverted.values()) if (i.marker.isValid()) i.marker.remove();
        inverted.clear();
        for (UUID id : new ArrayList<>(burrowed.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) endBurrow(p, false);
        }
        fields.clear();
    }
}
