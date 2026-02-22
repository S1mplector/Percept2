package com.jvn.core.menu.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
    Double boundsHeight,
    boolean slotPreviewEnabled,
    String slotPreviewPlaceholderAssetPath,
    String slotPreviewFrameAssetPath,
    Double slotPreviewX,
    Double slotPreviewY,
    Double slotPreviewWidth,
    Double slotPreviewHeight,
    Map<String, String> extras
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
    slotPreviewPlaceholderAssetPath = normalize(slotPreviewPlaceholderAssetPath, null);
    slotPreviewFrameAssetPath = normalize(slotPreviewFrameAssetPath, null);
    if (slotPreviewX != null && !Double.isFinite(slotPreviewX)) slotPreviewX = null;
    if (slotPreviewY != null && !Double.isFinite(slotPreviewY)) slotPreviewY = null;
    if (slotPreviewWidth != null && !Double.isFinite(slotPreviewWidth)) slotPreviewWidth = null;
    if (slotPreviewHeight != null && !Double.isFinite(slotPreviewHeight)) slotPreviewHeight = null;
    extras = extras == null ? Collections.emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
  }

  public MenuItemSpec(
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
    this(
        id,
        label,
        styleId,
        iconPath,
        enabled,
        action,
        buttonAssetPath,
        buttonSelectedAssetPath,
        buttonDisabledAssetPath,
        boundsX,
        boundsY,
        boundsWidth,
        boundsHeight,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    );
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
