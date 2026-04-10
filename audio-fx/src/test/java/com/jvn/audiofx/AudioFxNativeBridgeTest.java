package com.jvn.audiofx;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jvn.core.audio.AmbienceProfile;

class AudioFxNativeBridgeTest {
  @BeforeEach
  void requireNativeBridge() {
    Assumptions.assumeTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
  }

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

  @Test
  void spreadIncreasesStereoSeparation() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    byte[] narrow = renderAmbience(
        "ocean",
        0.78f,
        0.58f,
        new AmbienceProfile(0.70f, 0.60f, 0.05f, 0.55f, true),
        44_100);
    byte[] wide = renderAmbience(
        "ocean",
        0.78f,
        0.58f,
        new AmbienceProfile(0.70f, 0.60f, 0.95f, 0.55f, true),
        44_100);
    double narrowSeparation = stereoSeparation(narrow);
    double wideSeparation = stereoSeparation(wide);
    assertTrue(wideSeparation > narrowSeparation * 1.8,
        "higher spread should widen left/right divergence");
  }

  @Test
  void thunderKeepsMoreStrikeTextureThanOcean() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile profile = new AmbienceProfile(0.88f, 0.78f, 0.65f, 0.96f, true);
    byte[] thunder = renderAmbience("thunder", 0.96f, 0.62f, profile, 176_400);
    byte[] ocean = renderAmbience("ocean", 0.96f, 0.62f, profile, 176_400);
    double thunderTexture = highBandProxy(monoSamples(thunder));
    double oceanTexture = highBandProxy(monoSamples(ocean));
    assertTrue(thunderTexture > oceanTexture * 1.25,
        "thunder should keep more strike texture than ocean");
  }

  @Test
  void fireplaceStaysWarmerThanNightInsects() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile profile = new AmbienceProfile(0.72f, 0.58f, 0.40f, 0.66f, true);
    double fireWarmth = lowBandShare(monoSamples(renderAmbience("fireplace", 0.82f, 0.55f, profile, 44_100)), 320.0);
    double insectWarmth = lowBandShare(monoSamples(renderAmbience("night_insects", 0.82f, 0.55f, profile, 44_100)), 320.0);
    assertTrue(fireWarmth > insectWarmth * 1.03,
        "fireplace should keep more warm-band energy than night insects");
  }

  @Test
  void nightInsectsStayBrighterThanRain() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile profile = new AmbienceProfile(0.80f, 0.82f, 0.50f, 0.90f, true);
    double insectTexture = highBandProxy(monoSamples(renderAmbience("night_insects", 0.88f, 0.55f, profile, 88_200)));
    double rainTexture = highBandProxy(monoSamples(renderAmbience("rain", 0.88f, 0.55f, profile, 88_200)));
    assertTrue(insectTexture > rainTexture * 1.20,
        "night insects should stay brighter and more chirp-heavy than rain");
  }

  @Test
  void detailRaisesHighBandTextureForOcean() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    byte[] lowDetail = renderAmbience(
        "ocean",
        0.80f,
        0.55f,
        new AmbienceProfile(0.10f, 0.55f, 0.50f, 0.55f, true),
        44_100);
    byte[] highDetail = renderAmbience(
        "ocean",
        0.80f,
        0.55f,
        new AmbienceProfile(0.95f, 0.55f, 0.50f, 0.55f, true),
        44_100);
    double lowTexture = highBandProxy(monoSamples(lowDetail));
    double highTexture = highBandProxy(monoSamples(highDetail));
    assertTrue(highTexture > lowTexture * 1.10,
        "detail should increase high-band ocean texture");
  }

  @Test
  void presetAliasesResolveToDeterministicCanonicalOutput() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile profile = new AmbienceProfile(0.67f, 0.58f, 0.44f, 0.73f, true);
    assertArrayEquals(
        renderAmbience("rain", 0.81f, 0.55f, profile, 44_100),
        renderAmbience("storm", 0.81f, 0.55f, profile, 44_100));
    assertArrayEquals(
        renderAmbience("ocean", 0.81f, 0.55f, profile, 44_100),
        renderAmbience("surf", 0.81f, 0.55f, profile, 44_100));
    assertArrayEquals(
        renderAmbience("thunder", 0.81f, 0.55f, profile, 44_100),
        renderAmbience("lightning", 0.81f, 0.55f, profile, 44_100));
    assertArrayEquals(
        renderAmbience("fireplace", 0.81f, 0.55f, profile, 44_100),
        renderAmbience("campfire", 0.81f, 0.55f, profile, 44_100));
    assertArrayEquals(
        renderAmbience("night_insects", 0.81f, 0.55f, profile, 44_100),
        renderAmbience("cricket", 0.81f, 0.55f, profile, 44_100));
  }

  @Test
  void nonLoopingAmbienceStopsAndReturnsSilenceAfterTimeout() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile profile = new AmbienceProfile(0.72f, 0.52f, 0.60f, 0.66f, false);
    try (AudioFxNativeBridge.AmbienceRenderer renderer = AudioFxNativeBridge.createAmbienceRenderer(44_100)) {
      renderer.configure("thunder", 0.86f, 0.55f, profile);
      byte[] chunk = new byte[4096 * 4];
      int totalFrames = 0;
      while (!renderer.isFinished() && totalFrames < 44_100 * 18) {
        int written = renderer.render(chunk, 4096);
        assertEquals(chunk.length, written);
        totalFrames += 4096;
      }
      assertTrue(renderer.isFinished(), "non-looping ambience should auto-stop");
      byte[] after = new byte[4096 * 4];
      int writtenAfterStop = renderer.render(after, 4096);
      assertEquals(after.length, writtenAfterStop);
      assertEquals(0L, sampleEnergy(after), "finished renderer should emit silence");
    }
  }

  @Test
  void zeroFrameRenderReturnsZeroWithoutChangingState() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    try (AudioFxNativeBridge.AmbienceRenderer renderer = AudioFxNativeBridge.createAmbienceRenderer(44_100)) {
      renderer.configure("wind", 0.82f, 0.55f, AmbienceProfile.defaults());
      assertEquals(0, renderer.render(new byte[0], 0));
      assertFalse(renderer.isFinished(), "zero-frame render should not finish renderer");
    }
  }

  private static byte[] renderAmbience(String preset, AmbienceProfile profile) {
    return renderAmbience(preset, 0.75f, 0.55f, profile, 4096);
  }

  private static byte[] renderAmbience(String preset, float intensity, float volume, AmbienceProfile profile, int totalFrames) {
    try (AudioFxNativeBridge.AmbienceRenderer renderer = AudioFxNativeBridge.createAmbienceRenderer(44_100)) {
      renderer.configure(preset, intensity, volume, profile);
      byte[] pcm = new byte[totalFrames * 4];
      int offset = 0;
      int remainingFrames = totalFrames;
      while (remainingFrames > 0) {
        int chunkFrames = Math.min(4096, remainingFrames);
        byte[] chunk = new byte[chunkFrames * 4];
        int written = renderer.render(chunk, chunkFrames);
        assertTrue(written > 0);
        System.arraycopy(chunk, 0, pcm, offset, written);
        offset += written;
        remainingFrames -= chunkFrames;
      }
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

  private static double[] monoSamples(byte[] pcm) {
    int frames = pcm.length / 4;
    double[] mono = new double[frames];
    for (int frame = 0; frame < frames; frame++) {
      int offset = frame * 4;
      short left = (short) (((pcm[offset + 1] & 0xFF) << 8) | (pcm[offset] & 0xFF));
      short right = (short) (((pcm[offset + 3] & 0xFF) << 8) | (pcm[offset + 2] & 0xFF));
      mono[frame] = ((left + right) * 0.5) / 32767.0;
    }
    return mono;
  }

  private static double stereoSeparation(byte[] pcm) {
    double sumSquares = 0.0;
    int frames = pcm.length / 4;
    for (int frame = 0; frame < frames; frame++) {
      int offset = frame * 4;
      short left = (short) (((pcm[offset + 1] & 0xFF) << 8) | (pcm[offset] & 0xFF));
      short right = (short) (((pcm[offset + 3] & 0xFF) << 8) | (pcm[offset + 2] & 0xFF));
      double delta = (left - right) / 32767.0;
      sumSquares += delta * delta;
    }
    return Math.sqrt(sumSquares / Math.max(1, frames));
  }

  private static double lowBandShare(double[] samples, double cutoffHz) {
    double dt = 1.0 / 44_100.0;
    double alpha = dt / ((1.0 / (2.0 * Math.PI * cutoffHz)) + dt);
    double lp = 0.0;
    double low = 0.0;
    double high = 0.0;
    for (double sample : samples) {
      lp += alpha * (sample - lp);
      double hp = sample - lp;
      low += lp * lp;
      high += hp * hp;
    }
    return low / Math.max(1e-9, low + high);
  }

  private static double envelopeVariation(double[] samples, int windowSize) {
    int windows = samples.length / windowSize;
    double[] envelopes = new double[windows];
    for (int w = 0; w < windows; w++) {
      double sumSquares = 0.0;
      for (int i = 0; i < windowSize; i++) {
        double sample = samples[w * windowSize + i];
        sumSquares += sample * sample;
      }
      envelopes[w] = Math.sqrt(sumSquares / windowSize);
    }
    double mean = 0.0;
    for (double envelope : envelopes) {
      mean += envelope;
    }
    mean /= Math.max(1, envelopes.length);
    double variance = 0.0;
    for (double envelope : envelopes) {
      double delta = envelope - mean;
      variance += delta * delta;
    }
    variance /= Math.max(1, envelopes.length);
    return Math.sqrt(variance) / Math.max(1e-9, mean);
  }

  private static double highBandProxy(double[] samples) {
    double previous = samples[0];
    double acc = 0.0;
    for (int i = 1; i < samples.length; i++) {
      double current = samples[i];
      acc += Math.abs(current - previous);
      previous = current;
    }
    return acc / Math.max(1, samples.length - 1);
  }
}
