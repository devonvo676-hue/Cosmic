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
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Comet dash: low and fast, damaging everything you pass through; the earth splits into rock walls on both sides. */
public class MeteorAbility extends CosmicAbility {
    public MeteorAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.METEOR); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Vector dir = Targets.flatDirection(player);
        World w = player.getWorld();
        Set<UUID> hitSet = new HashSet<>();
        double damage = dmg(11, slot);
        UUID id = player.getUniqueId();
        status().addFallImmunity(id);
        w.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.8f);
        w.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.2f);

        every(9, t -> {
            if (!player.isOnline() || player.isDead()) { status().removeFallImmunity(id); return false; }
            Location loc = player.getLocation();
            Block ahead = loc.clone().add(dir.clone().multiply(0.8)).add(0, 0.5, 0).getBlock();
            boolean blocked = ahead.getType().isSolid() && ahead.getRelative(0, 1, 0).getType().isSolid();
            if (t >= 9 || blocked) {
                player.setVelocity(dir.clone().multiply(0.3).setY(0.1));
                if (bonus(slot)) slam(player, loc, slot);
                later(20, () -> status().removeFallImmunity(id));
                return false;
            }
            player.setVelocity(dir.clone().multiply(1.75).setY(player.isOnGround() ? 0.05 : Math.min(player.getVelocity().getY(), 0.1)));
            rupture(loc, dir, t);
            // comet head + burning tail
            Location body = loc.clone().add(0, 0.9, 0);
            w.spawnParticle(Particle.FLAME, body, 8, 0.25, 0.4, 0.25, 0.02);
            w.spawnParticle(Particle.DUST, body, 6, 0.3, 0.5, 0.3, 0, Fx.dust(0x06d6a0, 1.5f));
            w.spawnParticle(Particle.DUST, body.clone().subtract(dir.clone().multiply(0.8)), 4, 0.2, 0.3, 0.2, 0, Fx.dust(0xffd166, 1.1f));
            for (int i = 0; i < 4; i++) {  // wind streaks shooting backwards
                Location s = body.clone().add(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5);
                w.spawnParticle(Particle.END_ROD, s, 0, -dir.getX(), 0, -dir.getZ(), 0.6);
            }
            for (LivingEntity e : Targets.enemies(player, body, 1.9)) {
                if (!hitSet.add(e.getUniqueId())) continue;
                hit(player, e, damage);
                Vector side = e.getLocation().toVector().subtract(loc.toVector()).setY(0);
                if (side.lengthSquared() < 0.01) side = dir.clone();
                e.setVelocity(side.normalize().multiply(0.7).add(dir.clone().multiply(0.5)).setY(0.55));
                w.spawnParticle(Particle.EXPLOSION, e.getLocation().add(0, 1, 0), 1);
                w.playSound(e.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
            }
            return true;
        });
        return true;
    }

    /**
     * The earth splits open around the comet: jagged rock spires (made of whatever ground you're dashing over)
     * burst up on both sides of your path, leaving a channel down the middle, then sink back. Visual only,
     * no blocks are changed.
     */
    private void rupture(Location loc, Vector dir, int t) {
        World w = loc.getWorld();
        Block ground = loc.clone().subtract(0, 0.2, 0).getBlock();
        if (!ground.getType().isSolid()) ground = ground.getRelative(0, -1, 0);
        if (!ground.getType().isSolid()) return;
        Material top = ground.getType();
        Material body = earthy(top);
        double surfaceY = ground.getY() + 1.0;
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        float yaw = (float) Math.atan2(dir.getX(), dir.getZ());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 2; k++) {
                Location base = loc.clone().add(side.clone().multiply(s * r.nextDouble(1.15, 1.5)))
                        .add(dir.clone().multiply(-k * 0.85 + r.nextDouble(-0.2, 0.2)));
                base.setY(surfaceY);
                spire(base, yaw, s, top, body, (float) r.nextDouble(1.3, 2.3));
            }
        }
        Location trench = loc.clone();
        trench.setY(surfaceY + 0.05);
        w.spawnParticle(Particle.BLOCK, trench, 22, 0.9, 0.1, 0.9, 0.25, ground.getBlockData());
        w.spawnParticle(Particle.DUST, trench, 4, 0.8, 0.05, 0.8, 0, Fx.dust(0x06d6a0, 1.0f));
        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, trench, 1, 0.5, 0, 0.5, 0.01);
        if (t % 2 == 0) w.playSound(trench, Sound.BLOCK_BASALT_BREAK, 1f, 0.5f);
        if (t % 3 == 0) w.playSound(trench, Sound.BLOCK_DRIPSTONE_BLOCK_BREAK, 0.9f, 0.6f);
    }

    /** One tapered rock spire leaning away from the channel: three stacked, twisted pieces that shoot up and sink back. */
    private static void spire(Location base, float yaw, int side, Material top, Material body, float height) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location at = base.clone();
        at.setYaw(0);
        at.setPitch(0);
        Quaternionf lean = new Quaternionf().rotateY(yaw).rotateZ(-side * (float) r.nextDouble(0.18, 0.38))
                .rotateX((float) r.nextDouble(-0.12, 0.12));
        float[] widths = {0.95f, 0.72f, 0.46f};
        float[] parts = {0.42f, 0.34f, 0.24f};
        float y = 0;
        for (int i = 0; i < 3; i++) {
            float wdt = widths[i] * (float) r.nextDouble(0.85, 1.15), h = parts[i] * height;
            Quaternionf rot = new Quaternionf(lean).rotateY((float) r.nextDouble(-0.6, 0.6));
            BlockDisplay piece = Fx.block(at, i == 2 ? top : body, new Vector3f(wdt, h, wdt), rot, false);
            Vector3f sink = new Vector3f(0, -(height + 0.4f), 0);
            piece.setTransformation(pillar(lean, rot, wdt, h, y, sink));
            Fx.animate(piece, 3, pillar(lean, rot, wdt, h, y, new Vector3f(0, -0.15f, 0)), 1);
            Fx.animate(piece, 10, pillar(lean, rot, wdt, h, y, sink), 30 + r.nextInt(8));
            Fx.removeLater(piece, 52);
            y += h * 0.92f;
        }
    }

    /** A block of size w*h*w whose bottom-centre sits `y` up the leaning spire axis, shifted by `offset`. */
    private static Transformation pillar(Quaternionf lean, Quaternionf rot, float w, float h, float y, Vector3f offset) {
        Vector3f bottom = new Vector3f(0, y, 0).rotate(new Quaternionf(lean)).add(offset);
        Vector3f corner = new Vector3f(w / 2, 0, w / 2).rotate(new Quaternionf(rot));
        return new Transformation(bottom.sub(corner), new Quaternionf(rot), new Vector3f(w, h, w), new Quaternionf());
    }

    /** The rock under the turf: grass and similar become dirt, sand stays sand, stone stays stone. */
    private static Material earthy(Material m) {
        return switch (m) {
            case GRASS_BLOCK, PODZOL, MYCELIUM, DIRT_PATH, FARMLAND, ROOTED_DIRT, MOSS_BLOCK -> Material.DIRT;
            case SNOW_BLOCK, POWDER_SNOW -> Material.PACKED_ICE;
            default -> m.isOccluding() ? m : Material.STONE;
        };
    }

    /** Comet Breaker: crater slam at the end of the dash. */
    private void slam(Player player, Location at, AbilitySlot slot) {
        World w = at.getWorld();
        Block ground = at.clone().subtract(0, 0.5, 0).getBlock();
        Material groundType = ground.getType().isSolid() ? ground.getType() : Material.STONE;
        w.spawnParticle(Particle.BLOCK, at, 80, 1.6, 0.3, 1.6, 0.2, groundType.createBlockData());
        w.spawnParticle(Particle.LAVA, at, 14, 1.4, 0.2, 1.4, 0);
        w.spawnParticle(Particle.EXPLOSION, at, 3, 1, 0.2, 1, 0);
        Fx.ring(w, at.clone().add(0, 0.2, 0), 4, 50, 0, Particle.FLAME);
        // crater: a big scorch mark, a shockwave and a ring of thrown-up earth
        Location surface = ground.getLocation().add(0.5, 1.03, 0.5);
        ItemDisplay crater = Fx.flat(surface, "scorch", 6f, 0.3f);
        Fx.animate(crater, 14, Fx.flatShape(0.01f, 0.3f), 50);
        Fx.removeLater(crater, 66);
        Fx.wave(surface, "ring_orange", 0.5f, 10f, 9);
        Fx.flare(at.clone().add(0, 0.8, 0), "flare_gold", 4f, 10);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            Location p = surface.clone().add(Math.cos(a) * 2.2, 0.1, Math.sin(a) * 2.2);
            w.spawnParticle(Particle.BLOCK, p, 10, 0.25, 0.4, 0.25, 0.3, groundType.createBlockData());
        }
        w.playSound(at, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        for (LivingEntity e : Targets.enemies(player, at, 4)) {
            hit(player, e, dmg(11, slot));
            Targets.knockAway(at, e, 0.6, 0.8);
            e.setFireTicks(Math.max(e.getFireTicks(), 80));
        }
    }
}
