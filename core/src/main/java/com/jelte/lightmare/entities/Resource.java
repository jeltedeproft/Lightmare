package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

public class Resource extends Entity {
    private boolean mined = false;
    private int clicksRequired = 3;
    private int currentClicks = 0;
    private Entity followTarget;
    private float followSpeed = 150f;
    private float stopDistance = 12f;

    public Resource(float x, float y, Texture texture) {
        super(x, y, 16, 16, texture);
    }

    public Resource(float x, float y, TextureRegion region) {
        super(x, y, 16, 16, region);
    }

    @Override
    public void update(float delta) {
        if (mined && followTarget != null) {
            float dist = position.dst(followTarget.getPosition());
            if (dist > stopDistance) {
                Vector2 moveDir = followTarget.getPosition().cpy().sub(position).nor();
                position.add(moveDir.scl(followSpeed * delta));
            }
        }
    }

    public boolean click() {
        if (mined) return false;
        currentClicks++;
        // Visual feedback: shrink slightly per click
        float scale = 1.0f - ((float)currentClicks / (clicksRequired + 1)) * 0.5f;
        size.set(16 * scale, 16 * scale);
        
        if (currentClicks >= clicksRequired) {
            return true;
        }
        return false;
    }

    public void setMined(boolean mined, Entity target) {
        this.mined = mined;
        this.followTarget = target;
        this.size.set(8, 8); // Final trail size
    }

    public boolean isMined() {
        return mined;
    }
}
