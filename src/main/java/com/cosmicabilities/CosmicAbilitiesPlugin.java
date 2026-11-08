package com.cosmicabilities;

import com.cosmicabilities.ability.AbilityEngine;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.admin.RankTags;
import com.cosmicabilities.boss.BossManager;
import com.cosmicabilities.command.CosmicCommand;
import com.cosmicabilities.command.MiscCommands;
import com.cosmicabilities.data.DataManager;
import com.cosmicabilities.gui.AdminPanel;
import com.cosmicabilities.item.CosmicMaterial;
import com.cosmicabilities.listener.BossListener;
import com.cosmicabilities.listener.PlayerListener;
import com.cosmicabilities.util.Targets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CosmicAbilitiesPlugin extends JavaPlugin {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private DataManager dataManager;
    private AbilityEngine engine;
    private BossManager bossManager;
    private PlayerListener playerListener;
    private BanPistol banPistol;
    private AdminPanel adminPanel;
    private RankTags rankTags;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        CosmicMaterial.init(this);
        Targets.init(this);

        dataManager = new DataManager(this);
        engine = new AbilityEngine(this);
        bossManager = new BossManager(this);
        playerListener = new PlayerListener(this);
        banPistol = new BanPistol(this);
        adminPanel = new AdminPanel(this);
        rankTags = new RankTags(this);

        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(new BossListener(this), this);
        getServer().getPluginManager().registerEvents(banPistol, this);
        getServer().getPluginManager().registerEvents(adminPanel, this);

        CosmicCommand cmd = new CosmicCommand(this);
        PluginCommand cosmic = getCommand("cosmic");
        if (cosmic != null) {
            cosmic.setExecutor(cmd);
            cosmic.setTabCompleter(cmd);
        }
        MiscCommands misc = new MiscCommands(this);
        for (String name : new String[]{"ability", "adminpanel", "tag", "unban"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(misc);
                c.setTabCompleter(misc);
            }
        }

        // Players already online (e.g. after /reload or a plugin manager reload).
        for (Player p : getServer().getOnlinePlayers()) {
            dataManager.load(p.getUniqueId());
            rankTags.validate(p);
        }

        getServer().getScheduler().runTaskTimer(this, engine::tickHud, 20L, 10L);
        getServer().getScheduler().runTaskTimer(this, bossManager::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, engine::tickPassives, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, dataManager::saveDirty, 6000L, 6000L);

        getLogger().info("Cosmic Abilities enabled - 10 abilities, 5 bosses ready.");
    }

    @Override
    public void onDisable() {
        if (bossManager != null) bossManager.shutdown();
        if (dataManager != null) dataManager.saveAll();
    }

    public Component mm(String text) {
        return miniMessage.deserialize(text);
    }

    public String prefix() {
        return getConfig().getString("messages.prefix", "<dark_gray>[<light_purple>Cosmic<dark_gray>] ");
    }

    public DataManager data() { return dataManager; }
    public AbilityEngine engine() { return engine; }
    public BossManager bosses() { return bossManager; }
    public PlayerListener playerListener() { return playerListener; }
    public BanPistol banPistol() { return banPistol; }
    public AdminPanel adminPanel() { return adminPanel; }
    public RankTags tags() { return rankTags; }
}
