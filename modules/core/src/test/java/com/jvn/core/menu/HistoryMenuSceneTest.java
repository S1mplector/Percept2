package com.jvn.core.menu;

import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.vn.DemoScenario;
import com.jvn.core.vn.VnScene;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryMenuSceneTest {

  @Test
  void defaultsExposeHistoryScreen() {
    MenuProfile profile = MenuProfile.defaults();

    assertTrue(profile.hasScreen("history"));
    assertEquals("history", profile.screen("history").id());
    assertEquals("history", profile.screen("history").layoutId());
    assertEquals("history", profile.screen("history").defaultStyleId());
  }

  @Test
  void historySceneUsesProfileAndScrollsUnderlyingState() {
    VnScene vnScene = new VnScene(DemoScenario.createSimpleDemo());
    vnScene.getState().getHistory().addEntry("Lavender", "Line one");
    vnScene.getState().getHistory().addEntry("Narrator", "Line two");

    MenuProfile profile = historyProfile();
    HistoryMenuScene history = new HistoryMenuScene(null, vnScene, profile);

    assertEquals("History Screen", history.getDisplayTitle());
    assertEquals("Use B to close", history.getDisplayHints());
    assertEquals(2, history.getEntries().size());
    assertTrue(history.linesPerPage(720) > 0);

    history.scrollByLines(3);
    assertEquals(3, vnScene.getState().getHistoryScroll());

    history.clearScroll();
    assertEquals(0, vnScene.getState().getHistoryScroll());
  }

  private static MenuProfile historyProfile() {
    Map<String, MenuLayoutSpec> layouts = Map.of("history", MenuProfile.defaultHistoryLayout());
    Map<String, MenuStyleSpec> styles = Map.of("history", MenuProfile.defaultHistoryStyle());
    Map<String, MenuScreenSpec> screens = new LinkedHashMap<>();
    screens.put("main", MenuProfile.defaultMainScreen());
    screens.put("history", new MenuScreenSpec(
        "history",
        "History Screen",
        "Use B to close",
        "history",
        "history",
        true,
        List.of()
    ));
    return new MenuProfile("main", screens, layouts, styles);
  }
}
