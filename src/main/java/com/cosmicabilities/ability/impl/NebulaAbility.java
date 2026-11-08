package com.cosmicabilities.ability.impl;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.CosmicAbility;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.util.Targets;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NebulaAbility extends CosmicAbility {
    private static final Color[] COLORS = {
            Color.fromRGB(247, 37, 133), Color.fromRGB(114, 9, 183), Color.fromRGB(76, 201, 240), Color.fromRGB(181, 23, 158)
    };

    public NebulaAbility(CosmicAbilitiesPlugin plugin) { super(plugin); }

    @Override
    public void cast(Player player, AbilitySlot data) {
        int lvl = data.level();
        double radius = scale(4, 0.3, lvl);
        int duration = ticks(scale(4, 0.3, lvl));
        int speedAmp = data.isPathA() ? 1 : 0;
        boolean shroud = data.isPathB();
        boolean veil = data.evolved();

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
        world.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.6f);

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= duration || !player.isOnline() || player.isDead()) { cancel(); return; }
                Location c = player.getLocation().add(0, 1, 0);
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 10; i++) {
                    double a = rnd.nextDouble(Math.PI * 2);
                    double r = Math.sqrt(rnd.nextDouble()) * radius;
                    Location p = c.clone().add(Math.cos(a) * r, rnd.nextDouble(-0.8, 1.2), Math.sin(a) * r);
                    dust(world, p, COLORS[rnd.nextInt(COLORS.length)], 2.0f, 1, 0.1);
                }
                if (t % 10 == 0) {
                    List<Player> allies = Targets.allies(player, c, radius);
                    for (Player ally : allies) {
                        if (!ally.equals(player) && Targets.isEnemy(player, ally) && !sameTeam(player, ally)) {
                            // Allies = caster + players on the same scoreboard team (or everyone if PvP hits are off).
                            continue;
                        }
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, speedAmp, true, true));
                        if (veil) ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, true, true));
                    }
                    for (LivingEntity e : Targets.enemies(player, c, radius)) {
                        if (shroud) {
                            e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 0, true, false));
                            e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 0, true, false));
                        }
                        if (veil && e instanceof Mob mob && mob.getTarget() instanceof Player targeted
                                && allies.contains(targeted)) {
                            mob.setTarget(null);
                        }
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static boolean sameTeam(Player a, Player b) {
        var team = a.getScoreboard().getEntryTeam(a.getName());
        return team != null && !com.cosmicabilities.admin.RankTags.isTagTeam(team) && team.hasEntry(b.getName());
    }
}
