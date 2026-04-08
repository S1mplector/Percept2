package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.jvn.core.audio.AudioFacade;

class VnDialogueVoicePlaybackTest {

  @Test
  void playsDialogueVoiceAndStopsPreviousLineOnAdvance() {
    VnScenario scenario = new VnScenarioBuilder("voice_dialogue")
        .dialogue("Alice", "First line", "voice/alice_001.ogg")
        .dialogue("Alice", "Second line", "voice/alice_002.ogg")
        .end()
        .build();

    FakeAudio audio = new FakeAudio();
    VnScene scene = new VnScene(scenario);
    scene.setAudioFacade(audio);

    scene.onEnter();
    assertEquals(1, audio.playVoiceCount);
    assertEquals(1, audio.stopVoiceCount);
    assertEquals("voice/alice_001.ogg", audio.lastVoiceTrack);

    scene.advance();
    assertEquals(2, audio.playVoiceCount);
    assertEquals(3, audio.stopVoiceCount);
    assertEquals("voice/alice_002.ogg", audio.lastVoiceTrack);
  }

  private static final class FakeAudio implements AudioFacade {
    private int playVoiceCount;
    private int stopVoiceCount;
    private String lastVoiceTrack;

    @Override
    public void playBgm(String trackId, boolean loop) {
    }

    @Override
    public void stopBgm() {
    }

    @Override
    public void playSfx(String sfxId) {
    }

    @Override
    public void playVoice(String voiceId) {
      playVoiceCount++;
      lastVoiceTrack = voiceId;
    }

    @Override
    public void stopVoice() {
      stopVoiceCount++;
    }
  }
}
