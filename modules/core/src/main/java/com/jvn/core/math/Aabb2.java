package com.jvn.core.math;

/**
 * Mutable axis-aligned bounding box stored as min/max edges.
 *
 * <p>{@link Rect} is better for UI layout because it stores x/y/width/height.
 * {@code Aabb2} is better for geometry, physics broad-phase, and editor gizmos
 * because expanding and combining boxes is edge-based.</p>
 */
public class Aabb2 implements Shape2D {
  public double minX;
  public double minY;
  public double maxX;
  public double maxY;

  public Aabb2() {
    this(0, 0, 0, 0);
  }

  public Aabb2(double minX, double minY, double maxX, double maxY) {
    set(minX, minY, maxX, maxY);
  }

  public static Aabb2 fromRect(Rect rect) {
    if (rect == null) return new Aabb2();
    return new Aabb2(rect.left(), rect.top(), rect.right(), rect.bottom());
  }

  public Aabb2 set(double minX, double minY, double maxX, double maxY) {
    this.minX = Math.min(minX, maxX);
    this.minY = Math.min(minY, maxY);
    this.maxX = Math.max(minX, maxX);
    this.maxY = Math.max(minY, maxY);
    return this;
  }

  public Aabb2 set(Rect rect) {
    if (rect == null) return set(0, 0, 0, 0);
    return set(rect.left(), rect.top(), rect.right(), rect.bottom());
  }

  public Aabb2 copy() {
    return new Aabb2(minX, minY, maxX, maxY);
  }

  public double width() { return maxX - minX; }
  public double height() { return maxY - minY; }
  public double centerX() { return (minX + maxX) * 0.5; }
  public double centerY() { return (minY + maxY) * 0.5; }

  public boolean intersects(Aabb2 other) {
    return other != null
        && maxX >= other.minX && minX <= other.maxX
        && maxY >= other.minY && minY <= other.maxY;
  }

  public Aabb2 include(double x, double y) {
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    maxX = Math.max(maxX, x);
    maxY = Math.max(maxY, y);
    return this;
  }

  public Rect toRect(Rect out) {
    Rect target = out == null ? new Rect() : out;
    target.x = minX;
    target.y = minY;
    target.w = width();
    target.h = height();
    return target;
  }

  @Override
  public boolean contains(double x, double y) {
    return x >= minX && x <= maxX && y >= minY && y <= maxY;
  }

  @Override
  public Rect bounds(Rect out) {
    return toRect(out);
  }
}
