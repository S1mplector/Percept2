package com.jvn.core.math;

/**
 * Mutable circle defined by a centre point and radius.
 *
 * <p>Used for radial collision checks, proximity tests, and circular
 * trigger zones. Like the other math primitives ({@link Vec2}, {@link Rect}),
 * it is mutable for zero-allocation hot-path usage.</p>
 *
 * @see Vec2
 * @see Rect
 */
public class Circle implements Shape2D {

  /** X coordinate of the circle's centre. */
  public double x;

  /** Y coordinate of the circle's centre. */
  public double y;

  /** Radius of the circle (should be ≥ 0). */
  public double r;

  /** Construct a zero-radius circle at the origin. */
  public Circle() { this(0, 0, 0); }

  /**
   * Construct a circle with the given centre and radius.
   *
   * @param x centre X
   * @param y centre Y
   * @param r radius (should be ≥ 0)
   */
  public Circle(double x, double y, double r) { this.x = x; this.y = y; this.r = r; }

  /**
   * Test whether a point lies inside (or on the boundary of) this circle.
   *
   * <p>Uses squared-distance comparison to avoid a costly {@code sqrt} call.</p>
   *
   * @param px X coordinate of the point
   * @param py Y coordinate of the point
   * @return {@code true} if the distance from the centre to the point is ≤ radius
   */
  @Override
  public boolean contains(double px, double py) {
    double dx = px - x;
    double dy = py - y;
    return dx * dx + dy * dy <= r * r;
  }

  @Override
  public Rect bounds(Rect out) {
    Rect target = out == null ? new Rect() : out;
    target.x = x - r;
    target.y = y - r;
    target.w = r * 2.0;
    target.h = r * 2.0;
    return target;
  }
}
