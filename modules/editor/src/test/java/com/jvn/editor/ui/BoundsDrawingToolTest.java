package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BoundsDrawingToolTest {
  @Test
  void clampBoundOriginKeepsExtentInsideCanvas() {
    assertEquals(0.75, BoundsDrawingTool.clampBoundOrigin(0.95, 0.25), 0.0001);
    assertEquals(0.0, BoundsDrawingTool.clampBoundOrigin(-0.25, 0.25), 0.0001);
  }

  @Test
  void clampBoundSizeRejectsInvalidAndOversizedExtents() {
    assertEquals(0.01, BoundsDrawingTool.clampBoundSize(Double.NaN), 0.0001);
    assertEquals(0.01, BoundsDrawingTool.clampBoundSize(0.001), 0.0001);
    assertEquals(1.0, BoundsDrawingTool.clampBoundSize(2.0), 0.0001);
  }
}
