package com.jvn.plugin.api;

/**
 * A required plugin and its accepted semantic-version range.
 *
 * @param id exact plugin identifier
 * @param version exact, wildcard, caret, or comparison range; blank values become {@code *}
 */
public record PluginDependency(String id, String version) {
  /** Validates identity and normalizes a blank range to a wildcard. */
  public PluginDependency {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Dependency id is required");
    version = version == null || version.isBlank() ? "*" : version.trim();
  }
}
