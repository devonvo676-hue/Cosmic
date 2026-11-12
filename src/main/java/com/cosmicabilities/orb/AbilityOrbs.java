package com.cosmicabilities.orb;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import com.cosmicabilities.fx.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ability orbs and the Cosmic Reroll.
 *  - Bosses drop a random ability orb.
 *  - When a player is killed, their GAINED ability (slot 2) drops as an orb only the killer can pick up at first.
 *    The ability they joined with (slot 1) is never lost.
 *  - Right-click an orb to absorb it into slot 2 (a gained ability already there is dropped as an orb).
 *  - Cosmic Reroll (craftable): right-click to spin for a new slot-1 ability. Announced to everyone.
 */
public class AbilityOrbs implements Listener {
    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey orbKey, orbTierKey, rerollKey;

    public AbilityOrbs(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.orbKey = new NamespacedKey(plugin, "ability_orb");
        this.orbTierKey = new NamespacedKey(plugin, "ability_orb_tier");
        this.rerollKey = new NamespacedKey(plugin, "cosmic_reroll");
    }

    // ------------------------------------------------------------------ items

    public ItemStack orb(AbilityType type, int tier) {
        ItemStack it = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", type.id()));
        meta.displayName(plugin.mm("<!italic><bold>" + type.colored() + " Orb"));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.mm("<!italic><dark_gray>" + type.role + (tier >= 2 ? " <dark_gray>| <aqua>Upgraded" : "")));
        lore.add(plugin.mm("<!italic><gray>" + type.description));
        lore.add(Component.empty());
        lore.add(plugin.mm("<!italic><yellow>Right-click to absorb this ability"));
        lore.add(plugin.mm("<!italic><dark_gray>Goes into your gained slot (slot 2)"));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(orbKey, PersistentDataType.STRING, type.id());
        meta.getPersistentDataContainer().set(orbTierKey, PersistentDataType.INTEGER, tier);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack reroll() {
        ItemStack it = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("cosmic", "reroll"));
        meta.displayName(plugin.mm("<!italic><gradient:#f72585:#4cc9f0><bold>Cosmic Reroll"));
        meta.lore(List.of(
                plugin.mm("<!italic><gray>Right-click to spin the cosmic wheel"),
                plugin.mm("<!italic><gray>and change the ability you joined with"),
                plugin.mm("<!italic><gray>(slot 1) to a new random one."),
                Component.empty(),
                plugin.mm("<!italic><dark_gray>The whole server will know.")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(rerollKey, PersistentDataType.STRING, "1");
        it.setItemMeta(meta);
        return it;
    }

    private AbilityType orbType(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return AbilityType.fromId(it.getItemMeta().getPersistentDataContainer().get(orbKey, PersistentDataType.STRING));
    }

    private int orbTier(ItemStack it) {
        Integer t = it.getItemMeta().getPersistentDataContainer().get(orbTierKey, PersistentDataType.INTEGER);
        return t == null ? 1 : t;
    }

    public boolean isReroll(ItemStack it) {
        return it != null && !it.getType().isAir() && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().get(rerollKey, PersistentDataType.STRING) != null;
    }

    // ------------------------------------------------------------------ drops

    /** Drops a glowing orb that never despawns. If owner is set, only they can pick it up for a while. */
    public Item drop(Location at, AbilityType type, int tier, UUID owner) {
        World w = at.getWorld();
        Item item = w.dropItem(at.clone().add(0, 0.5, 0), orb(type, tier));
        item.setUnlimitedLifetime(true);
        item.setInvulnerable(true);
        item.setGlowing(true);
        item.setVelocity(item.getVelocity().multiply(0.3).setY(0.3));
        if (owner != null) {
            item.setOwner(owner);
            int seconds = plugin.getConfig().getInt("ability-drops.killer-only-seconds", 60);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (item.isValid()) item.setOwner(null); }, seconds * 20L);
        }
        w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.08);
        w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0, Fx.dust(type.rgb(), 1.5f));
        w.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.8f);
        return item;
    }

    /** Called when a boss dies: a random ability orb for whoever grabs it first. */
    /** The boss's own ability (or, if the ability-chance roll fails, a random one) goes to whoever did the most damage. */
    public void bossDrop(Location at, String bossName, String bossId, UUID top, double exclusiveChance) {
        if (!plugin.getConfig().getBoolean("ability-drops.from-bosses", true)) return;
        AbilityType exclusive = AbilityType.forBoss(bossId);
        AbilityType type;
        if (exclusive != null && ThreadLocalRandom.current().nextDouble() < exclusiveChance) {
            type = exclusive;
        } else {
            java.util.List<AbilityType> all = AbilityType.rollable();
            type = all.get(ThreadLocalRandom.current().nextInt(all.size()));
        }
        award(top, type, at, bossName);
    }

    /** Gives an ability orb straight to the top damage dealer (dropped at their feet if their inventory is full). */
    public void award(UUID top, AbilityType type, Location fallback, String sourceName) {
        Player p = top == null ? null : Bukkit.getPlayer(top);
        String label = type.exclusive() ? (type.source() == AbilityType.Source.DRAGON ? "<gold><bold>LEGENDARY</bold> " : "<gold><bold>BOSS ABILITY</bold> ") : "";
        if (p == null) {
            drop(fallback, type, 1, top);
            Bukkit.broadcast(plugin.mm(plugin.prefix() + sourceName + " <gray>dropped " + label + type.colored() + "<gray>!"));
            return;
        }
        for (ItemStack left : p.getInventory().addItem(orb(type, 1)).values()) drop(p.getLocation(), type, 1, p.getUniqueId());
        p.showTitle(Title.title(plugin.mm("<gold><bold>TOP DAMAGE!"), plugin.mm("<gray>You won " + label + type.colored()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(700))));
        for (Player o : Bukkit.getOnlinePlayers()) o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.9f);
        Bukkit.broadcast(plugin.mm(plugin.prefix() + "<white>" + p.getName() + " <gray>did the most damage to " + sourceName
                + " <gray>and won " + label + type.colored() + "<gray>!"));
    }

    // ------------------------------------------------------------------ Ender Dragon: top damage dealer wins Dragon's Awakening

    private final java.util.Map<UUID, java.util.Map<UUID, Double>> dragonDamage = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonHit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        org.bukkit.entity.Entity hit = event.getEntity();
        if (hit instanceof org.bukkit.entity.ComplexEntityPart part) hit = part.getParent();
        if (!(hit instanceof org.bukkit.entity.EnderDragon dragon)) return;
        org.bukkit.entity.Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player shooter) damager = shooter;
        if (damager instanceof org.bukkit.entity.TNTPrimed tnt && tnt.getSource() instanceof Player src) damager = src;
        if (!(damager instanceof Player player)) return;
        dragonDamage.computeIfAbsent(dragon.getUniqueId(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                .merge(player.getUniqueId(), event.getFinalDamage(), Double::sum);
    }

    @EventHandler
    public void onDragonDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.EnderDragon dragon)) return;
        java.util.Map<UUID, Double> dmg = dragonDamage.remove(dragon.getUniqueId());
        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("ability-drops.dragon-chance", 1.0)) return;
        UUID top = dmg == null ? null : dmg.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);
        if (top == null && dragon.getKiller() != null) top = dragon.getKiller().getUniqueId();
        World w = dragon.getWorld();
        Location at = w.getEnvironment() == World.Environment.THE_END ? new Location(w, 0.5, w.getHighestBlockYAt(0, 0) + 1.5, 0.5) : dragon.getLocation();
        final UUID winner = top;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> award(winner, AbilityType.DRAGON_AWAKENING, at,
                "<dark_purple><bold>the Ender Dragon</bold>"), 200L);
    }

    /** Called on player death. Returns a message describing what was lost, or null. */
    public String handleDeath(Player victim) {
        PlayerData d = plugin.data().get(victim.getUniqueId());
        if (d == null || d.gained().isEmpty()) return null;
        Player killer = victim.getKiller();
        boolean anyDeath = plugin.getConfig().getBoolean("ability-drops.lose-on-any-death", false);
        if (killer == null && !anyDeath) return null;
        AbilitySlot gained = d.gained();
        AbilityType type = gained.ability();
        int tier = gained.tier();
        gained.clear();
        d.setLost(type, tier);
        plugin.data().save(d);
        drop(victim.getLocation(), type, tier, killer == null ? null : killer.getUniqueId());
        if (killer != null) {
            Bukkit.broadcast(plugin.mm(plugin.prefix() + "<white>" + killer.getName() + " <gray>killed <white>" + victim.getName()
                    + " <gray>and can claim their " + type.colored() + "<gray>!"));
        }
        return type.colored();
    }

    // ------------------------------------------------------------------ using orbs / rerolls

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        AbilityType type = orbType(hand);
        if (type != null) {
            event.setCancelled(true);
            if (absorb(p, type, orbTier(hand))) hand.setAmount(hand.getAmount() - 1);
            return;
        }
        if (isReroll(hand)) {
            event.setCancelled(true);
            useReroll(p, hand);
        }
    }

    /** Puts an ability into the gained slot. A different gained ability already there drops at your feet. */
    private boolean absorb(Player p, AbilityType type, int tier) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return false;
        if (d.primary().ability() == type || d.gained().ability() == type) {
            p.sendMessage(plugin.mm(plugin.prefix() + "<red>You already have " + type.colored() + "<red>."));
            return false;
        }
        if (!d.gained().isEmpty()) {
            drop(p.getLocation(), d.gained().ability(), d.gained().tier(), p.getUniqueId());
            p.sendMessage(plugin.mm(plugin.prefix() + "<gray>Your old " + d.gained().ability().colored() + " <gray>dropped as an orb."));
        }
        d.gained().equip(type, tier);
        plugin.data().save(d);
        plugin.engine().resetCooldown(p.getUniqueId());
        absorbFx(p, type, "ABILITY ABSORBED");
        return true;
    }

    /** A Cosmic Reroll spins the wheel for a new slot-1 ability (the one you joined with). */
    private void useReroll(Player p, ItemStack hand) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d == null) return;
        if (plugin.roll().isSpinning(p.getUniqueId())) return;
        hand.setAmount(hand.getAmount() - 1);
        plugin.roll().spin(p, com.cosmicabilities.gui.RollPanel.Reason.REROLL);
    }

    private void absorbFx(Player p, AbilityType type, String title) {
        World w = p.getWorld();
        Location c = p.getLocation().add(0, 1, 0);
        w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
        w.playSound(c, Sound.ITEM_TOTEM_USE, 0.6f, 1.5f);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < 3; i++) {
                Fx.ring(w, c.clone().add(0, -0.8 + i * 0.7, 0), 1.2 - i * 0.3, 24, i, Fx.dust(type.rgb(), 1.3f));
            }
            Fx.burst(w, c, Particle.END_ROD, 30, 0.2);
        });
        p.showTitle(Title.title(plugin.mm("<" + type.color + "><bold>" + title),
                plugin.mm("<white>" + type.displayName + " <gray>is now in slot 2 <dark_gray>(/ability 2)"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(500))));
    }
}
