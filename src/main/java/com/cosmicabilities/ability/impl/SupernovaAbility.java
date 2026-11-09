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
import org.bukkit.util.Vector;

import java.util.List;

/**
 * A sun ignites above your head and swells as it charges, drawing light in. When it's full, it fires a searing
 * beam of sunlight into every enemy around you. Stellar Cataclysm (bonus): it fires a second volley.
 */
public class SupernovaAbility extends CosmicAbility {
    private static final int CHARGE = 24;
    private static final double RADIUS = 9;
    private static final double HEIGHT = 3.4;

    public SupernovaAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.SUPERNOVA); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        Location start = player.getLocation().add(0, HEIGHT, 0);
        ItemDisplay corona = Fx.sprite(start, "flare_gold", 0.05f, Display.Billboard.CENTER);
        ItemDisplay sun = Fx.sprite(start, "sun", 0.05f, Display.Billboard.CENTER);
        sun.setTeleportDuration(2);
        corona.setTeleportDuration(2);
        int life = CHARGE + (bonus(slot) ? 34 : 18);
        Fx.removeLater(sun, life + 4);
        Fx.removeLater(corona, life + 4);
        w.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.6f);
        w.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.5f);

        every(life, t -> {
            if (!player.isOnline() || player.isDead()) { sun.remove(); corona.remove(); return false; }
            Location c = player.getLocation().add(0, HEIGHT, 0);
            sun.teleport(c);
            corona.teleport(c);
            if (t <= CHARGE) {
                float size = 0.3f + 1.7f * t / CHARGE;
                Fx.animate(sun, 2, Fx.sized(size, size, t * 0.05f), 1);
                Fx.animate(corona, 2, Fx.sized(size * 2.4f, size * 2.4f, -t * 0.08f), 1);
                // light streams into the sun from all around
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + t * 0.25;
                    double r = 3.6 * (1 - (t % 8) / 8.0) + size * 0.5;
                    Location p = c.clone().add(Math.cos(a) * r, Math.sin(a * 2 + t * 0.3) * 0.9, Math.sin(a) * r);
                    Vector in = c.toVector().subtract(p.toVector()).normalize();
                    w.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, Fx.dust(i % 2 == 0 ? 0xffd166 : 0xff8c42, 1.1f));
                    w.spawnParticle(Particle.END_ROD, p, 0, in.getX(), in.getY(), in.getZ(), 0.25);
                }
                w.spawnParticle(Particle.FLAME, c, 2, size * 0.3, size * 0.3, size * 0.3, 0.01);
                if (t % 4 == 0) w.playSound(c, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.6f + t * 0.05f);
            }
            if (t == CHARGE) volley(player, c, dmg(13, slot), 12);
            if (bonus(slot) && t == CHARGE + 16) volley(player, c, dmg(8, slot), 10);
            if (t > life - 10) {
                float size = Math.max(0.01f, 2.0f * (life - t) / 10f);
                Fx.animate(sun, 2, Fx.sized(size, size, t * 0.05f), 1);
                Fx.animate(corona, 2, Fx.sized(size * 2f, size * 2f, -t * 0.08f), 1);
            }
            if (t >= life) { sun.remove(); corona.remove(); }
            return true;
        });
        return true;
    }

    /** The sun locks onto every enemy in range and holds a beam on each for a moment. */
    private void volley(Player player, Location sunAt, double damage, int hold) {
        World w = sunAt.getWorld();
        List<LivingEntity> targets = Targets.enemies(player, player.getLocation(), RADIUS);
        Fx.flare(sunAt, "flare_white", 5f, 8);
        w.playSound(sunAt, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
        w.playSound(sunAt, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.7f);
        if (targets.isEmpty()) {
            // nothing in range: the beam scorches the ground in front of you instead
            Location ground = player.getLocation().add(Targets.flatDirection(player).multiply(5));
            Fx.ray(sunAt, ground, "beam_gold", 0.9f, 8);
            Fx.flat(ground.clone().add(0, 0.05, 0), "scorch", 2.4f, 0.4f);
            return;
        }
        for (LivingEntity e : targets) {
            hit(player, e, damage);
            e.setFireTicks(Math.max(e.getFireTicks(), 80));
            every(hold, t -> {
                if (!e.isValid() || !player.isOnline()) return false;
                Location s = player.getLocation().add(0, HEIGHT, 0);
                Location hitAt = e.getLocation().add(0, e.getHeight() * 0.55, 0);
                if (t % 2 == 0) {
                    float width = 1.0f * (1 - t / (float) hold) + 0.25f;
                    Fx.ray(s, hitAt, "beam_gold", width, 3);
                    Fx.ray(s, hitAt, "beam_white", width * 0.35f, 3);
                }
                // embers along the beam and a burning splash on the target
                Vector step = hitAt.toVector().subtract(s.toVector());
                for (int i = 0; i < 4; i++) {
                    Location p = s.clone().add(step.clone().multiply(Math.random()));
                    w.spawnParticle(Particle.FLAME, p, 1, 0.05, 0.05, 0.05, 0.01);
                }
                w.spawnParticle(Particle.DUST, hitAt, 3, 0.2, 0.3, 0.2, 0, Fx.dust(0xffb703, 1.3f));
                if (t == 0) {
                    Fx.flare(hitAt, "flare_gold", 2.2f, 8);
                    w.playSound(hitAt, Sound.ENTITY_GENERIC_BURN, 1f, 0.8f);
                }
                return true;
            });
        }
    }
}
