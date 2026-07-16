package com.jvn.plugin.api;

/**
 * Stable entry point implemented by every JVN plugin.
 *
 * <p>The entrypoint class named by {@code jvn-plugin.json} must be public, implement this interface,
 * and provide a public no-argument constructor. The host creates one instance per host lifecycle.</p>
 *
 * <p>Plugins should perform registration in {@link #initialize(PluginContext)}, begin background
 * work in {@link #start()}, and release every owned thread, stream, file handle, and listener in
 * {@link #stop()}. A plugin must not assume that another plugin has started during initialization;
 * declared dependencies are initialized first but all valid plugins start only afterward.</p>
 *
 * <p>Exceptions are isolated and converted into host diagnostics. An initialization or startup
 * failure removes every extension registered by that plugin and invokes {@code stop()} as a
 * best-effort rollback.</p>
 */
public interface JvnPlugin {
  /**
   * Receives host services and registers extensions.
   *
   * @param context immutable identity, paths, configuration, logging, and owned registries
   * @throws Exception when initialization cannot complete; the host rolls back the plugin
   */
  void initialize(PluginContext context) throws Exception;

  /**
   * Starts operational work after the initialization phase.
   * @throws Exception when the plugin cannot start; dependents will not start
   */
  default void start() throws Exception {}

  /**
   * Releases resources during shutdown or rollback. Called in reverse dependency order during
   * normal shutdown. Implementations should be safe after partial initialization.
   * @throws Exception to report cleanup failure; remaining plugins are still stopped
   */
  default void stop() throws Exception {}
}
