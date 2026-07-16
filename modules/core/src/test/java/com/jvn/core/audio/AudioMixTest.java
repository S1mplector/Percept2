package com.jvn.core.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AudioMixTest {
  @Test
  void clampsGainsAndCombinesMasterWithChannels() {
    AudioMix mix = new AudioMix();
    mix.setMasterVolume(0.5f);
    mix.setBgmVolume(0.4f);
    mix.setSfxVolume(2f);
    mix.setVoiceVolume(Float.NaN);

    assertEquals(0.2, mix.effective(AudioChannel.BGM), 0.00001);
    assertEquals(0.5, mix.effective(AudioChannel.SFX), 0.00001);
    assertEquals(0.0, mix.effective(AudioChannel.VOICE), 0.00001);
  }

  @Test
  void mutePreservesConfiguredGains() {
    AudioMix mix = new AudioMix();
    mix.setBgmVolume(0.35f);
    mix.setMuted(true);
    assertEquals(0.0, mix.effective(AudioChannel.BGM));

    mix.setMuted(false);
    assertEquals(0.35, mix.effective(AudioChannel.BGM), 0.00001);
  }
}
