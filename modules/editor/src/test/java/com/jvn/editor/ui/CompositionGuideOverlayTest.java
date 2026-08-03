package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CompositionGuideOverlayTest {
  @Test
  void layoutSizesDrawingCanvasToDetachedPreviewBounds() {
    CompositionGuideOverlay overlay = new CompositionGuideOverlay();
    overlay.resize(1280.0, 720.0);
    overlay.layout();

    assertEquals(1280.0, overlay.renderedWidth());
    assertEquals(720.0, overlay.renderedHeight());
  }

  @Test
  void goldenGridUsesExactReciprocalPhiSquaredIntersections() {
    double[] fractions = CompositionGuideOverlay.goldenGridFractions();

    assertEquals(1.0 / (CompositionGuideOverlay.PHI * CompositionGuideOverlay.PHI), fractions[0], 1e-15);
    assertEquals(1.0, fractions[0] + fractions[1], 1e-15);
    assertEquals(CompositionGuideOverlay.PHI, fractions[1] / fractions[0], 1e-15);
  }
}
