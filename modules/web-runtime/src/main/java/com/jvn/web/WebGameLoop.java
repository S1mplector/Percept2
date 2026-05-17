package com.jvn.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final double TARGET_FPS = 60.0;
  private static final long TARGET_FRAME_TIME_NS = (long) (1_000_000_000.0 / TARGET_FPS);

  private final Engine engine;
  private final RenderSurface surface;
  private long lastFrameTimeNs = 0;
  private volatile boolean running = false;

  public WebGameLoop(Engine engine, RenderSurface surface) {
    this.engine = engine;
    this.surface = surface;
  }

  /**
   * Start the game loop on requestAnimationFrame.
   */
  public void start() {
    if (running) return;
    running = true;
    log.info("Starting web game loop");
    scheduleNextFrame();
  }

  /**
   * Stop the game loop.
   */
  public void stop() {
    running = false;
    log.info("Stopping web game loop");
  }

  private void scheduleNextFrame() {
    if (!running) return;
    requestAnimationFrameNative(this::onFrame);
  }

  private void onFrame(double timestamp) {
    if (!running || !surface.isValid()) {
      return;
    }

    // Calculate delta time in milliseconds
    long currentTimeMs = (long) timestamp; // timestamp is already in ms
    long deltaMs = lastFrameTimeNs > 0
        ? currentTimeMs - (lastFrameTimeNs / 1_000_000)
        : 16; // Default to ~60 FPS on first frame
    lastFrameTimeNs = currentTimeMs * 1_000_000;

    try {
      // Update game engine (expects milliseconds)
      engine.update(deltaMs);

      // Render frame - this would be handled by a separate renderer
      // that polls the engine's scene stack and draws with WebRenderer
      // For now, this is a placeholder

      // Present the frame
      surface.present();
    } catch (Exception e) {
      log.error("Error in game loop", e);
    }

    // Schedule next frame
    scheduleNextFrame();
  }

  // Native requestAnimationFrame binding
  private static native void requestAnimationFrameNative(FrameCallback callback) /*-{
    window.requestAnimationFrame(function(timestamp) {
      callback.@com.jvn.web.WebGameLoop$FrameCallback::onFrame(D)(timestamp);
    });
  }-*/;

  @FunctionalInterface
  interface FrameCallback {
    void onFrame(double timestamp);
  }
}
