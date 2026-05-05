package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;

public class Resource extends Entity {
    private boolean mined = false;
    private Entity followTarget;
    private float followSpeed = 150f;
    private float stopDistance = 12f;

    public Resource(float x, float y, Texture texture) {
        super(x, y, 16, 16, texture);
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

    public void setMined(boolean mined, Entity target) {
        this.mined = mined;
        this.followTarget = target;
        this.size.set(8, 8); // Make it smaller when mined for the trail
    }

    public boolean isMined() {
        return mined;
    }
}
