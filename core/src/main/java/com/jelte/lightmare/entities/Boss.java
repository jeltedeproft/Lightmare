package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Two-phase boss: starts wearing an intimidating "shell" and, on the first
 * hit, reveals a much smaller, cute creature underneath — the twist that
 * recontextualises everything the player has been doing. After the shell
 * cracks the boss keeps the same HP pool and just takes hits until dead.
 */
public class Boss extends Entity {
    private static final float SHELL_W = 64f;
    private static final float SHELL_H = 64f;
    private static final float CUTE_W = 32f;
    private static final float CUTE_H = 32f;
    private static final int MAX_HP = 7;
    /** Hits before the shell shatters. Remaining hits play out in cute phase. */
    private static final int SHELL_HITS = 3;
    private static final float SHELL_CHASE_SPEED = 38f;
    private static final float CUTE_FLEE_SPEED = 55f;
    private static final float CONTACT_RANGE = 22f;
    private static final float CONTACT_DPS = 18f;
    private static final float FLASH_DURATION = 0.18f;

    private final TextureRegion shellRegion;
    private final TextureRegion cuteRegion;
    private boolean shellBroken = false;
    private int hp = MAX_HP;
    private float flashTimer = 0f;

    public Boss(float x, float y, Texture shell, Texture cute) {
        super(x, y, SHELL_W, SHELL_H, shell);
        this.shellRegion = this.region; // Entity ctor wrapped shell as the region
        this.cuteRegion = new TextureRegion(cute);
    }

    @Override
    public void update(float delta) {
        if (flashTimer > 0f) flashTimer -= delta;
    }

    /**
     * Phase-dependent movement. Shell phase chases the player and applies
     * contact damage on close range — the fight needs the player moving.
     * Cute phase flees, so the player has to chase a tiny target down.
     * The house perimeter is solid for the boss (no door access).
     */
    public void updateAI(Player player, House house, float delta) {
        if (isDead()) return;
        Vector2 toPlayer = new Vector2(
            player.getPosition().x - position.x,
            player.getPosition().y - position.y);
        float dist = toPlayer.len();
        if (dist > 0.001f) toPlayer.scl(1f / dist);

        if (!shellBroken) {
            moveBlockedByHouse(toPlayer.x * SHELL_CHASE_SPEED * delta,
                               toPlayer.y * SHELL_CHASE_SPEED * delta, house);
            if (dist < CONTACT_RANGE) {
                player.takeDamage(CONTACT_DPS * delta);
            }
        } else {
            moveBlockedByHouse(-toPlayer.x * CUTE_FLEE_SPEED * delta,
                               -toPlayer.y * CUTE_FLEE_SPEED * delta, house);
        }
    }

    private void moveBlockedByHouse(float dx, float dy, House house) {
        float newX = position.x + dx;
        if (!house.isBlockedByWall(newX, position.y, size.x, size.y, false)) {
            position.x = newX;
        }
        float newY = position.y + dy;
        if (!house.isBlockedByWall(position.x, newY, size.x, size.y, false)) {
            position.y = newY;
        }
    }

    /** @return true if this hit killed the boss. */
    public boolean takeHit() {
        hp--;
        flashTimer = FLASH_DURATION;
        if (!shellBroken && hp <= MAX_HP - SHELL_HITS) {
            shellBroken = true;
            region = cuteRegion;
            // Shrink and re-center so the cute body stays where the shell was.
            position.x += (SHELL_W - CUTE_W) * 0.5f;
            position.y += (SHELL_H - CUTE_H) * 0.5f;
            size.set(CUTE_W, CUTE_H);
        }
        return hp <= 0;
    }

    public boolean isDead() { return hp <= 0; }
    public boolean isShellBroken() { return shellBroken; }

    @Override
    public void render(SpriteBatch batch) {
        if (region == null) return;
        float ox = 0f, oy = 0f;
        boolean tinted = false;
        if (flashTimer > 0f) {
            ox = MathUtils.random(-1.5f, 1.5f);
            oy = MathUtils.random(-1.5f, 1.5f);
            batch.setColor(1f, 0.6f, 0.6f, 1f);
            tinted = true;
        }
        batch.draw(region, position.x + ox, position.y + oy, size.x, size.y);
        if (tinted) batch.setColor(Color.WHITE);
    }
}
