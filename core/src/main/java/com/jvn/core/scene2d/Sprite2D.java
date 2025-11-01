package com.jvn.core.scene2d;

public class Sprite2D extends Entity2D {
  private String imagePath;
  private double width;
  private double height;
  private double alpha = 1.0;
  private boolean useRegion = false;
  private double sx, sy, sw, sh;
  private double originX = 0.0;
  private double originY = 0.0;

  public Sprite2D(String imagePath, double width, double height) {
    this.imagePath = imagePath;
    this.width = width;
    this.height = height;
  }

  public Sprite2D region(String imagePath, double sx, double sy, double sw, double sh, double dw, double dh) {
    this.imagePath = imagePath;
    this.useRegion = true;
    this.sx = sx; this.sy = sy; this.sw = sw; this.sh = sh;
    this.width = dw; this.height = dh;
    return this;
  }

  public void setAlpha(double a) { this.alpha = a; }
  public double getAlpha() { return alpha; }
  public String getImagePath() { return imagePath; }
  public void setImagePath(String path) { this.imagePath = path; }
  public double getWidth() { return width; }
  public double getHeight() { return height; }
  public void setSize(double w, double h) { this.width = w; this.height = h; }
  public void setOrigin(double ox, double oy) { this.originX = ox; this.originY = oy; }
  public double getOriginX() { return originX; }
  public double getOriginY() { return originY; }

  @Override
  public void render(Blitter2D b) {
    if (imagePath == null) return;
    b.push();
    if (alpha != 1.0) b.setGlobalAlpha(alpha);
    double dx = -originX * width;
    double dy = -originY * height;
    if (useRegion) b.drawImageRegion(imagePath, sx, sy, sw, sh, dx, dy, width, height);
    else b.drawImage(imagePath, dx, dy, width, height);
    b.pop();
  }
}
