package com.jvn.core.menu.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MenuProfileValidator {
  private MenuProfileValidator() {}

  public static List<String> validate(MenuProfile profile) {
    List<String> warnings = new ArrayList<>();
    if (profile == null) {
      warnings.add("Menu profile is null");
      return warnings;
    }

    if (!profile.hasScreen(profile.defaultScreenId())) {
      warnings.add("Default menu '" + profile.defaultScreenId() + "' is not explicitly defined; fallback will be used");
    }

    for (MenuLayoutSpec layout : profile.layouts().values()) {
      if (layout == null) continue;
      String layoutId = layout.id();
      if (layout.listYStart() < 0.0) {
        warnings.add("Layout '" + layoutId + "' has negative listYStart");
      }
      if (layout.lineHeight() <= 0.0) {
        warnings.add("Layout '" + layoutId + "' has non-positive lineHeight");
      }
      if (layout.listWidthFactor() < 0.1 || layout.listWidthFactor() > 1.0) {
        warnings.add("Layout '" + layoutId + "' has listWidthFactor outside 0.1..1.0");
      }
      String align = normalize(layout.textAlign());
      if (!Set.of("left", "center", "right").contains(align)) {
        warnings.add("Layout '" + layoutId + "' has invalid textAlign '" + layout.textAlign() + "'");
      }
      String titleAlign = normalize(layout.titleAlign());
      if (!Set.of("left", "center", "right").contains(titleAlign)) {
        warnings.add("Layout '" + layoutId + "' has invalid titleAlign '" + layout.titleAlign() + "'");
      }
      String hintsAlign = normalize(layout.hintsAlign());
      if (!Set.of("left", "center", "right").contains(hintsAlign)) {
        warnings.add("Layout '" + layoutId + "' has invalid hintsAlign '" + layout.hintsAlign() + "'");
      }
      if (layout.hintsBottomMargin() < 0.0) {
        warnings.add("Layout '" + layoutId + "' has negative hintsBottomMargin");
      }
      if (layout.titleY() != null && layout.titleY() < 0.0) {
        warnings.add("Layout '" + layoutId + "' has negative titleY");
      }
      if (layout.subtitleGap() < 0.0) {
        warnings.add("Layout '" + layoutId + "' has negative subtitleGap");
      }
    }

    for (MenuStyleSpec style : profile.styles().values()) {
      if (style == null) continue;
      String styleId = style.id();
      if (style.itemOpacity() != null && (style.itemOpacity() < 0.0 || style.itemOpacity() > 1.0)) {
        warnings.add("Style '" + styleId + "' has itemOpacity outside 0..1");
      }
      if (style.backgroundOpacity() != null && (style.backgroundOpacity() < 0.0 || style.backgroundOpacity() > 1.0)) {
        warnings.add("Style '" + styleId + "' has backgroundOpacity outside 0..1");
      }
    }

    for (String screenId : profile.screenIds()) {
      MenuScreenSpec screen = profile.screen(screenId);
      if (screen.items().isEmpty()) {
        warnings.add("Menu '" + screenId + "' has no items");
      }
      if (!profile.hasLayout(screen.layoutId())) {
        warnings.add("Menu '" + screenId + "' references unknown layout '" + screen.layoutId() + "'");
      }
      if (!profile.hasStyle(screen.defaultStyleId())) {
        warnings.add("Menu '" + screenId + "' references unknown default style '" + screen.defaultStyleId() + "'");
      }
      Set<String> seenItemIds = new LinkedHashSet<>();
      for (MenuItemSpec item : screen.items()) {
        if (item == null) continue;
        if (!seenItemIds.add(item.id())) {
          warnings.add("Menu '" + screenId + "' has duplicate item id '" + item.id() + "'");
        }
        if (item.styleId() != null && !profile.hasStyle(item.styleId())) {
          warnings.add("Menu '" + screenId + "' item '" + item.id() + "' references unknown style '" + item.styleId() + "'");
        }
        MenuActionSpec action = item.action();
        if (action == null) continue;
        if (action.type() == MenuActionType.OPEN_MENU) {
          String target = action.target();
          if (target == null || target.isBlank()) {
            warnings.add("Menu '" + screenId + "' item '" + item.id() + "' has OPEN_MENU without target");
          } else if (!profile.hasScreen(target)) {
            warnings.add("Menu '" + screenId + "' item '" + item.id() + "' targets unknown menu '" + target + "'");
          }
        }
        if (action.type() == MenuActionType.RUN_SCRIPT) {
          String target = action.target();
          if (target == null || target.isBlank()) {
            warnings.add("Menu '" + screenId + "' item '" + item.id() + "' has RUN_SCRIPT without script target");
          }
        }
        if (!action.isCustomAction()
            && action.type() != MenuActionType.OPEN_MENU
            && action.type() != MenuActionType.RUN_SCRIPT
            && action.target() != null
            && !action.target().isBlank()) {
          warnings.add("Menu '" + screenId + "' item '" + item.id() + "' defines unused action target '" + action.target() + "'");
        }

        int boundsCount = countDefined(item.boundsX(), item.boundsY(), item.boundsWidth(), item.boundsHeight());
        if (boundsCount > 0 && boundsCount < 4) {
          warnings.add("Menu '" + screenId + "' item '" + item.id() + "' has partial bounds; X/Y/Width/Height must be set together");
        }
        if (item.boundsWidth() != null && item.boundsWidth() <= 0.0) {
          warnings.add("Menu '" + screenId + "' item '" + item.id() + "' has non-positive boundsWidth");
        }
        if (item.boundsHeight() != null && item.boundsHeight() <= 0.0) {
          warnings.add("Menu '" + screenId + "' item '" + item.id() + "' has non-positive boundsHeight");
        }

        int slotBoundsCount = countDefined(item.slotPreviewX(), item.slotPreviewY(), item.slotPreviewWidth(), item.slotPreviewHeight());
        if (slotBoundsCount > 0 && slotBoundsCount < 4) {
          warnings.add("Menu '" + screenId + "' item '" + item.id()
              + "' has partial slot preview bounds; X/Y/Width/Height must be set together");
        }
        if (item.slotPreviewWidth() != null && item.slotPreviewWidth() <= 0.0) {
          warnings.add("Menu '" + screenId + "' item '" + item.id() + "' has non-positive slotPreviewWidth");
        }
        if (item.slotPreviewHeight() != null && item.slotPreviewHeight() <= 0.0) {
          warnings.add("Menu '" + screenId + "' item '" + item.id() + "' has non-positive slotPreviewHeight");
        }
      }
    }

    return warnings;
  }

  private static String normalize(String value) {
    if (value == null) return "";
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static int countDefined(Object... values) {
    int count = 0;
    if (values == null) return 0;
    for (Object value : values) {
      if (value != null) count++;
    }
    return count;
  }
}
