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
    Integer itemFontSize,
    String buttonAssetPath,
    String buttonSelectedAssetPath,
    String buttonDisabledAssetPath,
    Double buttonTextPaddingX,
    Double buttonTextPaddingY
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
    buttonAssetPath = normalize(buttonAssetPath, null);
    buttonSelectedAssetPath = normalize(buttonSelectedAssetPath, null);
    buttonDisabledAssetPath = normalize(buttonDisabledAssetPath, null);
    if (buttonTextPaddingX != null && !Double.isFinite(buttonTextPaddingX)) buttonTextPaddingX = null;
    if (buttonTextPaddingY != null && !Double.isFinite(buttonTextPaddingY)) buttonTextPaddingY = null;
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
