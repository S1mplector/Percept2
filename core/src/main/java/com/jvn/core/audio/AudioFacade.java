package com.jvn.core.audio;

public interface AudioFacade {
  void playBgm(String trackId, boolean loop);
  void stopBgm();
  void playSfx(String sfxId);
}
