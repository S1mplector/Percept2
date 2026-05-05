package com.jvn.core.scene;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Stack-based scene manager that controls the active {@link Scene} lifecycle.
 *
 * <p>At any given time, only the scene on the <em>top</em> of the stack receives
 * update callbacks from the engine. The manager automatically dispatches
 * lifecycle events ({@code onEnter}, {@code onExit}, {@code onPause},
 * {@code onResume}) as scenes are pushed, popped, or replaced.</p>
 *
 * <h2>Stack Operations</h2>
 * <ul>
 *   <li><b>{@link #push(Scene)}</b> — pauses the current top, pushes the new scene,
 *       and calls {@code onEnter} on it.</li>
 *   <li><b>{@link #pop()}</b> — calls {@code onExit} on the top scene, removes it,
 *       and calls {@code onResume} on the scene below (if any).</li>
 *   <li><b>{@link #replace(Scene)}</b> — pops the top scene (with {@code onExit}),
 *       then pushes the replacement (with {@code onEnter}). Useful for
 *       screen-to-screen transitions without growing the stack.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Not thread-safe — all operations must be called from the engine's update thread.</p>
 *
 * @see Scene
 * @see com.jvn.core.engine.Engine
 */
public class SceneManager {

  /** LIFO scene stack. The element at the top (head) is the active scene. */
  private final Deque<Scene> stack = new ArrayDeque<>();

  /**
   * Push a new scene onto the stack, making it the active scene.
   *
   * <p>If there is already a scene on top, its {@link Scene#onPause()} is called
   * first. Then the new scene's {@link Scene#onEnter()} is invoked.</p>
   *
   * <p><b>Exception safety:</b> the stack mutation (pushing {@code scene}) is
   * always performed before lifecycle callbacks run, and each callback is
   * isolated in its own {@code try/catch}. This guarantees that a misbehaving
   * {@code onPause} or {@code onEnter} cannot leave the manager in a state
   * where the stack disagrees with the scenes' beliefs about their own
   * activeness.</p>
   *
   * @param scene the scene to push; {@code null} is silently ignored
   */
  public void push(Scene scene) {
    if (scene == null) return;
    Scene previous = stack.peek();
    stack.push(scene);
    // Pause the previous top AFTER the stack mutation so that if onPause
    // throws, the new scene is still reachable via peek() and the engine
    // can continue driving updates.
    if (previous != null) {
      safeLifecycle("onPause", previous, Scene::onPause);
    }
    safeLifecycle("onEnter", scene, Scene::onEnter);
  }

  /**
   * Pop the top scene off the stack.
   *
   * <p>Calls {@link Scene#onExit()} on the removed scene. If another scene
   * remains on the stack, its {@link Scene#onResume()} is called so it can
   * reactivate audio, animation, etc.</p>
   *
   * <p><b>Exception safety:</b> the scene is removed from the stack before
   * {@code onExit} fires, and {@code onResume} always runs on the newly
   * exposed scene even if {@code onExit} threw — preventing a situation
   * where the below-scene is left permanently paused because of a crash in
   * the above-scene's exit logic.</p>
   *
   * @return the removed scene, or {@code null} if the stack was empty
   */
  public Scene pop() {
    if (stack.isEmpty()) return null;
    Scene removed = stack.pop();
    try {
      safeLifecycle("onExit", removed, Scene::onExit);
    } finally {
      Scene nowTop = stack.peek();
      if (nowTop != null) {
        safeLifecycle("onResume", nowTop, Scene::onResume);
      }
    }
    return removed;
  }

  /**
   * Replace the current top scene with a new one.
   *
   * <p>Equivalent to a {@link #pop()} followed by a {@link #push(Scene)},
   * but the scene <em>below</em> the old top does <b>not</b> receive
   * {@code onResume}/{@code onPause} calls — only the replaced scene gets
   * {@code onExit} and the new scene gets {@code onEnter}.</p>
   *
   * <p>{@code null} replacements are treated as a plain {@link #pop()} so
   * callers that compute the replacement lazily don't accidentally empty
   * the stack by forgetting a null-check.</p>
   *
   * @param scene the replacement scene; {@code null} falls through to pop
   */
  public void replace(Scene scene) {
    if (scene == null) {
      pop();
      return;
    }
    if (!stack.isEmpty()) {
      Scene removed = stack.pop();
      safeLifecycle("onExit", removed, Scene::onExit);
    }
    // Delegate to push() so the new scene's onEnter runs through the same
    // exception-isolated path. The scene below the replaced top is NOT
    // paused/resumed — this is the documented semantic of replace().
    stack.push(scene);
    safeLifecycle("onEnter", scene, Scene::onEnter);
  }

  /**
   * Execute a {@link Scene} lifecycle callback while swallowing and logging
   * any {@link RuntimeException} so the {@code SceneManager}'s own
   * invariants (stack contents, symmetric pause/resume) are never corrupted
   * by a misbehaving scene.
   *
   * <p>Errors ({@link Error}) are <em>not</em> caught — a
   * {@link OutOfMemoryError} or {@link StackOverflowError} indicates the
   * whole process is in a bad state and should propagate.</p>
   */
  private static void safeLifecycle(String phase, Scene scene, SceneLifecycleCall call) {
    try {
      call.apply(scene);
    } catch (RuntimeException ex) {
      System.err.println("SceneManager: " + phase + " on "
          + scene.getClass().getSimpleName() + " threw "
          + ex.getClass().getSimpleName() + ": " + ex.getMessage());
      ex.printStackTrace(System.err);
    }
  }

  @FunctionalInterface
  private interface SceneLifecycleCall {
    void apply(Scene scene);
  }

  /** @return the current top scene, or {@code null} if the stack is empty */
  public Scene peek() { return stack.peek(); }

  /** @return {@code true} if no scenes are on the stack */
  public boolean isEmpty() { return stack.isEmpty(); }
}
