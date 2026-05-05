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
        arrowTexture = createArrowTexture(64, 64, Color.WHITE); // Higher res
        restartTexture = createRestartTexture(128, 128, Color.WHITE); // Higher res
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
        playerTexture.dispose();
        houseTexture.dispose();
        monsterTexture.dispose();
        resourceTexture.dispose();
        pixelTexture.dispose();
        arrowTexture.dispose();
        restartTexture.dispose();
    }
}
