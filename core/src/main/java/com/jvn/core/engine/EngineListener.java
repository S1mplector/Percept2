package com.jvn.core.engine;

/**
 * Callback interface for observing engine update phases.
 * Register via {@link Engine#addListener(EngineListener)}.
 *
 * <p>All methods have default no-op implementations so listeners
 * can override only the phases they care about.</p>
 */
public interface EngineListener {
  /**
   * Called at the very start of each frame, before any game logic runs.
   * Useful for profiling start markers, input recording, or debug overlays.
   *
   * @param rawDeltaMs the raw (unclamped, unsmoothed) frame delta
   */
  default void preUpdate(long rawDeltaMs) {}

  /**
   * Called at the end of each frame, after all game logic and lateUpdate.
   * Useful for profiling end markers, frame counters, or diagnostic output.
   *
   * @param effectiveDeltaMs the effective (clamped, smoothed, scaled) frame delta
   */
  default void postUpdate(long effectiveDeltaMs) {}
}
