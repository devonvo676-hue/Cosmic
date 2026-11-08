package com.cosmicabilities.data;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Stores each player in plugins/CosmicAbilities/players/&lt;uuid&gt;.yml */
public class DataManager {
    private final CosmicAbilitiesPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DataManager(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create players folder");
        }
    }

    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    /** Loads (or creates) a player's data and caches it. */
    public PlayerData load(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;
        PlayerData data = new PlayerData(uuid);
        File file = file(uuid);
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            if (yml.getString("slots.1.ability") == null && yml.getString("ability") != null) {
                // Migrate the old single-ability format into slot 1.
                readSlot(data.primary(), yml.getString("ability"), yml.getInt("level", 1), yml.getString("path", ""));
            } else {
                for (int i = 1; i <= PlayerData.SLOTS; i++) {
                    readSlot(data.slot(i), yml.getString("slots." + i + ".ability"),
                            yml.getInt("slots." + i + ".level", 1), yml.getString("slots." + i + ".path", ""));
                }
            }
            data.setMastery(yml.getInt("mastery", 0));
            data.markClean();
        }
        cache.put(uuid, data);
        return data;
    }

    /** Loads data for an offline player without caching it permanently. */
    public PlayerData loadOffline(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;
        PlayerData data = load(uuid);
        cache.remove(uuid);
        return data;
    }

    private static void readSlot(AbilitySlot slot, String ability, int level, String path) {
        AbilityType type = AbilityType.fromId(ability);
        if (type == null) return;
        slot.equip(type, level);
        if (path != null && !path.isEmpty()) slot.setPath(Character.toUpperCase(path.charAt(0)));
    }

    public void save(PlayerData data) {
        YamlConfiguration yml = new YamlConfiguration();
        for (int i = 1; i <= PlayerData.SLOTS; i++) {
            AbilitySlot s = data.slot(i);
            String base = "slots." + i + ".";
            yml.set(base + "ability", s.isEmpty() ? null : s.ability().id());
            yml.set(base + "level", s.level());
            yml.set(base + "path", s.path() == 0 ? "" : String.valueOf(s.path()));
        }
        yml.set("mastery", data.mastery());
        try {
            yml.save(file(data.uuid()));
            data.markClean();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save data for " + data.uuid(), e);
        }
    }

    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null) save(data);
    }

    public void saveDirty() {
        for (PlayerData data : cache.values()) if (data.dirty()) save(data);
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) save(data);
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }
}
