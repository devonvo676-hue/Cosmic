package com.cosmicabilities.item;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cosmic Pickaxe (netherite, mines 3x3) and Cosmic Axe (netherite, fells whole trees).
 * Shift + right-click (in the air) toggles the special mining on/off; the state is saved on the item and shown in its lore.
 */
public class CosmicTools implements Listener {
    public enum Tool {
        PICKAXE("cosmic_pickaxe", Material.NETHERITE_PICKAXE, "<gradient:#7b2ff7:#f107a3:#00e5ff><bold>Cosmic Pickaxe",
                "Mines a 3x3 area"),
        AXE("cosmic_axe", Material.NETHERITE_AXE, "<gradient:#00e5ff:#7b2ff7:#f107a3><bold>Cosmic Axe",
                "Fells the whole tree at once"),
        SHOVEL("cosmic_shovel", Material.NETHERITE_SHOVEL, "<gradient:#f107a3:#7b2ff7:#00e5ff><bold>Cosmic Shovel",
                "Digs a 3x3 area");

        public final String id;
        public final Material material;
        public final String name;
        public final String power;

        Tool(String id, Material material, String name, String power) {
            this.id = id; this.material = material; this.name = name; this.power = power;
        }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey toolKey, modeKey;
    /** Players currently breaking extra blocks (so the extra BlockBreakEvents we fire don't chain). */
    private final Set<UUID> busy = new HashSet<>();

    public CosmicTools(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "cosmic_tool");
        this.modeKey = new NamespacedKey(plugin, "cosmic_tool_on");
    }

    public ItemStack create(Tool tool) {
        ItemStack it = new ItemStack(tool.material);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", tool.id));
        meta.displayName(plugin.mm("<!italic>" + tool.name));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.id);
        meta.getPersistentDataContainer().set(modeKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        Enchantment eff = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"));
        Enchantment unb = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
        if (eff != null) it.addUnsafeEnchantment(eff, plugin.getConfig().getInt("cosmic-tools.efficiency", 5));
        if (unb != null) it.addUnsafeEnchantment(unb, plugin.getConfig().getInt("cosmic-tools.unbreaking", 3));
        writeLore(it, tool, true);
        return it;
    }

    public Tool toolOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        if (id == null) return null;
        for (Tool t : Tool.values()) if (t.id.equals(id)) return t;
        return null;
    }

    private boolean isOn(ItemStack it) {
        Byte b = it.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.BYTE);
        return b == null || b == 1;
    }

    private void writeLore(ItemStack it, Tool tool, boolean on) {
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.mm("<!italic><dark_gray>Forged from a dying star"));
        lore.add(Component.empty());
        lore.add(plugin.mm("<!italic><aqua>" + tool.power + ": " + (on ? "<green><bold>ON" : "<red><bold>OFF")));
        lore.add(plugin.mm("<!italic><yellow>Shift + right-click <gray>to toggle"));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(on);
        it.setItemMeta(meta);
    }

    // ------------------------------------------------------------------ toggle

    @EventHandler
    public void onToggle(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Tool tool = toolOf(hand);
        if (tool == null) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && tool != Tool.PICKAXE) event.setCancelled(true);  // no log stripping or path making
        boolean on = !isOn(hand);
        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(modeKey, PersistentDataType.BYTE, (byte) (on ? 1 : 0));
        hand.setItemMeta(meta);
        writeLore(hand, tool, on);
        p.sendActionBar(plugin.mm(tool.name + " <gray>- " + tool.power + ": " + (on ? "<green><bold>ON" : "<red><bold>OFF")));
        p.playSound(p.getLocation(), on ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, on ? 1.6f : 1.2f);
    }

    // ------------------------------------------------------------------ mining

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (busy.contains(p.getUniqueId()) || p.getGameMode() == GameMode.SPECTATOR) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        Tool tool = toolOf(hand);
        if (tool == null || !isOn(hand)) return;
        Block origin = event.getBlock();
        List<Block> extra = tool == Tool.AXE ? tree(origin) : area(p, origin, hand, tool);
        if (extra.isEmpty()) return;
        busy.add(p.getUniqueId());
        try {
            int broken = 0;
            for (Block b : extra) {
                if (hand.getType().isAir() || toolOf(hand) == null) break;   // tool broke
                BlockBreakEvent sub = new BlockBreakEvent(b, p);
                plugin.getServer().getPluginManager().callEvent(sub);   // respects claims/protection plugins
                if (sub.isCancelled()) continue;
                if (p.getGameMode() == GameMode.CREATIVE) b.setType(Material.AIR);
                else b.breakNaturally(hand, true);
                broken++;
                if (p.getGameMode() != GameMode.CREATIVE && broken % 2 == 0) p.damageItemStack(EquipmentSlot.HAND, 1);
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    /** The 8 blocks around the mined one, in the plane facing the player, that this pickaxe could mine. */
    private List<Block> area(Player p, Block origin, ItemStack hand, Tool tool) {
        BlockFace face = p.getTargetBlockFace(6);
        if (face == null) face = Math.abs(p.getLocation().getPitch()) > 50 ? BlockFace.UP : BlockFace.NORTH;
        List<Block> out = new ArrayList<>();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                Block rel = switch (face) {
                    case UP, DOWN -> origin.getRelative(a, 0, b);
                    case EAST, WEST -> origin.getRelative(0, a, b);
                    default -> origin.getRelative(a, b, 0);
                };
                if (mineable(rel, hand, tool)) out.add(rel);
            }
        }
        return out;
    }

    private boolean mineable(Block b, ItemStack hand, Tool tool) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid() || m.getHardness() < 0) return false;   // bedrock, barriers, portals...
        if (m == Material.SPAWNER || m == Material.END_PORTAL_FRAME || m == Material.REINFORCED_DEEPSLATE) return false;
        Tag<Material> tag = tool == Tool.SHOVEL ? Tag.MINEABLE_SHOVEL : Tag.MINEABLE_PICKAXE;
        return tag.isTagged(m) && b.isPreferredTool(hand);
    }

    /** Every log connected to the chopped one (including diagonals), up to cosmic-tools.max-tree-blocks. */
    private List<Block> tree(Block origin) {
        if (!Tag.LOGS.isTagged(origin.getType())) return List.of();
        int max = plugin.getConfig().getInt("cosmic-tools.max-tree-blocks", 200);
        boolean leaves = plugin.getConfig().getBoolean("cosmic-tools.break-leaves", false);
        List<Block> out = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(origin);
        while (!queue.isEmpty() && out.size() < max) {
            Block cur = queue.poll();
            for (int x = -1; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                if (x == 0 && y == 0 && z == 0) continue;
                Block n = cur.getRelative(x, y, z);
                if (!seen.add(n)) continue;
                boolean log = Tag.LOGS.isTagged(n.getType());
                boolean leaf = leaves && Tag.LEAVES.isTagged(n.getType()) && n.getLocation().distanceSquared(origin.getLocation()) < 49;
                if (!log && !leaf) continue;
                out.add(n);
                if (log) queue.add(n);
            }
        }
        out.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        return out;
    }

    /** Dropped cosmic gear glows white so it's easy to find (orbs already glow). */
    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("dropped-item-glow", true)) return;
        ItemStack it = event.getEntity().getItemStack();
        if (toolOf(it) != null || plugin.banPistol().isPistol(it) || plugin.orbs().isReroll(it)) {
            event.getEntity().setGlowing(true);   // no team colour = white outline
        }
    }

    public static boolean hasTool(Collection<ItemStack> items, CosmicTools tools, Tool tool) {
        for (ItemStack it : items) if (tools.toolOf(it) == tool) return true;
        return false;
    }
}
