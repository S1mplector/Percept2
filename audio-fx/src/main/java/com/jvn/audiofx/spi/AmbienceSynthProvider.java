package com.jvn.audiofx.spi;

public interface AmbienceSynthProvider {
  String id();
  void play(String preset, float intensity, float volume, boolean loop);
  void stop();
  default void setVolume(float volume) {}
}
