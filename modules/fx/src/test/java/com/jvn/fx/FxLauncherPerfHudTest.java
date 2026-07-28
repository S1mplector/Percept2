package com.jvn.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FxLauncherPerfHudTest {

  @Test
  void runtimeMemoryUsesJvmLabel() {
    assertEquals("JVM 128 MB", FxLauncher.jvmMemoryText(127.6));
  }

  @Test
  void runtimeFpsFormatsAvailableSample() {
    assertEquals("FPS 60", FxLauncher.fpsText(59.7));
  }

  @Test
  void runtimeFpsKeepsPlaceholderUntilSampleExists() {
    assertEquals("FPS --", FxLauncher.fpsText(Double.NaN));
  }
}
