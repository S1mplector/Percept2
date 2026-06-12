package com.jvn.core.math;

/**
 * Mutable capsule: a line segment swept by a radius.
 *
 * <p>Useful for character hit volumes, trigger zones, thick editor guides, and
 * forgiving pointer hit tests.</p>
 */
public class Capsule2 implements Shape2D {
  public double x1;
  public double y1;
  public double x2;
  public double y2;
  public double r;

  public Capsule2() {
    this(0, 0, 0, 0, 0);
  }

  public Capsule2(double x1, double y1, double x2, double y2, double r) {
    set(x1, y1, x2, y2, r);
  }

  public Capsule2 set(double x1, double y1, double x2, double y2, double r) {
    this.x1 = x1;
    this.y1 = y1;
    this.x2 = x2;
    this.y2 = y2;
    this.r = Math.max(0.0, r);
    return this;
  }

  public Capsule2 copy() {
    return new Capsule2(x1, y1, x2, y2, r);
  }

  public Segment2 segment(Segment2 out) {
    Segment2 target = out == null ? new Segment2() : out;
    return target.set(x1, y1, x2, y2);
  }

  @Override
  public boolean contains(double x, double y) {
    return Geometry2D.distancePointSegmentSq(x, y, x1, y1, x2, y2) <= r * r;
  }

  @Override
  public Rect bounds(Rect out) {
    Rect target = out == null ? new Rect() : out;
    double minX = Math.min(x1, x2) - r;
    double minY = Math.min(y1, y2) - r;
    double maxX = Math.max(x1, x2) + r;
    double maxY = Math.max(y1, y2) + r;
    target.x = minX;
    target.y = minY;
    target.w = maxX - minX;
    target.h = maxY - minY;
    return target;
  }
}
