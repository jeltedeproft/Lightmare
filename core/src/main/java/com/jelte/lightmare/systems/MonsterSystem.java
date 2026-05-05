package com.jelte.lightmare.systems;

import com.badlogic.gdx.math.MathUtils;
import com.jelte.lightmare.Resources;
import com.jelte.lightmare.entities.Monster;
import com.jelte.lightmare.entities.Player;

public class MonsterSystem {
    private EntityManager entityManager;
    private float spawnTimer = 0f;
    private float spawnInterval = 5f; // Every 5 seconds

    public MonsterSystem(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void update(float delta, Player player) {
        spawnTimer += delta;
        if (spawnTimer >= spawnInterval) {
            spawnTimer = 0;
            spawnMonster(player);
        }

        // AI update for all monsters
        for (int i = 0; i < entityManager.getEntities().size(); i++) {
            Object e = entityManager.getEntities().get(i);
            if (e instanceof Monster) {
                ((Monster) e).updateAI(player, entityManager.getHouse(), delta);
            }
        }
    }

    private void spawnMonster(Player player) {
        // Spawn monster outside the player's view/light
        float angle = MathUtils.random(0, MathUtils.PI2);
        float distance = MathUtils.random(200, 300);
        float x = player.getPosition().x + MathUtils.cos(angle) * distance;
        float y = player.getPosition().y + MathUtils.sin(angle) * distance;

        entityManager.addEntity(new Monster(x, y, Resources.monsterTexture));
    }
}
