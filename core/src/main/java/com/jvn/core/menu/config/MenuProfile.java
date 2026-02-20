package com.jvn.core.menu.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  public boolean hasScreen(String id) {
    String key = normalize(id, null);
    return key != null && screens.containsKey(key);
  }

  public boolean hasLayout(String id) {
    String key = normalize(id, null);
    return key != null && layouts.containsKey(key);
  }

  public boolean hasStyle(String id) {
    String key = normalize(id, null);
    return key != null && styles.containsKey(key);
  }

  public Set<String> screenIds() {
    return screens.keySet();
  }

  public Set<String> layoutIds() {
    return layouts.keySet();
  }

  public Set<String> styleIds() {
    return styles.keySet();
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
        null, null, null, "#808080",     // item colors (normal, selected, hover, disabled)
        "  ", "> ", "- ",                 // prefixes
        null, null, null,                 // font family, weight, size
        null, null, null, null,           // shadow color, offsetX, offsetY, opacity
        null, null, null, null,           // button assets (normal, selected, hover, disabled)
        null, null,                       // button text padding X, Y
        null, null, null, null, null,     // title color, fontFamily, fontWeight, fontSize, shadowColor
        null, null, null,                 // hints color, fontFamily, fontSize
        null, null, null                  // background asset, color, opacity
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
            new MenuItemSpec("new_game", null, null, null, true, new MenuActionSpec(MenuActionType.NEW_GAME, null), null, null, null, null, null, null, null),
            new MenuItemSpec("load", null, null, null, true, new MenuActionSpec(MenuActionType.LOAD_MENU, null), null, null, null, null, null, null, null),
            new MenuItemSpec("settings", null, null, null, true, new MenuActionSpec(MenuActionType.SETTINGS_MENU, null), null, null, null, null, null, null, null),
            new MenuItemSpec("quit", null, null, null, true, new MenuActionSpec(MenuActionType.QUIT, null), null, null, null, null, null, null, null)
        )
    );
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
