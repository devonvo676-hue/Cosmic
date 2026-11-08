package com.cosmicabilities.ability;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.status.StatusManager;
import com.cosmicabilities.util.Targets;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.IntPredicate;

/**
 * Base class for every ability. cast() returns false if the ability couldn't fire (no cooldown is used then).
 * The upgrade only shortens the cooldown (handled by the engine).
 */
public abstract class CosmicAbility {
    protected final CosmicAbilitiesPlugin plugin;
    protected final AbilityType type;

    protected CosmicAbility(CosmicAbilitiesPlugin plugin, AbilityType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public abstract boolean cast(Player player, AbilitySlot slot);

    protected double dmg(double base, AbilitySlot slot) {
        return base;
    }

    /** Each ability's signature bonus mechanic (Event Horizon, Quasar, ...). On by default; config can turn them off. */
    protected boolean bonus(AbilitySlot slot) {
        return plugin.getConfig().getBoolean("abilities.bonus-mechanics", true);
    }

    /** Deals this ability's damage, scaled by its own ability-settings.<id>.damage-multiplier. */
    protected double hit(Player caster, LivingEntity target, double amount) {
        double mult = plugin.getConfig().getDouble("ability-settings." + type.id() + ".damage-multiplier", 1.0);
        return Targets.damage(caster, target, amount * mult, type.rgb());
    }

    protected StatusManager status() {
        return plugin.status();
    }

    /** Runs body(tick) every tick until it returns false or maxTicks is reached. */
    protected void every(int maxTicks, IntPredicate body) {
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t > maxTicks || !body.test(t)) {
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    protected void later(long ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks);
    }
}
