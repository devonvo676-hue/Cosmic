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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Charge (sparks converge on you), then a layered star-shell explosion with a ground shockwave. */
public class SupernovaAbility extends CosmicAbility {
    public SupernovaAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.SUPERNOVA); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        w.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.7f);
        every(12, t -> {
            if (!player.isOnline()) return false;
            Location c = player.getLocation().add(0, 1, 0);
            double r = 3.5 * (1 - t / 12.0) + 0.4;
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5 + t * 0.25;
                double y = Math.sin(a * 2 + t) * 0.8;
                w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r, c.getY() + y, c.getZ() + Math.sin(a) * r,
                        1, 0, 0, 0, 0, Fx.dust(t % 2 == 0 ? 0xffd166 : 0xff8c42, 1.2f));
            }
            if (t % 3 == 0) w.playSound(c, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 0.8f + t * 0.1f);
            if (t == 12) {
                blast(player, player.getLocation().add(0, 1, 0), 5.5, dmg(9, slot), 1.15, false);
                if (bonus(slot)) {
                    Location at = player.getLocation().add(0, 1, 0);
                    later(22, () -> { if (player.isOnline()) blast(player, at, 8, dmg(6, slot), 0.9, true); });
                }
            }
            return true;
        });
        return true;
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
        w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f, 0.7f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1);
        Fx.burst(w, c, Particle.FLAME, 50, 0.45);
        Fx.burst(w, c, Particle.END_ROD, 25, 0.35);
        // ground shockwave rolling outward
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
