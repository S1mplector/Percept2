package com.jvn.web;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teavm.jso.browser.AnimationFrameCallback;
import org.teavm.jso.browser.Window;

import com.jvn.core.engine.Engine;
import com.jvn.render.RenderSurface;

/**
 * Game loop implementation for web execution using {@code requestAnimationFrame}.
 *
 * <p>Unlike desktop, which uses threads and Thread.sleep(), the web game loop
 * must comply with the browser's event model and use {@code requestAnimationFrame}
 * for smooth 60 FPS rendering.</p>
 */
public class WebGameLoop {
  private static final Logger log = LoggerFactory.getLogger(WebGameLoop.class);

  private final Engine engine;
  private final RenderSurface surface;
  private final AnimationFrameScheduler scheduler;
  private double lastFrameTimestampMs = -1.0;
  private Runnable frameRenderer;
  private boolean running;

  public WebGameLoop(Engine engine, RenderSurface surface) {
    this(engine, surface, callback -> Window.requestAnimationFrame(callback));
  }

  WebGameLoop(
      Engine engine,
      RenderSurface surface,
      AnimationFrameScheduler scheduler) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.surface = Objects.requireNonNull(surface, "surface");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.frameRenderer = () -> {};
  }

  /** Set the drawing callback invoked after each engine update. */
  public void setFrameRenderer(Runnable frameRenderer) {
    this.frameRenderer = Objects.requireNonNull(frameRenderer, "frameRenderer");
  }

  /** Start the game loop on requestAnimationFrame. */
  public void start() {
    if (running) return;
    running = true;
    lastFrameTimestampMs = -1.0;
    log.info("Starting web game loop");
    scheduleNextFrame();
  }

  /**
   * Stop the game loop.
   */
  public void stop() {
    if (!running) return;
    running = false;
    log.info("Stopping web game loop");
  }

  /** @return whether this loop is currently scheduling frames */
  public boolean isRunning() {
    return running;
  }

  private void scheduleNextFrame() {
    if (!running) return;
    scheduler.request(this::onFrame);
  }

  private void onFrame(double timestamp) {
    if (!running) return;
    if (!surface.isValid()) {
      running = false;
      return;
    }

    long deltaMs = lastFrameTimestampMs >= 0.0
        ? Math.max(0L, Math.round(timestamp - lastFrameTimestampMs))
        : 16L;
    lastFrameTimestampMs = timestamp;

    try {
      engine.update(deltaMs);
      frameRenderer.run();
      surface.present();
    } catch (Exception e) {
      log.error("Error in game loop", e);
    }

    // Schedule next frame
    scheduleNextFrame();
  }

  @FunctionalInterface
  interface AnimationFrameScheduler {
    void request(AnimationFrameCallback callback);
  }
}
