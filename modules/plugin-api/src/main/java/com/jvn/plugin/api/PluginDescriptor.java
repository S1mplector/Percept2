package com.jvn.plugin.api;

import java.util.List;
import java.util.Set;

/**
 * Validated, immutable representation of {@code jvn-plugin.json}.
 *
 * @param id globally unique and stable reverse-domain plugin identifier
 * @param name human-readable name shown in diagnostics and future management UI
 * @param version plugin semantic version
 * @param apiVersion semantic-version range accepted for the JVN Plugin API
 * @param entrypoint fully qualified public class implementing {@link JvnPlugin}
 * @param description optional short plain-text description; normalized to an empty string
 * @param vendor optional author or organization; normalized to an empty string
 * @param dependencies required plugins and accepted versions, in declaration order
 * @param capabilities extension families the plugin is authorized to request
 */
public record PluginDescriptor(
    String id,
    String name,
    String version,
    String apiVersion,
    String entrypoint,
    String description,
    String vendor,
    List<PluginDependency> dependencies,
    Set<PluginCapability> capabilities
) {
  /** Validates required fields and creates immutable collection snapshots. */
  public PluginDescriptor {
    require("id", id);
    require("name", name);
    require("version", version);
    require("apiVersion", apiVersion);
    require("entrypoint", entrypoint);
    description = description == null ? "" : description;
    vendor = vendor == null ? "" : vendor;
    dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
  }

  private static void require(String field, String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
  }
}
