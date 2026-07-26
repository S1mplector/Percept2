package com.jvn.web;

import com.jvn.core.engine.Engine;

/**
 * Live browser runtime handles returned by {@link WebLauncher}.
 *
 * <p>The session exposes the engine and Canvas 2D renderer so a web game bootstrap can push its
 * initial scene and replace the default frame renderer as browser support matures.</p>
 */
public final class WebRuntimeSession implements AutoCloseable {
  private final Engine engine;
  private final WebCanvasRenderSurface surface;
  private final WebRenderer renderer;
  private final WebGameLoop gameLoop;
  private boolean closed;

  WebRuntimeSession(
      Engine engine,
      WebCanvasRenderSurface surface,
      WebRenderer renderer,
      WebGameLoop gameLoop) {
    this.engine = engine;
    this.surface = surface;
    this.renderer = renderer;
    this.gameLoop = gameLoop;
  }

  public Engine engine() {
    return engine;
  }

  public WebCanvasRenderSurface surface() {
    return surface;
  }

  public WebRenderer renderer() {
    return renderer;
  }

  public WebGameLoop gameLoop() {
    return gameLoop;
  }

  /** Replace the callback responsible for drawing each browser frame. */
  public void setFrameRenderer(Runnable frameRenderer) {
    if (closed) throw new IllegalStateException("Web runtime session is closed");
    gameLoop.setFrameRenderer(frameRenderer);
  }

  public boolean isRunning() {
    return !closed && gameLoop.isRunning();
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    gameLoop.stop();
    surface.dispose();
  }
}
