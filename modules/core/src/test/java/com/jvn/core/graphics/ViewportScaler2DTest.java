package com.jvn.core.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.math.Rect;

class ViewportScaler2DTest {

  @Test
  void fitExposesScreenBoundsContentSizeAndCoordinateConversions() {
    ViewportScaler2D.Transform transform = ViewportScaler2D.fit(100, 50, 400, 300);

    Rect screen = transform.screenBounds(null);
    Rect logical = transform.logicalBounds(null);

    assertEquals(4.0, transform.scale(), 1e-9);
    assertEquals(0.0, transform.offsetX(), 1e-9);
    assertEquals(50.0, transform.offsetY(), 1e-9);
    assertEquals(400.0, transform.contentWidth(), 1e-9);
    assertEquals(200.0, transform.contentHeight(), 1e-9);
    assertEquals(0.0, screen.x, 1e-9);
    assertEquals(50.0, screen.y, 1e-9);
    assertEquals(400.0, screen.w, 1e-9);
    assertEquals(200.0, screen.h, 1e-9);
    assertEquals(0.0, logical.x, 1e-9);
    assertEquals(0.0, logical.y, 1e-9);
    assertEquals(100.0, logical.w, 1e-9);
    assertEquals(50.0, logical.h, 1e-9);
    assertEquals(40.0, transform.logicalToScreenX(10.0), 1e-9);
    assertEquals(70.0, transform.logicalToScreenY(5.0), 1e-9);
    assertEquals(10.0, transform.screenToLogicalX(40.0), 1e-9);
    assertEquals(5.0, transform.screenToLogicalY(70.0), 1e-9);
  }

  @Test
  void fitContainsChecksRespectLetterboxedArea() {
    ViewportScaler2D.Transform transform = ViewportScaler2D.fit(100, 50, 400, 300);

    assertTrue(transform.containsScreen(200.0, 150.0));
    assertFalse(transform.containsScreen(200.0, 20.0));
    assertTrue(transform.containsLogical(100.0, 50.0));
    assertFalse(transform.containsLogical(101.0, 50.0));
  }

  @Test
  void fitFallsBackToSafeDimensionsWhenInputsAreNonPositive() {
    ViewportScaler2D.Transform transform = ViewportScaler2D.fit(0, 0, 0, 0);

    assertEquals(1.0, transform.scale(), 1e-9);
    assertEquals(0.0, transform.offsetX(), 1e-9);
    assertEquals(0.0, transform.offsetY(), 1e-9);
    assertEquals(1.0, transform.targetWidth(), 1e-9);
    assertEquals(1.0, transform.targetHeight(), 1e-9);
  }
}
