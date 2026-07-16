package com.jvn.plugin.api;

/** ServiceLoader bridge for plugins bundled on the application classpath. */
public interface BundledPluginProvider {
  /** Returns manifest-equivalent metadata.
   * @return validated plugin metadata
   */
  PluginDescriptor descriptor();
  /** Creates a fresh entrypoint instance.
   * @return plugin instance
   */
  JvnPlugin create();
}
