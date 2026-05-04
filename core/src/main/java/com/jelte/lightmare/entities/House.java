package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;

public class House extends Entity {
    private float lightRadius = 100f;

    public House(float x, float y, Texture texture) {
        super(x, y, 48, 48, texture);
    }

    @Override
    public void update(float delta) {
    }

    public float getLightRadius() {
        return lightRadius;
    }
}
