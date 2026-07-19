package com.jvn.plugin.api.animation;

import java.util.List;

/** A named, metadata-rich easing curve contributed by a plugin. */
public interface AnimationEasing {
  /**
   * Returns the author-facing display name.
   * @return display name
   */
  String label();
  /**
   * Returns a concise author-facing explanation.
   * @return explanation or empty text
   */
  default String description() { return ""; }
  /**
   * Returns the editor catalog category.
   * @return category
   */
  default String category() { return "Plugin"; }
  /**
   * Returns optional external documentation.
   * @return URL or empty text
   */
  default String documentationUrl() { return ""; }
  /**
   * Returns the accepted numeric inputs.
   * @return immutable parameter descriptors
   */
  default List<AnimationParameter> parameters() { return List.of(); }
  /**
   * Evaluates the curve for one sample.
   * @param frame clamped progress and validated parameters
   * @return interpolated progress; finite overshoot values are allowed
   */
  double evaluate(AnimationEasingFrame frame);
}
