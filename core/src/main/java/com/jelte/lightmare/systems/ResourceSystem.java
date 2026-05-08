package com.jelte.lightmare.systems;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.jelte.lightmare.Resources;
import com.jelte.lightmare.entities.Entity;
import com.jelte.lightmare.entities.House;
import com.jelte.lightmare.entities.Player;
import com.jelte.lightmare.entities.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodic, radius-based procedural spawner for ore resources. Keeps a target
 * count of un-mined ores in a band around the player and despawns any that
 * drift outside the unload radius, so memory stays bounded as the player
 * wanders an effectively infinite world.
 */
public class ResourceSystem {
    private static final float SPAWN_CHECK_INTERVAL = 1.5f;
    private static final int TARGET_NEARBY = 8;

    // Cluster centers spawn just past the visible area so new ore appears in the
    // dark and the player has to walk into it.
    private static final float SPAWN_DISTANCE_MIN = 120f;
    private static final float SPAWN_DISTANCE_MAX = 240f;

    // Resources outside this radius are removed to keep entity count bounded.
    private static final float DESPAWN_DISTANCE = 500f;

    private static final int CLUSTER_MIN = 2;
    private static final int CLUSTER_MAX = 5;
    private static final float CLUSTER_RADIUS = 28f;

    private static final float MIN_SEPARATION = 18f;
    // Padding around the house's bounding box (rectangular keep-out zone).
    private static final float HOUSE_BUFFER = 32f;

    private final EntityManager entityManager;
    private final House house;
    private float spawnTimer = 0f;

    public ResourceSystem(EntityManager entityManager, House house) {
        this.entityManager = entityManager;
        this.house = house;
    }

    /** Spawn a starting cluster close to the player so there's something to mine immediately. */
    public void seedInitial(Player player) {
        spawnCluster(player, 40f, 90f);
    }

    public void update(float delta, Player player) {
        spawnTimer += delta;
        if (spawnTimer < SPAWN_CHECK_INTERVAL) return;
        spawnTimer = 0f;

        if (countNearbyResources(player) < TARGET_NEARBY) {
            spawnCluster(player, SPAWN_DISTANCE_MIN, SPAWN_DISTANCE_MAX);
        }
        despawnFar(player);
    }

    private int countNearbyResources(Player player) {
        int count = 0;
        Vector2 pp = player.getPosition();
        float r2 = SPAWN_DISTANCE_MAX * SPAWN_DISTANCE_MAX;
        for (Entity e : entityManager.getEntities()) {
            if (e instanceof Resource && !((Resource) e).isMined()) {
                Vector2 ep = e.getPosition();
                float dx = ep.x - pp.x;
                float dy = ep.y - pp.y;
                if (dx * dx + dy * dy < r2) count++;
            }
        }
        return count;
    }

    private void spawnCluster(Player player, float minDist, float maxDist) {
        float angle = MathUtils.random(0f, MathUtils.PI2);
        float distance = MathUtils.random(minDist, maxDist);
        float cx = player.getPosition().x + MathUtils.cos(angle) * distance;
        float cy = player.getPosition().y + MathUtils.sin(angle) * distance;

        int count = MathUtils.random(CLUSTER_MIN, CLUSTER_MAX);
        int spawned = 0;
        int attempts = 0;
        while (spawned < count && attempts < 25) {
            attempts++;
            float x = cx + MathUtils.random(-CLUSTER_RADIUS, CLUSTER_RADIUS);
            float y = cy + MathUtils.random(-CLUSTER_RADIUS, CLUSTER_RADIUS);
            if (!isPositionValid(x, y)) continue;

            // Random ore variant per spawn, using the four atlas regions.
            int variant = MathUtils.random(Resources.oreRegions.length - 1);
            entityManager.addEntity(new Resource(x, y, Resources.oreRegions[variant]));
            spawned++;
        }
    }

    private boolean isPositionValid(float x, float y) {
        // Rectangular keep-out around the house bounding box. Radial checks
        // don't fit a non-square house — corners would be inside the radius
        // even when ore is well outside the actual building footprint.
        Vector2 hp = house.getPosition();
        Vector2 hs = house.getSize();
        if (x >= hp.x - HOUSE_BUFFER && x <= hp.x + hs.x + HOUSE_BUFFER
            && y >= hp.y - HOUSE_BUFFER && y <= hp.y + hs.y + HOUSE_BUFFER) {
            return false;
        }

        float sep2 = MIN_SEPARATION * MIN_SEPARATION;
        for (Entity e : entityManager.getEntities()) {
            if (e instanceof Resource && !((Resource) e).isMined()) {
                Vector2 ep = e.getPosition();
                float dx = x - ep.x;
                float dy = y - ep.y;
                if (dx * dx + dy * dy < sep2) return false;
            }
        }
        return true;
    }

    private void despawnFar(Player player) {
        List<Entity> toRemove = null;
        Vector2 pp = player.getPosition();
        float r2 = DESPAWN_DISTANCE * DESPAWN_DISTANCE;
        for (Entity e : entityManager.getEntities()) {
            // Only despawn un-mined ores. Mined ores follow the trail, so removing
            // them mid-trail would be visible and break the deposit-at-house flow.
            if (e instanceof Resource && !((Resource) e).isMined()) {
                Vector2 ep = e.getPosition();
                float dx = ep.x - pp.x;
                float dy = ep.y - pp.y;
                if (dx * dx + dy * dy > r2) {
                    if (toRemove == null) toRemove = new ArrayList<>();
                    toRemove.add(e);
                }
            }
        }
        if (toRemove != null) {
            for (Entity e : toRemove) entityManager.removeEntity(e);
        }
    }
}
