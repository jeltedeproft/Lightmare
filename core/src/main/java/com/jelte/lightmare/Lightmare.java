package com.jelte.lightmare;

import com.badlogic.gdx.Game;
import com.jelte.lightmare.screens.GameScreen;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Lightmare extends Game {
    @Override
    public void create() {
        Resources.load();
        setScreen(new GameScreen());
    }

    @Override
    public void dispose() {
        super.dispose();
        Resources.dispose();
    }
}
