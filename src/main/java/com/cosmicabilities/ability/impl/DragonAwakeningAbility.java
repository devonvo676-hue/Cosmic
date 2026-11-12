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
 * Ender Dragon drop: a massive spectral Ender Dragon bursts out behind you and charges wherever you're looking
 * (up, down, diagonal), biting everything in its path, leaving dragon breath behind. (10 hearts by default)
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
        Vector dir = player.getEyeLocation().getDirection().normalize();
        double range = plugin.getConfig().getDouble("ability-settings." + type.id() + ".range", 34);
        double bite = plugin.getConfig().getDouble("ability-settings." + type.id() + ".hearts", 10);
        Location start = player.getEyeLocation().subtract(dir.clone().multiply(6)).add(0, 1.5, 0);
        org.bukkit.entity.EnderDragon dragon;
        try {
            dragon = w.spawn(start, org.bukkit.entity.EnderDragon.class, d -> {
                d.addScoreboardTag(TAG);
                d.addScoreboardTag(Fx.TAG);
                d.setPersistent(false);
                d.setAI(false);
                d.setInvulnerable(true);
                d.setSilent(true);
                d.setGlowing(true);
                d.setCollidable(false);
                d.setRemoveWhenFarAway(false);
                d.setPhase(org.bukkit.entity.EnderDragon.Phase.HOVER);
                d.setRotation(yawOf(dir) + 180f, 0);
            });
        } catch (RuntimeException e) {
            return false;
        }
        Set<UUID> bitten = new HashSet<>();
        w.playSound(start, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.9f);
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(start) < 80 * 80) p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.2f, 0.8f);
        int ticks = 28;
        List<Location> trail = new ArrayList<>();
        every(ticks + 30, t -> {
            if (!dragon.isValid()) return false;
            if (t <= ticks) {
                double prog = t / (double) ticks;
                double ease = prog < 0.15 ? prog * prog / 0.15 : prog;          // a short wind-up, then full speed
                Location at = start.clone().add(dir.clone().multiply((range + 6) * ease));
                // stay above the ground
                double floor = w.getHighestBlockYAt(at) + 1.5;
                if (at.getY() < floor && dir.getY() < 0.3) at.setY(floor);
                at.setYaw(yawOf(dir) + 180f);
                at.setPitch(0);
                dragon.teleport(at);
                trail.add(at.clone());
                Location head = at.clone().add(dir.clone().multiply(6.5));
                w.spawnParticle(Particle.DUST, head, 12, 1.5, 1.2, 1.5, 0, Fx.dust(type.rgb(), 2.4f));
                w.spawnParticle(Particle.PORTAL, at, 30, 3, 1.2, 3, 0.4);
                Fx.safe(w, Particle.DRAGON_BREATH, at.clone().subtract(dir.clone().multiply(3)), 14, 1.2, 0.6, 1.2, 0.01, 1.0f);
                if (t % 4 == 0) w.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.9f);
                for (LivingEntity e : Targets.enemies(player, head, 4.5)) bite(player, e, bite, dir, bitten);
                for (LivingEntity e : Targets.enemies(player, at, 4.0)) bite(player, e, bite, dir, bitten);
                if (t == ticks) w.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.6f);
                return true;
            }
            // vanish into purple smoke
            if (t == ticks + 1) {
                Location at = dragon.getLocation();
                w.spawnParticle(Particle.REVERSE_PORTAL, at, 200, 4, 2, 4, 0.2);
                w.spawnParticle(Particle.DUST, at, 80, 4, 2, 4, 0, Fx.dust(type.rgb(), 3f));
                dragon.remove();
                if (bonus(slot)) {   // lingering dragon breath along its path
                    for (int i = 0; i < trail.size(); i += 4) {
                        Location c = trail.get(i).clone();
                        c.setY(w.getHighestBlockYAt(c) + 1);
                        if (c.distanceSquared(trail.get(i)) > 36) continue;
                        every(100, k -> {
                            if (k % 3 == 0) Fx.safe(w, Particle.DRAGON_BREATH, c, 8, 1.4, 0.15, 1.4, 0.005, 1.0f);
                            if (k % 5 == 0) w.spawnParticle(Particle.DUST, c, 4, 1.4, 0.2, 1.4, 0, Fx.dust(type.rgb(), 1.4f));
                            if (k % 20 == 10) for (LivingEntity e : Targets.enemies(player, c, 2.4)) hearts(player, e, 1);
                            return true;
                        });
                    }
                }
                return false;
            }
            return true;
        });
        return true;
    }

    private void bite(Player player, LivingEntity e, double hearts, Vector dir, Set<UUID> bitten) {
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
