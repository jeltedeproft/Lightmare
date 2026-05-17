package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Static flickering torch sprite. The atlas stores three keyframes
 * (torch_1/_2/_3) which we cycle through to fake the flame motion. The
 * torch itself doesn't emit light — GameScreen pairs each Torch with a
 * PointLight at spawn time so the lighting system stays centralized.
 */
public class Torch extends Entity {
    public static final float WIDTH = 16f;
    public static final float HEIGHT = 16f;
    private static final float FRAME_DURATION = 0.18f;

    private final TextureRegion[] frames;
    private float timer = 0f;
    private int currentFrame = 0;

    public Torch(float x, float y, TextureRegion[] frames) {
        super(x, y, WIDTH, HEIGHT, frames[0]);
        this.frames = frames;
    }

    @Override
    public void update(float delta) {
        timer += delta;
        if (timer >= FRAME_DURATION) {
            timer -= FRAME_DURATION;
            currentFrame = (currentFrame + 1) % frames.length;
            this.region = frames[currentFrame];
        }
    }

    public int getCurrentFrame() { return currentFrame; }
}
