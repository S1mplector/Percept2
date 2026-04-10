package com.jvn.audiofx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioFxControllerTest {
  @Test
  void selectsProvidersBasedOnBridgeAvailability() {
    AudioFxController controller = new AudioFxController();
    if (AudioFxNativeBridge.isAvailable()) {
      assertEquals("native-loom", controller.ambienceProviderId());
      assertEquals("native-beez", controller.beezProviderId());
      assertTrue(controller.nativeBridgeAvailable());
    } else {
      assertEquals("disabled-ambience", controller.ambienceProviderId());
      assertEquals("disabled-chiptune", controller.beezProviderId());
      assertFalse(controller.nativeBridgeAvailable());
    }
    assertTrue(controller.diagnosticsSummary().contains("bridge="));
  }
}
