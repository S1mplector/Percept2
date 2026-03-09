package com.jvn.core.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Composite {@link AssetManager} that chains a <em>primary</em> manager
 * with a <em>fallback</em>, creating a layered asset resolution strategy.
 *
 * <p>For every operation the primary manager is consulted first. If the
 * asset is not found there, the fallback is tried. This pattern is useful
 * for overlaying a project's filesystem assets on top of the classpath
 * defaults:</p>
 * <pre>{@code
 * AssetManager overlay = new OverlayAssetManager(
 *     new FilesystemAssetManager(projectRoot),   // user assets first
 *     new ClasspathAssetManager()                // embedded defaults
 * );
 * }</pre>
 *
 * <p>Either delegate may be {@code null}; {@code null} delegates are
 * silently skipped.</p>
 *
 * @see ClasspathAssetManager
 * @see FilesystemAssetManager
 */
public class OverlayAssetManager implements AssetManager {

  /** First manager to consult for every lookup. */
  private final AssetManager primary;

  /** Second manager consulted when the primary does not have the asset. */
  private final AssetManager fallback;

  /**
   * Construct a layered asset manager.
   *
   * @param primary  first manager to try (may be {@code null})
   * @param fallback second manager tried when primary misses (may be {@code null})
   */
  public OverlayAssetManager(AssetManager primary, AssetManager fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  @Override
  public boolean exists(AssetType type, String name) {
    if (primary != null && primary.exists(type, name)) return true;
    return fallback != null && fallback.exists(type, name);
  }

  @Override
  public URL url(AssetType type, String name) {
    URL u = primary != null ? primary.url(type, name) : null;
    if (u != null) return u;
    return fallback != null ? fallback.url(type, name) : null;
  }

  @Override
  public InputStream open(AssetType type, String name) throws IOException {
    if (primary != null && primary.exists(type, name)) return primary.open(type, name);
    if (fallback != null) return fallback.open(type, name);
    throw new IOException("Asset not found: " + name);
  }

  @Override
  public List<String> list(String directory) {
    Set<String> names = new LinkedHashSet<>();
    if (primary != null) names.addAll(primary.list(directory));
    if (fallback != null) names.addAll(fallback.list(directory));
    return new ArrayList<>(names);
  }
}
