package com.cosmicabilities.data;

import com.cosmicabilities.ability.AbilityType;

/** One equipped ability (slot 1 = joined-with, slot 2 = gained) and whether it has been upgraded (tier 2). */
public class AbilitySlot {
    public static final int MAX_TIER = 2;

    private final PlayerData owner;
    private AbilityType ability;
    private int tier = 1;

    AbilitySlot(PlayerData owner) {
        this.owner = owner;
    }

    public AbilityType ability() { return ability; }
    public int tier() { return tier; }
    public boolean isEmpty() { return ability == null; }
    public boolean upgraded() { return tier >= 2; }

    public void setTier(int tier) {
        this.tier = Math.max(1, Math.min(MAX_TIER, tier));
        owner.markDirty();
    }

    public void equip(AbilityType ability, int tier) {
        this.ability = ability;
        this.tier = Math.max(1, Math.min(MAX_TIER, tier));
        owner.markDirty();
    }

    public void clear() {
        ability = null;
        tier = 1;
        owner.markDirty();
    }
}
