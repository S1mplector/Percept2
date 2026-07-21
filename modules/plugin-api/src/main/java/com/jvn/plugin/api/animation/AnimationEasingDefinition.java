package com.jvn.plugin.api.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/** Fluent builder for authoring an {@link AnimationEasing}. */
public final class AnimationEasingDefinition {
  private AnimationEasingDefinition() {}

  /**
   * Starts an easing definition.
   * @param label author-facing name
   * @return a new easing builder
   */
  public static Builder easing(String label) { return new Builder(label); }

  /**
   * Creates an inclusive parameter range.
   * @param minimum inclusive minimum
   * @param maximum inclusive maximum
   * @return validated range
   */
  public static Range range(double minimum, double maximum) { return new Range(minimum, maximum); }

  /**
   * Inclusive numeric parameter range.
   * @param minimum inclusive minimum
   * @param maximum inclusive maximum
   */
  public record Range(double minimum, double maximum) {
    /** Validates a finite, ordered range. */
    public Range {
      if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
        throw new IllegalArgumentException("Invalid animation parameter range");
      }
    }
  }

  /** Fluent builder completed by {@link #evaluate(ToDoubleFunction)}. */
  public static final class Builder {
    private final String label;
    private String description = "";
    private String category = "Plugin";
    private String documentationUrl = "";
    private final List<AnimationParameter> parameters = new ArrayList<>();

    private Builder(String label) {
      if (label == null || label.isBlank()) throw new IllegalArgumentException("Easing label is required");
      this.label = label.trim();
    }

    /**
     * Sets the author-facing explanation.
     * @param value explanation
     * @return this builder
     */
    public Builder description(String value) { description = clean(value); return this; }
    /**
     * Sets the editor catalog category.
     * @param value category
     * @return this builder
     */
    public Builder category(String value) { category = clean(value); return this; }
    /**
     * Sets optional external documentation.
     * @param url documentation URL
     * @return this builder
     */
    public Builder documentation(String url) { documentationUrl = clean(url); return this; }

    /**
     * Adds a numeric parameter using its script name as the display label.
     * @param name script-facing name
     * @param defaultValue default value
     * @param range accepted range
     * @return this builder
     */
    public Builder parameter(String name, double defaultValue, Range range) {
      return parameter(name, name, "", defaultValue, range);
    }

    /**
     * Adds a fully described numeric parameter.
     * @param name script-facing name
     * @param parameterLabel author-facing label
     * @param parameterDescription author-facing explanation
     * @param defaultValue default value
     * @param range accepted range
     * @return this builder
     */
    public Builder parameter(
        String name, String parameterLabel, String parameterDescription,
        double defaultValue, Range range
    ) {
      Objects.requireNonNull(range, "range");
      AnimationParameter parameter = new AnimationParameter(
          name, parameterLabel, parameterDescription, defaultValue, range.minimum(), range.maximum());
      if (parameters.stream().anyMatch(existing -> existing.name().equals(parameter.name()))) {
        throw new IllegalArgumentException("Duplicate animation parameter: " + parameter.name());
      }
      parameters.add(parameter);
      return this;
    }

    /**
     * Completes the immutable easing definition.
     * @param evaluator pure, fast evaluation function
     * @return contributed easing contract
     */
    public AnimationEasing evaluate(ToDoubleFunction<AnimationEasingFrame> evaluator) {
      Objects.requireNonNull(evaluator, "evaluator");
      List<AnimationParameter> snapshot = List.copyOf(parameters);
      String resolvedCategory = category.isBlank() ? "Plugin" : category;
      return new AnimationEasing() {
        @Override public String label() { return label; }
        @Override public String description() { return description; }
        @Override public String category() { return resolvedCategory; }
        @Override public String documentationUrl() { return documentationUrl; }
        @Override public List<AnimationParameter> parameters() { return snapshot; }
        @Override public double evaluate(AnimationEasingFrame frame) { return evaluator.applyAsDouble(frame); }
      };
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
  }
}
