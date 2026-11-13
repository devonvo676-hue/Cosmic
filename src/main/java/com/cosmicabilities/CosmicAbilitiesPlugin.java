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
    private com.cosmicabilities.economy.Economy economy;
    private com.cosmicabilities.economy.Coinflip coinflip;
    private com.cosmicabilities.discord.DiscordBridge discord;
    private com.cosmicabilities.item.CosmicItems items;
    private com.cosmicabilities.boss.BossRig bossRig;
    private com.cosmicabilities.rank.Ranks ranks;
    private com.cosmicabilities.hud.Sidebar sidebar;
    private com.cosmicabilities.auction.AuctionHouse auctions;
    private com.cosmicabilities.team.Teams teams;
    private com.cosmicabilities.events.ServerEvents events;
    private com.cosmicabilities.supply.SupplyDrops supplyDrops;
    private com.cosmicabilities.admin.AdminRpg rpg;
    private com.cosmicabilities.admin.Cocaine cocaine;
    private com.cosmicabilities.stats.KillLog killLog;
    private com.cosmicabilities.gui.ConfigBrowser configBrowser;
    private com.cosmicabilities.admin.Suits suits;
    private com.cosmicabilities.admin.FighterJet jet;
    private com.cosmicabilities.admin.ArmorBay armorBay;
    private com.cosmicabilities.gui.Minimap minimap;
    private com.cosmicabilities.command.SpawnCommands spawnCommands;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // add any settings that are new in this version to an existing config.yml (keeps the server's own values)
        getConfig().options().copyDefaults(true);
        // 2.4: Paradox no longer gives its caster a speed boost (one-time fix for configs saved by older versions)
        if (!getConfig().getBoolean("migrations.paradox-no-speed", false)) {
            java.util.List<String> buffs = new java.util.ArrayList<>(getConfig().getStringList("cast-buffs.paradox"));
            buffs.removeIf(b -> b.trim().toLowerCase(java.util.Locale.ROOT).startsWith("speed"));
            if (buffs.isEmpty()) buffs.add("resistance:1:4");
            getConfig().set("cast-buffs.paradox", buffs);
            getConfig().set("migrations.paradox-no-speed", true);
        }
        // 2.5: the Cosmic Pickaxe and Axe are crafted with Cosmic Shards instead of amethyst shards
        if (!getConfig().getBoolean("migrations.tools-cosmic-shards", false)) {
            for (String id : new String[]{"cosmic_pickaxe", "cosmic_axe"}) {
                org.bukkit.configuration.ConfigurationSection ing = getConfig().getConfigurationSection("recipes." + id + ".ingredients");
                if (ing == null) continue;
                for (String k : ing.getKeys(false)) {
                    if ("AMETHYST_SHARD".equalsIgnoreCase(ing.getString(k))) getConfig().set("recipes." + id + ".ingredients." + k, "cosmic:cosmic_shard");
                }
            }
            getConfig().set("migrations.tools-cosmic-shards", true);
        }
        // 2.8: back to each ability's own cooldown (2.7 had set them all to 3 minutes). Still editable in /cconfig.
        if (!getConfig().getBoolean("migrations.cooldowns-restored", false)) {
            for (com.cosmicabilities.ability.AbilityType t : com.cosmicabilities.ability.AbilityType.values()) {
                getConfig().set("ability-settings." + t.id() + ".cooldown", t.baseCooldown);
            }
            getConfig().set("migrations.cooldowns-restored", true);
            getConfig().set("migrations.cooldowns-3min", true);
        }
        // 2.7 anti-one-shot nerf: all normal ability damage x0.8 (on top of the combo damage cap)
        if (!getConfig().getBoolean("migrations.damage-nerf-27", false)) {
            if (getConfig().getDouble("abilities.damage-multiplier", 1.0) >= 1.0) getConfig().set("abilities.damage-multiplier", 0.8);
            getConfig().set("migrations.damage-nerf-27", true);
        }
        // 2.8: every boss gets its own full set of values; the 2.7 global multipliers go back to 1
        if (!getConfig().getBoolean("migrations.boss-values-28", false)) {
            org.bukkit.configuration.file.YamlConfiguration defs = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(java.util.Objects.requireNonNull(getResource("config.yml")), java.nio.charset.StandardCharsets.UTF_8));
            for (com.cosmicabilities.boss.BossType t : com.cosmicabilities.boss.BossType.values()) {
                org.bukkit.configuration.ConfigurationSection sec = defs.getConfigurationSection("bosses." + t.id());
                if (sec != null) for (String k : sec.getKeys(true)) if (!sec.isConfigurationSection(k)) getConfig().set("bosses." + t.id() + "." + k, sec.get(k));
            }
            for (String k : new String[]{"health-multiplier", "damage-multiplier", "size-multiplier"}) getConfig().set("bosses." + k, 1.0);
            getConfig().set("migrations.boss-values-28", true);
        }
        // 2.9: boss size is now a multiplier (1 = normal, hitbox follows the model); drop the old tab list slogan
        if (!getConfig().getBoolean("migrations.boss-size-29", false)) {
            for (com.cosmicabilities.boss.BossType t : com.cosmicabilities.boss.BossType.values()) getConfig().set("bosses." + t.id() + ".size", 1.0);
            getConfig().set("bosses.size-multiplier", 1.0);
            if ("<gray>Money SMP with cosmic abilities".equals(getConfig().getString("tablist.header-line"))) getConfig().set("tablist.header-line", "");
            getConfig().set("migrations.boss-size-29", true);
        }
        // 3.0: no upgrade tokens in supply drops; combo limit off by default (explained in /cconfig)
        if (!getConfig().getBoolean("migrations.v30", false)) {
            java.util.List<String> loot = new java.util.ArrayList<>(getConfig().getStringList("supply-drops.loot"));
            loot.removeIf(l -> l.toLowerCase(java.util.Locale.ROOT).contains("upgrade_token"));
            getConfig().set("supply-drops.loot", loot);
            getConfig().set("abilities.combo-damage-cap", 0);
            getConfig().set("owner-items", null);   // v2tou is built in now
            getConfig().set("migrations.v30", true);
        }
        // 3.4: time freeze became slow motion with no recharge after it ends
        if (!getConfig().getBoolean("migrations.v34", false)) {
            getConfig().set("ability-settings.lightspeed.freeze-cooldown-seconds", 0);
            getConfig().set("ability-settings.lightspeed.slowmo-speed", 0.2);
            getConfig().set("migrations.v34", true);
        }
        // 2.6: no more daily login cash - players earn cash from kills instead
        if (!getConfig().getBoolean("migrations.no-daily-cash", false)) {
            getConfig().set("economy.daily-reward", 0);
            getConfig().set("migrations.no-daily-cash", true);
        }
        saveConfig();
        CosmicMaterial.init(this);
        Targets.init(this);
        Fx.init(this);

        dataManager = new DataManager(this);
        teams = new com.cosmicabilities.team.Teams(this);
        events = new com.cosmicabilities.events.ServerEvents(this);
        supplyDrops = new com.cosmicabilities.supply.SupplyDrops(this);
        rpg = new com.cosmicabilities.admin.AdminRpg(this);
        cocaine = new com.cosmicabilities.admin.Cocaine(this);
        killLog = new com.cosmicabilities.stats.KillLog(this);
        configBrowser = new com.cosmicabilities.gui.ConfigBrowser(this);
        suits = new com.cosmicabilities.admin.Suits(this);
        jet = new com.cosmicabilities.admin.FighterJet(this);
        armorBay = new com.cosmicabilities.admin.ArmorBay(this);
        minimap = new com.cosmicabilities.gui.Minimap(this);
        spawnCommands = new com.cosmicabilities.command.SpawnCommands(this);
        status = new StatusManager(this);
        combat = new CombatTracker();
        engine = new AbilityEngine(this);
        bossManager = new BossManager(this);
        bossFightPanel = new BossFightPanel(this);
        holdersPanel = new HoldersPanel(this);
        keybindPanel = new KeybindPanel(this);
        orbs = new AbilityOrbs(this);
        items = new com.cosmicabilities.item.CosmicItems(this);
        economy = new com.cosmicabilities.economy.Economy(this);
        coinflip = new com.cosmicabilities.economy.Coinflip(this);
        discord = new com.cosmicabilities.discord.DiscordBridge(this);
        bossRig = new com.cosmicabilities.boss.BossRig(this);
        sidebar = new com.cosmicabilities.hud.Sidebar(this);
        ranks = new com.cosmicabilities.rank.Ranks(this);
        auctions = new com.cosmicabilities.auction.AuctionHouse(this);
        com.cosmicabilities.admin.BanPistol.opOnly = getConfig().getBoolean("admin.op-only", true);
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
                banPistol, adminPanel, abilitiesPanel, cutscenes, afk, afkShop, tools, recipes, roll, adminHub, menu, items, economy, coinflip, discord, ranks, sidebar, auctions, teams, rpg, cocaine, supplyDrops, killLog, configBrowser, suits, jet, armorBay, minimap}) {
            getServer().getPluginManager().registerEvents(l, this);
        }

        CosmicCommand cmd = new CosmicCommand(this);
        PluginCommand cosmic = getCommand("cosmic");
        if (cosmic != null) {
            cosmic.setExecutor(cmd);
            cosmic.setTabCompleter(cmd);
        }
        for (String name : new String[]{"balance", "eco"}) bind(name, economy, economy);
        bind("cf", coinflip, coinflip);
        bind("ah", auctions, auctions);
        bind("team", teams, teams);
        bind("leaderboard", teams, teams);
        bind("endseason", teams, teams);
        for (String c : new String[]{"spawn", "setspawn", "resetcooldown"}) {
            PluginCommand pc = getCommand(c);
            if (pc != null) pc.setExecutor(spawnCommands);
        }
        PluginCommand mmc = getCommand("minimap");
        if (mmc != null) mmc.setExecutor(minimap);
        PluginCommand klc = getCommand("killlog");
        if (klc != null) klc.setExecutor(killLog);
        PluginCommand supplyCmd = getCommand("supplydrop");
        if (supplyCmd != null) supplyCmd.setExecutor(supplyDrops);
        bind("cosmicrank", ranks, ranks);
        PluginCommand sidebarCmd = getCommand("sidebar");
        if (sidebarCmd != null) sidebarCmd.setExecutor(sidebar);
        bind("discordbridge", discord, discord);
        PluginCommand recipesCmd = getCommand("recipes");
        if (recipesCmd != null) recipesCmd.setExecutor((s, c, l, a) -> {
            if (s instanceof Player p) menu.openItems(p);
            return true;
        });
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
        getServer().getScheduler().runTaskTimer(this, bossRig::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, sidebar::tick, 40L, 20L);
        getServer().getScheduler().runTaskTimer(this, auctions::expire, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, events::tick, 20L, 20L);
        getServer().getScheduler().runTask(this, armorBay::rebuildAll);
        getServer().getScheduler().runTaskTimer(this, teams::tick, 20L, 20L);
        for (Player online : getServer().getOnlinePlayers()) rankTags.refresh(online);
        getServer().getScheduler().runTaskTimer(this, engine::tickHud, 20L, 10L);
        getServer().getScheduler().runTaskTimer(this, bossManager::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, afk::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, dataManager::saveDirty, 6000L, 6000L);

        getLogger().info("Cosmic Abilities " + getPluginMeta().getVersion() + " enabled - 20 abilities, 5 bosses, teams, ability orbs, cosmic tools, AFK shop.");
    }

    @Override
    public void onDisable() {
        if (roll != null) roll.shutdown();
        if (coinflip != null) coinflip.shutdown();
        if (ranks != null) ranks.saveOpen();
        if (auctions != null) auctions.save();
        if (teams != null) teams.save();
        if (killLog != null) killLog.save();
        if (events != null) events.shutdown();
        if (cocaine != null) cocaine.shutdown();
        if (engine != null) engine.shutdownAbilities();
        if (jet != null) jet.shutdown();
        if (armorBay != null) armorBay.shutdown();
        if (minimap != null) minimap.saveCache();
        if (suits != null) suits.shutdown();
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
        com.cosmicabilities.admin.BanPistol.opOnly = getConfig().getBoolean("admin.op-only", true);
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
    public com.cosmicabilities.economy.Economy economy() { return economy; }
    public com.cosmicabilities.economy.Coinflip coinflip() { return coinflip; }
    public com.cosmicabilities.discord.DiscordBridge discord() { return discord; }
    public com.cosmicabilities.item.CosmicItems items() { return items; }
    public com.cosmicabilities.boss.BossRig bossRig() { return bossRig; }
    public com.cosmicabilities.rank.Ranks ranks() { return ranks; }
    public com.cosmicabilities.hud.Sidebar sidebar() { return sidebar; }
    public com.cosmicabilities.auction.AuctionHouse auctions() { return auctions; }
    public com.cosmicabilities.team.Teams teams() { return teams; }
    public com.cosmicabilities.events.ServerEvents events() { return events; }
    public com.cosmicabilities.supply.SupplyDrops supplyDrops() { return supplyDrops; }
    public com.cosmicabilities.admin.AdminRpg rpg() { return rpg; }
    public com.cosmicabilities.admin.Cocaine cocaine() { return cocaine; }
    public com.cosmicabilities.stats.KillLog killLog() { return killLog; }
    public com.cosmicabilities.gui.ConfigBrowser configBrowser() { return configBrowser; }
    public com.cosmicabilities.admin.Suits suits() { return suits; }
    public com.cosmicabilities.admin.FighterJet jet() { return jet; }
    public com.cosmicabilities.admin.ArmorBay armorBay() { return armorBay; }
    public com.cosmicabilities.gui.Minimap minimap() { return minimap; }
    public com.cosmicabilities.command.SpawnCommands spawnCommands() { return spawnCommands; }

    private void bind(String name, org.bukkit.command.CommandExecutor exec, org.bukkit.command.TabCompleter tab) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(exec);
            c.setTabCompleter(tab);
        }
    }
}
