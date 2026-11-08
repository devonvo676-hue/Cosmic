package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Builds GUI items. Ability icons use the resource pack's item models (cosmic:&lt;ability&gt;). */
public final class Icons {
    private Icons() {}

    public static ItemStack ability(CosmicAbilitiesPlugin plugin, AbilityType type, List<String> extraLore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", type.id()));
        meta.displayName(plugin.mm("<!italic><bold>" + type.colored(1)));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.mm("<!italic><dark_gray>" + type.role));
        lore.add(plugin.mm("<!italic><gray>" + type.description));
        lore.add(plugin.mm("<!italic><dark_gray>Paths: <gold>" + type.pathAName + "<dark_gray> / <gold>" + type.pathBName));
        lore.add(plugin.mm("<!italic><dark_gray>Evolves into <light_purple>" + type.evolutionName));
        if (!extraLore.isEmpty()) lore.add(Component.empty());
        for (String line : extraLore) lore.add(plugin.mm("<!italic>" + line));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack button(CosmicAbilitiesPlugin plugin, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.mm("<!italic>" + name));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(plugin.mm("<!italic>" + l));
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }
}
