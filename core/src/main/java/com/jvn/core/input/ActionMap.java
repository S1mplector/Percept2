package com.jvn.core.input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Maps logical action names to physical {@link InputCode} bindings and queries
 * the live {@link Input} state through those bindings.
 *
 * <p>{@code ActionMap} is the primary way game logic checks for input.
 * Instead of hard-coding key names, code queries semantic actions:</p>
 * <pre>{@code
 * if (actionMap.wasPressed("advance")) { ... }
 * }</pre>
 *
 * <p>Bindings can be modified at runtime, exported to an
 * {@link ActionBindingProfile} for persistence, and re-loaded later.</p>
 *
 * <h2>Fluent API</h2>
 * <p>All {@code bind*} and {@code unbind*} methods return {@code this},
 * allowing chained calls:</p>
 * <pre>{@code
 * actionMap.bindKey("jump", "SPACE")
 *          .bindKey("jump", "W")
 *          .bindGamepadButton("jump", 0, "A");
 * }</pre>
 *
 * @see Input
 * @see InputCode
 * @see ActionBindingProfile
 */
public class ActionMap {

  /** The live input state that bindings are tested against. */
  private final Input input;

  /** Action name → set of bound input codes. */
  private final Map<String, Set<InputCode>> bindings = new HashMap<>();

  /**
   * Construct an action map wired to the given input state.
   *
   * @param input the live input tracker
   */
  public ActionMap(Input input) { this.input = input; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Binding helpers (fluent)
  // ──────────────────────────────────────────────────────────────────────────

  /** Bind a keyboard key to an action. */
  public ActionMap bindKey(String action, String keyName) {
    return bind(action, InputCode.key(keyName));
  }

  /** Bind a mouse button to an action. */
  public ActionMap bindMouse(String action, int button) {
    return bind(action, InputCode.mouse(button));
  }

  /** Bind a gamepad digital button to an action. */
  public ActionMap bindGamepadButton(String action, int pad, String button) {
    return bind(action, InputCode.gamepadButton(pad, button));
  }

  /** Bind a gamepad analog axis to an action. */
  public ActionMap bindGamepadAxis(String action, int pad, String axis) {
    return bind(action, InputCode.gamepadAxis(pad, axis));
  }

  /** Remove a keyboard key binding from an action. */
  public ActionMap unbindKey(String action, String keyName) {
    return unbind(action, InputCode.key(keyName));
  }

  /** Remove a mouse button binding from an action. */
  public ActionMap unbindMouse(String action, int button) {
    return unbind(action, InputCode.mouse(button));
  }

  /** Remove a gamepad button binding from an action. */
  public ActionMap unbindGamepadButton(String action, int pad, String button) {
    return unbind(action, InputCode.gamepadButton(pad, button));
  }

  /** Remove a gamepad axis binding from an action. */
  public ActionMap unbindGamepadAxis(String action, int pad, String axis) {
    return unbind(action, InputCode.gamepadAxis(pad, axis));
  }

  /**
   * Add a binding (internal helper).
   *
   * @return {@code this} for fluent chaining
   */
  private ActionMap bind(String action, InputCode code) {
    if (action == null || code == null) return this;
    bindings.computeIfAbsent(action, k -> new HashSet<>()).add(code);
    return this;
  }

  /**
   * Remove a binding (internal helper).
   *
   * @return {@code this} for fluent chaining
   */
  private ActionMap unbind(String action, InputCode code) {
    Set<InputCode> set = bindings.get(action);
    if (set != null) set.remove(code);
    return this;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Action queries
  // ──────────────────────────────────────────────────────────────────────────

  /** @return {@code true} if any binding for the action is currently held */
  public boolean isDown(String action) {
    return test(action, input::isDown);
  }

  /** @return {@code true} if any binding for the action was pressed this frame */
  public boolean wasPressed(String action) {
    return test(action, input::wasPressed);
  }

  /** @return {@code true} if any binding for the action was released this frame */
  public boolean wasReleased(String action) {
    return test(action, input::wasReleased);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Profile import / export
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Export the current bindings to a serialisable profile snapshot.
   *
   * @return a new {@link ActionBindingProfile} with a copy of all bindings
   */
  public ActionBindingProfile toProfile() {
    ActionBindingProfile profile = new ActionBindingProfile();
    bindings.forEach((action, codes) -> {
      for (InputCode c : codes) profile.add(action, c);
    });
    return profile;
  }

  /**
   * Replace all bindings with those from the given profile.
   *
   * @param profile the profile to load; {@code null} clears all bindings
   */
  public void loadProfile(ActionBindingProfile profile) {
    bindings.clear();
    if (profile == null) return;
    profile.bindings().forEach((action, codes) -> bindings.put(action, new HashSet<>(codes)));
  }

  /**
   * Check whether a specific input code is bound to a given action.
   *
   * @param action the action name
   * @param code   the input code
   * @return {@code true} if the code is in the action's binding set
   */
  public boolean matches(String action, InputCode code) {
    if (action == null || code == null) return false;
    Set<InputCode> set = bindings.get(action);
    return set != null && set.contains(code);
  }

  /**
   * Test whether any binding for the given action satisfies the predicate.
   *
   * @param action    the action name
   * @param predicate test function (e.g. {@code input::isDown})
   * @return {@code true} if at least one bound code passes
   */
  private boolean test(String action, Predicate<InputCode> predicate) {
    Set<InputCode> set = bindings.get(action);
    if (set == null) return false;
    for (InputCode code : set) {
      if (predicate.test(code)) return true;
    }
    return false;
  }
}
