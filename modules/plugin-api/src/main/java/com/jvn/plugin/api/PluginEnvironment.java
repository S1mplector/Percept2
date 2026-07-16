package com.jvn.plugin.api;

/** Product surface in which a plugin host operates. */
public enum PluginEnvironment {
  /** Shipped game runtime. */
  RUNTIME,
  /** JavaFX authoring editor. */
  EDITOR,
  /** Unit or integration test harness. */
  TEST
}
