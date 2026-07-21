package com.jvn.plugin.api.animation;

/**
 * Describes one named numeric input accepted by an animation extension.
 * @param name stable script-facing parameter name
 * @param label author-facing display label
 * @param description concise author-facing explanation
 * @param defaultValue value used when scripts omit the parameter
 * @param minimum inclusive minimum
 * @param maximum inclusive maximum
 */
public record AnimationParameter(
    String name,
    String label,
    String description,
    double defaultValue,
    double minimum,
    double maximum
) {
  /** Validates and normalizes a parameter descriptor. */
  public AnimationParameter {
    name = requireToken(name, "Parameter name");
    label = label == null || label.isBlank() ? name : label.trim();
    description = description == null ? "" : description.trim();
    if (!Double.isFinite(defaultValue) || !Double.isFinite(minimum) || !Double.isFinite(maximum)) {
      throw new IllegalArgumentException("Animation parameter values must be finite");
    }
    if (minimum > maximum) throw new IllegalArgumentException("Parameter minimum exceeds maximum: " + name);
    if (defaultValue < minimum || defaultValue > maximum) {
      throw new IllegalArgumentException("Parameter default is outside its range: " + name);
    }
  }

  private static String requireToken(String value, String label) {
    if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
      throw new IllegalArgumentException(label + " must be an identifier");
    }
    return value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
  }
}
