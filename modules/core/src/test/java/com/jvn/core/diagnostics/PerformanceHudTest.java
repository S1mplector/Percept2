package com.jvn.core.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceHudTest {

  @Test
  void heapMbIsPositiveAfterTick() {
    PerformanceHud hud = new PerformanceHud();
    hud.tick(System.nanoTime());
    assertTrue(hud.getHeapMb() > 0, "Heap MB should be positive");
  }

  @Test
  void fpsIsZeroBeforeEnoughFrames() {
    PerformanceHud hud = new PerformanceHud();
    hud.tick(System.nanoTime());
    // Only one frame — fps window needs at least 2
    assertEquals(0.0, hud.getFps(), 0.001);
  }

  @Test
  void fpsIsPositiveAfterTwoFrames() {
    PerformanceHud hud = new PerformanceHud();
    long t = System.nanoTime();
    hud.tick(t);
    hud.tick(t + 16_666_667L); // ~60fps frame gap
    assertTrue(hud.getFps() > 0, "FPS should be positive after two frames");
  }

  @Test
  void imageCacheHitRateNaNWhenNoAccesses() {
    PerformanceHud hud = new PerformanceHud();
    assertTrue(Double.isNaN(hud.getImageCacheHitRate()));
  }

  @Test
  void imageCacheHitRateComputedCorrectly() {
    PerformanceHud hud = new PerformanceHud();
    hud.setImageCacheStats(75, 25);
    assertEquals(0.75, hud.getImageCacheHitRate(), 0.001);
  }

  @Test
  void activeTimelinesTracked() {
    PerformanceHud hud = new PerformanceHud();
    hud.setActiveTimelines(3);
    assertEquals(3, hud.getActiveTimelines());
  }

  @Test
  void drawCallStatsTracked() {
    PerformanceHud hud = new PerformanceHud();
    hud.setDrawCallStats(5, 2);
    assertEquals(5, hud.getCharacterLayerDrawCalls());
    assertEquals(2, hud.getOtherDrawCalls());
    assertEquals(7, hud.getTotalDrawCalls());
  }
}
