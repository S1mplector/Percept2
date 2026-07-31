package com.jvn.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EditorFrameStatsTest {
  @Test
  void reportsAveragePercentileWorstFrameAndStalls() {
    EditorFrameStats stats = new EditorFrameStats(5);
    stats.record(16_000_000L);
    stats.record(17_000_000L);
    stats.record(18_000_000L);
    stats.record(30_000_000L);
    stats.record(50_000_000L);

    EditorFrameStats.Snapshot snapshot = stats.snapshot(60.0);

    assertEquals(38.17, snapshot.averageFps(), 0.01);
    assertEquals(50.0, snapshot.p95Millis(), 0.01);
    assertEquals(50.0, snapshot.maxMillis(), 0.01);
    assertEquals(2, snapshot.stalls());
    assertEquals(5, snapshot.samples());
  }

  @Test
  void keepsOnlyTheNewestSamplesAndIgnoresInvalidDurations() {
    EditorFrameStats stats = new EditorFrameStats(2);
    stats.record(0);
    stats.record(10_000_000L);
    stats.record(20_000_000L);
    stats.record(40_000_000L);

    EditorFrameStats.Snapshot snapshot = stats.snapshot(60.0);

    assertEquals(33.33, snapshot.averageFps(), 0.01);
    assertEquals(40.0, snapshot.maxMillis(), 0.01);
    assertEquals(1, snapshot.stalls());
    assertEquals(2, snapshot.samples());
  }

  @Test
  void rejectsAnEmptyWindow() {
    assertThrows(IllegalArgumentException.class, () -> new EditorFrameStats(0));
  }
}
