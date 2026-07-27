package com.jvn.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.jvn.core.audio.AudioFacade;

class MenuAudioLifecycleTest {

  @Test
  void gameplayHandoffStopsOnlyMenuBgm() {
    RecordingAudio audio = new RecordingAudio();

    MenuAudioLifecycle.beginGameplay(audio);

    assertEquals(1, audio.bgmStops);
    assertEquals(0, audio.sfxStops);
    assertEquals(0, audio.voiceStops);
  }

  private static final class RecordingAudio implements AudioFacade {
    private int bgmStops;
    private int sfxStops;
    private int voiceStops;

    @Override public void playBgm(String trackId, boolean loop) {}
    @Override public void playSfx(String sfxId) {}
    @Override public void stopBgm() { bgmStops++; }
    @Override public void stopSfx() { sfxStops++; }
    @Override public void stopVoice() { voiceStops++; }
  }
}
