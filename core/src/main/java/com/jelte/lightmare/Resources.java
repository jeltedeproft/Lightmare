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

    public static void load() {
        playerTexture = createColoredTexture(16, 16, Color.YELLOW);
        houseTexture = createColoredTexture(48, 48, Color.BROWN);
        monsterTexture = createColoredTexture(16, 16, Color.RED);
        resourceTexture = createColoredTexture(16, 16, Color.GRAY);
        pixelTexture = createColoredTexture(1, 1, Color.WHITE);
    }

    private static Texture createColoredTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
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
    }
}
