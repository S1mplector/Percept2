package com.jvn.core.graphics;

public class Camera2D {
  private double x;
  private double y;
  private double zoom = 1.0;

  public double getX() { return x; }
  public double getY() { return y; }
  public double getZoom() { return zoom; }

  public void setPosition(double x, double y) { this.x = x; this.y = y; }
  public void setZoom(double z) { this.zoom = z <= 0 ? 0.0001 : z; }

  public double worldToScreenX(double wx, double viewportWidth, double originX) {
    return (wx - x) * zoom + originX;
  }

  public double worldToScreenY(double wy, double viewportHeight, double originY) {
    return (wy - y) * zoom + originY;
  }

  public double screenToWorldX(double sx, double viewportWidth, double originX) {
    return (sx - originX) / zoom + x;
  }

  public double screenToWorldY(double sy, double viewportHeight, double originY) {
    return (sy - originY) / zoom + y;
  }
}
