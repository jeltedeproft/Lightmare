package com.jelte.lightmare.systems;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.jelte.lightmare.Resources;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lightweight particle pool for visual juice — small grey pixel sprites that
 * fly out from a point, slow with drag, and fade as their life ticks down.
 */
public class ParticleSystem {
    private static class Particle {
        final Vector2 position = new Vector2();
        final Vector2 velocity = new Vector2();
        float life;
        float maxLife;
        float size;
        float gray;
    }

    private final List<Particle> particles = new ArrayList<>();

    /** Spawn `count` particles flying out from (x, y) in random directions. */
    public void burst(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.position.set(x, y);
            float angle = MathUtils.random(0f, MathUtils.PI2);
            float speed = MathUtils.random(20f, 60f);
            p.velocity.set(MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed);
            p.maxLife = MathUtils.random(0.4f, 0.8f);
            p.life = p.maxLife;
            p.size = MathUtils.random(1f, 2f);
            p.gray = MathUtils.random(0.4f, 0.8f);
            particles.add(p);
        }
    }

    public void update(float delta) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.life -= delta;
            if (p.life <= 0f) {
                it.remove();
                continue;
            }
            p.position.x += p.velocity.x * delta;
            p.position.y += p.velocity.y * delta;
            // Drag — particles ease to a stop instead of sliding forever.
            p.velocity.scl(Math.max(0f, 1f - 4f * delta));
        }
    }

    public void render(SpriteBatch batch) {
        for (Particle p : particles) {
            float alpha = p.life / p.maxLife;
            batch.setColor(p.gray, p.gray, p.gray, alpha);
            batch.draw(Resources.pixelTexture, p.position.x, p.position.y, p.size, p.size);
        }
        batch.setColor(Color.WHITE);
    }
}
