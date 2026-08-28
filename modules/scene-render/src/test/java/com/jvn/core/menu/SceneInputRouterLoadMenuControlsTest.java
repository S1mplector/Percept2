package com.jvn.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.scenerender.input.DefaultMenuSceneFactory;
import com.jvn.scenerender.input.SceneInputRouter;
import com.jvn.scenerender.menu.MenuRenderer;
import com.jvn.scenerender.menu.MenuTheme;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import com.jvn.scenerender.vn.VnRenderer;

/**
 * Covers the LoadMenuScene branch of SceneInputRouter.handleClick's LoadControlHit
 * switch (Task 4's port of FxLauncher's cycle-left/cycle-right/favorites-toggle/
 * page-drag control handling), exercising the page-cycling and favorites-toggle
 * paths that Task 4's own test suite left uncovered (it only exercised the plain
 * item-select path).
 *
 * Deliberately declared in package com.jvn.core.menu (not com.jvn.scenerender.input,
 * despite living in the scene-render module's test sourceset): LoadMenuScene's only
 * constructor overload that accepts a custom MenuProfile — needed here to force a
 * small page size so a handful of saves reliably spans multiple pages — is
 * package-private to com.jvn.core.menu (see LoadMenuScene.java; the public 6-arg
 * constructor always goes through MenuProfileLoader.loadWithDiagnostics(), whose
 * default layouts have no maxVisibleItems cap, so seeded saves would always fit on
 * one page and page-cycling could never be tested from outside that package).
 * modules/core's own MenuSceneActionRoutingTest/MenuSaveScopeFilteringTest use this
 * same package-private constructor from within com.jvn.core.menu; this test reuses
 * that access from the scene-render module's test classpath, which already depends
 * on core, to combine that fixture-construction with the real SceneInputRouter.
 */
class SceneInputRouterLoadMenuControlsTest {

  private static final double W = 1280.0;
  private static final double H = 720.0;

  @Test
  void clickingCycleRightAdvancesLoadMenuPage() throws Exception {
    Fixture fx = buildMultiPageLoadMenu();
    LoadMenuScene scene = fx.scene;
    assertTrue(scene.getPageCount() > 1, "fixture must have multiple pages for this test to be meaningful");

    int pageBefore = scene.getCurrentPageIndex();

    double[] coords = findControlHit(fx.menuRenderer, scene, MenuRenderer.LoadControlType.CYCLE_RIGHT);
    assertTrue(coords != null, "failed to find coordinates hitting CYCLE_RIGHT control via grid scan");

    fx.router.handleClick(scene, fx.engine, W, H, coords[0], coords[1]);

    assertEquals(pageBefore + 1, scene.getCurrentPageIndex());
  }

  @Test
  void clickingCycleLeftReturnsToPreviousLoadMenuPage() throws Exception {
    Fixture fx = buildMultiPageLoadMenu();
    LoadMenuScene scene = fx.scene;
    assertTrue(scene.getPageCount() > 1, "fixture must have multiple pages for this test to be meaningful");

    // Move to page 1 first so cycle-left has somewhere to go.
    scene.movePage(1);
    int pageBefore = scene.getCurrentPageIndex();
    assertTrue(pageBefore > 0, "expected to have advanced off page 0 before testing cycle-left");

    double[] coords = findControlHit(fx.menuRenderer, scene, MenuRenderer.LoadControlType.CYCLE_LEFT);
    assertTrue(coords != null, "failed to find coordinates hitting CYCLE_LEFT control via grid scan");

    fx.router.handleClick(scene, fx.engine, W, H, coords[0], coords[1]);

    assertEquals(pageBefore - 1, scene.getCurrentPageIndex());
  }

  @Test
  void clickingFavoritesToggleFlipsFavoritesOnlyFlag() throws Exception {
    Fixture fx = buildMultiPageLoadMenu();
    LoadMenuScene scene = fx.scene;
    boolean before = scene.isFavoritesOnly();

    double[] coords = findControlHit(fx.menuRenderer, scene, MenuRenderer.LoadControlType.TOGGLE_FAVORITES_ONLY);
    assertTrue(coords != null, "failed to find coordinates hitting TOGGLE_FAVORITES_ONLY control via grid scan");

    fx.router.handleClick(scene, fx.engine, W, H, coords[0], coords[1]);

    assertEquals(!before, scene.isFavoritesOnly());
  }

  /**
   * Scans a grid of (x, y) candidates and returns the first pair whose
   * MenuRenderer.getLoadControlHit resolves to the requested control type,
   * matching the coordinate-resolution approach already established in
   * SceneInputRouterMenuClickTest.clickingMainMenuItemSelectsAndActivatesIt
   * and SceneInputRouterHoverTest.
   */
  private static double[] findControlHit(MenuRenderer menuRenderer, LoadMenuScene scene, MenuRenderer.LoadControlType wanted) {
    for (double gy = 0; gy < H; gy += 2.0) {
      for (double gx = 0; gx < W; gx += 2.0) {
        MenuRenderer.LoadControlHit hit = menuRenderer.getLoadControlHit(scene, W, H, gx, gy);
        if (hit != null && hit.type() == wanted) {
          return new double[] {gx, gy};
        }
      }
    }
    return null;
  }

  private static Fixture buildMultiPageLoadMenu() throws Exception {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    Engine engine = new Engine(ApplicationConfig.builder().build());
    VnSaveManager saveManager =
        new VnSaveManager(Files.createTempDirectory("jvn-scene-input-router-load-menu-controls").toString());

    // Seed enough save slots to exceed one page's worth of items: force a small
    // maxVisibleItems via a custom layout so 5 saves reliably span 2+ pages,
    // rather than relying on the default (effectively unbounded) layout.
    for (int i = 0; i < 5; i++) {
      saveState(saveManager, "slot_" + i, "demo_story", "demo.vns");
    }

    MenuProfile profile = smallPageProfile();
    LoadMenuScene scene = new LoadMenuScene(engine, saveManager, "demo.vns", new VnSettings(), null, null, profile);

    return new Fixture(engine, menuRenderer, router, scene);
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

  /**
   * A MenuProfile whose "load" screen layout caps maxVisibleItems at 2, so 5
   * seeded saves span 3 pages — enough to make page-cycling and page-selector
   * hit tests meaningful (default profile layouts have no cap). defaultLoadScreen()
   * points at layoutId "slots" (see MenuProfile.defaultLoadScreen), so the paged
   * layout must be registered under that key for LoadMenuScene to pick it up.
   */
  private static MenuProfile smallPageProfile() {
    MenuLayoutSpec pagedLayout = new MenuLayoutSpec(
        "slots", 0.22, 68.0, 0.54, "left", 28.0, 0.12, null, null, 2);
    var layouts = java.util.Map.of("slots", pagedLayout, "default", MenuProfile.defaultLayout());
    var styles = java.util.Map.of("default", MenuProfile.defaultStyle(), "slot", MenuProfile.defaultSlotStyle());
    var screens = new java.util.LinkedHashMap<String, MenuScreenSpec>();
    screens.put("main", MenuProfile.defaultMainScreen());
    screens.put("load", MenuProfile.defaultLoadScreen());
    return new MenuProfile("main", screens, layouts, styles);
  }

  private record Fixture(Engine engine, MenuRenderer menuRenderer, SceneInputRouter router, LoadMenuScene scene) {}
}
