package com.cosmicabilities;

import com.cosmicabilities.ability.AbilityEngine;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.admin.RankTags;
import com.cosmicabilities.afk.AfkManager;
import com.cosmicabilities.afk.AfkShop;
import com.cosmicabilities.boss.BossManager;
import com.cosmicabilities.command.CosmicCommand;
import com.cosmicabilities.command.MiscCommands;
import com.cosmicabilities.cutscene.CutsceneManager;
import com.cosmicabilities.data.DataManager;
import com.cosmicabilities.fx.Fx;
import com.cosmicabilities.gui.AbilitiesPanel;
import com.cosmicabilities.gui.AdminPanel;
import com.cosmicabilities.gui.ConfigPanel;
import com.cosmicabilities.item.CosmicMaterial;
import com.cosmicabilities.listener.BossListener;
import com.cosmicabilities.listener.PlayerListener;
import com.cosmicabilities.boss.BossFightPanel;
import com.cosmicabilities.gui.HoldersPanel;
import com.cosmicabilities.keybind.KeybindListener;
import com.cosmicabilities.keybind.KeybindPanel;
import com.cosmicabilities.orb.AbilityOrbs;
import com.cosmicabilities.status.CombatTracker;
import com.cosmicabilities.status.StatusListener;
import com.cosmicabilities.status.StatusManager;
import com.cosmicabilities.util.Targets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class CosmicAbilitiesPlugin extends JavaPlugin {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private DataManager dataManager;
    private AbilityEngine engine;
    private StatusManager status;
    private CombatTracker combat;
    private BossManager bossManager;
    private BossFightPanel bossFightPanel;
    private HoldersPanel holdersPanel;
    private KeybindPanel keybindPanel;
    private AbilityOrbs orbs;
    private ConfigPanel configPanel;
    private PlayerListener playerListener;
    private BanPistol banPistol;
    private AdminPanel adminPanel;
    private AbilitiesPanel abilitiesPanel;
    private RankTags rankTags;
    private CutsceneManager cutscenes;
    private AfkManager afk;
    private AfkShop afkShop;
    private com.cosmicabilities.item.CosmicTools tools;
    private com.cosmicabilities.recipe.RecipeManager recipes;
    private com.cosmicabilities.gui.RollPanel roll;
    private com.cosmicabilities.gui.AdminHub adminHub;
    private com.cosmicabilities.gui.MainMenu menu;
    private MiscCommands miscCommands;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // add any settings that are new in this version to an existing config.yml (keeps the server's own values)
        getConfig().options().copyDefaults(true);
        saveConfig();
        CosmicMaterial.init(this);
        Targets.init(this);
        Fx.init(this);

        dataManager = new DataManager(this);
        status = new StatusManager(this);
        combat = new CombatTracker();
        engine = new AbilityEngine(this);
        bossManager = new BossManager(this);
        bossFightPanel = new BossFightPanel(this);
        holdersPanel = new HoldersPanel(this);
        keybindPanel = new KeybindPanel(this);
        orbs = new AbilityOrbs(this);
        tools = new com.cosmicabilities.item.CosmicTools(this);
        recipes = new com.cosmicabilities.recipe.RecipeManager(this);
        recipes.registerAll();
        roll = new com.cosmicabilities.gui.RollPanel(this);
        adminHub = new com.cosmicabilities.gui.AdminHub(this);
        menu = new com.cosmicabilities.gui.MainMenu(this);
        configPanel = new ConfigPanel(this);
        playerListener = new PlayerListener(this);
        banPistol = new BanPistol(this);
        adminPanel = new AdminPanel(this);
        abilitiesPanel = new AbilitiesPanel(this);
        rankTags = new RankTags(this);
        cutscenes = new CutsceneManager(this);
        afk = new AfkManager(this);
        afkShop = new AfkShop(this);

        for (Listener l : new Listener[]{playerListener, new BossListener(this), new StatusListener(this), combat,
                new KeybindListener(this), bossFightPanel, holdersPanel, keybindPanel, orbs, configPanel,
                banPistol, adminPanel, abilitiesPanel, cutscenes, afk, afkShop, tools, recipes, roll, adminHub, menu}) {
            getServer().getPluginManager().registerEvents(l, this);
        }

        CosmicCommand cmd = new CosmicCommand(this);
        PluginCommand cosmic = getCommand("cosmic");
        if (cosmic != null) {
            cosmic.setExecutor(cmd);
            cosmic.setTabCompleter(cmd);
        }
        MiscCommands misc = new MiscCommands(this);
        miscCommands = misc;
        for (String name : new String[]{"ability", "abilities", "abilityholders", "keybinds", "bossfight", "creload", "cconfig", "trust", "untrust", "adminpanel", "tag", "unban", "announce", "afk", "afkshop"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(misc);
                c.setTabCompleter(misc);
            }
        }

        for (Player p : getServer().getOnlinePlayers()) {
            dataManager.load(p.getUniqueId());
            rankTags.validate(p);
            engine.stripOldPassives(p);
        }

        // remove any effect entities left behind by a crash/restart, then keep sweeping strays
        Fx.sweep(true);
        getServer().getScheduler().runTaskTimer(this, () -> Fx.sweep(false), 200L, 200L);
        getServer().getScheduler().runTaskTimer(this, status::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, engine::tickHud, 20L, 10L);
        getServer().getScheduler().runTaskTimer(this, bossManager::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, afk::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, dataManager::saveDirty, 6000L, 6000L);

        getLogger().info("Cosmic Abilities " + getPluginMeta().getVersion() + " enabled - 14 abilities, 5 bosses, ability orbs, cosmic tools, AFK shop.");
    }

    @Override
    public void onDisable() {
        if (roll != null) roll.shutdown();
        if (cutscenes != null) cutscenes.shutdown();
        if (status != null) status.shutdown();
        if (bossManager != null) bossManager.shutdown();
        Fx.sweep(true);
        if (dataManager != null) dataManager.saveAll();
    }

    /** /creload: re-read config.yml and afkshop.yml from disk and apply everything immediately. */
    public void reloadAll() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        afkShop.reload();
        applyConfig();
    }

    /** Re-applies settings that aren't read live (recipe on/off, name tag style). */
    public void applyConfig() {
        recipes.registerAll();
        for (Player p : getServer().getOnlinePlayers()) rankTags.validate(p);
    }

    public Component mm(String text) {
        return miniMessage.deserialize(text);
    }

    public String prefix() {
        return getConfig().getString("messages.prefix", "<dark_gray>[<light_purple>Cosmic<dark_gray>] ");
    }

    public DataManager data() { return dataManager; }
    public AbilityEngine engine() { return engine; }
    public StatusManager status() { return status; }
    public CombatTracker combat() { return combat; }
    public BossManager bosses() { return bossManager; }
    public BossFightPanel bossFightPanel() { return bossFightPanel; }
    public HoldersPanel holdersPanel() { return holdersPanel; }
    public KeybindPanel keybindPanel() { return keybindPanel; }
    public AbilityOrbs orbs() { return orbs; }
    public ConfigPanel configPanel() { return configPanel; }
    public PlayerListener playerListener() { return playerListener; }
    public BanPistol banPistol() { return banPistol; }
    public AdminPanel adminPanel() { return adminPanel; }
    public AbilitiesPanel abilitiesPanel() { return abilitiesPanel; }
    public RankTags tags() { return rankTags; }
    public CutsceneManager cutscenes() { return cutscenes; }
    public AfkManager afk() { return afk; }
    public AfkShop afkShop() { return afkShop; }
    public com.cosmicabilities.item.CosmicTools tools() { return tools; }
    public com.cosmicabilities.recipe.RecipeManager recipes() { return recipes; }
    public com.cosmicabilities.gui.RollPanel roll() { return roll; }
    public com.cosmicabilities.gui.AdminHub adminHub() { return adminHub; }
    public com.cosmicabilities.gui.MainMenu menu() { return menu; }
    public MiscCommands miscCommands() { return miscCommands; }
}
