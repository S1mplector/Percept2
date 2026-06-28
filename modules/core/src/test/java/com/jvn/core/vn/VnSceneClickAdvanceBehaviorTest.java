package com.jvn.core.vn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void waitTagPausesRevealUntilClickReleasesIt() {
    VnScenario scenario = new VnScenarioBuilder("wait_tag")
        .dialogue("Narrator", "Hi{w} there.")
        .dialogue("Narrator", "Second line.")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.onEnter();
    scene.getState().getSettings().setTextSpeed(1);

    scene.update(1);
    scene.update(1);

    assertEquals(0, scene.getState().getCurrentNodeIndex());
    assertEquals(2, scene.getState().getTextRevealProgress());
    assertTrue(scene.getState().isWaitingForInput());

    scene.update(1000);
    assertEquals(2, scene.getState().getTextRevealProgress());

    scene.advanceFromClick();
    scene.update(1);

    assertEquals(0, scene.getState().getCurrentNodeIndex());
    assertEquals(3, scene.getState().getTextRevealProgress());
  }

  @Test
  void timedWaitTagResumesRevealAfterDuration() {
    VnScenario scenario = new VnScenarioBuilder("timed_wait_tag")
        .dialogue("Narrator", "Hi{w=0.5}!")
        .dialogue("Narrator", "Second line.")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.onEnter();
    scene.getState().getSettings().setTextSpeed(0);

    scene.update(0);
    assertEquals(2, scene.getState().getTextRevealProgress());
    assertTrue(scene.getState().isWaitingForInput());

    scene.update(499);
    assertEquals(2, scene.getState().getTextRevealProgress());

    scene.update(1);
    assertEquals(0, scene.getState().getCurrentNodeIndex());
    assertEquals(3, scene.getState().getTextRevealProgress());
  }

  @Test
  void nowaitTagAdvancesWithoutFinalInput() {
    VnScenario scenario = new VnScenarioBuilder("nowait_tag")
        .dialogue("Narrator", "Hi{nw}")
        .dialogue("Narrator", "Second line.")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.onEnter();
    scene.getState().getSettings().setTextSpeed(0);

    scene.update(0);

    assertEquals(1, scene.getState().getCurrentNodeIndex());
  }

  @Test
  void timedNowaitTagAdvancesAfterDuration() {
    VnScenario scenario = new VnScenarioBuilder("timed_nowait_tag")
        .dialogue("Narrator", "Hi{nw=0.5}")
        .dialogue("Narrator", "Second line.")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);
    scene.onEnter();
    scene.getState().getSettings().setTextSpeed(0);

    scene.update(0);
    assertEquals(0, scene.getState().getCurrentNodeIndex());
    assertEquals(2, scene.getState().getTextRevealProgress());

    scene.update(499);
    assertEquals(0, scene.getState().getCurrentNodeIndex());

    scene.update(1);
    assertEquals(1, scene.getState().getCurrentNodeIndex());
  }
}
