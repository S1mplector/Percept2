package com.jvn.ios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetManager;
import com.jvn.core.assets.AssetPaths;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.OverlayAssetManager;
import com.jvn.core.assets.PackFileAssetManager;
import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.render.RenderSurface;

/**
 * iOS entry point for JVN games via UIApplication delegate.
 *
 * <p>This launcher initializes the engine with iOS-specific rendering (CoreGraphics)
 * and asset loading via NSBundle. Requires Multi-OS Engine (MOE) for Java-to-Objective-C interop.</p>
 */
@SuppressWarnings("NullAway")
public class IosLauncher {
  private static final Logger log = LoggerFactory.getLogger(IosLauncher.class);

  private Engine engine;
  private IosGameLoop gameLoop;
  private RenderSurface surface;

  /**
   * Initialize the launcher with iOS configuration.
   *
   * @param window iOS UIWindow (via MOE reflection/binding)
   * @param config game configuration
   */
  public void initialize(Object window, ApplicationConfig config) {
    try {
      log.info("Initializing iOS launcher for game: {}", config.title());

      // Create render surface from iOS view controller / main view
      surface = new IosRenderSurface(window);

      // Set up asset loading: packed assets with classpath fallback
      AssetManager assetManager = createAssetManager();

      // Initialize the engine
      engine = new Engine(config);
      engine.start();
      AssetCatalog.setDefaultManager(assetManager);

      // Create iOS renderer (CoreGraphics) with asset manager for image loading
      IosRenderer renderer = new IosRenderer(surface, assetManager);

      // Start the game loop
      gameLoop = new IosGameLoop(engine, renderer, surface);
      gameLoop.start();

      log.info("iOS game loop started");
    } catch (Exception e) {
      log.error("Failed to initialize iOS launcher", e);
      throw new RuntimeException("iOS launcher initialization failed", e);
    }
  }

  /**
   * Pause the game (called when app enters background).
   */
  public void pause() {
    if (gameLoop != null) {
      gameLoop.pause();
    }
  }

  /**
   * Resume the game (called when app returns to foreground).
   */
  public void resume() {
    if (gameLoop != null) {
      gameLoop.resume();
    }
  }

  /**
   * Terminate the game (called when app is being killed).
   */
  public void terminate() {
    if (gameLoop != null) {
      gameLoop.stop();
      gameLoop = null;
    }
    if (engine != null) {
      engine.stop();
      engine = null;
    }
    if (surface != null) {
      surface.dispose();
      surface = null;
    }
  }

  private AssetManager createAssetManager() {
    try {
      // Try to load packed assets first (from app bundle)
      String packPath = AssetPaths.BASE + "assets.pack";
      PackFileAssetManager packAssets = new PackFileAssetManager(packPath);
      return new OverlayAssetManager(packAssets, new ClasspathAssetManager());
    } catch (Exception e) {
      log.warn("Failed to load packed assets, falling back to classpath", e);
      return new ClasspathAssetManager();
    }
  }
}
