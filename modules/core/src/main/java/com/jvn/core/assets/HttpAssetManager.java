package com.jvn.core.assets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AssetManager} that fetches assets over HTTP(S) with in-memory LRU caching.
 *
 * <p>Used by web exports (TeaVM, CheerpJ) where filesystem access is unavailable.
 * The base URL is configured at construction; all asset lookups are relative to it.</p>
 *
 * <p>Assets are cached in memory with an LRU eviction policy. The cache capacity
 * can be configured; the default is 64 MB.</p>
 */
public class HttpAssetManager implements AssetManager {
  private static final Logger log = LoggerFactory.getLogger(HttpAssetManager.class);

  private final String baseUrl;
  private final long maxCacheBytes;
  private final Map<String, byte[]> cache = new LinkedHashMap<String, byte[]>(16, 0.75f, true) {
    private long totalBytes = 0;

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
      if (totalBytes > maxCacheBytes) {
        totalBytes -= eldest.getValue().length;
        return true;
      }
      return false;
    }

    @Override
    public byte[] put(String key, byte[] value) {
      byte[] prev = super.put(key, value);
      if (prev != null) {
        totalBytes -= prev.length;
      }
      totalBytes += value.length;
      return prev;
    }
  };

  /**
   * Construct an HTTP asset manager.
   *
   * @param baseUrl   the base URL (e.g., "https://example.com/assets/")
   * @param maxCacheBytes maximum cache size in bytes (default 64 MB)
   */
  public HttpAssetManager(String baseUrl, long maxCacheBytes) {
    this.baseUrl = ensureTrailingSlash(baseUrl);
    this.maxCacheBytes = maxCacheBytes;
  }

  /**
   * Construct an HTTP asset manager with a default cache size of 64 MB.
   */
  public HttpAssetManager(String baseUrl) {
    this(baseUrl, 64 * 1024 * 1024);
  }

  @Override
  public boolean exists(AssetType type, String name) {
    try {
      return fetch(type, name) != null;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public URL url(AssetType type, String name) {
    try {
      String path = AssetPaths.build(type, name);
      String fullUrl = baseUrl + path;
      return new URI(fullUrl).toURL();
    } catch (URISyntaxException | java.net.MalformedURLException e) {
      log.warn("Invalid URL for asset {}/{}: {}", type, name, e.getMessage());
      return null;
    }
  }

  @Override
  public InputStream open(AssetType type, String name) throws IOException {
    byte[] data = fetch(type, name);
    if (data == null) {
      throw new IOException("Asset not found: " + name);
    }
    return new ByteArrayInputStream(data);
  }

  @Override
  public List<String> list(String directory) {
    // HTTP asset manager cannot list directories (no directory index available)
    log.warn("HttpAssetManager does not support directory listing");
    return Collections.emptyList();
  }

  private byte[] fetch(AssetType type, String name) throws IOException {
    String path = AssetPaths.build(type, name);
    String cacheKey = path;

    // Check cache first
    byte[] cached = cache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    // Fetch from remote
    String fullUrl = baseUrl + path;
    try {
      log.debug("Fetching asset from HTTP: {}", fullUrl);
      URLConnection conn = new URI(fullUrl).toURL().openConnection();
      conn.setConnectTimeout(10_000);
      conn.setReadTimeout(30_000);

      try (InputStream is = conn.getInputStream()) {
        byte[] data = is.readAllBytes();
        cache.put(cacheKey, data);
        return data;
      }
    } catch (IOException | URISyntaxException e) {
      log.warn("Failed to fetch asset from {}: {}", fullUrl, e.getMessage());
      throw new IOException("Failed to fetch asset: " + name, e);
    }
  }

  private String ensureTrailingSlash(String url) {
    if (url == null || url.isEmpty()) return "";
    return url.endsWith("/") ? url : url + "/";
  }
}
