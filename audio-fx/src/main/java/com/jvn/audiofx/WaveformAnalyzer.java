package com.jvn.audiofx;

/**
 * Renders a short PCM buffer from the native bridge and extracts waveform
 * envelope, RMS, and peak data for visualization in the editor sidebar.
 * <p>
 * All methods are static and thread-safe (each call creates its own renderer).
 * Falls back gracefully when the native bridge is unavailable.
 */
public final class WaveformAnalyzer {

  /** Result of a waveform analysis pass. */
  public record Analysis(
      float[] envelope,
      float rms,
      float peak,
      boolean nativeAvailable
  ) {}

  private static final int SAMPLE_RATE = 44_100;
  private static final int RENDER_FRAMES = 4096;
  private static final int CHANNELS = 2;
  private static final int BYTES_PER_SAMPLE = 2;
  private static final int FRAME_BYTES = CHANNELS * BYTES_PER_SAMPLE;

  private WaveformAnalyzer() {}

  /**
   * Render a short buffer of the given synth settings and return an analysis.
   *
   * @param settings current synth configuration
   * @param envelopeBins number of bins for the waveform envelope (e.g. 128)
   * @return analysis with envelope, RMS, and peak; envelope is empty if native unavailable
   */
  public static Analysis analyze(SynthPreviewSettings settings, int envelopeBins) {
    if (settings == null || envelopeBins < 1) {
      return new Analysis(new float[0], 0f, 0f, false);
    }
    if (!AudioFxNativeBridge.isAvailable()) {
      return analyzeJavaFallback(settings, envelopeBins);
    }

    byte[] pcm = new byte[RENDER_FRAMES * FRAME_BYTES];
    int written;

    if (settings.type() == SynthPreviewSettings.SynthType.CHIPTUNE) {
      try (AudioFxNativeBridge.BeezRenderer renderer = AudioFxNativeBridge.createBeezRenderer(SAMPLE_RATE)) {
        renderer.configure(settings.cueId(), settings.intensity(), settings.volume(), settings.loop());
        written = renderer.render(pcm, RENDER_FRAMES);
      }
    } else {
      try (AudioFxNativeBridge.AmbienceRenderer renderer = AudioFxNativeBridge.createAmbienceRenderer(SAMPLE_RATE)) {
        renderer.configure(settings.preset(), settings.intensity(), settings.volume(), settings.toAmbienceProfile());
        written = renderer.render(pcm, RENDER_FRAMES);
      }
    }

    return extractAnalysis(pcm, written, envelopeBins, true);
  }

  /**
   * Extract envelope, RMS, and peak from raw 16-bit stereo LE PCM bytes.
   * Useful for analyzing a buffer captured from a live preview thread.
   */
  public static Analysis fromPcmBytes(byte[] pcm, int byteCount, int envelopeBins) {
    return extractAnalysis(pcm, byteCount, envelopeBins, AudioFxNativeBridge.isAvailable());
  }

  // --- Internal ---

  private static Analysis analyzeJavaFallback(SynthPreviewSettings settings, int envelopeBins) {
    if (settings.type() == SynthPreviewSettings.SynthType.CHIPTUNE) {
      return new Analysis(new float[Math.max(1, envelopeBins)], 0f, 0f, false);
    }
    FxAmbienceDsp.Preset preset = FxAmbienceDsp.Preset.fromToken(settings.preset());
    FxAmbienceDsp.State state = new FxAmbienceDsp.State(0x12345678L);
    double dt = 1.0 / SAMPLE_RATE;

    float[] samples = new float[RENDER_FRAMES];
    for (int i = 0; i < RENDER_FRAMES; i++) {
      samples[i] = (float) FxAmbienceDsp.synthSample(state, dt, preset, settings.intensity());
    }

    return extractFromSamples(samples, envelopeBins, false);
  }

  private static Analysis extractAnalysis(byte[] pcm, int byteCount, int envelopeBins, boolean nativeAvailable) {
    int totalFrames = Math.min(byteCount / FRAME_BYTES, RENDER_FRAMES);
    if (totalFrames <= 0) {
      return new Analysis(new float[Math.max(1, envelopeBins)], 0f, 0f, nativeAvailable);
    }

    // Decode left channel from 16-bit stereo LE
    float[] samples = new float[totalFrames];
    for (int i = 0; i < totalFrames; i++) {
      int offset = i * FRAME_BYTES;
      short left = (short) ((pcm[offset] & 0xFF) | (pcm[offset + 1] << 8));
      samples[i] = left / 32768f;
    }

    return extractFromSamples(samples, envelopeBins, nativeAvailable);
  }

  private static Analysis extractFromSamples(float[] samples, int envelopeBins, boolean nativeAvailable) {
    int bins = Math.max(1, envelopeBins);
    float[] envelope = new float[bins];
    float peak = 0f;
    double sumSq = 0.0;

    int framesPerBin = Math.max(1, samples.length / bins);

    for (int b = 0; b < bins; b++) {
      int start = b * framesPerBin;
      int end = Math.min(start + framesPerBin, samples.length);
      float binMax = 0f;
      for (int i = start; i < end; i++) {
        float abs = Math.abs(samples[i]);
        if (abs > binMax) binMax = abs;
        sumSq += (double) samples[i] * samples[i];
      }
      envelope[b] = binMax;
      if (binMax > peak) peak = binMax;
    }

    float rms = (float) Math.sqrt(sumSq / samples.length);
    return new Analysis(envelope, rms, peak, nativeAvailable);
  }
}
