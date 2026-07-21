package com.jvn.plugin.api.animation;

import java.util.Map;

/**
 * Immutable input supplied while evaluating a contributed easing curve.
 * @param progress normalized input progress, clamped to {@code [0, 1]}
 * @param parameters immutable resolved parameter values
 */
public record AnimationEasingFrame(double progress, Map<String, Double> parameters) {
  /** Normalizes an evaluation frame. */
  public AnimationEasingFrame {
    progress = Math.max(0.0, Math.min(1.0, progress));
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }

  /** Returns a resolved parameter value.
   * @param name parameter name
   * @return configured or default value supplied by the host
   * @throws IllegalArgumentException when the extension did not declare the name
   */
  public double parameter(String name) {
    Double value = parameters.get(name);
    if (value == null) throw new IllegalArgumentException("Unknown animation parameter: " + name);
    return value;
  }
}
