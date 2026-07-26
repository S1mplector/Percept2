package com.jvn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.teavm.jso.browser.AnimationFrameCallback;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.render.RenderSurface;

class WebGameLoopTest {

  @Test
  void updatesRendersPresentsAndSchedulesNextFrame() {
    Engine engine = startedEngine();
    FakeSurface surface = new FakeSurface();
    FakeScheduler scheduler = new FakeScheduler();
    int[] renders = {0};
    WebGameLoop loop = new WebGameLoop(engine, surface, scheduler);
    loop.setFrameRenderer(() -> renders[0]++);

    loop.start();
    assertTrue(loop.isRunning());
    assertEquals(1, scheduler.requestCount);

    scheduler.fire(100.0);
    scheduler.fire(117.0);

    assertEquals(2L, engine.frameStats().getTotalFrames());
    assertEquals(2, renders[0]);
    assertEquals(2, surface.presentCount);
    assertEquals(3, scheduler.requestCount);
  }

  @Test
  void stopPreventsQueuedCallbackFromContinuingLoop() {
    Engine engine = startedEngine();
    FakeSurface surface = new FakeSurface();
    FakeScheduler scheduler = new FakeScheduler();
    WebGameLoop loop = new WebGameLoop(engine, surface, scheduler);

    loop.start();
    loop.stop();
    scheduler.fire(100.0);

    assertFalse(loop.isRunning());
    assertEquals(0L, engine.frameStats().getTotalFrames());
    assertEquals(0, surface.presentCount);
    assertEquals(1, scheduler.requestCount);
  }

  @Test
  void invalidSurfaceStopsLoopWithoutUpdating() {
    Engine engine = startedEngine();
    FakeSurface surface = new FakeSurface();
    FakeScheduler scheduler = new FakeScheduler();
    WebGameLoop loop = new WebGameLoop(engine, surface, scheduler);

    loop.start();
    surface.valid = false;
    scheduler.fire(100.0);

    assertFalse(loop.isRunning());
    assertEquals(0L, engine.frameStats().getTotalFrames());
    assertEquals(1, scheduler.requestCount);
  }

  private static Engine startedEngine() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    engine.start();
    return engine;
  }

  private static final class FakeScheduler implements WebGameLoop.AnimationFrameScheduler {
    private AnimationFrameCallback callback = timestamp -> {};
    private boolean hasCallback;
    private int requestCount;

    @Override
    public void request(AnimationFrameCallback callback) {
      this.callback = callback;
      hasCallback = true;
      requestCount++;
    }

    private void fire(double timestamp) {
      if (!hasCallback) throw new AssertionError("No animation frame is scheduled");
      callback.onAnimationFrame(timestamp);
    }
  }

  private static final class FakeSurface implements RenderSurface {
    private boolean valid = true;
    private int presentCount;

    @Override
    public double getWidth() {
      return 1280;
    }

    @Override
    public double getHeight() {
      return 720;
    }

    @Override
    public double getPixelScale() {
      return 1;
    }

    @Override
    public void present() {
      presentCount++;
    }

    @Override
    public boolean isValid() {
      return valid;
    }

    @Override
    public void dispose() {
      valid = false;
    }
  }
}
