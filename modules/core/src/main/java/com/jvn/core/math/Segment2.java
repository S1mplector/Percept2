package com.jvn.core.math;

/**
 * Mutable 2D line segment defined by a start and end point.
 */
public class Segment2 {
  public double x1;
  public double y1;
  public double x2;
  public double y2;

  public Segment2() {
    this(0, 0, 0, 0);
  }

  public Segment2(double x1, double y1, double x2, double y2) {
    this.x1 = x1;
    this.y1 = y1;
    this.x2 = x2;
    this.y2 = y2;
  }

  public Segment2 set(double x1, double y1, double x2, double y2) {
    this.x1 = x1;
    this.y1 = y1;
    this.x2 = x2;
    this.y2 = y2;
    return this;
  }

  public Segment2 set(Vec2 start, Vec2 end) {
    double sx = start == null ? 0.0 : start.x;
    double sy = start == null ? 0.0 : start.y;
    double ex = end == null ? 0.0 : end.x;
    double ey = end == null ? 0.0 : end.y;
    return set(sx, sy, ex, ey);
  }

  public Segment2 translate(double dx, double dy) {
    this.x1 += dx;
    this.y1 += dy;
    this.x2 += dx;
    this.y2 += dy;
    return this;
  }

  public Segment2 copy() {
    return new Segment2(x1, y1, x2, y2);
  }

  public double dx() {
    return x2 - x1;
  }

  public double dy() {
    return y2 - y1;
  }

  public double lengthSquared() {
    double dx = dx();
    double dy = dy();
    return dx * dx + dy * dy;
  }

  public double length() {
    return Math.sqrt(lengthSquared());
  }

  public Vec2 start(Vec2 out) {
    Vec2 target = out == null ? new Vec2() : out;
    return target.set(x1, y1);
  }

  public Vec2 end(Vec2 out) {
    Vec2 target = out == null ? new Vec2() : out;
    return target.set(x2, y2);
  }

  public Vec2 delta(Vec2 out) {
    Vec2 target = out == null ? new Vec2() : out;
    return target.set(dx(), dy());
  }

  public Vec2 midpoint(Vec2 out) {
    Vec2 target = out == null ? new Vec2() : out;
    return target.set((x1 + x2) * 0.5, (y1 + y2) * 0.5);
  }

  public Vec2 pointAt(double t, Vec2 out) {
    Vec2 target = out == null ? new Vec2() : out;
    return target.set(
        x1 + (x2 - x1) * t,
        y1 + (y2 - y1) * t);
  }

  public Rect bounds(Rect out) {
    Rect target = out == null ? new Rect() : out;
    double minX = Math.min(x1, x2);
    double minY = Math.min(y1, y2);
    double maxX = Math.max(x1, x2);
    double maxY = Math.max(y1, y2);
    target.x = minX;
    target.y = minY;
    target.w = maxX - minX;
    target.h = maxY - minY;
    return target;
  }
}
