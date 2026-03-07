package com.jvn.audiofx.spi;

public interface ChipSynthProvider {
  String id();
  void play(String cueId, float intensity, float volume, boolean loop);
  void stop();
  default void setVolume(float volume) {}
}
