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
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Comet dash: low and fast, damaging everything you pass through, with a burning tail and wind streaks. */
public class MeteorAbility extends CosmicAbility {
    public MeteorAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.METEOR); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Vector dir = Targets.flatDirection(player);
        World w = player.getWorld();
        Set<UUID> hitSet = new HashSet<>();
        double damage = dmg(7, slot);
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

    /** Comet Breaker: crater slam at the end of the dash. */
    private void slam(Player player, Location at, AbilitySlot slot) {
        World w = at.getWorld();
        Block ground = at.clone().subtract(0, 0.5, 0).getBlock();
        Material groundType = ground.getType().isSolid() ? ground.getType() : Material.STONE;
        w.spawnParticle(Particle.BLOCK, at, 80, 1.6, 0.3, 1.6, 0.2, groundType.createBlockData());
        w.spawnParticle(Particle.LAVA, at, 14, 1.4, 0.2, 1.4, 0);
        w.spawnParticle(Particle.EXPLOSION, at, 3, 1, 0.2, 1, 0);
        Fx.ring(w, at.clone().add(0, 0.2, 0), 4, 50, 0, Particle.FLAME);
        Fx.ring(w, at.clone().add(0, 0.25, 0), 2.6, 36, 0.3, Fx.dust(0x06d6a0, 1.6f));
        w.playSound(at, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        for (LivingEntity e : Targets.enemies(player, at, 4)) {
            hit(player, e, dmg(8, slot));
            Targets.knockAway(at, e, 0.6, 0.8);
            e.setFireTicks(Math.max(e.getFireTicks(), 80));
        }
    }
}
