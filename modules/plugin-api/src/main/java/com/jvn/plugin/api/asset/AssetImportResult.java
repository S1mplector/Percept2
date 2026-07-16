package com.jvn.plugin.api.asset;

import java.nio.file.Path;
import java.util.List;

/**
 * Complete import outcome.
 * @param success whether all required outputs were produced
 * @param outputs generated paths, preferably relative to the project
 * @param diagnostics ordered human-readable warnings or failures
 */
public record AssetImportResult(boolean success, List<Path> outputs, List<String> diagnostics) {
  /** Normalizes outputs and diagnostics to immutable lists. */
  public AssetImportResult {
    outputs = outputs == null ? List.of() : List.copyOf(outputs);
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }
}
