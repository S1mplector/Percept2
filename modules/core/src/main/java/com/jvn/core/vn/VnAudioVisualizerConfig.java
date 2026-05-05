package com.jvn.core.vn;

/**
 * Shared audio visualizer variable names and normalization helpers.
 */
public final class VnAudioVisualizerConfig {
  private VnAudioVisualizerConfig() {
  }

  public static final String VAR_ENABLED = "ui.audioVisualizer";
  public static final String VAR_BARS = "ui.audioVisualizerBars";
  public static final String VAR_COLOR = "ui.audioVisualizerColor";
  public static final String VAR_ACCENT = "ui.audioVisualizerAccent";
  public static final String VAR_ALPHA = "ui.audioVisualizerAlpha";
  public static final String VAR_GLOW = "ui.audioVisualizerGlow";
  public static final String VAR_STYLE = "ui.audioVisualizerStyle";
  public static final String VAR_HEIGHT = "ui.audioVisualizerHeight";
  public static final String VAR_Z = "ui.audioVisualizerZ";

  public static final int MIN_BARS = 8;
  public static final int MAX_BARS = 96;
  public static final int DEFAULT_BARS = 48;

  public static final double MIN_ALPHA = 0.10;
  public static final double MAX_ALPHA = 1.00;
  public static final double DEFAULT_ALPHA = 0.86;

  public static final double MIN_HEIGHT = 0.20;
  public static final double MAX_HEIGHT = 1.00;
  public static final double DEFAULT_HEIGHT = 0.84;

  public static final int DEFAULT_Z = -100;

  public static final String STYLE_DYNAMIC = "dynamic";
  public static final String STYLE_MINIMAL = "minimal";
  public static final String DEFAULT_STYLE = STYLE_DYNAMIC;

  public static final String AUTO = "auto";
  public static final long STALE_NS = 700_000_000L;

  public static int clampBars(int value) {
    return Math.max(MIN_BARS, Math.min(MAX_BARS, value));
  }

  public static double clampAlpha(double value) {
    return Math.max(MIN_ALPHA, Math.min(MAX_ALPHA, value));
  }

  public static double clampHeight(double value) {
    return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, value));
  }

  public static String normalizeStyle(String raw) {
    if (raw == null || raw.isBlank()) return DEFAULT_STYLE;
    String style = raw.trim().toLowerCase();
    if (STYLE_MINIMAL.equals(style)) return STYLE_MINIMAL;
    return STYLE_DYNAMIC;
  }

  public static boolean isTruthy(Object value) {
    if (value == null) return false;
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.doubleValue() != 0.0;
    if (value instanceof String s) {
      String t = s.trim().toLowerCase();
      return "1".equals(t) || "true".equals(t) || "on".equals(t) || "yes".equals(t);
    }
    return false;
  }

  public static Boolean parseBooleanToken(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return switch (raw.trim().toLowerCase()) {
      case "on", "show", "true", "1", "yes" -> Boolean.TRUE;
      case "off", "hide", "false", "0", "no" -> Boolean.FALSE;
      default -> null;
    };
  }

  public static boolean isAutoToken(String raw) {
    return raw == null || raw.isBlank() || AUTO.equalsIgnoreCase(raw.trim());
  }
}
