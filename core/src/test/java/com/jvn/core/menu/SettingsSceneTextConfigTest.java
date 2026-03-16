package com.jvn.core.menu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.vn.VnSettings;

class SettingsSceneTextConfigTest {

  @Test
  void explicitBlankScreenTextAndLabelsDoNotFallback() {
    Map<String, MenuLayoutSpec> layouts = Map.of("settings", MenuProfile.defaultSettingsLayout());
    Map<String, MenuStyleSpec> styles = Map.of("settings", MenuProfile.defaultSettingsStyle());
    Map<String, MenuScreenSpec> screens = new LinkedHashMap<>();
    screens.put("main", MenuProfile.defaultMainScreen());
    screens.put("settings", new MenuScreenSpec(
        "settings",
        "",
        "",
        "",
        "settings",
        "settings",
        true,
        List.of(
            new MenuItemSpec("decor", "", "settings", null, false, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("resume", "", "settings", null, true, new MenuActionSpec(MenuActionType.BACK, null), null, null, null, null, null, null, null)
        )
    ));

    SettingsScene scene = new SettingsScene(new VnSettings(), null, new MenuProfile("main", screens, layouts, styles), "settings");

    assertEquals("", scene.getDisplayTitle());
    assertEquals("", scene.getDisplaySubtitle());
    assertEquals("", scene.getDisplayHints());
    assertArrayEquals(new String[] { "", "" }, scene.getDisplayItems());
  }
}
