package com.jvn.android;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.assets.AssetManager;
import com.jvn.core.assets.AssetPaths;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.OverlayAssetManager;
import com.jvn.core.assets.PackFileAssetManager;
import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.render.RenderSurface;

/**
 * Android entry point and Activity launcher for JVN games.
 *
 * <p>This class initializes the engine with Android-specific rendering and asset loading.
 * In a real Android project, this would extend {@code android.app.Activity}; for now,
 * it's a POJO that can be instantiated by an Activity wrapper.</p>
 *
 * <p>Asset loading strategy:
 * <ol>
 *   <li>Primary: PackFileAssetManager (assets.pack bundled in APK)
 *   <li>Fallback: ClasspathAssetManager (default assets)
 * </ol>
 * </p>
 */
public class AndroidLauncher {
  private static final Logger log = LoggerFactory.getLogger(AndroidLauncher.class);

  private Engine engine;
  private AndroidGameLoop gameLoop;

  /**
   * Initialize the launcher with an Android application context.
   *
   * @param appContext Android application context (would be android.content.Context)
   * @param config game configuration
   */
  public void initialize(Object appContext, ApplicationConfig config) {
    try {
      log.info("Initializing Android launcher for game: {}", config.title());

      // Create render surface from Android SurfaceView
      RenderSurface surface = new AndroidRenderSurface(appContext);

      // Set up asset loading: packed assets with classpath fallback
      AssetManager assetManager = createAssetManager();

      // Initialize the engine
      engine = new Engine(config);
      // TODO: attach asset manager to engine

      // Create Android renderer with asset manager for image loading
      AndroidRenderer renderer = new AndroidRenderer(surface, assetManager);

      // Start the game loop
      gameLoop = new AndroidGameLoop(engine, renderer, surface);
      gameLoop.start();

      log.info("Android game loop started");
    } catch (Exception e) {
      log.error("Failed to initialize Android launcher", e);
      throw new RuntimeException("Android launcher initialization failed", e);
    }
  }

  /**
   * Pause the game (called from Activity.onPause).
   */
  public void pause() {
    if (gameLoop != null) {
      gameLoop.pause();
    }
  }

  /**
   * Resume the game (called from Activity.onResume).
   */
  public void resume() {
    if (gameLoop != null) {
      gameLoop.resume();
    }
  }

  /**
   * Destroy the game (called from Activity.onDestroy).
   */
  public void destroy() {
    if (gameLoop != null) {
      gameLoop.stop();
    }
    if (engine != null) {
      // TODO: cleanup engine resources
    }
  }

  private AssetManager createAssetManager() {
    try {
      // Try to load packed assets first
      String packPath = AssetPaths.BASE + "assets.pack";
      PackFileAssetManager packAssets = new PackFileAssetManager(packPath);
      return new OverlayAssetManager(packAssets, new ClasspathAssetManager());
    } catch (Exception e) {
      log.warn("Failed to load packed assets, falling back to classpath", e);
      return new ClasspathAssetManager();
    }
  }
}
