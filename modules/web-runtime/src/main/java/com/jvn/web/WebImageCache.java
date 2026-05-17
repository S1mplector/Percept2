package com.jvn.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Image cache for web renderer, storing loaded canvas Image elements.
 *
 * <p>Images are loaded asynchronously from asset paths and cached for reuse.</p>
 */
public class WebImageCache {
  private static final Logger log = LoggerFactory.getLogger(WebImageCache.class);

  private final Map<String, Object> cache = new HashMap<>();
  private final Map<String, List<Runnable>> loadingCallbacks = new HashMap<>();
  private final Map<String, Boolean> loading = new HashMap<>();

  /**
   * Get or load an image from classpath asset path.
   *
   * @param classpath the asset classpath (e.g., "game/images/hero.png")
   * @return the canvas Image element, or null if not yet loaded
   */
  @SuppressWarnings("NullAway")
  public Object getOrLoad(String classpath) {
    return getOrLoadInternal(classpath, null);
  }

  /**
   * Get or load an image from classpath asset path.
   *
   * @param classpath the asset classpath (e.g., "game/images/hero.png")
   * @param onLoaded callback invoked when image is loaded
   * @return the canvas Image element, or null if not yet loaded
   */
  @SuppressWarnings("NullAway")
  public Object getOrLoad(String classpath, Runnable onLoaded) {
    return getOrLoadInternal(classpath, onLoaded);
  }

  @SuppressWarnings("NullAway")
  private Object getOrLoadInternal(String classpath, Runnable onLoaded) {
    Object cached = cache.get(classpath);
    if (cached != null) {
      return cached;
    }

    // Register callback for when image loads
    if (onLoaded != null) {
      loadingCallbacks.computeIfAbsent(classpath, k -> new ArrayList<>()).add(onLoaded);
    }

    // Start async load if not already loading
    if (!loading.getOrDefault(classpath, false)) {
      loading.put(classpath, true);
      loadImageAsync(classpath);
    }

    return null;
  }

  private void loadImageAsync(String classpath) {
    // Convert classpath to web URL (e.g., "game/images/hero.png" -> "/assets/game/images/hero.png")
    String imageUrl = "/assets/" + classpath;
    loadImageNative(imageUrl, classpath, this);
  }

  /**
   * Called by native JS when an image finishes loading.
   */
  public void onImageLoaded(String classpath, Object imageElement) {
    loading.remove(classpath);
    cache.put(classpath, imageElement);
    log.debug("Image loaded: {}", classpath);

    // Invoke all pending callbacks
    List<Runnable> callbacks = loadingCallbacks.remove(classpath);
    if (callbacks != null) {
      for (Runnable cb : callbacks) {
        try {
          cb.run();
        } catch (Exception e) {
          log.error("Error in image load callback", e);
        }
      }
    }
  }

  /**
   * Called by native JS if image fails to load.
   */
  public void onImageError(String classpath, String error) {
    loading.remove(classpath);
    log.error("Failed to load image {}: {}", classpath, error);
    loadingCallbacks.remove(classpath);
  }

  /**
   * Clear the entire image cache.
   */
  public void clear() {
    cache.clear();
    loadingCallbacks.clear();
    loading.clear();
  }

  // Native JS image loading via TeaVM JSO
  private static native void loadImageNative(String url, String classpath, WebImageCache cache) /*-{
    var img = new Image();
    img.onload = function() {
      cache.@com.jvn.web.WebImageCache::onImageLoaded(Ljava/lang/String;Ljava/lang/Object;)(classpath, img);
    };
    img.onerror = function() {
      cache.@com.jvn.web.WebImageCache::onImageError(Ljava/lang/String;Ljava/lang/String;)(classpath, "Failed to load");
    };
    img.src = url;
  }-*/;
}
