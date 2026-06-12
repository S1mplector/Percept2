package com.jvn.core.physics;

import com.jvn.core.math.Circle;
import com.jvn.core.math.Geometry2D;
import com.jvn.core.math.Ray2;
import com.jvn.core.math.Rect;
import com.jvn.core.math.Segment2;

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
 *   <li>{@link #intersects(Circle, Rect)} — circle vs AABB overlap.</li>
 *   <li>{@link #raycastSegmentAABB} — segment vs AABB raycast.</li>
 *   <li>{@link #raycastRayAABB} — ray vs AABB raycast.</li>
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
    return Geometry2D.intersects(a, b);
  }

  /**
   * Test whether a circle overlaps an axis-aligned rectangle.
   *
   * @param circle the circle
   * @param rect the rectangle
   * @return {@code true} if the shapes intersect
   */
  public static boolean intersects(Circle circle, Rect rect) {
    return Geometry2D.intersects(circle, rect);
  }

  /**
   * Test whether an axis-aligned rectangle overlaps a circle.
   *
   * @param rect the rectangle
   * @param circle the circle
   * @return {@code true} if the shapes intersect
   */
  public static boolean intersects(Rect rect, Circle circle) {
    return intersects(circle, rect);
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
    return Geometry2D.raycastSegmentAABB(sx, sy, ex, ey, r);
  }

  /**
   * Raycast a typed line segment against an axis-aligned bounding box.
   *
   * @param segment the segment to test
   * @param r the AABB to test against
   * @return a 3-element array {@code [intersectX, intersectY, t]} or {@code null}
   */
  public static double[] raycastSegmentAABB(Segment2 segment, Rect r) {
    if (segment == null || r == null) return null;
    return raycastSegmentAABB(segment.x1, segment.y1, segment.x2, segment.y2, r);
  }

  /**
   * Raycast an infinite ray against an axis-aligned bounding box using
   * the slab method.
   *
   * @param ox origin X
   * @param oy origin Y
   * @param dx direction X
   * @param dy direction Y
   * @param r the AABB to test against
   * @return a 3-element array {@code [intersectX, intersectY, t]} where
   *         {@code t >= 0} scales the direction vector, or {@code null}
   */
  public static double[] raycastRayAABB(double ox, double oy, double dx, double dy, Rect r) {
    return Geometry2D.raycastRayAABB(ox, oy, dx, dy, r);
  }

  /**
   * Raycast a typed ray against an axis-aligned bounding box.
   *
   * @param ray the ray to test
   * @param r the AABB to test against
   * @return a 3-element array {@code [intersectX, intersectY, t]} or {@code null}
   */
  public static double[] raycastRayAABB(Ray2 ray, Rect r) {
    if (ray == null || r == null) return null;
    return raycastRayAABB(ray.x, ray.y, ray.dx, ray.dy, r);
  }
}
