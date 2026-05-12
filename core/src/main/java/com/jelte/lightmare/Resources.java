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
    // Programmatic placeholders that are still used (house tint, UI bits).
    public static Texture houseTexture;
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
    public static TextureRegion upgradeMachineRegion;

    // Programmatic stat icons for the upgrade panel.
    public static Texture iconBattery;
    public static Texture iconSpeed;
    public static Texture iconMining;
    public static Texture iconLight;
    /** rock_blue, rock_green, rock_orange, rock_purple — pick a random index per spawn. */
    public static TextureRegion[] oreRegions;
    public static String[] oreNames = {"rock_blue", "rock_green", "rock_orange", "rock_purple"};

    // Audio
    public static Sound mineSound;
    public static Sound powerUpSound;
    public static Music[] musicTracks;
    private static final String MUSIC_DIR = "audio/music/";

    // Default lsans-15 bundled with libGDX (loaded from classpath, works on GWT).
    public static BitmapFont font;

    public static void load() {
        houseTexture = createColoredTexture(48, 48, Color.BROWN);
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
        upgradeMachineRegion = atlas.findRegion("upgrademachine");

        iconBattery = createBatteryIcon(32, 32);
        iconSpeed = createSpeedIcon(32, 32);
        iconMining = createMiningIcon(32, 32);
        iconLight = createLightIcon(32, 32);
        oreRegions = new TextureRegion[oreNames.length];
        for (int i = 0; i < oreNames.length; i++) {
            oreRegions[i] = atlas.findRegion(oreNames[i]);
        }

        mineSound = Gdx.audio.newSound(Gdx.files.internal("audio/mine.wav"));
        powerUpSound = Gdx.audio.newSound(Gdx.files.internal("audio/powerUp.wav"));

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

    private static Texture createBatteryIcon(int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0); p.fill();
        p.setColor(Color.WHITE);
        int bw = 22, bh = 14;
        int bx = (w - bw - 4) / 2;
        int by = (h - bh) / 2;
        // Battery body outline (drawn 2x for a 2px-thick frame).
        p.drawRectangle(bx, by, bw, bh);
        p.drawRectangle(bx + 1, by + 1, bw - 2, bh - 2);
        // Positive tip nub.
        p.fillRectangle(bx + bw, by + 4, 3, bh - 8);
        // Three filled cells inside.
        p.fillRectangle(bx + 3, by + 3, 5, bh - 6);
        p.fillRectangle(bx + 9, by + 3, 5, bh - 6);
        p.fillRectangle(bx + 15, by + 3, 4, bh - 6);
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return t;
    }

    private static Texture createSpeedIcon(int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0); p.fill();
        p.setColor(Color.WHITE);
        int cy = h / 2;
        int cx = w / 2 - 4;
        // Three right-pointing chevrons spaced horizontally.
        for (int i = -1; i <= 1; i++) {
            int ox = i * 7;
            p.fillTriangle(cx + ox - 4, cy - 9, cx + ox - 4, cy + 9, cx + ox + 4, cy);
            p.setColor(0, 0, 0, 0);
            p.fillTriangle(cx + ox - 6, cy - 5, cx + ox - 6, cy + 5, cx + ox + 0, cy);
            p.setColor(Color.WHITE);
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return t;
    }

    private static Texture createMiningIcon(int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0); p.fill();
        p.setColor(Color.WHITE);
        // Pickaxe head — a horizontal bar at the top, tapering on the ends.
        int headY = 6;
        p.fillTriangle(2, headY + 5, 14, headY + 5, 2, headY - 1);
        p.fillTriangle(w - 2, headY + 5, w - 14, headY + 5, w - 2, headY - 1);
        p.fillRectangle(4, headY, w - 8, 4);
        // Handle running from head center to bottom-right.
        for (int i = -1; i <= 1; i++) {
            p.drawLine(w / 2 + i, headY + 4, w - 6 + i, h - 4);
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return t;
    }

    private static Texture createLightIcon(int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0); p.fill();
        p.setColor(Color.WHITE);
        int cx = w / 2, cy = h / 2;
        // Sun body + 8 surrounding rays.
        p.fillCircle(cx, cy, 6);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            int x1 = (int) Math.round(cx + Math.cos(ang) * 10);
            int y1 = (int) Math.round(cy + Math.sin(ang) * 10);
            int x2 = (int) Math.round(cx + Math.cos(ang) * 14);
            int y2 = (int) Math.round(cy + Math.sin(ang) * 14);
            p.drawLine(x1, y1, x2, y2);
            p.drawLine(x1 + 1, y1, x2 + 1, y2);
            p.drawLine(x1, y1 + 1, x2, y2 + 1);
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
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
        houseTexture.dispose();
        pixelTexture.dispose();
        arrowTexture.dispose();
        restartTexture.dispose();
        if (atlas != null) atlas.dispose();
        if (font != null) font.dispose();
        if (mineSound != null) mineSound.dispose();
        if (powerUpSound != null) powerUpSound.dispose();
        if (musicTracks != null) {
            for (Music m : musicTracks) {
                if (m != null) m.dispose();
            }
        }
        if (iconBattery != null) iconBattery.dispose();
        if (iconSpeed != null) iconSpeed.dispose();
        if (iconMining != null) iconMining.dispose();
        if (iconLight != null) iconLight.dispose();
    }
}
