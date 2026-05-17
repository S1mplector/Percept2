package com.jvn.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Stability regression tests for {@link FrameStats}.
 *
 * <p>Before the stability pass, {@link FrameStats#record(long)} accepted
 * negative deltas verbatim. Clock skew or a paused debugger could make the
 * host send e.g. {@code -500} to the engine, which ended up in the rolling
 * window and made {@link FrameStats#getMinMs()} return a bogus negative
 * value and — if the windowed average happened to land near zero —
 * {@link FrameStats#getFps()} return {@link Double#POSITIVE_INFINITY}.</p>
 */
public class FrameStatsTest {

  @Test
  public void recordFloorsNegativeDeltasAtZero() {
    FrameStats stats = new FrameStats(4);

    stats.record(-500);
    stats.record(-10);
    stats.record(0);
    stats.record(16);

    assertEquals(0.0, stats.getMinMs(), 1e-9,
        "negative deltas must be floored to 0 before entering the rolling window");
    assertEquals(16.0, stats.getMaxMs(), 1e-9);
    assertEquals(4.0, stats.getAvgMs(), 1e-9, "avg of {0,0,0,16} across 4 samples is 4");
  }

  @Test
  public void fpsIsFiniteAndNonNegativeForAllZeroWindow() {
    FrameStats stats = new FrameStats(3);
    stats.record(-1);
    stats.record(0);
    stats.record(0);

    double fps = stats.getFps();
    assertFalse(Double.isInfinite(fps), "fps must not be Infinity on an all-zero window");
    assertFalse(Double.isNaN(fps), "fps must not be NaN on an all-zero window");
    assertTrue(fps >= 0.0, "fps must never be negative; got " + fps);
  }

  @Test
  public void totalFramesIncrementsEvenForClampedNegativeDeltas() {
    FrameStats stats = new FrameStats();
    long before = stats.getTotalFrames();
    stats.record(-100);
    assertEquals(before + 1, stats.getTotalFrames(),
        "totalFrames must track physical invocations regardless of delta sign");
  }

  @Test
  public void rollingWindowDropsOutgoingMinAndMax() {
    FrameStats stats = new FrameStats(3);

    stats.record(5);
    stats.record(50);
    stats.record(20);
    assertEquals(5.0, stats.getMinMs(), 1e-9);
    assertEquals(50.0, stats.getMaxMs(), 1e-9);

    stats.record(30); // drops 5
    assertEquals(20.0, stats.getMinMs(), 1e-9);
    assertEquals(50.0, stats.getMaxMs(), 1e-9);
    assertEquals((50.0 + 20.0 + 30.0) / 3.0, stats.getAvgMs(), 1e-9);

    stats.record(10); // drops 50
    assertEquals(10.0, stats.getMinMs(), 1e-9);
    assertEquals(30.0, stats.getMaxMs(), 1e-9);
    assertEquals((20.0 + 30.0 + 10.0) / 3.0, stats.getAvgMs(), 1e-9);
  }
}
