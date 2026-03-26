package com.jvn.core.math;

/**
 * Mutable 2D transform composed of translation, rotation, and non-uniform scale.
 *
 * <p>Transform order is scale -> rotate -> translate.</p>
 */
public class Transform2 {
  public double x;
  public double y;
  public double rotationDeg;
  public double scaleX;
  public double scaleY;

  public Transform2() {
    this(0, 0, 0, 1, 1);
  }

  public Transform2(double x, double y, double rotationDeg, double scaleX, double scaleY) {
    this.x = x;
    this.y = y;
    this.rotationDeg = rotationDeg;
    this.scaleX = scaleX;
    this.scaleY = scaleY;
  }

  public Transform2 set(double x, double y, double rotationDeg, double scaleX, double scaleY) {
    this.x = x;
    this.y = y;
    this.rotationDeg = rotationDeg;
    this.scaleX = scaleX;
    this.scaleY = scaleY;
    return this;
  }

  public Transform2 setIdentity() {
    return set(0, 0, 0, 1, 1);
  }

  public Transform2 setPosition(double x, double y) {
    this.x = x;
    this.y = y;
    return this;
  }

  public Transform2 setScale(double scale) {
    return setScale(scale, scale);
  }

  public Transform2 setScale(double scaleX, double scaleY) {
    this.scaleX = scaleX;
    this.scaleY = scaleY;
    return this;
  }

  public Transform2 translate(double dx, double dy) {
    this.x += dx;
    this.y += dy;
    return this;
  }

  public Transform2 rotateDeg(double deltaDeg) {
    this.rotationDeg += deltaDeg;
    return this;
  }

  public Transform2 scale(double scale) {
    return scale(scale, scale);
  }

  public Transform2 scale(double sx, double sy) {
    this.scaleX *= sx;
    this.scaleY *= sy;
    return this;
  }

  public Transform2 copy() {
    return new Transform2(x, y, rotationDeg, scaleX, scaleY);
  }

  public Vec2 transform(double px, double py, Vec2 out) {
    Vec2 target = out == null ? new Vec2() : out;
    double sx = px * scaleX;
    double sy = py * scaleY;
    double radians = Math.toRadians(rotationDeg);
    double cos = Math.cos(radians);
    double sin = Math.sin(radians);
    double rx = sx * cos - sy * sin;
    double ry = sx * sin + sy * cos;
    return target.set(rx + x, ry + y);
  }

  public Vec2 applyTo(Vec2 point) {
    if (point == null) return null;
    double px = point.x;
    double py = point.y;
    return transform(px, py, point);
  }
}
