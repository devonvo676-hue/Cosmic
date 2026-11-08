package com.cosmicabilities.item;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Boss-dropped upgrade materials. Identified by a PDC tag, so renamed/vanilla items never count. */
public enum CosmicMaterial {
    VOID_SHARD("Void Shard", Material.ECHO_SHARD, "#9d4edd", "Dropped by the Void Emperor"),
    SOLAR_CORE("Solar Core", Material.MAGMA_CREAM, "#ffb703", "Dropped by the Solar Titan"),
    FROZEN_STARDUST("Frozen Stardust", Material.PRISMARINE_CRYSTALS, "#8ecae6", "Dropped by the Frost Leviathan"),
    NEBULA_ESSENCE("Nebula Essence", Material.AMETHYST_SHARD, "#f72585", "Dropped by the Nebula Serpent"),
    ASTRAL_FRAGMENT("Astral Fragment", Material.FIREWORK_STAR, "#e0e1dd", "Dropped by the Cosmic Destroyer");

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static NamespacedKey key;

    public final String displayName;
    public final Material base;
    public final String color;
    public final String source;

    CosmicMaterial(String displayName, Material base, String color, String source) {
        this.displayName = displayName;
        this.base = base;
        this.color = color;
        this.source = source;
    }

    public static void init(CosmicAbilitiesPlugin plugin) {
        key = new NamespacedKey(plugin, "cosmic_material");
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String colored() {
        return "<" + color + ">" + displayName + "</" + color + ">";
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(base, Math.max(1, Math.min(amount, base.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<!italic><bold>" + colored()));
        List<Component> lore = List.of(
                MM.deserialize("<!italic><gray>Cosmic upgrade material"),
                MM.deserialize("<!italic><dark_gray>" + source),
                Component.empty(),
                MM.deserialize("<!italic><yellow>Use <white>/cosmic upgrade</white> to empower your ability"));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id());
        item.setItemMeta(meta);
        return item;
    }

    public static CosmicMaterial of(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return null;
        return fromId(id);
    }

    public static CosmicMaterial fromId(String id) {
        for (CosmicMaterial m : values()) if (m.id().equalsIgnoreCase(id)) return m;
        return null;
    }

    public int count(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (of(item) == this) total += item.getAmount();
        }
        return total;
    }

    /** Removes amount of this material. Caller must check count() first. */
    public void take(Player player, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack item = contents[i];
            if (of(item) != this) continue;
            int use = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - use);
            amount -= use;
            if (item.getAmount() <= 0) player.getInventory().setItem(i, null);
        }
    }

    /** Gives items, dropping anything that doesn't fit at the player's feet. */
    public void give(Player player, int amount) {
        while (amount > 0) {
            int stack = Math.min(amount, base.getMaxStackSize());
            HashMap<Integer, ItemStack> left = player.getInventory().addItem(create(stack));
            for (ItemStack overflow : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
            amount -= stack;
        }
    }
}
