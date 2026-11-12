package com.cosmicabilities.command;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /spawn (everyone): teleport to the server spawn after a short warm-up (stand still, not in combat).
 * /setspawn (admins): sets it where you stand. /resetcooldown (admins): resets your own ability cooldowns.
 */
public class SpawnCommands implements CommandExecutor {
    private final CosmicAbilitiesPlugin plugin;
    private final Map<UUID, BukkitRunnable> pending = new HashMap<>();

    public SpawnCommands(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public Location spawn() {
        String w = plugin.getConfig().getString("spawn.world");
        World world = w == null ? null : Bukkit.getWorld(w);
        if (world == null) return Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 0, 0.5);
        return new Location(world, plugin.getConfig().getDouble("spawn.x"), plugin.getConfig().getDouble("spawn.y"),
                plugin.getConfig().getDouble("spawn.z"), (float) plugin.getConfig().getDouble("spawn.yaw"), (float) plugin.getConfig().getDouble("spawn.pitch"));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return true;
        switch (command.getName().toLowerCase()) {
            case "setspawn" -> {
                if (!BanPistol.isAdmin(p)) { p.sendMessage(plugin.mm(plugin.prefix() + "<red>Only admins can set the spawn.")); return true; }
                Location l = p.getLocation();
                plugin.getConfig().set("spawn.world", l.getWorld().getName());
                plugin.getConfig().set("spawn.x", l.getX());
                plugin.getConfig().set("spawn.y", l.getY());
                plugin.getConfig().set("spawn.z", l.getZ());
                plugin.getConfig().set("spawn.yaw", (double) l.getYaw());
                plugin.getConfig().set("spawn.pitch", (double) l.getPitch());
                plugin.saveConfig();
                l.getWorld().setSpawnLocation(l);
                p.sendMessage(plugin.mm(plugin.prefix() + "<green>Spawn set to where you're standing. <gray>Players get here with <yellow>/spawn<gray>."));
            }
            case "resetcooldown" -> {
                if (!BanPistol.isAdmin(p)) { p.sendMessage(plugin.mm(plugin.prefix() + "<red>Only admins can use that.")); return true; }
                plugin.engine().resetCooldown(p.getUniqueId());
                p.sendMessage(plugin.mm(plugin.prefix() + "<green>Your ability cooldowns were reset."));
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);
            }
            default -> teleport(p);
        }
        return true;
    }

    private void teleport(Player p) {
        if (pending.containsKey(p.getUniqueId())) return;
        if (plugin.combat().inCombat(p, plugin.getConfig().getInt("spawn.combat-seconds", 10)) && !BanPistol.isAdmin(p)) {
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>You can't /spawn while in combat."));
            return;
        }
        int warmup = BanPistol.isAdmin(p) ? 0 : Math.max(0, plugin.getConfig().getInt("spawn.warmup-seconds", 3));
        Location start = p.getLocation().clone();
        BukkitRunnable task = new BukkitRunnable() {
            int left = warmup;
            @Override
            public void run() {
                if (!p.isOnline()) { pending.remove(p.getUniqueId()); cancel(); return; }
                if (p.getLocation().getWorld() != start.getWorld() || p.getLocation().distanceSquared(start) > 0.6) {
                    p.sendActionBar(plugin.mm("<red>Teleport cancelled - you moved."));
                    pending.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                if (left <= 0) {
                    pending.remove(p.getUniqueId());
                    cancel();
                    Location to = spawn();
                    p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);
                    p.teleport(to);
                    p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                    to.getWorld().spawnParticle(Particle.END_ROD, to.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.05);
                    p.sendActionBar(plugin.mm("<green>Welcome to spawn!"));
                    return;
                }
                p.sendActionBar(plugin.mm("<aqua>Teleporting to spawn in <white>" + left + "<aqua>... don't move"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.5f);
                left--;
            }
        };
        pending.put(p.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 20L);
    }
}
