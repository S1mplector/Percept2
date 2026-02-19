package com.jvn.core.menu.config;

public record MenuStyleSpec(
    String id,
    String itemColor,
    String itemSelectedColor,
    String itemDisabledColor,
    String itemPrefix,
    String itemSelectedPrefix,
    String itemDisabledPrefix,
    String itemFontFamily,
    String itemFontWeight,
    Integer itemFontSize
) {
  public MenuStyleSpec {
    id = normalize(id, "default");
    itemColor = normalize(itemColor, null);
    itemSelectedColor = normalize(itemSelectedColor, null);
    itemDisabledColor = normalize(itemDisabledColor, null);
    itemPrefix = normalize(itemPrefix, null);
    itemSelectedPrefix = normalize(itemSelectedPrefix, null);
    itemDisabledPrefix = normalize(itemDisabledPrefix, null);
    itemFontFamily = normalize(itemFontFamily, null);
    itemFontWeight = normalize(itemFontWeight, null);
    if (itemFontSize != null && itemFontSize <= 0) itemFontSize = null;
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
