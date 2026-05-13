package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

public class Monster extends Entity {
    private float speed = 40f;
    private float damagePerSecond = 10f;
    private boolean active = true;

    // AI States
    private float fleeTimer = 0;
    private final float fleeDuration = 1.5f;

    public Monster(float x, float y, TextureRegion region) {
        super(x, y, 16, 16, region);
    }

    @Override
    public void update(float delta) {
        if (fleeTimer > 0) {
            fleeTimer -= delta;
        }
    }

    public void updateAI(Player player, House house, float delta) {
        float distToPlayer = position.dst(player.getPosition());
        float distToHouse = position.dst(house.getPosition());

        float pLight = player.getLightRadius();
        float hLight = house.getLightRadius();

        // Check if monster is in light
        boolean inPlayerLight = pLight > 0 && distToPlayer < pLight;
        boolean inHouseLight = hLight > 0 && distToHouse < hLight;

        if (inPlayerLight || inHouseLight || fleeTimer > 0) {
            // Trigger flee state if just hit light
            if (fleeTimer <= 0) {
                fleeTimer = fleeDuration;
            }

            // Flee from player/light
            Vector2 fleeDir = position.cpy().sub(player.getPosition()).nor();
            moveBlockedByHouse(fleeDir.x * speed * 2f * delta,
                               fleeDir.y * speed * 2f * delta, house);
        } else {
            // Move towards player
            Vector2 moveDir = player.getPosition().cpy().sub(position).nor();
            moveBlockedByHouse(moveDir.x * speed * delta,
                               moveDir.y * speed * delta, house);

            // Attack if close
            if (distToPlayer < 12) {
                player.takeDamage(damagePerSecond * delta);
            }
        }
    }

    /**
     * Axis-separated step that respects the house perimeter (no door access for
     * monsters). Sliding on one axis while the other is blocked means monsters
     * pile up against the wall instead of getting frozen by it.
     */
    private void moveBlockedByHouse(float dx, float dy, House house) {
        float newX = position.x + dx;
        if (!house.isBlockedByWall(newX, position.y, size.x, size.y, false)) {
            position.x = newX;
        }
        float newY = position.y + dy;
        if (!house.isBlockedByWall(position.x, newY, size.x, size.y, false)) {
            position.y = newY;
        }
    }
}
