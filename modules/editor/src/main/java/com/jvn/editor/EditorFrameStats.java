package com.jvn.editor;

import java.util.Arrays;

/** Rolling JavaFX frame-time statistics used by the editor performance HUD. */
final class EditorFrameStats {
  private final long[] frameNanos;
  private int next;
  private int size;

  EditorFrameStats(int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
    frameNanos = new long[capacity];
  }

  void record(long elapsedNanos) {
    if (elapsedNanos <= 0) return;
    frameNanos[next] = elapsedNanos;
    next = (next + 1) % frameNanos.length;
    if (size < frameNanos.length) size++;
  }

  Snapshot snapshot(double targetFps) {
    if (size == 0) return new Snapshot(0.0, 0.0, 0.0, 0, 0);
    long[] sorted = new long[size];
    long total = 0L;
    long max = 0L;
    for (int i = 0; i < size; i++) {
      long value = frameNanos[i];
      sorted[i] = value;
      total += value;
      max = Math.max(max, value);
    }
    Arrays.sort(sorted);
    int p95Index = Math.max(0, (int) Math.ceil(size * 0.95) - 1);
    long p95 = sorted[p95Index];
    double resolvedTargetFps = Double.isFinite(targetFps) && targetFps > 0.0 ? targetFps : 60.0;
    long stallThreshold = Math.max(25_000_000L, Math.round(1_500_000_000.0 / resolvedTargetFps));
    int stalls = 0;
    for (long value : sorted) {
      if (value > stallThreshold) stalls++;
    }
    double averageNanos = total / (double) size;
    return new Snapshot(
        averageNanos > 0.0 ? 1_000_000_000.0 / averageNanos : 0.0,
        p95 / 1_000_000.0,
        max / 1_000_000.0,
        stalls,
        size);
  }

  record Snapshot(double averageFps, double p95Millis, double maxMillis, int stalls, int samples) {}
}
