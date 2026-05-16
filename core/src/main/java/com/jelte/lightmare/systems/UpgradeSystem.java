package com.jelte.lightmare.systems;

import com.jelte.lightmare.entities.Player;
import com.jelte.lightmare.entities.Resource;

/**
 * Single-step unlock per robot part. Each part takes one ore variant; reach
 * UNLOCK_THRESHOLD of that color in storage and the part comes online with
 * its full effect applied — no levels, no in-house purchase step.
 *
 *   HEADLIGHT ← blue   LEGS ← green   DRILL ← orange   GUN ← purple
 */
public class UpgradeSystem {
    public enum Upgrade {
        HEADLIGHT(0),
        LEGS(1),
        DRILL(2),
        GUN(3);
        /** Index into Resources.oreRegions / storage chests (0=blue..3=purple). */
        public final int oreVariant;
        Upgrade(int oreVariant) { this.oreVariant = oreVariant; }
    }

    /** Ore count of a single color needed to unlock its corresponding part. */
    public static final int UNLOCK_THRESHOLD = 10;

    private final boolean[] unlocked = new boolean[Upgrade.values().length];

    public boolean isUnlocked(Upgrade u) { return unlocked[u.ordinal()]; }

    /** True when every part is unlocked — gates the boss arc. */
    public boolean allUnlocked() {
        for (Upgrade u : Upgrade.values()) {
            if (!isUnlocked(u)) return false;
        }
        return true;
    }

    /** True when the gun is online — gates click-to-shoot. */
    public boolean gunReady() { return isUnlocked(Upgrade.GUN); }

    public boolean canUnlock(Upgrade u, int[] storage) {
        if (isUnlocked(u)) return false;
        return storage[u.oreVariant] >= UNLOCK_THRESHOLD;
    }

    /** @return true if this call flipped the part from locked to unlocked. */
    public boolean unlock(Upgrade u, Player player) {
        if (isUnlocked(u)) return false;
        unlocked[u.ordinal()] = true;
        applyEffect(u, player);
        return true;
    }

    private void applyEffect(Upgrade u, Player p) {
        switch (u) {
            case HEADLIGHT:
                p.setMaxLightRadius(p.getMaxLightRadius() + 120f);
                break;
            case LEGS:
                p.setSpeed(p.getSpeed() + 80f);
                break;
            case DRILL:
                // One-click mining once the drill is online.
                Resource.setGlobalClicksRequired(1);
                break;
            case GUN:
                // Stat-free gate — see gunReady().
                break;
        }
    }
}
