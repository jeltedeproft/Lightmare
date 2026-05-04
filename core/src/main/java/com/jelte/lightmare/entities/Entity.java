package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;

public abstract class Entity {
    protected Vector2 position;
    protected Vector2 size;
    protected Texture texture;

    public Entity(float x, float y, float width, float height, Texture texture) {
        this.position = new Vector2(x, y);
        this.size = new Vector2(width, height);
        this.texture = texture;
    }

    public abstract void update(float delta);

    public void render(SpriteBatch batch) {
        if (texture != null) {
            batch.draw(texture, position.x, position.y, size.x, size.y);
        }
    }

    public Vector2 getPosition() {
        return position;
    }

    public Vector2 getSize() {
        return size;
    }
}
