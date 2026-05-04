package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;

public class Monster extends Entity {
    public Monster(float x, float y, Texture texture) {
        super(x, y, 16, 16, texture);
    }

    @Override
    public void update(float delta) {
        // AI logic to avoid light and move towards player if dark
    }
}
