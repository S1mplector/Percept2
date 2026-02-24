package com.jvn.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utilities for encoding/decoding normalized polygon points and hit-testing.
 *
 * <p>Encoding format: {@code x1,y1;x2,y2;...} where each coordinate is
 * normalized (usually 0..1, but values are not strictly clamped here).
 */
public final class BoundsPointCodec {
  private BoundsPointCodec() {
  }

  public record Point(double x, double y) {
  }

  public static String encode(List<Point> points) {
    if (points == null || points.isEmpty()) return "";
    StringBuilder out = new StringBuilder();
    for (Point point : points) {
      if (point == null || !Double.isFinite(point.x()) || !Double.isFinite(point.y())) continue;
      if (out.length() > 0) out.append(';');
      out.append(format(point.x())).append(',').append(format(point.y()));
    }
    return out.toString();
  }

  public static List<Point> parse(String encoded) {
    List<Point> out = new ArrayList<>();
    if (encoded == null || encoded.isBlank()) return out;
    String[] pairs = encoded.split(";");
    for (String pair : pairs) {
      if (pair == null || pair.isBlank()) continue;
      String[] xy = pair.trim().split(",");
      if (xy.length != 2) continue;
      Double x = parseDouble(xy[0]);
      Double y = parseDouble(xy[1]);
      if (x == null || y == null) continue;
      out.add(new Point(x, y));
    }
    return out;
  }

  /**
   * Hit-tests a point against a polygon whose vertices are normalized relative
   * to the provided rectangle.
   */
  public static boolean containsInRect(List<Point> localPoints, double rectX, double rectY, double rectW, double rectH, double px, double py) {
    if (localPoints == null || localPoints.size() < 3) return false;
    List<Point> absolute = new ArrayList<>(localPoints.size());
    for (Point point : localPoints) {
      if (point == null) continue;
      absolute.add(new Point(
          rectX + rectW * point.x(),
          rectY + rectH * point.y()
      ));
    }
    return contains(absolute, px, py);
  }

  /** Standard ray-casting point-in-polygon test. */
  public static boolean contains(List<Point> polygon, double px, double py) {
    if (polygon == null || polygon.size() < 3) return false;
    boolean inside = false;
    int n = polygon.size();
    for (int i = 0, j = n - 1; i < n; j = i++) {
      Point pi = polygon.get(i);
      Point pj = polygon.get(j);
      if (pi == null || pj == null) continue;
      double xi = pi.x();
      double yi = pi.y();
      double xj = pj.x();
      double yj = pj.y();

      boolean intersects = ((yi > py) != (yj > py))
          && (px < (xj - xi) * (py - yi) / ((yj - yi) + 1e-12) + xi);
      if (intersects) inside = !inside;
    }
    return inside;
  }

  private static String format(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static Double parseDouble(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      double value = Double.parseDouble(raw.trim());
      return Double.isFinite(value) ? value : null;
    } catch (Exception ignored) {
      return null;
    }
  }
}

