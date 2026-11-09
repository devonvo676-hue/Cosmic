package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A 3D black hole opens where you aim: a pitch-black shadow ringed by light, inside a tilted, spinning accretion disk,
 * with glowing matter spiralling in. It drags enemies into its core, crushing and slowing them.
 * Event Horizon (bonus): it collapses and detonates, hurling everyone out.
 */
public class SingularityAbility extends CosmicAbility {
    private static final int HOT = 0xffd28a, WARM = 0xff8c32, VIOLET = 0x9d4edd;
    private static final float SIZE = 3.0f, DISK = 7.5f, TILT = 0.32f;

    public SingularityAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.SINGULARITY); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location center = Targets.targetPoint(player, 14).add(0, 0.6, 0);
        World w = center.getWorld();
        double radius = 6;
        int duration = 80;
        double tickDmg = dmg(2.5, slot);

        // 3D black hole: a camera-facing shadow + lensed photon ring, and a real tilted accretion disk
        // spinning around it (its front half passes in front of the shadow, its back half behind it).
        ItemDisplay hole = Fx.sprite(center, "bh_core", 0.05f, Display.Billboard.CENTER);
        Fx.animate(hole, 8, Fx.sized(SIZE, SIZE, 0), 1);
        Fx.removeLater(hole, duration + 20);
        Vector toCaster = player.getLocation().toVector().subtract(center.toVector());
        float yaw = (float) Math.atan2(toCaster.getX(), toCaster.getZ());
        Quaternionf plane = new Quaternionf().rotateY(yaw).rotateX(TILT);
        ItemDisplay disk = Fx.sprite(center, "bh_disk", 0.05f, Display.Billboard.FIXED);
        disk.setTransformation(Fx.disk(0.05f, yaw, TILT, 0));
        Fx.animate(disk, 8, Fx.disk(DISK, yaw, TILT, 0.6f), 1);
        Fx.removeLater(disk, duration + 20);
        Fx.flare(center, "flare_purple", 3f, 10);
        w.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.5f);
        w.playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 0.6f);

        every(duration, t -> {
            if (!player.isOnline()) { hole.remove(); disk.remove(); return false; }
            // a slow "breathing" pulse so it feels alive, and the disk keeps turning
            if (t > 8 && t % 10 == 0) {
                float pulse = SIZE * (t % 20 == 0 ? 1.06f : 0.97f);
                Fx.animate(hole, 10, Fx.sized(pulse, pulse, 0), 1);
                Fx.animate(disk, 10, Fx.disk(DISK, yaw, TILT, 0.6f + (t / 10) * 0.9f), 1);
            }
            // swirling matter: two tilted streams of glowing particles orbiting and falling in
            double spin = t * 0.22;
            for (int i = 0; i < 18; i++) {
                double a = spin + i * Math.PI * 2 / 18;
                double r = 1.7 + 0.25 * Math.sin(i * 1.7 + t * 0.1);
                Vector3f q = plane.transform(new Vector3f((float) (Math.cos(a) * r * 1.4), 0, (float) (Math.sin(a) * r * 1.4)));
                w.spawnParticle(Particle.DUST, center.getX() + q.x, center.getY() + q.y, center.getZ() + q.z,
                        1, 0, 0, 0, 0, Fx.dust(i % 3 == 0 ? HOT : WARM, 0.9f));
            }
            for (int arm = 0; arm < 3; arm++) {
                double prog = ((t * 2 + arm * 13) % 40) / 40.0;
                double r = radius * (1 - prog);
                double a = arm * 2.09 + prog * 6;
                Location p = center.clone().add(Math.cos(a) * r, Math.sin(a * 0.5) * 0.4 * (1 - prog), Math.sin(a) * r);
                Vector in = center.toVector().subtract(p.toVector()).normalize();
                w.spawnParticle(Particle.DUST, p, 1, 0.03, 0.03, 0.03, 0, Fx.dust(prog > 0.7 ? HOT : VIOLET, 1.2f));
                w.spawnParticle(Particle.END_ROD, p, 0, in.getX(), in.getY(), in.getZ(), 0.18);
            }
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
                    // matter being torn off the victim toward the hole
                    Fx.line(w, e.getLocation().add(0, e.getHeight() / 2, 0), center, 0.3, Fx.dust(WARM, 0.6f));
                }
            }
            if (t >= duration) {
                if (bonus(slot)) collapse(player, center, radius, slot, hole, disk, yaw);
                else {
                    Fx.animate(hole, 6, Fx.sized(0.01f, 0.01f, 1.5f), 1);
                    Fx.animate(disk, 6, Fx.disk(0.01f, yaw, TILT, 9f), 1);
                    Fx.removeLater(hole, 8);
                    Fx.removeLater(disk, 8);
                    w.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
                }
                return false;
            }
            return true;
        });
        return true;
    }

    /** Event Horizon: everything is sucked in, the hole implodes to a point, then detonates. */
    private void collapse(Player player, Location c, double radius, AbilitySlot slot, ItemDisplay hole, ItemDisplay disk, float yaw) {
        World w = c.getWorld();
        for (LivingEntity e : Targets.enemies(player, c, radius + 1)) {
            e.setVelocity(c.toVector().subtract(e.getLocation().toVector()).normalize().multiply(0.7));
        }
        Fx.animate(hole, 8, Fx.sized(0.05f, 0.05f, 3f), 1);
        Fx.animate(disk, 8, Fx.disk(0.05f, yaw, TILT, 12f), 1);
        w.playSound(c, Sound.BLOCK_PORTAL_TRIGGER, 1.2f, 2f);
        later(9, () -> {
            hole.remove();
            disk.remove();
            Fx.flare(c, "flare_white", 7f, 12);
            Fx.flare(c, "flare_gold", 5f, 16);
            Fx.wave(c.clone().subtract(0, 0.6, 0), "ring_orange", 0.5f, (float) radius * 2.4f, 10);
            Fx.wave(c.clone().subtract(0, 0.5, 0), "ring_purple", 0.5f, (float) radius * 1.6f, 14);
            w.spawnParticle(Particle.SONIC_BOOM, c, 1);
            Fx.burst(w, c, Particle.END_ROD, 60, 0.6);
            Fx.burst(w, c, Particle.REVERSE_PORTAL, 80, 1.2);
            w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 0.7f);
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
            for (LivingEntity e : Targets.enemies(player, c, radius)) {
                hit(player, e, dmg(13, slot));
                Targets.knockAway(c, e, 1.4, 0.7);
            }
        });
    }
}
