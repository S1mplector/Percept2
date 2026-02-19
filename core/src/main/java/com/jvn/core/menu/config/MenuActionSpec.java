package com.jvn.core.menu.config;

public record MenuActionSpec(MenuActionType type, String target) {
  public MenuActionSpec {
    type = type == null ? MenuActionType.NOOP : type;
    target = normalize(target);
  }

  public static MenuActionSpec noop() {
    return new MenuActionSpec(MenuActionType.NOOP, null);
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
    return new MenuActionSpec(MenuActionType.parse(action), target);
  }

  private static String normalize(String v) {
    if (v == null) return null;
    String t = v.trim();
    return t.isEmpty() ? null : t;
  }
}
