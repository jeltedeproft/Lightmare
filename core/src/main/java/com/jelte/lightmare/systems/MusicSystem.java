package com.jelte.lightmare.systems;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.math.MathUtils;
import com.jelte.lightmare.Resources;

public class MusicSystem {
    private static final float VOLUME = 0.5f;

    private int currentIndex = -1;
    private Music current;

    public void start() {
        if (Resources.musicTracks == null || Resources.musicTracks.length == 0) return;
        // Restart path (new GameScreen after game over) reuses the same shared
        // Music instances — kill anything still playing from the previous run.
        for (Music m : Resources.musicTracks) {
            if (m != null && m.isPlaying()) m.stop();
        }
        playRandom();
    }

    public void stop() {
        if (current != null && current.isPlaying()) current.stop();
        current = null;
        currentIndex = -1;
    }

    private void playRandom() {
        int next;
        if (Resources.musicTracks.length == 1) {
            next = 0;
        } else {
            // Avoid the same track twice in a row.
            do {
                next = MathUtils.random(Resources.musicTracks.length - 1);
            } while (next == currentIndex);
        }
        currentIndex = next;
        current = Resources.musicTracks[next];
        current.setVolume(VOLUME);
        current.setLooping(false);
        current.setOnCompletionListener(this::onTrackFinished);
        current.play();
    }

    private void onTrackFinished(Music finished) {
        playRandom();
    }
}
