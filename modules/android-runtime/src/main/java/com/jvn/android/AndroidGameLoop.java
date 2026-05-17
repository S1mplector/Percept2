package com.jvn.android;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.engine.Engine;
import com.jvn.render.RenderSurface;

/**
 * Game loop implementation for Android using a background rendering thread.
 *
 * <p>Unlike the web (which uses requestAnimationFrame), Android uses a dedicated
 * rendering thread that continuously updates and renders the game. The loop respects
 * Activity lifecycle events (pause/resume).</p>
 */
@SuppressWarnings("NullAway")
public class AndroidGameLoop {
  private static final Logger log = LoggerFactory.getLogger(AndroidGameLoop.class);
  private static final long TARGET_FRAME_TIME_NS = (long) (1_000_000_000.0 / 60.0); // 60 FPS target

  private final Engine engine;
  private final AndroidRenderer renderer;
  private final RenderSurface surface;
  private Thread gameThread;
  private volatile boolean running = false;
  private volatile boolean paused = false;
  private long lastFrameTimeNs = 0;

  public AndroidGameLoop(Engine engine, AndroidRenderer renderer, RenderSurface surface) {
    this.engine = engine;
    this.renderer = renderer;
    this.surface = surface;
  }

  /**
   * Start the game loop on a background thread.
   */
  public void start() {
    if (running) return;
    running = true;
    gameThread = new Thread(this::runGameLoop);
    gameThread.setName("JVN-GameLoop");
    gameThread.setPriority(Thread.NORM_PRIORITY);
    gameThread.start();
    log.info("Android game loop started on background thread");
  }

  /**
   * Pause the game (Activity.onPause).
   */
  public void pause() {
    paused = true;
    log.info("Game paused");
  }

  /**
   * Resume the game (Activity.onResume).
   */
  public void resume() {
    paused = false;
    log.info("Game resumed");
  }

  /**
   * Stop the game loop (Activity.onDestroy).
   */
  public void stop() {
    running = false;
    if (gameThread != null && gameThread != Thread.currentThread()) {
      try {
        gameThread.join(5000); // Wait up to 5 seconds for the thread to finish
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    gameThread = null;
    log.info("Android game loop stopped");
  }

  private void runGameLoop() {
    log.info("Game loop thread running");
    lastFrameTimeNs = System.nanoTime();

    while (running && surface.isValid()) {
      try {
        if (!paused) {
          // Calculate delta time
          long currentTimeNs = System.nanoTime();
          long deltaMs = (currentTimeNs - lastFrameTimeNs) / 1_000_000;
          lastFrameTimeNs = currentTimeNs;

          // Update engine (expects milliseconds)
          engine.update(deltaMs);

          // Render frame (placeholder; actual rendering would be done by renderer)
          // surface.present();
        } else {
          // Paused: sleep to avoid busy-waiting
          Thread.sleep(16); // ~60 FPS sleep
        }

        // Frame rate limiting
        long elapsedNs = System.nanoTime() - lastFrameTimeNs;
        long sleepNs = TARGET_FRAME_TIME_NS - elapsedNs;
        if (sleepNs > 0) {
          Thread.sleep(sleepNs / 1_000_000, (int) (sleepNs % 1_000_000));
        }
      } catch (InterruptedException e) {
        log.debug("Game loop interrupted");
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.error("Error in Android game loop", e);
      }
    }

    log.info("Game loop thread exiting");
  }
}
