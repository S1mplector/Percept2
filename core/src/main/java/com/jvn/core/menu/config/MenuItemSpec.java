package com.jvn.core.menu.config;

public record MenuItemSpec(
    String id,
    String label,
    String styleId,
    String iconPath,
    boolean enabled,
    MenuActionSpec action
) {
  public MenuItemSpec {
    id = normalize(id, "item");
    label = normalize(label, null);
    styleId = normalize(styleId, null);
    iconPath = normalize(iconPath, null);
    action = action == null ? MenuActionSpec.noop() : action;
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
