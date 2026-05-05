package com.jvn.core.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.jvn.core.math.Rect;

class Camera2DTest {

  @Test
  void centerAndViewHelpersRespectZoom() {
    Camera2D camera = new Camera2D();
    camera.setViewportSize(200, 100);
    camera.setZoom(2.0);
    camera.setCenter(250, 125, 200, 100);

    Rect view = camera.viewRect(null);

    assertEquals(200.0, camera.getX(), 1e-9);
    assertEquals(100.0, camera.getY(), 1e-9);
    assertEquals(100.0, camera.viewWidth(), 1e-9);
    assertEquals(50.0, camera.viewHeight(), 1e-9);
    assertEquals(250.0, camera.centerX(), 1e-9);
    assertEquals(125.0, camera.centerY(), 1e-9);
    assertEquals(200.0, view.x, 1e-9);
    assertEquals(100.0, view.y, 1e-9);
    assertEquals(100.0, view.w, 1e-9);
    assertEquals(50.0, view.h, 1e-9);

    camera.setTargetCenter(300, 200, 200, 100);
    assertEquals(250.0, camera.getTargetX(), 1e-9);
    assertEquals(175.0, camera.getTargetY(), 1e-9);
  }

  @Test
  void boundsClampAgainstVisibleViewWhenViewportIsKnown() {
    Camera2D camera = new Camera2D();
    camera.setViewportSize(100, 50);
    camera.setBounds(0, 0, 200, 100);
    camera.setPosition(180, 90);

    assertEquals(100.0, camera.getX(), 1e-9);
    assertEquals(50.0, camera.getY(), 1e-9);

    camera.setTarget(500, 500);
    assertEquals(100.0, camera.getTargetX(), 1e-9);
    assertEquals(50.0, camera.getTargetY(), 1e-9);
  }

  @Test
  void boundsCenterTheViewWhenItIsLargerThanTheWorld() {
    Camera2D camera = new Camera2D();
    camera.setViewportSize(300, 200);
    camera.setBounds(0, 0, 100, 100);
    camera.setPosition(0, 0);

    assertEquals(-100.0, camera.getX(), 1e-9);
    assertEquals(-50.0, camera.getY(), 1e-9);
  }

  @Test
  void coordinateTransformsRoundTripWithAndWithoutOriginOffsets() {
    Camera2D camera = new Camera2D();
    camera.setPosition(100, 50);
    camera.setZoom(2.0);

    assertEquals(100.0, camera.worldToScreenX(150), 1e-9);
    assertEquals(80.0, camera.worldToScreenY(90), 1e-9);
    assertEquals(150.0, camera.screenToWorldX(100), 1e-9);
    assertEquals(90.0, camera.screenToWorldY(80), 1e-9);
    assertEquals(420.0, camera.worldToScreenX(150, 320), 1e-9);
    assertEquals(320.0, camera.worldToScreenY(90, 240), 1e-9);
    assertEquals(150.0, camera.screenToWorldX(420, 320), 1e-9);
    assertEquals(90.0, camera.screenToWorldY(320, 240), 1e-9);
  }

  @Test
  void smoothingUsesFrameRateIndependentExponentialDecay() {
    Camera2D camera = new Camera2D();
    camera.setPosition(0, 0);
    camera.setTarget(100, 50);
    camera.setSmoothingMs(100);

    camera.update(100);

    assertEquals(63.212055882855765, camera.getX(), 1e-9);
    assertEquals(31.606027941427882, camera.getY(), 1e-9);
  }
}
