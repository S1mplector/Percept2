package com.jvn.core.vn.ui;

import java.util.Locale;
import java.util.Set;

/**
 * Shared normalization and validation helpers for textbox action buttons.
 */
public final class VnUiActionButtonActions {
  private static final Set<String> SUPPORTED_ACTIONS = Set.of(
      "advance",
      "rollback",
      "back",
      "quick_save",
      "save_quick",
      "quick_load",
      "load_quick",
      "save_slots",
      "open_save_slots",
      "load_slots",
      "open_load_slots",
      "toggle_history",
      "history",
      "toggle_skip",
      "skip",
      "toggle_auto",
      "auto",
      "toggle_ui",
      "ui",
      "save_menu",
      "open_save_menu",
      "menu_save",
      "load_menu",
      "open_load_menu",
      "menu_load",
      "settings_menu",
      "open_settings_menu",
      "menu_settings",
      "main_menu",
      "open_main_menu",
      "menu_main",
      "open_menu",
      "menu_open",
      "quit",
      "quit_game",
      "close_game",
      "exit",
      "noop",
      "none"
  );

  private VnUiActionButtonActions() {
  }

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) return "noop";
    String trimmed = raw.trim();
    int colon = trimmed.indexOf(':');
    String action = colon > 0 ? trimmed.substring(0, colon) : trimmed;
    return action.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
  }

  public static String inlineTarget(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String trimmed = raw.trim();
    int colon = trimmed.indexOf(':');
    if (colon <= 0 || colon >= trimmed.length() - 1) return null;
    String target = trimmed.substring(colon + 1).trim();
    return target.isEmpty() ? null : target;
  }

  public static boolean isSupported(String raw) {
    return SUPPORTED_ACTIONS.contains(normalize(raw));
  }

  public static boolean requiresTarget(String raw) {
    return switch (normalize(raw)) {
      case "open_menu", "menu_open" -> true;
      default -> false;
    };
  }
}
