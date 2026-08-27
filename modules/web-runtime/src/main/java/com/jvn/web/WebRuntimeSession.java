package com.jvn.web;

import com.jvn.core.engine.Engine;
import com.jvn.core.vn.VnScene;
import org.jspecify.annotations.Nullable;

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
  private final @Nullable VnScene vnScene;
  private boolean closed;

  WebRuntimeSession(
      Engine engine,
      WebCanvasRenderSurface surface,
      WebRenderer renderer,
      WebGameLoop gameLoop,
      @Nullable VnScene vnScene) {
    this.engine = engine;
    this.surface = surface;
    this.renderer = renderer;
    this.gameLoop = gameLoop;
    this.vnScene = vnScene;
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

  /**
   * The active VN scene, if the launched bootstrap pushed one. Exposed only so a test harness
   * can synthesize {@code VnScene.advanceFromClick()} in the absence of real browser click/input
   * routing (sub-project 3, not built yet) — not a general-purpose runtime API. Real click
   * handling should route through the engine's input abstraction, not this getter, once it
   * exists.
   */
  public @Nullable VnScene vnScene() {
    return vnScene;
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
