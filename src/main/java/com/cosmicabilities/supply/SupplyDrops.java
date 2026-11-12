package com.cosmicabilities.supply;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.fx.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Supply drops: a crate floats down from the sky on a parachute, lands as a chest full of loot (supply-drops.loot)
 * and is announced to everyone. Admins call one at their position or any coordinates:
 * /supplydrop (random spot) [here | loot | <x> <y> <z> | random <radius>] or from /adminpanel. Loot is edited in a chest GUI.
 */
public class SupplyDrops implements CommandExecutor, org.bukkit.event.Listener {
    private final CosmicAbilitiesPlugin plugin;
    private final java.io.File lootFile;
    private final List<ItemStack> loot = new ArrayList<>();

    /** The loot editor: a plain chest the admin fills with whatever the drops should contain. */
    public static final class LootHolder implements org.bukkit.inventory.InventoryHolder {
        Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    public SupplyDrops(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.lootFile = new java.io.File(plugin.getDataFolder(), "supply-loot.yml");
        loadLoot();
    }

    private void loadLoot() {
        loot.clear();
        org.bukkit.configuration.file.YamlConfiguration yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(lootFile);
        for (String b64 : yml.getStringList("items")) {
            try {
                loot.add(ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(b64)));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping a broken supply drop item");
            }
        }
    }

    private void saveLoot() {
        org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (ItemStack it : loot) out.add(java.util.Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        yml.set("items", out);
        try { yml.save(lootFile); } catch (java.io.IOException e) { plugin.getLogger().warning("Could not save supply-loot.yml: " + e.getMessage()); }
    }

    public int lootCount() { return loot.size(); }

    private boolean isToken(ItemStack it) {
        return plugin.items().isToken(it);
    }

    public void openEditor(Player admin) {
        LootHolder h = new LootHolder();
        Inventory inv = Bukkit.createInventory(h, 54, plugin.mm("<dark_green>Supply drop loot <dark_gray>- close to save"));
        h.inventory = inv;
        for (int i = 0; i < loot.size() && i < 54; i++) inv.setItem(i, loot.get(i).clone());
        admin.openInventory(inv);
        admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>Put in everything a supply drop can contain. Each stack has a <white>"
                + Math.round(plugin.getConfig().getDouble("supply-drops.item-chance", 0.7) * 100) + "% <gray>chance to be in each drop. Close to save."));
    }

    @org.bukkit.event.EventHandler
    public void onEditorClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof LootHolder)) return;
        if (!(event.getPlayer() instanceof Player p) || !BanPistol.isAdmin(p)) return;
        loot.clear();
        for (ItemStack it : event.getInventory().getContents()) if (it != null && !it.getType().isAir()) loot.add(it.clone());
        saveLoot();
        p.sendMessage(plugin.mm(plugin.prefix() + "<green>Saved " + loot.size() + " supply drop items."));
    }

    /** A random spot around the world spawn (supply-drops.random-min-radius .. random-radius), never in water. */
    public Location randomLocation(World w) {
        double max = plugin.getConfig().getDouble("supply-drops.random-radius", 800);
        double min = Math.min(max, plugin.getConfig().getDouble("supply-drops.random-min-radius", 100));
        Location spawn = w.getSpawnLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location best = spawn;
        for (int i = 0; i < 12; i++) {
            double a = r.nextDouble(Math.PI * 2), d = min + r.nextDouble() * Math.max(1, max - min);
            Location at = spawn.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            Block top = w.getHighestBlockAt(at);
            best = at;
            if (!top.isLiquid() && top.getType() != Material.ICE) break;
        }
        return best;
    }

    public void callRandom(World w, CommandSender by) {
        call(randomLocation(w), by);
    }

    public void call(Location target, CommandSender by) {
        World w = target.getWorld();
        Block top = w.getHighestBlockAt(target.getBlockX(), target.getBlockZ());
        Location land = top.getLocation().add(0, 1, 0);
        if (land.getBlock().isLiquid() || !land.getBlock().isEmpty()) land.add(0, 1, 0);
        Location start = land.clone().add(0.5, 55, 0.5);
        Bukkit.broadcast(plugin.mm(plugin.prefix() + "<green><bold>SUPPLY DROP</bold> <gray>incoming at <white>" + land.getBlockX() + ", "
                + land.getBlockY() + ", " + land.getBlockZ() + " <gray>(" + w.getName() + ")! First one there gets the loot."));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_1, 0.8f, 1.2f);

        List<Display> rig = new ArrayList<>();
        // crate
        rig.add(part(start, Material.BARREL, new Vector3f(-0.5f, 0, -0.5f), new Vector3f(1, 1, 1)));
        rig.add(part(start, Material.LIME_CONCRETE, new Vector3f(-0.52f, 0.42f, -0.52f), new Vector3f(1.04f, 0.16f, 1.04f)));
        // parachute canopy (striped) and cords
        rig.add(part(start, Material.WHITE_WOOL, new Vector3f(-1.6f, 3.2f, -1.6f), new Vector3f(3.2f, 0.35f, 3.2f)));
        rig.add(part(start, Material.RED_WOOL, new Vector3f(-1.1f, 3.5f, -1.1f), new Vector3f(2.2f, 0.3f, 2.2f)));
        rig.add(part(start, Material.WHITE_WOOL, new Vector3f(-0.6f, 3.75f, -0.6f), new Vector3f(1.2f, 0.25f, 1.2f)));
        for (float[] c : new float[][]{{-1.4f, -1.4f}, {1.3f, -1.4f}, {-1.4f, 1.3f}, {1.3f, 1.3f}}) {
            rig.add(part(start, Material.WHITE_CONCRETE, new Vector3f(c[0] * 0.75f, 1f, c[1] * 0.75f), new Vector3f(0.1f, 2.3f, 0.1f)));
        }
        new BukkitRunnable() {
            double y = start.getY();
            int t = 0;
            @Override
            public void run() {
                t++;
                y -= 0.35;
                Location at = start.clone();
                at.setY(Math.max(land.getY(), y));
                for (Display d : rig) if (d.isValid()) d.teleport(at);
                if (t % 2 == 0) w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, at.clone().add(0, 0.2, 0), 1, 0.1, 0, 0.1, 0.01);
                if (t % 4 == 0) w.spawnParticle(Particle.DUST, at, 4, 0.4, 0.3, 0.4, 0, new Particle.DustOptions(Color.LIME, 1.4f));
                if (y <= land.getY()) {
                    cancel();
                    rig.forEach(d -> { if (d.isValid()) d.remove(); });
                    landed(land);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        if (by != null) by.sendMessage(plugin.mm(plugin.prefix() + "<gray>Supply drop called."));
    }

    private BlockDisplay part(Location at, Material m, Vector3f offset, Vector3f scale) {
        return at.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setPersistent(false);
            d.addScoreboardTag(Fx.TAG);
            d.addScoreboardTag(Fx.KEEP);
            d.setTeleportDuration(1);
            d.setViewRange(4f);
            d.setTransformation(new Transformation(offset, new Quaternionf(), scale, new Quaternionf()));
        });
    }

    private void landed(Location land) {
        World w = land.getWorld();
        Block b = land.getBlock();
        b.setType(Material.CHEST);
        if (b.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            List<ItemStack> loot = rollLoot();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (ItemStack it : loot) {
                int slot = r.nextInt(inv.getSize());
                for (int i = 0; i < inv.getSize() && inv.getItem(slot) != null; i++) slot = (slot + 1) % inv.getSize();
                inv.setItem(slot, it);
            }
        }
        w.playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.4f);
        w.spawnParticle(Particle.CLOUD, land.clone().add(0.5, 0.3, 0.5), 40, 1, 0.2, 1, 0.05);
        Firework fw = w.spawn(land.clone().add(0.5, 1, 0.5), Firework.class, f -> {
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.LIME, Color.WHITE).withFlicker().build());
            m.setPower(1);
            f.setFireworkMeta(m);
        });
        fw.detonate();
        // a beam of light marks it for a while
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t++ > 60 || b.getType() != Material.CHEST) { cancel(); return; }
                for (int y = 1; y < 30; y += 2) w.spawnParticle(Particle.END_ROD, land.clone().add(0.5, y, 0.5), 1, 0.05, 0.3, 0.05, 0);
            }
        }.runTaskTimer(plugin, 10L, 10L);
        int despawn = plugin.getConfig().getInt("supply-drops.despawn-minutes", 10);
        if (despawn > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (b.getType() == Material.CHEST) {
                    if (b.getState() instanceof Chest c) c.getBlockInventory().clear();
                    b.setType(Material.AIR);
                    w.spawnParticle(Particle.CLOUD, land.clone().add(0.5, 0.5, 0.5), 20, 0.4, 0.4, 0.4, 0.02);
                }
            }, despawn * 1200L);
        }
    }

    private List<ItemStack> rollLoot() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (!loot.isEmpty()) {
            double chance = plugin.getConfig().getDouble("supply-drops.item-chance", 0.7);
            List<ItemStack> out = new ArrayList<>();
            for (ItemStack it : loot) if (!isToken(it) && r.nextDouble() < chance) out.add(it.clone());
            out.removeIf(this::isToken);
            if (out.isEmpty()) for (ItemStack it : loot) if (!isToken(it)) { out.add(it.clone()); break; }
            return out;
        }
        return rollConfigLoot();
    }

    /** supply-drops.loot entries: "ITEM:min-max:chance", e.g. "DIAMOND:2-6:0.8" or "cosmic:upgrade_token:1:0.15" */
    private List<ItemStack> rollConfigLoot() {
        List<ItemStack> out = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (String line : plugin.getConfig().getStringList("supply-drops.loot")) {
            try {
                String[] p = line.split(":");
                boolean cosmic = p[0].equalsIgnoreCase("cosmic");
                String id = cosmic ? p[1] : p[0];
                String range = cosmic ? p[2] : p[1];
                double chance = Double.parseDouble(cosmic ? p[3] : p[2]);
                if (r.nextDouble() > chance) continue;
                String[] mm = range.split("-");
                int min = Integer.parseInt(mm[0]), max = mm.length > 1 ? Integer.parseInt(mm[1]) : min;
                int n = r.nextInt(min, max + 1);
                ItemStack it;
                if (cosmic) {
                    it = switch (id.toLowerCase(Locale.ROOT)) {
                        case "cosmic_shard" -> plugin.items().shard(n);
                        case "upgrade_token" -> null;   // upgrade tokens never come from supply drops
                        case "afk_token" -> plugin.items().afkToken(n);
                        case "reroll" -> { ItemStack x = plugin.orbs().reroll(); x.setAmount(n); yield x; }
                        default -> null;
                    };
                } else {
                    Material m = Material.matchMaterial(id);
                    it = m == null ? null : new ItemStack(m, Math.min(n, m.getMaxStackSize()));
                }
                if (it != null) out.add(it);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Bad supply-drops.loot line: " + line);
            }
        }
        return out;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!BanPistol.isAdmin(sender)) return true;
        Player p = sender instanceof Player pl ? pl : null;
        try {
            if (args.length >= 3) {
                World w = p != null ? p.getWorld() : Bukkit.getWorlds().get(0);
                call(new Location(w, Double.parseDouble(args[0]), Double.parseDouble(args[1]), Double.parseDouble(args[2])), sender);
            } else if (args.length >= 1 && args[0].equalsIgnoreCase("random") && p != null) {
                double rad = args.length > 1 ? Double.parseDouble(args[1]) : 200;
                double a = Math.random() * Math.PI * 2, d = Math.random() * rad;
                call(p.getLocation().add(Math.cos(a) * d, 0, Math.sin(a) * d), sender);
            } else if (args.length >= 1 && args[0].equalsIgnoreCase("here") && p != null) {
                call(p.getLocation(), sender);
            } else if (args.length >= 1 && args[0].equalsIgnoreCase("loot") && p != null) {
                openEditor(p);
            } else {
                callRandom(p != null ? p.getWorld() : Bukkit.getWorlds().get(0), sender);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.mm(plugin.prefix() + "<red>/supplydrop [here | loot | <x> <y> <z> | random <radius>]"));
        }
        return true;
    }
}
