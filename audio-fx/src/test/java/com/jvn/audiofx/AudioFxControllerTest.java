package com.jvn.audiofx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioFxControllerTest {
  @Test
  void defaultsToBeezAmbienceAndBeezChipProviders() {
    AudioFxController controller = new AudioFxController();
    assertEquals("beez", controller.ambienceProviderId());
    assertEquals("beez", controller.beezProviderId());
  }
}

