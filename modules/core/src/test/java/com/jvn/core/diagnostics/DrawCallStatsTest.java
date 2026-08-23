package com.jvn.core.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DrawCallStatsTest {

  @Test
  void startsAtZero() {
    DrawCallStats stats = new DrawCallStats();
    assertEquals(0, stats.getCharacterLayerDraws());
    assertEquals(0, stats.getOtherDraws());
    assertEquals(0, stats.getTotalDraws());
    assertEquals(0, stats.getStageLightingRecomposites());
  }

  @Test
  void stageLightingRecompositesTrackedAndReset() {
    DrawCallStats stats = new DrawCallStats();
    stats.incrementStageLightingRecomposite();
    stats.incrementStageLightingRecomposite();
    assertEquals(2, stats.getStageLightingRecomposites());
    stats.reset();
    assertEquals(0, stats.getStageLightingRecomposites());
  }

  @Test
  void countsIncrementsSeparately() {
    DrawCallStats stats = new DrawCallStats();
    stats.incrementCharacterLayer();
    stats.incrementCharacterLayer();
    stats.incrementOther();
    assertEquals(2, stats.getCharacterLayerDraws());
    assertEquals(1, stats.getOtherDraws());
    assertEquals(3, stats.getTotalDraws());
  }

  @Test
  void resetClearsCounts() {
    DrawCallStats stats = new DrawCallStats();
    stats.incrementCharacterLayer();
    stats.incrementOther();
    stats.reset();
    assertEquals(0, stats.getCharacterLayerDraws());
    assertEquals(0, stats.getOtherDraws());
    assertEquals(0, stats.getTotalDraws());
  }
}
