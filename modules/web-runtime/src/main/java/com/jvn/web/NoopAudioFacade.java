package com.jvn.web;

import com.jvn.core.audio.AudioFacade;

/**
 * No-op {@link AudioFacade} used until sub-project 4 wires real Web Audio playback.
 */
public final class NoopAudioFacade implements AudioFacade {

  @Override
  public void playBgm(String trackId, boolean loop) {}

  @Override
  public void stopBgm() {}

  @Override
  public void playSfx(String sfxId) {}
}
