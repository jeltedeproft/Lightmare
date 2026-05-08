package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class Player extends Entity {
    /** Tells the move() method whether a candidate AABB is solid. Implementations
     *  typically check tile layers, entity overlaps, etc. */
    public interface CollisionChecker {
        boolean isBlocked(float x, float y, float w, float h);
    }

    private float batteryLevel = 100f;
    private final float maxBattery = 100f;
    private float lightRadius = 50f;
    private float speed = 100f;
    private float hp = 100f;
    private final float maxHp = 100f;

    public Player(float x, float y, Texture texture) {
        super(x, y, 16, 16, texture);
    }

    public Player(float x, float y, TextureRegion region) {
        super(x, y, 16, 16, region);
    }

    @Override
    public void update(float delta) {
        // Battery logic will be handled here or by a system
        if (batteryLevel > 0) {
            batteryLevel -= delta * 2; // Drains over time
        }
        
        if (batteryLevel < 0) batteryLevel = 0;
    }

    public void move(float dx, float dy, float delta) {
        move(dx, dy, delta, null);
    }

    /**
     * Axis-separated movement so the player slides along walls instead of
     * sticking. Each axis tries the proposed step; if the resulting AABB is
     * blocked by the checker, that axis stays put while the other still moves.
     */
    public void move(float dx, float dy, float delta, CollisionChecker checker) {
        if (dx == 0f && dy == 0f) return;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= length;
        dy /= length;

        float stepX = dx * speed * delta;
        float stepY = dy * speed * delta;

        if (checker == null || !checker.isBlocked(position.x + stepX, position.y, size.x, size.y)) {
            position.x += stepX;
        }
        if (checker == null || !checker.isBlocked(position.x, position.y + stepY, size.x, size.y)) {
            position.y += stepY;
        }
    }

    public float getBatteryLevel() {
        return batteryLevel;
    }

    public float getMaxBattery() {
        return maxBattery;
    }

    public void recharge(float delta) {
        batteryLevel += delta * 10; // Recharge speed
        if (batteryLevel > maxBattery) batteryLevel = maxBattery;
    }

    public float getLightRadius() {
        if (batteryLevel <= 0) return 0;
        // Light radius shrinks with battery, but stays at least 50% until dead
        return lightRadius * (0.5f + 0.5f * (batteryLevel / maxBattery));
    }

    public float getHp() {
        return hp;
    }

    public float getMaxHp() {
        return maxHp;
    }

    public void takeDamage(float amount) {
        hp -= amount;
        if (hp < 0) hp = 0;
    }

    public void heal(float amount) {
        hp += amount;
        if (hp > maxHp) hp = maxHp;
    }

    public float getEmergencyLightRadius() {
        return 20f;
    }
}
