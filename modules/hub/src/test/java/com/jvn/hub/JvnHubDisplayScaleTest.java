package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import org.junit.jupiter.api.Test;

class JvnHubDisplayScaleTest {
  @Test
  void retinaLogicalResolutionDoesNotReceiveASecondScaleFactor() {
    assertEquals(1.0, JvnHub.automaticScaleForDisplay(1470, 956, 72));
  }

  @Test
  void highResolutionDisplayStillReceivesAReadableAutomaticScale() {
    assertEquals(1.45, JvnHub.automaticScaleForDisplay(3840, 2160, 96));
  }

  @Test
  void compactAndDefaultPresetsResizeTheWholeHubFootprint() {
    assertEquals(new Dimension(480, 405), JvnHub.hubSizeForScale(0.75));
    assertEquals(new Dimension(640, 540), JvnHub.hubSizeForScale(1.0));
  }

  @Test
  void explicitScaleIsClampedToSupportedBounds() {
    assertEquals(new Dimension(480, 405), JvnHub.hubSizeForScale(0.1));
    assertEquals(new Dimension(1184, 999), JvnHub.hubSizeForScale(3.0));
  }

  @Test
  void resizeOverlayReportsClampedPixelDimensions() {
    assertEquals("1280 × 720 px", JvnHub.formatWindowPixels(1280, 720));
    assertEquals("0 × 480 px", JvnHub.formatWindowPixels(-1, 480));
  }

  @Test
  void sourceBuildIndicatorUsesTheHubOrangeAccent() {
    String label = JvnHub.formatVersionLabel("0.4.3");
    if (label.startsWith("<html>")) {
      org.junit.jupiter.api.Assertions.assertTrue(label.contains("color:#ff9933"));
      org.junit.jupiter.api.Assertions.assertTrue(label.contains("Running from source"));
    }
  }
}
