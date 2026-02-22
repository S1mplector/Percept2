package com.jvn.core.engine;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.scene.Scene;
import com.jvn.core.scene.SceneManager;
import com.jvn.core.input.Input;
import com.jvn.core.menu.MenuActionHandler;
import com.jvn.core.tween.TweenRunner;
import com.jvn.core.vn.VnInteropFactory;

public class Engine {
  private final ApplicationConfig config;
  private boolean started;
  private final SceneManager sceneManager = new SceneManager();
  private final Input input = new Input();
  private final TweenRunner tweens = new TweenRunner();
  private VnInteropFactory vnInteropFactory;
  private MenuActionHandler menuActionHandler;
  private long maxDeltaMs = 75; // clamp to avoid huge simulation jumps
  private double deltaSmoothing = 0.1; // exponential smoothing factor [0..1]; 0 disables smoothing
  private double smoothedDeltaMs = -1.0;
  private long fixedUpdateMs = 0; // 0 = disabled
  private int maxFixedSteps = 5;
  private double accumulatorMs = 0.0;

  public Engine(ApplicationConfig config) {
    this.config = config;
    if (config != null) {
      setFixedUpdateStepMs(config.fixedUpdateMs(), config.fixedUpdateMaxSteps());
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

  public ApplicationConfig getConfig() {
    return config;
  }

   public void update(long deltaMs) {
     long clamped = clampDelta(deltaMs);
     long effective = smoothDelta(clamped);
     if (!started) {
       input.endFrame();
       return;
     }
     if (fixedUpdateMs > 0) {
       accumulatorMs += effective;
       int steps = 0;
       while (accumulatorMs >= fixedUpdateMs && steps < maxFixedSteps) {
         tick(fixedUpdateMs);
         accumulatorMs -= fixedUpdateMs;
         steps++;
       }
       if (steps == maxFixedSteps && accumulatorMs > fixedUpdateMs) {
         accumulatorMs = fixedUpdateMs;
       }
     } else {
       tick(effective);
     }
     input.endFrame();
   }

   public SceneManager scenes() {
     return sceneManager;
   }

   public Input input() {
     return input;
   }

  public TweenRunner tweens() {
    return tweens;
  }

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

  private long clampDelta(long deltaMs) {
    if (deltaMs < 0) return 0;
    if (maxDeltaMs > 0 && deltaMs > maxDeltaMs) return maxDeltaMs;
    return deltaMs;
  }

  private void tick(long deltaMs) {
    tweens.update(deltaMs);
    Scene current = sceneManager.peek();
    if (current != null) {
      current.update(deltaMs);
    }
  }

  private long smoothDelta(long deltaMs) {
    if (deltaSmoothing <= 0) return deltaMs;
    if (smoothedDeltaMs < 0) smoothedDeltaMs = deltaMs;
    smoothedDeltaMs = smoothedDeltaMs + (deltaMs - smoothedDeltaMs) * deltaSmoothing;
    if (smoothedDeltaMs < 0) smoothedDeltaMs = 0;
    return Math.round(smoothedDeltaMs);
  }

  public void setVnInteropFactory(VnInteropFactory f) { this.vnInteropFactory = f; }
  public VnInteropFactory getVnInteropFactory() { return vnInteropFactory; }

  public void setMenuActionHandler(MenuActionHandler handler) { this.menuActionHandler = handler; }
  public MenuActionHandler getMenuActionHandler() { return menuActionHandler; }
}
