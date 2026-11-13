package com.cosmicabilities.admin;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom armor suits (drawn by the resource pack's equipment textures). Putting one on saves the player's own
 * armor and gives it back when the suit comes off, dies with them, or they log out. Suit pieces can't be moved.
 */
public class Suits implements Listener {
    public enum Piece {
        HELMET("head", Material.NETHERITE_HELMET), CHEST("chest", Material.NETHERITE_CHESTPLATE),
        LEGS("legs", Material.NETHERITE_LEGGINGS), BOOTS("feet", Material.NETHERITE_BOOTS);
        final String slot; final Material base;
        Piece(String slot, Material base) { this.slot = slot; this.base = base; }
    }

    private final CosmicAbilitiesPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, ItemStack[]> saved = new ConcurrentHashMap<>();

    public Suits(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "suit");
    }

    public ItemStack piece(String suit, String name, Piece piece) {
        ItemStack it = new ItemStack(piece.base);
        ItemMeta m = it.getItemMeta();
        m.displayName(plugin.mm("<!italic>" + name));
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, suit);
        // every piece has its own custom look in the inventory; suits with a 3D helmet wear the model itself on the head
        String helmet3d = HELMET_MODELS.get(suit);
        m.setItemModel(new NamespacedKey("cosmic", piece == Piece.HELMET && helmet3d != null ? helmet3d
                : suit + "_" + piece.name().toLowerCase(java.util.Locale.ROOT)));
        it.setItemMeta(m);
        if (piece == Piece.HELMET && helmet3d != null) {
            return Bukkit.getUnsafe().modifyItemStack(it, "[minecraft:equippable={slot:\"head\"}]");
        }
        return Bukkit.getUnsafe().modifyItemStack(it, "[minecraft:equippable={slot:\"" + piece.slot + "\",asset_id:\"cosmic:" + suit + "\"}]");
    }

    /** Suits whose helmet is a real 3D model instead of a flat armor layer. */
    private static final Map<String, String> HELMET_MODELS = Map.of("nova_armor", "boss_fx_nova_helmet");

    /** Takes one piece off again, giving back whatever was in that slot before the suit. */
    public void unequip(Player p, Piece piece) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] old = saved.get(p.getUniqueId());
        int i = switch (piece) { case BOOTS -> 0; case LEGS -> 1; case CHEST -> 2; case HELMET -> 3; };
        ItemStack back = old == null || old[i] == null ? null : old[i].clone();
        switch (piece) {
            case HELMET -> { if (suitOf(inv.getHelmet()) != null) inv.setHelmet(back); }
            case CHEST -> { if (suitOf(inv.getChestplate()) != null) inv.setChestplate(back); }
            case LEGS -> { if (suitOf(inv.getLeggings()) != null) inv.setLeggings(back); }
            case BOOTS -> { if (suitOf(inv.getBoots()) != null) inv.setBoots(back); }
        }
        if (old != null) old[i] = null;   // given back already
    }

    public String suitOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public boolean wearing(Player p, String suit) {
        return suit.equals(suitOf(p.getInventory().getChestplate()));
    }

    /** Puts one piece on (saving whatever they had on first). */
    public void equip(Player p, String suit, String name, Piece piece) {
        PlayerInventory inv = p.getInventory();
        saved.computeIfAbsent(p.getUniqueId(), k -> {
            ItemStack[] a = inv.getArmorContents();
            ItemStack[] copy = new ItemStack[a.length];
            for (int i = 0; i < a.length; i++) copy[i] = a[i] == null ? null : a[i].clone();
            return copy;
        });
        ItemStack it = piece(suit, name, piece);
        switch (piece) {
            case HELMET -> inv.setHelmet(it);
            case CHEST -> inv.setChestplate(it);
            case LEGS -> inv.setLeggings(it);
            case BOOTS -> inv.setBoots(it);
        }
    }

    public void equipAll(Player p, String suit, String name) {
        for (Piece piece : Piece.values()) equip(p, suit, name, piece);
    }

    /** Takes the suit off and gives their own armor back. */
    public void remove(Player p) {
        ItemStack[] old = saved.remove(p.getUniqueId());
        PlayerInventory inv = p.getInventory();
        ItemStack[] now = inv.getArmorContents();
        for (int i = 0; i < now.length; i++) if (suitOf(now[i]) != null) now[i] = null;
        if (old != null) for (int i = 0; i < now.length; i++) if (now[i] == null) now[i] = old[i];
        inv.setArmorContents(now);
        for (ItemStack it : inv.getContents()) if (suitOf(it) != null) it.setAmount(0);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (suitOf(event.getCurrentItem()) != null || suitOf(event.getCursor()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        event.getDrops().removeIf(it -> suitOf(it) != null);
        ItemStack[] old = saved.remove(p.getUniqueId());
        if (old == null) return;
        if (event.getKeepInventory()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> { saved.put(p.getUniqueId(), old); remove(p); });
        } else {
            for (ItemStack it : old) if (it != null && !it.getType().isAir()) event.getDrops().add(it);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (saved.containsKey(event.getPlayer().getUniqueId())) remove(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerInventory inv = event.getPlayer().getInventory();
        ItemStack[] a = inv.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < a.length; i++) if (suitOf(a[i]) != null) { a[i] = null; changed = true; }
        if (changed) inv.setArmorContents(a);
    }

    public void shutdown() {
        for (UUID id : saved.keySet().toArray(new UUID[0])) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) remove(p);
        }
    }
}
