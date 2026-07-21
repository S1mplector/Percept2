package com.jvn.plugin.api;

import com.jvn.plugin.api.animation.AnimationContributions;

/** Author-focused entrypoint for contributing extensions. */
public interface PluginContributions {
  /**
   * Returns the plugin-owned animation authoring surface.
   * @return animation contributions
   */
  AnimationContributions animations();
}
