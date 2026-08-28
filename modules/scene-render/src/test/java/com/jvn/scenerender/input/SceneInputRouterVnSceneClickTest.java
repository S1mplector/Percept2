package com.jvn.scenerender.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnScene;
import com.jvn.scenerender.menu.MenuRenderer;
import com.jvn.scenerender.menu.MenuTheme;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import com.jvn.scenerender.vn.VnRenderer;

class SceneInputRouterVnSceneClickTest {

  @Test
  void clickOutsideAnyChoiceAdvancesDialogueInstead() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    // Same fixture-construction pattern used by VnSceneClickAdvanceBehaviorTest
    // (modules/core/src/test/java/com/jvn/core/vn/VnSceneClickAdvanceBehaviorTest.java):
    // a VnScenarioBuilder with two dialogue lines, so advanceFromClick() has
    // somewhere to advance from without throwing.
    VnScenario scenario = new VnScenarioBuilder("router_click_advance")
        .dialogue("Narrator", "Hello there.")
        .dialogue("Narrator", "Second line.")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    scene.onEnter();

    int textRevealBefore = scene.getState().getTextRevealProgress();

    // Click far outside any UI element's known geometry — this fixture has no
    // choice node yet, so any click should fall through to advanceFromClick().
    router.handleClick(scene, null, 1280.0, 720.0, 5.0, 5.0);

    // advanceFromClick() on a not-yet-fully-revealed line completes the reveal
    // rather than moving to the next node — assert the reveal progressed.
    assertTrue(scene.getState().getTextRevealProgress() >= textRevealBefore);
    assertEquals("Hello there.".length(), scene.getState().getTextRevealProgress());
    assertEquals(0, scene.getState().getCurrentNodeIndex());
  }

  @Test
  void clickOnChoiceOptionSelectsIt() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    // Choice hit-test geometry follows VnChoiceOverlayRendererTest's approach
    // (modules/scene-render/src/test/java/com/jvn/scenerender/vn/VnChoiceOverlayRendererTest.java):
    // a single choice with default VnUiLayoutSpec geometry (choiceXCenter=0.5,
    // choiceWidthFactor=0.6, choiceHeight=50, choiceYStart=-1 auto-center) on a
    // 1280x720 render area centers the single choice's rect at x in [256,1024],
    // y in [335,385] — so (640, 360) deterministically lands inside it, unlike
    // that test's own assertion which had to tolerate a miss.
    VnScenario scenario = new VnScenarioBuilder("router_click_choice")
        .choice("Option A")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    scene.onEnter();

    router.handleClick(scene, null, 1280.0, 720.0, 640.0, 360.0);

    // selectChoice() on the (only, untargeted) choice advances past the CHOICE node.
    assertEquals(1, scene.getState().getCurrentNodeIndex());
  }
}
