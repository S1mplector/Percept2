package com.jvn.core.input;

/**
 * Centralized action identifiers and defaults.
 */
public final class InputActions {
  public static final String ADVANCE = "advance";
  public static final String SKIP_TOGGLE = "skip_toggle";
  public static final String AUTO_TOGGLE = "auto_toggle";
  public static final String HIDE_UI = "hide_ui";
  public static final String HISTORY = "history";
  public static final String SETTINGS = "settings";
  public static final String QUICK_SAVE = "quick_save";
  public static final String QUICK_LOAD = "quick_load";
  public static final String SAVE_MENU = "save_menu";
  public static final String ROLLBACK = "rollback";
  public static final String ROLLFORWARD = "rollforward";

  public static final String MENU_UP = "menu_up";
  public static final String MENU_DOWN = "menu_down";
  public static final String MENU_LEFT = "menu_left";
  public static final String MENU_RIGHT = "menu_right";
  public static final String MENU_CONFIRM = "menu_confirm";
  public static final String MENU_BACK = "menu_back";
  public static final String MENU_DELETE = "menu_delete";
  public static final String MENU_RENAME = "menu_rename";

  private InputActions() {}

  public static ActionBindingProfile defaultProfile() {
    ActionBindingProfile p = new ActionBindingProfile();
    // Core VN/navigation
    p.add(ADVANCE, InputCode.key("SPACE"))
        .add(ADVANCE, InputCode.key("ENTER"))
        .add(ADVANCE, InputCode.mouse(1));
    p.add(SKIP_TOGGLE, InputCode.key("CONTROL"))
        .add(SKIP_TOGGLE, InputCode.key("COMMAND"));
    p.add(AUTO_TOGGLE, InputCode.key("A"));
    p.add(HIDE_UI, InputCode.key("H"));
    p.add(HISTORY, InputCode.key("B"));
    p.add(SETTINGS, InputCode.key("S"));
    p.add(QUICK_SAVE, InputCode.key("F5"));
    p.add(QUICK_LOAD, InputCode.key("F9"));
    p.add(SAVE_MENU, InputCode.key("F6"));
    p.add(ROLLBACK, InputCode.key("PAGE_UP"))
        .add(ROLLBACK, InputCode.key("BACK_QUOTE")); // ` key
    p.add(ROLLFORWARD, InputCode.key("PAGE_DOWN"));

    // Menus
    p.add(MENU_UP, InputCode.key("UP")).add(MENU_UP, InputCode.key("W"));
    p.add(MENU_DOWN, InputCode.key("DOWN")).add(MENU_DOWN, InputCode.key("S"));
    p.add(MENU_LEFT, InputCode.key("LEFT")).add(MENU_LEFT, InputCode.key("A"));
    p.add(MENU_RIGHT, InputCode.key("RIGHT")).add(MENU_RIGHT, InputCode.key("D"));
    p.add(MENU_CONFIRM, InputCode.key("ENTER")).add(MENU_CONFIRM, InputCode.key("SPACE"));
    p.add(MENU_BACK, InputCode.key("ESCAPE"));
    p.add(MENU_DELETE, InputCode.key("DELETE"));
    p.add(MENU_RENAME, InputCode.key("R"));
    return p;
  }
}
