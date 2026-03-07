package com.jvn.audiofx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioFxControllerTest {
  @Test
  void prefersNativeProvidersWhenBridgeAvailable() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AudioFxController controller = new AudioFxController();
    assertEquals("native-loom", controller.ambienceProviderId());
    assertEquals("native-beez", controller.beezProviderId());
    assertTrue(controller.diagnosticsSummary().contains("bridge="));
  }
}
