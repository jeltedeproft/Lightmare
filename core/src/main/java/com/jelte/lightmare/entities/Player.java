package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;

public class Player extends Entity {
    private float batteryLevel = 100f;
    private final float maxBattery = 100f;
    private float lightRadius = 50f;
    private float speed = 100f;

    public Player(float x, float y, Texture texture) {
        super(x, y, 16, 16, texture);
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
        if (dx != 0 || dy != 0) {
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            dx /= length;
            dy /= length;
            position.x += dx * speed * delta;
            position.y += dy * speed * delta;
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
}
