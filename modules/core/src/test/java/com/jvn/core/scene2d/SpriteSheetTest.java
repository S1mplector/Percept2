package com.jvn.core.scene2d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SpriteSheetTest {
  @Test
  public void drawsCorrectRegion() {
    SpriteSheet sheet = new SpriteSheet("atlas.png", 16, 16, 4);
    CapturingBlitter bl = new CapturingBlitter();
    sheet.drawTile(bl, 5, 10, 12, 32, 48); // index 5 => row 1, col 1 (zero-based)
    assertEquals(16, bl.sx, 1e-6);
    assertEquals(16, bl.sy, 1e-6);
    assertEquals(16, bl.sw, 1e-6);
    assertEquals(16, bl.sh, 1e-6);
    assertEquals(10, bl.dx, 1e-6);
    assertEquals(12, bl.dy, 1e-6);
    assertEquals(32, bl.dw, 1e-6);
    assertEquals(48, bl.dh, 1e-6);
  }

  private static class CapturingBlitter implements Blitter2D {
    double sx, sy, sw, sh, dx, dy, dw, dh;
    @Override public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {
      this.sx = sx; this.sy = sy; this.sw = sw; this.sh = sh;
      this.dx = dx; this.dy = dy; this.dw = dw; this.dh = dh;
    }
    // Unused methods below
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
    @Override public void drawText(String text, double x, double y, double size, boolean bold) {}
    @Override public double measureTextWidth(String text, double size, boolean bold) { return 0; }
  }
}
