package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Story prop placed outside the house at game start: the broken robot whose
 * salvaged parts the player welds onto themselves over the course of the run.
 * Swaps to the "working" sprite once every part has been repaired — a quiet
 * acknowledgement that the robot is whole again right before the boss arc.
 */
public class BrokenRobot extends Entity {
    public static final float WIDTH = 24f;
    public static final float HEIGHT = 24f;

    private final TextureRegion brokenRegion;
    private final TextureRegion workingRegion;
    private boolean repaired = false;

    public BrokenRobot(float x, float y, Texture broken, Texture working) {
        super(x, y, WIDTH, HEIGHT, broken);
        this.brokenRegion = this.region;
        this.workingRegion = new TextureRegion(working);
    }

    @Override
    public void update(float delta) {
    }

    public void setRepaired() {
        if (repaired) return;
        repaired = true;
        this.region = workingRegion;
    }

    public boolean isRepaired() { return repaired; }
}
