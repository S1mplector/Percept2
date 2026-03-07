package com.jvn.audiofx;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.jvn.core.audio.AmbienceProfile;

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
    byte[] wind = renderAmbience("wind", AmbienceProfile.defaults());
    byte[] rain = renderAmbience("rain", new AmbienceProfile(0.85f, 0.35f, 0.72f, 0.92f, true));
    byte[] ocean = renderAmbience("ocean", new AmbienceProfile(0.42f, 0.78f, 0.88f, 0.67f, true));

    assertTrue(sampleEnergy(wind) > 10_000L, "Wind ambience rendered silence");
    assertTrue(sampleEnergy(rain) > 10_000L, "Rain ambience rendered silence");
    assertTrue(sampleEnergy(ocean) > 10_000L, "Ocean ambience rendered silence");
    assertNotEquals(sampleEnergy(wind), sampleEnergy(rain));
    assertFalse(Arrays.equals(wind, rain), "Wind and rain buffers should not be identical");
    assertFalse(Arrays.equals(rain, ocean), "Rain and ocean buffers should not be identical");
  }

  @Test
  void ambienceParametersShapeTheNativeRenderer() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    byte[] dryWind = renderAmbience("wind", new AmbienceProfile(0.15f, 0.15f, 0.10f, 0.10f, true));
    byte[] brightWind = renderAmbience("wind", new AmbienceProfile(0.92f, 0.88f, 0.90f, 0.94f, true));
    assertNotEquals(sampleEnergy(dryWind), sampleEnergy(brightWind));
    assertFalse(Arrays.equals(dryWind, brightWind), "Ambience control changes should alter rendered PCM");
  }

  @Test
  void thunderPresetProducesAudibleOutput() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    byte[] thunder = renderAmbience("thunder", new AmbienceProfile(0.60f, 0.55f, 0.50f, 0.70f, true));
    assertTrue(sampleEnergy(thunder) > 10_000L, "Thunder ambience rendered silence");
  }

  @Test
  void fireplacePresetProducesAudibleOutput() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    byte[] fire = renderAmbience("fireplace", new AmbienceProfile(0.65f, 0.50f, 0.55f, 0.60f, true));
    assertTrue(sampleEnergy(fire) > 10_000L, "Fireplace ambience rendered silence");
  }

  @Test
  void nightInsectsPresetProducesAudibleOutput() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    byte[] insects = renderAmbience("night_insects", new AmbienceProfile(0.50f, 0.60f, 0.45f, 0.80f, true));
    assertTrue(sampleEnergy(insects) > 10_000L, "Night insects ambience rendered silence");
  }

  @Test
  void newPresetsProduceMateriallyDifferentOutput() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile profile = new AmbienceProfile(0.60f, 0.55f, 0.50f, 0.65f, true);
    byte[] thunder = renderAmbience("thunder", profile);
    byte[] fire = renderAmbience("fireplace", profile);
    byte[] insects = renderAmbience("night_insects", profile);
    byte[] wind = renderAmbience("wind", profile);

    assertFalse(Arrays.equals(thunder, fire), "Thunder and fireplace should differ");
    assertFalse(Arrays.equals(fire, insects), "Fireplace and night_insects should differ");
    assertFalse(Arrays.equals(thunder, insects), "Thunder and night_insects should differ");
    assertFalse(Arrays.equals(thunder, wind), "Thunder and wind should differ");
  }

  @Test
  void newPresetParametersAlterOutput() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    for (String preset : new String[]{"thunder", "fireplace", "night_insects"}) {
      byte[] low = renderAmbience(preset, new AmbienceProfile(0.15f, 0.15f, 0.10f, 0.10f, true));
      byte[] high = renderAmbience(preset, new AmbienceProfile(0.92f, 0.88f, 0.90f, 0.94f, true));
      assertNotEquals(sampleEnergy(low), sampleEnergy(high),
          preset + ": parameter changes should alter energy");
      assertFalse(Arrays.equals(low, high),
          preset + ": parameter changes should alter PCM output");
    }
  }

  private static byte[] renderAmbience(String preset, AmbienceProfile profile) {
    try (AudioFxNativeBridge.AmbienceRenderer renderer = AudioFxNativeBridge.createAmbienceRenderer(44_100)) {
      renderer.configure(preset, 0.75f, 0.55f, profile);
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
