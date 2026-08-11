package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HudToastLayoutTest {
  private static final java.util.function.ToDoubleFunction<String> MONOSPACE = text -> text.length() * 8.0;

  @Test
  void sizesShortToastAroundItsText() {
    HudToastLayout.Layout layout = HudToastLayout.compute("Saved", 1280, 22, MONOSPACE);

    assertEquals(1, layout.lines().size());
    assertEquals(140.0, layout.width(), 0.001);
    assertEquals(42.0, layout.height(), 0.001);
  }

  @Test
  void wrapsLongTimelineWarningAndGrowsBoxHeight() {
    String warning = "Timeline warning: Animation finishes within one 60 Hz display frame and may appear instant";
    HudToastLayout.Layout layout = HudToastLayout.compute(warning, 640, 22, MONOSPACE);

    assertTrue(layout.lines().size() >= 2);
    assertEquals(20.0 + layout.lines().size() * 22.0, layout.height(), 0.001);
    assertTrue(layout.width() <= 640 * 0.72);
    double contentWidth = layout.width() - HudToastLayout.HORIZONTAL_PADDING * 2.0;
    assertTrue(layout.lines().stream().allMatch(line -> MONOSPACE.applyAsDouble(line) <= contentWidth));
  }

  @Test
  void breaksAnUnspacedTokenInsteadOfOverflowingViewport() {
    HudToastLayout.Layout layout = HudToastLayout.compute("x".repeat(200), 240, 20, MONOSPACE);

    assertTrue(layout.lines().size() > 1);
    assertTrue(layout.width() <= 240 - 32);
    double contentWidth = layout.width() - HudToastLayout.HORIZONTAL_PADDING * 2.0;
    assertTrue(layout.lines().stream().allMatch(line -> MONOSPACE.applyAsDouble(line) <= contentWidth));
  }
}
