package com.jvn.core.vn.ui;

import java.util.List;

/**
 * Properties-backed reactive overlay screen definition.
 */
public record VnReactiveScreenSpec(
    String id,
    String title,
    String text,
    String visibleIf,
    double x,
    double y,
    double width,
    double height,
    boolean modal,
    boolean dimBackground,
    boolean dismissOnAdvance,
    boolean callScreen,
    long timerMs,
    String timerAction,
    String timerTarget,
    String returnKey,
    List<Button> buttons,
    VnFacetSpec facet
) {
  public VnReactiveScreenSpec {
    id = normalize(id, "screen");
    title = normalize(title, id);
    text = normalize(text, "");
    visibleIf = normalize(visibleIf, "");
    x = clamp01(sane(x, 0.18));
    y = clamp01(sane(y, 0.18));
    width = clamp(sane(width, 0.64), 0.05, 1.0);
    height = clamp(sane(height, 0.42), 0.05, 1.0);
    timerMs = Math.max(0L, timerMs);
    timerAction = normalize(timerAction, callScreen ? "return" : "hide");
    timerTarget = normalize(timerTarget, "");
    returnKey = normalize(returnKey, "screen.return." + id);
    buttons = buttons == null ? List.of() : List.copyOf(buttons);
    facet = facet == null ? new VnFacetSpec("root", List.of()) : facet;
  }

  /** Backward-compatible constructor for legacy reactive screen definitions. */
  public VnReactiveScreenSpec(
      String id, String title, String text, String visibleIf,
      double x, double y, double width, double height,
      boolean modal, boolean dimBackground, boolean dismissOnAdvance,
      boolean callScreen, long timerMs, String timerAction, String timerTarget,
      String returnKey, List<Button> buttons
  ) {
    this(id, title, text, visibleIf, x, y, width, height, modal, dimBackground,
        dismissOnAdvance, callScreen, timerMs, timerAction, timerTarget,
        returnKey, buttons, new VnFacetSpec("root", List.of()));
  }

  public record Button(
      String id,
      String label,
      String action,
      String target,
      boolean enabled,
      String enabledIf,
      String visibleIf,
      double x,
      double y,
      double width,
      double height,
      String coordinateSpace
  ) {
    public Button {
      id = normalize(id, "button");
      label = normalize(label, id);
      action = normalize(action, "noop");
      target = normalize(target, "");
      enabledIf = normalize(enabledIf, "");
      visibleIf = normalize(visibleIf, "");
      x = clamp01(sane(x, 0.0));
      y = clamp01(sane(y, 0.0));
      width = clamp(sane(width, 0.25), 0.01, 1.0);
      height = clamp(sane(height, 0.18), 0.01, 1.0);
      coordinateSpace = VnOverlayButtonSpec.normalizeCoordinateSpace(coordinateSpace);
    }
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
