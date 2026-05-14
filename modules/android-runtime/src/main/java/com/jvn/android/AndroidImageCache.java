package com.jvn.android;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.jvn.core.assets.AssetManager;
import com.jvn.core.assets.AssetPaths;
import com.jvn.core.assets.AssetType;

/**
 * Image cache for Android renderer, managing {@code android.graphics.Bitmap} objects.
 *
 * <p>Bitmaps are loaded from assets and cached for reuse to avoid repeated decoding.</p>
 */
public class AndroidImageCache {
  private static final Logger log = LoggerFactory.getLogger(AndroidImageCache.class);

  private final AssetManager assetManager;
  private final Map<String, Object> cache = new HashMap<>();

  public AndroidImageCache(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  /**
   * Get or load a bitmap from classpath asset path.
   *
   * @param classpath the asset classpath (e.g., "game/images/hero.png")
   * @return the android.graphics.Bitmap, or null if loading failed
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
        Object bitmap = decodeBitmapNative(imageData);
        if (bitmap != null) {
          cache.put(classpath, bitmap);
          log.debug("Bitmap loaded: {}", classpath);
          return bitmap;
        }
      }
    } catch (Exception e) {
      log.error("Failed to load bitmap: {}", classpath, e);
    }

    return null;
  }

  /**
   * Clear the entire image cache, recycling all bitmaps.
   */
  public void clear() {
    for (Object bitmap : cache.values()) {
      recycleBitmapNative(bitmap);
    }
    cache.clear();
  }

  // Native Android bitmap operations (via reflection)
  private static native Object decodeBitmapNative(byte[] imageData) /*-{
    // Would use BitmapFactory.decodeByteArray()
    return null;
  }-*/;

  private static native void recycleBitmapNative(Object bitmap) /*-{
    // Would call bitmap.recycle()
  }-*/;
}
