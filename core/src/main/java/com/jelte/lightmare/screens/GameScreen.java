package com.jelte.lightmare.screens;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jelte.lightmare.Resources;
import com.jelte.lightmare.entities.Entity;
import com.jelte.lightmare.entities.House;
import com.jelte.lightmare.entities.Player;
import com.jelte.lightmare.entities.Resource;
import com.jelte.lightmare.input.PlayerController;
import com.jelte.lightmare.systems.EntityManager;

public class GameScreen implements Screen {
    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private EntityManager entityManager;
    private PlayerController playerController;
    private Player player;

    public GameScreen() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(320, 180, camera);
        batch = new SpriteBatch();
        entityManager = new EntityManager();
        playerController = new PlayerController();

        // Setup initial world
        House house = new House(140, 70, Resources.houseTexture);
        player = new Player(156, 80, Resources.playerTexture);
        
        entityManager.addEntity(house);
        entityManager.addEntity(player);

        // Add some resources
        entityManager.addEntity(new Resource(50, 50, Resources.resourceTexture));
        entityManager.addEntity(new Resource(250, 150, Resources.resourceTexture));
        entityManager.addEntity(new Resource(200, 30, Resources.resourceTexture));
    }

    @Override
    public void show() {
    }

    @Override
    public void render(float delta) {
        // Update logic
        float dx = playerController.getHorizontalInput();
        float dy = playerController.getVerticalInput();
        player.move(dx, dy, delta);
        
        entityManager.update(delta);

        // Interaction logic
        checkInteractions(delta);

        // Rendering
        ScreenUtils.clear(0.05f, 0.05f, 0.1f, 1f);

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();
        entityManager.render(batch);
        
        // UI: Battery bar (Wordless)
        renderUI();
        
        batch.end();
    }

    private void checkInteractions(float delta) {
        // Recharge if near house
        House house = null;
        for (Entity e : entityManager.getEntities()) {
            if (e instanceof House) {
                house = (House) e;
                break;
            }
        }

        if (house != null) {
            float dist = player.getPosition().dst(house.getPosition());
            if (dist < 40) { // Near enough to recharge
                player.recharge(delta);
            }
        }

        // Mine resources
        Entity toRemove = null;
        for (Entity e : entityManager.getEntities()) {
            if (e instanceof Resource) {
                float dist = player.getPosition().dst(e.getPosition());
                if (dist < 16) {
                    toRemove = e;
                    break;
                }
            }
        }
        if (toRemove != null) {
            entityManager.removeEntity(toRemove);
        }
    }

    private void renderUI() {
        // Draw battery background (dark gray)
        batch.setColor(Color.DARK_GRAY);
        batch.draw(Resources.pixelTexture, 10, 160, 50, 10);
        
        // Draw battery level (green to red based on level)
        float batteryPct = player.getBatteryLevel() / player.getMaxBattery();
        if (batteryPct > 0.5f) {
            batch.setColor(Color.GREEN);
        } else if (batteryPct > 0.2f) {
            batch.setColor(Color.ORANGE);
        } else {
            batch.setColor(Color.RED);
        }
        batch.draw(Resources.pixelTexture, 11, 161, 48 * batteryPct, 8);
        
        // Reset color for other renderings
        batch.setColor(Color.WHITE);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}
