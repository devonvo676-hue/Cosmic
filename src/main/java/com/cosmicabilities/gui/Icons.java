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

    /** Full description of an ability, used by both the info panel and the admin panel. */
    public static ItemStack ability(CosmicAbilitiesPlugin plugin, AbilityType type, List<String> extraLore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", type.id()));
        meta.displayName(plugin.mm("<!italic><bold>" + type.colored()));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.mm("<!italic><dark_gray>" + type.role + " <dark_gray>| <gray>Cooldown <white>"
                + (int) plugin.engine().cooldownSeconds(type, 1) + "s"));
        lore.add(Component.empty());
        for (String line : wrap(type.description, 38)) lore.add(plugin.mm("<!italic><gray>" + line));
        lore.add(Component.empty());
        lore.add(plugin.mm("<!italic><green>On cast: <white>" + plugin.engine().buffText(type)));
        lore.add(plugin.mm("<!italic><light_purple>" + type.bonusName + ":"));
        for (String line : wrap(type.bonusDesc, 38)) lore.add(plugin.mm("<!italic><white>  " + line));
        int pct = (int) Math.round(plugin.getConfig().getDouble("upgrades.cooldown-reduction", 0.3) * 100);
        lore.add(plugin.mm("<!italic><aqua>Upgrade: <white>" + pct + "% shorter cooldown <dark_gray>(uses " + type.material.displayName + "s)"));
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

    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
