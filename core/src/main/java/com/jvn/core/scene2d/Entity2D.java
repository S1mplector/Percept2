package com.jvn.core.scene2d;

public class Entity2D {
  protected double x;
  protected double y;
  protected double rotationDeg;
  protected double scaleX = 1.0;
  protected double scaleY = 1.0;
  protected double z;
  protected boolean visible = true;
  protected double parallaxX = 1.0;
  protected double parallaxY = 1.0;

  public double getX() { return x; }
  public double getY() { return y; }
  public double getRotationDeg() { return rotationDeg; }
  public double getScaleX() { return scaleX; }
  public double getScaleY() { return scaleY; }
  public double getZ() { return z; }
  public boolean isVisible() { return visible; }
  public double getParallaxX() { return parallaxX; }
  public double getParallaxY() { return parallaxY; }

  public void setPosition(double x, double y) { this.x = x; this.y = y; }
  public void setRotationDeg(double deg) { this.rotationDeg = deg; }
  public void setScale(double sx, double sy) { this.scaleX = sx; this.scaleY = sy; }
  public void setZ(double z) { this.z = z; }
  public void setVisible(boolean visible) { this.visible = visible; }
  public void setParallax(double px, double py) { this.parallaxX = px; this.parallaxY = py; }

  public void update(long deltaMs) {}

  public void render(Blitter2D b) {}
}
