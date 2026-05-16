package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;

public class House extends Entity {
    public static final float WIDTH = 192f;
    public static final float HEIGHT = 128f;
    public static final float WALL_THICKNESS = 8f;
    public static final float DOOR_WIDTH = 32f;

    // X offset of the door's left edge from the house's left edge — tuned to
    // sit under the small side door on the right of house.png (sprite x≈63-72
    // in the 87-wide source, which scales to a rendered center around x=149).
    public static final float DOOR_X_OFFSET = 117f;

    private float lightRadius = 240f;

    public House(float x, float y, Texture texture) {
        super(x, y, WIDTH, HEIGHT, texture);
    }

    @Override
    public void update(float delta) {
    }

    public float getLightRadius() {
        return lightRadius;
    }

    public boolean containsPoint(float x, float y) {
        return x >= position.x && x <= position.x + size.x
            && y >= position.y && y <= position.y + size.y;
    }

    public float getCenterX() {
        return position.x + size.x * 0.5f;
    }

    public float getCenterY() {
        return position.y + size.y * 0.5f;
    }

    public float getDoorX() {
        return position.x + DOOR_X_OFFSET;
    }

    /**
     * AABB-vs-walls test. If {@code allowDoor} is true the south wall is split
     * at the door — that's the player path. For monsters and the boss we pass
     * false so the south wall is solid and they can't enter at all.
     */
    public boolean isBlockedByWall(float x, float y, float w, float h, boolean allowDoor) {
        // North wall (top).
        if (overlaps(x, y, w, h,
            position.x, position.y + size.y - WALL_THICKNESS,
            size.x, WALL_THICKNESS)) return true;
        // West wall (left).
        if (overlaps(x, y, w, h,
            position.x, position.y,
            WALL_THICKNESS, size.y)) return true;
        // East wall (right).
        if (overlaps(x, y, w, h,
            position.x + size.x - WALL_THICKNESS, position.y,
            WALL_THICKNESS, size.y)) return true;
        // South wall — either split around the door for the player, or solid.
        if (allowDoor) {
            float doorX = getDoorX();
            float leftW = doorX - position.x;
            if (leftW > 0f && overlaps(x, y, w, h,
                position.x, position.y, leftW, WALL_THICKNESS)) return true;
            float rightStart = doorX + DOOR_WIDTH;
            float rightW = position.x + size.x - rightStart;
            if (rightW > 0f && overlaps(x, y, w, h,
                rightStart, position.y, rightW, WALL_THICKNESS)) return true;
        } else {
            if (overlaps(x, y, w, h,
                position.x, position.y, size.x, WALL_THICKNESS)) return true;
        }
        return false;
    }

    private static boolean overlaps(float ax, float ay, float aw, float ah,
                                    float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }
}
