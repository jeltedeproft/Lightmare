package com.jelte.lightmare;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

public class Resources {
    public static Texture playerTexture;
    public static Texture houseTexture;
    public static Texture monsterTexture;
    public static Texture resourceTexture;
    public static Texture pixelTexture;
    public static Texture arrowTexture;
    public static Texture restartTexture;

    public static void load() {
        playerTexture = createColoredTexture(16, 16, Color.YELLOW);
        houseTexture = createColoredTexture(48, 48, Color.BROWN);
        monsterTexture = createColoredTexture(16, 16, Color.RED);
        resourceTexture = createColoredTexture(16, 16, Color.GRAY);
        pixelTexture = createColoredTexture(1, 1, Color.WHITE);
        arrowTexture = createArrowTexture(16, 16, Color.WHITE);
        restartTexture = createRestartTexture(32, 32, Color.WHITE);
    }

    private static Texture createColoredTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static Texture createArrowTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0,0,0,0));
        pixmap.fill();
        pixmap.setColor(color);
        // Simple triangle pointing up
        pixmap.fillTriangle(width/2, 0, 0, height, width, height);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static Texture createRestartTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0,0,0,0));
        pixmap.fill();
        pixmap.setColor(color);
        // Draw a circular arc (simplified)
        pixmap.drawCircle(width/2, height/2, width/3);
        pixmap.setColor(new Color(0,0,0,0));
        pixmap.fillRectangle(width/2, 0, width/2, height/2); // Cut part of circle
        pixmap.setColor(color);
        // Arrow head for the circle
        pixmap.fillTriangle(width/2, 0, width/2, height/4, width*3/4, height/8);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    public static void dispose() {
        playerTexture.dispose();
        houseTexture.dispose();
        monsterTexture.dispose();
        resourceTexture.dispose();
        pixelTexture.dispose();
        arrowTexture.dispose();
        restartTexture.dispose();
    }
}
