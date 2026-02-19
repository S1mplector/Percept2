package com.jvn.core.menu.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MenuProfile(
    String defaultScreenId,
    Map<String, MenuScreenSpec> screens,
    Map<String, MenuLayoutSpec> layouts,
    Map<String, MenuStyleSpec> styles
) {
  public MenuProfile {
    defaultScreenId = normalize(defaultScreenId, "main");
    screens = screens == null ? Map.of() : Map.copyOf(screens);
    layouts = layouts == null ? Map.of() : Map.copyOf(layouts);
    styles = styles == null ? Map.of() : Map.copyOf(styles);
  }

  public MenuScreenSpec screen(String id) {
    String key = normalize(id, defaultScreenId);
    MenuScreenSpec out = screens.get(key);
    if (out != null) return out;
    out = screens.get(defaultScreenId);
    if (out != null) return out;
    out = screens.get("main");
    if (out != null) return out;
    return defaultMainScreen();
  }

  public MenuLayoutSpec layout(String id) {
    String key = normalize(id, "default");
    MenuLayoutSpec out = layouts.get(key);
    if (out != null) return out;
    out = layouts.get("default");
    return out != null ? out : defaultLayout();
  }

  public MenuStyleSpec style(String id) {
    String key = normalize(id, "default");
    MenuStyleSpec out = styles.get(key);
    if (out != null) return out;
    out = styles.get("default");
    return out != null ? out : defaultStyle();
  }

  public static MenuProfile defaults() {
    Map<String, MenuLayoutSpec> layouts = new LinkedHashMap<>();
    layouts.put("default", defaultLayout());

    Map<String, MenuStyleSpec> styles = new LinkedHashMap<>();
    styles.put("default", defaultStyle());

    Map<String, MenuScreenSpec> screens = new LinkedHashMap<>();
    screens.put("main", defaultMainScreen());

    return new MenuProfile("main", screens, layouts, styles);
  }

  public static MenuLayoutSpec defaultLayout() {
    return new MenuLayoutSpec("default", 0.35, 40.0, 1.0, "center", 20.0, null);
  }

  public static MenuStyleSpec defaultStyle() {
    return new MenuStyleSpec(
        "default",
        null,
        null,
        "#808080",
        "  ",
        "> ",
        "- ",
        null,
        null,
        null
    );
  }

  public static MenuScreenSpec defaultMainScreen() {
    return new MenuScreenSpec(
        "main",
        null,
        null,
        "default",
        "default",
        true,
        List.of(
            new MenuItemSpec("new_game", null, null, null, true, new MenuActionSpec(MenuActionType.NEW_GAME, null)),
            new MenuItemSpec("load", null, null, null, true, new MenuActionSpec(MenuActionType.LOAD_MENU, null)),
            new MenuItemSpec("settings", null, null, null, true, new MenuActionSpec(MenuActionType.SETTINGS_MENU, null)),
            new MenuItemSpec("quit", null, null, null, true, new MenuActionSpec(MenuActionType.QUIT, null))
        )
    );
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
