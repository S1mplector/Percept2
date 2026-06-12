package com.jvn.core.math;

import java.util.Arrays;

/**
 * Mutable polygon stored as flattened x/y coordinates.
 */
public class Polygon2 implements Shape2D {
  private double[] xy;
  private int pointCount;

  public Polygon2(double... xy) {
    set(xy);
  }

  public static Polygon2 of(double... xy) {
    return new Polygon2(xy);
  }

  public Polygon2 set(double... xy) {
    if (xy == null || xy.length < 6 || xy.length % 2 != 0) {
      this.xy = new double[0];
      this.pointCount = 0;
      return this;
    }
    this.xy = Arrays.copyOf(xy, xy.length);
    this.pointCount = xy.length / 2;
    return this;
  }

  public Polygon2 copy() {
    return new Polygon2(xy);
  }

  public int pointCount() {
    return pointCount;
  }

  public boolean isEmpty() {
    return pointCount == 0;
  }

  public double x(int index) {
    checkIndex(index);
    return xy[index * 2];
  }

  public double y(int index) {
    checkIndex(index);
    return xy[index * 2 + 1];
  }

  public double[] toArray() {
    return Arrays.copyOf(xy, xy.length);
  }

  @Override
  public boolean contains(double x, double y) {
    if (pointCount < 3) return false;
    boolean inside = false;
    int j = pointCount - 1;
    for (int i = 0; i < pointCount; i++) {
      double xi = x(i);
      double yi = y(i);
      double xj = x(j);
      double yj = y(j);
      if (Geometry2D.pointOnSegment(x, y, xj, yj, xi, yi, 1e-9)) return true;
      boolean crosses = ((yi > y) != (yj > y))
          && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
      if (crosses) inside = !inside;
      j = i;
    }
    return inside;
  }

  @Override
  public Rect bounds(Rect out) {
    Rect target = out == null ? new Rect() : out;
    if (pointCount == 0) {
      target.x = 0;
      target.y = 0;
      target.w = 0;
      target.h = 0;
      return target;
    }
    double minX = xy[0];
    double maxX = xy[0];
    double minY = xy[1];
    double maxY = xy[1];
    for (int i = 1; i < pointCount; i++) {
      double px = x(i);
      double py = y(i);
      minX = Math.min(minX, px);
      minY = Math.min(minY, py);
      maxX = Math.max(maxX, px);
      maxY = Math.max(maxY, py);
    }
    target.x = minX;
    target.y = minY;
    target.w = maxX - minX;
    target.h = maxY - minY;
    return target;
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= pointCount) {
      throw new IndexOutOfBoundsException("point index " + index + " outside 0.." + (pointCount - 1));
    }
  }
}
