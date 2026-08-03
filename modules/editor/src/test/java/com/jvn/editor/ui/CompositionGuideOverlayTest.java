package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  void fittedFrameMatchesVirtualAspectAndCentersLetterboxing() {
    double[] frame = CompositionGuideOverlay.fittedFrame(1000.0, 800.0, 1920.0, 1080.0);

    assertEquals(0.0, frame[0], 1e-12);
    assertEquals(118.75, frame[1], 1e-12);
    assertEquals(1000.0, frame[2], 1e-12);
    assertEquals(562.5, frame[3], 1e-12);
    assertEquals(1920.0 / 1080.0, frame[2] / frame[3], 1e-12);
  }

  @Test
  void resizingRecomputesTheSharedFrameUsedByEveryGuide() {
    CompositionGuideOverlay overlay = new CompositionGuideOverlay();
    overlay.setVirtualResolution(1920.0, 1080.0);

    overlay.resize(1600.0, 900.0);
    overlay.layout();
    assertEquals(0.0, overlay.renderedFrame()[0], 1e-12);
    assertEquals(0.0, overlay.renderedFrame()[1], 1e-12);
    assertEquals(1600.0, overlay.renderedFrame()[2], 1e-12);
    assertEquals(900.0, overlay.renderedFrame()[3], 1e-12);

    overlay.resize(900.0, 900.0);
    overlay.layout();
    assertEquals(0.0, overlay.renderedFrame()[0], 1e-12);
    assertEquals(196.875, overlay.renderedFrame()[1], 1e-12);
    assertEquals(900.0, overlay.renderedFrame()[2], 1e-12);
    assertEquals(506.25, overlay.renderedFrame()[3], 1e-12);
    assertEquals(900.0, overlay.renderedWidth(), 1e-12);
    assertEquals(900.0, overlay.renderedHeight(), 1e-12);
  }

  @Test
  void goldenGridUsesExactReciprocalPhiSquaredIntersections() {
    double[] fractions = CompositionGuideOverlay.goldenGridFractions();

    assertEquals(1.0 / (CompositionGuideOverlay.PHI * CompositionGuideOverlay.PHI), fractions[0], 1e-15);
    assertEquals(1.0, fractions[0] + fractions[1], 1e-15);
    assertEquals(CompositionGuideOverlay.PHI, fractions[1] / fractions[0], 1e-15);
  }

  @Test
  void goldenSpiralPoleCoincidesWithGoldenGridIntersection() {
    double[] fractions = CompositionGuideOverlay.goldenGridFractions();
    double[] pole = CompositionGuideOverlay.goldenSpiralPole(1920.0, 1080.0);

    assertEquals(1920.0 * fractions[1], pole[0], 1e-12);
    assertEquals(1080.0 * fractions[0], pole[1], 1e-12);
  }

  @Test
  void everyGoldenSpiralTurnRemainsInsideVirtualFrame() {
    double width = 1920.0;
    double height = 1080.0;

    for (double[] point : CompositionGuideOverlay.goldenSpiralPoints(width, height)) {
      assertTrue(point[0] >= 0.0 && point[0] <= width, "spiral x must remain in frame");
      assertTrue(point[1] >= 0.0 && point[1] <= height, "spiral y must remain in frame");
    }
  }
}
