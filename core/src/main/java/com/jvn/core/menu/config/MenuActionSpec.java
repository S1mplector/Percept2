package com.jvn.core.menu.config;

import java.util.Locale;

public record MenuActionSpec(MenuActionType type, String target, String rawAction) {
  public MenuActionSpec {
    type = type == null ? MenuActionType.NOOP : type;
    target = normalize(target);
    rawAction = normalize(rawAction);
  }

  public MenuActionSpec(MenuActionType type, String target) {
    this(type, target, canonicalActionName(type));
  }

  public static MenuActionSpec noop() {
    return new MenuActionSpec(MenuActionType.NOOP, null, "noop");
  }

  public static MenuActionSpec parse(String rawAction, String rawTarget) {
    String action = rawAction == null ? "" : rawAction.trim();
    String target = normalize(rawTarget);
    if (!action.isEmpty() && target == null) {
      int colon = action.indexOf(':');
      if (colon > 0 && colon < action.length() - 1) {
        target = normalize(action.substring(colon + 1));
        action = action.substring(0, colon);
      }
    }
    MenuActionType parsedType = MenuActionType.parse(action);
    return new MenuActionSpec(parsedType, target, action);
  }

  public String actionKey() {
    return rawAction != null ? rawAction : canonicalActionName(type);
  }

  public boolean isCustomAction() {
    if (rawAction == null || rawAction.isBlank()) return false;
    if (type != MenuActionType.NOOP) return false;
    String v = rawAction.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return !("noop".equals(v) || "no_op".equals(v) || "none".equals(v));
  }

  private static String canonicalActionName(MenuActionType type) {
    if (type == null) return "noop";
    return switch (type) {
      case NEW_GAME -> "new_game";
      case LOAD_MENU -> "load_menu";
      case SAVE_MENU -> "save_menu";
      case SETTINGS_MENU -> "settings_menu";
      case MAIN_MENU -> "main_menu";
      case OPEN_MENU -> "open_menu";
      case RUN_SCRIPT -> "run_script";
      case BACK -> "back";
      case QUIT -> "quit";
      case NOOP -> "noop";
    };
  }

  private static String normalize(String v) {
    if (v == null) return null;
    String t = v.trim();
    return t.isEmpty() ? null : t;
  }
}
