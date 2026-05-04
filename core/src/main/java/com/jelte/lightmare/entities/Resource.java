package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;

public class Resource extends Entity {
    public Resource(float x, float y, Texture texture) {
        super(x, y, 16, 16, texture);
    }

    @Override
    public void update(float delta) {
    }
}
