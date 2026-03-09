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
   * @param scene the scene to push; {@code null} is silently ignored
   */
  public void push(Scene scene) {
    if (scene == null) return;
    if (!stack.isEmpty()) {
      stack.peek().onPause();
    }
    stack.push(scene);
    scene.onEnter();
  }

  /**
   * Pop the top scene off the stack.
   *
   * <p>Calls {@link Scene#onExit()} on the removed scene. If another scene
   * remains on the stack, its {@link Scene#onResume()} is called so it can
   * reactivate audio, animation, etc.</p>
   *
   * @return the removed scene, or {@code null} if the stack was empty
   */
  public Scene pop() {
    if (stack.isEmpty()) return null;
    Scene s = stack.pop();
    s.onExit();
    if (!stack.isEmpty()) stack.peek().onResume();
    return s;
  }

  /**
   * Replace the current top scene with a new one.
   *
   * <p>Equivalent to a {@link #pop()} followed by a {@link #push(Scene)},
   * but the scene <em>below</em> the old top does <b>not</b> receive
   * {@code onResume}/{@code onPause} calls — only the replaced scene gets
   * {@code onExit} and the new scene gets {@code onEnter}.</p>
   *
   * @param scene the replacement scene
   */
  public void replace(Scene scene) {
    if (!stack.isEmpty()) {
      Scene s = stack.pop();
      s.onExit();
    }
    push(scene);
  }

  /** @return the current top scene, or {@code null} if the stack is empty */
  public Scene peek() { return stack.peek(); }

  /** @return {@code true} if no scenes are on the stack */
  public boolean isEmpty() { return stack.isEmpty(); }
}
