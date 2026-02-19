package com.jvn.core.menu.config;

import java.util.List;

public record MenuScreenSpec(
    String id,
    String titleText,
    String hintsText,
    String layoutId,
    String defaultStyleId,
    boolean wrapSelection,
    List<MenuItemSpec> items
) {
  public MenuScreenSpec {
    id = normalize(id, "main");
    titleText = normalize(titleText, null);
    hintsText = normalize(hintsText, null);
    layoutId = normalize(layoutId, "default");
    defaultStyleId = normalize(defaultStyleId, "default");
    items = items == null ? List.of() : List.copyOf(items);
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
