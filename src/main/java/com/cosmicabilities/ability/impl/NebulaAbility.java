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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** A drifting, layered gas cloud with twinkling stars. Buffs allies inside, weakens and reveals enemies. */
public class NebulaAbility extends CosmicAbility {
    private static final int[] GAS = {0xf72585, 0xb5179e, 0x7209b7, 0x4cc9f0, 0xffafcc};

    public NebulaAbility(CosmicAbilitiesPlugin plugin) { super(plugin, AbilityType.NEBULA); }

    @Override
    public boolean cast(Player player, AbilitySlot slot) {
        Location center = player.getLocation();
        World w = center.getWorld();
        double radius = 6;
        int duration = 120;
        boolean veil = bonus(slot);
        w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
        String[] clouds = {"nebula_pink", "nebula_purple", "nebula_blue", "nebula_pink", "nebula_blue"};
        java.util.List<org.bukkit.entity.ItemDisplay> sheets = new java.util.ArrayList<>();
        for (int i = 0; i < clouds.length; i++) {
            double a0 = i * Math.PI * 2 / clouds.length;
            Location at = center.clone().add(Math.cos(a0) * radius * 0.45, 1.0 + (i % 3) * 0.6, Math.sin(a0) * radius * 0.45);
            org.bukkit.entity.ItemDisplay cloud = Fx.sprite(at, clouds[i], 0.1f, org.bukkit.entity.Display.Billboard.CENTER);
            float size = (float) radius * (0.9f + (i % 2) * 0.35f);
            Fx.animate(cloud, 12, Fx.sized(size, size, i), 1);
            Fx.removeLater(cloud, duration + 14);
            sheets.add(cloud);
        }
        Fx.flare(center.clone().add(0, 1.2, 0), "flare_white", 3f, 10);
        w.playSound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.4f);

        every(duration, t -> {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            if (t % 10 == 0 && t > 12) {
                // clouds slowly turn and breathe
                for (int i = 0; i < sheets.size(); i++) {
                    float size = (float) radius * (0.9f + (i % 2) * 0.35f) * (t % 20 == 0 ? 1.05f : 0.97f);
                    Fx.animate(sheets.get(i), 10, Fx.sized(size, size, i + t * 0.01f * (i % 2 == 0 ? 1 : -1)), 1);
                }
            }
            if (t >= duration) {
                for (org.bukkit.entity.ItemDisplay sheet : sheets) {
                    Fx.animate(sheet, 8, Fx.sized(0.01f, 0.01f, 0), 1);
                    Fx.removeLater(sheet, 10);
                }
            }
            if (t % 2 == 0) {
                // gas: slowly rotating spiral bands at different heights, big soft particles
                for (int i = 0; i < 16; i++) {
                    double band = (i % 4) / 3.0;
                    double a = t * 0.025 * (i % 2 == 0 ? 1 : -1) + i * 0.8 + r.nextDouble(0.3);
                    double rr = radius * (0.25 + 0.75 * Math.sqrt(r.nextDouble()));
                    double y = 0.2 + band * 2.2 + r.nextDouble(-0.2, 0.2);
                    w.spawnParticle(Particle.DUST, center.getX() + Math.cos(a) * rr, center.getY() + y, center.getZ() + Math.sin(a) * rr,
                            1, 0.15, 0.1, 0.15, 0, Fx.dust(GAS[(i + t / 10) % GAS.length], (float) r.nextDouble(2.2, 3.4)));
                }
            }
            if (t % 3 == 0) {  // stars that twinkle in and out
                double a = r.nextDouble(Math.PI * 2), rr = radius * Math.sqrt(r.nextDouble());
                w.spawnParticle(Particle.END_ROD, center.clone().add(Math.cos(a) * rr, r.nextDouble(0.3, 2.8), Math.sin(a) * rr), 1, 0, 0, 0, 0);
            }
            if (t % 20 == 0) {
                Fx.ring(w, center.clone().add(0, 0.1, 0), radius, 60, t, Fx.dust(0xf72585, 1.0f));
                if (r.nextBoolean()) w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, (float) r.nextDouble(0.6, 1.4));
            }
            if (t % 10 == 0 && player.isOnline()) {
                List<Player> allies = Targets.allies(player, center, radius);
                for (Player ally : allies) {
                    ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, false, true, true));
                    ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 0, false, true, true));
                    if (veil) ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, false, true, true));
                }
                for (LivingEntity e : Targets.enemies(player, center, radius)) {
                    e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 0, false, false, false));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 0, false, true, true));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, true, true));
                    if (t % 20 == 0) hit(player, e, dmg(1.5, slot));
                    if (veil && e instanceof Mob mob && mob.getTarget() instanceof Player tp && allies.contains(tp)) mob.setTarget(null);
                }
            }
            return true;
        });
        return true;
    }
}
