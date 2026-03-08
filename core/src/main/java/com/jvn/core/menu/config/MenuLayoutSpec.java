package com.jvn.core.menu.config;

public record MenuLayoutSpec(
    String id,
    double listYStart,
    double lineHeight,
    double listWidthFactor,
    String textAlign,
    double hintsBottomMargin,
    Double titleY,
    double subtitleGap,
    Double listXCenter,
    Double titleX,
    Integer maxVisibleItems,
    String titleAlign,
    String hintsAlign,
    Double hintsX
) {
  public MenuLayoutSpec(String id, double listYStart, double lineHeight,
                        double listWidthFactor, String textAlign,
                        double hintsBottomMargin, Double titleY) {
    this(id, listYStart, lineHeight, listWidthFactor, textAlign, hintsBottomMargin, titleY, 12.0, null, null, null, "center", "center", null);
  }

  public MenuLayoutSpec(String id, double listYStart, double lineHeight,
                        double listWidthFactor, String textAlign,
                        double hintsBottomMargin, Double titleY,
                        Double listXCenter, Double titleX, Integer maxVisibleItems) {
    this(id, listYStart, lineHeight, listWidthFactor, textAlign, hintsBottomMargin, titleY, 12.0, listXCenter, titleX, maxVisibleItems, "center", "center", null);
  }

  public MenuLayoutSpec(String id, double listYStart, double lineHeight,
                        double listWidthFactor, String textAlign,
                        double hintsBottomMargin, Double titleY,
                        double subtitleGap,
                        Double listXCenter, Double titleX, Integer maxVisibleItems) {
    this(id, listYStart, lineHeight, listWidthFactor, textAlign, hintsBottomMargin, titleY, subtitleGap, listXCenter, titleX, maxVisibleItems, "center", "center", null);
  }

  public MenuLayoutSpec {
    id = normalize(id, "default");
    listYStart = sane(listYStart, 0.35);
    lineHeight = sane(lineHeight, 40.0);
    listWidthFactor = clamp(sane(listWidthFactor, 1.0), 0.1, 1.0);
    textAlign = normalizeAlign(textAlign, "center");
    hintsBottomMargin = sane(hintsBottomMargin, 20.0);
    titleY = titleY != null ? sane(titleY, 60.0) : null;
    subtitleGap = Math.max(0.0, sane(subtitleGap, 12.0));
    if (listXCenter != null) listXCenter = clamp(sane(listXCenter, 0.5), 0.0, 1.0);
    if (titleX != null) titleX = clamp(sane(titleX, 0.5), 0.0, 1.0);
    if (maxVisibleItems != null && maxVisibleItems <= 0) maxVisibleItems = null;
    titleAlign = normalizeAlign(titleAlign, "center");
    hintsAlign = normalizeAlign(hintsAlign, "center");
    if (hintsX != null) hintsX = clamp(sane(hintsX, 0.5), 0.0, 1.0);
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }

  private static String normalizeAlign(String v, String def) {
    String normalized = normalize(v, def).toLowerCase();
    return switch (normalized) {
      case "left", "center", "right" -> normalized;
      default -> def;
    };
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
