package com.jelte.lightmare.screens;

import box2dLight.PointLight;
import box2dLight.RayHandler;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.jelte.lightmare.maps.InfiniteTmxMapLoader;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jelte.lightmare.Resources;
import com.jelte.lightmare.Shaders;
import com.jelte.lightmare.entities.Boss;
import com.jelte.lightmare.entities.Entity;
import com.jelte.lightmare.entities.House;
import com.jelte.lightmare.entities.Player;
import com.jelte.lightmare.entities.Monster;
import com.jelte.lightmare.entities.Resource;
import com.jelte.lightmare.input.PlayerController;
import com.jelte.lightmare.systems.EntityManager;
import com.jelte.lightmare.systems.MonsterSystem;
import com.jelte.lightmare.systems.MusicSystem;
import com.jelte.lightmare.systems.ParticleSystem;
import com.jelte.lightmare.systems.ResourceSystem;
import com.jelte.lightmare.systems.UpgradeSystem;
import com.jelte.lightmare.systems.UpgradeSystem.Upgrade;
import java.util.ArrayList;
import java.util.List;

public class GameScreen implements Screen {
    /**
     * Game flow:
     *   PLAYING ─[deposit threshold reached]─▶ BOSS_INTRO (~1.8s cinematic pan)
     *   BOSS_INTRO ─▶ PLAYING (boss is now an active entity)
     *   PLAYING ─[boss killed]─▶ WITHER (~3.5s red overlay ramps up)
     *   WITHER ─▶ END (silent end screen with restart)
     *   PLAYING ─[HP=0]─▶ GAMEOVER
     */
    private enum State { PLAYING, BOSS_INTRO, WITHER, END, GAMEOVER }
    private State state = State.PLAYING;

    // Boss arc
    private static final int BOSS_DEPOSIT_THRESHOLD = 25;
    private static final float BOSS_INTRO_DURATION = 1.8f;
    private static final float WITHER_DURATION = 3.5f;
    private Boss boss;
    private float bossIntroTimer = 0f;
    private float witherTimer = 0f;
    private int totalDeposits = 0;

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
    private MusicSystem musicSystem;
    private UpgradeSystem upgradeSystem;
    private PlayerController playerController;

    // Upgrade machine + panel state. Machine position is set once the house
    // is built (see constructor) and lives in world space so the same AABB
    // can be used for both rendering and click detection.
    private static final float MACHINE_W = 32f;
    private static final float MACHINE_H = 32f;
    private float machineX, machineY;
    private boolean upgradePanelOpen = false;

    // Juice
    private static final float SHAKE_DURATION = 0.2f;
    private float shakeIntensity = 0f;
    private float shakeTimer = 0f;
    private float prevHp;
    private float flickerTimer = 0f;

    // House interior state
    private boolean playerInside = false;
    /** Tinted bodies for the 4 storage chests, parallel to Resources.oreRegions. */
    private static final Color[] STORAGE_COLORS = {
        new Color(0.25f, 0.45f, 0.75f, 1f),  // blue
        new Color(0.30f, 0.60f, 0.30f, 1f),  // green
        new Color(0.85f, 0.55f, 0.20f, 1f),  // orange
        new Color(0.55f, 0.25f, 0.65f, 1f),  // purple
    };
    /** Per-variant count of deposited ore. Indexed same as Resources.oreRegions. */
    private final int[] storageCounts = new int[4];
    private Player player;
    private House house;
    private List<Resource> trail = new ArrayList<>();

    // Lighting
    private World world;
    private RayHandler rayHandler;
    private PointLight playerLight;
    private PointLight emergencyLight;
    private PointLight houseLight;

    // Pixel-art render targets. The world is drawn at full brightness into
    // gameFbo. Lights (with the dither shader) are drawn additively into a
    // separate lightFbo cleared to black, producing a "light intensity mask".
    // We then multiply gameFbo by lightFbo so unlit areas go pitch black and
    // lit areas reveal the world's actual colors.
    private FrameBuffer gameFbo;
    private FrameBuffer lightFbo;
    private TextureRegion fboRegion;
    private TextureRegion lightFboRegion;
    private ShaderProgram ditherShader;

    // Tiled background
    private TiledMap tiledMap;
    private OrthogonalTiledMapRenderer mapRenderer;
    private TiledMapTileLayer rocksLayer;
    private int mapTileWidth;
    private int mapTileHeight;

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

        lightFbo = new FrameBuffer(Pixmap.Format.RGBA8888, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, false);
        lightFbo.getColorBufferTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        lightFboRegion = new TextureRegion(lightFbo.getColorBufferTexture(), 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        lightFboRegion.flip(false, true);

        ditherShader = Shaders.createDitherShader();

        // Tiled background. Force nearest filter on the tileset so it stays
        // crisp through the FBO upscale (the default is Linear, which would
        // blur the pixel art).
        TmxMapLoader.Parameters mapParams = new TmxMapLoader.Parameters();
        mapParams.textureMinFilter = Texture.TextureFilter.Nearest;
        mapParams.textureMagFilter = Texture.TextureFilter.Nearest;
        tiledMap = new InfiniteTmxMapLoader().load("map/map2.tmx", mapParams);
        mapRenderer = new OrthogonalTiledMapRenderer(tiledMap);
        rocksLayer = (TiledMapTileLayer) tiledMap.getLayers().get("rocks");
        mapTileWidth = tiledMap.getProperties().get("tilewidth", Integer.class);
        mapTileHeight = tiledMap.getProperties().get("tileheight", Integer.class);

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

        // Setup initial world — house centered on the map so the painted area
        // surrounds the player evenly. Using the TMX dimensions means this keeps
        // working if the map is resized in Tiled.
        int mapTilesWide = tiledMap.getProperties().get("width", Integer.class);
        int mapTilesTall = tiledMap.getProperties().get("height", Integer.class);
        float mapCenterX = mapTilesWide * mapTileWidth * 0.5f;
        float mapCenterY = mapTilesTall * mapTileHeight * 0.5f;
        float houseX = mapCenterX - House.WIDTH * 0.5f;
        float houseY = mapCenterY - House.HEIGHT * 0.5f;
        house = new House(houseX, houseY, Resources.houseTexture);
        // Player spawns inside the house at the same relative offset as before.
        player = new Player(houseX + 16, houseY + 10,
            Resources.playerFront, Resources.playerBack,
            Resources.playerLeft, Resources.playerRight);

        cameraTargetX = player.getPosition().x + 8;
        cameraTargetY = player.getPosition().y + 8;

        entityManager.addEntity(house);
        entityManager.addEntity(player);

        // Add lights
        // Monochrome lights — color is white, alpha controls relative strength
        // so the composite dither operates on a single brightness channel.
        houseLight = new PointLight(rayHandler, 128, new Color(1, 1, 1, 0.8f), house.getLightRadius(), house.getCenterX(), house.getCenterY());
        playerLight = new PointLight(rayHandler, 64, new Color(1, 1, 1, 0.9f), player.getLightRadius(), player.getPosition().x + 8, player.getPosition().y + 8);
        emergencyLight = new PointLight(rayHandler, 32, new Color(1, 1, 1, 0.3f), player.getEmergencyLightRadius(), player.getPosition().x + 8, player.getPosition().y + 8);

        // Procedural ore spawning: a starting cluster near the player, then the
        // ResourceSystem keeps a target count seeded around them as they wander.
        resourceSystem = new ResourceSystem(entityManager, house);
        resourceSystem.seedInitial(player);

        particleSystem = new ParticleSystem();
        musicSystem = new MusicSystem();
        upgradeSystem = new UpgradeSystem();
        // Resource.globalClicksRequired is static and would otherwise persist
        // across a game-over restart, leaving rocks easier than intended.
        Resource.setGlobalClicksRequired(3);

        // Upgrade machine sits along the left wall, vertically centered, so
        // the back-wall chests and the south-wall door stay clear.
        machineX = house.getPosition().x + 24f;
        machineY = house.getPosition().y + (House.HEIGHT - MACHINE_H) * 0.5f;
        // Music start is deferred to render() — browsers (and HTML5/GWT in
        // particular) block audio playback until the user has interacted with
        // the page, so calling play() from the constructor throws NotAllowedError.
        prevHp = player.getHp();
    }

    private boolean musicStarted = false;

    private void tryStartMusic() {
        if (musicStarted) return;
        boolean hasInput = Gdx.input.justTouched()
            || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ANY_KEY);
        if (hasInput) {
            musicSystem.start();
            musicStarted = true;
        }
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
        if (state == State.END) {
            renderEndScreen();
            return;
        }

        tryStartMusic();

        // Closing the panel via ESC is checked before reading any other input
        // so the close-then-rerelease can happen on the same frame.
        if (upgradePanelOpen && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            upgradePanelOpen = false;
        }

        // WITHER is a frozen cinematic — everything pauses while the world dies.
        // BOSS_INTRO keeps the world ticking but locks player input so the
        // camera pan reads as "the game is showing you something".
        boolean updatesFrozen = state == State.WITHER;
        boolean playerInputAllowed = state == State.PLAYING && !upgradePanelOpen;

        // Update logic
        float dx = playerInputAllowed ? playerController.getHorizontalInput() : 0f;
        float dy = playerInputAllowed ? playerController.getVerticalInput() : 0f;

        if (!updatesFrozen) {
            player.move(dx, dy, delta, this::isBlockedByRocks);

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
        }

        // Test player against house bounds (use sprite center) for the inside-view toggle.
        playerInside = house.containsPoint(player.getPosition().x + 8, player.getPosition().y + 8);

        // Panel only makes sense inside the house — close if the player leaves.
        if (upgradePanelOpen && !playerInside) {
            upgradePanelOpen = false;
        }

        // Click to Mine / open upgrade panel / interact with panel / hit boss.
        // Locked during cinematic states so the player can't, e.g., mine while
        // the boss reveal is panning the camera.
        if (state == State.PLAYING) {
            handleClicks();
        }

        // State timers (after click handling so this-frame transitions render correctly)
        if (state == State.BOSS_INTRO) {
            bossIntroTimer -= delta;
            if (bossIntroTimer <= 0f) state = State.PLAYING;
        } else if (state == State.WITHER) {
            witherTimer -= delta;
            if (witherTimer <= 0f) state = State.END;
        }

        // Smooth Camera Follow (LERP) — keep float precision in cameraTargetX/Y
        // and snap to integer pixels at render time to avoid sub-pixel jitter on
        // the low-res FBO. While the player holds input, ease gently. The
        // moment input releases, switch to a much faster lerp so the tail
        // collapses in ~100ms instead of dragging out ~800ms — otherwise the
        // rounded render position keeps stepping by 1 FBO pixel per frame
        // during the tail and the light circles visibly shake.
        if (state == State.BOSS_INTRO && boss != null) {
            // Pan toward boss center during the reveal cinematic.
            float bcx = boss.getPosition().x + boss.getSize().x * 0.5f;
            float bcy = boss.getPosition().y + boss.getSize().y * 0.5f;
            cameraTargetX += (bcx - cameraTargetX) * 2.5f * delta;
            cameraTargetY += (bcy - cameraTargetY) * 2.5f * delta;
        } else if (!updatesFrozen) {
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
        }

        // Update lights
        if (!updatesFrozen) flickerTimer += delta;
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

        // Interaction logic — skip during cinematic states so the player can't
        // accidentally trigger another deposit/upgrade during the boss arc.
        if (state == State.PLAYING) {
            checkInteractions(delta);
        }

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

        // === WORLD PASS: tilemap + entities into gameFbo at full brightness ===
        gameFbo.begin();
        Gdx.gl.glViewport(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        ScreenUtils.clear(0, 0, 0, 1f);

        // Interior view is suppressed during cinematic states so the boss
        // reveal pan and the wither overlay aren't blocked by the four walls
        // when the trigger fires while the player is still inside the house.
        boolean showInterior = playerInside
            && state != State.BOSS_INTRO
            && state != State.WITHER;

        if (!showInterior) {
            mapRenderer.setView(camera);
            mapRenderer.render();
        }

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (showInterior) {
            renderInsideView();
        } else {
            entityManager.render(batch);
        }
        particleSystem.render(batch);
        renderHomeIndicator(delta);
        batch.end();
        gameFbo.end();

        // === LIGHT PASS: dithered lights into lightFbo cleared to ambient ===
        // ambient = (0,0,0) means unlit areas multiply to pure black later.
        // Bumping the clear color (e.g., 0.05) gives a faint baseline visibility
        // — useful if "complete darkness" feels too punishing.
        lightFbo.begin();
        Gdx.gl.glViewport(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        rayHandler.setCombinedMatrix(camera);
        rayHandler.updateAndRender();
        lightFbo.end();

        // === COMPOSITE: gameFbo *= lightFbo ===
        // (GL_DST_COLOR, GL_ZERO) = pure multiplicative. Each gameFbo pixel is
        // multiplied by the corresponding lightFbo pixel: black light → black
        // world (invisible), white light → world unchanged. The dither shader's
        // kept/discarded pattern in the light FBO becomes the visible reveal.
        gameFbo.begin();
        Gdx.gl.glViewport(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        fboCamera.update();
        batch.setProjectionMatrix(fboCamera.combined);
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(lightFboRegion, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
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

        if (state == State.WITHER) {
            // Ramp from 0 → strong red as the timer counts down, so the world
            // visibly bleeds out before the end card lands.
            float t = 1f - Math.max(0f, witherTimer / WITHER_DURATION);
            renderWitherOverlay(t);
        }

        if (upgradePanelOpen) {
            renderUpgradePanel();
        }
    }

    private void handleClicks() {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) return;

        // Panel takes click priority — any click goes to panel slots or closes
        // it. Use the fixed-UI coord space so the panel lives in screen pixels.
        if (upgradePanelOpen) {
            float uiX = toUiX(Gdx.input.getX());
            float uiY = toUiY(Gdx.input.getY());
            handleUpgradePanelClick(uiX, uiY);
            return;
        }

        // viewport.unproject uses the letterboxed GL viewport (not the full
        // canvas), so clicks map correctly even when the window aspect
        // doesn't match 16:9. camera.unproject(Vector3) would be wrong then.
        Vector3 worldCoords = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(worldCoords);

        // Click on the upgrade machine (only relevant when inside the house —
        // outside, the machine sprite isn't drawn).
        if (playerInside
            && worldCoords.x >= machineX && worldCoords.x <= machineX + MACHINE_W
            && worldCoords.y >= machineY && worldCoords.y <= machineY + MACHINE_H) {
            upgradePanelOpen = true;
            return;
        }

        // Click on the boss — close-range attack like mining. After 5 hits the
        // boss is dead and we kick off the WITHER cinematic.
        if (boss != null && !boss.isDead()
            && worldCoords.x >= boss.getPosition().x && worldCoords.x <= boss.getPosition().x + boss.getSize().x
            && worldCoords.y >= boss.getPosition().y && worldCoords.y <= boss.getPosition().y + boss.getSize().y
            && player.getPosition().dst(boss.getPosition()) < 80f) {
            boolean killed = boss.takeHit();
            Resources.mineSound.play();
            triggerShake(killed ? 4f : 2f);
            if (killed) {
                state = State.WITHER;
                witherTimer = WITHER_DURATION;
            }
            return;
        }

        for (Entity e : entityManager.getEntities()) {
            if (e instanceof Resource && !((Resource) e).isMined()) {
                if (worldCoords.x >= e.getPosition().x && worldCoords.x <= e.getPosition().x + e.getSize().x &&
                    worldCoords.y >= e.getPosition().y && worldCoords.y <= e.getPosition().y + e.getSize().y) {

                    // Check if player is near
                    if (player.getPosition().dst(e.getPosition()) < 50) {
                        Resource r = (Resource) e;
                        boolean finished = r.click();
                        Resources.mineSound.play();
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

    private void spawnBoss() {
        // East of the house, just out of the player's normal view, so the
        // intro pan is a small geographic reveal rather than a teleport feel.
        float bx = house.getPosition().x + House.WIDTH + 80f;
        float by = house.getPosition().y + (House.HEIGHT - 64f) * 0.5f;
        boss = new Boss(bx, by, Resources.bossShellTexture, Resources.bossCuteTexture);
        entityManager.addEntity(boss);
        state = State.BOSS_INTRO;
        bossIntroTimer = BOSS_INTRO_DURATION;
        // If the player triggered the spawn from inside the upgrade panel,
        // close it so the cinematic isn't drawn on top of stale UI.
        upgradePanelOpen = false;
        triggerShake(3.5f);
    }

    private void renderWitherOverlay(float t) {
        // Red film on top of the composited screen — pure full-screen quad in
        // UI space so we don't fight the world camera.
        batch.getProjectionMatrix().setToOrtho2D(0, 0, UI_W, UI_H);
        batch.begin();
        batch.setColor(0.6f, 0.05f, 0.05f, 0.55f * t);
        batch.draw(Resources.pixelTexture, 0, 0, UI_W, UI_H);
        // Vignette by stacking a darker band near the edges.
        batch.setColor(0f, 0f, 0f, 0.4f * t);
        float band = 60f;
        batch.draw(Resources.pixelTexture, 0, 0, UI_W, band);
        batch.draw(Resources.pixelTexture, 0, UI_H - band, UI_W, band);
        batch.draw(Resources.pixelTexture, 0, 0, band, UI_H);
        batch.draw(Resources.pixelTexture, UI_W - band, 0, band, UI_H);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void renderEndScreen() {
        ScreenUtils.clear(0.04f, 0f, 0f, 1f);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, UI_W, UI_H);
        batch.begin();

        // Cute creature corpse — drawn rotated 90° to read as "lying down".
        // Once you have real art, swap this for a dedicated death sprite.
        TextureRegion corpse = new TextureRegion(Resources.bossCuteTexture);
        float corpseSize = 64f;
        float corpseX = UI_W * 0.5f - 70f;
        float corpseY = UI_H * 0.5f - 20f;
        batch.setColor(0.55f, 0.35f, 0.45f, 1f); // desaturated, lifeless
        batch.draw(corpse,
            corpseX, corpseY, corpseSize * 0.5f, corpseSize * 0.5f,
            corpseSize, corpseSize, 1f, 1f, 90f);

        // Evil lil guy standing over the corpse — same sprite, blood-red tint.
        batch.setColor(0.7f, 0.1f, 0.1f, 1f);
        float pSize = 48f;
        batch.draw(Resources.playerFront,
            UI_W * 0.5f + 20f, UI_H * 0.5f - 16f, pSize, pSize);

        // Restart button — reuse the existing restart icon so the affordance
        // matches the game-over screen the player already saw (if any).
        batch.setColor(Color.WHITE);
        float btn = 48f;
        float btnX = (UI_W - btn) * 0.5f;
        float btnY = 50f;
        batch.draw(Resources.restartTexture, btnX, btnY, btn, btn);

        batch.end();

        // Restart on click anywhere on the button.
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            float ux = toUiX(Gdx.input.getX());
            float uy = toUiY(Gdx.input.getY());
            if (ux >= btnX && ux <= btnX + btn && uy >= btnY && uy <= btnY + btn) {
                ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new GameScreen());
            }
        }
    }

    // --- Upgrade panel layout, drawn in a fixed 640x360 UI space. ---
    private static final float UI_W = 640f;
    private static final float UI_H = 360f;
    private static final float PANEL_W = 420f;
    private static final float PANEL_H = 240f;
    private static final float SLOT_W = 80f;
    private static final float SLOT_H = 180f;
    private static final float CLOSE_SIZE = 18f;

    private float panelX() { return (UI_W - PANEL_W) * 0.5f; }
    private float panelY() { return (UI_H - PANEL_H) * 0.5f; }

    private float slotX(int i) {
        float gap = (PANEL_W - 4f * SLOT_W) / 5f;
        return panelX() + gap + i * (SLOT_W + gap);
    }
    private float slotY() { return panelY() + 30f; }

    private float closeBtnX() { return panelX() + PANEL_W - CLOSE_SIZE - 8f; }
    private float closeBtnY() { return panelY() + PANEL_H - CLOSE_SIZE - 8f; }

    /** Convert a raw screen pixel to fixed UI X in [0, UI_W]. */
    private float toUiX(int screenX) {
        return ((float)(screenX - viewport.getLeftGutterWidth()) / viewport.getScreenWidth()) * UI_W;
    }
    /** Convert a raw screen pixel to fixed UI Y in [0, UI_H] (Y up). */
    private float toUiY(int screenY) {
        return (1f - (float)(screenY - viewport.getTopGutterHeight()) / viewport.getScreenHeight()) * UI_H;
    }

    private void handleUpgradePanelClick(float ux, float uy) {
        // Close button
        if (ux >= closeBtnX() && ux <= closeBtnX() + CLOSE_SIZE
            && uy >= closeBtnY() && uy <= closeBtnY() + CLOSE_SIZE) {
            upgradePanelOpen = false;
            return;
        }
        // Upgrade slots
        for (int i = 0; i < Upgrade.values().length; i++) {
            float sx = slotX(i), sy = slotY();
            if (ux >= sx && ux <= sx + SLOT_W && uy >= sy && uy <= sy + SLOT_H) {
                Upgrade u = Upgrade.values()[i];
                if (upgradeSystem.canAfford(u, storageCounts)) {
                    upgradeSystem.purchase(u, storageCounts, player);
                    Resources.powerUpSound.play();
                }
                return;
            }
        }
        // Click outside the panel body closes it.
        float pxL = panelX(), pxR = panelX() + PANEL_W;
        float pyB = panelY(), pyT = panelY() + PANEL_H;
        if (ux < pxL || ux > pxR || uy < pyB || uy > pyT) {
            upgradePanelOpen = false;
        }
    }

    private void renderUpgradePanel() {
        // Switch to fixed 640x360 UI projection so the panel is stationary on
        // screen no matter where the world camera happens to be looking.
        batch.getProjectionMatrix().setToOrtho2D(0, 0, UI_W, UI_H);
        batch.begin();

        // Darken the world behind the panel.
        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(Resources.pixelTexture, 0, 0, UI_W, UI_H);

        // Panel background + border.
        batch.setColor(0.10f, 0.08f, 0.05f, 1f);
        batch.draw(Resources.pixelTexture, panelX(), panelY(), PANEL_W, PANEL_H);
        batch.setColor(0.55f, 0.40f, 0.20f, 1f);
        drawRectOutline(panelX(), panelY(), PANEL_W, PANEL_H, 2f);

        for (int i = 0; i < Upgrade.values().length; i++) {
            renderUpgradeSlot(Upgrade.values()[i], slotX(i), slotY());
        }

        // Close button: an "X" universally read as "close" without text.
        batch.setColor(0.6f, 0.6f, 0.6f, 1f);
        drawX(closeBtnX(), closeBtnY(), CLOSE_SIZE);

        batch.end();

        // Restore for any subsequent draws (renderUI does its own anyway).
        batch.setColor(Color.WHITE);
    }

    private void renderUpgradeSlot(Upgrade u, float sx, float sy) {
        boolean affordable = upgradeSystem.canAfford(u, storageCounts);
        boolean maxed = upgradeSystem.isMaxed(u);
        int level = upgradeSystem.getLevel(u);

        // Slot background tinted faintly with the ore color so the slot's
        // payment ore is signalled before you even look at the cost row.
        Color tint = STORAGE_COLORS[u.oreVariant];
        batch.setColor(tint.r * 0.4f, tint.g * 0.4f, tint.b * 0.4f, 1f);
        batch.draw(Resources.pixelTexture, sx, sy, SLOT_W, SLOT_H);
        batch.setColor(tint);
        drawRectOutline(sx, sy, SLOT_W, SLOT_H, 1f);

        // Level pips along the top: filled circles for purchased levels,
        // hollow for the remaining.
        float pipR = 4f;
        float pipGap = 4f;
        float pipsTotalW = UpgradeSystem.MAX_LEVEL * (pipR * 2f) + (UpgradeSystem.MAX_LEVEL - 1) * pipGap;
        float pipStartX = sx + (SLOT_W - pipsTotalW) * 0.5f + pipR;
        float pipY = sy + SLOT_H - 12f;
        for (int p = 0; p < UpgradeSystem.MAX_LEVEL; p++) {
            float px = pipStartX + p * (pipR * 2f + pipGap);
            // Hollow ring drawn with a small black inset for a "filled" pip,
            // or a tinted ring for "empty".
            batch.setColor(p < level ? Color.WHITE : new Color(0.3f, 0.3f, 0.3f, 1f));
            batch.draw(Resources.pixelTexture, px - pipR, pipY - pipR, pipR * 2f, pipR * 2f);
        }

        // Stat icon — programmatically drawn so it's pure visual, no text.
        Texture icon = iconFor(u);
        float iconSize = 36f;
        float ix = sx + (SLOT_W - iconSize) * 0.5f;
        float iy = sy + SLOT_H * 0.5f - iconSize * 0.5f + 6f;
        batch.setColor(affordable || maxed ? Color.WHITE : new Color(1f, 1f, 1f, 0.35f));
        batch.draw(icon, ix, iy, iconSize, iconSize);

        // Cost row: a stack of mini ore sprites showing what each level costs.
        // Hidden once the upgrade hits its cap — no point displaying a cost
        // the player can't pay any further.
        if (!maxed) {
            float oreSize = 12f;
            float oreGap = 2f;
            float costTotalW = UpgradeSystem.COST_PER_LEVEL * oreSize
                + (UpgradeSystem.COST_PER_LEVEL - 1) * oreGap;
            float costStartX = sx + (SLOT_W - costTotalW) * 0.5f;
            float costY = sy + 10f;
            batch.setColor(affordable ? Color.WHITE : new Color(1f, 1f, 1f, 0.4f));
            for (int c = 0; c < UpgradeSystem.COST_PER_LEVEL; c++) {
                batch.draw(Resources.oreRegions[u.oreVariant],
                    costStartX + c * (oreSize + oreGap), costY, oreSize, oreSize);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private Texture iconFor(Upgrade u) {
        switch (u) {
            case BATTERY: return Resources.iconBattery;
            case SPEED:   return Resources.iconSpeed;
            case MINING:  return Resources.iconMining;
            case LIGHT:   return Resources.iconLight;
        }
        return Resources.iconBattery;
    }

    private void drawRectOutline(float x, float y, float w, float h, float thickness) {
        batch.draw(Resources.pixelTexture, x, y, w, thickness);
        batch.draw(Resources.pixelTexture, x, y + h - thickness, w, thickness);
        batch.draw(Resources.pixelTexture, x, y, thickness, h);
        batch.draw(Resources.pixelTexture, x + w - thickness, y, thickness, h);
    }

    private void drawX(float x, float y, float size) {
        // Two diagonal bars approximated with stacks of thin rectangles —
        // pixelTexture has no rotation API per draw, so we fake it cheaply.
        int steps = (int) size;
        for (int i = 0; i < steps; i++) {
            float t = i / (float) steps;
            batch.draw(Resources.pixelTexture, x + t * size, y + t * size, 2f, 2f);
            batch.draw(Resources.pixelTexture, x + t * size, y + (1f - t) * size, 2f, 2f);
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

    /**
     * AABB-vs-rocks-layer test. Any non-empty cell in the "rocks" layer is
     * treated as solid. We sample every tile the AABB overlaps, so the player
     * never clips into a rock regardless of which corner enters first.
     */
    private boolean isBlockedByRocks(float x, float y, float w, float h) {
        if (rocksLayer == null) return false;

        // -1e-3 on the max edge so an AABB exactly on a tile boundary doesn't
        // claim to overlap the next tile (would over-block on perfect alignment).
        int minTileX = (int) Math.floor(x / mapTileWidth);
        int maxTileX = (int) Math.floor((x + w - 0.001f) / mapTileWidth);
        int minTileY = (int) Math.floor(y / mapTileHeight);
        int maxTileY = (int) Math.floor((y + h - 0.001f) / mapTileHeight);

        for (int ty = minTileY; ty <= maxTileY; ty++) {
            for (int tx = minTileX; tx <= maxTileX; tx++) {
                TiledMapTileLayer.Cell cell = rocksLayer.getCell(tx, ty);
                if (cell != null && cell.getTile() != null) return true;
            }
        }
        return false;
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

        // Four storage chests along the back wall, one per ore variant.
        // Chest body is tinted to match the ore color, the ore sprite sits on
        // top, and the deposited count is drawn above each chest.
        int n = STORAGE_COLORS.length;
        float chestW = 26f;
        float chestH = 18f;
        float chestPad = (hs.x - n * chestW) / (n + 1);
        float chestY = hp.y + hs.y - wallThickness - chestH - 12f;

        for (int i = 0; i < n; i++) {
            float chestX = hp.x + chestPad + i * (chestW + chestPad);

            // Chest body
            batch.setColor(STORAGE_COLORS[i]);
            batch.draw(Resources.pixelTexture, chestX, chestY, chestW, chestH);

            // Ore icon sitting on the chest
            batch.setColor(Color.WHITE);
            float oreSize = 14f;
            float oreX = chestX + (chestW - oreSize) * 0.5f;
            float oreY = chestY + (chestH - oreSize) * 0.5f;
            batch.draw(Resources.oreRegions[i], oreX, oreY, oreSize, oreSize);

            // Count above the chest. BitmapFont.draw places text by its baseline
            // so we offset upward by the (scaled) line height.
            String text = String.valueOf(storageCounts[i]);
            float textWidth = text.length() * 3.5f; // rough advance for scaled lsans-15
            Resources.font.draw(batch, text,
                chestX + (chestW - textWidth) * 0.5f,
                chestY + chestH + 10f);
        }

        // Upgrade machine — purely visual here; click detection lives in
        // handleClicks() using the same machineX/machineY world coords.
        batch.setColor(Color.WHITE);
        batch.draw(Resources.upgradeMachineRegion, machineX, machineY, MACHINE_W, MACHINE_H);

        batch.setColor(Color.WHITE);
        player.render(batch);
    }

    private void checkInteractions(float delta) {
        // Recharge / deposit happens whenever the player is actually inside the
        // house. Uses the same flag the renderer uses for the interior view.
        if (playerInside) {
            player.recharge(delta);

            if (!trail.isEmpty()) {
                int deposited = 0;
                for (Resource r : trail) {
                    int v = r.getVariant();
                    if (v >= 0 && v < storageCounts.length) {
                        storageCounts[v]++;
                    }
                    entityManager.removeEntity(r);
                    deposited++;
                }
                trail.clear();
                Resources.powerUpSound.play();
                player.heal(delta * 20); // Healing when at house
                totalDeposits += deposited;
                if (boss == null && totalDeposits >= BOSS_DEPOSIT_THRESHOLD) {
                    spawnBoss();
                }
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
        lightFbo.dispose();
        ditherShader.dispose();
        mapRenderer.dispose();
        tiledMap.dispose();
    }
}
