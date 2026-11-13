package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.Owner;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /minimap - v2tou only. A 9 x 5 map of the world drawn from the real terrain (each square shows the block on top
 * of that area), with every online player's head where they are and his waypoints.
 * Click a player's head or a waypoint to teleport there. Click terrain to zoom in on it, right-click terrain to
 * teleport there, shift-click terrain to drop a waypoint. Arrows pan, the magnifiers zoom.
 */
public class Minimap implements Listener, CommandExecutor {
    private static final int COLS = 9, ROWS = 5;
    private static final int[] ZOOMS = {8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192};   // blocks per square

    private enum Kind { TERRAIN, PLAYER, WAYPOINT }
    private record Cell(Kind kind, int x, int z, String ref) {}

    private static final class View implements InventoryHolder {
        Inventory inv;
        String world;
        int cx, cz, zoom;
        boolean list;
        final Map<Integer, Cell> cells = new java.util.HashMap<>();
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final File file;
    private final Map<String, Location> waypoints = new LinkedHashMap<>();
    private final Map<String, Material> terrain = new ConcurrentHashMap<>();   // "world:x:z" -> top block
    private final Map<UUID, View> last = new ConcurrentHashMap<>();
    private final Map<UUID, Location> naming = new ConcurrentHashMap<>();       // waiting for a waypoint name in chat

    public Minimap(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "waypoints.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("waypoints");
        if (sec != null) for (String k : sec.getKeys(false)) {
            Location l = sec.getLocation(k);
            if (l != null) waypoints.put(k, l);
        }
        // the map refreshes while it's open so players move around on it
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof View v && !v.list) draw(p, v);
            }
        }, 40L, 40L);
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Location> e : waypoints.entrySet()) yml.set("waypoints." + e.getKey(), e.getValue());
        try { yml.save(file); } catch (IOException ex) { plugin.getLogger().warning("Couldn't save waypoints.yml"); }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p) || !Owner.isOwner(p)) {
            sender.sendMessage(plugin.mm("<red>Unknown command. Type \"/help\" for help."));
            return true;
        }
        open(p);
        return true;
    }

    public void open(Player p) {
        if (!Owner.isOwner(p)) return;
        View v = last.get(p.getUniqueId());
        if (v == null || v.world == null || Bukkit.getWorld(v.world) == null) {
            v = new View();
            World w = p.getWorld();
            v.world = w.getName();
            // start zoomed out far enough to see the whole world inside the border (if it's a sensible size)
            WorldBorder b = w.getWorldBorder();
            if (b.getSize() <= 120_000) {
                v.cx = b.getCenter().getBlockX();
                v.cz = b.getCenter().getBlockZ();
                v.zoom = 0;
                while (v.zoom < ZOOMS.length - 1 && ZOOMS[v.zoom] * COLS < b.getSize()) v.zoom++;
            } else {
                v.cx = p.getLocation().getBlockX();
                v.cz = p.getLocation().getBlockZ();
                v.zoom = 7;
            }
        }
        View nv = new View();
        nv.world = v.world; nv.cx = v.cx; nv.cz = v.cz; nv.zoom = v.zoom;
        last.put(p.getUniqueId(), nv);
        nv.inv = Bukkit.createInventory(nv, 54, plugin.mm(title(nv)));
        draw(p, nv);
        p.openInventory(nv.inv);
    }

    private String title(View v) {
        return "<dark_aqua><bold>Minimap</bold> <dark_gray>" + v.world + " | " + ZOOMS[v.zoom] + " blocks/square";
    }

    private int originX(View v) { return v.cx - ZOOMS[v.zoom] * COLS / 2; }
    private int originZ(View v) { return v.cz - ZOOMS[v.zoom] * ROWS / 2; }

    private void draw(Player viewer, View v) {
        World w = Bukkit.getWorld(v.world);
        if (w == null) return;
        int size = ZOOMS[v.zoom];
        int ox = originX(v), oz = originZ(v);
        v.cells.clear();
        v.list = false;
        // terrain first
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            int x = ox + c * size + size / 2, z = oz + r * size + size / 2;
            int slot = r * 9 + c;
            v.cells.put(slot, new Cell(Kind.TERRAIN, x, z, null));
            v.inv.setItem(slot, terrainItem(w, x, z, size, viewer));
        }
        // waypoints on top of terrain
        for (Map.Entry<String, Location> e : waypoints.entrySet()) {
            Location l = e.getValue();
            if (l.getWorld() == null || !l.getWorld().equals(w)) continue;
            int slot = slotOf(v, l.getBlockX(), l.getBlockZ());
            if (slot < 0) continue;
            v.cells.put(slot, new Cell(Kind.WAYPOINT, l.getBlockX(), l.getBlockZ(), e.getKey()));
            v.inv.setItem(slot, Icons.button(plugin, Material.RECOVERY_COMPASS, "<gold><bold>" + e.getKey(),
                    "<gray>" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ(), "",
                    "<yellow>Click <gray>teleport here", "<yellow>Shift + right click <gray>delete"));
        }
        // live players on top of everything
        Map<Integer, List<Player>> here = new java.util.HashMap<>();
        for (Player o : w.getPlayers()) {
            int slot = slotOf(v, o.getLocation().getBlockX(), o.getLocation().getBlockZ());
            if (slot >= 0) here.computeIfAbsent(slot, k -> new ArrayList<>()).add(o);
        }
        for (Map.Entry<Integer, List<Player>> e : here.entrySet()) {
            List<Player> ps = e.getValue();
            Player first = ps.contains(viewer) && ps.size() > 1 ? ps.stream().filter(o -> !o.equals(viewer)).findFirst().orElse(viewer) : ps.get(0);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + first.getLocation().getBlockX() + ", " + first.getLocation().getBlockY() + ", " + first.getLocation().getBlockZ());
            lore.add("<gray>Health: <red>" + Math.round(first.getHealth()) / 2.0 + " hearts");
            if (ps.size() > 1) {
                lore.add("");
                lore.add("<gray>Also here:");
                for (Player o : ps) if (!o.equals(first)) lore.add("<white> " + o.getName());
                lore.add("<dark_gray>(zoom in to tell them apart)");
            }
            lore.add("");
            lore.add(first.equals(viewer) ? "<aqua>That's you" : "<yellow>Click <gray>teleport to " + first.getName());
            ItemStack head = Icons.button(plugin, Material.PLAYER_HEAD, (first.equals(viewer) ? "<aqua><bold>" : "<green><bold>") + first.getName(),
                    lore.toArray(new String[0]));
            if (head.getItemMeta() instanceof SkullMeta sm) { sm.setOwningPlayer(first); head.setItemMeta(sm); }
            v.cells.put(e.getKey(), new Cell(Kind.PLAYER, first.getLocation().getBlockX(), first.getLocation().getBlockZ(), first.getUniqueId().toString()));
            v.inv.setItem(e.getKey(), head);
        }
        // controls
        int step = size * 3;
        v.inv.setItem(45, Icons.button(plugin, Material.ARROW, "<yellow>West", "<gray>Move " + step + " blocks west"));
        v.inv.setItem(46, Icons.button(plugin, Material.ARROW, "<yellow>North", "<gray>Move " + step + " blocks north"));
        v.inv.setItem(47, Icons.button(plugin, Material.ARROW, "<yellow>South", "<gray>Move " + step + " blocks south"));
        v.inv.setItem(48, Icons.button(plugin, Material.ARROW, "<yellow>East", "<gray>Move " + step + " blocks east"));
        v.inv.setItem(49, Icons.button(plugin, Material.COMPASS, "<aqua><bold>Centre on me",
                "<gray>Map centre: <white>" + v.cx + ", " + v.cz, "<gray>Showing <white>" + size * COLS + " x " + size * ROWS + " <gray>blocks",
                "", "<yellow>Click <gray>centre on you", "<yellow>Right click <gray>next world"));
        v.inv.setItem(50, Icons.button(plugin, Material.SPYGLASS, "<green>Zoom in", "<gray>" + (v.zoom > 0 ? ZOOMS[v.zoom - 1] + " blocks/square" : "Max zoom")));
        v.inv.setItem(51, Icons.button(plugin, Material.FILLED_MAP, "<green>Zoom out", "<gray>" + (v.zoom < ZOOMS.length - 1 ? ZOOMS[v.zoom + 1] + " blocks/square" : "Whole world")));
        v.inv.setItem(52, Icons.button(plugin, Material.LODESTONE, "<gold><bold>Set waypoint here", "<gray>Saves where you're standing.", "<gray>You'll name it in chat.",
                "", "<dark_gray>Tip: shift-click any square on", "<dark_gray>the map to drop one there."));
        v.inv.setItem(53, Icons.button(plugin, Material.BOOK, "<gold><bold>All waypoints (" + waypoints.size() + ")", "<gray>Every waypoint in every world."));
    }

    private int slotOf(View v, int x, int z) {
        int size = ZOOMS[v.zoom];
        int c = Math.floorDiv(x - originX(v), size), r = Math.floorDiv(z - originZ(v), size);
        if (c < 0 || c >= COLS || r < 0 || r >= ROWS) return -1;
        return r * 9 + c;
    }

    /** The top block in the middle of a square. Unvisited land shows as fog; chunks load in the background. */
    private ItemStack terrainItem(World w, int x, int z, int size, Player viewer) {
        String k = w.getName() + ":" + x + ":" + z;
        Material m = terrain.get(k);
        String coords = "<dark_gray>" + x + ", " + z;
        if (m == null) {
            if (w.isChunkLoaded(x >> 4, z >> 4)) {
                m = top(w, x, z);
                cache(k, m);
            } else {
                terrain.put(k, Material.STRUCTURE_VOID);   // loading...
                w.getChunkAtAsync(x >> 4, z >> 4, false).thenAccept(chunk -> {
                    if (chunk == null) { cache(k, Material.BLACK_STAINED_GLASS_PANE); return; }
                    cache(k, top(w, x, z));
                    if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof View v && !v.list) draw(viewer, v);
                });
                return Icons.button(plugin, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Loading...", coords);
            }
        }
        if (m == Material.STRUCTURE_VOID) return Icons.button(plugin, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Loading...", coords);
        if (m == Material.BLACK_STAINED_GLASS_PANE) return Icons.button(plugin, m, "<dark_gray>Unexplored", coords,
                "", "<yellow>Right click <gray>teleport (generates it)");
        return Icons.button(plugin, m, "<white>" + pretty(m), coords, "",
                "<yellow>Click <gray>zoom in here", "<yellow>Right click <gray>teleport here", "<yellow>Shift click <gray>set a waypoint here");
    }

    private void cache(String k, Material m) {
        if (terrain.size() > 30_000) terrain.clear();
        terrain.put(k, m);
    }

    private static Material top(World w, int x, int z) {
        Block b = w.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
        Material m = b.getType();
        String n = m.name();
        if (n.contains("WATER") || m == Material.KELP || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS
                || m == Material.BUBBLE_COLUMN) return Material.BLUE_STAINED_GLASS;
        if (m == Material.LAVA) return Material.MAGMA_BLOCK;
        if (m == Material.SNOW) return Material.SNOW_BLOCK;
        if (!m.isItem() || m.isAir()) {
            Material below = b.getRelative(0, -1, 0).getType();
            return below.isItem() && !below.isAir() ? below : Material.STONE;
        }
        return m;
    }

    private static String pretty(Material m) {
        if (m == Material.BLUE_STAINED_GLASS) return "Water";
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ waypoint list page

    private void openList(Player p) {
        View v = new View();
        View cur = last.get(p.getUniqueId());
        if (cur != null) { v.world = cur.world; v.cx = cur.cx; v.cz = cur.cz; v.zoom = cur.zoom; }
        v.list = true;
        v.inv = Bukkit.createInventory(v, 54, plugin.mm("<gold><bold>Waypoints"));
        int i = 0;
        for (Map.Entry<String, Location> e : waypoints.entrySet()) {
            if (i >= 45) break;
            Location l = e.getValue();
            v.cells.put(i, new Cell(Kind.WAYPOINT, l.getBlockX(), l.getBlockZ(), e.getKey()));
            v.inv.setItem(i++, Icons.button(plugin, Material.RECOVERY_COMPASS, "<gold><bold>" + e.getKey(),
                    "<gray>" + (l.getWorld() == null ? "?" : l.getWorld().getName()) + "  " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ(),
                    "", "<yellow>Click <gray>teleport here", "<yellow>Right click <gray>show on the map", "<yellow>Shift + right click <gray>delete"));
        }
        if (waypoints.isEmpty()) v.inv.setItem(22, Icons.button(plugin, Material.PAPER, "<gray>No waypoints yet",
                "<gray>Use <white>Set waypoint here <gray>on the map,", "<gray>or shift-click a square."));
        v.inv.setItem(49, Icons.button(plugin, Material.FILLED_MAP, "<yellow>Back to the map"));
        p.openInventory(v.inv);
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View v)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || !Owner.isOwner(p)) { event.getWhoClicked().closeInventory(); return; }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        ClickType click = event.getClick();
        if (v.list) { listClick(p, v, slot, click); return; }
        World w = Bukkit.getWorld(v.world);
        if (w == null) { p.closeInventory(); return; }
        int size = ZOOMS[v.zoom];
        if (slot >= 45) {
            switch (slot) {
                case 45 -> v.cx -= size * 3;
                case 46 -> v.cz -= size * 3;
                case 47 -> v.cz += size * 3;
                case 48 -> v.cx += size * 3;
                case 49 -> {
                    if (click.isRightClick()) {
                        List<World> worlds = Bukkit.getWorlds();
                        World next = worlds.get((worlds.indexOf(w) + 1) % worlds.size());
                        v.world = next.getName();
                        v.cx = next.getSpawnLocation().getBlockX();
                        v.cz = next.getSpawnLocation().getBlockZ();
                    } else {
                        v.world = p.getWorld().getName();
                        v.cx = p.getLocation().getBlockX();
                        v.cz = p.getLocation().getBlockZ();
                    }
                }
                case 50 -> v.zoom = Math.max(0, v.zoom - 1);
                case 51 -> v.zoom = Math.min(ZOOMS.length - 1, v.zoom + 1);
                case 52 -> { askName(p, p.getLocation()); return; }
                case 53 -> { openList(p); return; }
                default -> { return; }
            }
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            reopen(p, v);
            return;
        }
        Cell cell = v.cells.get(slot);
        if (cell == null) return;
        switch (cell.kind()) {
            case PLAYER -> {
                Player target = Bukkit.getPlayer(UUID.fromString(cell.ref()));
                if (target == null || target.equals(p)) return;
                p.closeInventory();
                p.teleport(target.getLocation());
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Teleported to <white>" + target.getName() + "<gray>."));
            }
            case WAYPOINT -> {
                if (click == ClickType.SHIFT_RIGHT) { deleteWaypoint(p, cell.ref()); draw(p, v); return; }
                Location l = waypoints.get(cell.ref());
                if (l == null || l.getWorld() == null) return;
                p.closeInventory();
                p.teleport(l);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Teleported to waypoint <gold>" + cell.ref() + "<gray>."));
            }
            case TERRAIN -> {
                if (click.isShiftClick()) {
                    int y = w.isChunkLoaded(cell.x() >> 4, cell.z() >> 4) ? w.getHighestBlockYAt(cell.x(), cell.z()) + 1 : 100;
                    askName(p, new Location(w, cell.x() + 0.5, y, cell.z() + 0.5, p.getLocation().getYaw(), 0));
                } else if (click.isRightClick()) {
                    p.closeInventory();
                    teleportToColumn(p, w, cell.x(), cell.z());
                } else {
                    v.cx = cell.x();
                    v.cz = cell.z();
                    v.zoom = Math.max(0, v.zoom - 2);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                    reopen(p, v);
                }
            }
        }
    }

    private void listClick(Player p, View v, int slot, ClickType click) {
        if (slot == 49) { open(p); return; }
        Cell cell = v.cells.get(slot);
        if (cell == null) return;
        Location l = waypoints.get(cell.ref());
        if (l == null || l.getWorld() == null) return;
        if (click == ClickType.SHIFT_RIGHT) { deleteWaypoint(p, cell.ref()); openList(p); return; }
        if (click.isRightClick()) {
            View m = last.computeIfAbsent(p.getUniqueId(), k -> new View());
            m.world = l.getWorld().getName();
            m.cx = l.getBlockX();
            m.cz = l.getBlockZ();
            if (m.zoom == 0) m.zoom = 4;
            open(p);
            return;
        }
        p.closeInventory();
        p.teleport(l);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Teleported to waypoint <gold>" + cell.ref() + "<gray>."));
    }

    private void reopen(Player p, View v) {
        // the size in the title changes with the zoom, so open a fresh view in place
        View nv = new View();
        nv.world = v.world; nv.cx = v.cx; nv.cz = v.cz; nv.zoom = v.zoom;
        nv.inv = Bukkit.createInventory(nv, 54, plugin.mm(title(nv)));
        last.put(p.getUniqueId(), nv);
        draw(p, nv);
        p.openInventory(nv.inv);
    }

    private void teleportToColumn(Player p, World w, int x, int z) {
        p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Teleporting to <white>" + x + ", " + z + "<gray>..."));
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (!p.isOnline()) return;
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            p.teleport(new Location(w, x + 0.5, y, z + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch()));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            cache(w.getName() + ":" + x + ":" + z, top(w, x, z));
        });
    }

    private void deleteWaypoint(Player p, String name) {
        if (waypoints.remove(name) != null) {
            save();
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Deleted waypoint <gold>" + name + "<gray>."));
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.6f, 1.4f);
        }
    }

    private void askName(Player p, Location at) {
        p.closeInventory();
        naming.put(p.getUniqueId(), at);
        p.sendMessage(plugin.mm(plugin.prefix() + "<gold>Type a name for the waypoint <gray>at <white>" + at.getBlockX() + ", " + at.getBlockY() + ", "
                + at.getBlockZ() + " <gray>(or <white>cancel<gray>)."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Location at = naming.remove(event.getPlayer().getUniqueId());
        if (at == null) return;
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (raw.equalsIgnoreCase("cancel")) { p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Cancelled.")); return; }
            String name = raw.replaceAll("[^A-Za-z0-9 _\\-]", "").trim();
            if (name.length() > 24) name = name.substring(0, 24);
            if (name.isEmpty()) name = "Waypoint " + (waypoints.size() + 1);
            name = name.replace('.', '_');
            waypoints.put(name, at);
            save();
            p.sendMessage(plugin.mm(plugin.prefix() + "<green>Saved waypoint <gold>" + name + "<green>. <gray>Open <white>/minimap <gray>to see it."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
    }
}
