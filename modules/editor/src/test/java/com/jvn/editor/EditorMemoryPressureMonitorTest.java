package com.jvn.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class EditorMemoryPressureMonitorTest {

  @Test
  void warnsOnceWhenConfiguredHeapIsTooSmall() {
    EditorMemoryPressureMonitor monitor = new EditorMemoryPressureMonitor();

    assertEquals(EditorMemoryPressureMonitor.Event.LOW_MAX_HEAP,
        monitor.sample(0L, 100L, 512L * EditorMemoryPressureMonitor.MIB, 0L, 0L)
            .orElseThrow().event());
    assertTrue(monitor.sample(1_000_000_000L, 100L, 512L * EditorMemoryPressureMonitor.MIB, 0L, 0L)
        .isEmpty());
  }

  @Test
  void ignoresShortAllocationSpikeButReportsSustainedHighHeap() {
    EditorMemoryPressureMonitor monitor = new EditorMemoryPressureMonitor();
    long max = 1024L * EditorMemoryPressureMonitor.MIB;
    monitor.sample(0L, max / 2L, max, 0L, 0L);
    assertTrue(monitor.sample(1_000_000_000L, (long) (max * 0.86), max, 0L, 0L).isEmpty());
    assertTrue(monitor.sample(5_000_000_000L, max / 2L, max, 1L, 30L).isEmpty());

    monitor.sample(10_000_000_000L, (long) (max * 0.86), max, 0L, 0L);
    Optional<EditorMemoryPressureMonitor.Snapshot> event =
        monitor.sample(25_000_000_000L, (long) (max * 0.87), max, 0L, 0L);

    assertEquals(EditorMemoryPressureMonitor.Event.SUSTAINED_HIGH_HEAP, event.orElseThrow().event());
  }

  @Test
  void criticalPressureWinsAndIsThrottled() {
    EditorMemoryPressureMonitor monitor = new EditorMemoryPressureMonitor();
    long max = 1024L * EditorMemoryPressureMonitor.MIB;
    monitor.sample(0L, max / 2L, max, 0L, 0L);
    monitor.sample(1_000_000_000L, (long) (max * 0.96), max, 0L, 0L);

    assertEquals(EditorMemoryPressureMonitor.Event.CRITICAL_HEAP,
        monitor.sample(4_000_000_000L, (long) (max * 0.97), max, 0L, 0L)
            .orElseThrow().event());
    assertTrue(monitor.sample(8_000_000_000L, (long) (max * 0.98), max, 0L, 0L).isEmpty());
  }

  @Test
  void reportsGcThrashingOnlyWhenGcTimeAndRetainedHeapAreBothHigh() {
    EditorMemoryPressureMonitor monitor = new EditorMemoryPressureMonitor();
    long max = 2048L * EditorMemoryPressureMonitor.MIB;
    monitor.sample(0L, max / 2L, max, 0L, 0L);
    for (int second = 1; second <= 9; second++) {
      assertTrue(monitor.sample(
          second * 1_000_000_000L,
          (long) (max * 0.80),
          max,
          1L,
          250L).isEmpty());
    }

    assertEquals(EditorMemoryPressureMonitor.Event.GC_THRASHING,
        monitor.sample(10_000_000_000L, (long) (max * 0.81), max, 1L, 250L)
            .orElseThrow().event());
  }
}
