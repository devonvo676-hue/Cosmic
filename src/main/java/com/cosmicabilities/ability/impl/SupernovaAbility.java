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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * A miniature sun ignites above your head and charges, drawing in light. When it's full it fires solar beams
 * down into every enemy around you, then the shockwave hits.
 */
public class SupernovaAbility extends CosmicAbility {
    private static final int CHARGE = 22;
    private static final double RADIUS = 7;

    public SupernovaAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.SUPERNOVA); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        ItemDisplay sun = Fx.model(player.getLocation().add(0, 3.4, 0), "planet_sun", 0.05f);
        sun.setTeleportDuration(2);
        sun.setGlowing(true);
        sun.setGlowColorOverride(org.bukkit.Color.fromRGB(0xffb703));
        w.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.6f);
        w.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.5f);

        every(CHARGE + 30, t -> {
            if (!player.isOnline() || player.isDead()) { sun.remove(); return false; }
            Location c = player.getLocation().add(0, 3.4, 0);
            sun.teleport(c);
            float spin = t * 0.2f;
            if (t <= CHARGE) {
                // the sun swells while light and flame stream into it
                float scale = 0.2f + 1.3f * t / CHARGE;
                Fx.animate(sun, 2, Fx.spin(scale, new Quaternionf().rotateXYZ(spin * 0.6f, spin, 0.2f)), 1);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + t * 0.3;
                    double r = 3.5 * (1 - (t % 8) / 8.0) + 0.8;
                    w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r, c.getY() + Math.sin(a * 2) * 0.8 - 0.6,
                            c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, Fx.dust(i % 2 == 0 ? 0xffd166 : 0xff8c42, 1.3f));
                }
                Fx.ring(w, c, 0.9 + scale * 0.5, 14, -spin, Particle.FLAME);    // corona
                w.spawnParticle(Particle.LAVA, c, 1, 0.3, 0.3, 0.3, 0);
                if (t % 4 == 0) w.playSound(c, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.6f + t * 0.06f);
            }
            if (t == CHARGE) fire(player, c, slot);
            if (t > CHARGE) {
                // the spent sun shrinks and fades
                float scale = Math.max(0.01f, 1.8f * (1 - (t - CHARGE) / 10f));
                Fx.animate(sun, 2, Fx.spin(scale, new Quaternionf().rotateXYZ(spin, spin * 1.5f, 0)), 1);
                if (t >= CHARGE + 10) {
                    sun.remove();
                    if (bonus(slot) && t == CHARGE + 22) {
                        blast(player, player.getLocation().add(0, 1, 0), RADIUS + 1.5, dmg(6, slot), 0.9, true);
                        return false;
                    }
                    if (!bonus(slot)) return false;
                }
            }
            return true;
        });
        return true;
    }

    /** The release: a flash, a beam of sunlight into every enemy in range, then the shockwave around you. */
    private void fire(Player player, Location sunAt, AbilitySlot slot) {
        World w = sunAt.getWorld();
        Fx.burst(w, sunAt, Particle.END_ROD, 40, 0.5);
        Fx.burst(w, sunAt, Particle.FLAME, 40, 0.35);
        w.playSound(sunAt, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
        w.playSound(sunAt, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f, 1.2f);
        List<LivingEntity> targets = Targets.enemies(player, player.getLocation(), RADIUS);
        for (LivingEntity e : targets) {
            Location hitAt = e.getLocation().add(0, e.getHeight() * 0.6, 0);
            Fx.beam(sunAt, hitAt, Material.ORANGE_STAINED_GLASS, 0.45f, 8);
            Fx.beam(sunAt, hitAt, Material.SHROOMLIGHT, 0.14f, 6);
            hit(player, e, dmg(9, slot));
            e.setFireTicks(Math.max(e.getFireTicks(), 80));
            w.spawnParticle(Particle.FLAME, hitAt, 15, 0.3, 0.4, 0.3, 0.05);
        }
        blast(player, player.getLocation().add(0, 1, 0), 5, dmg(3, slot), 1.1, false);
    }

    private void blast(Player player, Location c, double radius, double damage, double kb, boolean after) {
        World w = c.getWorld();
        Material[] shells = after
                ? new Material[]{Material.RED_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.MAGMA_BLOCK}
                : new Material[]{Material.ORANGE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.SHROOMLIGHT};
        float[] sizes = {(float) radius * 1.9f, (float) radius * 1.5f, (float) radius * 0.6f};
        for (int i = 0; i < shells.length; i++) {
            Quaternionf rot = new Quaternionf().rotateXYZ(0.4f * i, 0.7f * i, 0.25f);
            BlockDisplay shell = Fx.block(c, shells[i], new Vector3f(0.2f, 0.2f, 0.2f), rot, true);
            float sz = sizes[i];
            Fx.animate(shell, 4, Fx.centred(sz, sz, sz, new Quaternionf(rot).rotateY(0.6f)), 1);
            Fx.animate(shell, 4, Fx.centred(0.05f, 0.05f, 0.05f, new Quaternionf(rot).rotateY(1.2f)), 6);
            Fx.removeLater(shell, 11);
        }
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, after ? 0.6f : 0.9f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1);
        Fx.burst(w, c, Particle.FLAME, 50, 0.45);
        Location ground = c.clone().subtract(0, 0.9, 0);
        every(7, t -> {
            double r = radius * (t + 1) / 7.0;
            Fx.ring(w, ground, r, (int) (r * 10), t * 0.2, Particle.FLAME);
            Fx.ring(w, ground.clone().add(0, 0.15, 0), r * 0.92, (int) (r * 6), 0, Fx.dust(after ? 0xd00000 : 0xff8c42, 1.5f));
            w.spawnParticle(Particle.LARGE_SMOKE, ground, (int) r * 2, r * 0.5, 0.05, r * 0.5, 0.01);
            return true;
        });
        for (LivingEntity e : Targets.enemies(player, c, radius)) {
            hit(player, e, damage);
            Targets.knockAway(c, e, kb, 0.55);
            e.setFireTicks(Math.max(e.getFireTicks(), 60));
        }
    }
}
