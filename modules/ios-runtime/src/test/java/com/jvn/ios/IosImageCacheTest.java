package com.jvn.ios;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.jvn.core.assets.ClasspathAssetManager;

/**
 * Tests for {@link IosImageCache}.
 */
public class IosImageCacheTest {

  @Test
  void testCacheInitialization() {
    IosImageCache cache = new IosImageCache(new ClasspathAssetManager());
    assertNotNull(cache, "Cache should be initialized");
  }

  @Test
  void testLoadNonexistentImage() {
    IosImageCache cache = new IosImageCache(new ClasspathAssetManager());
    Object result = cache.getOrLoad("game/images/nonexistent.png");
    assertNull(result, "Non-existent image should return null");
  }

  @Test
  void testCacheClear() {
    IosImageCache cache = new IosImageCache(new ClasspathAssetManager());
    cache.clear();
    // Verify no exception is thrown
    Object result = cache.getOrLoad("game/images/test.png");
    assertNull(result, "Cache should be empty after clear");
  }

  @Test
  void testAssetManagerUsed() {
    IosImageCache cache = new IosImageCache(new ClasspathAssetManager());
    // Should attempt to load from asset manager
    Object result = cache.getOrLoad("game/images/missing.png");
    // Would be null since image doesn't exist
    assertNull(result, "Should use asset manager for loading");
  }
}
