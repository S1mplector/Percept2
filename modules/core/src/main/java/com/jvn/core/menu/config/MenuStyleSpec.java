package com.jvn.core.menu.config;

public record MenuStyleSpec(
    String id,
    // --- Item text colors ---
    String itemColor,
    String itemSelectedColor,
    String itemHoverColor,
    String itemDisabledColor,
    // --- Item prefixes ---
    String itemPrefix,
    String itemSelectedPrefix,
    String itemDisabledPrefix,
    // --- Item font ---
    String itemFontFamily,
    String itemFontWeight,
    Integer itemFontSize,
    // --- Item text effects ---
    String itemShadowColor,
    Double itemShadowOffsetX,
    Double itemShadowOffsetY,
    Double itemOpacity,
    // --- Button skins ---
    String buttonAssetPath,
    String buttonSelectedAssetPath,
    String buttonHoverAssetPath,
    String buttonDisabledAssetPath,
    Double buttonTextPaddingX,
    Double buttonTextPaddingY,
    // --- Title styling ---
    String titleColor,
    String titleFontFamily,
    String titleFontWeight,
    Integer titleFontSize,
    String titleShadowColor,
    // --- Hints styling ---
    String hintsColor,
    String hintsFontFamily,
    String hintsFontWeight,
    Integer hintsFontSize,
    // --- Background ---
    String backgroundAssetPath,
    String backgroundColor,
    Double backgroundOpacity
) {
  public MenuStyleSpec {
    id = normalize(id, "default");
    itemColor = normalize(itemColor, null);
    itemSelectedColor = normalize(itemSelectedColor, null);
    itemHoverColor = normalize(itemHoverColor, null);
    itemDisabledColor = normalize(itemDisabledColor, null);
    itemPrefix = normalizePrefix(itemPrefix, null);
    itemSelectedPrefix = normalizePrefix(itemSelectedPrefix, null);
    itemDisabledPrefix = normalizePrefix(itemDisabledPrefix, null);
    itemFontFamily = normalize(itemFontFamily, null);
    itemFontWeight = normalize(itemFontWeight, null);
    if (itemFontSize != null && itemFontSize <= 0) itemFontSize = null;
    itemShadowColor = normalize(itemShadowColor, null);
    if (itemShadowOffsetX != null && !Double.isFinite(itemShadowOffsetX)) itemShadowOffsetX = null;
    if (itemShadowOffsetY != null && !Double.isFinite(itemShadowOffsetY)) itemShadowOffsetY = null;
    if (itemOpacity != null) itemOpacity = clamp(itemOpacity, 0.0, 1.0);
    buttonAssetPath = normalize(buttonAssetPath, null);
    buttonSelectedAssetPath = normalize(buttonSelectedAssetPath, null);
    buttonHoverAssetPath = normalize(buttonHoverAssetPath, null);
    buttonDisabledAssetPath = normalize(buttonDisabledAssetPath, null);
    if (buttonTextPaddingX != null && !Double.isFinite(buttonTextPaddingX)) buttonTextPaddingX = null;
    if (buttonTextPaddingY != null && !Double.isFinite(buttonTextPaddingY)) buttonTextPaddingY = null;
    titleColor = normalize(titleColor, null);
    titleFontFamily = normalize(titleFontFamily, null);
    titleFontWeight = normalize(titleFontWeight, null);
    if (titleFontSize != null && titleFontSize <= 0) titleFontSize = null;
    titleShadowColor = normalize(titleShadowColor, null);
    hintsColor = normalize(hintsColor, null);
    hintsFontFamily = normalize(hintsFontFamily, null);
    hintsFontWeight = normalize(hintsFontWeight, null);
    if (hintsFontSize != null && hintsFontSize <= 0) hintsFontSize = null;
    backgroundAssetPath = normalize(backgroundAssetPath, null);
    backgroundColor = normalize(backgroundColor, null);
    if (backgroundOpacity != null) backgroundOpacity = clamp(backgroundOpacity, 0.0, 1.0);
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }

  private static String normalizePrefix(String v, String def) {
    if (v == null) return def;
    return v.trim();
  }

  private static double clamp(double v, double min, double max) {
    if (v < min) return min;
    if (v > max) return max;
    return v;
  }
}
