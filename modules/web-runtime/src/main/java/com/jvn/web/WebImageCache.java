package com.jvn.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teavm.jso.canvas.CanvasImageSource;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLImageElement;

/**
 * Image cache for web renderer, storing loaded canvas Image elements.
 *
 * <p>Images are loaded asynchronously from asset paths and cached for reuse.</p>
 */
public class WebImageCache {
  private static final Logger log = LoggerFactory.getLogger(WebImageCache.class);

  private final Map<String, CanvasImageSource> cache = new HashMap<>();
  private final Map<String, List<Runnable>> loadingCallbacks = new HashMap<>();
  private final Map<String, Boolean> loading = new HashMap<>();

  /**
   * Get or load an image from classpath asset path.
   *
   * @param classpath the asset classpath (e.g., "game/images/hero.png")
   * @return the canvas Image element, or null if not yet loaded
   */
  @SuppressWarnings("NullAway")
  public CanvasImageSource getOrLoad(String classpath) {
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
  public CanvasImageSource getOrLoad(String classpath, Runnable onLoaded) {
    return getOrLoadInternal(classpath, onLoaded);
  }

  @SuppressWarnings("NullAway")
  private CanvasImageSource getOrLoadInternal(String classpath, Runnable onLoaded) {
    CanvasImageSource cached = cache.get(classpath);
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

  /**
   * Natural pixel dimensions of the cached image at {@code classpath}, if it has
   * finished loading. Triggers/continues an async load as a side effect if not yet
   * cached, matching {@link #getOrLoad}'s existing behavior — the caller (typically
   * {@code Blitter2D.imageDimensions}) is expected to re-poll on a later frame.
   */
  public java.util.Optional<double[]> dimensionsOf(String classpath) {
    CanvasImageSource cachedValue = cache.get(classpath);
    if (cachedValue instanceof HTMLImageElement img) {
      int width = img.getNaturalWidth();
      int height = img.getNaturalHeight();
      if (width > 0 && height > 0) {
        return java.util.Optional.of(new double[] { width, height });
      }
    }
    getOrLoadInternal(classpath, null);
    return java.util.Optional.empty();
  }

  private void loadImageAsync(String classpath) {
    String imageUrl = resolveAssetUrl(classpath);
    HTMLImageElement image = (HTMLImageElement) HTMLDocument.current().createElement("img");
    image.addEventListener("load", event -> onImageLoaded(classpath, image));
    image.addEventListener("error", event -> onImageError(classpath, "Failed to load " + imageUrl));
    image.setSrc(imageUrl);
  }

  /** Called by the browser load event when an image finishes loading. */
  public void onImageLoaded(String classpath, CanvasImageSource imageElement) {
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

  /** Called by the browser error event if an image fails to load. */
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

  static String resolveAssetUrl(String classpath) {
    if (classpath == null || classpath.isBlank()) {
      throw new IllegalArgumentException("Web asset path must not be blank");
    }
    String normalized = classpath.replace('\\', '/');
    if (normalized.startsWith("data:")
        || normalized.startsWith("blob:")
        || normalized.startsWith("https://")
        || normalized.startsWith("http://")) {
      return normalized;
    }
    while (normalized.startsWith("/")) normalized = normalized.substring(1);
    return normalized.startsWith("assets/") ? normalized : "assets/" + normalized;
  }
}
