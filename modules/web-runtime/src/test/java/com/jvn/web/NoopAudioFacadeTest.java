package com.jvn.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class NoopAudioFacadeTest {

  @Test
  void allCoreOperationsAreNoOpsAndDoNotThrow() {
    NoopAudioFacade facade = new NoopAudioFacade();

    assertDoesNotThrow(() -> facade.playBgm("bgm/title.ogg", true));
    assertDoesNotThrow(facade::stopBgm);
    assertDoesNotThrow(() -> facade.playSfx("sfx/click.ogg"));
    assertDoesNotThrow(() -> facade.playVoice("voice/line1.ogg"));
    assertDoesNotThrow(facade::stopAllAudio);
    assertDoesNotThrow(facade::close);
  }
}
