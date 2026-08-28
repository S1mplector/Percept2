package com.jvn.scenerender.input;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnScene;
import com.jvn.scenerender.menu.MenuRenderer;
import com.jvn.scenerender.menu.MenuTheme;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import com.jvn.scenerender.vn.VnRenderer;

class SceneInputRouterKeyboardActionsTest {

  @Test
  void toggleSkipFlipsVnSceneSkipMode() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    // Same fixture-scenario construction pattern used by
    // SceneInputRouterVnSceneClickTest (Task 2).
    VnScenario scenario = new VnScenarioBuilder("router_toggle_skip")
        .dialogue("Narrator", "Hello there.")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    scene.onEnter();

    Engine engine = new Engine(ApplicationConfig.builder().build());
    engine.scenes().push(scene);

    boolean skipBefore = scene.getState().isSkipMode();

    router.toggleSkip(engine);

    assertTrue(scene.getState().isSkipMode() != skipBefore);
  }
}
