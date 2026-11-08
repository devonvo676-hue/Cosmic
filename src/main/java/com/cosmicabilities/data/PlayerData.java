package com.cosmicabilities.data;

import com.cosmicabilities.ability.AbilityType;
import com.cosmicabilities.keybind.Keybind;

import java.util.UUID;

/**
 * Slot 1 = the ability the player joined with (permanent).
 * Slot 2 = a gained ability (from a boss orb or a kill). It drops when the player is killed.
 */
public final class PlayerData {
    public static final int SLOTS = 2;

    private final UUID uuid;
    private final AbilitySlot[] slots = new AbilitySlot[SLOTS];
    private final Keybind[] binds = {Keybind.SNEAK_SWAP, Keybind.SNEAK_LEFT_CLICK};
    private int afkTokens = 0;
    private int afkProgress = 0;
    /** The gained ability lost on the last death (restored by a Cosmic Reroll). */
    private AbilityType lostAbility;
    private int lostTier = 1;
    private boolean dirty = false;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        for (int i = 0; i < SLOTS; i++) slots[i] = new AbilitySlot(this);
    }

    public UUID uuid() { return uuid; }
    public int afkTokens() { return afkTokens; }
    public int afkProgress() { return afkProgress; }
    public boolean dirty() { return dirty; }

    public AbilitySlot slot(int number) {
        if (number < 1 || number > SLOTS) return null;
        return slots[number - 1];
    }

    public AbilitySlot primary() { return slots[0]; }
    public AbilitySlot gained() { return slots[1]; }

    public boolean hasAnyAbility() {
        for (AbilitySlot s : slots) if (!s.isEmpty()) return true;
        return false;
    }

    public boolean has(AbilityType type) {
        for (AbilitySlot s : slots) if (s.ability() == type) return true;
        return false;
    }

    public Keybind bind(int slot) { return binds[slot - 1]; }

    /** Sets a slot's keybind; if the other slot used it, the two swap. */
    public void setBind(int slot, Keybind bind) {
        int other = slot == 1 ? 2 : 1;
        if (bind != Keybind.COMMAND_ONLY && binds[other - 1] == bind) binds[other - 1] = binds[slot - 1];
        binds[slot - 1] = bind;
        dirty = true;
    }

    public AbilityType lostAbility() { return lostAbility; }
    public int lostTier() { return lostTier; }
    public void setLost(AbilityType type, int tier) { lostAbility = type; lostTier = tier; dirty = true; }

    public void setAfkTokens(int tokens) { afkTokens = Math.max(0, tokens); dirty = true; }
    public void addAfkTokens(int amount) { setAfkTokens(afkTokens + amount); }
    public void setAfkProgress(int seconds) { afkProgress = Math.max(0, seconds); dirty = true; }

    void markDirty() { dirty = true; }
    public void markClean() { dirty = false; }
}
