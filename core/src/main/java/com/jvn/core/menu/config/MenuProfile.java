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
    layouts.put("submenu", defaultSubmenuLayout());
    layouts.put("slots", defaultSlotsLayout());

    Map<String, MenuStyleSpec> styles = new LinkedHashMap<>();
    styles.put("default", defaultStyle());
    styles.put("submenu", defaultSubmenuStyle());
    styles.put("slot", defaultSlotStyle());

    Map<String, MenuScreenSpec> screens = new LinkedHashMap<>();
    screens.put("main", defaultMainScreen());
    screens.put("extras", defaultExtrasScreen());
    screens.put("credits", defaultCreditsScreen());
    screens.put("confirm_exit", defaultConfirmExitScreen());
    screens.put("load", defaultLoadScreen());
    screens.put("save", defaultSaveScreen());
    screens.put("settings", defaultSettingsScreen());
    screens.put("pause", defaultPauseScreen());

    return new MenuProfile("main", screens, layouts, styles);
  }

  public static MenuLayoutSpec defaultLayout() {
    return new MenuLayoutSpec("default", 0.38, 62.0, 0.36, "center", 32.0, 0.16);
  }

  public static MenuLayoutSpec defaultSubmenuLayout() {
    return new MenuLayoutSpec("submenu", 0.26, 56.0, 0.52, "left", 28.0, 0.13);
  }

  public static MenuLayoutSpec defaultSlotsLayout() {
    return new MenuLayoutSpec("slots", 0.22, 68.0, 0.54, "left", 28.0, 0.12);
  }

  public static MenuStyleSpec defaultStyle() {
    return new MenuStyleSpec(
        "default",
        "#C8D6EC", "#FFDFA0", "#E4EEFF", "#5C6B84",
        "", "▸ ", "",
        "SansSerif", "SEMI_BOLD", 26,
        "#00000066", 1.5, 1.5, 1.0,
        null, null, null, null,
        24.0, 2.0,
        "#EEF4FF", "SansSerif", "BOLD", 48, "#00000088",
        "#8898B8", "SansSerif", 15,
        null, "#060D1A", 1.0
    );
  }

  public static MenuStyleSpec defaultSubmenuStyle() {
    return new MenuStyleSpec(
        "submenu",
        "#B8C8E4", "#90D4F8", "#D8E8FF", "#5A6E8C",
        "", "▸ ", "",
        "SansSerif", "NORMAL", 22,
        "#00000044", 1.0, 1.0, 1.0,
        null, null, null, null,
        20.0, 1.0,
        "#D8E6FF", "SansSerif", "BOLD", 36, "#00000066",
        "#7888A8", "SansSerif", 14,
        null, "#08101E", 1.0
    );
  }

  public static MenuStyleSpec defaultSlotStyle() {
    return new MenuStyleSpec(
        "slot",
        "#C4D4EC", "#FFE4A0", "#E0ECFF", "#5E6E88",
        "", "▸ ", "",
        "SansSerif", "SEMI_BOLD", 20,
        "#00000044", 1.0, 1.0, 1.0,
        null, null, null, null,
        18.0, 0.0,
        "#D8E6FF", "SansSerif", "BOLD", 36, "#00000066",
        "#7888A8", "SansSerif", 14,
        null, "#070E1C", 1.0
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
            new MenuItemSpec("new_game", "New Game", null, null, true, new MenuActionSpec(MenuActionType.NEW_GAME, null), null, null, null, null, null, null, null),
            new MenuItemSpec("load", "Continue", null, null, true, new MenuActionSpec(MenuActionType.LOAD_MENU, null), null, null, null, null, null, null, null),
            new MenuItemSpec("settings", "Settings", null, null, true, new MenuActionSpec(MenuActionType.SETTINGS_MENU, null), null, null, null, null, null, null, null),
            new MenuItemSpec("extras", "Gallery", null, null, true, new MenuActionSpec(MenuActionType.OPEN_MENU, "extras"), null, null, null, null, null, null, null),
            new MenuItemSpec("quit", "Exit", null, null, true, new MenuActionSpec(MenuActionType.OPEN_MENU, "confirm_exit"), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultExtrasScreen() {
    return new MenuScreenSpec(
        "extras",
        "Gallery",
        "Select an item    Esc: Back",
        "submenu",
        "submenu",
        true,
        List.of(
            new MenuItemSpec("music_room", "Music Room", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("cg_gallery", "CG Gallery", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("credits", "Credits", "submenu", null, true, new MenuActionSpec(MenuActionType.OPEN_MENU, "credits"), null, null, null, null, null, null, null),
            new MenuItemSpec("back", "Back", "submenu", null, true, new MenuActionSpec(MenuActionType.MAIN_MENU, null), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultCreditsScreen() {
    return new MenuScreenSpec(
        "credits",
        "Credits",
        "Esc: Back",
        "submenu",
        "submenu",
        true,
        List.of(
            new MenuItemSpec("line_engine", "Built with JVN Engine", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("line_author", "Story, Art, and Direction by [Your Name]", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("line_music", "Music and Sound by [Composer]", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("line_thanks", "Special thanks to everyone who played.", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("back", "Back", "submenu", null, true, new MenuActionSpec(MenuActionType.OPEN_MENU, "extras"), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultConfirmExitScreen() {
    return new MenuScreenSpec(
        "confirm_exit",
        "Exit",
        "Enter: Confirm    Esc: Cancel",
        "submenu",
        "submenu",
        true,
        List.of(
            new MenuItemSpec("prompt", "Are you sure you want to exit?", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("quit_yes", "Quit", "submenu", null, true, new MenuActionSpec(MenuActionType.QUIT, null), null, null, null, null, null, null, null),
            new MenuItemSpec("quit_no", "Cancel", "submenu", null, true, new MenuActionSpec(MenuActionType.MAIN_MENU, null), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultLoadScreen() {
    return new MenuScreenSpec(
        "load",
        "Load Game",
        "Enter: Load    Del: Delete    Esc: Back",
        "slots",
        "slot",
        true,
        List.of(
            new MenuItemSpec("save_slot", null, "slot", null, true, new MenuActionSpec(MenuActionType.LOAD_MENU, null), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultSaveScreen() {
    return new MenuScreenSpec(
        "save",
        "Save Game",
        "Enter: Save    Del: Delete    Esc: Back",
        "slots",
        "slot",
        true,
        List.of(
            new MenuItemSpec("new_slot", "New Save", "submenu", null, true, new MenuActionSpec(MenuActionType.SAVE_MENU, null), null, null, null, null, null, null, null),
            new MenuItemSpec("save_slot", null, "slot", null, true, new MenuActionSpec(MenuActionType.SAVE_MENU, null), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultPauseScreen() {
    return new MenuScreenSpec(
        "pause",
        "Paused",
        "Esc: Resume",
        "default",
        "default",
        true,
        List.of(
            new MenuItemSpec("resume", "Resume", "default", null, true, new MenuActionSpec(MenuActionType.BACK, null), null, null, null, null, null, null, null),
            new MenuItemSpec("save", "Save", "default", null, true, new MenuActionSpec(MenuActionType.SAVE_MENU, null), null, null, null, null, null, null, null),
            new MenuItemSpec("load", "Load", "default", null, true, new MenuActionSpec(MenuActionType.LOAD_MENU, null), null, null, null, null, null, null, null),
            new MenuItemSpec("settings", "Settings", "default", null, true, new MenuActionSpec(MenuActionType.SETTINGS_MENU, null), null, null, null, null, null, null, null),
            new MenuItemSpec("main_menu", "Main Menu", "default", null, true, new MenuActionSpec(MenuActionType.MAIN_MENU, null), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultSettingsScreen() {
    return new MenuScreenSpec(
        "settings",
        "Settings",
        "Left/Right: Adjust    Esc: Back",
        "submenu",
        "submenu",
        true,
        List.of(
            new MenuItemSpec("text_speed", "Text Speed: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("auto_play_delay", "Auto-Advance: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("bgm_volume", "Music: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("sfx_volume", "Sound Effects: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("voice_volume", "Voices: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("skip_unread", "Skip Unread: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("skip_after_choices", "Skip After Choices: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("click_reveal_before_advance", "Click to Reveal: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("back", "Back", "submenu", null, true, new MenuActionSpec(MenuActionType.BACK, null), null, null, null, null, null, null, null)
        )
    );
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
