package com.cosmicabilities.data;

import java.util.UUID;

public final class PlayerData {
    public static final int SLOTS = 2;

    private final UUID uuid;
    private final AbilitySlot[] slots = new AbilitySlot[SLOTS];
    private int mastery = 0;
    private boolean dirty = false;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        for (int i = 0; i < SLOTS; i++) slots[i] = new AbilitySlot(this);
    }

    public UUID uuid() { return uuid; }
    public int mastery() { return mastery; }
    public boolean dirty() { return dirty; }

    /** Slot by 1-based number (1 or 2). Returns null if out of range. */
    public AbilitySlot slot(int number) {
        if (number < 1 || number > SLOTS) return null;
        return slots[number - 1];
    }

    /** The awakened (slot 1) ability. */
    public AbilitySlot primary() { return slots[0]; }

    public boolean hasAnyAbility() {
        for (AbilitySlot s : slots) if (!s.isEmpty()) return true;
        return false;
    }

    public void setMastery(int mastery) { this.mastery = Math.max(0, mastery); dirty = true; }
    public void addMastery(int amount) { setMastery(mastery + amount); }
    void markDirty() { dirty = true; }
    public void markClean() { dirty = false; }
}
