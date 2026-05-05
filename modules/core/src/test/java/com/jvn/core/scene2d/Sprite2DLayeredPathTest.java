package com.jvn.core.scene2d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class Sprite2DLayeredPathTest {

  @Test
  void rendersEachLayerInPathSpec() {
    Sprite2D sprite = new Sprite2D("a.png | b.png | c.png", 100, 200);
    sprite.setOrigin(0.5, 1.0);

    CapturingBlitter blitter = new CapturingBlitter();
    sprite.render(blitter);

    assertEquals(3, blitter.images.size());
    assertEquals("a.png", blitter.images.get(0).path);
    assertEquals("b.png", blitter.images.get(1).path);
    assertEquals("c.png", blitter.images.get(2).path);
    assertEquals(-50.0, blitter.images.get(0).x, 1e-9);
    assertEquals(-200.0, blitter.images.get(0).y, 1e-9);
  }

  @Test
  void keepsSingleImageBehaviorWhenNoLayerSeparator() {
    Sprite2D sprite = new Sprite2D("hero.png", 80, 120);
    CapturingBlitter blitter = new CapturingBlitter();

    sprite.render(blitter);

    assertEquals(1, blitter.images.size());
    assertEquals("hero.png", blitter.images.get(0).path);
  }

  @Test
  void ignoresEmptyLayerTokens() {
    Sprite2D sprite = new Sprite2D("  a.png  |   | b.png | ", 60, 60);
    CapturingBlitter blitter = new CapturingBlitter();

    sprite.render(blitter);

    assertEquals(2, blitter.images.size());
    assertEquals("a.png", blitter.images.get(0).path);
    assertEquals("b.png", blitter.images.get(1).path);
  }

  private static final class CapturingBlitter implements Blitter2D {
    private final List<DrawImageCall> images = new ArrayList<>();

    @Override public void drawImage(String classpath, double x, double y, double w, double h) {
      images.add(new DrawImageCall(classpath, x, y, w, h));
    }

    @Override public void push() {}
    @Override public void pop() {}
    @Override public void setGlobalAlpha(double a) {}

    // Unused methods
    @Override public void clear(double r, double g, double b, double a) {}
    @Override public void setFill(double r, double g, double b, double a) {}
    @Override public void setStroke(double r, double g, double b, double a) {}
    @Override public void setStrokeWidth(double w) {}
    @Override public void setFont(String family, double size, boolean bold) {}
    @Override public void translate(double x, double y) {}
    @Override public void rotateDeg(double degrees) {}
    @Override public void scale(double sx, double sy) {}
    @Override public void fillRect(double x, double y, double w, double h) {}
    @Override public void strokeRect(double x, double y, double w, double h) {}
    @Override public void fillCircle(double cx, double cy, double radius) {}
    @Override public void strokeCircle(double cx, double cy, double radius) {}
    @Override public void drawLine(double x1, double y1, double x2, double y2) {}
    @Override public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {}
    @Override public void drawText(String text, double x, double y, double size, boolean bold) {}
    @Override public double measureTextWidth(String text, double size, boolean bold) { return 0; }
  }

  private record DrawImageCall(String path, double x, double y, double w, double h) {}
}
