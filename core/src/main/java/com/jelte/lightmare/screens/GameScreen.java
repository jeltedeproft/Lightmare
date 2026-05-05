package com.jelte.lightmare.screens;

import box2dLight.PointLight;
import box2dLight.RayHandler;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jelte.lightmare.Resources;
import com.jelte.lightmare.entities.Entity;
import com.jelte.lightmare.entities.House;
import com.jelte.lightmare.entities.Player;
import com.jelte.lightmare.entities.Monster;
import com.jelte.lightmare.entities.Resource;
import com.jelte.lightmare.input.PlayerController;
import com.jelte.lightmare.systems.EntityManager;
import com.jelte.lightmare.systems.MonsterSystem;

public class GameScreen implements Screen {
    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private EntityManager entityManager;
    private MonsterSystem monsterSystem;
    private PlayerController playerController;
    private Player player;

    // Lighting
    private World world;
    private RayHandler rayHandler;
    private PointLight playerLight;
    private PointLight houseLight;

    public GameScreen() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(320, 180, camera);
        batch = new SpriteBatch();
        entityManager = new EntityManager();
        monsterSystem = new MonsterSystem(entityManager);
        playerController = new PlayerController();

        // Physics & Lighting setup
        world = new World(new Vector2(0, 0), true);
        rayHandler = new RayHandler(world);
        rayHandler.setAmbientLight(0.05f, 0.05f, 0.1f, 0.1f); // Very dark blue night

        // Setup initial world
        House house = new House(140, 70, Resources.houseTexture);
        player = new Player(156, 80, Resources.playerTexture);
        
        entityManager.addEntity(house);
        entityManager.addEntity(player);

        // Add lights
        houseLight = new PointLight(rayHandler, 128, new Color(1, 0.9f, 0.7f, 0.8f), 100, house.getPosition().x + 24, house.getPosition().y + 24);
        playerLight = new PointLight(rayHandler, 64, new Color(1, 1, 0.8f, 0.9f), player.getLightRadius(), player.getPosition().x + 8, player.getPosition().y + 8);

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
        monsterSystem.update(delta, player);

        // Smooth Camera Follow (LERP)
        float lerp = 5f;
        camera.position.x += (player.getPosition().x + 8 - camera.position.x) * lerp * delta;
        camera.position.y += (player.getPosition().y + 8 - camera.position.y) * lerp * delta;

        // Update lights
        playerLight.setPosition(player.getPosition().x + 8, player.getPosition().y + 8);
        playerLight.setDistance(player.getLightRadius());

        // Interaction logic
        checkInteractions(delta);

        // Rendering
        ScreenUtils.clear(0, 0, 0, 1f); // Clear to absolute black

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();
        entityManager.render(batch);
        batch.end();

        // Render Lights
        rayHandler.setCombinedMatrix(camera);
        rayHandler.updateAndRender();

        // UI: Battery bar (Wordless) - Rendered in screen space
        renderUI();
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
        // Use a temporary matrix to draw UI in screen space (ignoring camera move)
        batch.getProjectionMatrix().setToOrtho2D(0, 0, 320, 180);
        batch.begin();

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
        batch.end();

        // Restore camera projection for next frame
        batch.setProjectionMatrix(camera.combined);
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
