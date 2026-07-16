package com.jvn.plugin.api.asset;

import java.nio.file.Path;
import java.util.Map;

/**
 * One asset import operation.
 * @param source existing input file or directory
 * @param destinationDirectory host-approved output directory
 * @param options immutable importer-specific settings
 */
public record AssetImportRequest(Path source, Path destinationDirectory, Map<String, String> options) {
  /** Normalizes options to an immutable map. */
  public AssetImportRequest {
    options = options == null ? Map.of() : Map.copyOf(options);
  }
}
