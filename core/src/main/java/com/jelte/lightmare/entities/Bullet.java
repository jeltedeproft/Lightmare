package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.jelte.lightmare.Resources;

/**
 * Straight-line projectile fired by the player once the gun upgrade is maxed.
 * Travels at constant velocity until it hits a monster, hits the boss, or runs
 * out of range — at which point GameScreen removes it from the entity list.
 *
 * Renders as a small bright square; we deliberately avoid sprite art so the
 * bullet reads as a pure light dot inside the dithered darkness.
 */
public class Bullet extends Entity {
    public static final float SPEED = 260f;
    public static final float MAX_DISTANCE = 160f;
    private static final float SIZE = 3f;

    private final Vector2 velocity;
    private float traveled = 0f;
    private boolean spent = false;

    public Bullet(float x, float y, float dirX, float dirY) {
        super(x, y, SIZE, SIZE, (com.badlogic.gdx.graphics.g2d.TextureRegion) null);
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 0.0001f) { dirX = 1f; dirY = 0f; len = 1f; }
        this.velocity = new Vector2(dirX / len * SPEED, dirY / len * SPEED);
    }

    @Override
    public void update(float delta) {
        if (spent) return;
        float dx = velocity.x * delta;
        float dy = velocity.y * delta;
        position.add(dx, dy);
        traveled += (float) Math.sqrt(dx * dx + dy * dy);
        if (traveled >= MAX_DISTANCE) spent = true;
    }

    public boolean isSpent() { return spent; }
    public void markSpent() { spent = true; }

    public boolean overlaps(Entity e) {
        Vector2 ep = e.getPosition();
        Vector2 es = e.getSize();
        return position.x < ep.x + es.x
            && position.x + size.x > ep.x
            && position.y < ep.y + es.y
            && position.y + size.y > ep.y;
    }

    @Override
    public void render(SpriteBatch batch) {
        if (spent) return;
        batch.setColor(1f, 0.2f, 0.15f, 1f);
        batch.draw(Resources.pixelTexture, position.x, position.y, size.x, size.y);
        batch.setColor(Color.WHITE);
    }
}
