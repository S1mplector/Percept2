package com.jvn.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.scene.Scene;

class EngineFixedStepTest {

  @Test
  void fixedUpdateCalledAtFixedRate() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.setFixedUpdateStepMs(10, 5);
    engine.start();

    engine.update(35); // 35ms → 3 fixed ticks of 10ms, 5ms remainder

    assertEquals(3, scene.fixedUpdateCount, "fixedUpdate should fire 3 times");
    assertEquals(30, scene.fixedUpdateTotalDelta, "fixedUpdate total should be 30ms");
  }

  @Test
  void variableUpdateCalledOncePerFrame() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.setFixedUpdateStepMs(10, 5);
    engine.start();

    engine.update(35);

    assertEquals(1, scene.updateCount, "update should fire exactly once per frame");
    assertEquals(35, scene.updateTotalDelta, "update receives the full frame delta");
  }

  @Test
  void interpolationAlphaReflectsRemainder() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.setFixedUpdateStepMs(10, 5);
    engine.start();

    engine.update(35); // 5ms remainder out of 10ms step

    assertEquals(0.5, engine.getInterpolationAlpha(), 0.001,
        "alpha should be remainder/step = 5/10 = 0.5");
  }

  @Test
  void noFixedStepMeansAlphaIsZero() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    // fixedUpdateMs = 0 (default, disabled)
    engine.start();

    engine.update(16);

    assertEquals(0.0, engine.getInterpolationAlpha(),
        "alpha should be 0 when fixed timestep is disabled");
    assertEquals(1, scene.updateCount);
    assertEquals(0, scene.fixedUpdateCount, "fixedUpdate should not be called without fixed step");
  }

  private static final class CountingScene implements Scene {
    int fixedUpdateCount = 0;
    long fixedUpdateTotalDelta = 0;
    int updateCount = 0;
    long updateTotalDelta = 0;

    @Override public void fixedUpdate(long deltaMs) {
      fixedUpdateCount++;
      fixedUpdateTotalDelta += deltaMs;
    }
    @Override public void update(long deltaMs) {
      updateCount++;
      updateTotalDelta += deltaMs;
    }
  }
}
