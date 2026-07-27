package com.musicplayer.core.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JavaZoomAudioEngineTest {

    @Test
    void basicPlayerGainRemainsInNormalizedApiRange() {
        assertEquals(0.0, JavaZoomAudioEngine.normalizeBasicPlayerGain(-1.0));
        assertEquals(0.0, JavaZoomAudioEngine.normalizeBasicPlayerGain(Double.NaN));
        assertEquals(0.65, JavaZoomAudioEngine.normalizeBasicPlayerGain(0.65));
        assertEquals(1.0, JavaZoomAudioEngine.normalizeBasicPlayerGain(2.0));
    }
}
