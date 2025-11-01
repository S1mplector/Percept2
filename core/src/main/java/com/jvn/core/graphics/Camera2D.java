package com.jvn.core.graphics;

public class Camera2D {
  private double x;
  private double y;
  private double zoom = 1.0;
  private double targetX;
  private double targetY;
  private double smoothingMs = 0.0;
  private boolean hasBounds = false;
  private double boundLeft;
  private double boundTop;
  private double boundRight;
  private double boundBottom;

  public double getX() { return x; }
  public double getY() { return y; }
  public double getZoom() { return zoom; }

  public void setPosition(double x, double y) { this.x = x; this.y = y; this.targetX = x; this.targetY = y; clampToBounds(); }
  public void setZoom(double z) { this.zoom = z <= 0 ? 0.0001 : z; }

  public void setTarget(double x, double y) { this.targetX = x; this.targetY = y; }
  public double getTargetX() { return targetX; }
  public double getTargetY() { return targetY; }

  public void setSmoothingMs(double ms) { this.smoothingMs = ms < 0 ? 0 : ms; }

  public void setBounds(double left, double top, double right, double bottom) {
    this.boundLeft = Math.min(left, right);
    this.boundTop = Math.min(top, bottom);
    this.boundRight = Math.max(left, right);
    this.boundBottom = Math.max(top, bottom);
    this.hasBounds = true;
    clampToBounds();
  }

  public void clearBounds() { this.hasBounds = false; }

  public void update(long deltaMs) {
    if (smoothingMs <= 0) {
      this.x = targetX;
      this.y = targetY;
      clampToBounds();
      return;
    }
    double dt = deltaMs <= 0 ? 0 : deltaMs;
    double tau = smoothingMs;
    double alpha = 1.0 - Math.exp(-dt / tau);
    this.x = this.x + (targetX - this.x) * alpha;
    this.y = this.y + (targetY - this.y) * alpha;
    clampToBounds();
  }

  private void clampToBounds() {
    if (!hasBounds) return;
    if (x < boundLeft) x = boundLeft;
    if (y < boundTop) y = boundTop;
    if (x > boundRight) x = boundRight;
    if (y > boundBottom) y = boundBottom;
  }

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
