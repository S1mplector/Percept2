package com.jvn.audiofx;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioFxNativeBridgeTest {
  @Test
  void nativeBridgeLoads() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    assertTrue(AudioFxNativeBridge.diagnostics().contains("jvn-audiofx-native"));
  }

  @Test
  void beezRendererProducesAudiblePcm() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    try (AudioFxNativeBridge.BeezRenderer renderer = AudioFxNativeBridge.createBeezRenderer(44_100)) {
      renderer.configure("confirm", 0.9f, 0.7f, false);
      byte[] pcm = new byte[4096 * 4];
      int written = renderer.render(pcm, 4096);
      assertTrue(written > 0);
      assertTrue(sampleEnergy(pcm) > 20_000L, "Beez renderer produced near-silent output");
      assertFalse(renderer.isFinished(), "Renderer should still be active during the first buffer");
    }
  }

  @Test
  void ambienceRendererSupportsMultiplePresets() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    byte[] wind = renderAmbience("wind");
    byte[] rain = renderAmbience("rain");
    byte[] ocean = renderAmbience("ocean");

    assertTrue(sampleEnergy(wind) > 10_000L, "Wind ambience rendered silence");
    assertTrue(sampleEnergy(rain) > 10_000L, "Rain ambience rendered silence");
    assertTrue(sampleEnergy(ocean) > 10_000L, "Ocean ambience rendered silence");
    assertNotEquals(sampleEnergy(wind), sampleEnergy(rain));
    assertFalse(Arrays.equals(wind, rain), "Wind and rain buffers should not be identical");
    assertFalse(Arrays.equals(rain, ocean), "Rain and ocean buffers should not be identical");
  }

  private static byte[] renderAmbience(String preset) {
    try (AudioFxNativeBridge.AmbienceRenderer renderer = AudioFxNativeBridge.createAmbienceRenderer(44_100)) {
      renderer.configure(preset, 0.75f, 0.55f, true);
      byte[] pcm = new byte[4096 * 4];
      int written = renderer.render(pcm, 4096);
      assertTrue(written > 0);
      return pcm;
    }
  }

  private static long sampleEnergy(byte[] pcm) {
    long energy = 0L;
    for (int i = 0; i + 1 < pcm.length; i += 2) {
      int lo = pcm[i] & 0xFF;
      int hi = pcm[i + 1];
      short sample = (short) ((hi << 8) | lo);
      energy += Math.abs(sample);
    }
    return energy;
  }
}
