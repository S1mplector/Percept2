package com.jvn.audiofx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.audio.AmbienceProfile;
import org.junit.jupiter.api.Test;

class NativeAmbienceMathTest {

  @Test
  void nativeAmbienceRenderingIsDeterministicForSameConfiguration() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile profile = new AmbienceProfile(0.72f, 0.58f, 0.50f, 0.55f, true);
    byte[] first = renderAmbience("thunder", 0.88f, 0.60f, profile, 176_400);
    byte[] second = renderAmbience("thunder", 0.88f, 0.60f, profile, 176_400);
    assertArrayEquals(first, second, "native ambience output should be deterministic for the same preset and parameters");
  }

  @Test
  void reconfiguringSameRendererResetsDeterministicState() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile profile = new AmbienceProfile(0.70f, 0.66f, 0.52f, 0.61f, true);
    try (AudioFxNativeBridge.AmbienceRenderer renderer = AudioFxNativeBridge.createAmbienceRenderer(44_100)) {
      renderer.configure("ocean", 0.84f, 0.58f, profile);
      byte[] first = renderChunk(renderer, 88_200);
      renderer.configure("ocean", 0.84f, 0.58f, profile);
      byte[] second = renderChunk(renderer, 88_200);
      assertArrayEquals(
          first,
          second,
          "reconfiguring the same native renderer should reset RNG/LFO state");
    }
  }

  @Test
  void chunkSizeDoesNotChangeRenderedAmbience() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile profile = new AmbienceProfile(0.76f, 0.81f, 0.57f, 0.73f, true);
    try (AudioFxNativeBridge.AmbienceRenderer renderer = AudioFxNativeBridge.createAmbienceRenderer(44_100)) {
      renderer.configure("rain", 0.86f, 0.58f, profile);
      byte[] fixed = renderChunkPattern(renderer, 132_300, new int[] {4096});
      renderer.configure("rain", 0.86f, 0.58f, profile);
      byte[] varied = renderChunkPattern(renderer, 132_300, new int[] {257, 997, 61, 2048, 509});
      assertArrayEquals(fixed, varied, "render chunking should not alter ambience PCM");
    }
  }

  @Test
  void windMotionRaisesMacrodynamicVariation() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    double lowMotion = envelopeVariation(monoSamples(renderAmbience(
        "wind",
        0.82f,
        0.55f,
        new AmbienceProfile(0.72f, 0.10f, 0.50f, 0.55f, true),
        352_800)), 2048);
    double highMotion = envelopeVariation(monoSamples(renderAmbience(
        "wind",
        0.82f,
        0.55f,
        new AmbienceProfile(0.72f, 0.95f, 0.50f, 0.55f, true),
        352_800)), 2048);
    assertTrue(highMotion > lowMotion * 1.04,
        "higher wind motion should yield stronger envelope variation");
  }

  @Test
  void rainAccentRaisesImpactTexture() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    double lowAccent = highBandProxy(monoSamples(renderAmbience(
        "rain",
        0.80f,
        0.55f,
        new AmbienceProfile(0.65f, 0.50f, 0.50f, 0.10f, true),
        176_400)));
    double highAccent = highBandProxy(monoSamples(renderAmbience(
        "rain",
        0.80f,
        0.55f,
        new AmbienceProfile(0.65f, 0.50f, 0.50f, 0.95f, true),
        176_400)));
    assertTrue(highAccent > lowAccent * 1.05,
        "higher rain accent should increase droplet/splash texture");
  }

  @Test
  void oceanAccentDeepensWaveWeight() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    double lowAccent = lowBandShare(monoSamples(renderAmbience(
        "ocean",
        0.85f,
        0.55f,
        new AmbienceProfile(0.70f, 0.50f, 0.55f, 0.10f, true),
        352_800)), 220.0);
    double highAccent = lowBandShare(monoSamples(renderAmbience(
        "ocean",
        0.85f,
        0.55f,
        new AmbienceProfile(0.70f, 0.50f, 0.55f, 0.95f, true),
        352_800)), 220.0);
    assertTrue(highAccent > lowAccent * 1.10,
        "higher ocean accent should deepen undertow/crash low-band weight");
  }

  @Test
  void thunderAccentRaisesMacrodynamicVariation() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    double lowAccent = envelopeVariation(monoSamples(renderAmbience(
        "thunder",
        0.88f,
        0.60f,
        new AmbienceProfile(0.72f, 0.60f, 0.55f, 0.10f, true),
        352_800)), 2048);
    double highAccent = envelopeVariation(monoSamples(renderAmbience(
        "thunder",
        0.88f,
        0.60f,
        new AmbienceProfile(0.72f, 0.60f, 0.55f, 0.95f, true),
        352_800)), 2048);
    assertTrue(highAccent > lowAccent * 1.10,
        "higher thunder accent should increase storm-scale dynamic swings");
  }

  @Test
  void fireplaceDetailAddsCrackleWithoutLosingWarmth() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    double[] lowDetail = monoSamples(renderAmbience(
        "fireplace",
        0.78f,
        0.55f,
        new AmbienceProfile(0.10f, 0.45f, 0.40f, 0.55f, true),
        176_400));
    double[] highDetail = monoSamples(renderAmbience(
        "fireplace",
        0.78f,
        0.55f,
        new AmbienceProfile(0.95f, 0.45f, 0.40f, 0.55f, true),
        176_400));
    double lowTexture = highBandProxy(lowDetail);
    double highTexture = highBandProxy(highDetail);
    double lowWarmth = lowBandShare(lowDetail, 320.0);
    double highWarmth = lowBandShare(highDetail, 320.0);
    assertTrue(highTexture > lowTexture * 1.45,
        "higher fireplace detail should add noticeably more crackle texture");
    assertTrue(highWarmth > lowWarmth * 0.97,
        "higher fireplace detail should not hollow out the fire bed");
  }

  @Test
  void nightInsectsAccentRaisesChorusBrightnessAndPeaks() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    double[] lowAccent = monoSamples(renderAmbience(
        "night_insects",
        0.82f,
        0.55f,
        new AmbienceProfile(0.70f, 0.60f, 0.45f, 0.10f, true),
        352_800));
    double[] highAccent = monoSamples(renderAmbience(
        "night_insects",
        0.82f,
        0.55f,
        new AmbienceProfile(0.70f, 0.60f, 0.45f, 0.95f, true),
        352_800));
    double lowHighBand = highBandProxy(lowAccent);
    double highHighBand = highBandProxy(highAccent);
    double lowCrest = crestFactor(lowAccent);
    double highCrest = crestFactor(highAccent);
    assertTrue(highHighBand > lowHighBand * 1.40,
        "higher night-insect accent should brighten the chorus");
    assertTrue(highCrest > lowCrest * 1.04,
        "higher night-insect accent should create stronger burst peaks");
  }

  @Test
  void extremeConfigurationsRemainDcStable() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile[] profiles = {
      new AmbienceProfile(0.0f, 0.0f, 0.0f, 0.0f, true),
      new AmbienceProfile(1.0f, 1.0f, 1.0f, 1.0f, true)
    };
    for (String preset : new String[] {"wind", "rain", "ocean", "thunder", "fireplace", "night_insects"}) {
      for (AmbienceProfile profile : profiles) {
        double[] samples = monoSamples(renderAmbience(preset, 1.0f, 0.75f, profile, 88_200));
        double meanOffset = Math.abs(mean(samples));
        assertTrue(meanOffset < 0.015, preset + " should stay DC-stable under extreme controls");
        assertTrue(Double.isFinite(rms(samples)), preset + " rms should stay finite");
      }
    }
  }

  @Test
  void multipleSampleRatesProduceStableAudibleOutput() {
    assertTrue(AudioFxNativeBridge.isAvailable(), AudioFxNativeBridge.diagnostics());
    AmbienceProfile profile = new AmbienceProfile(0.78f, 0.72f, 0.54f, 0.63f, true);
    for (int sampleRate : new int[] {22_050, 48_000}) {
      double[] samples = monoSamples(renderAmbience(sampleRate, "ocean", 0.84f, 0.56f, profile, sampleRate * 2));
      assertTrue(rms(samples) > 0.01, "sampleRate " + sampleRate + " should remain audible");
      assertTrue(Math.abs(mean(samples)) < 0.02, "sampleRate " + sampleRate + " should remain DC-safe");
    }
  }

  private static byte[] renderAmbience(
      String preset, float intensity, float volume, AmbienceProfile profile, int totalFrames) {
    try (AudioFxNativeBridge.AmbienceRenderer renderer = AudioFxNativeBridge.createAmbienceRenderer(44_100)) {
      renderer.configure(preset, intensity, volume, profile);
      return renderChunk(renderer, totalFrames);
    }
  }

  private static byte[] renderAmbience(
      int sampleRate, String preset, float intensity, float volume, AmbienceProfile profile, int totalFrames) {
    try (AudioFxNativeBridge.AmbienceRenderer renderer = AudioFxNativeBridge.createAmbienceRenderer(sampleRate)) {
      renderer.configure(preset, intensity, volume, profile);
      return renderChunk(renderer, totalFrames);
    }
  }

  private static byte[] renderChunk(AudioFxNativeBridge.AmbienceRenderer renderer, int totalFrames) {
    return renderChunkPattern(renderer, totalFrames, new int[] {4096});
  }

  private static byte[] renderChunkPattern(
      AudioFxNativeBridge.AmbienceRenderer renderer, int totalFrames, int[] chunkPattern) {
    byte[] pcm = new byte[totalFrames * 4];
    int offset = 0;
    int remainingFrames = totalFrames;
    int chunkIndex = 0;
    while (remainingFrames > 0) {
      int requested = chunkPattern[chunkIndex % chunkPattern.length];
      int chunkFrames = Math.min(requested, remainingFrames);
      byte[] chunk = new byte[chunkFrames * 4];
      int written = renderer.render(chunk, chunkFrames);
      System.arraycopy(chunk, 0, pcm, offset, written);
      offset += written;
      remainingFrames -= chunkFrames;
      chunkIndex++;
    }
    return pcm;
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

  private static double crestFactor(double[] samples) {
    double peak = 0.0;
    double sumSquares = 0.0;
    for (double sample : samples) {
      double abs = Math.abs(sample);
      if (abs > peak) peak = abs;
      sumSquares += sample * sample;
    }
    return peak / Math.max(1e-9, Math.sqrt(sumSquares / Math.max(1, samples.length)));
  }

  private static double rms(double[] samples) {
    double sumSquares = 0.0;
    for (double sample : samples) {
      sumSquares += sample * sample;
    }
    return Math.sqrt(sumSquares / Math.max(1, samples.length));
  }

  private static double mean(double[] samples) {
    double sum = 0.0;
    for (double sample : samples) {
      sum += sample;
    }
    return sum / Math.max(1, samples.length);
  }
}
