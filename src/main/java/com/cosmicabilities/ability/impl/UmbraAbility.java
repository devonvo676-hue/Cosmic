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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Burrow: you sink into the ground and vanish from every player (only a tremor trail of dust gives you away),
 * then erupt beneath enemies in a fountain of earth.
 */
public class UmbraAbility extends CosmicAbility {
    public UmbraAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.UMBRA); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Block ground = player.getLocation().subtract(0, 0.2, 0).getBlock();
        if (!ground.getType().isSolid()) {
            plugin.engine().notify(player, plugin.mm("<red>You need solid ground to burrow into."));
            return false;
        }
        World w = player.getWorld();
        Location at = player.getLocation();
        BlockData dirt = ground.getBlockData();
        // sinking: earth swallows the player
        w.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.4, 0), 60, 0.4, 0.4, 0.4, 0, dirt);
        w.spawnParticle(Particle.LARGE_SMOKE, at, 10, 0.4, 0.1, 0.4, 0.02);
        w.playSound(at, Sound.ENTITY_WARDEN_DIG, 1f, 1.3f);
        w.playSound(at, Sound.BLOCK_GRAVEL_BREAK, 1.2f, 0.6f);

        Location hole = ground.getLocation().add(0.5, 1.005, 0.5);
        Fx.decal(hole, Material.BLACK_CONCRETE, 1.1f, 0.3f, 50);
        Fx.decal(hole, Material.COARSE_DIRT, 1.8f, 1.1f, 45);
        int ticks = bonus(slot) ? 120 : 80;
        status().burrow(player, ticks, () -> erupt(player, slot));
        return true;
    }

    private void erupt(Player player, AbilitySlot slot) {
        Location at = player.getLocation();
        World w = at.getWorld();
        Block ground = at.clone().subtract(0, 0.2, 0).getBlock();
        Material groundType = ground.getType().isSolid() ? ground.getType() : Material.DIRT;
        BlockData data = groundType.createBlockData();
        w.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 120, 1.2, 0.8, 1.2, 0.3, data);
        w.spawnParticle(Particle.EXPLOSION, at, 2, 0.5, 0.2, 0.5, 0);
        w.spawnParticle(Particle.DUST, at.clone().add(0, 0.3, 0), 30, 1.5, 0.2, 1.5, 0, Fx.dust(0xba68ff, 1.4f));
        w.playSound(at, Sound.ENTITY_WARDEN_EMERGE, 0.8f, 1.6f);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
        Location crater = ground.getLocation().add(0.5, 1.005, 0.5);
        Fx.decal(crater, Material.BLACK_CONCRETE, 1.2f, 0f, 60);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Fx.decal(crater.clone().add(Math.cos(a) * 1.3, 0, Math.sin(a) * 1.3), groundType, 0.8f, (float) a, 55);
        }
        // chunks of earth thrown into the air
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            float s = (float) r.nextDouble(0.3, 0.55);
            BlockDisplay chunk = Fx.block(at.clone().add(0, 0.2, 0), groundType, new Vector3f(s, s, s),
                    new Quaternionf().rotateXYZ((float) r.nextDouble(3), (float) r.nextDouble(3), 0), false);
            double a = r.nextDouble(Math.PI * 2);
            Vector3f up = new Vector3f((float) (Math.cos(a) * 1.4), (float) r.nextDouble(1.6, 2.6), (float) (Math.sin(a) * 1.4));
            Fx.animate(chunk, 6, new Transformation(up, new Quaternionf().rotateXYZ(2f, 1f, 0), new Vector3f(s, s, s), new Quaternionf()), 1);
            Fx.animate(chunk, 6, new Transformation(new Vector3f(up.x * 1.6f, -0.2f, up.z * 1.6f),
                    new Quaternionf().rotateXYZ(4f, 2f, 0), new Vector3f(s * 0.6f, s * 0.6f, s * 0.6f), new Quaternionf()), 8);
            Fx.removeLater(chunk, 15);
        }
        double radius = 3.5;
        for (LivingEntity e : Targets.enemies(player, at, radius)) {
            hit(player, e, dmg(bonus(slot) ? 10 : 7, slot));
            e.setVelocity(e.getVelocity().setY(0.85));
            if (bonus(slot)) e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, true, true));
        }
    }
}
