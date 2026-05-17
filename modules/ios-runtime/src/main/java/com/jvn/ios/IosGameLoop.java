package com.jvn.ios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.engine.Engine;
import com.jvn.render.RenderSurface;

/**
 * Game loop implementation for iOS using CADisplayLink.
 *
 * <p>iOS uses CADisplayLink for synchronized rendering with the display refresh rate.
 * This is more efficient and smoother than a custom thread-based loop.</p>
 */
public class IosGameLoop {
  private static final Logger log = LoggerFactory.getLogger(IosGameLoop.class);

  private final Engine engine;
  private final IosRenderer renderer;
  private final RenderSurface surface;
  private Object displayLink; // CADisplayLink
  private volatile boolean running = false;
  private volatile boolean paused = false;
  private long lastFrameTimeNs = 0;

  public IosGameLoop(Engine engine, IosRenderer renderer, RenderSurface surface) {
    this.engine = engine;
    this.renderer = renderer;
    this.surface = surface;
  }

  /**
   * Start the game loop using CADisplayLink.
   */
  public void start() {
    if (running) return;
    running = true;
    displayLink = createDisplayLink(this::onFrame);
    log.info("iOS game loop started with CADisplayLink");
  }

  /**
   * Pause the game (app enters background).
   */
  public void pause() {
    paused = true;
    if (displayLink != null) {
      setDisplayLinkPausedNative(displayLink, true);
    }
    log.info("Game paused");
  }

  /**
   * Resume the game (app returns to foreground).
   */
  public void resume() {
    paused = false;
    if (displayLink != null) {
      setDisplayLinkPausedNative(displayLink, false);
    }
    log.info("Game resumed");
  }

  /**
   * Stop the game loop.
   */
  public void stop() {
    running = false;
    if (displayLink != null) {
      invalidateDisplayLinkNative(displayLink);
      displayLink = null;
    }
    log.info("iOS game loop stopped");
  }

  private void onFrame(long timestamp) {
    if (!running || !surface.isValid()) {
      return;
    }

    if (paused) {
      return; // Skip update while paused, but don't stop the display link
    }

    try {
      // Calculate delta time in milliseconds
      // timestamp is in nanoseconds from a system baseline
      long currentTimeNs = timestamp;
      long deltaMs = lastFrameTimeNs > 0
          ? (currentTimeNs - lastFrameTimeNs) / 1_000_000
          : 16; // Default to ~60 FPS on first frame
      lastFrameTimeNs = currentTimeNs;

      // Update game engine
      engine.update(deltaMs);

      // Render frame (would trigger view's setNeedsDisplay -> drawRect)
      // surface.present();
    } catch (Exception e) {
      log.error("Error in iOS game loop", e);
    }
  }

  // iOS CADisplayLink bindings (via Multi-OS Engine)
  private static native Object createDisplayLink(FrameCallback callback) /*-{
    // Would use MOE reflection to create CADisplayLink and add to main run loop
    // The callback would be called on each display refresh (~60 Hz)
    return null;
  }-*/;

  private static native void setDisplayLinkPausedNative(Object displayLink, boolean paused) /*-{
    // Would set displayLink.paused = paused
  }-*/;

  private static native void invalidateDisplayLinkNative(Object displayLink) /*-{
    // Would call [displayLink invalidate]
  }-*/;

  @FunctionalInterface
  interface FrameCallback {
    void onFrame(long timestamp);
  }
}
