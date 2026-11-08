package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.util.Targets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Chaos pulse: a cube of coloured fragments bursts apart around each victim and their hotbar is shuffled. */
public class EntropyAbility extends CosmicAbility {
    private static final Material[] SHARDS = {Material.RED_CONCRETE, Material.YELLOW_CONCRETE, Material.CYAN_CONCRETE,
            Material.WHITE_CONCRETE, Material.MAGENTA_CONCRETE};

    public EntropyAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.ENTROPY); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location c = player.getLocation().add(0, 1, 0);
        World w = c.getWorld();
        double radius = 6;
        w.playSound(c, Sound.ENTITY_SHULKER_BULLET_HIT, 1.4f, 0.6f);
        w.playSound(c, Sound.BLOCK_GLASS_BREAK, 1f, 1.8f);
        Fx.wave(player.getLocation(), "ring_red", 0.5f, (float) radius * 2.2f, 8);
        Fx.flare(c, "flare_white", 2.5f, 8);
        // jittery static shell expanding out
        every(6, t -> {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double rr = radius * (t + 1) / 6.0;
            for (int i = 0; i < 30; i++) {
                double a = r.nextDouble(Math.PI * 2), y = r.nextDouble(-1, 1.2);
                int col = new int[]{0xef233c, 0xffb703, 0x00b4d8, 0xffffff}[r.nextInt(4)];
                w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * rr, c.getY() + y, c.getZ() + Math.sin(a) * rr,
                        1, 0, 0, 0, 0, Fx.dust(col, 0.9f));
            }
            return true;
        });
        for (LivingEntity e : Targets.enemies(player, c, radius)) {
            hit(player, e, dmg(5, slot));
            Fx.flare(e.getLocation().add(0, 1.1, 0), "flare_white", 1.8f, 8);
            glitch(e.getLocation().add(0, 1, 0));
            if (e instanceof Player victim) {
                ItemStack held = victim.getInventory().getItemInMainHand();
                if (!held.getType().isAir()) {
                    w.spawnParticle(Particle.ITEM, victim.getLocation().add(0, 1.2, 0), 16, 0.2, 0.2, 0.2, 0.1, held);
                }
                status().scrambleHotbar(victim, plugin.getConfig().getInt("entropy.scramble-seconds", 5) * 20);
                if (bonus(slot)) stripOffhand(victim);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false, true));
                victim.sendActionBar(Component.text("Your hotbar is scrambled for "
                        + plugin.getConfig().getInt("entropy.scramble-seconds", 5) + " seconds!", TextColor.color(0xef233c)));
                victim.playSound(victim.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.6f);
            } else {
                e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, true, true));
            }
        }
        return true;
    }

    /** Total Collapse: the offhand item is moved into the main inventory (or swapped into the hotbar if full). */
    private static void stripOffhand(Player victim) {
        PlayerInventory inv = victim.getInventory();
        ItemStack off = inv.getItemInOffHand();
        if (off.getType().isAir()) return;
        inv.setItemInOffHand(null);
        int free = -1;
        for (int i = 9; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) { free = i; break; }
        }
        if (free == -1) {
            int slot = ThreadLocalRandom.current().nextInt(9);
            ItemStack swap = inv.getItem(slot);
            inv.setItem(slot, off);
            inv.setItemInOffHand(swap);
        } else {
            inv.setItem(free, off);
        }
    }

    /** A burst of RGB-split static around the victim. */
    private void glitch(Location c) {
        World w = c.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[] cols = {0xef233c, 0x00f5d4, 0x3a86ff, 0xffffff};
        every(6, t -> {
            for (int i = 0; i < 14; i++) {
                double x = r.nextDouble(-0.7, 0.7), y = r.nextDouble(-0.9, 0.9);
                for (int k = 0; k < 3; k++) {   // short horizontal "scanline" streaks
                    w.spawnParticle(Particle.DUST, c.getX() + x + k * 0.08, c.getY() + y, c.getZ() + r.nextDouble(-0.4, 0.4),
                            1, 0, 0, 0, 0, Fx.dust(cols[(i + k) % cols.length], 0.7f));
                }
            }
            return true;
        });
    }
}
