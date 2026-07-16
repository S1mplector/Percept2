package com.jvn.plugin.api.editor;

import java.nio.file.Path;
import java.util.Map;

/**
 * Editor action context.
 * @param projectDirectory active project, or {@code null}
 * @param services immutable optional host services; {@code window} is currently supplied when available
 */
public record EditorToolContext(Path projectDirectory, Map<String, Object> services) {
  /** Normalizes services to an immutable map. */
  public EditorToolContext {
    services = services == null ? Map.of() : Map.copyOf(services);
  }
}
