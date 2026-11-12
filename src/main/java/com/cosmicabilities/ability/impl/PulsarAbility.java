package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pulsar: hurl a spinning neutron star. It flies until it reaches an enemy or a wall, then hangs there spinning,
 * sweeping two lighthouse beams around while pulsing out shockwaves, and finally collapses in a big detonation.
 */
public class PulsarAbility extends CosmicAbility {
    private static final double RANGE = 30, SPEED = 1.1;
    private static final int SPIN_TICKS = 50;

    public PulsarAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.PULSAR); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        Vector dir = player.getEyeLocation().getDirection().normalize();
        Location start = player.getEyeLocation().add(dir.clone().multiply(1.2)).subtract(0, 0.2, 0);
        boolean quasar = bonus(slot);
        double beamLen = quasar ? 9 : 6.5;
        ItemDisplay star = Fx.sprite(start, "pulsar", 0.05f, Display.Billboard.CENTER);
        star.setTeleportDuration(1);
        Fx.animate(star, 4, Fx.sized(1.4f, 1.4f, 3f), 1);
        ItemDisplay halo = Fx.sprite(start, "flare_cyan", 0.05f, Display.Billboard.CENTER);
        halo.setTeleportDuration(1);
        Fx.animate(halo, 4, Fx.sized(2.6f, 2.6f, 0), 1);
        ItemDisplay[] beams = {beam(start), beam(start)};
        w.playSound(start, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f);
        w.playSound(start, Sound.ITEM_TRIDENT_THROW, 1.2f, 0.6f);
        Map<UUID, Integer> lastBeamHit = new HashMap<>();
        Location[] anchor = new Location[1];
        int[] anchoredAt = new int[1];
        every((int) (RANGE / SPEED) + SPIN_TICKS + 10, t -> {
            if (!player.isOnline()) { star.remove(); halo.remove(); for (ItemDisplay b : beams) b.remove(); return false; }
            Location at;
            if (anchor[0] == null) {
                // flying: stop at the first enemy or wall
                at = start.clone().add(dir.clone().multiply(Math.min(RANGE, t * SPEED)));
                boolean stop = t * SPEED >= RANGE || at.getBlock().getType().isSolid() || !Targets.enemies(player, at, 1.6).isEmpty();
                if (at.getBlock().getType().isSolid()) at.subtract(dir.clone().multiply(0.8));
                if (t % 2 == 0) w.spawnParticle(Particle.END_ROD, at, 2, 0.1, 0.1, 0.1, 0.01);
                w.spawnParticle(Particle.DUST, at, 3, 0.15, 0.15, 0.15, 0, Fx.dust(type.rgb(), 1.3f));
                if (stop) {
                    anchor[0] = at.clone();
                    anchoredAt[0] = t;
                    w.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.8f);
                    w.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 1.4f);
                }
            } else {
                at = anchor[0];
            }
            Location spot = at.clone(); spot.setYaw(0); spot.setPitch(0);
            star.teleport(spot);
            halo.teleport(spot);
            if (anchor[0] == null) return true;
            int k = t - anchoredAt[0];
            // the two lighthouse beams: tilted, sweeping round (they speed up as it goes)
            double angle = k * (0.18 + k * 0.004);
            float len = (float) Math.min(beamLen * 2, beamLen * 2 * (k + 1) / 6.0);
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI / 2;
                Quaternionf rot = new Quaternionf().rotateY((float) a).rotateZ((float) Math.toRadians(i == 0 ? 75 : 105));
                ItemDisplay b = beams[i];
                b.teleport(spot);
                b.setInterpolationDelay(0);
                b.setInterpolationDuration(1);
                b.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(i == 0 ? 0.7f : 0.45f, len, i == 0 ? 0.7f : 0.45f), new Quaternionf()));
                // damage enemies the beam sweeps over (each at most every 10 ticks)
                Vector3f axis = rot.transform(new Vector3f(0, 1, 0));
                Vector ax = new Vector(axis.x, axis.y, axis.z);
                for (LivingEntity e : Targets.enemies(player, at, len / 2 + 1)) {
                    Vector to = e.getLocation().add(0, e.getHeight() / 2, 0).toVector().subtract(at.toVector());
                    double along = to.dot(ax);
                    double off = to.clone().subtract(ax.clone().multiply(along)).length();
                    if (off > 1.1 || Math.abs(along) > len / 2) continue;
                    Integer last = lastBeamHit.get(e.getUniqueId());
                    if (last != null && t - last < 10) continue;
                    lastBeamHit.put(e.getUniqueId(), t);
                    hit(player, e, 2.5);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.2);
                    if (quasar) {
                        Vector pull = at.toVector().subtract(e.getLocation().toVector());
                        if (pull.lengthSquared() > 1) e.setVelocity(e.getVelocity().add(pull.normalize().multiply(0.35)));
                    }
                }
            }
            if (k % 3 == 0) w.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 0.6f + k * 0.03f);
            // pulses: a ring of light every 12 ticks
            if (k > 0 && k % 12 == 0) {
                Fx.wave(at.clone().subtract(0, 0.4, 0), "ring_cyan", 0.5f, 8f, 8);
                ItemDisplay ring = Fx.disc(at, "ring_white", 0.3f, new Vector(0, 1, 0).add(dir.clone().multiply(0.3)));
                Fx.animate(ring, 6, Fx.discShape(6f, new Vector(0, 1, 0), 1f), 1);
                Fx.removeLater(ring, 8);
                w.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.8f);
                for (LivingEntity e : Targets.enemies(player, at, 4)) {
                    hit(player, e, 2);
                    e.setVelocity(e.getVelocity().add(new Vector(0, 0.15, 0)));
                }
            }
            if (k >= SPIN_TICKS) {
                // collapse and detonate
                Fx.animate(star, 3, Fx.sized(0.2f, 0.2f, 6f), 0);
                star.remove(); halo.remove();
                for (ItemDisplay b : beams) b.remove();
                Fx.flare(at, "flare_white", 7f, 10);
                Fx.flare(at, "flare_cyan", 10f, 14);
                Fx.wave(at.clone().subtract(0, 0.5, 0), "ring_white", 1f, 11f, 10);
                Fx.wave(at.clone().subtract(0, 0.3, 0), "ring_cyan", 1f, 9f, 8);
                w.spawnParticle(Particle.END_ROD, at, 60, 0.2, 0.2, 0.2, 0.5);
                w.spawnParticle(Particle.ELECTRIC_SPARK, at, 60, 1.5, 1.5, 1.5, 0.4);
                w.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 1.6f);
                w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2f, 0.6f);
                for (LivingEntity e : Targets.enemies(player, at, 5)) {
                    hit(player, e, 6);
                    Targets.knockAway(at, e, 1.1, 0.6);
                }
                return false;
            }
            return true;
        });
        return true;
    }

    private ItemDisplay beam(Location at) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta m = item.getItemMeta();
        m.setItemModel(new NamespacedKey("cosmic", "fxbeam_beam_cyan"));
        item.setItemMeta(m);
        Location spot = at.clone(); spot.setYaw(0); spot.setPitch(0);
        return at.getWorld().spawn(spot, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.setTeleportDuration(1);
            d.setDisplayWidth(24);
            d.setDisplayHeight(24);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
        });
    }
}
