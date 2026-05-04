package com.jelte.lightmare.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;

public class PlayerController {
    public float getHorizontalInput() {
        float input = 0;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) {
            input -= 1;
        }
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) {
            input += 1;
        }
        return input;
    }

    public float getVerticalInput() {
        float input = 0;
        if (Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN)) {
            input -= 1;
        }
        if (Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP)) {
            input += 1;
        }
        return input;
    }
}
