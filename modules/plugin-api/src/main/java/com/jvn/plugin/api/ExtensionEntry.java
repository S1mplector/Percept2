package com.jvn.plugin.api;

/**
 * One immutable registry entry.
 * @param id normalized extension identifier
 * @param pluginId owning plugin identifier
 * @param extension registered implementation
 * @param <T> extension contract
 */
public record ExtensionEntry<T>(String id, String pluginId, T extension) {}
