package com.jvn.audio.simp3;

import com.jvn.core.audio.AudioFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Simp3AudioService implements AudioFacade {
  private static final Logger log = LoggerFactory.getLogger(Simp3AudioService.class);
  @Override
  public void playBgm(String trackId, boolean loop) {
    // TODO: Wire to Simp3 later
    log.info("playBgm trackId={}, loop={}", trackId, loop);
  }

  @Override
  public void stopBgm() {
    log.info("stopBgm");
  }

  @Override
  public void playSfx(String sfxId) {
    log.info("playSfx sfxId={}", sfxId);
  }
}
