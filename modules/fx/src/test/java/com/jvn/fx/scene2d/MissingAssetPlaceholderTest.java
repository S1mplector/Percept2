package com.jvn.fx.scene2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MissingAssetPlaceholderTest {

  @Test
  void doesNotDrawWhenDeveloperModeDisabled() {
    assertFalse(MissingAssetPlaceholder.shouldDraw(false));
  }

  @Test
  void drawsWhenDeveloperModeEnabled() {
    assertTrue(MissingAssetPlaceholder.shouldDraw(true));
  }

  @Test
  void labelPrefersContextOverBarePath() {
    assertEquals(
        "layer:eyes (art/hero_eyes.png)",
        MissingAssetPlaceholder.labelFor("art/hero_eyes.png", "layer:eyes"));
  }

  @Test
  void labelFallsBackToPathAloneWhenContextMissing() {
    assertEquals("art/hero_eyes.png", MissingAssetPlaceholder.labelFor("art/hero_eyes.png", null));
    assertEquals("art/hero_eyes.png", MissingAssetPlaceholder.labelFor("art/hero_eyes.png", "  "));
  }
}
