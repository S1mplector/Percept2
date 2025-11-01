package com.jvn.core.physics;

import com.jvn.core.math.Circle;
import com.jvn.core.math.Rect;

public final class Collision2D {
  private Collision2D() {}

  public static boolean intersects(Rect a, Rect b) {
    return a.intersects(b);
  }

  public static boolean intersects(Circle a, Circle b) {
    double dx = a.x - b.x;
    double dy = a.y - b.y;
    double rr = a.r + b.r;
    return dx * dx + dy * dy <= rr * rr;
  }

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
