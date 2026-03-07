package com.jvn.audiofx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}
