package com.jvn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebAudioFacadeVolumeTest {

  @Test
  void volumeAndMuteStateTrackedBeforeAnyPlaybackCall() {
    WebAudioFacade facade = new WebAudioFacade();

    assertEquals(1f, facade.getMasterVolume());
    assertEquals(0.7f, facade.getBgmVolume());
    assertEquals(0.8f, facade.getSfxVolume());
    assertEquals(1f, facade.getVoiceVolume());
    assertFalse(facade.isMuted());

    facade.setBgmVolume(0.3f);
    facade.setMasterVolume(0.5f);
    facade.setMuted(true);

    assertEquals(0.3f, facade.getBgmVolume());
    assertEquals(0.5f, facade.getMasterVolume());
    assertTrue(facade.isMuted());
  }

  @Test
  void backendIdAndCapabilitiesMatchDesktopParity() {
    WebAudioFacade facade = new WebAudioFacade();
    assertEquals("webaudio", facade.backendId());
    assertTrue(facade.capabilities().dedicatedVoiceChannel());
    assertTrue(facade.capabilities().crossfade());
    assertTrue(facade.capabilities().spectrum());
  }
}
