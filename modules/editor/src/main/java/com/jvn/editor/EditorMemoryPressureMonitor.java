package com.jvn.editor;

import java.util.Optional;

/** Stateful, throttled classifier for editor heap and garbage-collection pressure. */
final class EditorMemoryPressureMonitor {
  static final long MIB = 1024L * 1024L;
  static final long LOW_HEAP_BYTES = 768L * MIB;
  static final double HIGH_RATIO = 0.84;
  static final double CRITICAL_RATIO = 0.94;

  private static final long HIGH_SUSTAIN_NS = 15_000_000_000L;
  private static final long CRITICAL_SUSTAIN_NS = 3_000_000_000L;
  private static final long GC_WINDOW_NS = 10_000_000_000L;
  private static final long HIGH_COOLDOWN_NS = 20L * 60L * 1_000_000_000L;
  private static final long CRITICAL_COOLDOWN_NS = 5L * 60L * 1_000_000_000L;
  private static final long GC_COOLDOWN_NS = 15L * 60L * 1_000_000_000L;

  enum Event {
    LOW_MAX_HEAP,
    SUSTAINED_HIGH_HEAP,
    CRITICAL_HEAP,
    GC_THRASHING
  }

  record Snapshot(
      Event event,
      long heapUsedBytes,
      long heapMaxBytes,
      double heapRatio,
      long windowGcCount,
      long windowGcTimeMs
  ) {}

  private boolean lowHeapChecked;
  private long highSinceNs = -1L;
  private long criticalSinceNs = -1L;
  private long gcWindowStartNs = -1L;
  private long gcWindowCount;
  private long gcWindowTimeMs;
  private long lastHighAlertNs = Long.MIN_VALUE;
  private long lastCriticalAlertNs = Long.MIN_VALUE;
  private long lastGcAlertNs = Long.MIN_VALUE;

  Optional<Snapshot> sample(
      long nowNs,
      long heapUsedBytes,
      long heapMaxBytes,
      long gcCountDelta,
      long gcTimeDeltaMs
  ) {
    long safeMax = Math.max(1L, heapMaxBytes);
    long safeUsed = Math.max(0L, heapUsedBytes);
    double ratio = Math.max(0.0, Math.min(1.0, (double) safeUsed / safeMax));

    if (!lowHeapChecked) {
      lowHeapChecked = true;
      if (safeMax < LOW_HEAP_BYTES) {
        return Optional.of(snapshot(Event.LOW_MAX_HEAP, safeUsed, safeMax, ratio));
      }
    }

    if (ratio >= CRITICAL_RATIO) {
      if (criticalSinceNs < 0L) criticalSinceNs = nowNs;
    } else {
      criticalSinceNs = -1L;
    }
    if (ratio >= HIGH_RATIO) {
      if (highSinceNs < 0L) highSinceNs = nowNs;
    } else {
      highSinceNs = -1L;
    }

    accumulateGcWindow(nowNs, gcCountDelta, gcTimeDeltaMs);

    if (criticalSinceNs >= 0L
        && nowNs - criticalSinceNs >= CRITICAL_SUSTAIN_NS
        && cooldownElapsed(nowNs, lastCriticalAlertNs, CRITICAL_COOLDOWN_NS)) {
      lastCriticalAlertNs = nowNs;
      lastHighAlertNs = nowNs;
      return Optional.of(snapshot(Event.CRITICAL_HEAP, safeUsed, safeMax, ratio));
    }

    if (gcWindowStartNs >= 0L && nowNs - gcWindowStartNs >= GC_WINDOW_NS) {
      long windowCount = gcWindowCount;
      long windowTime = gcWindowTimeMs;
      resetGcWindow(nowNs);
      if (ratio >= 0.78
          && windowCount >= 4L
          && windowTime >= 2_000L
          && cooldownElapsed(nowNs, lastGcAlertNs, GC_COOLDOWN_NS)) {
        lastGcAlertNs = nowNs;
        lastHighAlertNs = nowNs;
        return Optional.of(new Snapshot(
            Event.GC_THRASHING, safeUsed, safeMax, ratio, windowCount, windowTime));
      }
    }

    if (highSinceNs >= 0L
        && nowNs - highSinceNs >= HIGH_SUSTAIN_NS
        && cooldownElapsed(nowNs, lastHighAlertNs, HIGH_COOLDOWN_NS)) {
      lastHighAlertNs = nowNs;
      return Optional.of(snapshot(Event.SUSTAINED_HIGH_HEAP, safeUsed, safeMax, ratio));
    }
    return Optional.empty();
  }

  private void accumulateGcWindow(long nowNs, long countDelta, long timeDeltaMs) {
    if (gcWindowStartNs < 0L) gcWindowStartNs = nowNs;
    gcWindowCount = saturatedAdd(gcWindowCount, Math.max(0L, countDelta));
    gcWindowTimeMs = saturatedAdd(gcWindowTimeMs, Math.max(0L, timeDeltaMs));
  }

  private void resetGcWindow(long nowNs) {
    gcWindowStartNs = nowNs;
    gcWindowCount = 0L;
    gcWindowTimeMs = 0L;
  }

  private Snapshot snapshot(Event event, long used, long max, double ratio) {
    return new Snapshot(event, used, max, ratio, gcWindowCount, gcWindowTimeMs);
  }

  private static boolean cooldownElapsed(long nowNs, long lastNs, long cooldownNs) {
    return lastNs == Long.MIN_VALUE || nowNs - lastNs >= cooldownNs;
  }

  private static long saturatedAdd(long left, long right) {
    if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
    return left + right;
  }
}
