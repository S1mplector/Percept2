package com.jvn.core.vn;

/**
 * Horizontal anchor presets for bubble dialogue placement.
 */
public enum BubbleAnchor {
  AUTO("auto"),
  LEFT("left"),
  CENTER("center"),
  RIGHT("right");

  private final String token;

  BubbleAnchor(String token) {
    this.token = token;
  }

  public String token() {
    return token;
  }

  public static BubbleAnchor fromToken(String raw) {
    if (raw == null || raw.isBlank()) return AUTO;
    return switch (raw.trim().toLowerCase()) {
      case "left", "l", "far_left", "farleft" -> LEFT;
      case "center", "centre", "c", "middle" -> CENTER;
      case "right", "r", "far_right", "farright" -> RIGHT;
      default -> AUTO;
    };
  }

  public static BubbleAnchor fromVariable(Object value) {
    if (value instanceof BubbleAnchor anchor) return anchor;
    return fromToken(value == null ? null : String.valueOf(value));
  }
}
