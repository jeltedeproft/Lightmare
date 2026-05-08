package com.jelte.lightmare.screens;

import box2dLight.PointLight;
import box2dLight.RayHandler;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jelte.lightmare.Resources;
import com.jelte.lightmare.Shaders;
import com.jelte.lightmare.entities.Entity;
import com.jelte.lightmare.entities.House;
import com.jelte.lightmare.entities.Player;
import com.jelte.lightmare.entities.Monster;
import com.jelte.lightmare.entities.Resource;
import com.jelte.lightmare.input.PlayerController;
import com.jelte.lightmare.systems.EntityManager;
import com.jelte.lightmare.systems.MonsterSystem;
import com.jelte.lightmare.systems.ParticleSystem;
import com.jelte.lightmare.systems.ResourceSystem;
import java.util.ArrayList;
import java.util.List;

public class GameScreen implements Screen {
    private enum State { PLAYING, GAMEOVER }
    private State state = State.PLAYING;

    private static final int VIRTUAL_WIDTH = 640;
    private static final int VIRTUAL_HEIGHT = 360;

    private OrthographicCamera camera;
    private OrthographicCamera fboCamera;
    private Viewport viewport;
    private float cameraTargetX, cameraTargetY;
    private SpriteBatch batch;
    private EntityManager entityManager;
    private MonsterSystem monsterSystem;
    private ResourceSystem resourceSystem;
    private ParticleSystem particleSystem;
    private PlayerController playerController;

    // Juice
    private static final float SHAKE_DURATION = 0.2f;
    private float shakeIntensity = 0f;
    private float shakeTimer = 0f;
    private float prevHp;
    private float flickerTimer = 0f;

    // House interior state
    private boolean playerInside = false;
    private Player player;
    private House house;
    private List<Resource> trail = new ArrayList<>();

    // Lighting
    private World world;
    private RayHandler rayHandler;
    private PointLight playerLight;
    private PointLight emergencyLight;
    private PointLight houseLight;

    // Pixel-art render target — sprites and lights both render into this FBO,
    // and the dither happens inside the box2dlights light shader.
    private FrameBuffer gameFbo;
    private TextureRegion fboRegion;
    private ShaderProgram ditherShader;

    // UI Effects
    private float pulseTimer = 0;

    public GameScreen() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);

        fboCamera = new OrthographicCamera();
        fboCamera.setToOrtho(false, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);

        gameFbo = new FrameBuffer(Pixmap.Format.RGBA8888, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, false);
        gameFbo.getColorBufferTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        fboRegion = new TextureRegion(gameFbo.getColorBufferTexture(), 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        fboRegion.flip(false, true);

        ditherShader = Shaders.createDitherShader();

        batch = new SpriteBatch();
        entityManager = new EntityManager();
        monsterSystem = new MonsterSystem(entityManager);
        playerController = new PlayerController();

        // Physics & Lighting setup
        world = new World(new Vector2(0, 0), true);
        rayHandler = new RayHandler(world);
        // Render lights directly into our low-res FBO instead of box2dlight's
        // internal full-resolution lightmap, so the dither shader runs at the
        // FBO pixel grid (otherwise the lightmap upscale blurs the dither).
        rayHandler.setShadows(false);
        rayHandler.setBlur(false);
        rayHandler.setLightShader(ditherShader);

        // Setup initial world
        house = new House(140, 70, Resources.houseTexture);
        player = new Player(156, 80, Resources.playerTexture);

        cameraTargetX = player.getPosition().x + 8;
        cameraTargetY = player.getPosition().y + 8;

        entityManager.addEntity(house);
        entityManager.addEntity(player);

        // Add lights
        // Monochrome lights — color is white, alpha controls relative strength
        // so the composite dither operates on a single brightness channel.
        houseLight = new PointLight(rayHandler, 128, new Color(1, 1, 1, 0.8f), 100, house.getCenterX(), house.getCenterY());
        playerLight = new PointLight(rayHandler, 64, new Color(1, 1, 1, 0.9f), player.getLightRadius(), player.getPosition().x + 8, player.getPosition().y + 8);
        emergencyLight = new PointLight(rayHandler, 32, new Color(1, 1, 1, 0.3f), player.getEmergencyLightRadius(), player.getPosition().x + 8, player.getPosition().y + 8);

        // Procedural ore spawning: a starting cluster near the player, then the
        // ResourceSystem keeps a target count seeded around them as they wander.
        resourceSystem = new ResourceSystem(entityManager, house);
        resourceSystem.seedInitial(player);

        particleSystem = new ParticleSystem();
        prevHp = player.getHp();
    }

    private void triggerShake(float intensity) {
        shakeIntensity = intensity;
        shakeTimer = SHAKE_DURATION;
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
        resourceSystem.update(delta, player);
        particleSystem.update(delta);

        // Monster contact deals continuous tick damage. Any HP drop this frame
        // refreshes the screen-shake — when contact breaks, shake decays out.
        if (player.getHp() < prevHp) {
            triggerShake(1.0f);
        }
        prevHp = player.getHp();

        if (player.getHp() <= 0) {
            state = State.GAMEOVER;
        }

        // Test player against house bounds (use sprite center) for the inside-view toggle.
        playerInside = house.containsPoint(player.getPosition().x + 8, player.getPosition().y + 8);

        // Click to Mine
        handleMining();

        // Smooth Camera Follow (LERP) — keep float precision in cameraTargetX/Y
        // and snap to integer pixels at render time to avoid sub-pixel jitter on
        // the low-res FBO. While the player holds input, ease gently. The
        // moment input releases, switch to a much faster lerp so the tail
        // collapses in ~100ms instead of dragging out ~800ms — otherwise the
        // rounded render position keeps stepping by 1 FBO pixel per frame
        // during the tail and the light circles visibly shake.
        boolean playerIdle = (dx == 0f && dy == 0f);
        float lerp = playerIdle ? 20f : 5f;
        float targetX = player.getPosition().x + 8;
        float targetY = player.getPosition().y + 8;
        float dxCam = targetX - cameraTargetX;
        float dyCam = targetY - cameraTargetY;
        if (dxCam * dxCam + dyCam * dyCam < 0.25f) {
            cameraTargetX = targetX;
            cameraTargetY = targetY;
        } else {
            cameraTargetX += dxCam * lerp * delta;
            cameraTargetY += dyCam * lerp * delta;
        }

        // Update lights
        flickerTimer += delta;
        float lightDist = player.getLightRadius();
        float battPct = player.getBatteryLevel() / player.getMaxBattery();
        if (battPct < 0.1f) {
            // Below 10% battery: light flickers, harder the closer to empty.
            // Sin gives a dying-bulb pulse, random adds the irregular jitter.
            float intensity = 1f - battPct / 0.1f; // 0 at 10%, 1 at 0%
            float flicker = 1f
                + 0.15f * intensity * MathUtils.sin(flickerTimer * 35f)
                + 0.1f * intensity * MathUtils.random(-1f, 1f);
            lightDist *= flicker;
        }
        playerLight.setPosition(player.getPosition().x + 8, player.getPosition().y + 8);
        playerLight.setDistance(lightDist);

        emergencyLight.setPosition(player.getPosition().x + 8, player.getPosition().y + 8);
        emergencyLight.setDistance(player.getEmergencyLightRadius());

        // Interaction logic
        checkInteractions(delta);

        // Compute shake offset (decays linearly from intensity to 0 over SHAKE_DURATION).
        float shakeX = 0f, shakeY = 0f;
        if (shakeTimer > 0f) {
            shakeTimer -= delta;
            if (shakeTimer < 0f) shakeTimer = 0f;
            float amount = shakeIntensity * (shakeTimer / SHAKE_DURATION);
            shakeX = MathUtils.random(-amount, amount);
            shakeY = MathUtils.random(-amount, amount);
        }

        camera.position.x = MathUtils.round(cameraTargetX + shakeX);
        camera.position.y = MathUtils.round(cameraTargetY + shakeY);
        camera.update();

        // === FBO PASS: sprites then dithered lights, both into gameFbo ===
        gameFbo.begin();
        Gdx.gl.glViewport(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        ScreenUtils.clear(0, 0, 0, 1f);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (playerInside) {
            renderInsideView();
        } else {
            entityManager.render(batch);
        }
        particleSystem.render(batch);
        renderHomeIndicator(delta);
        batch.end();

        rayHandler.setCombinedMatrix(camera);
        rayHandler.updateAndRender();
        gameFbo.end();

        // === SCREEN PASS: blit FBO with letterboxed nearest-neighbor scaling ===
        // apply(false): set the letterboxed GL viewport without re-centering the
        // world camera. apply(true) would clobber camera.position to (320, 180)
        // and break next frame's handleMining() unproject.
        viewport.apply(false);
        ScreenUtils.clear(0, 0, 0, 1f);

        fboCamera.update();
        batch.setProjectionMatrix(fboCamera.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(fboRegion, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        batch.end();

        // UI: Battery bar (Wordless) - Rendered in screen space
        renderUI();
    }

    private void handleMining() {
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            // viewport.unproject uses the letterboxed GL viewport (not the full
            // canvas), so clicks map correctly even when the window aspect
            // doesn't match 16:9. camera.unproject(Vector3) would be wrong then.
            Vector3 worldCoords = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(worldCoords);
            for (Entity e : entityManager.getEntities()) {
                if (e instanceof Resource && !((Resource) e).isMined()) {
                    if (worldCoords.x >= e.getPosition().x && worldCoords.x <= e.getPosition().x + e.getSize().x &&
                        worldCoords.y >= e.getPosition().y && worldCoords.y <= e.getPosition().y + e.getSize().y) {
                        
                        // Check if player is near
                        if (player.getPosition().dst(e.getPosition()) < 50) {
                            Resource r = (Resource) e;
                            boolean finished = r.click();
                            // Per-click particle burst; shake on the finishing click.
                            particleSystem.burst(
                                r.getPosition().x + r.getSize().x / 2f,
                                r.getPosition().y + r.getSize().y / 2f,
                                MathUtils.random(4, 5));
                            if (finished) {
                                Entity followTarget = trail.isEmpty() ? player : trail.get(trail.size() - 1);
                                r.setMined(true, followTarget);
                                trail.add(r);
                                triggerShake(2.5f);
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
        camera.position.set(320, 180, 0);
        camera.update();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        // Visual "X" or dead icon instead of words
        batch.setColor(Color.WHITE);
        batch.draw(Resources.restartTexture, 304, 164, 32, 32);
        batch.end();

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            Vector3 uiCoords = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(uiCoords);
            if (uiCoords.x >= 304 && uiCoords.x <= 336 && uiCoords.y >= 164 && uiCoords.y <= 196) {
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
            float angle = MathUtils.atan2(house.getCenterY() - (player.getPosition().y + 8),
                                         house.getCenterX() - (player.getPosition().x + 8)) * MathUtils.radiansToDegrees;
            
            batch.setColor(1, 1, 1, alpha);
            // Draw arrow 32 pixels away from player toward house
            float arrowX = player.getPosition().x + 8 + MathUtils.cosDeg(angle) * 32 - 8;
            float arrowY = player.getPosition().y + 8 + MathUtils.sinDeg(angle) * 32 - 8;
            
            batch.draw(Resources.arrowTexture, arrowX, arrowY, 8, 8, 16, 16, 1, 1, angle - 90, 0, 0, 16, 16, false, false);
            batch.setColor(Color.WHITE);
        }
    }

    private void renderInsideView() {
        // Outside entities are skipped entirely — when you step indoors, the
        // doors close and you can't see the outside world. The FBO's black
        // clear stays where outside-the-house pixels would otherwise be drawn.
        Vector2 hp = house.getPosition();
        Vector2 hs = house.getSize();
        float wallThickness = 8f;

        // Walls — fill the whole house footprint with a dark brown.
        batch.setColor(0.28f, 0.18f, 0.08f, 1f);
        batch.draw(Resources.pixelTexture, hp.x, hp.y, hs.x, hs.y);

        // Floor — lighter, inset by wallThickness on all sides.
        batch.setColor(0.55f, 0.4f, 0.22f, 1f);
        batch.draw(Resources.pixelTexture,
            hp.x + wallThickness, hp.y + wallThickness,
            hs.x - 2f * wallThickness, hs.y - 2f * wallThickness);

        // Door gap at the bottom-center of the south wall — visual cue for
        // where the player walked in (and where they walk out).
        float doorW = 32f;
        float doorX = hp.x + (hs.x - doorW) * 0.5f;
        batch.setColor(0.65f, 0.5f, 0.28f, 1f);
        batch.draw(Resources.pixelTexture, doorX, hp.y, doorW, wallThickness);

        // Placeholder storage chest near the back (north) wall.
        batch.setColor(0.18f, 0.1f, 0.04f, 1f);
        float storW = 40f, storH = 22f;
        float storX = hp.x + (hs.x - storW) * 0.5f;
        float storY = hp.y + hs.y - wallThickness - storH - 12f;
        batch.draw(Resources.pixelTexture, storX, storY, storW, storH);

        batch.setColor(Color.WHITE);
        player.render(batch);
    }

    private void checkInteractions(float delta) {
        // Recharge / deposit happens whenever the player is actually inside the
        // house. Uses the same flag the renderer uses for the interior view.
        if (playerInside) {
            player.recharge(delta);

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
        gameFbo.dispose();
        ditherShader.dispose();
    }
}
