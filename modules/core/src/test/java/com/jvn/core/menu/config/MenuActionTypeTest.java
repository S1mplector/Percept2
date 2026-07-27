package com.jvn.core.menu.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MenuActionTypeTest {

  @Test
  void parsesGameplayPauseActionsAndAliases() {
    assertEquals(MenuActionType.HISTORY_MENU, MenuActionType.parse("history"));
    assertEquals(MenuActionType.HISTORY_MENU, MenuActionType.parse("backlog"));
    assertEquals(MenuActionType.TOGGLE_SKIP, MenuActionType.parse("toggle_skip"));
    assertEquals(MenuActionType.TOGGLE_SKIP, MenuActionType.parse("skip"));
    assertEquals(MenuActionType.TOGGLE_AUTO, MenuActionType.parse("toggle_auto"));
    assertEquals(MenuActionType.TOGGLE_AUTO, MenuActionType.parse("auto_play"));
  }
}
