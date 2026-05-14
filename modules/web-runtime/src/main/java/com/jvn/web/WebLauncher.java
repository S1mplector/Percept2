package com.jvn.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.render.RenderSurface;

/**
 * Entry point for web-based (TeaVM/WASM) execution of JVN games.
 *
 * <p>This launcher initializes the engine with a canvas renderer and manages
 * the game loop on {@code requestAnimationFrame}.</p>
 */
public class WebLauncher {
  private static final Logger log = LoggerFactory.getLogger(WebLauncher.class);

  private WebLauncher() {}

  /**
   * Initialize and launch the game for web execution.
   *
   * @param config game configuration
   * @param canvasElementId the HTML canvas element ID to render into
   */
  public static void launch(ApplicationConfig config, String canvasElementId) {
    try {
      log.info("Initializing web launcher for game: {}", config.title());

      // Create the render surface from the HTML canvas
      RenderSurface surface = new WebCanvasRenderSurface(canvasElementId);

      // Initialize the engine
      Engine engine = new Engine(config);

      // Start the game loop on requestAnimationFrame
      WebGameLoop gameLoop = new WebGameLoop(engine, surface);
      gameLoop.start();

      log.info("Game loop started on requestAnimationFrame");
    } catch (Exception e) {
      log.error("Failed to initialize web launcher", e);
      throw new RuntimeException("Web launcher initialization failed", e);
    }
  }

  /**
   * Exported entry point for JavaScript interop.
   * This will be called from the generated HTML/JS loader.
   */
  public static void startGame(String configJson, String canvasId) {
    // Parse config from JSON and launch
    // TODO: implement JSON parsing (may need custom parser for TeaVM)
    ApplicationConfig config = ApplicationConfig.builder()
        .title("JVN Game")
        .width(1280)
        .height(720)
        .build();
    launch(config, canvasId);
  }
}
