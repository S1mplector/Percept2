package com.jvn.core.engine;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineAdvancedLoopTest {

  // --- Time Scale ---

  @Test
  void timeScaleHalvesEffectiveDelta() {
    Engine engine = newEngine();
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.setTimeScale(0.5);
    engine.start();

    engine.update(20);

    assertEquals(1, scene.updateCount);
    assertEquals(10, scene.updateTotalDelta, "20ms * 0.5 scale = 10ms effective");
  }

  @Test
  void timeScaleDoublesEffectiveDelta() {
    Engine engine = newEngine();
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.setTimeScale(2.0);
    engine.start();

    engine.update(20);

    assertEquals(1, scene.updateCount);
    assertEquals(40, scene.updateTotalDelta, "20ms * 2.0 scale = 40ms effective");
  }

  @Test
  void timeScaleZeroFreezesGameTime() {
    Engine engine = newEngine();
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.setTimeScale(0.0);
    engine.start();

    engine.update(16);

    assertEquals(1, scene.updateCount);
    assertEquals(0, scene.updateTotalDelta, "zero time scale means zero effective delta");
  }

  @Test
  void timeScaleClampedToRange() {
    Engine engine = newEngine();
    engine.setTimeScale(-5.0);
    assertEquals(0.0, engine.getTimeScale(), "negative clamped to 0");

    engine.setTimeScale(100.0);
    assertEquals(10.0, engine.getTimeScale(), "above 10 clamped to 10");

    engine.setTimeScale(Double.NaN);
    assertEquals(0.0, engine.getTimeScale(), "NaN clamped to 0");
  }

  @Test
  void timeScaleAffectsFixedUpdate() {
    Engine engine = newEngine();
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.setFixedUpdateStepMs(10, 10);
    engine.setTimeScale(2.0);
    engine.start();

    engine.update(15); // effective = 30ms → 3 fixed ticks

    assertEquals(3, scene.fixedUpdateCount, "30ms effective / 10ms step = 3 ticks");
  }

  @Test
  void timeScaleFromConfig() {
    ApplicationConfig cfg = ApplicationConfig.builder().timeScale(0.25).build();
    Engine engine = new Engine(cfg);
    assertEquals(0.25, engine.getTimeScale());
  }

  // --- Pause ---

  @Test
  void pauseFreezesSceneUpdates() {
    Engine engine = newEngine();
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.start();
    engine.setPaused(true);

    engine.update(16);

    assertEquals(0, scene.updateCount, "update should not fire when paused");
    assertEquals(0, scene.fixedUpdateCount);
    assertEquals(0, scene.lateUpdateCount);
    assertTrue(engine.isPaused());
  }

  @Test
  void unpauseResumesUpdates() {
    Engine engine = newEngine();
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.start();

    engine.setPaused(true);
    engine.update(16);
    assertEquals(0, scene.updateCount);

    engine.setPaused(false);
    engine.update(16);
    assertEquals(1, scene.updateCount, "update should fire after unpause");
  }

  // --- Late Update ---

  @Test
  void lateUpdateCalledAfterUpdate() {
    Engine engine = newEngine();
    OrderTrackingScene scene = new OrderTrackingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.start();

    engine.update(16);

    assertEquals(List.of("update", "lateUpdate"), scene.callOrder);
  }

  @Test
  void lateUpdateCalledWithSameDeltaAsUpdate() {
    Engine engine = newEngine();
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.start();

    engine.update(25);

    assertEquals(1, scene.lateUpdateCount);
    assertEquals(25, scene.lateUpdateTotalDelta);
  }

  @Test
  void fullPhaseOrderWithFixedStep() {
    Engine engine = newEngine();
    OrderTrackingScene scene = new OrderTrackingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.setFixedUpdateStepMs(10, 5);
    engine.start();

    engine.update(25); // 2 fixed ticks + 1 update + 1 lateUpdate

    assertEquals(List.of("fixedUpdate", "fixedUpdate", "update", "lateUpdate"), scene.callOrder);
  }

  // --- Frame Stats ---

  @Test
  void frameStatsTracksFps() {
    Engine engine = newEngine();
    engine.setDeltaSmoothing(0);
    engine.start();

    for (int i = 0; i < 10; i++) {
      engine.update(16); // ~60 FPS
    }

    FrameStats stats = engine.frameStats();
    assertEquals(10, stats.getSampleCount());
    assertEquals(10, stats.getTotalFrames());
    assertTrue(stats.getFps() > 50 && stats.getFps() < 70,
        "FPS should be around 62.5 for 16ms frames, got " + stats.getFps());
    assertEquals(16.0, stats.getAvgMs(), 0.01);
    assertEquals(16.0, stats.getMinMs(), 0.01);
    assertEquals(16.0, stats.getMaxMs(), 0.01);
  }

  @Test
  void frameStatsTracksMinMax() {
    Engine engine = newEngine();
    engine.setDeltaSmoothing(0);
    engine.start();

    engine.update(10);
    engine.update(30);
    engine.update(20);

    FrameStats stats = engine.frameStats();
    assertEquals(10.0, stats.getMinMs());
    assertEquals(30.0, stats.getMaxMs());
    assertEquals(20.0, stats.getAvgMs(), 0.01);
  }

  @Test
  void frameStatsRecordedEvenWhenPaused() {
    Engine engine = newEngine();
    engine.setDeltaSmoothing(0);
    engine.start();
    engine.setPaused(true);

    engine.update(16);

    assertEquals(1, engine.frameStats().getTotalFrames(),
        "stats should record frames even when paused");
  }

  // --- Engine Listeners ---

  @Test
  void listenerReceivesPreAndPostUpdate() {
    Engine engine = newEngine();
    engine.setDeltaSmoothing(0);
    engine.start();

    List<String> calls = new ArrayList<>();
    engine.addListener(new EngineListener() {
      @Override public void preUpdate(long rawDeltaMs) {
        calls.add("pre:" + rawDeltaMs);
      }
      @Override public void postUpdate(long effectiveDeltaMs) {
        calls.add("post:" + effectiveDeltaMs);
      }
    });

    engine.update(16);

    assertEquals(List.of("pre:16", "post:16"), calls);
  }

  @Test
  void listenerFiresEvenWhenPaused() {
    Engine engine = newEngine();
    engine.setDeltaSmoothing(0);
    engine.start();
    engine.setPaused(true);

    List<String> calls = new ArrayList<>();
    engine.addListener(new EngineListener() {
      @Override public void preUpdate(long rawDeltaMs) { calls.add("pre"); }
      @Override public void postUpdate(long effectiveDeltaMs) { calls.add("post"); }
    });

    engine.update(16);

    assertEquals(List.of("pre", "post"), calls,
        "listeners should fire even when engine is paused");
  }

  @Test
  void listenerRemovalWorks() {
    Engine engine = newEngine();
    engine.start();

    int[] count = {0};
    EngineListener listener = new EngineListener() {
      @Override public void preUpdate(long rawDeltaMs) { count[0]++; }
    };

    engine.addListener(listener);
    engine.update(16);
    assertEquals(1, count[0]);

    engine.removeListener(listener);
    engine.update(16);
    assertEquals(1, count[0], "listener should not fire after removal");
  }

  @Test
  void duplicateListenerIgnored() {
    Engine engine = newEngine();
    engine.start();

    int[] count = {0};
    EngineListener listener = new EngineListener() {
      @Override public void preUpdate(long rawDeltaMs) { count[0]++; }
    };

    engine.addListener(listener);
    engine.addListener(listener); // duplicate
    engine.update(16);
    assertEquals(1, count[0], "same listener should only fire once");
  }

  @Test
  void listenerPreUpdateReceivesRawDelta() {
    Engine engine = newEngine();
    engine.setDeltaSmoothing(0);
    engine.setTimeScale(2.0);
    engine.start();

    long[] rawDelta = {0};
    long[] postDelta = {0};
    engine.addListener(new EngineListener() {
      @Override public void preUpdate(long rawDeltaMs) { rawDelta[0] = rawDeltaMs; }
      @Override public void postUpdate(long effectiveDeltaMs) { postDelta[0] = effectiveDeltaMs; }
    });

    engine.update(20);

    assertEquals(20, rawDelta[0], "preUpdate should receive raw (unscaled) delta");
    assertEquals(40, postDelta[0], "postUpdate should receive effective (scaled) delta");
  }

  // --- Helpers ---

  private static Engine newEngine() {
    return new Engine(ApplicationConfig.builder().build());
  }

  private static final class CountingScene implements Scene {
    int fixedUpdateCount = 0;
    long fixedUpdateTotalDelta = 0;
    int updateCount = 0;
    long updateTotalDelta = 0;
    int lateUpdateCount = 0;
    long lateUpdateTotalDelta = 0;

    @Override public void fixedUpdate(long deltaMs) {
      fixedUpdateCount++;
      fixedUpdateTotalDelta += deltaMs;
    }
    @Override public void update(long deltaMs) {
      updateCount++;
      updateTotalDelta += deltaMs;
    }
    @Override public void lateUpdate(long deltaMs) {
      lateUpdateCount++;
      lateUpdateTotalDelta += deltaMs;
    }
  }

  private static final class OrderTrackingScene implements Scene {
    final List<String> callOrder = new ArrayList<>();

    @Override public void fixedUpdate(long deltaMs) { callOrder.add("fixedUpdate"); }
    @Override public void update(long deltaMs) { callOrder.add("update"); }
    @Override public void lateUpdate(long deltaMs) { callOrder.add("lateUpdate"); }
  }
}
