package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jelte.lightmare.Resources;

public class Player extends Entity {
    /** Tells the move() method whether a candidate AABB is solid. Implementations
     *  typically check tile layers, entity overlaps, etc. */
    public interface CollisionChecker {
        boolean isBlocked(float x, float y, float w, float h);
    }

    public enum Facing { UP, DOWN, LEFT, RIGHT }

    private float batteryLevel = 100f;
    private float maxBattery = 100f;
    private float lightRadius = 120f;
    private float speed = 100f;
    private float hp = 100f;
    private final float maxHp = 100f;

    private final TextureRegion regionFront;
    private final TextureRegion regionBack;
    private final TextureRegion regionLeft;
    private final TextureRegion regionRight;
    private Facing facing = Facing.DOWN;

    // Salvaged robot parts overlaid on the lilguy sprite once the matching
    // upgrade is purchased (level >= 1). Headlight has no sprite yet — the
    // upgrade still applies a light-radius boost, just no visible overlay.
    private boolean hasLegs = false;
    private boolean hasDrill = false;
    private boolean hasGun = false;

    public Player(float x, float y, TextureRegion front, TextureRegion back,
                  TextureRegion left, TextureRegion right) {
        super(x, y, 16, 16, front);
        this.regionFront = front;
        this.regionBack = back;
        this.regionLeft = left;
        this.regionRight = right;
    }

    @Override
    public void update(float delta) {
        // Battery logic will be handled here or by a system
        if (batteryLevel > 0) {
            batteryLevel -= delta * 2; // Drains over time
        }
        
        if (batteryLevel < 0) batteryLevel = 0;
    }

    public void move(float dx, float dy, float delta) {
        move(dx, dy, delta, null);
    }

    /**
     * Axis-separated movement so the player slides along walls instead of
     * sticking. Each axis tries the proposed step; if the resulting AABB is
     * blocked by the checker, that axis stays put while the other still moves.
     */
    public void move(float dx, float dy, float delta, CollisionChecker checker) {
        if (dx == 0f && dy == 0f) return;

        // Dominant axis wins so diagonal input picks a single facing sprite
        // rather than flickering between two.
        if (Math.abs(dx) >= Math.abs(dy)) {
            facing = dx > 0 ? Facing.RIGHT : Facing.LEFT;
        } else {
            facing = dy > 0 ? Facing.UP : Facing.DOWN;
        }
        updateRegionForFacing();

        float length = (float) Math.sqrt(dx * dx + dy * dy);
        dx /= length;
        dy /= length;

        float stepX = dx * speed * delta;
        float stepY = dy * speed * delta;

        if (checker == null || !checker.isBlocked(position.x + stepX, position.y, size.x, size.y)) {
            position.x += stepX;
        }
        if (checker == null || !checker.isBlocked(position.x, position.y + stepY, size.x, size.y)) {
            position.y += stepY;
        }
    }

    private void updateRegionForFacing() {
        switch (facing) {
            case UP:    region = regionBack;  break;
            case DOWN:  region = regionFront; break;
            case LEFT:  region = regionLeft;  break;
            case RIGHT: region = regionRight; break;
        }
    }

    public Facing getFacing() {
        return facing;
    }

    public float getBatteryLevel() {
        return batteryLevel;
    }

    public float getMaxBattery() {
        return maxBattery;
    }

    public void recharge(float delta) {
        batteryLevel += delta * 10; // Recharge speed
        if (batteryLevel > maxBattery) batteryLevel = maxBattery;
    }

    public float getLightRadius() {
        if (batteryLevel <= 0) return 0;
        // Light radius shrinks with battery, but stays at least 50% until dead
        return lightRadius * (0.5f + 0.5f * (batteryLevel / maxBattery));
    }

    public float getHp() {
        return hp;
    }

    public float getMaxHp() {
        return maxHp;
    }

    public void takeDamage(float amount) {
        hp -= amount;
        if (hp < 0) hp = 0;
    }

    public void heal(float amount) {
        hp += amount;
        if (hp > maxHp) hp = maxHp;
    }

    public float getEmergencyLightRadius() {
        return 20f;
    }

    public float getSpeed() { return speed; }
    public void setSpeed(float s) { this.speed = s; }

    public float getMaxLightRadius() { return lightRadius; }
    public void setMaxLightRadius(float r) { this.lightRadius = r; }

    public boolean hasLegs() { return hasLegs; }
    public boolean hasDrill() { return hasDrill; }
    public boolean hasGun() { return hasGun; }
    public void setHasLegs(boolean v) { this.hasLegs = v; }
    public void setHasDrill(boolean v) { this.hasDrill = v; }
    public void setHasGun(boolean v) { this.hasGun = v; }

    // Robot part overlay placements relative to the 16x16 lilguy sprite.
    // (dx, dy) is the offset from the player's bottom-left; (w, h) is the
    // overlay's draw size in world pixels — using each sprite's native
    // dimensions so they don't get stretched.
    //   - Legs:   22x11, centered horizontally, flush with feet
    //   - Drill:  14x19, hanging from the player's right arm
    //   - Weapon: 15x20, hanging from the player's left arm
    private static final float LEGS_DX = -3f, LEGS_DY = 0f, LEGS_W = 22f, LEGS_H = 11f;
    private static final float DRILL_DX = 6f, DRILL_DY = -2f, DRILL_W = 14f, DRILL_H = 19f;
    private static final float WEAPON_DX = -5f, WEAPON_DY = -2f, WEAPON_W = 15f, WEAPON_H = 20f;

    /**
     * Draw the player body then overlay every salvaged part the player owns.
     * When facing left we mirror each part's horizontal offset around the
     * player's center and flip the sprite, so an arm-mounted part stays on the
     * same body side regardless of which way the player is looking.
     */
    @Override
    public void render(SpriteBatch batch) {
        super.render(batch);
        boolean flip = facing == Facing.LEFT;
        if (hasLegs)  drawPart(batch, Resources.robotLegsTexture,   LEGS_DX,   LEGS_DY,   LEGS_W,   LEGS_H,   flip);
        if (hasDrill) drawPart(batch, Resources.robotDrillTexture,  DRILL_DX,  DRILL_DY,  DRILL_W,  DRILL_H,  flip);
        if (hasGun)   drawPart(batch, Resources.robotWeaponTexture, WEAPON_DX, WEAPON_DY, WEAPON_W, WEAPON_H, flip);
    }

    private void drawPart(SpriteBatch batch, Texture tex,
                          float dx, float dy, float w, float h, boolean flip) {
        if (tex == null) return;
        // Mirror dx around the player's center so a right-arm part ends up on
        // the left-arm side when facing left, instead of staying glued to the
        // same world-space offset.
        float drawDx = flip ? (size.x - dx - w) : dx;
        batch.draw(tex,
            position.x + drawDx, position.y + dy, w, h,
            0, 0, tex.getWidth(), tex.getHeight(),
            flip, false);
    }
}
