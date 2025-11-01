package com.jvn.core.scene2d;

public class Panel2D extends Entity2D {
  private double width;
  private double height;
  private double r = 0, g = 0, blue = 0, a = 0.6;
  private double strokeR = 1, strokeG = 1, strokeB = 1, strokeA = 1;
  private double strokeWidth = 0;
  private NinePatch ninePatch;

  public Panel2D(double width, double height) {
    this.width = width;
    this.height = height;
  }

  public void setSize(double w, double h) { this.width = w; this.height = h; }
  public void setFill(double r, double g, double b, double a) { this.r = r; this.g = g; this.blue = b; this.a = a; }
  public void setStroke(double r, double g, double b, double a, double width) {
    this.strokeR = r; this.strokeG = g; this.strokeB = b; this.strokeA = a; this.strokeWidth = Math.max(0, width);
  }
  public void setNinePatch(NinePatch np) { this.ninePatch = np; }

  @Override
  public void render(Blitter2D b) {
    b.push();
    if (ninePatch != null) {
      ninePatch.draw(b, 0, 0, width, height);
    } else {
      b.setGlobalAlpha(a);
      b.setFill(r, g, blue, a);
      b.fillRect(0, 0, width, height);
      if (strokeWidth > 0) {
        b.setGlobalAlpha(strokeA);
        b.setStroke(strokeR, strokeG, strokeB, strokeA);
        b.setStrokeWidth(strokeWidth);
        b.strokeRect(0, 0, width, height);
      }
    }
    b.pop();
  }
}

