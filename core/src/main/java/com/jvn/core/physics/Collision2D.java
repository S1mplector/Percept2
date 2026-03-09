package com.jvn.core.physics;

import com.jvn.core.math.Circle;
import com.jvn.core.math.Rect;

/**
 * Static utility class providing lightweight 2-D collision tests.
 *
 * <p>All methods are pure functions with no side-effects — they test for
 * overlap or intersection and return the result without modifying the
 * input shapes.</p>
 *
 * <h2>Supported Tests</h2>
 * <ul>
 *   <li>{@link #intersects(Rect, Rect)} — AABB vs AABB overlap.</li>
 *   <li>{@link #intersects(Circle, Circle)} — circle vs circle overlap.</li>
 *   <li>{@link #raycastSegmentAABB} — segment vs AABB raycast.</li>
 * </ul>
 *
 * @see PhysicsWorld2D
 * @see RigidBody2D
 */
public final class Collision2D {

  /** Non-instantiable utility class. */
  private Collision2D() {}

  /**
   * Test whether two axis-aligned bounding boxes overlap.
   *
   * @param a the first rectangle
   * @param b the second rectangle
   * @return {@code true} if the rectangles intersect
   */
  public static boolean intersects(Rect a, Rect b) {
    return a.intersects(b);
  }

  /**
   * Test whether two circles overlap.
   *
   * @param a the first circle
   * @param b the second circle
   * @return {@code true} if the circles intersect
   */
  public static boolean intersects(Circle a, Circle b) {
    double dx = a.x - b.x;
    double dy = a.y - b.y;
    double rr = a.r + b.r;
    return dx * dx + dy * dy <= rr * rr;
  }

  /**
   * Raycast a line segment against an axis-aligned bounding box using
   * the slab method.
   *
   * @param sx start-X of the segment
   * @param sy start-Y of the segment
   * @param ex end-X of the segment
   * @param ey end-Y of the segment
   * @param r  the AABB to test against
   * @return a 3-element array {@code [intersectX, intersectY, t]} where
   *         {@code t ∈ [0, 1]} is the parametric hit distance, or
   *         {@code null} if no intersection
   */
  public static double[] raycastSegmentAABB(double sx, double sy, double ex, double ey, Rect r) {
    double dx = ex - sx;
    double dy = ey - sy;
    double tmin = 0.0;
    double tmax = 1.0;

    if (dx != 0.0) {
      double tx1 = (r.left() - sx) / dx;
      double tx2 = (r.right() - sx) / dx;
      double tminx = Math.min(tx1, tx2);
      double tmaxx = Math.max(tx1, tx2);
      tmin = Math.max(tmin, tminx);
      tmax = Math.min(tmax, tmaxx);
    } else if (sx < r.left() || sx > r.right()) {
      return null;
    }

    if (dy != 0.0) {
      double ty1 = (r.top() - sy) / dy;
      double ty2 = (r.bottom() - sy) / dy;
      double tminy = Math.min(ty1, ty2);
      double tmaxy = Math.max(ty1, ty2);
      tmin = Math.max(tmin, tminy);
      tmax = Math.min(tmax, tmaxy);
    } else if (sy < r.top() || sy > r.bottom()) {
      return null;
    }

    if (tmax >= tmin && tmin >= 0.0 && tmin <= 1.0) {
      double ix = sx + tmin * dx;
      double iy = sy + tmin * dy;
      return new double[] { ix, iy, tmin };
    }
    return null;
  }
}
