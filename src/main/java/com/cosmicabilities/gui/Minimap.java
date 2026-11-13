package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.Owner;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * v2tou's minimap: a live map that sits in his offhand (bottom corner of the screen) and follows him around,
 * drawn from the real terrain with height shading. Every online player is on it with their name, and so are his
 * waypoints. /minimap turns it on and off.
 *
 * Holding it in the main hand (press F to swap it over) gives a red X pointer you move by turning your head:
 *   LEFT CLICK on a player / waypoint      teleport there
 *   SNEAK + LEFT CLICK on empty ground     drop a waypoint there (name it in chat)
 *   SNEAK + LEFT CLICK on a waypoint       delete it
 *   RIGHT CLICK / SNEAK + RIGHT CLICK      zoom in / out (zoomed all the way out it shows the whole explored world)
 */
public class Minimap implements Listener, CommandExecutor {
    private static final int[] ZOOMS = {1, 2, 4, 8, 16, 32, 64};   // blocks per pixel
    private static final int N = 128;
    private static final int UNKNOWN = 0, UNGENERATED = -2;

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey itemKey, savedOffhandKey, wantsKey;
    private final File wpFile, cacheFile;
    private final Map<String, Location> waypoints = new LinkedHashMap<>();
    private final Map<UUID, Location> naming = new ConcurrentHashMap<>();
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    /** world -> chunk key -> (colour id << 16 | height), sampled at each chunk's centre. Lets the zoomed-out map show the whole world. */
    private final Map<String, Map<Long, Integer>> chunkCache = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private int asyncBudget;
    private MapView mapView;

    // colours: id -> 3 shades (dark, normal, light) as map palette bytes
    private final List<byte[]> palette = new ArrayList<>();
    private final Map<Integer, Integer> rgbToId = new ConcurrentHashMap<>();
    private final Map<Material, Integer> materialId = new EnumMap<>(Material.class);
    private byte dark, fog, white, black;

    private static final class View {
        String world;
        int zoom = 1, ox = Integer.MIN_VALUE, oz, z = -1;
        final int[] col = new int[N * N];
        final int[] h = new int[N * N];
        boolean pointer;
        float anchorYaw, anchorPitch;
        int px = 64, py = 64;
        String hoverKind, hoverRef;
        int hoverX, hoverZ;
    }

    public Minimap(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "minimap");
        this.savedOffhandKey = new NamespacedKey(plugin, "minimap_saved_offhand");
        this.wantsKey = new NamespacedKey(plugin, "minimap_on");
        this.wpFile = new File(plugin.getDataFolder(), "waypoints.yml");
        this.cacheFile = new File(plugin.getDataFolder(), "minimap-cache.bin");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(wpFile);
        ConfigurationSection sec = yml.getConfigurationSection("waypoints");
        if (sec != null) for (String k : sec.getKeys(false)) {
            Location l = sec.getLocation(k);
            if (l != null) waypoints.put(k, l);
        }
        initPalette();
        loadCache();
        mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
        for (MapRenderer r : new ArrayList<>(mapView.getRenderers())) mapView.removeRenderer(r);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        mapView.addRenderer(new Renderer());
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> asyncBudget = 6, 1L, 1L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveCache, 20L * 300, 20L * 300);
        plugin.getServer().getScheduler().runTask(plugin, () -> { for (Player p : Bukkit.getOnlinePlayers()) fixItems(p); });
    }

    // ------------------------------------------------------------------ the item

    private boolean isMap(ItemStack it) {
        return it != null && it.getType() == Material.FILLED_MAP && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    private ItemStack mapItem() {
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        MapMeta m = (MapMeta) it.getItemMeta();
        m.setMapView(mapView);
        m.displayName(plugin.mm("<!italic><aqua><bold>Minimap"));
        m.lore(List.of(plugin.mm("<!italic><gray>Hold it in your main hand (F) to point:"),
                plugin.mm("<!italic><yellow>Click <gray>a player / waypoint: teleport"),
                plugin.mm("<!italic><yellow>Sneak + click <gray>ground: new waypoint"),
                plugin.mm("<!italic><yellow>Sneak + click <gray>a waypoint: delete it"),
                plugin.mm("<!italic><yellow>Right click <gray>zoom in, <yellow>sneak <gray>zoom out"),
                plugin.mm("<!italic><dark_red>v2tou only")));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    /** The map's id changes every restart: point any minimap he's carrying at the current one. */
    private void fixItems(Player p) {
        ItemStack[] all = p.getInventory().getContents();
        for (ItemStack it : all) {
            if (!isMap(it)) continue;
            if (!Owner.isOwner(p)) { it.setAmount(0); continue; }
            MapMeta m = (MapMeta) it.getItemMeta();
            if (m.getMapView() == null || m.getMapView().getId() != mapView.getId()) { m.setMapView(mapView); it.setItemMeta(m); }
        }
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

    /** /minimap: on (map goes into the offhand, whatever was there is kept safe) or off (it comes back). */
    public void open(Player p) {
        if (!Owner.isOwner(p)) return;
        boolean had = false;
        for (ItemStack it : p.getInventory().getContents()) if (isMap(it)) { it.setAmount(0); had = true; }
        if (had) {
            p.getPersistentDataContainer().remove(wantsKey);
            restoreOffhand(p);
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Minimap <red>off<gray>."));
            return;
        }
        p.getPersistentDataContainer().set(wantsKey, PersistentDataType.BYTE, (byte) 1);
        giveMap(p);
        p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Minimap <green>on<gray>. Press <white>F <gray>to hold it and point/click on it."));
        p.playSound(p.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1f, 1.2f);
    }

    private void giveMap(Player p) {
        ItemStack off = p.getInventory().getItemInOffHand();
        if (!off.getType().isAir() && !isMap(off)) {
            p.getPersistentDataContainer().set(savedOffhandKey, PersistentDataType.STRING, Base64.getEncoder().encodeToString(off.serializeAsBytes()));
        }
        p.getInventory().setItemInOffHand(mapItem());
    }

    private void restoreOffhand(Player p) {
        String raw = p.getPersistentDataContainer().get(savedOffhandKey, PersistentDataType.STRING);
        p.getPersistentDataContainer().remove(savedOffhandKey);
        if (raw == null) return;
        try {
            ItemStack back = ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
            if (p.getInventory().getItemInOffHand().getType().isAir()) p.getInventory().setItemInOffHand(back);
            else for (ItemStack left : p.getInventory().addItem(back).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Couldn't give back " + p.getName() + "'s offhand item: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ colours

    private void initPalette() {
        dark = color(new java.awt.Color(18, 18, 24));
        fog = color(new java.awt.Color(36, 36, 44));
        white = color(java.awt.Color.WHITE);
        black = color(java.awt.Color.BLACK);
        palette.add(null);   // id 0 = unknown
    }

    @SuppressWarnings("deprecation")
    private static byte color(java.awt.Color c) { return MapPalette.matchColor(c); }

    private int idFor(int r, int g, int b) {
        int rgb = (r << 16) | (g << 8) | b;
        return rgbToId.computeIfAbsent(rgb, k -> {
            byte[] shades = new byte[3];
            double[] f = {0.78, 1.0, 1.14};
            for (int i = 0; i < 3; i++) shades[i] = color(new java.awt.Color(Math.min(255, (int) (r * f[i])), Math.min(255, (int) (g * f[i])), Math.min(255, (int) (b * f[i]))));
            synchronized (palette) { palette.add(shades); return palette.size() - 1; }
        });
    }

    private int classify(Material m) {
        Integer cached = materialId.get(m);
        if (cached != null) return cached;
        int id = classifyRaw(m);
        materialId.put(m, id);
        return id;
    }

    private int classifyRaw(Material m) {
        String n = m.name();
        if (n.contains("WATER") || n.contains("KELP") || n.contains("SEAGRASS") || m == Material.BUBBLE_COLUMN) return idFor(52, 90, 220);
        if (m == Material.LAVA) return idFor(232, 96, 24);
        if (m == Material.MAGMA_BLOCK) return idFor(170, 60, 24);
        for (DyeColor dc : DyeColor.values()) if (n.startsWith(dc.name() + "_")) {
            org.bukkit.Color c = dc.getColor();
            return idFor(c.getRed(), c.getGreen(), c.getBlue());
        }
        if (n.contains("LEAVES")) return idFor(44, 118, 34);
        if (n.contains("ICE")) return idFor(150, 180, 250);
        if (n.contains("SNOW")) return idFor(245, 248, 252);
        if (m == Material.GRASS_BLOCK || n.contains("MOSS") || n.contains("GRASS") || n.contains("FERN") || n.contains("VINE")
                || n.contains("TULIP") || n.contains("DAISY") || n.contains("POPPY") || n.contains("DANDELION") || n.contains("BUSH")
                || n.contains("LILY") || n.contains("SAPLING") || n.contains("AZALEA")) return idFor(108, 168, 58);
        if (n.equals("RED_SAND") || n.contains("RED_SANDSTONE")) return idFor(196, 108, 48);
        if (n.contains("SAND")) return idFor(226, 212, 156);
        if (n.contains("DIRT") || n.contains("MUD") || n.contains("PODZOL") || n.contains("MYCELIUM") || n.contains("FARMLAND") || n.contains("PATH")
                || n.contains("ROOTED")) return idFor(134, 96, 64);
        if (n.contains("GRAVEL")) return idFor(136, 130, 126);
        if (n.contains("CLAY")) return idFor(158, 164, 178);
        if (n.contains("TERRACOTTA")) return idFor(156, 92, 66);
        if (n.contains("DEEPSLATE") || n.contains("BLACKSTONE") || n.contains("BASALT")) return idFor(78, 78, 84);
        if (n.contains("NETHERRACK") || n.startsWith("NETHER_") || n.contains("CRIMSON")) return idFor(112, 34, 34);
        if (n.contains("WARPED")) return idFor(40, 120, 110);
        if (n.contains("SOUL")) return idFor(82, 64, 50);
        if (n.contains("END_STONE") || n.contains("PURPUR")) return idFor(218, 216, 160);
        if (n.contains("OBSIDIAN")) return idFor(30, 22, 44);
        if (n.contains("LOG") || n.contains("WOOD") || n.contains("PLANKS") || n.contains("STEM") || n.contains("FENCE") || n.contains("DOOR")
                || n.contains("BAMBOO") || n.contains("BOOKSHELF") || n.contains("CHEST") || n.contains("BARREL")) return idFor(146, 112, 68);
        if (n.contains("GLASS")) return idFor(196, 216, 226);
        if (n.contains("BRICK")) return idFor(150, 84, 70);
        if (n.contains("COPPER")) return idFor(186, 110, 80);
        if (n.contains("STONE") || n.contains("COBBLE") || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("GRANITE")
                || n.contains("TUFF") || n.contains("ORE") || n.contains("CALCITE")) return idFor(118, 118, 120);
        return idFor(128, 128, 128);
    }

    // ------------------------------------------------------------------ terrain sampling + cache

    private static long chunkKey(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    private int sampleBlock(World w, int x, int z) {
        Block b = w.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
        return (classify(b.getType()) << 16) | (b.getY() & 0xffff);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk c = event.getChunk();
        World w = c.getWorld();
        chunkCache.computeIfAbsent(w.getName(), k -> new ConcurrentHashMap<>())
                .put(chunkKey(c.getX(), c.getZ()), sampleBlock(w, (c.getX() << 4) + 8, (c.getZ() << 4) + 8));
    }

    /** Colour + height at a map pixel; UNKNOWN while it's still being looked up. */
    private int sample(World w, int x, int z, int zoom) {
        int cx = x >> 4, cz = z >> 4;
        if (zoom <= 8 && w.isChunkLoaded(cx, cz)) return sampleBlock(w, x, z);
        Map<Long, Integer> cache = chunkCache.computeIfAbsent(w.getName(), k -> new ConcurrentHashMap<>());
        Integer v = cache.get(chunkKey(cx, cz));
        if (v != null) return v;
        String key = w.getName() + ":" + cx + ":" + cz;
        if (asyncBudget > 0 && pending.add(key)) {
            asyncBudget--;
            w.getChunkAtAsync(cx, cz, false).whenComplete((chunk, err) -> {
                pending.remove(key);
                if (chunk == null) cache.put(chunkKey(cx, cz), UNGENERATED);   // never been generated: stays dark
            });
        }
        return UNKNOWN;
    }

    private void loadCache() {
        if (!cacheFile.exists()) return;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(cacheFile)))) {
            int worlds = in.readInt();
            for (int i = 0; i < worlds; i++) {
                String name = in.readUTF();
                int n = in.readInt();
                Map<Long, Integer> m = chunkCache.computeIfAbsent(name, k -> new ConcurrentHashMap<>());
                List<String> names = new ArrayList<>();
                int pn = in.readInt();
                for (int j = 0; j < pn; j++) names.add(in.readUTF());
                for (int j = 0; j < n; j++) {
                    long k = in.readLong();
                    int v = in.readInt();
                    if (v == UNGENERATED) continue;   // may have been generated since
                    int matIdx = v >>> 16;
                    Material mat = matIdx < names.size() ? Material.matchMaterial(names.get(matIdx)) : null;
                    if (mat == null) continue;
                    m.put(k, (classify(mat) << 16) | (v & 0xffff));
                }
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Minimap cache unreadable, starting fresh: " + e.getMessage());
        }
    }

    /** Saved by material name (colour ids are rebuilt each start). */
    public void saveCache() {
        Map<Integer, Material> idToMat = new java.util.HashMap<>();
        for (Map.Entry<Material, Integer> e : materialId.entrySet()) idToMat.putIfAbsent(e.getValue(), e.getKey());
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(cacheFile)))) {
            out.writeInt(chunkCache.size());
            for (Map.Entry<String, Map<Long, Integer>> w : chunkCache.entrySet()) {
                out.writeUTF(w.getKey());
                Map<Long, Integer> m = w.getValue();
                List<String> names = new ArrayList<>();
                Map<Integer, Integer> remap = new java.util.HashMap<>();
                List<long[]> rows = new ArrayList<>();
                for (Map.Entry<Long, Integer> e : m.entrySet()) {
                    int v = e.getValue();
                    if (v == UNGENERATED) continue;
                    Material mat = idToMat.get(v >>> 16);
                    if (mat == null) continue;
                    int idx = remap.computeIfAbsent(v >>> 16, k -> { names.add(mat.name()); return names.size() - 1; });
                    rows.add(new long[]{e.getKey(), (idx << 16) | (v & 0xffff)});
                }
                out.writeInt(rows.size());
                out.writeInt(names.size());
                for (String s : names) out.writeUTF(s);
                for (long[] r : rows) { out.writeLong(r[0]); out.writeInt((int) r[1]); }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save the minimap cache: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ drawing

    private final class Renderer extends MapRenderer {
        Renderer() { super(true); }

        @Override
        public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
            MapCursorCollection cursors = new MapCursorCollection();
            if (!Owner.isOwner(player)) {
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) canvas.setPixel(x, y, black);
                canvas.setCursors(cursors);
                return;
            }
            View v = views.computeIfAbsent(player.getUniqueId(), k -> new View());
            World w = player.getWorld();
            int zoom = ZOOMS[v.zoom];
            Location loc = player.getLocation();
            int ox = Math.floorDiv(loc.getBlockX(), zoom) - 64, oz = Math.floorDiv(loc.getBlockZ(), zoom) - 64;
            shift(v, w.getName(), ox, oz, zoom);
            // fill in what's missing, then keep refreshing a slice every tick so changes show up
            int budget = 2500;
            for (int i = 0; i < N * N && budget > 0; i++) {
                if (v.col[i] != UNKNOWN) continue;
                int s = sample(w, (ox + i % N) * zoom + zoom / 2, (oz + i / N) * zoom + zoom / 2, zoom);
                if (s != UNKNOWN) { v.col[i] = s == UNGENERATED ? UNGENERATED : s >>> 16; v.h[i] = s == UNGENERATED ? 0 : (short) (s & 0xffff); }
                budget--;
            }
            for (int k = 0; k < 300; k++) {
                int i = (int) ((player.getTicksLived() * 300L + k) % (N * N));
                int s = sample(w, (ox + i % N) * zoom + zoom / 2, (oz + i / N) * zoom + zoom / 2, zoom);
                if (s != UNKNOWN && s != UNGENERATED) { v.col[i] = s >>> 16; v.h[i] = (short) (s & 0xffff); }
            }
            for (int i = 0; i < N * N; i++) {
                int id = v.col[i];
                byte px;
                if (id == UNKNOWN) px = fog;
                else if (id == UNGENERATED || id >= palette.size()) px = dark;
                else {
                    int north = i >= N ? v.h[i - N] : v.h[i];
                    int shade = v.col[Math.max(0, i - N)] <= 0 ? 1 : v.h[i] > north ? 2 : v.h[i] < north ? 0 : 1;
                    px = palette.get(id)[shade];
                }
                canvas.setPixel(i % N, i / N, px);
            }
            // pointer (main hand)
            boolean main = isMap(player.getInventory().getItemInMainHand());
            if (main && !v.pointer) { v.anchorYaw = loc.getYaw(); v.anchorPitch = loc.getPitch(); }
            v.pointer = main;
            v.hoverKind = null;
            if (main) {
                v.px = clamp(64 + Math.round(wrap(loc.getYaw() - v.anchorYaw) * 2.2f));
                v.py = clamp(64 + Math.round((loc.getPitch() - v.anchorPitch) * 2.2f));
                v.hoverX = (ox + v.px) * zoom + zoom / 2;
                v.hoverZ = (oz + v.py) * zoom + zoom / 2;
            }
            int best = 26;   // squared pixel distance for hovering
            // waypoints
            for (Map.Entry<String, Location> e : waypoints.entrySet()) {
                Location l = e.getValue();
                if (l.getWorld() == null || !l.getWorld().equals(w)) continue;
                int x = Math.floorDiv(l.getBlockX(), zoom) - ox, y = Math.floorDiv(l.getBlockZ(), zoom) - oz;
                if (x < 0 || x >= N || y < 0 || y >= N) continue;
                if (main) {
                    int d = (x - v.px) * (x - v.px) + (y - v.py) * (y - v.py);
                    if (d < best) { best = d; v.hoverKind = "wp"; v.hoverRef = e.getKey(); }
                }
                cursors.addCursor(new MapCursor(pos(x), pos(y), (byte) 8, MapCursor.Type.BANNER_YELLOW, true,
                        Component.text(e.getKey(), NamedTextColor.GOLD)));
            }
            // other players
            for (Player o : w.getPlayers()) {
                if (o.equals(player)) continue;
                Location ol = o.getLocation();
                int x = Math.floorDiv(ol.getBlockX(), zoom) - ox, y = Math.floorDiv(ol.getBlockZ(), zoom) - oz;
                boolean in = x >= 0 && x < N && y >= 0 && y < N;
                if (in && main) {
                    int d = (x - v.px) * (x - v.px) + (y - v.py) * (y - v.py);
                    if (d < best) { best = d; v.hoverKind = "player"; v.hoverRef = o.getUniqueId().toString(); }
                }
                cursors.addCursor(new MapCursor(pos(clamp(x)), pos(clamp(y)), dir(ol.getYaw()),
                        in ? MapCursor.Type.FRAME : MapCursor.Type.PLAYER_OFF_MAP, true, Component.text(o.getName(), NamedTextColor.GREEN)));
            }
            // him, in the middle
            cursors.addCursor(new MapCursor(pos(64), pos(64), dir(loc.getYaw()), MapCursor.Type.PLAYER, true));
            if (main) {
                Component cap = null;
                if ("player".equals(v.hoverKind)) {
                    Player o = Bukkit.getPlayer(UUID.fromString(v.hoverRef));
                    cap = Component.text("Click: TP to " + (o == null ? "?" : o.getName()), NamedTextColor.WHITE);
                } else if ("wp".equals(v.hoverKind)) {
                    cap = Component.text("Click: TP to " + v.hoverRef, NamedTextColor.WHITE);
                }
                cursors.addCursor(new MapCursor(pos(v.px), pos(v.py), (byte) 0, MapCursor.Type.RED_X, true, cap));
            }
            canvas.setCursors(cursors);
            // corner text: zoom and position
            String z = "1:" + zoom;
            canvas.drawText(3, 3, MinecraftFont.Font, "§" + (black & 0xff) + ";" + z);
            canvas.drawText(2, 2, MinecraftFont.Font, "§" + (white & 0xff) + ";" + z);
            String xz = loc.getBlockX() + " " + loc.getBlockZ();
            canvas.drawText(3, N - 9, MinecraftFont.Font, "§" + (black & 0xff) + ";" + xz);
            canvas.drawText(2, N - 10, MinecraftFont.Font, "§" + (white & 0xff) + ";" + xz);
        }
    }

    /** Moves the stored picture along when he walks so only the new edge needs sampling. */
    private static void shift(View v, String world, int ox, int oz, int zoom) {
        if (!world.equals(v.world) || zoom != v.z || v.ox == Integer.MIN_VALUE || Math.abs(ox - v.ox) >= N || Math.abs(oz - v.oz) >= N) {
            java.util.Arrays.fill(v.col, UNKNOWN);
            java.util.Arrays.fill(v.h, 0);
        } else if (ox != v.ox || oz != v.oz) {
            int dx = ox - v.ox, dz = oz - v.oz;
            int[] col = new int[N * N], h = new int[N * N];
            for (int y = 0; y < N; y++) {
                int sy = y + dz;
                if (sy < 0 || sy >= N) continue;
                for (int x = 0; x < N; x++) {
                    int sx = x + dx;
                    if (sx < 0 || sx >= N) continue;
                    col[y * N + x] = v.col[sy * N + sx];
                    h[y * N + x] = v.h[sy * N + sx];
                }
            }
            System.arraycopy(col, 0, v.col, 0, N * N);
            System.arraycopy(h, 0, v.h, 0, N * N);
        }
        v.world = world; v.z = zoom; v.ox = ox; v.oz = oz;
    }

    private static int clamp(int p) { return Math.max(0, Math.min(N - 1, p)); }
    private static byte pos(int pixel) { return (byte) Math.max(-128, Math.min(127, pixel * 2 - 127)); }
    private static byte dir(float yaw) { return (byte) (((int) ((yaw + (yaw < 0 ? -8.0 : 8.0)) * 16.0 / 360.0)) & 15); }
    private static float wrap(float a) {
        a %= 360;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    // ------------------------------------------------------------------ clicking on the map

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !isMap(p.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        if (!Owner.isOwner(p)) { p.getInventory().setItemInMainHand(null); return; }
        View v = views.computeIfAbsent(p.getUniqueId(), k -> new View());
        Action a = event.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            v.zoom = p.isSneaking() ? Math.min(ZOOMS.length - 1, v.zoom + 1) : Math.max(0, v.zoom - 1);
            p.playSound(p.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.6f, 0.8f + v.zoom * 0.1f);
            p.sendActionBar(plugin.mm("<aqua>Zoom <white>1:" + ZOOMS[v.zoom] + (v.zoom == ZOOMS.length - 1 ? " <gray>(whole world)" : "")));
            return;
        }
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;
        World w = p.getWorld();
        if ("player".equals(v.hoverKind)) {
            Player o = Bukkit.getPlayer(UUID.fromString(v.hoverRef));
            if (o == null) return;
            p.teleport(o.getLocation());
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Teleported to <white>" + o.getName() + "<gray>."));
        } else if ("wp".equals(v.hoverKind)) {
            String name = v.hoverRef;
            if (p.isSneaking()) {
                if (waypoints.remove(name) != null) {
                    saveWaypoints();
                    p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Deleted waypoint <gold>" + name + "<gray>."));
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.6f, 1.4f);
                }
                return;
            }
            Location l = waypoints.get(name);
            if (l == null || l.getWorld() == null) return;
            p.teleport(l);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Teleported to waypoint <gold>" + name + "<gray>."));
        } else if (p.isSneaking() && v.pointer) {
            int x = v.hoverX, z = v.hoverZ;
            int y = w.isChunkLoaded(x >> 4, z >> 4) ? w.getHighestBlockYAt(x, z) + 1 : p.getLocation().getBlockY();
            naming.put(p.getUniqueId(), new Location(w, x + 0.5, y, z + 0.5, p.getLocation().getYaw(), 0));
            p.sendMessage(plugin.mm(plugin.prefix() + "<gold>Type a name for the waypoint <gray>at <white>" + x + ", " + z
                    + " <gray>(or <white>cancel<gray>)."));
        } else {
            p.sendActionBar(plugin.mm("<gray>Point the <red>X <gray>at a player or waypoint. <yellow>Sneak+click <gray>ground for a new waypoint."));
        }
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
            waypoints.put(name, at);
            saveWaypoints();
            p.sendMessage(plugin.mm(plugin.prefix() + "<green>Saved waypoint <gold>" + name + "<green>."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
        });
    }

    private void saveWaypoints() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Location> e : waypoints.entrySet()) yml.set("waypoints." + e.getKey(), e.getValue());
        try { yml.save(wpFile); } catch (IOException ex) { plugin.getLogger().warning("Couldn't save waypoints.yml"); }
    }

    // ------------------------------------------------------------------ keeping it his

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isMap(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) return;   // his own inventory is fine
        if (isMap(event.getCurrentItem()) || isMap(event.getCursor())) event.setCancelled(true);
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && event.getWhoClicked() instanceof Player p
                && isMap(p.getInventory().getItem(event.getHotbarButton()))) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isMap);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (Owner.isOwner(p) && p.getPersistentDataContainer().has(wantsKey, PersistentDataType.BYTE)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                boolean has = false;
                for (ItemStack it : p.getInventory().getContents()) if (isMap(it)) has = true;
                if (!has) p.getInventory().setItemInOffHand(mapItem());
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        fixItems(event.getPlayer());
    }
}
