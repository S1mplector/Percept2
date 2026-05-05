package com.jvn.core.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneManagerLifecycleTest {

  @Test
  void pauseAndResumeAreCalledAroundPushPop() {
    SceneManager mgr = new SceneManager();
    TrackingScene a = new TrackingScene();
    TrackingScene b = new TrackingScene();

    mgr.push(a);
    assertEquals(1, a.enterCount);
    assertEquals(0, a.pauseCount);

    mgr.push(b);
    assertEquals(1, a.pauseCount);
    assertEquals(1, b.enterCount);

    mgr.pop(); // pop b, resume a
    assertEquals(1, b.exitCount);
    assertEquals(1, a.resumeCount);

    mgr.pop(); // pop a
    assertEquals(1, a.exitCount);
    assertTrue(mgr.isEmpty());
  }

  private static final class TrackingScene implements Scene {
    int enterCount;
    int exitCount;
    int pauseCount;
    int resumeCount;
    int updateCount;

    @Override public void onEnter() { enterCount++; }
    @Override public void onExit() { exitCount++; }
    @Override public void onPause() { pauseCount++; }
    @Override public void onResume() { resumeCount++; }
    @Override public void update(long deltaMs) { updateCount++; }
  }
}
