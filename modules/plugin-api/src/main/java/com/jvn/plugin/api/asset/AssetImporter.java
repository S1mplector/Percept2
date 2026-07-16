package com.jvn.plugin.api.asset;

import java.nio.file.Path;

/**
 * Imports or converts external content into a JVN project.
 *
 * <p>{@link #supports(Path)} should be cheap and side-effect free. Import operations must stay
 * within the requested destination and explicitly report every generated path.</p>
 */
public interface AssetImporter {
  /** Supplies the display name.
   * @return human-readable importer name
   */
  String label();
  /** Checks support without modifying the source.
   * @param source input path
   * @return whether it is supported
   */
  boolean supports(Path source);
  /** Imports content.
   * @param request approved operation
   * @return outcome
   * @throws Exception if it cannot complete
   */
  AssetImportResult importAsset(AssetImportRequest request) throws Exception;
}
