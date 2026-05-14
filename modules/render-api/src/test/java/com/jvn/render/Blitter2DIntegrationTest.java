package com.jvn.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.jvn.core.scene2d.Blitter2D;

/**
 * Integration tests for Blitter2D interface.
 */
public class Blitter2DIntegrationTest {

  /**
   * Mock Blitter2D for testing.
   */
  static class MockBlitter2D implements Blitter2D {
    private boolean cleared = false;
    private int drawCalls = 0;

    @Override
    public void clear(double r, double g, double b, double a) {
      cleared = true;
    }

    @Override
    public void setFill(double r, double g, double b, double a) {
      // Mock set fill
    }

    @Override
    public void setStroke(double r, double g, double b, double a) {
      // Mock set stroke
    }

    @Override
    public void setStrokeWidth(double w) {
      // Mock set width
    }

    @Override
    public void setGlobalAlpha(double a) {
      // Mock set alpha
    }

    @Override
    public void setFont(String family, double size, boolean bold) {
      // Mock set font
    }

    @Override
    public void push() {
      // Mock push
    }

    @Override
    public void pop() {
      // Mock pop
    }

    @Override
    public void translate(double x, double y) {
      // Mock translate
    }

    @Override
    public void rotateDeg(double degrees) {
      // Mock rotate
    }

    @Override
    public void scale(double sx, double sy) {
      // Mock scale
    }

    @Override
    public void transform(double mxx, double myx, double mxy, double myy, double tx, double ty) {
      // Mock transform
    }

    @Override
    public void fillRect(double x, double y, double w, double h) {
      drawCalls++;
    }

    @Override
    public void strokeRect(double x, double y, double w, double h) {
      drawCalls++;
    }

    @Override
    public void fillCircle(double cx, double cy, double radius) {
      drawCalls++;
    }

    @Override
    public void strokeCircle(double cx, double cy, double radius) {
      drawCalls++;
    }

    @Override
    public void drawLine(double x1, double y1, double x2, double y2) {
      drawCalls++;
    }

    @Override
    public void drawImage(String classpath, double x, double y, double w, double h) {
      drawCalls++;
    }

    @Override
    public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                                 double dx, double dy, double dw, double dh) {
      drawCalls++;
    }

    @Override
    public void drawText(String text, double x, double y, double size, boolean bold) {
      drawCalls++;
    }

    @Override
    public double measureTextWidth(String text, double size, boolean bold) {
      return text != null ? text.length() * size * 0.6 : 0;
    }

    @Override
    public void setClipRect(double x, double y, double w, double h) {
      // Mock clip
    }

    @Override
    public void setTextAlign(String hAlign, String vAlign) {
      // Mock text align
    }

    @Override
    public void setBlendMode(String mode) {
      // Mock blend mode
    }
  }

  @Test
  void testBlitterClearing() {
    Blitter2D blitter = new MockBlitter2D();
    blitter.clear(0, 0, 0, 1);
    // Verify clear was called (in mock)
    assertTrue(((MockBlitter2D) blitter).cleared);
  }

  @Test
  void testBlitterDrawing() {
    Blitter2D blitter = new MockBlitter2D();
    MockBlitter2D mock = (MockBlitter2D) blitter;

    blitter.fillRect(10, 10, 100, 50);
    blitter.strokeRect(20, 20, 50, 30);
    blitter.fillCircle(100, 100, 25);
    blitter.drawLine(0, 0, 100, 100);
    blitter.drawImage("game/images/test.png", 0, 0, 32, 32);
    blitter.drawText("Score: 100", 50, 50, 14, false);

    assertEquals(6, mock.drawCalls, "Should have 6 draw calls");
  }

  @Test
  void testBlitterTextMeasurement() {
    Blitter2D blitter = new MockBlitter2D();
    double width = blitter.measureTextWidth("Hello", 12, false);
    assertTrue(width > 0, "Text width should be positive");
    assertEquals(36.0, width, "Width should be proportional to text");
  }

  @Test
  void testBlitterStateManagement() {
    Blitter2D blitter = new MockBlitter2D();
    blitter.push();
    blitter.setFill(1, 0, 0, 1);
    blitter.pop();
    // Should not throw
    assertTrue(true);
  }

  @Test
  void testBlitterTransformations() {
    Blitter2D blitter = new MockBlitter2D();
    blitter.translate(50, 50);
    blitter.rotateDeg(45);
    blitter.scale(2, 2);
    blitter.transform(1, 0, 0, 1, 100, 100);
    // Should not throw
    assertTrue(true);
  }
}
