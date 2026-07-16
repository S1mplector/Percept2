package com.jvn.plugin.api;

import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Capability-oriented host services exposed to one plugin.
 *
 * <p>The context is valid for the lifetime of its plugin instance. Configuration and descriptors
 * are immutable snapshots. Paths may be {@code null} where documented: in particular,
 * {@link #projectDirectory()} is absent when the editor has no project or a runtime uses only
 * classpath assets.</p>
 *
 * <p>Calling a registry accessor for a capability omitted from the manifest throws
 * {@link IllegalStateException}. The host owns registration cleanup, although plugins may close
 * individual {@link Registration} handles earlier.</p>
 */
public interface PluginContext {
  /** Returns identity and requirements.
   * @return the validated descriptor
   */
  PluginDescriptor descriptor();
  /** Identifies the host surface.
   * @return the product surface
   */
  PluginEnvironment environment();
  /** Reports the application build.
   * @return host version or {@code dev}
   */
  String jvnVersion();
  /** Locates durable storage.
   * @return plugin-owned data directory
   */
  Path dataDirectory();
  /** Locates the project.
   * @return active project or {@code null}
   */
  Path projectDirectory();
  /** Provides settings.
   * @return immutable configuration properties
   */
  Map<String, String> configuration();
  /** Provides structured logging.
   * @return plugin-namespaced logger
   */
  Logger logger();
  /** Provides extension points.
   * @return capability-checked owned registries
   */
  PluginRegistries registries();
}
