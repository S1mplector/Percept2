package com.jvn.core.math;

/**
 * Mutable axis-aligned rectangle defined by its top-left corner and dimensions.
 *
 * <p>{@code Rect} is used throughout the engine for bounding boxes, UI layout
 * regions, collision areas, and viewport definitions. Like {@link Vec2}, it is
 * mutable to minimise garbage in hot paths.</p>
 *
 * <h2>Coordinate Convention</h2>
 * <p>The rectangle assumes a <b>screen-space</b> coordinate system where
 * {@code (x, y)} is the <em>top-left</em> corner, {@code w} extends rightward,
 * and {@code h} extends downward:</p>
 * <pre>
 *   (x, y) ──────────── (x+w, y)
 *     │                     │
 *     │       interior      │
 *     │                     │
 *   (x, y+h) ─────────── (x+w, y+h)
 * </pre>
 *
 * @see Vec2
 * @see Circle
 */
public class Rect implements Shape2D {

  /** X coordinate of the left edge. */
  public double x;

  /** Y coordinate of the top edge. */
  public double y;

  /** Width of the rectangle (extends rightward from {@link #x}). */
  public double w;

  /** Height of the rectangle (extends downward from {@link #y}). */
  public double h;

  /** Construct a zero-area rectangle at the origin. */
  public Rect() { this(0, 0, 0, 0); }

  /**
   * Construct a rectangle with the given position and dimensions.
   *
   * @param x left edge
   * @param y top edge
   * @param w width (should be ≥ 0)
   * @param h height (should be ≥ 0)
   */
  public Rect(double x, double y, double w, double h) { this.x = x; this.y = y; this.w = w; this.h = h; }

  /** @return the X coordinate of the left edge (same as {@link #x}) */
  public double left() { return x; }

  /** @return the X coordinate of the right edge ({@code x + w}) */
  public double right() { return x + w; }

  /** @return the Y coordinate of the top edge (same as {@link #y}) */
  public double top() { return y; }

  /** @return the Y coordinate of the bottom edge ({@code y + h}) */
  public double bottom() { return y + h; }

  /**
   * Test whether a point lies inside (or on the boundary of) this rectangle.
   *
   * @param px X coordinate of the point
   * @param py Y coordinate of the point
   * @return {@code true} if the point is within or on the edges of this rect
   */
  @Override
  public boolean contains(double px, double py) {
    return px >= left() && px <= right() && py >= top() && py <= bottom();
  }

  /**
   * Test whether this rectangle overlaps (or touches) another rectangle.
   * Two rectangles that share only an edge are considered intersecting.
   *
   * @param o the other rectangle
   * @return {@code true} if the rectangles overlap
   */
  public boolean intersects(Rect o) {
    return right() >= o.left() && left() <= o.right() && bottom() >= o.top() && top() <= o.bottom();
  }

  @Override
  public Rect bounds(Rect out) {
    Rect target = out == null ? new Rect() : out;
    target.x = x;
    target.y = y;
    target.w = w;
    target.h = h;
    return target;
  }
}
