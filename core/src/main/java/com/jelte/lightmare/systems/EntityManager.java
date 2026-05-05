package com.jelte.lightmare.systems;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.jelte.lightmare.entities.Entity;
import com.jelte.lightmare.entities.Player;
import com.jelte.lightmare.entities.House;
import java.util.ArrayList;
import java.util.List;

public class EntityManager {
    private List<Entity> entities;
    private Player player;
    private House house;

    public EntityManager() {
        entities = new ArrayList<>();
    }

    public void addEntity(Entity entity) {
        entities.add(entity);
        if (entity instanceof Player) {
            player = (Player) entity;
        } else if (entity instanceof House) {
            house = (House) entity;
        }
    }

    public void update(float delta) {
        for (Entity entity : entities) {
            entity.update(delta);
        }
    }

    public void render(SpriteBatch batch) {
        for (Entity entity : entities) {
            entity.render(batch);
        }
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public void removeEntity(Entity entity) {
        entities.remove(entity);
    }

    public House getHouse() {
        return house;
    }
}
