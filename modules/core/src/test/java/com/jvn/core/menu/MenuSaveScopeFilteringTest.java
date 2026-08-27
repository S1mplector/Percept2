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
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.save.VnSaveManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuSaveScopeFilteringTest {

  @Test
  void loadMenuFiltersToMatchingProjectSavesAndRewritesLegacyTitle() throws Exception {
    VnSaveManager saveManager = new VnSaveManager(Files.createTempDirectory("jvn-load-scope").toString());
    saveState(saveManager, "slot_current", "story_a", "scripts/a.vns");
    saveState(saveManager, "slot_other", "story_b", "scripts/b.vns");

    Engine engine = new Engine(ApplicationConfig.builder().build());
    LoadMenuScene scene = new LoadMenuScene(
        engine,
        saveManager,
        "scripts/a.vns",
        new VnSettings(),
        null,
        null,
        profileWithTitles("Load Journey", "Save")
    );

    assertEquals(List.of("slot_current"), scene.getSaves());
    assertEquals("Load Save", scene.getDisplayTitle());
  }

  @Test
  void saveMenuFiltersToActiveSceneScope() throws Exception {
    VnSaveManager saveManager = new VnSaveManager(Files.createTempDirectory("jvn-save-scope").toString());
    saveState(saveManager, "slot_current", "story_a", "scripts/a.vns");
    saveState(saveManager, "slot_other", "story_b", "scripts/b.vns");

    VnScene vnScene = new VnScene(new VnScenarioBuilder("story_a")
        .label("start")
        .dialogue("Narrator", "Hello")
        .end()
        .build());
    vnScene.getState().setSourceScriptName("scripts/a.vns");

    Engine engine = new Engine(ApplicationConfig.builder().build());
    SaveMenuScene scene = new SaveMenuScene(
        engine,
        saveManager,
        vnScene,
        "scripts/a.vns",
        profileWithTitles("Load", "Save")
    );

    assertEquals(List.of("slot_current"), scene.getSaves());
  }

  @Test
  void loadMenuHidesSavesFromOtherProjects() throws Exception {
    VnSaveManager saveManager = new VnSaveManager(Files.createTempDirectory("jvn-load-hide-foreign").toString());
    saveState(saveManager, "slot_other_one", "story_b", "scripts/b.vns");
    saveState(saveManager, "slot_other_two", "story_c", "scripts/c.vns");

    Engine engine = new Engine(ApplicationConfig.builder().build());
    LoadMenuScene scene = new LoadMenuScene(
        engine,
        saveManager,
        "scripts/a.vns",
        new VnSettings(),
        null,
        null,
        profileWithTitles("Load", "Save")
    );

    assertTrue(scene.getSaves().isEmpty());
  }

  private static void saveState(VnSaveManager saveManager, String slotName, String scenarioId, String scriptName) throws Exception {
    VnState state = new VnState();
    state.setScenario(new VnScenarioBuilder(scenarioId)
        .label("start")
        .dialogue("Narrator", "Line")
        .end()
        .build());
    state.setSourceScriptName(scriptName);
    saveManager.save(state, slotName);
  }

  private static MenuProfile profileWithTitles(String loadTitle, String saveTitle) {
    Map<String, MenuLayoutSpec> layouts = Map.of("default", MenuProfile.defaultLayout());
    Map<String, MenuStyleSpec> styles = Map.of("default", MenuProfile.defaultStyle());
    Map<String, MenuScreenSpec> screens = new LinkedHashMap<>();
    screens.put("main", MenuProfile.defaultMainScreen());
    screens.put("load", new MenuScreenSpec(
        "load",
        loadTitle,
        null,
        "default",
        "default",
        true,
        List.of(new MenuItemSpec("save_slot", "Slot", null, null, true, new MenuActionSpec(MenuActionType.LOAD_MENU, null),
            null, null, null, null, null, null, null))
    ));
    screens.put("save", new MenuScreenSpec(
        "save",
        saveTitle,
        null,
        "default",
        "default",
        true,
        List.of(
            new MenuItemSpec("new_slot", "New", null, null, true, new MenuActionSpec(MenuActionType.SAVE_MENU, null),
                null, null, null, null, null, null, null),
            new MenuItemSpec("save_slot", "Slot", null, null, true, new MenuActionSpec(MenuActionType.SAVE_MENU, null),
                null, null, null, null, null, null, null)
        )
    ));
    return new MenuProfile("main", screens, layouts, styles);
  }
}
