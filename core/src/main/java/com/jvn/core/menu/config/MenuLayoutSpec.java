package com.jvn.core.menu.config;

public record MenuLayoutSpec(
    String id,
    double listYStart,
    double lineHeight,
    double listWidthFactor,
    String textAlign,
    double hintsBottomMargin,
    Double titleY
) {
  public MenuLayoutSpec {
    id = normalize(id, "default");
    listYStart = sane(listYStart, 0.35);
    lineHeight = sane(lineHeight, 40.0);
    listWidthFactor = clamp(sane(listWidthFactor, 1.0), 0.1, 1.0);
    textAlign = normalize(textAlign, "center").toLowerCase();
    hintsBottomMargin = sane(hintsBottomMargin, 20.0);
    titleY = titleY != null ? sane(titleY, 60.0) : null;
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }

  private static double sane(double v, double def) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return def;
    return v;
  }

  private static double clamp(double v, double min, double max) {
    if (v < min) return min;
    if (v > max) return max;
    return v;
  }
}
