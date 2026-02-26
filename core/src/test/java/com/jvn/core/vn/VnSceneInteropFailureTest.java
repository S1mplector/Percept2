package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VnSceneInteropFailureTest {

  @Test
  void externalInteropFailureShowsHudAndKeepsProcessing() {
    VnScenario scenario = new VnScenarioBuilder("interop_external_failure")
        .external("var", "set hp 10")
        .dialogue("Narrator", "After external")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.setInterop((command, vnScene) -> {
      throw new IllegalStateException("boom");
    });

    scene.onEnter();

    assertEquals(1, scene.getState().getCurrentNodeIndex());
    assertEquals("VN external [var] failed: IllegalStateException: boom", scene.getState().getHudMessage());
  }

  @Test
  void preflightInteropFailureShowsHudMessage() {
    VnScenario scenario = new VnScenarioBuilder("interop_preflight_failure")
        .external("var", "set hp 10")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.setInterop((command, vnScene) -> {
      throw new RuntimeException("preflight exploded");
    });

    scene.preflightState(1);

    String hud = scene.getState().getHudMessage();
    assertNotNull(hud);
    assertTrue(hud.startsWith("VN preflight [var] failed: RuntimeException"));
  }
}
