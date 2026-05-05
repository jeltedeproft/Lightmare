package com.jelte.lightmare.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;

public class Monster extends Entity {
    private float speed = 40f;
    private float damagePerSecond = 10f;
    private boolean active = true;

    public Monster(float x, float y, Texture texture) {
        super(x, y, 16, 16, texture);
    }

    @Override
    public void update(float delta) {
    }

    public void updateAI(Player player, House house, float delta) {
        float distToPlayer = position.dst(player.getPosition());
        float distToHouse = position.dst(house.getPosition());

        float pLight = player.getLightRadius();
        float hLight = house.getLightRadius();

        // Check if monster is in light
        boolean inPlayerLight = pLight > 0 && distToPlayer < pLight;
        boolean inHouseLight = hLight > 0 && distToHouse < hLight;

        if (inPlayerLight || inHouseLight) {
            // Flee from player
            Vector2 fleeDir = position.cpy().sub(player.getPosition()).nor();
            position.add(fleeDir.scl(speed * 2 * delta));
        } else {
            // Move towards player
            Vector2 moveDir = player.getPosition().cpy().sub(position).nor();
            position.add(moveDir.scl(speed * delta));
            
            // Attack if close
            if (distToPlayer < 12) {
                player.takeDamage(damagePerSecond * delta);
            }
        }
    }
}
