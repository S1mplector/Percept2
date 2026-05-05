package com.jvn.core.vn.ui;

/**
 * Clickable button hotspot placed on top of VN dialogue UI.
 *
 * <p>Bounds can be normalized either to the textbox rect (0..1) or to the
 * viewport (0..1), controlled by {@code coordinateSpace}.
 */
public record VnUiActionButtonSpec(
    String id,
    String label,
    String action,
    String target,
    boolean enabled,
    String assetPath,
    String hoverAssetPath,
    String disabledAssetPath,
    String boundsPoints,
    double x,
    double y,
    double width,
    double height,
    String coordinateSpace
) {
  public VnUiActionButtonSpec(
      String id,
      String label,
      String action,
      String target,
      boolean enabled,
      String assetPath,
      String hoverAssetPath,
      String disabledAssetPath,
      String boundsPoints,
      double x,
      double y,
      double width,
      double height
  ) {
    this(id, label, action, target, enabled, assetPath, hoverAssetPath, disabledAssetPath, boundsPoints, x, y, width, height, "textbox");
  }

  public VnUiActionButtonSpec {
    id = normalize(id, "button");
    label = normalize(label, id);
    action = normalize(action, "noop");
    target = normalize(target, null);
    assetPath = normalize(assetPath, null);
    hoverAssetPath = normalize(hoverAssetPath, null);
    disabledAssetPath = normalize(disabledAssetPath, null);
    boundsPoints = normalize(boundsPoints, null);
    coordinateSpace = normalizeCoordinateSpace(coordinateSpace);
    x = clamp01(sane(x, 0.0));
    y = clamp01(sane(y, 0.0));
    width = clamp(sane(width, 0.12), 0.01, 1.0);
    height = clamp(sane(height, 0.25), 0.01, 1.0);
    if (x + width > 1.0) width = Math.max(0.01, 1.0 - x);
    if (y + height > 1.0) height = Math.max(0.01, 1.0 - y);
  }

  public static VnUiActionButtonSpec defaults(String id) {
    String safe = normalize(id, "button");
    return new VnUiActionButtonSpec(
        safe,
        safe,
        "noop",
        null,
        true,
        null,
        null,
        null,
        null,
        0.0,
        0.0,
        0.12,
        0.25,
        "textbox"
    );
  }

  public boolean viewportSpace() {
    return "viewport".equalsIgnoreCase(coordinateSpace);
  }

  public static String normalizeCoordinateSpace(String value) {
    String normalized = normalize(value, "textbox");
    if (normalized == null) return "textbox";
    String lower = normalized.trim().toLowerCase();
    if ("viewport".equals(lower) || "screen".equals(lower) || "global".equals(lower)) return "viewport";
    return "textbox";
  }

  private static String normalize(String value, String def) {
    if (value == null) return def;
    String t = value.trim();
    return t.isEmpty() ? def : t;
  }

  private static double sane(double value, double def) {
    if (Double.isNaN(value) || Double.isInfinite(value)) return def;
    return value;
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
