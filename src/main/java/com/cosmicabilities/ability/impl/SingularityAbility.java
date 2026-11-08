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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Black hole: a spinning dark core with a glowing rim and a tilted accretion disk that drags enemies in. */
public class SingularityAbility extends CosmicAbility {
    private static final int DISK_IN = 0xffb703, DISK_OUT = 0x9d4edd;

    public SingularityAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.SINGULARITY); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location center = Targets.targetPoint(player, 14).add(0, 0.4, 0);
        World w = center.getWorld();
        double radius = 6;
        int duration = 80;
        double tickDmg = dmg(1.6, slot);
        boolean evolved = bonus(slot);

        BlockDisplay core = Fx.block(center, Material.BLACK_CONCRETE, new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf(), false);
        BlockDisplay rim = Fx.block(center, Material.PURPLE_STAINED_GLASS, new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf(), true);
        rim.setGlowing(true);
        rim.setGlowColorOverride(Color.fromRGB(0xc77dff));
        Fx.animate(core, 6, Fx.centred(0.9f, 0.9f, 0.9f, new Quaternionf().rotateXYZ(0.6f, 0.4f, 0)), 1);
        Fx.animate(rim, 6, Fx.centred(1.35f, 1.35f, 1.35f, new Quaternionf().rotateXYZ(-0.3f, 0.8f, 0.2f)), 1);

        w.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.5f);
        w.playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 0.6f);

        every(duration, t -> {
            if (!player.isOnline()) { cleanup(core, rim); return false; }
            float spin = t * 0.18f;
            if (t % 4 == 0 && t > 6) {
                Fx.animate(core, 4, Fx.centred(0.9f, 0.9f, 0.9f, new Quaternionf().rotateXYZ(0.6f + spin, spin * 1.3f, 0.2f)), 1);
                Fx.animate(rim, 4, Fx.centred(1.35f, 1.35f, 1.35f, new Quaternionf().rotateXYZ(-spin, -spin * 0.7f, 0.3f)), 1);
            }
            // accretion disk: a tilted ellipse, inner edge hot gold, outer edge violet
            double tilt = 0.35;
            for (int i = 0; i < 26; i++) {
                double a = spin * 2 + i * Math.PI * 2 / 26;
                double r = 1.3 + (i % 3) * 0.45;
                int col = r < 1.8 ? DISK_IN : DISK_OUT;
                double x = Math.cos(a) * r, z = Math.sin(a) * r;
                w.spawnParticle(Particle.DUST, center.getX() + x, center.getY() + z * tilt, center.getZ() + z,
                        1, 0, 0, 0, 0, Fx.dust(col, r < 1.8 ? 0.9f : 1.2f));
            }
            // matter spiralling in along 3 arms
            for (int arm = 0; arm < 3; arm++) {
                double prog = ((t * 2 + arm * 13) % 40) / 40.0;
                double r = radius * (1 - prog);
                double a = arm * 2.09 + prog * 5;
                w.spawnParticle(Particle.DUST, center.getX() + Math.cos(a) * r, center.getY() + (1 - prog) * 0.8 * Math.sin(a),
                        center.getZ() + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0, Fx.dust(0x5a189a, 1.4f));
            }
            if (t % 3 == 0) w.spawnParticle(Particle.REVERSE_PORTAL, center, 6, 0.2, 0.2, 0.2, 0.02);
            if (t % 20 == 0) w.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.6f);

            for (LivingEntity e : Targets.enemies(player, center, radius)) {
                Vector pull = center.toVector().subtract(e.getLocation().add(0, e.getHeight() / 2, 0).toVector());
                double dist = pull.length();
                if (dist > 0.6) {
                    Vector v = pull.normalize().multiply(0.16 + 0.08 * (1 - dist / radius));
                    v.setY(Math.max(e.getVelocity().getY() * 0.5, v.getY()));
                    e.setVelocity(v);
                }
                if (t % 10 == 0) {
                    hit(player, e, dist < 2 ? tickDmg * 1.6 : tickDmg);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 1, false, false, true));
                }
            }
            if (t >= duration) {
                if (evolved) collapse(player, center, radius, slot, core, rim);
                else {
                    Fx.animate(core, 5, Fx.centred(0.01f, 0.01f, 0.01f, new Quaternionf()), 1);
                    Fx.animate(rim, 5, Fx.centred(0.01f, 0.01f, 0.01f, new Quaternionf()), 1);
                    later(7, () -> cleanup(core, rim));
                    w.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
                }
                return false;
            }
            return true;
        });
        return true;
    }

    /** Event Horizon: everything is sucked to the core, the core implodes, then detonates outward. */
    private void collapse(Player player, Location c, double radius, AbilitySlot slot, BlockDisplay core, BlockDisplay rim) {
        World w = c.getWorld();
        for (LivingEntity e : Targets.enemies(player, c, radius + 1)) {
            Vector v = c.toVector().subtract(e.getLocation().toVector()).normalize().multiply(0.7);
            e.setVelocity(v);
        }
        Fx.animate(core, 6, Fx.centred(0.15f, 0.15f, 0.15f, new Quaternionf().rotateY(3f)), 1);
        Fx.animate(rim, 6, Fx.centred(2.6f, 0.08f, 2.6f, new Quaternionf().rotateX(0.35f)), 1);
        w.playSound(c, Sound.BLOCK_PORTAL_TRIGGER, 1.2f, 2f);
        later(8, () -> {
            cleanup(core, rim);
            w.spawnParticle(Particle.SONIC_BOOM, c, 1);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1);
            Fx.burst(w, c, Particle.END_ROD, 60, 0.6);
            Fx.burst(w, c, Particle.REVERSE_PORTAL, 80, 1.2);
            Fx.ring(w, c, 2.5, 40, 0, Fx.dust(DISK_IN, 1.6f));
            Fx.ring(w, c, 4.5, 60, 0.2, Fx.dust(DISK_OUT, 1.6f));
            w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 0.7f);
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
            for (LivingEntity e : Targets.enemies(player, c, radius)) {
                hit(player, e, dmg(9, slot));
                Targets.knockAway(c, e, 1.4, 0.7);
            }
        });
    }

    private static void cleanup(BlockDisplay a, BlockDisplay b) {
        if (a.isValid()) a.remove();
        if (b.isValid()) b.remove();
    }
}
