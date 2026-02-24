package com.jvn.core.vn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VnSceneClickAdvanceBehaviorTest {

  @Test
  void clickRevealBeforeAdvanceIsDefaultBehavior() {
    VnScenario scenario = new VnScenarioBuilder("click_default")
        .dialogue("Narrator", "Hello there.")
        .dialogue("Narrator", "Second line.")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.onEnter();

    scene.getState().setTextRevealProgress(1);
    scene.advanceFromClick();

    assertEquals(0, scene.getState().getCurrentNodeIndex());
    assertEquals("Hello there.".length(), scene.getState().getTextRevealProgress());

    scene.advanceFromClick();
    assertEquals(1, scene.getState().getCurrentNodeIndex());
  }

  @Test
  void clickCanAdvanceImmediatelyWhenRevealFirstIsDisabled() {
    VnScenario scenario = new VnScenarioBuilder("click_disabled")
        .dialogue("Narrator", "Hello there.")
        .dialogue("Narrator", "Second line.")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.onEnter();
    scene.getState().getSettings().setClickRevealBeforeAdvance(false);
    scene.getState().setTextRevealProgress(1);

    scene.advanceFromClick();

    assertEquals(1, scene.getState().getCurrentNodeIndex());
  }
}
