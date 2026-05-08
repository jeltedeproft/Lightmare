package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

public abstract class Entity {
    protected Vector2 position;
    protected Vector2 size;
    protected TextureRegion region;

    public Entity(float x, float y, float width, float height, TextureRegion region) {
        this.position = new Vector2(x, y);
        this.size = new Vector2(width, height);
        this.region = region;
    }

    public Entity(float x, float y, float width, float height, Texture texture) {
        this(x, y, width, height, texture == null ? null : new TextureRegion(texture));
    }

    public abstract void update(float delta);

    public void render(SpriteBatch batch) {
        if (region != null) {
            batch.draw(region, position.x, position.y, size.x, size.y);
        }
    }

    public Vector2 getPosition() {
        return position;
    }

    public Vector2 getSize() {
        return size;
    }
}
