package com.jelte.lightmare.systems;

/**
 * Single-step unlock per upgrade. Reach UNLOCK_THRESHOLD of the matching ore
 * color in storage and the upgrade comes online. Three of the four unlocks
 * are physical mech parts that get bolted onto the broken robot; the fourth
 * (VISION) buffs the mech's detection range once it's active.
 *
 *   VISION ← blue   LEGS ← green   DRILL ← orange   GUN ← purple
 */
public class UpgradeSystem {
    public enum Upgrade {
        VISION(0),
        LEGS(1),
        DRILL(2),
        GUN(3);
        /** Index into Resources.oreRegions / storage chests (0=blue..3=purple). */
        public final int oreVariant;
        Upgrade(int oreVariant) { this.oreVariant = oreVariant; }
    }

    /** Ore count of a single color needed to unlock its upgrade. */
    public static final int UNLOCK_THRESHOLD = 10;

    private final boolean[] unlocked = new boolean[Upgrade.values().length];

    public boolean isUnlocked(Upgrade u) { return unlocked[u.ordinal()]; }

    public boolean canUnlock(Upgrade u, int[] storage) {
        if (isUnlocked(u)) return false;
        return storage[u.oreVariant] >= UNLOCK_THRESHOLD;
    }

    /** @return true if this call flipped the upgrade from locked to unlocked. */
    public boolean unlock(Upgrade u) {
        if (isUnlocked(u)) return false;
        unlocked[u.ordinal()] = true;
        return true;
    }
}
