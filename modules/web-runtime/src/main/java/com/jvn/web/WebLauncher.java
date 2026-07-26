package com.jvn.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;

/**
 * Entry point for TeaVM JavaScript execution of JVN's browser bootstrap.
 *
 * <p>This launcher initializes the engine with a canvas renderer and manages
 * the game loop on {@code requestAnimationFrame}.</p>
 */
public final class WebLauncher {
  private static final Logger log = LoggerFactory.getLogger(WebLauncher.class);

  private WebLauncher() {}

  /**
   * Initialize and launch the game for web execution.
   *
   * @param config game configuration
   * @param canvasElementId the HTML canvas element ID to render into
   * @return handles for the live browser runtime
   */
  public static WebRuntimeSession launch(ApplicationConfig config, String canvasElementId) {
    if (config == null) throw new IllegalArgumentException("Web application config must not be null");
    try {
      log.info("Initializing web launcher for game: {}", config.title());

      WebCanvasRenderSurface surface =
          new WebCanvasRenderSurface(canvasElementId, config.width(), config.height());
      WebRenderer renderer = new WebRenderer(surface);

      Engine engine = new Engine(config);
      engine.start();

      WebGameLoop gameLoop = new WebGameLoop(engine, surface);
      gameLoop.setFrameRenderer(() -> renderBootstrapFrame(renderer, config));
      WebRuntimeSession session = new WebRuntimeSession(engine, surface, renderer, gameLoop);
      gameLoop.start();

      log.info("Game loop started on requestAnimationFrame");
      return session;
    } catch (RuntimeException e) {
      log.error("Failed to initialize web launcher", e);
      throw e;
    }
  }

  /**
   * Start from serialized browser configuration.
   *
   * @return handles for the live browser runtime
   */
  public static WebRuntimeSession startGame(String configJson, String canvasId) {
    return launch(parseConfig(configJson), canvasId);
  }

  /**
   * Parse the JavaScript launcher's JSON configuration.
   *
   * <p>Supported fields are {@code title}, {@code width}, {@code height},
   * {@code fixedUpdateMs}, {@code fixedUpdateMaxSteps}, and {@code timeScale}. Missing fields use
   * web runtime defaults and unknown fields are ignored for forward compatibility. Malformed JSON
   * and invalid known-field values fail with an {@link IllegalArgumentException}.</p>
   *
   * @param configJson top-level JSON object, or blank for web runtime defaults
   * @return validated engine configuration
   */
  public static ApplicationConfig parseConfig(String configJson) {
    return WebApplicationConfigParser.parse(configJson);
  }

  private static void renderBootstrapFrame(WebRenderer renderer, ApplicationConfig config) {
    renderer.clear(0.025, 0.03, 0.045, 1.0);
    renderer.setFill(0.91, 0.84, 0.65, 1.0);
    renderer.drawText(config.title(), 42, 64, 28, true);
    renderer.setFill(0.67, 0.72, 0.8, 1.0);
    renderer.drawText("JVN Canvas 2D runtime", 42, 98, 16, false);
    renderer.setFill(0.48, 0.53, 0.62, 1.0);
    renderer.drawText("Engine loop online · game scene bootstrap pending", 42, 128, 13, false);
  }
}
