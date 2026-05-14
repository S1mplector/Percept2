package com.jvn.ios;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.jvn.core.assets.AssetManager;
import com.jvn.core.assets.AssetType;

/**
 * Image cache for iOS renderer, managing {@code UIImage} objects.
 *
 * <p>Images are loaded from assets and cached for reuse to avoid repeated decoding.</p>
 */
public class IosImageCache {
  private static final Logger log = LoggerFactory.getLogger(IosImageCache.class);

  private final AssetManager assetManager;
  private final Map<String, Object> cache = new HashMap<>();

  public IosImageCache(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  /**
   * Get or load a UIImage from classpath asset path.
   *
   * @param classpath the asset classpath (e.g., "game/images/hero.png")
   * @return the UIImage, or null if loading failed
   */
  public Object getOrLoad(String classpath) {
    Object cached = cache.get(classpath);
    if (cached != null) {
      return cached;
    }

    try {
      InputStream imageStream = assetManager.open(AssetType.IMAGE, classpath);
      if (imageStream != null) {
        byte[] imageData = imageStream.readAllBytes();
        imageStream.close();
        Object image = decodeImageNative(imageData);
        if (image != null) {
          cache.put(classpath, image);
          log.debug("UIImage loaded: {}", classpath);
          return image;
        }
      }
    } catch (Exception e) {
      log.error("Failed to load image: {}", classpath, e);
    }

    return null;
  }

  /**
   * Clear the entire image cache.
   */
  public void clear() {
    cache.clear();
  }

  // Native iOS image operations (via Multi-OS Engine)
  private static native Object decodeImageNative(byte[] imageData) /*-{
    // Would use UIImage.imageWithData()
    return null;
  }-*/;
}
