package com.cosmicabilities.data;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.keybind.Keybind;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Stores each player in plugins/CosmicAbilities/players/&lt;uuid&gt;.yml */
public class DataManager {
    private static final int FORMAT = 2;

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

    public PlayerData load(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;
        PlayerData data = new PlayerData(uuid);
        File file = file(uuid);
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            int format = yml.getInt("format", 1);
            if (format < 2 && yml.getString("slots.1.ability") == null && yml.getString("ability") != null) {
                // v1.0 single-ability file
                readSlot(data.slot(1), yml.getString("ability"), oldLevelToTier(yml.getInt("level", 1)));
            } else {
                for (int i = 1; i <= PlayerData.SLOTS; i++) {
                    int raw = yml.getInt("slots." + i + ".level", yml.getInt("slots." + i + ".tier", 1));
                    int tier = format < 2 ? oldLevelToTier(raw) : raw;
                    readSlot(data.slot(i), yml.getString("slots." + i + ".ability"), tier);
                }
            }
            for (int i = 1; i <= PlayerData.SLOTS; i++) {
                data.setBind(i, Keybind.parse(yml.getString("binds." + i), data.bind(i)));
            }
            AbilityType lost = AbilityType.fromId(yml.getString("lost.ability"));
            if (lost != null) data.setLost(lost, yml.getInt("lost.tier", 1));
            data.setAfkTokens(yml.getInt("afk.tokens", 0));
            data.setAfkProgress(yml.getInt("afk.progress", 0));
            data.markClean();
        }
        cache.put(uuid, data);
        return data;
    }

    /** v1 used levels 1-10: level 5+ counts as upgraded. */
    private static int oldLevelToTier(int level) {
        return level >= 5 ? 2 : 1;
    }

    public PlayerData loadOffline(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;
        PlayerData data = load(uuid);
        cache.remove(uuid);
        return data;
    }

    private static void readSlot(AbilitySlot slot, String ability, int tier) {
        AbilityType type = AbilityType.fromId(ability);
        if (type != null) slot.equip(type, tier);
    }

    public void save(PlayerData data) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("format", FORMAT);
        for (int i = 1; i <= PlayerData.SLOTS; i++) {
            AbilitySlot s = data.slot(i);
            yml.set("slots." + i + ".ability", s.isEmpty() ? null : s.ability().id());
            yml.set("slots." + i + ".tier", s.tier());
        }
        for (int i = 1; i <= PlayerData.SLOTS; i++) yml.set("binds." + i, data.bind(i).name());
        yml.set("lost.ability", data.lostAbility() == null ? null : data.lostAbility().id());
        yml.set("lost.tier", data.lostTier());
        yml.set("afk.tokens", data.afkTokens());
        yml.set("afk.progress", data.afkProgress());
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
