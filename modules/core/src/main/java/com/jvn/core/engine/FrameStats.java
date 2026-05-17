package com.jvn.core.engine;

/**
 * Tracks per-frame timing statistics for diagnostics and profiling.
 * Updated automatically by the {@link Engine} each frame.
 *
 * <p>Statistics are computed over a rolling window (default 60 frames)
 * to provide stable readings without long-term drift.</p>
 */
public class FrameStats {
  private static final int DEFAULT_WINDOW = 60;

  private final long[] samples;
  private int head = 0;
  private int count = 0;
  private long totalFrames = 0;
  private long sumMs = 0;
  private long minSampleMs = 0;
  private long maxSampleMs = 0;

  // Cached per-window stats
  private double fps;
  private double avgMs;
  private double minMs;
  private double maxMs;

  public FrameStats() {
    this(DEFAULT_WINDOW);
  }

  public FrameStats(int windowSize) {
    this.samples = new long[Math.max(1, windowSize)];
  }

  /**
   * Record a frame's delta time. Called by the engine each frame.
   *
   * <p>{@code deltaMs} is defensively floored to {@code 0}: negative values
   * (from clock skew / NTP correction / monotonic-clock wrap) would otherwise
   * pollute {@link #getMinMs()} and make {@link #getFps()} return
   * {@link Double#POSITIVE_INFINITY} whenever the rolling average hit zero.</p>
   */
  public void record(long deltaMs) {
    if (deltaMs < 0) deltaMs = 0;
    long outgoing = samples[head];
    samples[head] = deltaMs;
    head = (head + 1) % samples.length;
    if (count < samples.length) {
      count++;
      sumMs += deltaMs;
      if (count == 1) {
        minSampleMs = deltaMs;
        maxSampleMs = deltaMs;
      } else {
        if (deltaMs < minSampleMs) minSampleMs = deltaMs;
        if (deltaMs > maxSampleMs) maxSampleMs = deltaMs;
      }
    } else {
      sumMs += deltaMs - outgoing;
      if (outgoing == minSampleMs || outgoing == maxSampleMs) {
        recomputeMinMax();
      } else {
        if (deltaMs < minSampleMs) minSampleMs = deltaMs;
        if (deltaMs > maxSampleMs) maxSampleMs = deltaMs;
      }
    }
    totalFrames++;
    recomputeDerived();
  }

  private void recomputeMinMax() {
    long lo = Long.MAX_VALUE;
    long hi = Long.MIN_VALUE;
    for (int i = 0; i < count; i++) {
      long s = samples[i];
      if (s < lo) lo = s;
      if (s > hi) hi = s;
    }
    minSampleMs = lo;
    maxSampleMs = hi;
  }

  private void recomputeDerived() {
    if (count == 0) return;
    avgMs = (double) sumMs / count;
    minMs = minSampleMs;
    maxMs = maxSampleMs;
    // avgMs is guaranteed >= 0 because every recorded sample is >= 0, but we
    // still guard against div-by-zero explicitly to avoid emitting
    // Double.POSITIVE_INFINITY on an all-zero window (e.g. headless tests).
    fps = avgMs > 0.0 ? 1000.0 / avgMs : 0.0;
  }

  /** Frames per second (averaged over the window). */
  public double getFps() { return fps; }

  /** Average frame time in ms (over the window). */
  public double getAvgMs() { return avgMs; }

  /** Minimum frame time in ms (over the window). */
  public double getMinMs() { return minMs; }

  /** Maximum frame time in ms (over the window). */
  public double getMaxMs() { return maxMs; }

  /** Total frames recorded since engine start. */
  public long getTotalFrames() { return totalFrames; }

  /** Number of samples currently in the window. */
  public int getSampleCount() { return count; }

  /** Reset all statistics. */
  public void reset() {
    head = 0;
    count = 0;
    totalFrames = 0;
    sumMs = 0;
    minSampleMs = 0;
    maxSampleMs = 0;
    fps = 0;
    avgMs = 0;
    minMs = 0;
    maxMs = 0;
  }
}
