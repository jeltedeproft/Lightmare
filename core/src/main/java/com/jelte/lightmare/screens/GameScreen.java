package com.jelte.lightmare.screens;

import box2dLight.PointLight;
import box2dLight.RayHandler;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
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
import java.util.ArrayList;
import java.util.List;

public class GameScreen implements Screen {
    private enum State { PLAYING, GAMEOVER }
    private State state = State.PLAYING;

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private EntityManager entityManager;
    private MonsterSystem monsterSystem;
    private PlayerController playerController;
    private Player player;
    private House house;
    private List<Resource> trail = new ArrayList<>();

    // Lighting
    private World world;
    private RayHandler rayHandler;
    private PointLight playerLight;
    private PointLight emergencyLight;
    private PointLight houseLight;

    // UI Effects
    private float pulseTimer = 0;

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
        house = new House(140, 70, Resources.houseTexture);
        player = new Player(156, 80, Resources.playerTexture);
        
        entityManager.addEntity(house);
        entityManager.addEntity(player);

        // Add lights
        houseLight = new PointLight(rayHandler, 128, new Color(1, 0.9f, 0.7f, 0.8f), 100, house.getPosition().x + 24, house.getPosition().y + 24);
        playerLight = new PointLight(rayHandler, 64, new Color(1, 1, 0.8f, 0.9f), player.getLightRadius(), player.getPosition().x + 8, player.getPosition().y + 8);
        emergencyLight = new PointLight(rayHandler, 32, new Color(0.2f, 0.2f, 0.5f, 0.3f), player.getEmergencyLightRadius(), player.getPosition().x + 8, player.getPosition().y + 8);

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
        if (state == State.GAMEOVER) {
            renderGameOver();
            return;
        }

        // Update logic
        float dx = playerController.getHorizontalInput();
        float dy = playerController.getVerticalInput();
        player.move(dx, dy, delta);
        
        entityManager.update(delta);
        monsterSystem.update(delta, player);

        if (player.getHp() <= 0) {
            state = State.GAMEOVER;
        }

        // Click to Mine
        handleMining();

        // Smooth Camera Follow (LERP)
        float lerp = 5f;
        camera.position.x += (player.getPosition().x + 8 - camera.position.x) * lerp * delta;
        camera.position.y += (player.getPosition().y + 8 - camera.position.y) * lerp * delta;

        // Update lights
        playerLight.setPosition(player.getPosition().x + 8, player.getPosition().y + 8);
        playerLight.setDistance(player.getLightRadius());
        
        emergencyLight.setPosition(player.getPosition().x + 8, player.getPosition().y + 8);
        emergencyLight.setDistance(player.getEmergencyLightRadius());

        // Interaction logic
        checkInteractions(delta);

        // Rendering
        ScreenUtils.clear(0, 0, 0, 1f); // Clear to absolute black

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();
        entityManager.render(batch);
        
        // Render Home Indicator Pulse
        renderHomeIndicator(delta);
        
        batch.end();

        // Render Lights
        rayHandler.setCombinedMatrix(camera);
        rayHandler.updateAndRender();

        // UI: Battery bar (Wordless) - Rendered in screen space
        renderUI();
    }

    private void handleMining() {
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            Vector3 worldCoords = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
            for (Entity e : entityManager.getEntities()) {
                if (e instanceof Resource && !((Resource) e).isMined()) {
                    if (worldCoords.x >= e.getPosition().x && worldCoords.x <= e.getPosition().x + e.getSize().x &&
                        worldCoords.y >= e.getPosition().y && worldCoords.y <= e.getPosition().y + e.getSize().y) {
                        
                        // Check if player is near
                        if (player.getPosition().dst(e.getPosition()) < 50) {
                            Resource r = (Resource) e;
                            if (r.click()) {
                                Entity followTarget = trail.isEmpty() ? player : trail.get(trail.size() - 1);
                                r.setMined(true, followTarget);
                                trail.add(r);
                            }
                        }
                    }
                }
            }
        }
    }

    private void renderGameOver() {
        ScreenUtils.clear(0.1f, 0, 0, 1f); // Dark red screen
        
        // Reset camera to center of virtual screen for UI
        camera.position.set(160, 90, 0);
        camera.update();
        
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        // Visual "X" or dead icon instead of words
        batch.setColor(Color.WHITE);
        batch.draw(Resources.restartTexture, 144, 74, 32, 32); 
        batch.end();

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            Vector3 uiCoords = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(uiCoords); 
            if (uiCoords.x >= 144 && uiCoords.x <= 176 && uiCoords.y >= 74 && uiCoords.y <= 106) {
                ((com.badlogic.gdx.Game)Gdx.app.getApplicationListener()).setScreen(new GameScreen());
            }
        }
    }

    private void renderHomeIndicator(float delta) {
        float batteryPct = player.getBatteryLevel() / player.getMaxBattery();
        if (batteryPct < 0.25f) {
            pulseTimer += delta * (1.0f - batteryPct) * 10f; // Pulses faster when lower
            float alpha = 0.3f + 0.4f * MathUtils.sin(pulseTimer);
            
            // Calculate angle to house
            float angle = MathUtils.atan2(house.getPosition().y + 24 - (player.getPosition().y + 8), 
                                         house.getPosition().x + 24 - (player.getPosition().x + 8)) * MathUtils.radiansToDegrees;
            
            batch.setColor(1, 1, 1, alpha);
            // Draw arrow 32 pixels away from player toward house
            float arrowX = player.getPosition().x + 8 + MathUtils.cosDeg(angle) * 32 - 8;
            float arrowY = player.getPosition().y + 8 + MathUtils.sinDeg(angle) * 32 - 8;
            
            batch.draw(Resources.arrowTexture, arrowX, arrowY, 8, 8, 16, 16, 1, 1, angle - 90, 0, 0, 16, 16, false, false);
            batch.setColor(Color.WHITE);
        }
    }

    private void checkInteractions(float delta) {
        // Recharge if near house
        float dist = player.getPosition().dst(house.getPosition());
        if (dist < 40) { // Near enough to recharge
            player.recharge(delta);
            
            // Deposit ores
            if (!trail.isEmpty()) {
                for (Resource r : trail) {
                    entityManager.removeEntity(r);
                }
                trail.clear();
                player.heal(delta * 20); // Healing when at house
            }
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

        // Draw HP bar
        batch.setColor(Color.DARK_GRAY);
        batch.draw(Resources.pixelTexture, 10, 145, 50, 10);
        batch.setColor(Color.SCARLET);
        float hpPct = player.getHp() / player.getMaxHp();
        batch.draw(Resources.pixelTexture, 11, 146, 48 * hpPct, 8);
        
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
        rayHandler.dispose();
        world.dispose();
    }
}
