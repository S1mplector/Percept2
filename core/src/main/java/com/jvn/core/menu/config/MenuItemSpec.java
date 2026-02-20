package com.jvn.core.menu.config;

public record MenuItemSpec(
    String id,
    String label,
    String styleId,
    String iconPath,
    boolean enabled,
    MenuActionSpec action,
    String buttonAssetPath,
    String buttonSelectedAssetPath,
    String buttonDisabledAssetPath,
    Double boundsX,
    Double boundsY,
    Double boundsWidth,
    Double boundsHeight
) {
  public MenuItemSpec {
    id = normalize(id, "item");
    label = normalize(label, null);
    styleId = normalize(styleId, null);
    iconPath = normalize(iconPath, null);
    action = action == null ? MenuActionSpec.noop() : action;
    buttonAssetPath = normalize(buttonAssetPath, null);
    buttonSelectedAssetPath = normalize(buttonSelectedAssetPath, null);
    buttonDisabledAssetPath = normalize(buttonDisabledAssetPath, null);
    if (boundsX != null && !Double.isFinite(boundsX)) boundsX = null;
    if (boundsY != null && !Double.isFinite(boundsY)) boundsY = null;
    if (boundsWidth != null && !Double.isFinite(boundsWidth)) boundsWidth = null;
    if (boundsHeight != null && !Double.isFinite(boundsHeight)) boundsHeight = null;
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
