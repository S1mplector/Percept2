package com.jvn.audiofx.spi;

import com.jvn.core.audio.AmbienceProfile;

public interface AmbienceSynthProvider {
  String id();
  void play(String preset, float intensity, float volume, boolean loop);
  default void play(String preset, float intensity, float volume, AmbienceProfile profile) {
    play(preset, intensity, volume, profile == null || profile.loop());
  }
  void stop();
  default void setVolume(float volume) {}
}
