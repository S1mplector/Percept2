package com.jvn.plugin.api.runtime;

import java.nio.file.Path;
import java.util.Map;

/**
 * Runtime lifecycle context.
 * @param jvnVersion host application version
 * @param projectDirectory active project, or {@code null}
 * @param attributes immutable forward-compatible host metadata
 */
public record RuntimeEvent(String jvnVersion, Path projectDirectory, Map<String, Object> attributes) {
  /** Normalizes attributes to an immutable map. */
  public RuntimeEvent {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }
}
