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

/** Frost Leviathan drop: the leviathan's icy jaws burst out of the ground in front of you and snap shut. */
public class GlacialMawAbility extends CosmicAbility {
    public GlacialMawAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.GLACIAL_MAW); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Vector dir = Targets.flatDirection(player);
        Location c = player.getLocation().add(dir.clone().multiply(5));
        World w = c.getWorld();
        c.setY(w.getHighestBlockYAt(c.getBlockX(), c.getBlockZ()) + 1);
        c.setYaw(0); c.setPitch(0);
        float yaw = (float) Math.atan2(-dir.getX(), dir.getZ());
        w.playSound(c, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
        w.playSound(c, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.6f);
        w.spawnParticle(Particle.SNOWFLAKE, c, 60, 2, 0.3, 2, 0.1);
        // two rows of ice fangs rise in a ring, then close in like jaws
        List<BlockDisplay> fangs = new ArrayList<>();
        List<double[]> angles = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12;
            Location base = c.clone().add(Math.cos(a) * 3.2, -0.5, Math.sin(a) * 3.2);
            BlockDisplay f = Fx.block(base, i % 2 == 0 ? Material.PACKED_ICE : Material.BLUE_ICE, new Vector3f(0.5f, 0.01f, 0.5f), new Quaternionf(), true);
            f.setTeleportDuration(2);
            Fx.animate(f, 4, Fx.centred(0.55f, 3.2f, 0.55f, new Quaternionf().rotateY(yaw)), 1);
            fangs.add(f);
            angles.add(new double[]{a});
        }
        boolean deep = bonus(slot);
        later(10, () -> {
            w.playSound(c, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.8f, 0.5f);
            w.playSound(c, Sound.BLOCK_POWDER_SNOW_BREAK, 1.5f, 0.6f);
            for (int i = 0; i < fangs.size(); i++) {
                double a = angles.get(i)[0];
                BlockDisplay f = fangs.get(i);
                if (!f.isValid()) continue;
                f.teleport(c.clone().add(Math.cos(a) * 0.6, -0.5, Math.sin(a) * 0.6));
                Fx.animate(f, 3, new Transformation(new Vector3f(-0.27f, 0, -0.27f),
                        new Quaternionf().rotateY((float) -a).rotateZ(0.5f), new Vector3f(0.55f, 3.2f, 0.55f), new Quaternionf()), 0);
            }
            w.spawnParticle(Particle.ITEM_SNOWBALL, c.clone().add(0, 1, 0), 60, 1.2, 1, 1.2, 0.2);
            for (LivingEntity e : Targets.enemies(player, c, 3.8)) {
                hit(player, e, 7);
                status().freeze(e, deep ? 60 : 35, player, 2);
            }
        });
        for (BlockDisplay f : fangs) {
            Fx.animate(f, 8, Fx.centred(0.01f, 0.01f, 0.01f, new Quaternionf()), 40);
            Fx.removeLater(f, 50);
        }
        return true;
    }
}
