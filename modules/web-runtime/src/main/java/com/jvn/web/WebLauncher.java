package com.jvn.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.scenerender.vn.VnRenderer;

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

      VnScenario scenario = WebFixtureScenario.load();
      VnScene vnScene = new VnScene(scenario);
      vnScene.setAudioFacade(new NoopAudioFacade());
      vnScene.setPersistenceBackend(new WebLocalStoragePersistenceBackend());
      vnScene.onEnter();
      // Instant text for this fixed bootstrap fixture — it exists to prove rendering, not
      // typing speed, and the webSmoke harness only drives a couple of animation frames.
      vnScene.getState().getSettings().setTextSpeed(0);
      engine.scenes().push(vnScene);

      VnRenderer vnRenderer = new VnRenderer(renderer);

      com.jvn.core.input.ActionMap actionMap = new com.jvn.core.input.ActionMap(new com.jvn.core.input.Input());
      actionMap.loadProfile(com.jvn.core.input.InputActions.defaultProfile());
      com.jvn.scenerender.input.SceneInputRouter sceneInputRouter =
          new com.jvn.scenerender.input.SceneInputRouter(
              new com.jvn.scenerender.menu.MenuRenderer(renderer), vnRenderer, new UnsupportedMenuSceneFactory());

      WebGameLoop gameLoop = new WebGameLoop(engine, surface);
      gameLoop.setFrameRenderer(() -> vnRenderer.render(
          vnScene.getState(), vnScene.getScenario(), surface.getWidth(), surface.getHeight()));
      WebRuntimeSession session =
          new WebRuntimeSession(engine, surface, renderer, gameLoop, vnScene, actionMap, sceneInputRouter);
      gameLoop.start();

      log.info("Game loop started on requestAnimationFrame");
      return session;
    } catch (RuntimeException e) {
      log.error("Failed to initialize web launcher", e);
      throw e;
    } catch (java.io.IOException e) {
      log.error("Failed to load web fixture scenario", e);
      throw new IllegalStateException("Failed to load web fixture scenario", e);
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
}
