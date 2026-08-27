package com.jvn.core.menu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
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
import com.jvn.core.vn.save.VnSaveManager;

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

    SettingsScene scene = new SettingsScene(new VnSettings(), null, null, new MenuProfile("main", screens, layouts, styles), "settings");

    assertEquals("", scene.getDisplayTitle());
    assertEquals("", scene.getDisplaySubtitle());
    assertEquals("", scene.getDisplayHints());
    assertArrayEquals(new String[] { "", "" }, scene.getDisplayItems());
  }

  @Test
  void explicitBlankSettingLabelSuppressesValueText() {
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
            new MenuItemSpec("text_speed", "", "settings", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null)
        )
    ));

    SettingsScene scene = new SettingsScene(new VnSettings(), null, null, new MenuProfile("main", screens, layouts, styles), "settings");

    assertArrayEquals(new String[] { "" }, scene.getDisplayItems());
  }

  @Test
  void showValueFalseSuppressesGeneratedSettingValueText() {
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
            new MenuItemSpec(
                "skip_unread",
                "Unseen Text",
                "settings",
                null,
                true,
                MenuActionSpec.noop(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("showValue", "false"),
                null,
                null,
                null
            )
        )
    ));

    SettingsScene scene = new SettingsScene(new VnSettings(), null, null, new MenuProfile("main", screens, layouts, styles), "settings");

    assertArrayEquals(new String[] { "Unseen Text" }, scene.getDisplayItems());
  }

  @Test
  void settingsPreferPrimaryControlsAndCarryThemAcrossTabSwitches() {
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
            new MenuItemSpec("audio_tab", "Audio", "settings", null, true, new MenuActionSpec(MenuActionType.SETTINGS_MENU, "settings_audio"), null, null, null, null, null, null, null),
            new MenuItemSpec("text_speed", "Text Speed {value}", "settings", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("auto_play_delay", "Auto {value}", "settings", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null)
        )
    ));
    screens.put("settings_audio", new MenuScreenSpec(
        "settings_audio",
        "",
        "",
        "",
        "settings",
        "settings",
        true,
        List.of(
            new MenuItemSpec("video_tab", "Video", "settings", null, true, new MenuActionSpec(MenuActionType.SETTINGS_MENU, "settings"), null, null, null, null, null, null, null),
            new MenuItemSpec("bgm_volume", "Music {value}", "settings", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null),
            new MenuItemSpec("auto_play_delay", "Auto {value}", "settings", null, true, MenuActionSpec.noop(), null, null, null, null, null, null, null)
        )
    ));

    MenuProfile profile = new MenuProfile("main", screens, layouts, styles);
    Engine engine = new Engine(ApplicationConfig.builder().build());
    SettingsScene scene = new SettingsScene(engine, new VnSaveManager(), "demo.vns", new VnSettings(), null, null, null, profile);

    assertEquals(1, scene.getSelected());

    scene.setSelected(2);
    scene.setSelected(0);
    engine.scenes().push(scene);
    scene.toggleCurrent();

    SettingsScene top = assertInstanceOf(SettingsScene.class, engine.scenes().peek());
    assertEquals("settings_audio", top.getMenuId());
    assertEquals(2, top.getSelected());
  }

  @Test
  void defaultSettingsExposeWorkingAccessibilityControls() {
    VnSettings settings = new VnSettings();
    SettingsScene scene = new SettingsScene(settings);

    selectByKey(scene, "text_to_speech");
    scene.toggleCurrent();
    assertTrue(settings.isTextToSpeechEnabled());

    selectByKey(scene, "ui_font_scale");
    scene.adjustCurrent(2);
    assertEquals(1.10, settings.getUiFontScale(), 0.0001);
    assertEquals(1.10, scene.getUiFontScale(), 0.0001);

    selectByKey(scene, "accessibility_theme");
    scene.toggleCurrent();
    assertEquals("highcontrast", settings.getAccessibilityTheme());
  }

  private static void selectByKey(SettingsScene scene, String key) {
    for (int i = 0; i < scene.getDisplayItems().length; i++) {
      scene.setSelected(i);
      if (key.equals(scene.getSelectedKey())) return;
    }
    throw new AssertionError("Settings key not found: " + key);
  }
}
