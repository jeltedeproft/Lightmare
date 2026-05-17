package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;

public class House extends Entity {
    // 2× the source sprite (87x82) — preserves its native aspect ratio so the
    // pixel art doesn't stretch, while staying large enough for the interior
    // chest/machine/robot layout.
    public static final float WIDTH = 174f;
    public static final float HEIGHT = 164f;
    public static final float WALL_THICKNESS = 8f;
    public static final float DOOR_WIDTH = 32f;

    // X offset of the door's left edge from the house's left edge — tuned to
    // sit under the small side door on the right of house.png (sprite x≈67 at
    // 1× → x≈135 at 2×, minus half of DOOR_WIDTH).
    public static final float DOOR_X_OFFSET = 119f;

    // Big open garage on the left of the south wall — the second walkable
    // entrance. Tuned to sit under the dark archway on house.png (sprite
    // x≈15-56 at 1× → roughly x=30-112 at 2×); slightly inset so a strip of
    // wall remains at the far left.
    public static final float GARAGE_X_OFFSET = 24f;
    public static final float GARAGE_WIDTH = 84f;

    // Dimmer than the world map's monsters expect to flee from — they only
    // recoil when within this radius of the house, so a smaller value lets
    // them push closer to the walls before turning back.
    private float lightRadius = 150f;

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

    public float getGarageX() {
        return position.x + GARAGE_X_OFFSET;
    }

    /**
     * AABB-vs-walls test. If {@code allowDoor} is true the south wall is split
     * around the garage and the door — both walkable for the player. For
     * monsters and the boss we pass false so the south wall is solid and they
     * can't enter at all.
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
        // South wall — three solid segments around the garage and the door for
        // the player path, or one solid wall for monsters.
        if (allowDoor) {
            float garageStart = position.x + GARAGE_X_OFFSET;
            float garageEnd = garageStart + GARAGE_WIDTH;
            float doorStart = position.x + DOOR_X_OFFSET;
            float doorEnd = doorStart + DOOR_WIDTH;
            // Left of garage.
            float leftW = garageStart - position.x;
            if (leftW > 0f && overlaps(x, y, w, h,
                position.x, position.y, leftW, WALL_THICKNESS)) return true;
            // Between garage and door.
            float midW = doorStart - garageEnd;
            if (midW > 0f && overlaps(x, y, w, h,
                garageEnd, position.y, midW, WALL_THICKNESS)) return true;
            // Right of door.
            float rightW = position.x + size.x - doorEnd;
            if (rightW > 0f && overlaps(x, y, w, h,
                doorEnd, position.y, rightW, WALL_THICKNESS)) return true;
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
