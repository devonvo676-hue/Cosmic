package com.cosmicabilities.gui;

import com.cosmicabilities.CosmicAbilitiesPlugin;
import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.admin.BanPistol;
import com.cosmicabilities.admin.RankTags;
import com.cosmicabilities.boss.BossType;
import com.cosmicabilities.data.AbilitySlot;
import com.cosmicabilities.data.PlayerData;
import com.cosmicabilities.item.CosmicMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Admin-only control panel: every ability, bosses, materials, and staff tools. Opened with /adminpanel. */
public class AdminPanel implements Listener {
    private static final int[] BOSS_SLOTS = {29, 30, 31, 32, 33};
    private static final int[] MATERIAL_SLOTS = {38, 39, 40, 41, 42};
    private static final int INFO = 4, PISTOL = 45, TAG_ADMIN = 46, TAG_OWNER = 47, TAG_OFF = 48,
            RESET_CD = 50, MAX_TIER = 51, CLEAR_SLOT2 = 52, CLOSE = 53;

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    private final CosmicAbilitiesPlugin plugin;

    public AdminPanel(CosmicAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player admin) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.mm("<dark_red><bold>Abilities & Items"));
        holder.inventory = inv;
        render(admin, inv);
        admin.openInventory(inv);
        admin.playSound(admin.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.3f);
    }

    private void render(Player admin, Inventory inv) {
        ItemStack pane = Icons.button(plugin, Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        PlayerData d = plugin.data().get(admin.getUniqueId());
        inv.setItem(INFO, Icons.button(plugin, Material.NETHER_STAR, "<gradient:#9b5de5:#00bbf9><bold>Your Abilities",
                "<gray>Slot 1: " + slotText(d, 1), "<gray>Slot 2: " + slotText(d, 2)));

        AbilityType[] types = AbilityType.values();
        for (int i = 0; i < types.length; i++) {
            inv.setItem(AbilitiesPanel.SLOTS[i], Icons.ability(plugin, types[i], List.of(
                    "<yellow>Left-click <gray>equip in slot 1",
                    "<yellow>Right-click <gray>equip in slot 2",
                    "<yellow>Shift-click <gray>test cast it now")));
        }
        BossType[] bosses = BossType.values();
        for (int i = 0; i < bosses.length; i++) {
            BossType b = bosses[i];
            inv.setItem(BOSS_SLOTS[i], Icons.button(plugin, Material.WITHER_SKELETON_SKULL, b.colored(),
                    "<gray>Drops " + b.drop.colored(), "", "<yellow>Click <gray>to start a boss fight here"));
        }
        CosmicMaterial[] mats = CosmicMaterial.values();
        for (int i = 0; i < mats.length; i++) inv.setItem(MATERIAL_SLOTS[i], mats[i].create(16));
        inv.setItem(PISTOL, plugin.banPistol().create());
        inv.setItem(TAG_ADMIN, Icons.button(plugin, Material.RED_BANNER, "<red><bold>[ADMIN] tag", "<gray>Requires admin or OP"));
        inv.setItem(TAG_OWNER, Icons.button(plugin, Material.YELLOW_BANNER, "<gold><bold>[OWNER] tag", "<gray>Requires OP"));
        inv.setItem(TAG_OFF, Icons.button(plugin, Material.WHITE_BANNER, "<white>Remove tag"));
        inv.setItem(RESET_CD, Icons.button(plugin, Material.CLOCK, "<aqua>Reset my cooldowns"));
        inv.setItem(MAX_TIER, Icons.button(plugin, Material.EXPERIENCE_BOTTLE, "<light_purple>Upgrade my abilities", "<gray>Both slots upgraded"));
        inv.setItem(CLEAR_SLOT2, Icons.button(plugin, Material.LAVA_BUCKET, "<red>Clear slot 2"));
        inv.setItem(CLOSE, Icons.button(plugin, Material.ARROW, "<yellow>Back to admin panel"));
    }

    private static String slotText(PlayerData d, int slot) {
        if (d == null || d.slot(slot).isEmpty()) return "<dark_gray>empty";
        AbilitySlot s = d.slot(slot);
        return s.ability().colored() + " <gray>(" + AbilityType.tierName(s.tier()) + ")";
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof Holder)) return;
        if (!BanPistol.isStaff(admin)) { admin.closeInventory(); return; }
        int raw = event.getRawSlot();
        ClickType click = event.getClick();
        PlayerData d = plugin.data().get(admin.getUniqueId());
        if (d == null) return;

        for (int i = 0; i < AbilityType.values().length; i++) {
            if (raw != AbilitiesPanel.SLOTS[i]) continue;
            AbilityType type = AbilityType.values()[i];
            if (click.isShiftClick()) {
                int slot = d.slot(1).ability() == type ? 1 : 2;
                AbilitySlot target = d.slot(slot);
                AbilityType prevType = target.ability();
                int prevTier = target.tier();
                if (prevType != type) target.equip(type, prevTier);
                plugin.engine().resetCooldown(admin.getUniqueId());
                admin.closeInventory();
                plugin.engine().tryCast(admin, slot);
                if (prevType != type) {
                    if (prevType == null) target.clear(); else target.equip(prevType, prevTier);
                }
                return;
            }
            int slot = click.isRightClick() ? 2 : 1;
            AbilitySlot target = d.slot(slot);
            if (target.ability() != type) target.equip(type, target.isEmpty() ? 1 : target.tier());
            plugin.data().save(d);
            plugin.engine().resetCooldown(admin.getUniqueId());
            admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>Equipped " + type.colored() + " <gray>in slot " + slot + "."));
            admin.playSound(admin.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            render(admin, event.getInventory());
            return;
        }
        for (int i = 0; i < BOSS_SLOTS.length; i++) {
            if (raw != BOSS_SLOTS[i]) continue;
            admin.closeInventory();
            plugin.bossFightPanel().start(BossType.values()[i], admin.getLocation(),
                    plugin.getConfig().getInt("bossfight.countdown-seconds", 10), admin.getName());
            return;
        }
        for (int i = 0; i < MATERIAL_SLOTS.length; i++) {
            if (raw != MATERIAL_SLOTS[i]) continue;
            CosmicMaterial.values()[i].give(admin, click.isShiftClick() ? 64 : 16);
            admin.playSound(admin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            return;
        }
        switch (raw) {
            case PISTOL -> {
                admin.getInventory().addItem(plugin.banPistol().create());
                admin.sendMessage(plugin.mm(plugin.prefix() + "<red>Ban Pistol <gray>added. Anyone you shoot is banned."));
            }
            case TAG_ADMIN -> tag(admin, RankTags.Rank.ADMIN);
            case TAG_OWNER -> tag(admin, RankTags.Rank.OWNER);
            case TAG_OFF -> {
                plugin.tags().clear(admin);
                admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>Tag removed."));
            }
            case RESET_CD -> {
                plugin.engine().resetCooldown(admin.getUniqueId());
                admin.sendMessage(plugin.mm(plugin.prefix() + "<aqua>Cooldowns reset."));
            }
            case MAX_TIER -> {
                for (int s = 1; s <= PlayerData.SLOTS; s++) if (!d.slot(s).isEmpty()) d.slot(s).setTier(AbilitySlot.MAX_TIER);
                plugin.data().save(d);
                admin.sendMessage(plugin.mm(plugin.prefix() + "<light_purple>Your abilities are now upgraded."));
                render(admin, event.getInventory());
            }
            case CLEAR_SLOT2 -> {
                d.slot(2).clear();
                plugin.data().save(d);
                render(admin, event.getInventory());
            }
            case CLOSE -> plugin.adminHub().open(admin);
            default -> { }
        }
    }

    private void tag(Player admin, RankTags.Rank rank) {
        if (plugin.tags().apply(admin, rank)) {
            admin.sendMessage(plugin.mm(plugin.prefix() + "<gray>You are now wearing the " + rank.name() + " tag."));
            admin.playSound(admin.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        } else {
            admin.sendMessage(plugin.mm(plugin.prefix() + "<red>You need " + (rank == RankTags.Rank.OWNER ? "OP" : "admin or OP")
                    + " to wear that tag."));
        }
    }
}
