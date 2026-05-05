package com.jvn.core.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

/**
 * Abstraction for locating and reading game assets from an underlying store
 * (classpath JAR, filesystem directory, or overlay of both).
 *
 * <p>Implementations resolve assets by combining an {@link AssetType} with a
 * relative name to form a full path under the conventional directory tree
 * defined by {@link AssetPaths}.</p>
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link ClasspathAssetManager} — loads from the classpath (embedded JARs).</li>
 *   <li>{@link FilesystemAssetManager} — loads from an external directory root.</li>
 *   <li>{@link OverlayAssetManager} — chains a primary and fallback manager.</li>
 * </ul>
 *
 * @see AssetCatalog
 * @see AssetPaths
 */
public interface AssetManager {

  /**
   * Check whether an asset exists in this store.
   *
   * @param type the asset type
   * @param name relative asset name
   * @return {@code true} if the asset can be resolved
   */
  boolean exists(AssetType type, String name);

  /**
   * Return a URL pointing to the asset, or {@code null} if not found.
   *
   * @param type the asset type
   * @param name relative asset name
   * @return the asset URL, or {@code null}
   */
  URL url(AssetType type, String name);

  /**
   * Open an input stream for reading the asset.
   *
   * @param type the asset type
   * @param name relative asset name
   * @return an open input stream (caller must close)
   * @throws IOException if the asset is not found or cannot be read
   */
  InputStream open(AssetType type, String name) throws IOException;

  /**
   * List the immediate children of a directory within the asset store.
   *
   * @param directory the directory path (e.g. {@code "game/images/"})
   * @return a list of child file/directory names (never {@code null})
   */
  List<String> list(String directory);
}
