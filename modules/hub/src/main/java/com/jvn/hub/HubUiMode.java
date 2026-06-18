package com.jvn.hub;

enum HubUiMode {
  CLASSIC,
  FX;

  static HubUiMode parse(String value) {
    if (value == null) return null;
    String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
    return switch (normalized) {
      case "classic", "swing", "old" -> CLASSIC;
      case "fx", "javafx", "new", "modern" -> FX;
      default -> null;
    };
  }
}
