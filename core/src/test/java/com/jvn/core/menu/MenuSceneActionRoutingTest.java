package com.jvn.core.menu;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.DemoScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuSceneActionRoutingTest {

  @Test
  void settingsOpenMenuActionPushesConfiguredMainMenu() throws Exception {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    VnSaveManager saveManager = new VnSaveManager(Files.createTempDirectory("jvn-settings-open-menu").toString());
    VnSettings settings = new VnSettings();
    MenuProfile profile = profileWith(
        settingsScreenWith(new MenuActionSpec(MenuActionType.OPEN_MENU, "extras")),
        loadScreenWith(new MenuActionSpec(MenuActionType.LOAD_MENU, null)),
        saveScreenWith(new MenuActionSpec(MenuActionType.SAVE_MENU, null))
    );

    SettingsScene scene = new SettingsScene(engine, saveManager, "demo.vns", settings, null, null, profile);
    assertEquals(0, scene.getSelected());

    scene.toggleCurrent();

    Scene top = engine.scenes().peek();
    MainMenuScene main = assertInstanceOf(MainMenuScene.class, top);
    assertEquals("extras", main.getMenuId());
  }

  @Test
  void settingsLoadMenuActionPushesLoadMenuScene() throws Exception {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    VnSaveManager saveManager = new VnSaveManager(Files.createTempDirectory("jvn-settings-load-menu").toString());
    VnSettings settings = new VnSettings();
    MenuProfile profile = profileWith(
        settingsScreenWith(new MenuActionSpec(MenuActionType.LOAD_MENU, "scripts/story/prologue.vns")),
        loadScreenWith(new MenuActionSpec(MenuActionType.LOAD_MENU, null)),
        saveScreenWith(new MenuActionSpec(MenuActionType.SAVE_MENU, null))
    );

    SettingsScene scene = new SettingsScene(engine, saveManager, "demo.vns", settings, null, null, profile);
    scene.toggleCurrent();

    assertInstanceOf(LoadMenuScene.class, engine.scenes().peek());
  }

  @Test
  void loadSlotOpenMenuActionPushesConfiguredMainMenu() throws Exception {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    VnSaveManager saveManager = new VnSaveManager(Files.createTempDirectory("jvn-load-open-menu").toString());
    MenuProfile profile = profileWith(
        settingsScreenWith(new MenuActionSpec(MenuActionType.BACK, null)),
        loadScreenWith(new MenuActionSpec(MenuActionType.OPEN_MENU, "extras")),
        saveScreenWith(new MenuActionSpec(MenuActionType.SAVE_MENU, null))
    );

    LoadMenuScene scene = new LoadMenuScene(engine, saveManager, "demo.vns", new VnSettings(), null, profile);
    assertTrue(scene.activateSelected());

    Scene top = engine.scenes().peek();
    MainMenuScene main = assertInstanceOf(MainMenuScene.class, top);
    assertEquals("extras", main.getMenuId());
  }

  @Test
  void saveSlotOpenMenuActionPushesConfiguredMainMenu() throws Exception {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    VnSaveManager saveManager = new VnSaveManager(Files.createTempDirectory("jvn-save-open-menu").toString());
    VnScene vnScene = new VnScene(DemoScenario.createSimpleDemo());
    MenuProfile profile = profileWith(
        settingsScreenWith(new MenuActionSpec(MenuActionType.BACK, null)),
        loadScreenWith(new MenuActionSpec(MenuActionType.LOAD_MENU, null)),
        saveScreenWith(new MenuActionSpec(MenuActionType.OPEN_MENU, "extras"))
    );

    SaveMenuScene scene = new SaveMenuScene(engine, saveManager, vnScene, "demo.vns", profile);
    assertTrue(scene.activateSelectedWithoutPrompt());

    Scene top = engine.scenes().peek();
    MainMenuScene main = assertInstanceOf(MainMenuScene.class, top);
    assertEquals("extras", main.getMenuId());
  }

  private static MenuProfile profileWith(MenuScreenSpec settings, MenuScreenSpec load, MenuScreenSpec save) {
    Map<String, MenuLayoutSpec> layouts = Map.of("default", MenuProfile.defaultLayout());
    Map<String, MenuStyleSpec> styles = Map.of("default", MenuProfile.defaultStyle());
    Map<String, MenuScreenSpec> screens = new LinkedHashMap<>();
    screens.put("main", MenuProfile.defaultMainScreen());
    screens.put("extras", new MenuScreenSpec(
        "extras",
        "Extras",
        null,
        "default",
        "default",
        true,
        List.of(new MenuItemSpec("back", "Back", null, null, true, new MenuActionSpec(MenuActionType.BACK, null),
            null, null, null, null, null, null, null))
    ));
    screens.put("settings", settings);
    screens.put("load", load);
    screens.put("save", save);
    return new MenuProfile("main", screens, layouts, styles);
  }

  private static MenuScreenSpec settingsScreenWith(MenuActionSpec action) {
    return new MenuScreenSpec(
        "settings",
        "Settings",
        null,
        "default",
        "default",
        true,
        List.of(new MenuItemSpec("custom_settings_action", "Action", null, null, true, action,
            null, null, null, null, null, null, null))
    );
  }

  private static MenuScreenSpec loadScreenWith(MenuActionSpec action) {
    return new MenuScreenSpec(
        "load",
        "Load",
        null,
        "default",
        "default",
        true,
        List.of(new MenuItemSpec("save_slot", "Slot", null, null, true, action,
            null, null, null, null, null, null, null))
    );
  }

  private static MenuScreenSpec saveScreenWith(MenuActionSpec action) {
    return new MenuScreenSpec(
        "save",
        "Save",
        null,
        "default",
        "default",
        true,
        List.of(new MenuItemSpec("new_slot", "New", null, null, true, action,
            null, null, null, null, null, null, null))
    );
  }
}
