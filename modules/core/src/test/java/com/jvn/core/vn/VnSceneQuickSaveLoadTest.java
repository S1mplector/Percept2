package com.jvn.core.vn;

import com.jvn.core.vn.save.VnSaveManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class VnSceneQuickSaveLoadTest {
  @Test
  public void quickSaveLoadRestoresState() throws Exception {
    // Build scenario
    VnScenario scenario = new VnScenarioBuilder("qs_story")
        .addBackground("room", "game/images/bg_room.png")
        .dialogue("", "Line 1")
        .dialogue("", "Line 2")
        .end()
        .build();

    VnScene scene = new VnScene(scenario);

    // Use temp dir for quick save
    Path tmp = Files.createTempDirectory("vn_quick_test");
    VnSaveManager mgr = new VnSaveManager(tmp.toString());
    VnQuickSaveManager qsm = new VnQuickSaveManager(mgr);
    scene.setQuickSaveManager(qsm);

    // Advance once and set some flags
    scene.advance(); // go to Line 2
    scene.getState().setSkipMode(true);
    scene.getState().setUiHidden(true);

    assertTrue(scene.quickSave());

    // Change state
    scene.getState().setSkipMode(false);
    scene.getState().setUiHidden(false);
    scene.getState().setCurrentBackgroundId("room");
    scene.getState().setVariable("x", 42);

    assertTrue(scene.quickLoad());

    // Verify restored
    assertTrue(scene.getState().isSkipMode());
    assertTrue(scene.getState().isUiHidden());
    assertEquals(1, scene.getState().getCurrentNodeIndex());
  }
}
