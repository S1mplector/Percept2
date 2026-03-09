package com.jvn.core.scene;

/**
 * Lifecycle contract for a single game scene (screen, level, menu, etc.).
 *
 * <p>Scenes are managed by a {@link SceneManager} stack. Only the <em>top</em>
 * scene on the stack receives update callbacks from the {@link com.jvn.core.engine.Engine}.
 * The lifecycle flows as follows:</p>
 *
 * <pre>
 *   ┌──────────┐  push   ┌──────────┐  update loop  ┌──────────┐  pop    ┌──────────┐
 *   │ (none)   │───────→ │ onEnter  │──────────────→│ running  │──────→  │ onExit   │
 *   └──────────┘         └──────────┘               └──────────┘         └──────────┘
 *                                                      │     ▲
 *                                              push    │     │  pop (child)
 *                                              child   ▼     │
 *                                                   ┌──────────┐
 *                                                   │ onPause  │
 *                                                   │   ...    │
 *                                                   │ onResume │
 *                                                   └──────────┘
 * </pre>
 *
 * <p>Implementors must provide {@link #update(long)}; all other methods have
 * default no-op implementations so scenes can override only what they need.</p>
 *
 * @see SceneManager
 * @see com.jvn.core.engine.Engine#update(long)
 */
public interface Scene {

  /**
   * Called once when this scene is pushed onto the stack and becomes active.
   * Use for resource loading, UI setup, and initial state preparation.
   */
  default void onEnter() {}

  /**
   * Called once when this scene is popped off the stack.
   * Use for resource cleanup and state teardown.
   */
  default void onExit() {}
  /**
   * Called when another scene is pushed on top of this one.
   * Use to pause audio/animation without tearing down state.
   */
  default void onPause() {}
  /**
   * Called when this scene becomes active again after a pop.
   */
  default void onResume() {}

  /**
   * Fixed-rate update for deterministic logic (physics, gameplay simulation).
   * Only called when the engine has a fixed timestep configured ({@code fixedUpdateMs > 0}).
   * Each call receives exactly {@code fixedUpdateMs} as its delta.
   * May be called zero or more times per frame depending on accumulated time.
   */
  default void fixedUpdate(long deltaMs) {}

  /**
   * Variable-rate update called once per frame with the (possibly smoothed) frame delta.
   * Use for animation, input-driven logic, UI, tweens, and anything that should
   * run at the display refresh rate rather than the physics rate.
   */
  void update(long deltaMs);

  /**
   * Called once per frame after {@link #update(long)}.
   * Ideal for camera follow, post-update corrections, and anything that depends
   * on finalized entity positions from the current frame.
   */
  default void lateUpdate(long deltaMs) {}
}
