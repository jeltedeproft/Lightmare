package com.jelte.lightmare.systems;

import com.jelte.lightmare.entities.Player;
import com.jelte.lightmare.entities.Resource;

/**
 * Tracks the four player upgrades and applies their effects on purchase.
 *
 * Each upgrade is paid in a single ore variant matching its chest's color, so
 * the link between "this ore type" and "this stat" is obvious without text:
 *   BATTERY ← blue   SPEED ← green   MINING ← orange   LIGHT ← purple
 */
public class UpgradeSystem {
    public enum Upgrade {
        BATTERY(0),
        SPEED(1),
        MINING(2),
        LIGHT(3);
        /** Index into Resources.oreRegions / storage chests (0=blue..3=purple). */
        public final int oreVariant;
        Upgrade(int oreVariant) { this.oreVariant = oreVariant; }
    }

    public static final int MAX_LEVEL = 4;
    public static final int COST_PER_LEVEL = 3;

    private final int[] levels = new int[Upgrade.values().length];

    public int getLevel(Upgrade u) { return levels[u.ordinal()]; }
    public boolean isMaxed(Upgrade u) { return levels[u.ordinal()] >= MAX_LEVEL; }

    public boolean canAfford(Upgrade u, int[] storage) {
        if (isMaxed(u)) return false;
        return storage[u.oreVariant] >= COST_PER_LEVEL;
    }

    public boolean purchase(Upgrade u, int[] storage, Player player) {
        if (!canAfford(u, storage)) return false;
        storage[u.oreVariant] -= COST_PER_LEVEL;
        levels[u.ordinal()]++;
        applyEffect(u, player);
        return true;
    }

    private void applyEffect(Upgrade u, Player p) {
        switch (u) {
            case BATTERY:
                p.setMaxBattery(p.getMaxBattery() + 20f);
                break;
            case SPEED:
                p.setSpeed(p.getSpeed() + 20f);
                break;
            case MINING:
                // Each level shaves one click off the required mining hits, capped at 1.
                Resource.setGlobalClicksRequired(3 - levels[u.ordinal()]);
                break;
            case LIGHT:
                p.setMaxLightRadius(p.getMaxLightRadius() + 30f);
                break;
        }
    }
}
