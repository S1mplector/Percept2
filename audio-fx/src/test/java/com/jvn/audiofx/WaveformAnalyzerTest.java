package com.jvn.audiofx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class WaveformAnalyzerTest {

  @Test
  void analyzeReturnsCorrectBinCount() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    WaveformAnalyzer.Analysis a = WaveformAnalyzer.analyze(s, 64);
    assertEquals(64, a.envelope().length, "Envelope should have requested bin count");
  }

  @Test
  void analyzeProducesNonZeroRmsForAmbience() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    s.setPreset("rain");
    s.setIntensity(0.8f);
    WaveformAnalyzer.Analysis a = WaveformAnalyzer.analyze(s, 32);
    assertTrue(a.rms() > 0f, "RMS should be non-zero for active ambience");
    assertTrue(a.peak() >= a.rms(), "Peak should be >= RMS");
  }

  @Test
  void differentPresetsProduceDifferentEnvelopes() {
    SynthPreviewSettings wind = new SynthPreviewSettings();
    wind.setPreset("wind");
    wind.setIntensity(0.7f);

    SynthPreviewSettings thunder = new SynthPreviewSettings();
    thunder.setPreset("thunder");
    thunder.setIntensity(0.7f);

    WaveformAnalyzer.Analysis aWind = WaveformAnalyzer.analyze(wind, 32);
    WaveformAnalyzer.Analysis aThunder = WaveformAnalyzer.analyze(thunder, 32);

    // At least some envelope bins should differ
    float totalDiff = 0f;
    for (int i = 0; i < 32; i++) {
      totalDiff += Math.abs(aWind.envelope()[i] - aThunder.envelope()[i]);
    }
    assertTrue(totalDiff > 0.01f, "Different presets should produce different envelopes");
  }

  @Test
  void nullSettingsReturnsEmptyAnalysis() {
    WaveformAnalyzer.Analysis a = WaveformAnalyzer.analyze(null, 64);
    assertEquals(0, a.envelope().length);
    assertEquals(0f, a.rms());
    assertEquals(0f, a.peak());
    assertFalse(a.nativeAvailable());
  }

  @Test
  void zeroBinsReturnsEmptyAnalysis() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    WaveformAnalyzer.Analysis a = WaveformAnalyzer.analyze(s, 0);
    assertEquals(0, a.envelope().length);
    assertFalse(a.nativeAvailable());
  }

  @Test
  void fromPcmBytesExtractsEnvelope() {
    // Generate a simple 16-bit stereo LE PCM buffer with a known pattern
    int frames = 256;
    int channels = 2;
    int bytesPerSample = 2;
    byte[] pcm = new byte[frames * channels * bytesPerSample];

    // Fill with a sine-like pattern in the left channel
    for (int i = 0; i < frames; i++) {
      double t = (double) i / frames;
      short sample = (short) (Math.sin(t * Math.PI * 4) * 16000);
      int offset = i * channels * bytesPerSample;
      pcm[offset] = (byte) (sample & 0xFF);
      pcm[offset + 1] = (byte) ((sample >> 8) & 0xFF);
      // right channel = 0
    }

    WaveformAnalyzer.Analysis a = WaveformAnalyzer.fromPcmBytes(pcm, pcm.length, 16);
    assertEquals(16, a.envelope().length);
    assertTrue(a.rms() > 0f, "RMS from synthetic PCM should be non-zero");
    assertTrue(a.peak() > 0f, "Peak from synthetic PCM should be non-zero");
    assertTrue(a.peak() <= 1.0f, "Peak should be normalized to <= 1.0");
  }

  @Test
  void envelopeValuesAreNormalized() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    s.setIntensity(0.95f);
    s.setVolume(0.90f);
    WaveformAnalyzer.Analysis a = WaveformAnalyzer.analyze(s, 128);
    for (float v : a.envelope()) {
      assertTrue(v >= 0f && v <= 1.0f, "Envelope values should be in [0, 1]: " + v);
    }
  }

  @Test
  void chiptuneAnalysisWorks() {
    SynthPreviewSettings s = new SynthPreviewSettings();
    s.setType(SynthPreviewSettings.SynthType.CHIPTUNE);
    s.setCueId("blip");
    s.setIntensity(0.8f);
    WaveformAnalyzer.Analysis a = WaveformAnalyzer.analyze(s, 32);
    // Should return some result without crashing, even if native unavailable
    assertNotNull(a);
    assertEquals(32, a.envelope().length);
  }

  // --- EMPTY constant ---

  @Test
  void emptySentinelHasZeroValues() {
    WaveformAnalyzer.Analysis empty = WaveformAnalyzer.EMPTY;
    assertNotNull(empty);
    assertEquals(0, empty.envelope().length);
    assertEquals(0f, empty.rms());
    assertEquals(0f, empty.peak());
    assertFalse(empty.nativeAvailable());
  }

  // --- StreamingAnalyzer ---

  @Test
  @Timeout(5)
  void streamingAnalyzerStartAndStopLifecycle() throws InterruptedException {
    WaveformAnalyzer.StreamingAnalyzer sa = new WaveformAnalyzer.StreamingAnalyzer();
    assertFalse(sa.isRunning());
    assertSame(WaveformAnalyzer.EMPTY, sa.latest());

    SynthPreviewSettings s = new SynthPreviewSettings();
    s.setPreset("wind");
    s.setIntensity(0.7f);
    sa.start(s);
    assertTrue(sa.isRunning());

    // Give the render thread time to produce at least one analysis
    Thread.sleep(200);
    WaveformAnalyzer.Analysis a = sa.latest();
    assertNotNull(a);
    // Envelope should have data (128 bins from DEFAULT_BINS)
    assertTrue(a.envelope().length > 0, "Streaming should produce envelope bins");

    sa.stop();
    assertFalse(sa.isRunning());
  }

  @Test
  @Timeout(5)
  void streamingAnalyzerReconfigureUpdatesAnalysis() throws InterruptedException {
    WaveformAnalyzer.StreamingAnalyzer sa = new WaveformAnalyzer.StreamingAnalyzer();
    SynthPreviewSettings s = new SynthPreviewSettings();
    s.setPreset("wind");
    s.setIntensity(0.3f);
    sa.start(s);
    Thread.sleep(200);
    WaveformAnalyzer.Analysis a1 = sa.latest();

    // Reconfigure to a different preset/intensity
    SynthPreviewSettings s2 = new SynthPreviewSettings();
    s2.setPreset("thunder");
    s2.setIntensity(0.9f);
    sa.reconfigure(s2);
    Thread.sleep(300);
    WaveformAnalyzer.Analysis a2 = sa.latest();

    // Both should be valid analyses
    assertNotNull(a1);
    assertNotNull(a2);
    assertTrue(a1.envelope().length > 0);
    assertTrue(a2.envelope().length > 0);

    sa.stop();
  }

  @Test
  @Timeout(5)
  void streamingAnalyzerStopIsIdempotent() {
    WaveformAnalyzer.StreamingAnalyzer sa = new WaveformAnalyzer.StreamingAnalyzer();
    // Calling stop before start should not throw
    sa.stop();
    sa.stop();
    assertFalse(sa.isRunning());
  }

  @Test
  @Timeout(5)
  void streamingAnalyzerNullSettingsDoesNotStart() {
    WaveformAnalyzer.StreamingAnalyzer sa = new WaveformAnalyzer.StreamingAnalyzer();
    sa.start(null);
    assertFalse(sa.isRunning());
  }

  // --- SynthPreviewSettings.copy() ---

  @Test
  void settingsCopyIsIndependent() {
    SynthPreviewSettings original = new SynthPreviewSettings();
    original.setType(SynthPreviewSettings.SynthType.CHIPTUNE);
    original.setPreset("ocean");
    original.setCueId("confirm");
    original.setIntensity(0.3f);
    original.setVolume(0.8f);
    original.setLoop(false);
    original.setDetail(0.1f);
    original.setMotion(0.2f);
    original.setSpread(0.3f);
    original.setAccent(0.4f);

    SynthPreviewSettings copy = original.copy();

    assertEquals(original.type(), copy.type());
    assertEquals(original.preset(), copy.preset());
    assertEquals(original.cueId(), copy.cueId());
    assertEquals(original.intensity(), copy.intensity());
    assertEquals(original.volume(), copy.volume());
    assertEquals(original.loop(), copy.loop());
    assertEquals(original.detail(), copy.detail());
    assertEquals(original.motion(), copy.motion());
    assertEquals(original.spread(), copy.spread());
    assertEquals(original.accent(), copy.accent());

    // Mutating original should not affect copy
    original.setPreset("rain");
    original.setIntensity(0.99f);
    assertEquals("ocean", copy.preset());
    assertEquals(0.3f, copy.intensity());
  }
}
