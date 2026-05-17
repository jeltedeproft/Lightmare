package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jelte.lightmare.Resources;

/**
 * The broken mech that sits inside the house at game start. Players bolt
 * salvaged parts (legs, drill, weapon) onto it by depositing ore; once all
 * three are attached the mech comes online, walks outside, and starts mining
 * and shooting on its own. The blue ore (VISION) buffs its detection range
 * separately.
 *
 * Render uses combination textures keyed by which parts are attached so the
 * sprite always reflects current state without needing manual overlays. Slots
 * 0 (no parts) and 7 (all parts) reuse the brokenRobot/workingRobot anchors;
 * the six intermediate slots are optional and fall back to brokenRobot if the
 * art isn't drawn yet.
 */
public class BrokenRobot extends Entity {
    public static final float WIDTH = 48f;
    public static final float HEIGHT = 48f;

    public enum State { BROKEN, ACTIVE }
    private State state = State.BROKEN;

    private boolean hasLegs = false;
    private boolean hasDrill = false;
    private boolean hasWeapon = false;

    public BrokenRobot(float x, float y) {
        super(x, y, WIDTH, HEIGHT, (TextureRegion) null);
        refreshRegion();
    }

    @Override
    public void update(float delta) {
        // AI runs externally in GameScreen.updateMechAI so it has access to
        // ores, monsters, the boss, and the bullet pool.
    }

    public boolean isActive() { return state == State.ACTIVE; }
    public boolean hasLegs() { return hasLegs; }
    public boolean hasDrill() { return hasDrill; }
    public boolean hasWeapon() { return hasWeapon; }

    public void attachLegs()   { if (!hasLegs)   { hasLegs   = true; refreshRegion(); tryActivate(); } }
    public void attachDrill()  { if (!hasDrill)  { hasDrill  = true; refreshRegion(); tryActivate(); } }
    public void attachWeapon() { if (!hasWeapon) { hasWeapon = true; refreshRegion(); tryActivate(); } }

    private void tryActivate() {
        if (state == State.BROKEN && hasLegs && hasDrill && hasWeapon) {
            state = State.ACTIVE;
            refreshRegion();
        }
    }

    /**
     * Manually relocate the mech — used by GameScreen when the mech activates
     * and needs to step outside the house.
     */
    public void setPosition(float x, float y) {
        position.set(x, y);
    }

    /**
     * Pick the combination sprite that matches the currently attached parts.
     * Index encoding: bit 2 = legs, bit 1 = drill, bit 0 = weapon. Missing
     * artwork falls back to the plain brokenRobot sprite so the game runs
     * even before the six intermediate combinations are drawn.
     */
    private void refreshRegion() {
        int idx = (hasLegs ? 4 : 0) | (hasDrill ? 2 : 0) | (hasWeapon ? 1 : 0);
        Texture tex = Resources.mechCombinationTextures[idx];
        if (tex == null) tex = Resources.brokenRobotTexture;
        this.region = new TextureRegion(tex);
    }

    @Override
    public void render(SpriteBatch batch) {
        if (region != null) {
            batch.draw(region, position.x, position.y, size.x, size.y);
        }
    }
}
