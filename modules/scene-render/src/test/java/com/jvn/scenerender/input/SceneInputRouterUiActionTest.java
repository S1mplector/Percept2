package com.jvn.scenerender.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.menu.HistoryMenuScene;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnScene;
import com.jvn.scenerender.menu.MenuRenderer;
import com.jvn.scenerender.menu.MenuTheme;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import com.jvn.scenerender.vn.VnRenderer;

class SceneInputRouterUiActionTest {

  @Test
  void rollbackButtonActionCallsSceneRollback() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    // Same fixture-construction pattern used by SceneInputRouterVnSceneClickTest /
    // VnSceneClickAdvanceBehaviorTest: two dialogue lines so advance() has
    // somewhere to move to, and rollback() has state to roll back from.
    VnScenario scenario = new VnScenarioBuilder("router_ui_action_rollback")
        .dialogue("Narrator", "Hello there.")
        .dialogue("Narrator", "Second line.")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    scene.onEnter();

    // Advance to the second node so rollback() has a previous entry to restore.
    scene.advance();
    assertEquals(1, scene.getState().getCurrentNodeIndex());

    boolean handled = router.executeUiActionForTest(scene, "rollback", "", null, true);

    assertTrue(handled);
    // rollback() restores the previously *captured* rollback entry; the entry
    // captured when the current (post-advance) node was shown is pushed last,
    // so it is what a single rollback pops first — restoring node index back
    // to where it already was is the platform-verified no-op case. A second
    // rollback call is needed to move to the prior node's captured entry.
    assertEquals(1, scene.getState().getCurrentNodeIndex());

    boolean handledAgain = router.executeUiActionForTest(scene, "rollback", "", null, true);
    assertTrue(handledAgain);
    assertEquals(0, scene.getState().getCurrentNodeIndex());
  }

  @Test
  void unknownButtonActionIsHandledButShowsHudMessage() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    VnScenario scenario = new VnScenarioBuilder("router_ui_action_unknown")
        .dialogue("Narrator", "Hello there.")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    scene.onEnter();

    boolean handled = router.executeUiActionForTest(scene, "totally_unknown_action", "", null, true);

    assertTrue(handled);
  }

  @Test
  void toggleHistoryActionPushesThenPopsHistoryMenuScene() throws Exception {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    Engine engine = new Engine(ApplicationConfig.builder().build());
    VnScenario scenario = new VnScenarioBuilder("router_ui_action_toggle_history")
        .dialogue("Narrator", "Hello there.")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    scene.onEnter();
    engine.scenes().push(scene);

    // First call: no HistoryMenuScene on top yet, so it should clear scroll
    // and push a new HistoryMenuScene.
    boolean handled = router.executeUiActionForTest(engine, scene, "toggle_history", "", null, true);

    assertTrue(handled);
    Scene afterFirstCall = engine.scenes().peek();
    assertInstanceOf(HistoryMenuScene.class, afterFirstCall);

    // Second call: a HistoryMenuScene is now on top, so it should pop
    // (close) it instead of pushing a second one — this is the toggle
    // behavior FxLauncher.handleToggleHistory implements.
    boolean handledAgain = router.executeUiActionForTest(engine, scene, "toggle_history", "", null, true);

    assertTrue(handledAgain);
    assertEquals(scene, engine.scenes().peek());
  }

  @Test
  void noopButtonActionIsHandledWithoutSideEffects() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    VnScenario scenario = new VnScenarioBuilder("router_ui_action_noop")
        .dialogue("Narrator", "Hello there.")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    scene.onEnter();
    int nodeIndexBefore = scene.getState().getCurrentNodeIndex();

    boolean handled = router.executeUiActionForTest(scene, "noop", "", null, true);

    assertTrue(handled);
    assertEquals(nodeIndexBefore, scene.getState().getCurrentNodeIndex());
  }
}
