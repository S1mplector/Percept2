package com.jvn.core.menu.config;

import java.util.List;

public record MenuScreenSpec(
    String id,
    String titleText,
    String subtitleText,
    String hintsText,
    String layoutId,
    String defaultStyleId,
    boolean wrapSelection,
    List<MenuItemSpec> items,
    String backgroundAsset
) {
  public MenuScreenSpec(String id, String titleText, String hintsText,
                        String layoutId, String defaultStyleId,
                        boolean wrapSelection, List<MenuItemSpec> items) {
    this(id, titleText, null, hintsText, layoutId, defaultStyleId, wrapSelection, items, null);
  }

  public MenuScreenSpec(String id, String titleText, String subtitleText, String hintsText,
                        String layoutId, String defaultStyleId,
                        boolean wrapSelection, List<MenuItemSpec> items) {
    this(id, titleText, subtitleText, hintsText, layoutId, defaultStyleId, wrapSelection, items, null);
  }

  public MenuScreenSpec {
    id = normalize(id, "main");
    titleText = normalizeText(titleText, null);
    subtitleText = normalizeText(subtitleText, null);
    hintsText = normalizeText(hintsText, null);
    layoutId = normalize(layoutId, "default");
    defaultStyleId = normalize(defaultStyleId, "default");
    items = items == null ? List.of() : List.copyOf(items);
    backgroundAsset = normalize(backgroundAsset, null);
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }

  private static String normalizeText(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? "" : t;
  }
}
