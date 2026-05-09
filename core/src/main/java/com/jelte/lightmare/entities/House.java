package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;

public class House extends Entity {
    public static final float WIDTH = 192f;
    public static final float HEIGHT = 128f;

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
}
