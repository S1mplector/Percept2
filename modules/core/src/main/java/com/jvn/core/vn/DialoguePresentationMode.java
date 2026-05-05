package com.jvn.core.vn;

/**
 * Supported dialogue presentation modes.
 */
public enum DialoguePresentationMode {
  STANDARD("standard"),
  NVL("nvl"),
  BUBBLE("bubble");

  private final String token;

  DialoguePresentationMode(String token) {
    this.token = token;
  }

  public String token() {
    return token;
  }

  public static DialoguePresentationMode fromToken(String raw) {
    if (raw == null || raw.isBlank()) return STANDARD;
    return switch (raw.trim().toLowerCase()) {
      case "nvl" -> NVL;
      case "bubble" -> BUBBLE;
      case "standard", "normal", "say", "adv" -> STANDARD;
      default -> STANDARD;
    };
  }

  public static DialoguePresentationMode fromVariable(Object value) {
    if (value instanceof DialoguePresentationMode mode) return mode;
    return fromToken(value == null ? null : String.valueOf(value));
  }
}
