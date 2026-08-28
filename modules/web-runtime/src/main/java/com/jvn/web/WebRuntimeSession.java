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
  private final com.jvn.core.input.ActionMap actionMap;
  private final com.jvn.scenerender.input.SceneInputRouter sceneInputRouter;
  private boolean closed;

  WebRuntimeSession(
      Engine engine,
      WebCanvasRenderSurface surface,
      WebRenderer renderer,
      WebGameLoop gameLoop,
      @Nullable VnScene vnScene,
      com.jvn.core.input.ActionMap actionMap,
      com.jvn.scenerender.input.SceneInputRouter sceneInputRouter) {
    this.engine = engine;
    this.surface = surface;
    this.renderer = renderer;
    this.gameLoop = gameLoop;
    this.vnScene = vnScene;
    this.actionMap = actionMap;
    this.sceneInputRouter = sceneInputRouter;
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
   * The active VN scene, if the launched bootstrap pushed one. Exposed for state
   * inspection and used internally by WebMain's DOM event listeners (via
   * sceneInputRouter()) to route clicks/keys into the running scene.
   */
  public @Nullable VnScene vnScene() {
    return vnScene;
  }

  /** The {@link com.jvn.core.input.ActionMap} bound to this session's default key-binding profile. */
  public com.jvn.core.input.ActionMap actionMap() {
    return actionMap;
  }

  /** The {@link com.jvn.scenerender.input.SceneInputRouter} shared by this session's DOM listeners. */
  public com.jvn.scenerender.input.SceneInputRouter sceneInputRouter() {
    return sceneInputRouter;
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
