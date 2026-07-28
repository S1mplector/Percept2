package com.jvn.core.math;

/**
 * Shared geometry and hit-test helpers for engine primitives.
 */
public final class Geometry2D {
  private Geometry2D() {}

  public static boolean contains(Shape2D shape, double x, double y) {
    return shape != null && shape.contains(x, y);
  }

  public static boolean intersects(Rect a, Rect b) {
    return a != null && b != null && a.intersects(b);
  }

  public static boolean intersects(Aabb2 a, Aabb2 b) {
    return a != null && a.intersects(b);
  }

  public static boolean intersects(Circle a, Circle b) {
    if (a == null || b == null) return false;
    double dx = a.x - b.x;
    double dy = a.y - b.y;
    double rr = a.r + b.r;
    return dx * dx + dy * dy <= rr * rr;
  }

  public static boolean intersects(Circle circle, Rect rect) {
    if (circle == null || rect == null) return false;
    double closestX = Scalars.clamp(circle.x, rect.left(), rect.right());
    double closestY = Scalars.clamp(circle.y, rect.top(), rect.bottom());
    double dx = closestX - circle.x;
    double dy = closestY - circle.y;
    return dx * dx + dy * dy <= circle.r * circle.r;
  }

  public static boolean intersects(Rect rect, Circle circle) {
    return intersects(circle, rect);
  }

  public static boolean intersects(Capsule2 capsule, Circle circle) {
    if (capsule == null || circle == null) return false;
    double rr = capsule.r + circle.r;
    return distancePointSegmentSq(circle.x, circle.y, capsule.x1, capsule.y1, capsule.x2, capsule.y2) <= rr * rr;
  }

  public static boolean intersects(Circle circle, Capsule2 capsule) {
    return intersects(capsule, circle);
  }

  public static boolean intersects(Capsule2 capsule, Rect rect) {
    if (capsule == null || rect == null) return false;
    double radius = Double.isFinite(capsule.r) ? Math.max(0.0, capsule.r) : 0.0;
    double left = rect.left() - radius;
    double top = rect.top() - radius;
    double right = rect.right() + radius;
    double bottom = rect.bottom() + radius;
    return segmentIntersectsAabb(
            capsule.x1, capsule.y1, capsule.x2, capsule.y2,
            left, top, right, bottom)
        || rect.contains(capsule.x1, capsule.y1)
        || rect.contains(capsule.x2, capsule.y2);
  }

  public static boolean intersects(Rect rect, Capsule2 capsule) {
    return intersects(capsule, rect);
  }

  public static double distancePointSegmentSq(double px, double py, double x1, double y1, double x2, double y2) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    double lenSq = dx * dx + dy * dy;
    if (lenSq == 0.0) {
      double ox = px - x1;
      double oy = py - y1;
      return ox * ox + oy * oy;
    }
    double t = Scalars.clamp(((px - x1) * dx + (py - y1) * dy) / lenSq, 0.0, 1.0);
    double cx = x1 + dx * t;
    double cy = y1 + dy * t;
    double ox = px - cx;
    double oy = py - cy;
    return ox * ox + oy * oy;
  }

  public static double distancePointSegment(double px, double py, double x1, double y1, double x2, double y2) {
    return Math.sqrt(distancePointSegmentSq(px, py, x1, y1, x2, y2));
  }

  public static boolean pointOnSegment(double px, double py, double x1, double y1, double x2, double y2, double epsilon) {
    double cross = (px - x1) * (y2 - y1) - (py - y1) * (x2 - x1);
    if (Math.abs(cross) > epsilon) return false;
    double dot = (px - x1) * (px - x2) + (py - y1) * (py - y2);
    return dot <= epsilon;
  }

  public static double[] raycastSegmentAABB(Segment2 segment, Rect rect) {
    if (segment == null || rect == null) return null;
    return raycastSegmentAABB(segment.x1, segment.y1, segment.x2, segment.y2, rect);
  }

  public static double[] raycastSegmentAABB(double sx, double sy, double ex, double ey, Rect r) {
    if (r == null) return null;
    double dx = ex - sx;
    double dy = ey - sy;
    double tmin = 0.0;
    double tmax = 1.0;

    if (dx != 0.0) {
      double tx1 = (r.left() - sx) / dx;
      double tx2 = (r.right() - sx) / dx;
      tmin = Math.max(tmin, Math.min(tx1, tx2));
      tmax = Math.min(tmax, Math.max(tx1, tx2));
    } else if (sx < r.left() || sx > r.right()) {
      return null;
    }

    if (dy != 0.0) {
      double ty1 = (r.top() - sy) / dy;
      double ty2 = (r.bottom() - sy) / dy;
      tmin = Math.max(tmin, Math.min(ty1, ty2));
      tmax = Math.min(tmax, Math.max(ty1, ty2));
    } else if (sy < r.top() || sy > r.bottom()) {
      return null;
    }

    if (tmax >= tmin && tmin >= 0.0 && tmin <= 1.0) {
      return new double[] { sx + tmin * dx, sy + tmin * dy, tmin };
    }
    return null;
  }

  public static double[] raycastRayAABB(Ray2 ray, Rect rect) {
    if (ray == null || rect == null) return null;
    return raycastRayAABB(ray.x, ray.y, ray.dx, ray.dy, rect);
  }

  public static double[] raycastRayAABB(double ox, double oy, double dx, double dy, Rect r) {
    if (r == null) return null;
    double tmin = 0.0;
    double tmax = Double.POSITIVE_INFINITY;

    if (dx != 0.0) {
      double tx1 = (r.left() - ox) / dx;
      double tx2 = (r.right() - ox) / dx;
      tmin = Math.max(tmin, Math.min(tx1, tx2));
      tmax = Math.min(tmax, Math.max(tx1, tx2));
    } else if (ox < r.left() || ox > r.right()) {
      return null;
    }

    if (dy != 0.0) {
      double ty1 = (r.top() - oy) / dy;
      double ty2 = (r.bottom() - oy) / dy;
      tmin = Math.max(tmin, Math.min(ty1, ty2));
      tmax = Math.min(tmax, Math.max(ty1, ty2));
    } else if (oy < r.top() || oy > r.bottom()) {
      return null;
    }

    if (tmax >= tmin && tmax >= 0.0) {
      double t = tmin < 0.0 ? 0.0 : tmin;
      return new double[] { ox + t * dx, oy + t * dy, t };
    }
    return null;
  }

  private static boolean segmentIntersectsAabb(
      double sx, double sy, double ex, double ey,
      double left, double top, double right, double bottom) {
    double dx = ex - sx;
    double dy = ey - sy;
    double tmin = 0.0;
    double tmax = 1.0;
    if (dx != 0.0) {
      double tx1 = (left - sx) / dx;
      double tx2 = (right - sx) / dx;
      tmin = Math.max(tmin, Math.min(tx1, tx2));
      tmax = Math.min(tmax, Math.max(tx1, tx2));
    } else if (sx < left || sx > right) {
      return false;
    }
    if (dy != 0.0) {
      double ty1 = (top - sy) / dy;
      double ty2 = (bottom - sy) / dy;
      tmin = Math.max(tmin, Math.min(ty1, ty2));
      tmax = Math.min(tmax, Math.max(ty1, ty2));
    } else if (sy < top || sy > bottom) {
      return false;
    }
    return tmax >= tmin && tmin >= 0.0 && tmin <= 1.0;
  }
}
