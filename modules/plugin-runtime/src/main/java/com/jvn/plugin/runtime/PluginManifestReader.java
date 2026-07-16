package com.jvn.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvn.plugin.api.PluginCapability;
import com.jvn.plugin.api.PluginDependency;
import com.jvn.plugin.api.PluginDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PluginManifestReader {
  public static final String MANIFEST_PATH = "jvn-plugin.json";
  private final ObjectMapper mapper = new ObjectMapper();

  public PluginDescriptor read(InputStream input) throws IOException {
    JsonNode root = mapper.readTree(input);
    if (root == null || !root.isObject()) throw new IOException("Plugin manifest must be a JSON object");
    List<PluginDependency> dependencies = new ArrayList<>();
    JsonNode dependencyNode = root.path("dependencies");
    if (dependencyNode.isArray()) {
      for (JsonNode node : dependencyNode) {
        dependencies.add(new PluginDependency(text(node, "id"), optionalText(node, "version", "*")));
      }
    }
    Set<PluginCapability> capabilities = new LinkedHashSet<>();
    JsonNode capabilityNode = root.path("capabilities");
    if (capabilityNode.isArray()) {
      for (JsonNode node : capabilityNode) capabilities.add(PluginCapability.fromId(node.asText()));
    }
    try {
      return new PluginDescriptor(
          text(root, "id"), text(root, "name"), text(root, "version"), text(root, "jvnApi"),
          text(root, "entrypoint"), optionalText(root, "description", ""),
          optionalText(root, "vendor", ""), dependencies, capabilities);
    } catch (IllegalArgumentException error) {
      throw new IOException("Invalid plugin manifest: " + error.getMessage(), error);
    }
  }

  private static String text(JsonNode node, String field) throws IOException {
    String value = optionalText(node, field, "");
    if (value.isBlank()) throw new IOException("Plugin manifest field '" + field + "' is required");
    return value;
  }

  private static String optionalText(JsonNode node, String field, String fallback) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? fallback : value.asText().trim();
  }
}
