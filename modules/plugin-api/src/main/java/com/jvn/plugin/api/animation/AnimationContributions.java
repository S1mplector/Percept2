package com.jvn.plugin.api.animation;

import com.jvn.plugin.api.Registration;

/** Fluent animation contribution surface owned by the calling plugin. */
public interface AnimationContributions {
  /**
   * Contributes a named easing curve owned by the calling plugin.
   * @param id stable, qualified script-facing ID
   * @param easing metadata and evaluator
   * @return idempotent early-unregistration handle
   */
  Registration easing(String id, AnimationEasing easing);
}
