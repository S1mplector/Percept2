package com.jvn.scenerender.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.scenerender.menu.MenuRenderer;
import com.jvn.scenerender.menu.MenuTheme;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import com.jvn.scenerender.vn.VnRenderer;

class SceneInputRouterHoverTest {

  @Test
  void hoveringMainMenuItemUpdatesSelection() throws Exception {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    // Construct a MainMenuScene the same way
    // SceneInputRouterMenuClickTest.clickingMainMenuItemSelectsAndActivatesIt does.
    Engine engine = new Engine(ApplicationConfig.builder().build());
    VnSaveManager saveManager =
        new VnSaveManager(Files.createTempDirectory("jvn-scene-input-router-hover-main-menu").toString());
    MainMenuScene scene = new MainMenuScene(engine, new VnSettings(), saveManager, "demo.vns", null, null);

    double w = 1280.0, h = 720.0;
    int expectedIndex = 1;

    // Resolve real hit-test coordinates by scanning a grid of (x, y) candidates and checking
    // MenuRenderer.getHoverIndexForMainMenu's return value directly, reusing the same approach
    // established in SceneInputRouterMenuClickTest.
    double x = -1, y = -1;
    outer:
    for (double gy = 0; gy < h; gy += 2.0) {
      for (double gx = 0; gx < w; gx += 20.0) {
        if (menuRenderer.getHoverIndexForMainMenu(scene, w, h, gx, gy) == expectedIndex) {
          x = gx;
          y = gy;
          break outer;
        }
      }
    }
    assertTrue(x >= 0 && y >= 0, "failed to find coordinates hitting main menu item index 1 via grid scan");

    router.handleHover(scene, w, h, x, y);

    assertEquals(expectedIndex, scene.getSelected());
  }
}
