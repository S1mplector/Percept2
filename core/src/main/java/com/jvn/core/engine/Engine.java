package com.jvn.core.engine;

import java.util.ArrayList;
import java.util.List;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.input.Input;
import com.jvn.core.menu.MenuActionHandler;
import com.jvn.core.scene.Scene;
import com.jvn.core.scene.SceneManager;
import com.jvn.core.tween.TweenRunner;
import com.jvn.core.vn.VnInteropFactory;

public class Engine {
  private final ApplicationConfig config;
  private boolean started;
  private boolean paused;
  private final SceneManager sceneManager = new SceneManager();
  private final Input input = new Input();
  private final TweenRunner tweens = new TweenRunner();
  private final FrameStats frameStats = new FrameStats();
  private final List<EngineListener> listeners = new ArrayList<>();
  private VnInteropFactory vnInteropFactory;
  private MenuActionHandler menuActionHandler;

  // --- Timing parameters ---
  private long maxDeltaMs = 75;
  private double deltaSmoothing = 0.1;
  private double smoothedDeltaMs = -1.0;
  private long fixedUpdateMs = 0;
  private int maxFixedSteps = 5;
  private double accumulatorMs = 0.0;
  private double timeScale = 1.0;

  /**
   * Interpolation alpha for rendering.
   * When using a fixed timestep, this is the fraction of a fixed step
   * remaining in the accumulator after the last physics tick.
   * Renderers can use this to interpolate between the previous and current
   * physics state for smooth visuals: {@code lerp(prevState, curState, alpha)}.
   * Value is always in [0.0, 1.0]. When fixed timestep is disabled, this is 0.0.
   */
  private double interpolationAlpha = 0.0;

  public Engine(ApplicationConfig config) {
    this.config = config;
    if (config != null) {
      setFixedUpdateStepMs(config.fixedUpdateMs(), config.fixedUpdateMaxSteps());
      setTimeScale(config.timeScale());
    }
  }

  public void start() {
    this.started = true;
  }

  public void stop() {
    this.started = false;
  }

  public boolean isStarted() {
    return started;
  }

  // --- Pause ---

  /**
   * Pause the engine. While paused, game time is frozen — no scene updates,
   * fixed updates, tweens, or late updates run. Input is still processed
   * and {@link #input()} remains responsive, so pause menus can react.
   * Listeners still receive pre/post update callbacks.
   */
  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  public boolean isPaused() {
    return paused;
  }

  // --- Config ---

  public ApplicationConfig getConfig() {
    return config;
  }

  // --- Main update loop ---

  /**
   * Main frame entry point. Called once per display frame by the renderer.
   *
   * <p><b>Frame pipeline:</b></p>
   * <ol>
   *   <li>Record raw delta for stats; notify listeners ({@code preUpdate})</li>
   *   <li>Clamp &rarr; smooth &rarr; scale delta</li>
   *   <li>If paused or not started: skip game logic, still process input</li>
   *   <li><b>Fixed update phase</b> — accumulate time, call {@link Scene#fixedUpdate(long)}
   *       at fixed intervals. Compute {@link #getInterpolationAlpha()} from remainder.</li>
   *   <li><b>Variable update phase</b> — update tweens, call {@link Scene#update(long)} once</li>
   *   <li><b>Late update phase</b> — call {@link Scene#lateUpdate(long)} once</li>
   *   <li>End input frame; notify listeners ({@code postUpdate})</li>
   * </ol>
   */
  public void update(long deltaMs) {
    // --- Pre-frame ---
    frameStats.record(deltaMs);
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).preUpdate(deltaMs);
    }

    long clamped = clampDelta(deltaMs);
    long smoothed = smoothDelta(clamped);
    long effective = applyTimeScale(smoothed);

    if (!started || paused) {
      interpolationAlpha = 0.0;
      input.endFrame();
      for (int i = 0; i < listeners.size(); i++) {
        listeners.get(i).postUpdate(effective);
      }
      return;
    }

    Scene current = sceneManager.peek();

    // --- Fixed update phase ---
    if (fixedUpdateMs > 0) {
      accumulatorMs += effective;
      int steps = 0;
      while (accumulatorMs >= fixedUpdateMs && steps < maxFixedSteps) {
        if (current != null) {
          current.fixedUpdate(fixedUpdateMs);
        }
        accumulatorMs -= fixedUpdateMs;
        steps++;
      }
      if (steps == maxFixedSteps && accumulatorMs > fixedUpdateMs) {
        accumulatorMs = fixedUpdateMs;
      }
      interpolationAlpha = accumulatorMs / fixedUpdateMs;
    } else {
      interpolationAlpha = 0.0;
    }

    // --- Variable update phase ---
    tweens.update(effective);
    if (current != null) {
      current.update(effective);
    }

    // --- Late update phase ---
    if (current != null) {
      current.lateUpdate(effective);
    }

    // --- Post-frame ---
    input.endFrame();
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).postUpdate(effective);
    }
  }

  // --- Accessors ---

  public SceneManager scenes() {
    return sceneManager;
  }

  public Input input() {
    return input;
  }

  public TweenRunner tweens() {
    return tweens;
  }

  public FrameStats frameStats() {
    return frameStats;
  }

  /**
   * Interpolation alpha for fixed-timestep rendering.
   * When using a fixed timestep, this is the fractional progress toward the
   * next fixed tick (range [0.0, 1.0]). Use it to interpolate visual state
   * between the last two physics snapshots for stutter-free rendering:
   * <pre>{@code
   * double renderX = prevX + (curX - prevX) * engine.getInterpolationAlpha();
   * }</pre>
   * Returns 0.0 when fixed timestep is disabled.
   */
  public double getInterpolationAlpha() {
    return interpolationAlpha;
  }

  // --- Timing configuration ---

  public void setMaxDeltaMs(long ms) {
    this.maxDeltaMs = ms <= 0 ? 0 : ms;
  }

  public void setDeltaSmoothing(double alpha) {
    if (Double.isNaN(alpha) || Double.isInfinite(alpha) || alpha < 0) alpha = 0;
    if (alpha > 1) alpha = 1;
    this.deltaSmoothing = alpha;
  }

  public void setFixedUpdateStepMs(long stepMs, int maxSteps) {
    this.fixedUpdateMs = stepMs <= 0 ? 0 : stepMs;
    this.maxFixedSteps = Math.max(1, maxSteps);
  }

  /**
   * Set the global time scale. Multiplies the effective delta after clamping
   * and smoothing. Use for slow-motion ({@code 0.5}), fast-forward ({@code 2.0}),
   * or freeze ({@code 0.0}) effects.
   *
   * @param scale time multiplier; clamped to [0.0, 10.0]
   */
  public void setTimeScale(double scale) {
    if (Double.isNaN(scale) || Double.isInfinite(scale) || scale < 0) scale = 0;
    if (scale > 10.0) scale = 10.0;
    this.timeScale = scale;
  }

  public double getTimeScale() {
    return timeScale;
  }

  // --- Listener management ---

  public void addListener(EngineListener listener) {
    if (listener != null && !listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  public void removeListener(EngineListener listener) {
    listeners.remove(listener);
  }

  // --- Internal helpers ---

  private long clampDelta(long deltaMs) {
    if (deltaMs < 0) return 0;
    if (maxDeltaMs > 0 && deltaMs > maxDeltaMs) return maxDeltaMs;
    return deltaMs;
  }

  private long smoothDelta(long deltaMs) {
    if (deltaSmoothing <= 0) return deltaMs;
    if (smoothedDeltaMs < 0) smoothedDeltaMs = deltaMs;
    smoothedDeltaMs = smoothedDeltaMs + (deltaMs - smoothedDeltaMs) * deltaSmoothing;
    if (smoothedDeltaMs < 0) smoothedDeltaMs = 0;
    return Math.round(smoothedDeltaMs);
  }

  private long applyTimeScale(long deltaMs) {
    if (timeScale == 1.0) return deltaMs;
    if (timeScale == 0.0) return 0;
    return Math.round(deltaMs * timeScale);
  }

  // --- Interop / menu wiring ---

  public void setVnInteropFactory(VnInteropFactory f) { this.vnInteropFactory = f; }
  public VnInteropFactory getVnInteropFactory() { return vnInteropFactory; }

  public void setMenuActionHandler(MenuActionHandler handler) { this.menuActionHandler = handler; }
  public MenuActionHandler getMenuActionHandler() { return menuActionHandler; }
}
