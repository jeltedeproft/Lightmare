package com.jelte.lightmare;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.List;

public class Resources {
    // Programmatic placeholders still used as UI bits.
    public static Texture pixelTexture;
    public static Texture arrowTexture;
    public static Texture restartTexture;

    // Sprite atlas for hand-drawn art.
    public static TextureAtlas atlas;
    public static TextureRegion playerFront;
    public static TextureRegion playerBack;
    public static TextureRegion playerLeft;
    public static TextureRegion playerRight;
    public static TextureRegion skullguyRegion;
    /** Animation frames for the torch sprite (torch_1/_2/_3 in the atlas). */
    public static TextureRegion[] torchFrames;

    // House + robot sprites, loaded as standalone textures so the atlas does
    // not need to be repacked for these additions.
    public static Texture houseTexture;
    public static Texture brokenRobotTexture;
    public static Texture workingRobotTexture;
    public static Texture robotLegsTexture;
    public static Texture robotDrillTexture;
    public static Texture robotWeaponTexture;

    // Combination sprites for the broken mech, indexed by which parts are
    // attached. Any slot may be null if the artist hasn't drawn it yet;
    // BrokenRobot falls back to the plain brokenRobot sprite in that case.
    //   index = (legs?1:0)<<2 | (drill?1:0)<<1 | (weapon?1:0)
    //   0=none, 1=W, 2=D, 3=DW, 4=L, 5=LW, 6=LD, 7=all (=workingRobot)
    public static Texture[] mechCombinationTextures = new Texture[8];

    // Programmatic eye icon used on the blue (VISION) chest. The other three
    // chests show their actual part sprite, but VISION has no physical part,
    // so we draw a small eye instead.
    public static Texture iconVision;

    // Boss placeholders — programmatic until real art lands.
    public static Texture bossShellTexture;
    public static Texture bossCuteTexture;

    // Planet-destruction cinematic placeholders.
    public static Texture starsTexture;
    public static Texture planetTexture;
    public static Texture brokenPlanetTexture;
    public static Texture chunkTexture;
    /** rock_blue, rock_green, rock_orange, rock_purple — pick a random index per spawn. */
    public static TextureRegion[] oreRegions;
    public static String[] oreNames = {"rock_blue", "rock_green", "rock_orange", "rock_purple"};

    // Audio
    public static Sound mineSound;
    public static Sound powerUpSound;
    public static Sound gunshotSound;
    public static Sound robotwalkSound;
    public static Music[] musicTracks;
    private static final String MUSIC_DIR = "audio/music/";

    // Default lsans-15 bundled with libGDX (loaded from classpath, works on GWT).
    public static BitmapFont font;

    public static void load() {
        pixelTexture = createColoredTexture(1, 1, Color.WHITE);
        arrowTexture = createArrowTexture(64, 64, Color.WHITE); // Higher res
        restartTexture = createRestartTexture(128, 128, Color.WHITE); // Higher res

        atlas = new TextureAtlas(Gdx.files.internal("sprites/lightmare.atlas"));
        // Pixel-art sprites must use Nearest filtering or they blur through the
        // FBO upscale — the atlas defaults to Linear unless we force it.
        for (Texture t : atlas.getTextures()) {
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        playerFront = atlas.findRegion("lilguyFront");
        playerBack = atlas.findRegion("lilguyBack");
        playerLeft = atlas.findRegion("lilguyLeft");
        playerRight = atlas.findRegion("lilguyRight");
        skullguyRegion = atlas.findRegion("skullguy");
        // TexturePacker strips the "_1/_2/_3" suffix and stores them indexed
        // under "torch"; findRegions returns them sorted by that index.
        com.badlogic.gdx.utils.Array<TextureAtlas.AtlasRegion> torchRegs = atlas.findRegions("torch");
        torchFrames = new TextureRegion[torchRegs.size];
        for (int i = 0; i < torchRegs.size; i++) {
            torchFrames[i] = torchRegs.get(i);
        }

        houseTexture = loadPixelTexture("sprites/items/house.png");
        brokenRobotTexture = loadPixelTexture("sprites/items/brokenRobot.png");
        workingRobotTexture = loadPixelTexture("sprites/items/workingRobot.png");
        robotLegsTexture = loadPixelTexture("sprites/items/robotLegs.png");
        robotDrillTexture = loadPixelTexture("sprites/items/robotDrill.png");
        robotWeaponTexture = loadPixelTexture("sprites/items/robotWeapon.png");

        // Mech combination art — anchors are brokenRobot (all 0) and
        // workingRobot (all 1); the six in-between sprites are optional and
        // fall back to brokenRobot if absent.
        mechCombinationTextures[0] = brokenRobotTexture;
        mechCombinationTextures[1] = loadPixelTextureOrNull("sprites/items/brokenRobot_W.png");
        mechCombinationTextures[2] = loadPixelTextureOrNull("sprites/items/brokenRobot_D.png");
        mechCombinationTextures[3] = loadPixelTextureOrNull("sprites/items/brokenRobot_DW.png");
        mechCombinationTextures[4] = loadPixelTextureOrNull("sprites/items/brokenRobot_L.png");
        mechCombinationTextures[5] = loadPixelTextureOrNull("sprites/items/brokenRobot_LW.png");
        mechCombinationTextures[6] = loadPixelTextureOrNull("sprites/items/brokenRobot_LD.png");
        mechCombinationTextures[7] = workingRobotTexture;

        iconVision = createVisionIcon(32, 32);

        bossShellTexture = createBossShellPlaceholder(64, 64);
        bossCuteTexture = createBossCutePlaceholder(32, 32);

        starsTexture = createStarsPlaceholder(640, 360);
        planetTexture = createPlanetPlaceholder(128);
        brokenPlanetTexture = createBrokenPlanetPlaceholder(128);
        chunkTexture = createChunkPlaceholder(20);
        oreRegions = new TextureRegion[oreNames.length];
        for (int i = 0; i < oreNames.length; i++) {
            oreRegions[i] = atlas.findRegion(oreNames[i]);
        }

        mineSound = Gdx.audio.newSound(Gdx.files.internal("audio/mine.wav"));
        powerUpSound = Gdx.audio.newSound(Gdx.files.internal("audio/powerUp.wav"));
        gunshotSound = Gdx.audio.newSound(Gdx.files.internal("audio/gunshot.wav"));
        robotwalkSound = Gdx.audio.newSound(Gdx.files.internal("audio/robotwalk.wav"));

        musicTracks = loadMusicTracks();

        // Default bitmap font. Scale to 0.5 so it sits sensibly at the 640x360
        // virtual resolution (15px → ~7.5px tall in world space).
        font = new BitmapFont();
        font.setUseIntegerPositions(true);
        font.getData().setScale(0.5f);
    }

    /**
     * Pull every file under audio/music/ from the build-generated assets.txt
     * manifest. Listing a directory at runtime doesn't work on GWT (the
     * preloader only knows files it preloaded), but the manifest is a plain
     * text file that ships to every platform — so this works on desktop and
     * html with no per-track maintenance.
     */
    private static Music[] loadMusicTracks() {
        if (!Gdx.files.internal("assets.txt").exists()) return new Music[0];
        String manifest = Gdx.files.internal("assets.txt").readString();
        List<Music> tracks = new ArrayList<>();
        for (String line : manifest.split("\\r?\\n")) {
            line = line.trim();
            if (line.length() > MUSIC_DIR.length() && line.startsWith(MUSIC_DIR)) {
                tracks.add(Gdx.audio.newMusic(Gdx.files.internal(line)));
            }
        }
        return tracks.toArray(new Music[0]);
    }

    private static Texture createVisionIcon(int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0); p.fill();
        int cx = w / 2, cy = h / 2;
        // Sclera, iris, pupil — concentric circles read as an eye even when
        // scaled down to chest size.
        p.setColor(Color.WHITE);
        p.fillCircle(cx, cy, w * 3 / 8);
        p.setColor(0.35f, 0.4f, 0.5f, 1f);
        p.fillCircle(cx, cy, w / 4);
        p.setColor(Color.BLACK);
        p.fillCircle(cx, cy, w / 8);
        // Catchlight off-center so it reads as a living eye, not a target.
        p.setColor(Color.WHITE);
        p.fillCircle(cx - w / 12, cy - w / 12, 1);
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return t;
    }

    private static Texture loadPixelTexture(String path) {
        Texture t = new Texture(Gdx.files.internal(path));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    /** Like loadPixelTexture but returns null if the file is missing, so the
     *  caller can substitute a fallback instead of crashing on startup. */
    private static Texture loadPixelTextureOrNull(String path) {
        if (!Gdx.files.internal(path).exists()) return null;
        return loadPixelTexture(path);
    }

    private static Texture createBossShellPlaceholder(int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0); p.fill();
        // Spiky dark-red blob silhouette.
        p.setColor(0.35f, 0.04f, 0.04f, 1f);
        p.fillCircle(w / 2, h / 2, w / 2 - 4);
        p.setColor(0.55f, 0.05f, 0.05f, 1f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int sx = (int) Math.round(w / 2 + Math.cos(a) * (w / 2 - 2));
            int sy = (int) Math.round(h / 2 + Math.sin(a) * (h / 2 - 2));
            p.fillCircle(sx, sy, 4);
        }
        // Angry yellow eyes with black pupils.
        p.setColor(1f, 0.85f, 0f, 1f);
        p.fillCircle(w / 2 - 12, h / 2 - 6, 6);
        p.fillCircle(w / 2 + 12, h / 2 - 6, 6);
        p.setColor(0, 0, 0, 1f);
        p.fillCircle(w / 2 - 12, h / 2 - 6, 3);
        p.fillCircle(w / 2 + 12, h / 2 - 6, 3);
        // Mean downward chevron mouth.
        p.setColor(0.1f, 0, 0, 1f);
        p.fillTriangle(w / 2 - 10, h / 2 + 8, w / 2 + 10, h / 2 + 8, w / 2, h / 2 + 18);
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return t;
    }

    private static Texture createBossCutePlaceholder(int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0); p.fill();
        // Round pink body.
        p.setColor(1f, 0.72f, 0.85f, 1f);
        p.fillCircle(w / 2, h / 2, w / 2 - 2);
        // Big dark eyes.
        p.setColor(0.08f, 0.08f, 0.08f, 1f);
        p.fillCircle(w / 2 - 6, h / 2 - 2, 3);
        p.fillCircle(w / 2 + 6, h / 2 - 2, 3);
        // Eye highlights.
        p.setColor(1f, 1f, 1f, 1f);
        p.fillCircle(w / 2 - 5, h / 2 - 3, 1);
        p.fillCircle(w / 2 + 7, h / 2 - 3, 1);
        // Cheek blush.
        p.setColor(1f, 0.5f, 0.65f, 1f);
        p.fillCircle(w / 2 - 10, h / 2 + 3, 2);
        p.fillCircle(w / 2 + 10, h / 2 + 3, 2);
        // Smile.
        p.setColor(0.5f, 0.2f, 0.3f, 1f);
        p.drawLine(w / 2 - 3, h / 2 + 5, w / 2 + 3, h / 2 + 5);
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        p.dispose();
        return t;
    }

    private static Texture createStarsPlaceholder(int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(0.02f, 0.02f, 0.05f, 1f);
        p.fill();
        java.util.Random rng = new java.util.Random(42);
        p.setColor(0.9f, 0.9f, 1f, 1f);
        for (int i = 0; i < 220; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);
            p.fillCircle(x, y, 1);
        }
        // A handful of slightly brighter accent stars.
        for (int i = 0; i < 18; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);
            p.fillCircle(x, y, 2);
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        p.dispose();
        return t;
    }

    private static Texture createPlanetPlaceholder(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0); p.fill();
        int cx = size / 2;
        int cy = size / 2;
        int r = size / 2 - 2;
        // Ocean base.
        p.setColor(0.15f, 0.35f, 0.65f, 1f);
        p.fillCircle(cx, cy, r);
        // Continents — kept well inside so they don't square off at the edge.
        java.util.Random rng = new java.util.Random(7);
        p.setColor(0.25f, 0.55f, 0.25f, 1f);
        for (int i = 0; i < 6; i++) {
            int contR = 6 + rng.nextInt(7);
            int maxOff = r - contR - 2;
            int dx = -maxOff + rng.nextInt(2 * maxOff + 1);
            int dy = -maxOff + rng.nextInt(2 * maxOff + 1);
            p.fillCircle(cx + dx, cy + dy, contR);
        }
        // Cloud highlight band.
        p.setColor(1f, 1f, 1f, 0.5f);
        p.fillCircle(cx - 10, cy - 8, 4);
        p.fillCircle(cx + 12, cy + 4, 3);
        // Terminator shadow on the right edge for a 3-D read.
        p.setColor(0f, 0f, 0f, 0.45f);
        for (int i = 0; i < 8; i++) p.drawCircle(cx + i, cy, r - i);
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        p.dispose();
        return t;
    }

    private static Texture createBrokenPlanetPlaceholder(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0); p.fill();
        int cx = size / 2;
        int cy = size / 2;
        int r = size / 2 - 2;
        // Dead-grey base.
        p.setColor(0.18f, 0.16f, 0.16f, 1f);
        p.fillCircle(cx, cy, r);
        // Glowing red lava cracks radiating from center.
        p.setColor(0.95f, 0.25f, 0.05f, 1f);
        for (int i = 0; i < 5; i++) {
            double a = i * (Math.PI * 2.0 / 5.0) + 0.4;
            int x2 = (int) Math.round(cx + Math.cos(a) * (r - 2));
            int y2 = (int) Math.round(cy + Math.sin(a) * (r - 2));
            p.drawLine(cx, cy, x2, y2);
            p.drawLine(cx + 1, cy, x2 + 1, y2);
        }
        // Bite missing from the top-right — that satisfying "chunk gone" silhouette.
        p.setColor(0f, 0f, 0f, 0f);
        p.fillCircle(cx + r / 2, cy - r / 2, r / 3);
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        p.dispose();
        return t;
    }

    private static Texture createChunkPlaceholder(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0); p.fill();
        int cx = size / 2;
        int cy = size / 2;
        int r = size / 2 - 2;
        // Dark crust.
        p.setColor(0.25f, 0.18f, 0.12f, 1f);
        p.fillCircle(cx, cy, r);
        // Lava core peeking through.
        p.setColor(0.95f, 0.35f, 0.10f, 1f);
        p.fillCircle(cx - 1, cy - 1, r - 3);
        // Thin dark rim.
        p.setColor(0.10f, 0.06f, 0.05f, 1f);
        p.drawCircle(cx, cy, r);
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        p.dispose();
        return t;
    }

    private static Texture createColoredTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private static Texture createArrowTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0,0,0,0));
        pixmap.fill();
        pixmap.setColor(color);
        pixmap.fillTriangle(width/2, 0, 0, height, width, height);
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private static Texture createRestartTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0,0,0,0));
        pixmap.fill();
        pixmap.setColor(color);
        
        // Thicker circular arc
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = width / 3;
        
        for (int i = 0; i < 4; i++) { // Mock "thickness"
            pixmap.drawCircle(centerX, centerY, radius - i);
        }
        
        pixmap.setColor(new Color(0,0,0,0));
        pixmap.fillRectangle(width/2, 0, width/2, height/2); 
        
        pixmap.setColor(color);
        // Larger Arrow head
        pixmap.fillTriangle(width/2, 0, width/2, height/3, width*4/5, height/6);
        
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    public static void dispose() {
        pixelTexture.dispose();
        arrowTexture.dispose();
        restartTexture.dispose();
        if (atlas != null) atlas.dispose();
        if (font != null) font.dispose();
        if (mineSound != null) mineSound.dispose();
        if (powerUpSound != null) powerUpSound.dispose();
        if (gunshotSound != null) gunshotSound.dispose();
        if (robotwalkSound != null) robotwalkSound.dispose();
        if (musicTracks != null) {
            for (Music m : musicTracks) {
                if (m != null) m.dispose();
            }
        }
        if (houseTexture != null) houseTexture.dispose();
        // Slots 0 and 7 alias brokenRobotTexture/workingRobotTexture — dispose
        // only the in-between slots here, then dispose the anchors below.
        for (int i = 1; i < 7; i++) {
            if (mechCombinationTextures[i] != null) mechCombinationTextures[i].dispose();
        }
        if (brokenRobotTexture != null) brokenRobotTexture.dispose();
        if (workingRobotTexture != null) workingRobotTexture.dispose();
        if (robotLegsTexture != null) robotLegsTexture.dispose();
        if (robotDrillTexture != null) robotDrillTexture.dispose();
        if (robotWeaponTexture != null) robotWeaponTexture.dispose();
        if (iconVision != null) iconVision.dispose();
        if (bossShellTexture != null) bossShellTexture.dispose();
        if (bossCuteTexture != null) bossCuteTexture.dispose();
        if (starsTexture != null) starsTexture.dispose();
        if (planetTexture != null) planetTexture.dispose();
        if (brokenPlanetTexture != null) brokenPlanetTexture.dispose();
        if (chunkTexture != null) chunkTexture.dispose();
    }
}
