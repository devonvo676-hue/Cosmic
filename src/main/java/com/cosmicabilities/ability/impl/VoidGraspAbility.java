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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Void Emperor drop: void hands burst out of the ground under nearby enemies and drag them toward you. */
public class VoidGraspAbility extends CosmicAbility {
    public VoidGraspAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.VOID_GRASP); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        World w = player.getWorld();
        List<LivingEntity> targets = Targets.enemies(player, player.getLocation(), 12);
        if (targets.isEmpty()) {
            plugin.engine().notify(player, plugin.mm("<gray>No enemies close enough to grab."));
            return false;
        }
        w.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.7f);
        boolean hold = bonus(slot);
        int n = 0;
        for (LivingEntity e : targets) {
            if (n++ >= 6) break;
            Location ground = e.getLocation().clone();
            ground.setY(Math.floor(ground.getY()));
            List<BlockDisplay> claw = new ArrayList<>();
            for (int f = 0; f < 5; f++) {
                double a = f * Math.PI * 2 / 5;
                Location base = ground.clone().add(Math.cos(a) * 0.7, 0, Math.sin(a) * 0.7);
                BlockDisplay finger = Fx.block(base, f % 2 == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN,
                        new Vector3f(0.22f, 0.01f, 0.22f), new Quaternionf().rotateZ((float) (Math.cos(a) * 0.4)).rotateX((float) (-Math.sin(a) * 0.4)), true);
                Fx.animate(finger, 5, new Transformation(new Vector3f(), new Quaternionf().rotateZ((float) (Math.cos(a) * -0.35)).rotateX((float) (Math.sin(a) * 0.35)),
                        new Vector3f(0.22f, 2.2f, 0.22f), new Quaternionf()), 1);
                claw.add(finger);
            }
            w.spawnParticle(Particle.REVERSE_PORTAL, ground.clone().add(0, 0.5, 0), 40, 0.6, 0.3, 0.6, 0.05);
            w.spawnParticle(Particle.DUST, ground.clone().add(0, 0.1, 0), 20, 0.8, 0.05, 0.8, 0, Fx.dust(type.rgb(), 1.6f));
            w.playSound(ground, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.7f, 0.6f);
            hit(player, e, 5);
            e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true));
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, hold ? 30 : 15, 4, false, true, true));
            later(hold ? 20 : 8, () -> {
                if (!e.isValid() || !player.isOnline()) return;
                Vector pull = player.getLocation().toVector().subtract(e.getLocation().toVector());
                double dist = pull.length();
                if (dist > 0.5) e.setVelocity(pull.normalize().multiply(Math.min(1.6, 0.3 + dist * 0.12)).setY(0.35));
                w.playSound(e.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
            });
            for (BlockDisplay b : claw) {
                Fx.animate(b, 6, new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.22f, 0.01f, 0.22f), new Quaternionf()), hold ? 22 : 10);
                Fx.removeLater(b, hold ? 30 : 18);
            }
        }
        return true;
    }
}
