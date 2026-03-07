package com.jvn.audiofx;

/**
 * Renders PCM from the native bridge and extracts waveform envelope, RMS,
 * and peak data for visualization in the editor sidebar.
 * <p>
 * Provides both one-shot {@link #analyze} and continuous {@link StreamingAnalyzer}
 * modes.
 */
public final class WaveformAnalyzer {

  /** Result of a waveform analysis pass. */
  public record Analysis(
      float[] envelope,
      float[] spectrum,
      float rms,
      float peak,
      boolean nativeAvailable
  ) {}

  /** Empty/zero analysis sentinel. */
  public static final Analysis EMPTY = new Analysis(new float[0], new float[0], 0f, 0f, false);

  private static final int FFT_SIZE = 1024;
  private static final int SPECTRUM_BANDS = 64;

  private static final int SAMPLE_RATE = 44_100;
  private static final int RENDER_FRAMES = 4096;
  private static final int CHANNELS = 2;
  private static final int BYTES_PER_SAMPLE = 2;
  private static final int FRAME_BYTES = CHANNELS * BYTES_PER_SAMPLE;

  private WaveformAnalyzer() {}

  // =========================================================================
  // One-shot analysis (snapshot)
  // =========================================================================

  /**
   * Render a short buffer of the given synth settings and return an analysis.
   *
   * @param settings current synth configuration
   * @param envelopeBins number of bins for the waveform envelope (e.g. 128)
   * @return analysis with envelope, RMS, and peak data from the native renderer
   */
  public static Analysis analyze(SynthPreviewSettings settings, int envelopeBins) {
    if (settings == null || envelopeBins < 1) {
      return new Analysis(new float[0], new float[0], 0f, 0f, false);
    }
    requireNativeBridge();

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

  // =========================================================================
  // Real-time streaming analyzer
  // =========================================================================

  /**
   * Continuously renders PCM from a dedicated native renderer and maintains a
   * rolling waveform analysis that updates at
   * approximately real-time rate.
   * <p>
   * Thread-safe: {@link #start}, {@link #reconfigure}, and {@link #stop}
   * may be called from any thread (typically the FX application thread).
   * {@link #latest()} is lock-free and returns the most recent analysis.
   */
  public static final class StreamingAnalyzer {
    private static final int CHUNK_FRAMES = 1024;
    private static final int ROLLING_FRAMES = 4096;
    private static final int DEFAULT_BINS = 128;
    private static final long CHUNK_SLEEP_MS =
        (long) Math.ceil(CHUNK_FRAMES * 1000.0 / SAMPLE_RATE);

    private final Object lock = new Object();
    private volatile boolean running;
    private volatile Analysis latestAnalysis = EMPTY;
    private Thread renderThread;
    private SynthPreviewSettings pendingSettings;
    private long configGeneration;

    /** Start (or restart) streaming with the given settings. */
    public void start(SynthPreviewSettings settings) {
      synchronized (lock) {
        stopInternal();
        pendingSettings = settings != null ? settings.copy() : null;
        configGeneration++;
        if (pendingSettings == null) return;
        requireNativeBridge();
        running = true;
        renderThread = new Thread(this::renderLoop, "synth-waveform-stream");
        renderThread.setDaemon(true);
        renderThread.start();
      }
    }

    /** Update settings while streaming. Takes effect on the next render cycle. */
    public void reconfigure(SynthPreviewSettings settings) {
      synchronized (lock) {
        pendingSettings = settings != null ? settings.copy() : null;
        configGeneration++;
      }
    }

    /** Stop streaming and release resources. */
    public void stop() {
      synchronized (lock) {
        stopInternal();
      }
    }

    /** Latest analysis result (never null, lock-free read). */
    public Analysis latest() { return latestAnalysis; }

    /** Whether the render thread is active. */
    public boolean isRunning() { return running; }

    private void stopInternal() {
      running = false;
      if (renderThread != null) {
        renderThread.interrupt();
        try { renderThread.join(400); } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        }
        renderThread = null;
      }
    }

    private void renderLoop() {
      try {
        while (running && !Thread.currentThread().isInterrupted()) {
          SynthPreviewSettings snap;
          long gen;
          synchronized (lock) {
            snap = pendingSettings;
            gen = configGeneration;
          }
          if (snap == null) break;
          runSession(snap.copy(), gen);
        }
      } finally {
        running = false;
      }
    }

    private void runSession(SynthPreviewSettings settings, long sessionGen) {
      requireNativeBridge();
      float[] rolling = new float[ROLLING_FRAMES];
      byte[] pcmBuf = new byte[CHUNK_FRAMES * FRAME_BYTES];

      if (settings.type() == SynthPreviewSettings.SynthType.CHIPTUNE) {
        try (AudioFxNativeBridge.BeezRenderer r =
                 AudioFxNativeBridge.createBeezRenderer(SAMPLE_RATE)) {
          r.configure(settings.cueId(), settings.intensity(),
              settings.volume(), settings.loop());
          chunkLoop(pcmBuf, rolling, sessionGen, true,
              (buf, frames) -> r.render(buf, frames));
        }
      } else {
        try (AudioFxNativeBridge.AmbienceRenderer r =
                 AudioFxNativeBridge.createAmbienceRenderer(SAMPLE_RATE)) {
          r.configure(settings.preset(), settings.intensity(),
              settings.volume(), settings.toAmbienceProfile());
          chunkLoop(pcmBuf, rolling, sessionGen, true,
              (buf, frames) -> r.render(buf, frames));
        }
      }
    }

    @FunctionalInterface
    private interface PcmSource {
      int render(byte[] pcm, int frames);
    }

    private void chunkLoop(byte[] pcmBuf, float[] rolling,
                           long sessionGen, boolean nativeAvail,
                           PcmSource source) {
      int totalWritten = 0;
      while (running && !Thread.currentThread().isInterrupted()) {
        synchronized (lock) {
          if (configGeneration != sessionGen) return;
        }

        int bytesWritten = source.render(pcmBuf, CHUNK_FRAMES);
        int framesDecoded = Math.min(
            bytesWritten / FRAME_BYTES, CHUNK_FRAMES);

        for (int i = 0; i < framesDecoded; i++) {
          int offset = i * FRAME_BYTES;
          short left = (short) ((pcmBuf[offset] & 0xFF)
              | (pcmBuf[offset + 1] << 8));
          rolling[totalWritten % ROLLING_FRAMES] = left / 32768f;
          totalWritten++;
        }

        int available = Math.min(totalWritten, ROLLING_FRAMES);
        int startIdx = totalWritten > ROLLING_FRAMES
            ? totalWritten % ROLLING_FRAMES : 0;
        latestAnalysis = computeRollingAnalysis(
            rolling, startIdx, available, DEFAULT_BINS, nativeAvail);

        try {
          Thread.sleep(CHUNK_SLEEP_MS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    private static Analysis computeRollingAnalysis(
        float[] rolling, int startIdx, int available,
        int bins, boolean nativeAvail) {
      if (available <= 0) return EMPTY;
      int effectiveBins = Math.max(1, bins);
      float[] envelope = new float[effectiveBins];
      float peak = 0f;
      double sumSq = 0.0;
      int framesPerBin = Math.max(1, available / effectiveBins);

      for (int b = 0; b < effectiveBins; b++) {
        int binStart = b * framesPerBin;
        int binEnd = Math.min(binStart + framesPerBin, available);
        float binMax = 0f;
        for (int i = binStart; i < binEnd; i++) {
          int idx = (startIdx + i) % rolling.length;
          float v = rolling[idx];
          float abs = Math.abs(v);
          if (abs > binMax) binMax = abs;
          sumSq += (double) v * v;
        }
        envelope[b] = binMax;
        if (binMax > peak) peak = binMax;
      }

      // Compute spectrum from the most recent samples
      float[] fftSamples = new float[FFT_SIZE];
      int fftStart = available > FFT_SIZE ? (startIdx + available - FFT_SIZE) % rolling.length : startIdx;
      int fftLen = Math.min(available, FFT_SIZE);
      for (int i = 0; i < fftLen; i++) {
        fftSamples[i] = rolling[(fftStart + i) % rolling.length];
      }
      float[] spectrum = computeSpectrum(fftSamples, fftLen, SPECTRUM_BANDS);

      float rms = (float) Math.sqrt(sumSq / available);
      return new Analysis(envelope, spectrum, rms, peak, nativeAvail);
    }
  }

  // =========================================================================
  // Internal helpers (one-shot)
  // =========================================================================

  private static Analysis extractAnalysis(byte[] pcm, int byteCount, int envelopeBins, boolean nativeAvailable) {
    int totalFrames = Math.min(byteCount / FRAME_BYTES, RENDER_FRAMES);
    if (totalFrames <= 0) {
      return new Analysis(new float[Math.max(1, envelopeBins)], new float[SPECTRUM_BANDS], 0f, 0f, nativeAvailable);
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

    // Compute spectrum via FFT
    float[] spectrum = computeSpectrum(samples, samples.length, SPECTRUM_BANDS);

    float rms = (float) Math.sqrt(sumSq / samples.length);
    return new Analysis(envelope, spectrum, rms, peak, nativeAvailable);
  }

  // =========================================================================
  // FFT and spectrum computation
  // =========================================================================

  /**
   * Compute a log-scaled magnitude spectrum from time-domain samples.
   * Uses a radix-2 Cooley-Tukey FFT with Hanning window.
   *
   * @param samples raw PCM samples (mono, normalized -1..1)
   * @param count   number of valid samples in the array
   * @param bands   number of output spectrum bands (log-frequency grouped)
   * @return float array of magnitude values in dB (-60..0 range, clamped)
   */
  private static float[] computeSpectrum(float[] samples, int count, int bands) {
    int n = FFT_SIZE;
    // Zero-pad or truncate to FFT_SIZE
    double[] re = new double[n];
    double[] im = new double[n];
    int len = Math.min(count, n);
    // Apply Hanning window
    for (int i = 0; i < len; i++) {
      double window = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (len - 1)));
      re[i] = samples[i] * window;
    }

    // In-place radix-2 FFT (iterative Cooley-Tukey)
    // Bit-reversal permutation
    int bits = Integer.numberOfTrailingZeros(n);
    for (int i = 0; i < n; i++) {
      int j = Integer.reverse(i) >>> (32 - bits);
      if (j > i) {
        double tmp = re[i]; re[i] = re[j]; re[j] = tmp;
        tmp = im[i]; im[i] = im[j]; im[j] = tmp;
      }
    }
    // FFT butterfly
    for (int size = 2; size <= n; size *= 2) {
      int halfSize = size / 2;
      double angle = -2.0 * Math.PI / size;
      double wRe = Math.cos(angle);
      double wIm = Math.sin(angle);
      for (int i = 0; i < n; i += size) {
        double curRe = 1.0, curIm = 0.0;
        for (int j = 0; j < halfSize; j++) {
          int a = i + j;
          int b = a + halfSize;
          double tRe = curRe * re[b] - curIm * im[b];
          double tIm = curRe * im[b] + curIm * re[b];
          re[b] = re[a] - tRe;
          im[b] = im[a] - tIm;
          re[a] += tRe;
          im[a] += tIm;
          double nextRe = curRe * wRe - curIm * wIm;
          curIm = curRe * wIm + curIm * wRe;
          curRe = nextRe;
        }
      }
    }

    // Compute magnitudes for positive frequencies
    int halfN = n / 2;
    double[] magnitudes = new double[halfN];
    for (int i = 0; i < halfN; i++) {
      magnitudes[i] = Math.sqrt(re[i] * re[i] + im[i] * im[i]) / halfN;
    }

    // Group into log-frequency bands
    float[] spectrum = new float[bands];
    // Map FFT bins to bands using log scale
    // Skip DC (bin 0), use bins 1..halfN-1
    double logMin = Math.log(1);
    double logMax = Math.log(halfN);
    for (int b = 0; b < bands; b++) {
      double bandLogStart = logMin + (logMax - logMin) * b / bands;
      double bandLogEnd = logMin + (logMax - logMin) * (b + 1) / bands;
      int binStart = Math.max(1, (int) Math.round(Math.exp(bandLogStart)));
      int binEnd = Math.min(halfN - 1, (int) Math.round(Math.exp(bandLogEnd)));
      if (binEnd < binStart) binEnd = binStart;

      double maxMag = 0;
      for (int i = binStart; i <= binEnd; i++) {
        if (magnitudes[i] > maxMag) maxMag = magnitudes[i];
      }

      // Convert to dB, clamp to -60..0
      double db = maxMag > 1e-10 ? 20.0 * Math.log10(maxMag) : -60.0;
      spectrum[b] = (float) Math.max(-60.0, Math.min(0.0, db));
    }

    return spectrum;
  }

  private static void requireNativeBridge() {
    if (!AudioFxNativeBridge.isAvailable()) {
      throw new IllegalStateException("AudioFX native bridge unavailable: " + AudioFxNativeBridge.diagnostics());
    }
  }
}
