package com.jvn.core.math;

/**
 * Mutable infinite 2D line represented by one point and one direction vector.
 */
public class Line2 {
  public double x;
  public double y;
  public double dx;
  public double dy;

  public Line2() {
    this(0, 0, 1, 0);
  }

  public Line2(double x, double y, double dx, double dy) {
    set(x, y, dx, dy);
  }

  public static Line2 through(double x1, double y1, double x2, double y2) {
    return new Line2(x1, y1, x2 - x1, y2 - y1);
  }

  public Line2 set(double x, double y, double dx, double dy) {
    this.x = x;
    this.y = y;
    this.dx = dx;
    this.dy = dy;
    return this;
  }

  public Line2 copy() {
    return new Line2(x, y, dx, dy);
  }

  public boolean isDegenerate() {
    return Scalars.approxEquals(dx, 0.0) && Scalars.approxEquals(dy, 0.0);
  }

  public double signedDistance(double px, double py) {
    double len = Math.hypot(dx, dy);
    if (len == 0.0) return Math.hypot(px - x, py - y);
    return ((px - x) * dy - (py - y) * dx) / len;
  }

  public double distance(double px, double py) {
    return Math.abs(signedDistance(px, py));
  }

  public Vec2 project(double px, double py, Vec2 out) {
    Vec2 target = out == null ? new Vec2() : out;
    double lenSq = dx * dx + dy * dy;
    if (lenSq == 0.0) return target.set(x, y);
    double t = ((px - x) * dx + (py - y) * dy) / lenSq;
    return target.set(x + dx * t, y + dy * t);
  }
}
