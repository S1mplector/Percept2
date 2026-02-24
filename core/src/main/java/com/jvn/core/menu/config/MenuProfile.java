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

    return new MenuProfile("main", screens, layouts, styles);
  }

  public static MenuLayoutSpec defaultLayout() {
    return new MenuLayoutSpec("default", 0.34, 68.0, 0.44, "center", 36.0, 0.14);
  }

  public static MenuLayoutSpec defaultSubmenuLayout() {
    return new MenuLayoutSpec("submenu", 0.24, 62.0, 0.64, "left", 30.0, 0.11);
  }

  public static MenuLayoutSpec defaultSlotsLayout() {
    return new MenuLayoutSpec("slots", 0.20, 74.0, 0.58, "left", 30.0, 0.10);
  }

  public static MenuStyleSpec defaultStyle() {
    return new MenuStyleSpec(
        "default",
        "#DCE6F8", "#FFE8A3", "#F4F8FF", "#7D8CA8",
        "", "▶ ", "• ",
        "Segoe UI", "SEMI_BOLD", 28,
        "#000000AA", 1.0, 1.0, 1.0,
        null, null, null, null,           // button assets (normal, selected, hover, disabled)
        28.0, 2.0,
        "#F2F7FF", "Segoe UI", "BOLD", 56, "#000000A8",
        "#A8B6D2", "Segoe UI", 18,
        "assets/demo/backgrounds/field/glorious_ricefield_day.png", "#050B16", 1.0
    );
  }

  public static MenuStyleSpec defaultSubmenuStyle() {
    return new MenuStyleSpec(
        "submenu",
        "#D6E0F4", "#B8EAFF", "#EAF4FF", "#7387AA",
        "  ", "▸ ", "  ",
        "Segoe UI", "NORMAL", 24,
        null, null, null, 1.0,
        null, null, null, null,
        24.0, 1.0,
        "#EAF2FF", "Segoe UI", "BOLD", 42, null,
        "#97AACE", "Segoe UI", 16,
        null, "#091222", 1.0
    );
  }

  public static MenuStyleSpec defaultSlotStyle() {
    return new MenuStyleSpec(
        "slot",
        "#E4EDF8", "#FFF1B5", "#F0F7FF", "#7B87A0",
        "", "▶ ", "• ",
        "Segoe UI", "SEMI_BOLD", 22,
        null, null, null, 1.0,
        null, null, null, null,
        22.0, 0.0,
        "#EAF2FF", "Segoe UI", "BOLD", 42, null,
        "#97AACE", "Segoe UI", 16,
        null, "#060F1D", 1.0
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
            new MenuItemSpec("extras", "Extras", null, null, true, new MenuActionSpec(MenuActionType.OPEN_MENU, "extras"), null, null, null, null, null, null, null),
            new MenuItemSpec("quit", null, null, null, true, new MenuActionSpec(MenuActionType.OPEN_MENU, "confirm_exit"), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultExtrasScreen() {
    return new MenuScreenSpec(
        "extras",
        "Extras",
        "Enter/Click: Select    Esc: Back",
        "submenu",
        "submenu",
        true,
        List.of(
            new MenuItemSpec("music_room", "Music Room (Soon)", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("credits", "Credits", "submenu", null, true, new MenuActionSpec(MenuActionType.OPEN_MENU, "credits"), null, null, null, null, null, null, null),
            new MenuItemSpec("back", "Return to Main Menu", "submenu", null, true, new MenuActionSpec(MenuActionType.MAIN_MENU, null), null, null, null, null, null, null, null)
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
            new MenuItemSpec("line_engine", "JVN Engine Team", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("line_editor", "Runtime, Editor, and VNS by JVN contributors", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("line_thanks", "Thanks for building with JVN.", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("back", "Back", "slot", null, true, new MenuActionSpec(MenuActionType.OPEN_MENU, "extras"), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultConfirmExitScreen() {
    return new MenuScreenSpec(
        "confirm_exit",
        "Exit Game",
        "Enter: Confirm    Esc: Cancel",
        "submenu",
        "submenu",
        true,
        List.of(
            new MenuItemSpec("prompt", "Leave this session?", "submenu", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("quit_yes", "Yes, Quit", "slot", null, true, new MenuActionSpec(MenuActionType.QUIT, null), null, null, null, null, null, null, null),
            new MenuItemSpec("quit_no", "No, Return", "submenu", null, true, new MenuActionSpec(MenuActionType.MAIN_MENU, null), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultLoadScreen() {
    return new MenuScreenSpec(
        "load",
        "Load Journey",
        "Enter: Load    Esc: Back    Del: Delete    R: Rename",
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
        "Save Journey",
        "Enter: Save    Esc: Back    Del: Delete    R: Rename",
        "slots",
        "slot",
        true,
        List.of(
            new MenuItemSpec("new_slot", "Create New Save", "submenu", null, true, new MenuActionSpec(MenuActionType.SAVE_MENU, null), null, null, null, null, null, null, null),
            new MenuItemSpec("save_slot", null, "slot", null, true, new MenuActionSpec(MenuActionType.SAVE_MENU, null), null, null, null, null, null, null, null)
        )
    );
  }

  public static MenuScreenSpec defaultSettingsScreen() {
    return new MenuScreenSpec(
        "settings",
        "Settings",
        "Up/Down: Select    Left/Right: Adjust    Esc: Back",
        "submenu",
        "submenu",
        true,
        List.of(
            new MenuItemSpec("text_speed", "Text Speed: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("bgm_volume", "BGM Volume: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("sfx_volume", "SFX Volume: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("voice_volume", "Voice Volume: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("auto_play_delay", "Auto Advance Delay: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("skip_unread", "Skip Unread Text: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("skip_after_choices", "Skip After Choices: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("physics_fixed_step", "Physics Fixed Step: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("physics_max_substeps", "Physics Max Substeps: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("physics_default_friction", "Physics Friction: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("input_profile", "Input Profile: {value}", "submenu", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("back", "Back", "slot", null, true, new MenuActionSpec(MenuActionType.BACK, null), null, null, null, null, null, null, null)
        )
    );
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
