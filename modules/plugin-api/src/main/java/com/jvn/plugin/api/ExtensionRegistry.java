package com.jvn.plugin.api;

import java.util.List;
import java.util.Optional;

/**
 * Thread-safe named extension collection. IDs are case-normalized and globally unique within one
 * extension family and host. Iteration preserves successful registration order.
 *
 * @param <T> extension contract stored by this registry
 */
public interface ExtensionRegistry<T> {
  /**
   * Registers an extension owned by the calling plugin.
   * @param id stable, preferably namespaced identifier
   * @param extension non-null implementation
   * @return idempotent early-unregistration handle
   * @throws IllegalArgumentException for blank IDs or null extensions
   * @throws IllegalStateException if the ID is already registered
   */
  Registration register(String id, T extension);
  /** Finds an extension.
   * @param id identifier
   * @return matching extension, if registered
   */
  Optional<T> find(String id);
  /** Takes a stable snapshot.
   * @return immutable registration-order entries
   */
  List<ExtensionEntry<T>> entries();
}
