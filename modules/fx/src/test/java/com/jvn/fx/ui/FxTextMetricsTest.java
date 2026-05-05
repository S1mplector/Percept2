package com.jvn.fx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.text.Font;
import org.junit.jupiter.api.Test;

class FxTextMetricsTest {

  @Test
  void measuresTextAndFontMetricsConsistently() {
    FxTextMetrics metrics = new FxTextMetrics();
    Font font = Font.font("SansSerif", 18);

    double widthA = metrics.width("Menu Entry", font);
    double widthB = metrics.width("Menu Entry", font);

    assertEquals(widthA, widthB);
    assertTrue(metrics.ascent(font) > 0.0);
    assertTrue(metrics.height(font) > 0.0);

    metrics.clear();

    assertEquals(widthA, metrics.width("Menu Entry", font));
  }
}
