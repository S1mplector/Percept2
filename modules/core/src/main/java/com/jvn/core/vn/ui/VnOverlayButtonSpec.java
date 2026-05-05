package com.jvn.core.vn.ui;

public record VnOverlayButtonSpec(
    String id,
    String screenId,
    String label,
    String action,
    String target,
    boolean enabled,
    double x,
    double y,
    double width,
    double height,
    String coordinateSpace
) {
  public VnOverlayButtonSpec {
    id = normalize(id, "button");
    screenId = normalize(screenId, "");
    label = normalize(label, id);
    action = normalize(action, "noop");
    target = normalize(target, "");
    coordinateSpace = normalizeCoordinateSpace(coordinateSpace);
    x = clamp01(sane(x, 0.0));
    y = clamp01(sane(y, 0.0));
    width = clamp(sane(width, 0.25), 0.01, 1.0);
    height = clamp(sane(height, 0.18), 0.01, 1.0);
  }

  public boolean viewportSpace() {
    return "viewport".equalsIgnoreCase(coordinateSpace);
  }

  public static String normalizeCoordinateSpace(String raw) {
    String normalized = normalize(raw, "screen");
    if ("viewport".equalsIgnoreCase(normalized) || "screen_global".equalsIgnoreCase(normalized)) {
      return "viewport";
    }
    return "screen";
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? fallback : trimmed;
  }

  private static double sane(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }

  private static double clamp01(double value) {
    return clamp(value, 0.0, 1.0);
  }

  private static double clamp(double value, double min, double max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }
}
