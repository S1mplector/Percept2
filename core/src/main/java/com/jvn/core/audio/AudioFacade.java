package com.jvn.core.audio;

public interface AudioFacade {
  void playBgm(String trackId, boolean loop);
  void stopBgm();
  void playSfx(String sfxId);
  default void playVoice(String voiceId) { playSfx(voiceId); }
  default void stopSfx() {}
  default void stopVoice() { stopSfx(); }
  default void stopAllAudio() {
    stopVoice();
    stopSfx();
    stopBgm();
  }
  default void setBgmVolume(float volume) {}
  default void setSfxVolume(float volume) {}
  default void setVoiceVolume(float volume) {}
  // Optional advanced controls; implement if backend supports them
  default void pauseBgm() {}
  default void resumeBgm() {}
  default void pauseAllAudio() { pauseBgm(); }
  default void resumeAllAudio() { resumeBgm(); }
  default void seekBgmSeconds(double seconds) {}
  default void crossfadeBgm(String trackId, long ms, boolean loop) {}
}
