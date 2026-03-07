package com.jvn.audiofx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FxAmbienceDspTest {
  private static final double DT = 1.0 / 44_100.0;

  @Test
  void parsesPresetAliases() {
    assertEquals(FxAmbienceDsp.Preset.WIND, FxAmbienceDsp.Preset.fromToken("wind"));
    assertEquals(FxAmbienceDsp.Preset.RAIN, FxAmbienceDsp.Preset.fromToken("drizzle"));
    assertEquals(FxAmbienceDsp.Preset.OCEAN, FxAmbienceDsp.Preset.fromToken("waves"));
    assertEquals(FxAmbienceDsp.Preset.THUNDER, FxAmbienceDsp.Preset.fromToken("thunder"));
    assertEquals(FxAmbienceDsp.Preset.THUNDER, FxAmbienceDsp.Preset.fromToken("lightning"));
    assertEquals(FxAmbienceDsp.Preset.FIREPLACE, FxAmbienceDsp.Preset.fromToken("fireplace"));
    assertEquals(FxAmbienceDsp.Preset.FIREPLACE, FxAmbienceDsp.Preset.fromToken("campfire"));
    assertEquals(FxAmbienceDsp.Preset.NIGHT_INSECTS, FxAmbienceDsp.Preset.fromToken("night_insects"));
    assertEquals(FxAmbienceDsp.Preset.NIGHT_INSECTS, FxAmbienceDsp.Preset.fromToken("crickets"));
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

  @Test
  void windGustEnvelopeShowsNaturalDynamicRange() {
    FxAmbienceDsp.State st = new FxAmbienceDsp.State(0x51515151L);
    // Warm-up
    for (int i = 0; i < 10_000; i++) {
      FxAmbienceDsp.synthSample(st, DT, FxAmbienceDsp.Preset.WIND, 0.78f);
    }
    int windows = 24;
    int windowSize = 2048;
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (int w = 0; w < windows; w++) {
      double rms = 0.0;
      for (int i = 0; i < windowSize; i++) {
        double s = FxAmbienceDsp.synthSample(st, DT, FxAmbienceDsp.Preset.WIND, 0.78f);
        rms += s * s;
      }
      rms = Math.sqrt(rms / windowSize);
      min = Math.min(min, rms);
      max = Math.max(max, rms);
    }
    assertTrue(max > min * 1.20, "wind should breathe with gust envelope variance");
  }

  @Test
  void newPresetsProduceDistinctOutput() {
    FxAmbienceDsp.State thunder = new FxAmbienceDsp.State(555555L);
    FxAmbienceDsp.State fire = new FxAmbienceDsp.State(555555L);
    FxAmbienceDsp.State insects = new FxAmbienceDsp.State(555555L);

    double totalThunderFire = 0.0;
    double totalFireInsects = 0.0;
    for (int i = 0; i < 1024; i++) {
      double st = FxAmbienceDsp.synthSample(thunder, DT, FxAmbienceDsp.Preset.THUNDER, 0.65f);
      double sf = FxAmbienceDsp.synthSample(fire, DT, FxAmbienceDsp.Preset.FIREPLACE, 0.65f);
      double si = FxAmbienceDsp.synthSample(insects, DT, FxAmbienceDsp.Preset.NIGHT_INSECTS, 0.65f);
      totalThunderFire += Math.abs(st - sf);
      totalFireInsects += Math.abs(sf - si);
    }
    assertTrue(totalThunderFire > 1.0, "Thunder and fireplace should produce different waveforms");
    assertTrue(totalFireInsects > 1.0, "Fireplace and night_insects should produce different waveforms");
  }

  @Test
  void newPresetsIntensityAffectsEnergy() {
    for (FxAmbienceDsp.Preset preset : new FxAmbienceDsp.Preset[]{
        FxAmbienceDsp.Preset.THUNDER, FxAmbienceDsp.Preset.FIREPLACE, FxAmbienceDsp.Preset.NIGHT_INSECTS}) {
      double low = rms(preset, 0.20f);
      double high = rms(preset, 0.95f);
      assertTrue(high > low * 1.05, preset + ": higher intensity should increase energy");
    }
  }

  @Test
  void windHighIntensityHasBrighterTexture() {
    double low = highBandProxy(FxAmbienceDsp.Preset.WIND, 0.25f);
    double high = highBandProxy(FxAmbienceDsp.Preset.WIND, 0.95f);
    assertTrue(high > low * 1.08, "higher wind intensity should increase high-band movement");
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

  private double highBandProxy(FxAmbienceDsp.Preset preset, float intensity) {
    FxAmbienceDsp.State st = new FxAmbienceDsp.State(991177L);
    for (int i = 0; i < 9000; i++) {
      FxAmbienceDsp.synthSample(st, DT, preset, intensity);
    }
    double prev = FxAmbienceDsp.synthSample(st, DT, preset, intensity);
    int n = 8192;
    double acc = 0.0;
    for (int i = 0; i < n; i++) {
      double cur = FxAmbienceDsp.synthSample(st, DT, preset, intensity);
      acc += Math.abs(cur - prev);
      prev = cur;
    }
    return acc / n;
  }
}
