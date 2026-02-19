package com.jvn.core.menu.config;

import java.util.ArrayList;
import java.util.List;

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
      for (MenuItemSpec item : screen.items()) {
        if (item == null) continue;
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
      }
    }

    return warnings;
  }
}
