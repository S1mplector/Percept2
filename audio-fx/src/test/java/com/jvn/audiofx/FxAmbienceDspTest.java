package com.jvn.audiofx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxAmbienceDspTest {
  private static final double DT = 1.0 / 44_100.0;

  @Test
  void parsesPresetAliases() {
    assertEquals(FxAmbienceDsp.Preset.WIND, FxAmbienceDsp.Preset.fromToken("wind"));
    assertEquals(FxAmbienceDsp.Preset.RAIN, FxAmbienceDsp.Preset.fromToken("drizzle"));
    assertEquals(FxAmbienceDsp.Preset.OCEAN, FxAmbienceDsp.Preset.fromToken("waves"));
    assertEquals(FxAmbienceDsp.Preset.WIND, FxAmbienceDsp.Preset.fromToken("unknown"));
  }

  @Test
  void sameSeedProducesDeterministicWaveform() {
    FxAmbienceDsp.State a = new FxAmbienceDsp.State(12345L);
    FxAmbienceDsp.State b = new FxAmbienceDsp.State(12345L);

    for (int i = 0; i < 2048; i++) {
      double sa = FxAmbienceDsp.synthSample(a, DT, FxAmbienceDsp.Preset.RAIN, 0.73f);
      double sb = FxAmbienceDsp.synthSample(b, DT, FxAmbienceDsp.Preset.RAIN, 0.73f);
      assertEquals(sa, sb, 1e-12, "sample mismatch at index " + i);
    }
  }

  @Test
  void differentPresetsDoNotCollapseToSameSignal() {
    FxAmbienceDsp.State rain = new FxAmbienceDsp.State(424242L);
    FxAmbienceDsp.State wind = new FxAmbienceDsp.State(424242L);

    double totalDelta = 0.0;
    for (int i = 0; i < 1024; i++) {
      double sr = FxAmbienceDsp.synthSample(rain, DT, FxAmbienceDsp.Preset.RAIN, 0.65f);
      double sw = FxAmbienceDsp.synthSample(wind, DT, FxAmbienceDsp.Preset.WIND, 0.65f);
      totalDelta += Math.abs(sr - sw);
    }
    assertTrue(totalDelta > 1.0, "expected clearly different waveforms across presets");
  }

  @Test
  void outputRemainsBoundedForAllPresets() {
    for (FxAmbienceDsp.Preset preset : FxAmbienceDsp.Preset.values()) {
      FxAmbienceDsp.State st = new FxAmbienceDsp.State(0xCAFEBABEL + preset.ordinal());
      for (int i = 0; i < 20_000; i++) {
        double sample = FxAmbienceDsp.synthSample(st, DT, preset, 1.0f);
        assertTrue(sample >= -1.0 && sample <= 1.0, "out of bounds sample for " + preset);
      }
    }
  }

  @Test
  void higherIntensityIncreasesAverageEnergy() {
    double low = rms(FxAmbienceDsp.Preset.OCEAN, 0.20f);
    double high = rms(FxAmbienceDsp.Preset.OCEAN, 0.95f);
    assertTrue(high > low * 1.10, "expected noticeably higher energy at higher intensity");
    assertNotEquals(low, high, 1e-9);
  }

  private double rms(FxAmbienceDsp.Preset preset, float intensity) {
    FxAmbienceDsp.State st = new FxAmbienceDsp.State(77777L);
    // Warm-up filter state before measuring RMS window.
    for (int i = 0; i < 8000; i++) {
      FxAmbienceDsp.synthSample(st, DT, preset, intensity);
    }
    double acc = 0.0;
    int n = 8192;
    for (int i = 0; i < n; i++) {
      double s = FxAmbienceDsp.synthSample(st, DT, preset, intensity);
      acc += s * s;
    }
    return Math.sqrt(acc / n);
  }
}

