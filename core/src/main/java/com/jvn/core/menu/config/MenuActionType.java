package com.jvn.core.menu.config;

public enum MenuActionType {
  NEW_GAME,
  LOAD_MENU,
  SAVE_MENU,
  SETTINGS_MENU,
  MAIN_MENU,
  OPEN_MENU,
  RUN_SCRIPT,
  BACK,
  QUIT,
  NOOP;

  public static MenuActionType parse(String raw) {
    if (raw == null || raw.isBlank()) return NOOP;
    String v = raw.trim().toLowerCase().replace('-', '_');
    return switch (v) {
      case "new", "new_game", "start", "start_game" -> NEW_GAME;
      case "load", "load_menu", "continue" -> LOAD_MENU;
      case "save", "save_menu" -> SAVE_MENU;
      case "settings", "settings_menu", "options" -> SETTINGS_MENU;
      case "main", "main_menu", "title", "title_menu" -> MAIN_MENU;
      case "open_menu", "submenu", "menu" -> OPEN_MENU;
      case "run_script", "script", "start_script", "play_script" -> RUN_SCRIPT;
      case "back", "return" -> BACK;
      case "quit", "exit" -> QUIT;
      default -> NOOP;
    };
  }
}
