package com.cosmicabilities.boss;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.gui.Icons;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * /bossfight (admins): start a server-wide boss fight. The whole server gets a countdown, then the boss arrives
 * where the admin is standing. Every boss drops a random ability orb when it dies.
 */
public class BossFightPanel implements Listener {
    private static final int[] BOSS_SLOTS = {11, 12, 13, 14, 15};
    private static final int COUNTDOWN_TOGGLE = 29, PLAYERS = 31, DESPAWN = 33, CLOSE = 40;
    private static final int[] COUNTDOWNS = {0, 10, 30, 60};

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private int countdownIndex = 1;
    private int requiredPlayers;

    public BossFightPanel(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        int configured = plugin.getConfig().getInt("bossfight.countdown-seconds", 10);
        for (int i = 0; i < COUNTDOWNS.length; i++) if (COUNTDOWNS[i] == configured) countdownIndex = i;
        requiredPlayers = Math.max(1, plugin.getConfig().getInt("bosses.min-players-to-damage", 3));
    }

    public void open(Player admin) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 45, plugin.mm("<dark_red><bold>Start a Boss Fight"));
        holder.inventory = inv;
        render(inv);
        admin.openInventory(inv);
    }

    private void render(Inventory inv) {
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        int min = requiredPlayers;
        BossType[] types = BossType.values();
        for (int i = 0; i < types.length; i++) {
            BossType b = types[i];
            inv.setItem(BOSS_SLOTS[i], Icons.button(plugin, Material.WITHER_SKELETON_SKULL, b.colored(),
                    "<gray>Health <white>" + (int) plugin.getConfig().getDouble("bosses." + b.id() + ".health", 500),
                    "<gray>Drops " + b.drop.colored() + " <gray>+ a random ability orb",
                    "<gray>Starts when <white>" + min + " player" + (min == 1 ? "" : "s") + " <gray>are there",
                    "",
                    "<yellow>Click <gray>to start the fight at your location"));
        }
        int cd = COUNTDOWNS[countdownIndex];
        inv.setItem(COUNTDOWN_TOGGLE, Icons.button(plugin, Material.CLOCK, "<aqua>Countdown: <white>" + (cd == 0 ? "none" : cd + "s"),
                "<gray>Click to change. Everyone sees the countdown."));
        inv.setItem(PLAYERS, Icons.button(plugin, Material.PLAYER_HEAD, "<gold>Players needed: <white>" + requiredPlayers,
                "<gray>The fight waits until this many players", "<gray>are near the spot, then starts.",
                "<gray>The boss also can't be hurt with fewer.", "",
                "<yellow>Left-click <gray>+1   <yellow>Right-click <gray>-1   <yellow>Shift <gray>x5"));
        inv.setItem(DESPAWN, Icons.gui(plugin, "despawn", "<red>End all boss fights", "<gray>Removes every active boss (no drops)."));
        inv.setItem(CLOSE, Icons.button(plugin, Material.OAK_DOOR, "<gray>Close"));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin) || !BanPistol.isStaff(admin)) return;
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof Holder)) return;
        int raw = event.getRawSlot();
        for (int i = 0; i < BOSS_SLOTS.length; i++) {
            if (raw == BOSS_SLOTS[i]) {
                admin.closeInventory();
                start(BossType.values()[i], admin.getLocation(), COUNTDOWNS[countdownIndex], requiredPlayers, admin.getName());
                return;
            }
        }
        switch (raw) {
            case PLAYERS -> {
                int step = event.getClick().isShiftClick() ? 5 : 1;
                requiredPlayers = Math.max(1, Math.min(100, requiredPlayers + (event.getClick().isRightClick() ? -step : step)));
                render(event.getInventory());
            }
            case COUNTDOWN_TOGGLE -> {
                countdownIndex = (countdownIndex + 1) % COUNTDOWNS.length;
                render(event.getInventory());
            }
            case DESPAWN -> {
                int n = plugin.bosses().despawnAll();
                admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>Removed <white>" + n + " <gray>boss(es)."));
                admin.closeInventory();
            }
            case CLOSE -> admin.closeInventory();
            default -> { }
        }
    }

    /** Kept for the admin panel: uses the config default player requirement. */
    public void start(BossType type, Location where, int seconds, String by) {
        start(type, where, seconds, Math.max(1, plugin.getConfig().getInt("bosses.min-players-to-damage", 3)), by);
    }

    /**
     * Announces the fight, waits until `required` players are near the spot (or times out), then runs the
     * countdown and the boss descends.
     */
    public void start(BossType type, Location where, int seconds, int required, String by) {
        Location at = where.clone();
        double radius = plugin.getConfig().getDouble("bossfight.gather-radius", 40);
        int timeout = plugin.getConfig().getInt("bossfight.wait-timeout-seconds", 300);
        Bukkit.broadcast(plugin.mm(plugin.prefix() + "<red><bold>BOSS FIGHT!</bold> " + type.colored() + " <gray>at <white>"
                + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ() + " <gray>(" + at.getWorld().getName() + ")"
                + "<gray>. Needs <white>" + required + " player" + (required == 1 ? "" : "s") + " <gray>there to begin. Started by " + by + "."));
        new BukkitRunnable() {
            int waited = 0;
            @Override
            public void run() {
                int here = 0;
                for (Player p : at.getWorld().getPlayers()) {
                    if (!p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                            && p.getLocation().distanceSquared(at) <= radius * radius) here++;
                }
                if (here >= required) {
                    cancel();
                    countdown(type, at, seconds, required);
                    return;
                }
                if (waited >= timeout) {
                    cancel();
                    Bukkit.broadcast(plugin.mm(plugin.prefix() + "<gray>The " + type.colored()
                            + " <gray>fight was cancelled - not enough players showed up."));
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendActionBar(plugin.mm("<red><bold>BOSS FIGHT</bold> " + type.colored() + " <gray>waiting for players: <white>"
                            + here + "/" + required + " <gray>at " + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ()));
                }
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 3, 0), 30, 0.2, 3, 0.2, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(Integer.parseInt(type.color.substring(1), 16)), 1.4f));
                waited++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** Server-wide countdown, then the boss descends in a column of light. */
    private void countdown(BossType type, Location at, int seconds, int required) {
        new BukkitRunnable() {
            int left = seconds;
            @Override
            public void run() {
                if (left <= 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(Title.title(plugin.mm(type.colored()), plugin.mm("<red>has arrived!"),
                                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
                    }
                    at.getWorld().strikeLightningEffect(at);
                    at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 2, 0), 80, 0.5, 3, 0.5, 0.05);
                    plugin.bosses().spawn(type, at, required);
                    cancel();
                    return;
                }
                if (left == seconds || left <= 5 || left % 10 == 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(Title.title(plugin.mm("<red><bold>BOSS FIGHT"),
                                plugin.mm(type.colored() + " <gray>in <white>" + left + "s"),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200))));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, left <= 3 ? 1.4f : 0.8f);
                    }
                }
                // a beam of the boss's colour marks the arrival point
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 3, 0), 40, 0.2, 3, 0.2, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(Integer.parseInt(type.color.substring(1), 16)), 1.6f));
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}
