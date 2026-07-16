/**
 * Versioned public contracts for extending Java Vector Nexus.
 *
 * <p>Plugin authors implement {@link com.jvn.plugin.api.JvnPlugin}, declare capabilities in
 * {@code jvn-plugin.json}, and register extensions through
 * {@link com.jvn.plugin.api.PluginContext#registries()}. Types outside {@code com.jvn.plugin.api}
 * are not covered by the plugin compatibility policy.</p>
 *
 * <p>The host provides lifecycle isolation and registration ownership, but plugin code executes in
 * the application JVM and is not sandboxed.</p>
 */
package com.jvn.plugin.api;
