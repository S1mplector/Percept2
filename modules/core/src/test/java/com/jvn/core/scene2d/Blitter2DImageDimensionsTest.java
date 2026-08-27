package com.jvn.core.scene2d;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Blitter2DImageDimensionsTest {

  @Test
  void imageDimensionsFeatureExists() {
    assertTrue(Arrays.asList(RenderFeature.values()).contains(RenderFeature.IMAGE_DIMENSIONS));
  }

  @Test
  void defaultImageDimensionsReturnsEmptyForAnyImplementationThatDoesNotOverrideIt() {
    Blitter2D blitter = new NoOpBlitter2D();
    assertTrue(blitter.imageDimensions("any/path.png").isEmpty());
  }

  /** Minimal no-op implementation exercising only the default {@code imageDimensions()}. */
  private static final class NoOpBlitter2D implements Blitter2D {
    @Override public void clear(double r, double g, double b, double a) {}
    @Override public void setFill(double r, double g, double b, double a) {}
    @Override public void setStroke(double r, double g, double b, double a) {}
    @Override public void setStrokeWidth(double w) {}
    @Override public void setGlobalAlpha(double a) {}
    @Override public void setFont(String family, double size, boolean bold) {}
    @Override public void push() {}
    @Override public void pop() {}
    @Override public void translate(double x, double y) {}
    @Override public void rotateDeg(double degrees) {}
    @Override public void scale(double sx, double sy) {}
    @Override public void fillRect(double x, double y, double w, double h) {}
    @Override public void strokeRect(double x, double y, double w, double h) {}
    @Override public void fillCircle(double cx, double cy, double radius) {}
    @Override public void strokeCircle(double cx, double cy, double radius) {}
    @Override public void drawLine(double x1, double y1, double x2, double y2) {}
    @Override public void drawImage(String classpath, double x, double y, double w, double h) {}
    @Override public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {}
    @Override public void drawText(String text, double x, double y, double size, boolean bold) {}
    @Override public double measureTextWidth(String text, double size, boolean bold) { return 0; }
  }
}
