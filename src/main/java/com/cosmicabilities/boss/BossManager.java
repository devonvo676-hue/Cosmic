package com.cosmicabilities.boss;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.item.CosmicMaterial;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Spawns, tracks, and rewards the five cosmic bosses. */
public class BossManager {
    public static final class ActiveBoss {
        final LivingEntity entity;
        final BossType type;
        final BossBar bar;
        final Map<UUID, Double> damage = new HashMap<>();
        int seconds = 0;

        ActiveBoss(LivingEntity entity, BossType type, BossBar bar) {
            this.entity = entity;
            this.type = type;
            this.bar = bar;
        }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey bossKey;
    private final NamespacedKey requiredKey;
    private final Map<UUID, ActiveBoss> active = new HashMap<>();

    public BossManager(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "cosmic_boss");
        this.requiredKey = new NamespacedKey(plugin, "required_players");
    }

    public BossType typeOf(Entity entity) {
        String id = entity.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
        return BossType.fromId(id);
    }

    /** Players that must be near this boss for it to take damage (set per fight, else the config default). */
    public int requiredPlayers(Entity boss) {
        Integer r = boss.getPersistentDataContainer().get(requiredKey, PersistentDataType.INTEGER);
        return r != null ? r : plugin.getConfig().getInt("bosses.min-players-to-damage", 3);
    }

    public LivingEntity spawn(BossType type, Location loc, int requiredPlayers) {
        LivingEntity boss = spawn(type, loc);
        if (boss != null) boss.getPersistentDataContainer().set(requiredKey, PersistentDataType.INTEGER, Math.max(1, requiredPlayers));
        return boss;
    }

    public LivingEntity spawn(BossType type, Location loc) {
        World world = loc.getWorld();
        Entity raw = world.spawnEntity(loc, type.entityType);
        if (!(raw instanceof LivingEntity boss)) {
            raw.remove();
            return null;
        }
        double health = cfg(type, "health", 600) * healthMult();
        double damage = cfg(type, "damage", 10) * damageMult();
        setBase(boss, Attribute.MAX_HEALTH, Math.min(2048, health));
        AttributeInstance maxHp = boss.getAttribute(Attribute.MAX_HEALTH);
        boss.setHealth(maxHp == null ? Math.min(boss.getHealth(), health) : maxHp.getValue());   // the server caps max health (spigot.yml, 1024 by default)
        setBase(boss, Attribute.SCALE, size(type));
        setBase(boss, Attribute.ARMOR, cfg(type, "armor", 10));
        setBase(boss, Attribute.ARMOR_TOUGHNESS, cfg(type, "armor-toughness", 4));
        setBase(boss, Attribute.MOVEMENT_SPEED, cfg(type, "speed", 0.3));
        setBase(boss, Attribute.ATTACK_DAMAGE, damage);
        setBase(boss, Attribute.KNOCKBACK_RESISTANCE, cfg(type, "knockback-resistance", 0.8));
        setBase(boss, Attribute.FOLLOW_RANGE, cfg(type, "follow-range", 40));

        if (boss instanceof org.bukkit.entity.Ageable baby) baby.setAdult();
        if (boss instanceof org.bukkit.entity.Hoglin hog) { hog.setImmuneToZombification(true); hog.setIsAbleToBeHunted(false); }
        if (boss instanceof org.bukkit.entity.PiglinAbstract pig) pig.setImmuneToZombification(true);
        boss.setSilent(true);   // the model makes its own sounds
        boss.customName(plugin.mm(type.colored()));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, type.id());
        boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false));

        EntityEquipment eq = boss.getEquipment();
        if (boss instanceof org.bukkit.entity.IronGolem golem) golem.setPlayerCreated(false);
        if (eq != null && type == BossType.VOID_EMPEROR) {
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            eq.setItemInMainHandDropChance(0f);
            eq.setHelmetDropChance(0f);
        } else if (eq != null && type == BossType.FROST_LEVIATHAN) {
            eq.setItemInMainHand(new ItemStack(Material.BOW));
            eq.setHelmet(new ItemStack(Material.BLUE_ICE));
            eq.setItemInMainHandDropChance(0f);
            eq.setHelmetDropChance(0f);
        }

        track(boss, type);
        if (plugin.getConfig().getBoolean("bosses.cutscene", true)) plugin.cutscenes().playBossIntro(boss, type);
        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        Bukkit.broadcast(plugin.mm(plugin.prefix() + type.colored() + " <gray>has descended at <white>"
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "<gray> in <white>" + world.getName()));
        return boss;
    }

    /** bosses.<boss>.<key>, falling back to bosses.<key>, then def. Everything about a boss is configurable this way. */
    public double cfg(BossType type, String key, double def) {
        return plugin.getConfig().getDouble("bosses." + type.id() + "." + key, plugin.getConfig().getDouble("bosses." + key, def));
    }

    /** How big the model is: bosses.<boss>.size (1 = normal) times the global size multiplier. */
    public double modelScale(BossType type) {
        return Math.max(0.2, Math.min(5, cfg(type, "size", 1.0) * sizeMult()));
    }

    /** The entity scale - chosen per boss so the hitbox matches the model at any size. */
    public double size(BossType type) {
        return Math.max(0.1, Math.min(16, type.scale * modelScale(type)));
    }

    /** Damage of a boss's special attack (bosses.<boss>.special-damage), times the global damage multiplier. */
    private double special(BossType type, double def) {
        return cfg(type, "special-damage", def) * damageMult();
    }

    /** bosses.health-multiplier / damage-multiplier / size-multiplier: global boss strength knobs. */
    public double healthMult() { return Math.max(0.05, plugin.getConfig().getDouble("bosses.health-multiplier", 1.0)); }
    public double damageMult() { return Math.max(0, plugin.getConfig().getDouble("bosses.damage-multiplier", 1.0)); }
    public double sizeMult() { return Math.max(0.25, Math.min(4, plugin.getConfig().getDouble("bosses.size-multiplier", 1.0))); }

    private static void setBase(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) inst.setBaseValue(value);
    }

    /** Starts tracking a boss (also used to re-adopt bosses after a restart / chunk reload). */
    public ActiveBoss track(LivingEntity boss, BossType type) {
        ActiveBoss existing = active.get(boss.getUniqueId());
        if (existing != null) return existing;
        String title = PlainTextComponentSerializer.plainText().serialize(plugin.mm(type.colored()));
        BossBar bar = Bukkit.createBossBar(title, type.barColor, BarStyle.SEGMENTED_10);
        ActiveBoss ab = new ActiveBoss(boss, type, bar);
        active.put(boss.getUniqueId(), ab);
        plugin.bossRig().attach(boss, type);
        return ab;
    }

    public int aliveCount() {
        int n = 0;
        for (ActiveBoss ab : active.values()) if (ab.entity.isValid() && !ab.entity.isDead()) n++;
        return n;
    }

    public ActiveBoss get(UUID uuid) {
        return active.get(uuid);
    }

    public void recordDamage(LivingEntity boss, Player player, double amount) {
        BossType type = typeOf(boss);
        if (type == null) return;
        ActiveBoss ab = track(boss, type);
        ab.damage.merge(player.getUniqueId(), amount, Double::sum);
    }

    /** Runs once per second. */
    public void tick() {
        int range = plugin.getConfig().getInt("bosses.bossbar-range", 48);

        Iterator<ActiveBoss> it = active.values().iterator();
        while (it.hasNext()) {
            ActiveBoss ab = it.next();
            LivingEntity boss = ab.entity;
            if (!boss.isValid() || boss.isDead()) {
                ab.bar.removeAll();
                it.remove();
                continue;
            }
            AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = max == null ? boss.getHealth() : max.getValue();
            ab.bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / maxHp)));

            List<Player> nearby = new ArrayList<>();
            for (Player p : boss.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(boss.getLocation()) <= (double) range * range) nearby.add(p);
            }
            for (Player p : new ArrayList<>(ab.bar.getPlayers())) if (!nearby.contains(p)) ab.bar.removePlayer(p);
            for (Player p : nearby) if (!ab.bar.getPlayers().contains(p)) ab.bar.addPlayer(p);

            ambient(ab);
            // always go for players (the iron golem / hoglin base mobs would otherwise pick other targets)
            if (boss instanceof Mob mob && boss.hasAI() && !(mob.getTarget() instanceof Player)) {
                double fr = cfg(ab.type, "follow-range", 40);
                Player best = null;
                double bd = fr * fr;
                for (Player p : targets(boss, fr)) {
                    double d = p.getLocation().distanceSquared(boss.getLocation());
                    if (d < bd) { bd = d; best = p; }
                }
                if (best != null) mob.setTarget(best);
            }
            ab.seconds++;
            int interval = (int) Math.max(2, cfg(ab.type, "special-attack-interval", 6));
            if (!boss.hasAI()) continue;   // still in its entrance cutscene
            if (ab.seconds % interval == 0) special(ab);
        }
    }

    private final Map<UUID, Long> lastHurtSound = new HashMap<>();

    /** The base mob is silent (it's hidden under the model), so each boss makes its own sounds. */
    public void hurtSound(LivingEntity boss, BossType type) {
        long now = System.currentTimeMillis();
        if (now - lastHurtSound.getOrDefault(boss.getUniqueId(), 0L) < 400) return;
        lastHurtSound.put(boss.getUniqueId(), now);
        Location l = boss.getLocation();
        float pitch = (float) Math.max(0.4, 1.0 - size(type) * 0.12);
        Sound s = switch (type) {
            case VOID_EMPEROR -> Sound.ENTITY_WITHER_SKELETON_HURT;
            case SOLAR_TITAN -> Sound.ENTITY_IRON_GOLEM_HURT;
            case FROST_LEVIATHAN -> Sound.ENTITY_ELDER_GUARDIAN_HURT;
            case NEBULA_SERPENT -> Sound.ENTITY_ENDER_DRAGON_HURT;
            case COSMIC_DESTROYER -> Sound.ENTITY_RAVAGER_HURT;
        };
        l.getWorld().playSound(l, s, 2f, pitch);
    }

    private void ambientSound(ActiveBoss ab) {
        Location l = ab.entity.getLocation();
        Sound s = switch (ab.type) {
            case VOID_EMPEROR -> Sound.ENTITY_WITHER_AMBIENT;
            case SOLAR_TITAN -> Sound.ENTITY_BLAZE_AMBIENT;
            case FROST_LEVIATHAN -> Sound.ENTITY_ELDER_GUARDIAN_AMBIENT;
            case NEBULA_SERPENT -> Sound.ENTITY_ENDER_DRAGON_AMBIENT;
            case COSMIC_DESTROYER -> Sound.ENTITY_RAVAGER_AMBIENT;
        };
        l.getWorld().playSound(l, s, 1.6f, 0.6f);
    }

    private void ambient(ActiveBoss ab) {
        if (ab.seconds % 5 == 2) ambientSound(ab);
        Location c = ab.entity.getLocation().add(0, ab.entity.getHeight() * 0.6, 0);
        World w = c.getWorld();
        switch (ab.type) {
            case VOID_EMPEROR -> w.spawnParticle(Particle.PORTAL, c, 30, 0.6, 1, 0.6, 0.5);
            case SOLAR_TITAN -> w.spawnParticle(Particle.FLAME, c, 15, 0.8, 1, 0.8, 0.02);
            case FROST_LEVIATHAN -> w.spawnParticle(Particle.SNOWFLAKE, c, 20, 0.8, 1, 0.8, 0.02);
            case NEBULA_SERPENT -> w.spawnParticle(Particle.DUST, c, 15, 0.8, 1, 0.8, 0,
                    new Particle.DustOptions(Color.fromRGB(247, 37, 133), 1.8f));
            case COSMIC_DESTROYER -> w.spawnParticle(Particle.END_ROD, c, 10, 1, 1, 1, 0.02);
        }
    }

    private List<Player> targets(LivingEntity boss, double radius) {
        List<Player> list = new ArrayList<>();
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            if (p.getLocation().distanceSquared(boss.getLocation()) <= radius * radius) list.add(p);
        }
        return list;
    }

    private void special(ActiveBoss ab) {
        LivingEntity boss = ab.entity;
        Location loc = boss.getLocation();
        World w = loc.getWorld();
        List<Player> players = targets(boss, cfg(ab.type, "special-radius", 14));
        if (players.isEmpty()) return;
        plugin.bossRig().attackPose(boss.getUniqueId());

        switch (ab.type) {
            case VOID_EMPEROR -> {
                // Gravitational pull + Darkness, then blinks behind a random target.
                w.playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 1.5f, 0.5f);
                for (Player p : players) {
                    Vector pull = loc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.1).setY(0.3);
                    p.setVelocity(pull);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                    p.damage(special(ab.type, 5), boss);
                }
                Player victim = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                Vector facing = victim.getLocation().getDirection().setY(0);
                if (facing.lengthSquared() < 1.0E-4) facing = new Vector(0, 0, 1);
                Location behind = victim.getLocation().clone().subtract(facing.normalize().multiply(2));
                if (behind.getBlock().isPassable() && behind.clone().add(0, 1, 0).getBlock().isPassable()) {
                    w.spawnParticle(Particle.REVERSE_PORTAL, loc, 60, 0.5, 1, 0.5, 0.1);
                    boss.teleport(behind);
                    w.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.6f);
                }
            }
            case SOLAR_TITAN -> {
                w.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.5f);
                w.playSound(loc, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2f, 0.4f);
                w.spawnParticle(Particle.LAVA, loc, 40, 3, 0.3, 3, 0);
                for (int r = 2; r <= 8; r += 2) {
                    for (int i = 0; i < r * 8; i++) {
                        double a = 2 * Math.PI * i / (r * 8);
                        w.spawnParticle(Particle.FLAME, loc.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 1, 0, 0, 0, 0);
                    }
                }
                for (Player p : targets(boss, 8)) {
                    p.damage(special(ab.type, 8), boss);
                    p.setFireTicks(100);
                }
            }
            case FROST_LEVIATHAN -> {
                w.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 2f, 0.5f);
                w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 200, 5, 1, 5, 0.05);
                for (Player p : targets(boss, 10)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
                    p.setFreezeTicks(Math.min(p.getMaxFreezeTicks() + 100, p.getFreezeTicks() + 200));
                    p.damage(special(ab.type, 5), boss);
                }
            }
            case NEBULA_SERPENT -> {
                // nebula breath: a cone of cosmic fire in front of the drake
                w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_SHOOT, 2f, 0.7f);
                w.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.5f);
                Vector fwd = loc.getDirection().setY(0);
                if (fwd.lengthSquared() < 1e-4) fwd = new Vector(0, 0, 1);
                fwd.normalize();
                Location mouth = loc.clone().add(0, boss.getHeight() * 0.7, 0).add(fwd.clone().multiply(boss.getWidth() * 0.9));
                for (int i = 1; i <= 10; i++) {
                    Location c = mouth.clone().add(fwd.clone().multiply(i * 0.9)).subtract(0, i * 0.12, 0);
                    w.spawnParticle(Particle.DUST, c, 12, i * 0.12, i * 0.08, i * 0.12, 0, new Particle.DustOptions(Color.fromRGB(247, 37, 133), 2.2f));
                    w.spawnParticle(Particle.FLAME, c, 4, i * 0.1, i * 0.06, i * 0.1, 0.01);
                }
                for (Player p : targets(boss, cfg(ab.type, "special-radius", 12))) {
                    Vector to = p.getLocation().toVector().subtract(loc.toVector()).setY(0);
                    if (to.lengthSquared() > 1 && to.normalize().dot(fwd) < 0.4) continue;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                    p.setFireTicks(60);
                    p.damage(special(ab.type, 6), boss);
                }
            }
            case COSMIC_DESTROYER -> {
                w.playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
                for (Player p : targets(boss, 7)) {
                    Vector away = p.getLocation().toVector().subtract(loc.toVector()).setY(0);
                    if (away.lengthSquared() > 0.01) away.normalize().multiply(1.2);
                    p.setVelocity(away.setY(0.9));
                    p.damage(special(ab.type, 10), boss);
                }
            }
        }
        if (boss instanceof Mob mob && mob.getTarget() == null) mob.setTarget(players.get(0));
    }

    /** Handles a boss death: rewards contributors. Returns true if the entity was a boss. */
    public boolean handleDeath(LivingEntity boss) {
        BossType type = typeOf(boss);
        if (type == null) return false;
        ActiveBoss ab = active.remove(boss.getUniqueId());
        Map<UUID, Double> damage = ab == null ? Map.of() : ab.damage;
        if (ab != null) ab.bar.removeAll();

        Player killer = boss.getKiller();
        double total = damage.values().stream().mapToDouble(Double::doubleValue).sum();
        double minShare = plugin.getConfig().getDouble("bosses.drops.min-damage-share", 0.05);
        UUID top0 = damage.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        UUID top = top0;
        List<Player> rewarded = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : damage.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            if (total > 0 && entry.getValue() / total < minShare) continue;
            rewarded.add(p);
        }
        if (rewarded.isEmpty() && killer != null) rewarded.add(killer);

        int min = (int) cfg(type, "drops.min", plugin.getConfig().getInt("bosses.drops.min", 3));
        int max = Math.max(min, (int) cfg(type, "drops.max", plugin.getConfig().getInt("bosses.drops.max", 6)));
        double astralChance = plugin.getConfig().getDouble("bosses.drops.bonus-astral-chance", 0.25);
        int topBonus = plugin.getConfig().getInt("bosses.drops.top-damage-bonus", 2);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (Player p : rewarded) {
            int amount = rnd.nextInt(min, max + 1) + (p.getUniqueId().equals(top) ? topBonus : 0);
            type.drop.give(p, amount);
            StringBuilder msg = new StringBuilder(plugin.prefix() + "<gray>You received <white>" + amount + "x "
                    + type.drop.colored());
            if (type != BossType.COSMIC_DESTROYER && rnd.nextDouble() < astralChance) {
                int bonus = rnd.nextInt(1, 3);
                CosmicMaterial.ASTRAL_FRAGMENT.give(p, bonus);
                msg.append(" <gray>and <white>").append(bonus).append("x ").append(CosmicMaterial.ASTRAL_FRAGMENT.colored());
            }
            if (p.getUniqueId().equals(top)) msg.append(" <gold>(top damage bonus)");
            p.sendMessage(plugin.mm(msg.toString()));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        Bukkit.broadcast(plugin.mm(plugin.prefix() + type.colored() + " <gray>has been defeated"
                + (killer != null ? " by <white>" + killer.getName() : "") + "<gray>!"));
        if (top == null && killer != null) top = killer.getUniqueId();
        plugin.orbs().bossDrop(boss.getLocation(), type.colored(), type.id(), top, cfg(type, "ability-chance", 1.0));
        return true;
    }

    /** Removes every tracked boss without drops. Returns how many were removed. */
    public int despawnAll() {
        int n = 0;
        for (ActiveBoss ab : new ArrayList<>(active.values())) {
            ab.bar.removeAll();
            plugin.bossRig().detach(ab.entity.getUniqueId());
            if (ab.entity.isValid()) { ab.entity.remove(); n++; }
        }
        active.clear();
        return n;
    }

    public void shutdown() {
        plugin.bossRig().shutdown();
        for (ActiveBoss ab : active.values()) ab.bar.removeAll();
        active.clear();
    }
}
