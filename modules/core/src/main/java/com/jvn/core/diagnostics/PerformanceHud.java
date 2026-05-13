package com.jvn.core.diagnostics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Collects lightweight performance metrics for the F3 HUD overlay.
 *
 * <p>Call {@link #tick(long)} each frame with the current nanosecond timestamp.
 * Read {@link #getFps()}, {@link #getHeapMb()}, etc. to render the overlay.</p>
 */
public final class PerformanceHud {

  private static final int FPS_WINDOW = 60;
  private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();

  private final long[] frameNanos = new long[FPS_WINDOW];
  private int frameHead = 0;
  private int frameCount = 0;

  private double fps = 0.0;
  private long heapMb = 0;
  private long imageCacheHits = 0;
  private long imageCacheMisses = 0;
  private int activeTimelines = 0;

  /** Update metrics with the current frame timestamp (nanoseconds from {@code System.nanoTime()}). */
  public void tick(long nowNanos) {
    frameNanos[frameHead] = nowNanos;
    frameHead = (frameHead + 1) % FPS_WINDOW;
    if (frameCount < FPS_WINDOW) frameCount++;

    if (frameCount >= 2) {
      int tail = (frameHead - frameCount + FPS_WINDOW) % FPS_WINDOW;
      long elapsed = nowNanos - frameNanos[tail];
      fps = elapsed > 0 ? (frameCount - 1) * 1_000_000_000.0 / elapsed : 0.0;
    }

    heapMb = MEMORY.getHeapMemoryUsage().getUsed() / (1024 * 1024);
  }

  public void setImageCacheStats(long hits, long misses) {
    this.imageCacheHits = hits;
    this.imageCacheMisses = misses;
  }

  public void setActiveTimelines(int count) {
    this.activeTimelines = count;
  }

  public double getFps() { return fps; }
  public long getHeapMb() { return heapMb; }
  public long getImageCacheHits() { return imageCacheHits; }
  public long getImageCacheMisses() { return imageCacheMisses; }
  public int getActiveTimelines() { return activeTimelines; }

  /** Hit rate in [0,1], or NaN if no accesses yet. */
  public double getImageCacheHitRate() {
    long total = imageCacheHits + imageCacheMisses;
    return total == 0 ? Double.NaN : (double) imageCacheHits / total;
  }
}
