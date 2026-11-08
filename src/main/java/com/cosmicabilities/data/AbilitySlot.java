package com.cosmicabilities.data;

import com.cosmicabilities.ability.AbilityType;

/** One equipped ability (slot 1 or 2) with its own level and specialization. */
public class AbilitySlot {
    private final PlayerData owner;
    private AbilityType ability;
    private int level = 1;
    /** 'A', 'B', or 0 when no specialization chosen yet. */
    private char path = 0;

    AbilitySlot(PlayerData owner) {
        this.owner = owner;
    }

    public AbilityType ability() { return ability; }
    public int level() { return level; }
    public char path() { return path; }
    public boolean isEmpty() { return ability == null; }
    public boolean evolved() { return level >= 10; }
    public boolean isPathA() { return path == 'A'; }
    public boolean isPathB() { return path == 'B'; }

    public void setAbility(AbilityType ability) { this.ability = ability; owner.markDirty(); }
    public void setLevel(int level) { this.level = Math.max(1, Math.min(10, level)); owner.markDirty(); }
    public void setPath(char path) { this.path = (path == 'A' || path == 'B') ? path : 0; owner.markDirty(); }

    /** Replace the ability and reset its progress. */
    public void equip(AbilityType ability, int level) {
        this.ability = ability;
        this.level = Math.max(1, Math.min(10, level));
        this.path = 0;
        owner.markDirty();
    }

    public void clear() {
        ability = null;
        level = 1;
        path = 0;
        owner.markDirty();
    }
}
