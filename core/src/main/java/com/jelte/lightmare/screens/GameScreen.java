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
import com.jelte.lightmare.entities.BrokenRobot;
import com.jelte.lightmare.entities.Bullet;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class GameScreen implements Screen {
    /**
     * Game flow:
     *   PLAYING ─[deposit threshold reached]─▶ BOSS_INTRO (~1.8s cinematic pan)
     *   BOSS_INTRO ─▶ PLAYING (boss is now an active entity)
     *   PLAYING ─[boss killed]─▶ WITHER (~3.5s red overlay ramps up)
     *   WITHER ─▶ END (silent end screen with restart)
     *   PLAYING ─[HP=0]─▶ GAMEOVER
     */
    private enum State { PLAYING, BOSS_INTRO, WITHER, PLANET_REVEAL, END, GAMEOVER }
    private State state = State.PLAYING;

    // Boss arc — triggered once every robot part is unlocked, i.e. the full
    // exoskeleton is assembled on the player.
    private static final float BOSS_INTRO_DURATION = 1.8f;
    private static final float WITHER_DURATION = 3.5f;
    private Boss boss;
    private float bossIntroTimer = 0f;
    private float witherTimer = 0f;

    // Planet destruction cinematic
    private static final float PLANET_DURATION = 6f;
    private static final float PLANET_SHATTER_AT = 4.3f;
    private static final int NUM_CHUNKS = 8;
    private float planetTimer = 0f;
    private final Vector2[] chunkPos = new Vector2[NUM_CHUNKS];
    private final Vector2[] chunkVel = new Vector2[NUM_CHUNKS];
    private final float[] chunkRot = new float[NUM_CHUNKS];
    private final float[] chunkRotVel = new float[NUM_CHUNKS];
    private boolean chunksInitialized = false;

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

    // Upgrade machine — kept as a decorative prop on the left wall inside the
    // house. Unlocks are now triggered automatically when a chest hits the ore
    // threshold, so the machine no longer has a click handler attached.
    private static final float MACHINE_W = 32f;
    private static final float MACHINE_H = 32f;
    private float machineX, machineY;

    // Juice
    private static final float SHAKE_DURATION = 0.2f;
    private float shakeIntensity = 0f;
    private float shakeTimer = 0f;
    private float prevHp;
    private float flickerTimer = 0f;

    // Robot footsteps — play robotwalk.wav on a fixed cadence whenever the
    // player is moving and the legs upgrade has been bought (lilguy's bare feet
    // stay silent).
    private static final float FOOTSTEP_INTERVAL = 0.35f;
    private float footstepCooldown = 0f;

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
    private BrokenRobot brokenRobot;
    private List<Resource> trail = new ArrayList<>();

    // Lighting
    private World world;
    private RayHandler rayHandler;
    private PointLight playerLight;
    private PointLight emergencyLight;
    private PointLight houseLight;

    // Per-bullet PointLights (small red tracer glow). Created in fireBullet,
    // position-synced each frame in updateBullets, removed when the bullet
    // despawns. IdentityHashMap so bullet identity is what keys the map even
    // if Bullet ever gains value-based equality later.
    private final Map<Bullet, PointLight> bulletLights = new IdentityHashMap<>();

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

        // Story prop: a broken robot slumped on the floor inside the house —
        // placed between the upgrade machine (left wall) and the door (right
        // side) so the player sees it immediately on spawning. Drawn manually
        // by renderInsideView (not via entityManager) so it doesn't bleed
        // through the house sprite when the player is outside. Sprite swaps
        // to the "working" robot once every part has been repaired.
        float robotX = house.getPosition().x + 100f;
        float robotY = house.getPosition().y + 24f;
        brokenRobot = new BrokenRobot(robotX, robotY,
            Resources.brokenRobotTexture, Resources.workingRobotTexture);

        // Add lights — monochrome (color white, alpha = strength) so the dither
        // composite operates on a single brightness channel.
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
        if (state == State.PLANET_REVEAL) {
            renderPlanetReveal(delta);
            return;
        }

        tryStartMusic();

        // WITHER is a frozen cinematic — everything pauses while the world dies.
        // BOSS_INTRO keeps the world ticking but locks player input so the
        // camera pan reads as "the game is showing you something".
        boolean updatesFrozen = state == State.WITHER;
        boolean playerInputAllowed = state == State.PLAYING;

        // Update logic
        float dx = playerInputAllowed ? playerController.getHorizontalInput() : 0f;
        float dy = playerInputAllowed ? playerController.getVerticalInput() : 0f;

        if (!updatesFrozen) {
            // Player checks rocks + house walls (with the door gap open).
            player.move(dx, dy, delta, (x, y, w, h) ->
                isBlockedByRocks(x, y, w, h)
                    || house.isBlockedByWall(x, y, w, h, true));

            updateFootsteps(dx, dy, delta);

            entityManager.update(delta);
            monsterSystem.update(delta, player);
            resourceSystem.update(delta, player);
            particleSystem.update(delta);
            updateBullets();
            // Boss has phase-dependent AI (chase shell, flee cute) — only active
            // once the intro pan has handed control back to the player.
            if (boss != null && state == State.PLAYING) {
                boss.updateAI(player, house, delta);
            }

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

        // Click to Mine / shoot / hit boss. Locked during cinematic states so
        // the player can't, e.g., mine while the boss reveal is panning.
        if (state == State.PLAYING) {
            handleClicks();
        }

        // State timers (after click handling so this-frame transitions render correctly)
        if (state == State.BOSS_INTRO) {
            bossIntroTimer -= delta;
            if (bossIntroTimer <= 0f) state = State.PLAYING;
        } else if (state == State.WITHER) {
            witherTimer -= delta;
            if (witherTimer <= 0f) {
                state = State.PLANET_REVEAL;
                planetTimer = 0f;
                chunksInitialized = false;
            }
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
            // No programmatic door overlay — the house.png sprite already shows
            // the door art, and the logical doorway sits under it via DOOR_X_OFFSET.
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

    }

    private void handleClicks() {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) return;

        // viewport.unproject uses the letterboxed GL viewport (not the full
        // canvas), so clicks map correctly even when the window aspect
        // doesn't match 16:9. camera.unproject(Vector3) would be wrong then.
        Vector3 worldCoords = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(worldCoords);

        // Fire a bullet whenever the gun is fully assembled. Bullets travel
        // through ores harmlessly, so the same click still mines or hits the
        // boss via the checks below — shooting is purely additive once unlocked.
        if (upgradeSystem.gunReady()) {
            fireBullet(worldCoords.x, worldCoords.y);
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

    private void updateFootsteps(float dx, float dy, float delta) {
        boolean moving = dx != 0f || dy != 0f;
        if (!moving || !player.hasLegs()) {
            // Reset so the very next step plays without waiting for a cooldown.
            footstepCooldown = 0f;
            return;
        }
        footstepCooldown -= delta;
        if (footstepCooldown <= 0f) {
            Resources.robotwalkSound.play();
            footstepCooldown = FOOTSTEP_INTERVAL;
        }
    }

    private void syncPlayerParts() {
        player.setHasLegs(upgradeSystem.isUnlocked(Upgrade.LEGS));
        player.setHasDrill(upgradeSystem.isUnlocked(Upgrade.DRILL));
        player.setHasGun(upgradeSystem.isUnlocked(Upgrade.GUN));
    }

    private void fireBullet(float targetX, float targetY) {
        // Originate at the player's visual center so bullets read as coming
        // from the body rather than the corner of the AABB.
        float ox = player.getPosition().x + 8f;
        float oy = player.getPosition().y + 8f;
        Bullet bullet = new Bullet(ox, oy, targetX - ox, targetY - oy);
        entityManager.addEntity(bullet);
        // Small red tracer glow that follows the bullet — short range, fewer
        // rays than the player light since the glow only needs to read as a
        // moving red dot in the dark.
        PointLight light = new PointLight(rayHandler, 24,
            new Color(1f, 0.25f, 0.15f, 0.9f), 32f, ox, oy);
        bulletLights.put(bullet, light);
        Resources.gunshotSound.play();
    }

    private void updateBullets() {
        // Single pass over the entity list: each live bullet checks every
        // monster and the boss, and is removed when spent or on impact.
        List<Entity> toRemove = null;
        for (Entity e : entityManager.getEntities()) {
            if (!(e instanceof Bullet)) continue;
            Bullet b = (Bullet) e;

            // Keep the tracer light glued to the bullet's center each frame.
            PointLight light = bulletLights.get(b);
            if (light != null) {
                light.setPosition(b.getPosition().x + 1.5f, b.getPosition().y + 1.5f);
            }

            if (b.isSpent()) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(b);
                continue;
            }
            // Monster collision — one-shot kill, matches the "they flee light" feel.
            for (Entity other : entityManager.getEntities()) {
                if (!(other instanceof Monster)) continue;
                if (b.overlaps(other)) {
                    if (toRemove == null) toRemove = new ArrayList<>();
                    toRemove.add(other);
                    b.markSpent();
                    toRemove.add(b);
                    triggerShake(1.5f);
                    break;
                }
            }
            if (b.isSpent()) continue;
            // Boss collision — same hit-counting as a melee click.
            if (boss != null && !boss.isDead() && b.overlaps(boss)) {
                boolean killed = boss.takeHit();
                triggerShake(killed ? 4f : 2f);
                if (killed) {
                    state = State.WITHER;
                    witherTimer = WITHER_DURATION;
                }
                b.markSpent();
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(b);
            }
        }
        if (toRemove != null) {
            for (Entity e : toRemove) {
                entityManager.removeEntity(e);
                // Free the box2dlight slot so dead bullets don't pile up lights.
                if (e instanceof Bullet) {
                    PointLight light = bulletLights.remove(e);
                    if (light != null) light.remove();
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

    private void renderPlanetReveal(float delta) {
        planetTimer += delta;

        // --- Animation parameters keyed off the timeline. ---
        float t = planetTimer;
        float planetSize = 160f;
        float cx = UI_W * 0.5f;
        float cy = UI_H * 0.5f;

        // Ease-in: planet appears small in the distance, grows to full size.
        float growIn = Math.min(1f, t / 0.8f);
        float scale = 0.2f + 0.8f * growIn;

        // Healthy → corrupted color ramp before shatter. Stays in (R,G,B) so
        // we can express it as a batch.setColor() tint multiplying the texture.
        float corrupt = clamp01((t - 1.6f) / 2.5f); // 0..1 over the corruption window

        // Heavy shake right before shatter.
        float shake = 0f;
        if (t > 3.0f && t < PLANET_SHATTER_AT) {
            shake = 1.5f + 3f * ((t - 3.0f) / (PLANET_SHATTER_AT - 3.0f));
        }
        float ox = MathUtils.random(-shake, shake);
        float oy = MathUtils.random(-shake, shake);

        boolean shattered = t >= PLANET_SHATTER_AT;
        if (shattered && !chunksInitialized) {
            initializeChunks(cx, cy);
            chunksInitialized = true;
            triggerShake(6f);
            Resources.powerUpSound.play(); // re-purpose as a "BOOM"
        }

        // White flash spans the shatter moment — a short, hot spike of brightness.
        float flashWindow = 0.25f;
        float flash = 0f;
        if (t >= PLANET_SHATTER_AT - flashWindow && t <= PLANET_SHATTER_AT + flashWindow) {
            float fd = Math.abs(t - PLANET_SHATTER_AT);
            flash = 1f - fd / flashWindow;
        }

        // --- Render ---
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, UI_W, UI_H);
        batch.begin();

        // Starfield backdrop, slightly drifting via a subtle scale pump.
        batch.setColor(Color.WHITE);
        batch.draw(Resources.starsTexture, 0, 0, UI_W, UI_H);

        if (!shattered) {
            // Intact planet, increasingly corrupted as t advances.
            float r = 1f + 0.4f * corrupt;
            float g = 1f - 0.5f * corrupt;
            float b = 1f - 0.6f * corrupt;
            batch.setColor(r, g, b, 1f);
            float sz = planetSize * scale;
            batch.draw(Resources.planetTexture,
                cx - sz * 0.5f + ox, cy - sz * 0.5f + oy, sz, sz);

            // Crack glow — six veins of red dots stretching from center outward,
            // getting longer and brighter as corruption ramps. Done with stacked
            // small quads so we don't need a proper line primitive.
            if (corrupt > 0.2f) {
                batch.setColor(1f, 0.2f, 0.05f, 0.9f * corrupt);
                int rays = 6;
                float crackLen = sz * 0.45f * corrupt;
                for (int i = 0; i < rays; i++) {
                    double a = i * (Math.PI * 2.0 / rays) + 0.4;
                    float ux = (float) Math.cos(a);
                    float uy = (float) Math.sin(a);
                    int steps = 6;
                    for (int j = 1; j <= steps; j++) {
                        float jt = j / (float) steps;
                        float px = cx + ox + ux * crackLen * jt - 1f;
                        float py = cy + oy + uy * crackLen * jt - 1f;
                        batch.draw(Resources.pixelTexture, px, py, 2f, 2f);
                    }
                    // Bright spark at the tip.
                    batch.draw(Resources.pixelTexture,
                        cx + ox + ux * crackLen - 1.5f,
                        cy + oy + uy * crackLen - 1.5f, 3f, 3f);
                }
            }
        } else {
            // Update + draw chunks. Each chunk drifts and rotates outward.
            for (int i = 0; i < NUM_CHUNKS; i++) {
                chunkPos[i].x += chunkVel[i].x * delta;
                chunkPos[i].y += chunkVel[i].y * delta;
                chunkRot[i] += chunkRotVel[i] * delta;
                // Soft drag so chunks don't fly to infinity instantly.
                chunkVel[i].scl(0.985f);

                float size = 22f;
                // Fade as they get further from center.
                float dx = chunkPos[i].x - cx;
                float dy = chunkPos[i].y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float alpha = clamp01(1f - dist / 320f);
                batch.setColor(1f, 1f, 1f, alpha);
                batch.draw(new TextureRegion(Resources.chunkTexture),
                    chunkPos[i].x - size * 0.5f, chunkPos[i].y - size * 0.5f,
                    size * 0.5f, size * 0.5f, size, size,
                    1f, 1f, chunkRot[i]);
            }

            // Lingering red afterglow at the destruction site, decaying.
            float afterglow = clamp01(1f - (t - PLANET_SHATTER_AT) / 1.2f);
            if (afterglow > 0f) {
                batch.setColor(1f, 0.3f, 0.1f, 0.5f * afterglow);
                float gSz = 80f * afterglow + 40f;
                batch.draw(Resources.pixelTexture,
                    cx - gSz * 0.5f, cy - gSz * 0.5f, gSz, gSz);
            }
        }

        // White flash sits on top of everything else.
        if (flash > 0f) {
            batch.setColor(1f, 1f, 1f, flash);
            batch.draw(Resources.pixelTexture, 0, 0, UI_W, UI_H);
        }

        batch.setColor(Color.WHITE);
        batch.end();

        // Hand off to the end card once the cinematic timer expires.
        if (t >= PLANET_DURATION) {
            state = State.END;
        }
    }

    private void initializeChunks(float cx, float cy) {
        for (int i = 0; i < NUM_CHUNKS; i++) {
            double a = i * (Math.PI * 2.0 / NUM_CHUNKS) + MathUtils.random(-0.3f, 0.3f);
            float speed = MathUtils.random(110f, 180f);
            chunkPos[i] = new Vector2(cx, cy);
            chunkVel[i] = new Vector2((float) Math.cos(a) * speed, (float) Math.sin(a) * speed);
            chunkRot[i] = MathUtils.random(0f, 360f);
            chunkRotVel[i] = MathUtils.random(-200f, 200f);
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private void renderEndScreen() {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, UI_W, UI_H);
        batch.begin();

        batch.setColor(Color.WHITE);
        float btn = 48f;
        float btnX = (UI_W - btn) * 0.5f;
        float btnY = (UI_H - btn) * 0.5f;
        batch.draw(Resources.restartTexture, btnX, btnY, btn, btn);

        batch.end();

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            float ux = toUiX(Gdx.input.getX());
            float uy = toUiY(Gdx.input.getY());
            if (ux >= btnX && ux <= btnX + btn && uy >= btnY && uy <= btnY + btn) {
                ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new GameScreen());
            }
        }
    }

    // Fixed 640x360 UI projection used by the cinematic overlays and the end
    // screen — the world camera moves but UI elements stay screen-locked.
    private static final float UI_W = 640f;
    private static final float UI_H = 360f;

    /** Convert a raw screen pixel to fixed UI X in [0, UI_W]. */
    private float toUiX(int screenX) {
        return ((float)(screenX - viewport.getLeftGutterWidth()) / viewport.getScreenWidth()) * UI_W;
    }
    /** Convert a raw screen pixel to fixed UI Y in [0, UI_H] (Y up). */
    private float toUiY(int screenY) {
        return (1f - (float)(screenY - viewport.getTopGutterHeight()) / viewport.getScreenHeight()) * UI_H;
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

        // Door gap in the south wall — uses the same House.getDoorX() as the
        // collision logic so the visible threshold lines up with the passage.
        batch.setColor(0.65f, 0.5f, 0.28f, 1f);
        batch.draw(Resources.pixelTexture,
            house.getDoorX(), hp.y, House.DOOR_WIDTH, wallThickness);

        // Four storage chests along the back wall, one per ore variant.
        // Chest body is tinted to match the ore color, the ore sprite sits on
        // top, and a row of 10 progress pips above the chest lights up as ore
        // is deposited — when all ten light, the matching robot part unlocks.
        int n = STORAGE_COLORS.length;
        float chestW = 26f;
        float chestH = 18f;
        float chestPad = (hs.x - n * chestW) / (n + 1);
        float chestY = hp.y + hs.y - wallThickness - chestH - 12f;

        final int pipCount = UpgradeSystem.UNLOCK_THRESHOLD;
        final float pipW = 2f;
        final float pipH = 4f;
        final float pipGap = 1f;
        final float pipsTotalW = pipCount * pipW + (pipCount - 1) * pipGap;
        final Color pipUnlit = new Color(0.18f, 0.18f, 0.18f, 1f);

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

            // Progress pips above the chest.
            float pipsStartX = chestX + (chestW - pipsTotalW) * 0.5f;
            float pipsY = chestY + chestH + 3f;
            for (int p = 0; p < pipCount; p++) {
                batch.setColor(p < storageCounts[i] ? STORAGE_COLORS[i] : pipUnlit);
                batch.draw(Resources.pixelTexture,
                    pipsStartX + p * (pipW + pipGap), pipsY, pipW, pipH);
            }
        }
        batch.setColor(Color.WHITE);

        // Upgrade machine — decorative prop on the left wall.
        batch.setColor(Color.WHITE);
        batch.draw(Resources.upgradeMachineRegion, machineX, machineY, MACHINE_W, MACHINE_H);

        // Broken/working robot in the middle of the floor. Drawn here (not via
        // entityManager) so it stays hidden from the outside view.
        brokenRobot.render(batch);

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
                    int v = r.getVariant();
                    if (v >= 0 && v < storageCounts.length) {
                        // Cap at the unlock threshold so the chest progress bar
                        // never overshoots and excess ore is silently discarded.
                        if (storageCounts[v] < UpgradeSystem.UNLOCK_THRESHOLD) {
                            storageCounts[v]++;
                        }
                    }
                    entityManager.removeEntity(r);
                }
                trail.clear();
                Resources.powerUpSound.play();
                player.heal(delta * 20); // Healing when at house
                checkPartUnlocks();
            }
        }
    }

    /**
     * After a deposit, see if any chest just crossed UNLOCK_THRESHOLD and bring
     * the matching part online. The final unlock triggers the boss arc.
     */
    private void checkPartUnlocks() {
        boolean anyUnlocked = false;
        for (Upgrade u : Upgrade.values()) {
            if (!upgradeSystem.isUnlocked(u)
                && storageCounts[u.oreVariant] >= UpgradeSystem.UNLOCK_THRESHOLD) {
                upgradeSystem.unlock(u, player);
                anyUnlocked = true;
            }
        }
        if (anyUnlocked) {
            syncPlayerParts();
            Resources.powerUpSound.play();
            if (boss == null && upgradeSystem.allUnlocked()) {
                brokenRobot.setRepaired();
                spawnBoss();
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
