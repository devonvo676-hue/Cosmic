package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ender Dragon drop: a massive spectral Ender Dragon tears out of your back, rises over your shoulders and dashes
 * at whoever you're aiming at (homing in on them), biting everything in its way. (10 hearts by default)
 */
public class DragonAwakeningAbility extends CosmicAbility implements org.bukkit.event.Listener {
    public static final String TAG = "cosmic_spectral_dragon";

    public DragonAwakeningAbility(CosmicAbilitiesPlugin plugin) {
        super(plugin, AbilityType.DRAGON_AWAKENING);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        double range = plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 40);
        double bite = 10;
        // the target: whoever you're aiming at (generous aim), else the closest enemy in front of you
        LivingEntity target = aimed(player, range);
        Vector look = player.getEyeLocation().getDirection().normalize();
        Vector flat = Targets.flatDirection(player);
        // 1) the dragon tears out of your back: it starts inside you and rises up and back over your shoulders
        Location back = player.getLocation().add(0, 1.2, 0).subtract(flat.clone().multiply(0.6));
        org.bukkit.entity.EnderDragon dragon;
        try {
            dragon = w.spawn(back, org.bukkit.entity.EnderDragon.class, d -> {
                d.addScoreboardTag(TAG);
                d.addScoreboardTag(Fx.TAG);
                d.setPersistent(false);
                d.setAI(false);
                d.setInvulnerable(true);
                d.setSilent(true);
                d.setGlowing(true);
                d.setCollidable(false);
                d.setPhase(org.bukkit.entity.EnderDragon.Phase.HOVER);
                d.setRotation(yawOf(look) + 180f, 0);
            });
        } catch (RuntimeException e) {
            return false;
        }
        Set<UUID> bitten = new HashSet<>();
        w.playSound(back, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 1.0f);
        w.playSound(back, Sound.ENTITY_WARDEN_EMERGE, 1.5f, 1.4f);
        int emerge = 14, dashMax = 30;
        Location[] pos = {back.clone()};
        every(emerge + dashMax + 2, t -> {
            if (!dragon.isValid() || !player.isOnline()) { dragon.remove(); return false; }
            if (t <= emerge) {
                // rising out of the player's back
                double k = t / (double) emerge;
                Vector f = Targets.flatDirection(player);
                Location p = player.getLocation().add(0, 1.2 + k * 5.5, 0).subtract(f.clone().multiply(0.6 + k * 4.5));
                Location aimAt = target != null && target.isValid() ? target.getLocation().add(0, 1, 0) : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(20));
                Vector face = aimAt.toVector().subtract(p.toVector());
                p.setYaw(yawOf(face) + 180f);
                p.setPitch(0);
                dragon.teleport(p);
                pos[0] = p;
                Location spine = player.getLocation().add(0, 1.3, 0).subtract(f.clone().multiply(0.3));
                w.spawnParticle(Particle.DUST, spine, 14, 0.25, 0.35, 0.25, 0, Fx.dust(type.rgb(), 2f));
                w.spawnParticle(Particle.REVERSE_PORTAL, spine, 20, 0.3, 0.4, 0.3, 0.15);
                Fx.safe(w, Particle.DRAGON_BREATH, p, 10, 2, 1, 2, 0.02, 1.0f);
                if (t % 4 == 0) w.playSound(p, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 1.1f);
                if (t == emerge) {
                    w.playSound(p, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.7f);
                    w.playSound(p, Sound.ENTITY_ENDER_DRAGON_SHOOT, 2f, 0.6f);
                }
                return true;
            }
            // 2) the dash: homes in on the target (or straight where you looked) at high speed
            Location cur = pos[0];
            Location goal;
            if (target != null && target.isValid() && !target.isDead()) goal = target.getLocation().add(0, target.getHeight() * 0.5, 0);
            else goal = cur.clone().add(look.clone().multiply(3));
            Vector step = goal.toVector().subtract(cur.toVector());
            double dist = step.length();
            if (dist > 0.01) step.normalize().multiply(Math.min(dist, 2.6));
            Location next = cur.clone().add(step);
            Vector heading = step.lengthSquared() > 1e-4 ? step.clone().normalize() : look;
            // the dragon's head sticks out ~6 blocks ahead of its body: lead with the head
            Location body = next.clone().subtract(heading.clone().multiply(5.5));
            body.setYaw(yawOf(heading) + 180f);
            body.setPitch(0);
            dragon.teleport(body);
            pos[0] = next;
            w.spawnParticle(Particle.DUST, next, 14, 1.5, 1, 1.5, 0, Fx.dust(type.rgb(), 2.6f));
            w.spawnParticle(Particle.PORTAL, body, 30, 3, 1.2, 3, 0.4);
            Fx.safe(w, Particle.DRAGON_BREATH, body, 14, 1.4, 0.6, 1.4, 0.01, 1.0f);
            if (t % 3 == 0) w.playSound(next, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.5f, 1.2f);
            for (LivingEntity e : Targets.enemies(player, next, 4.5)) biteHit(player, e, bite, heading, bitten);
            boolean reached = target == null ? (t - emerge) * 2.6 >= range : (target.isDead() || bitten.contains(target.getUniqueId()));
            if (reached || t >= emerge + dashMax) {
                w.playSound(next, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.6f);
                w.spawnParticle(Particle.REVERSE_PORTAL, body, 220, 4, 2, 4, 0.25);
                w.spawnParticle(Particle.DUST, next, 80, 3, 2, 3, 0, Fx.dust(type.rgb(), 3f));
                Fx.wave(next.clone().subtract(0, 1, 0), "ring_purple", 1f, 9f, 10);
                if (bonus(slot)) {
                    for (LivingEntity e : Targets.enemies(player, next, 6)) {
                        e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, true, true));
                        Targets.knockAway(next, e, 1.0, 0.6);
                    }
                }
                dragon.remove();
                return false;
            }
            return true;
        });
        return true;
    }

    /** Whoever the player is aiming at (2-block wide aim), else the closest enemy in a cone in front of them. */
    private LivingEntity aimed(Player player, double range) {
        var ray = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), range, 2.0,
                e -> e instanceof LivingEntity && Targets.isEnemy(player, e));
        if (ray != null && ray.getHitEntity() instanceof LivingEntity le) return le;
        Vector look = player.getEyeLocation().getDirection().normalize();
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : Targets.enemies(player, player.getLocation(), range)) {
            Vector to = e.getLocation().add(0, 1, 0).toVector().subtract(player.getEyeLocation().toVector());
            double dot = to.clone().normalize().dot(look);
            if (dot < 0.8) continue;
            double score = dot - to.length() / (range * 4);
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    private void biteHit(Player player, LivingEntity e, double hearts, Vector dir, Set<UUID> bitten) {
        if (!bitten.add(e.getUniqueId())) return;
        hearts(player, e, hearts);
        e.setVelocity(dir.clone().multiply(1.4).setY(Math.max(0.7, dir.getY() + 0.5)));
        World w = e.getWorld();
        w.playSound(e.getLocation(), Sound.ENTITY_EVOKER_FANGS_ATTACK, 2f, 0.5f);
        w.playSound(e.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 1.5f, 1.4f);
        w.spawnParticle(Particle.DAMAGE_INDICATOR, e.getLocation().add(0, 1, 0), 12, 0.4, 0.5, 0.4, 0.2);
    }

    private static float yawOf(Vector v) {
        return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
    }

    // the spectral dragon never breaks blocks, hurts anyone by touch, or takes damage
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onGrief(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (event.getEntity().getScoreboardTags().contains(TAG)) { event.setCancelled(true); event.blockList().clear(); }
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (event.getEntity().getScoreboardTags().contains(TAG)) event.setCancelled(true);
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onTouch(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        Entity d = event.getDamager();
        if (d instanceof org.bukkit.entity.ComplexEntityPart part) d = part.getParent();
        if (d.getScoreboardTags().contains(TAG)) event.setCancelled(true);
        Entity v = event.getEntity();
        if (v instanceof org.bukkit.entity.ComplexEntityPart part2) v = part2.getParent();
        if (v.getScoreboardTags().contains(TAG)) event.setCancelled(true);
    }
}
