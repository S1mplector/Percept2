package com.jvn.web;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WebImageCache}.
 */
public class WebImageCacheTest {

  @Test
  void testCacheReturnsNullForUnloadedImage() {
    WebImageCache cache = new WebImageCache();
    Object result = cache.getOrLoad("game/images/test.png", null);
    assertNull(result, "Async image should return null initially");
  }

  @Test
  void testCacheClear() {
    WebImageCache cache = new WebImageCache();
    cache.clear();
    // Verify no exception is thrown
    Object result = cache.getOrLoad("game/images/test.png", null);
    assertNull(result, "Cache should be empty after clear");
  }

  @Test
  void testCallbackRegistration() {
    WebImageCache cache = new WebImageCache();
    boolean[] callbackInvoked = {false};
    Runnable callback = () -> callbackInvoked[0] = true;

    cache.getOrLoad("game/images/test.png", callback);
    // Callback would be invoked asynchronously by JS in real environment
    assertFalse(callbackInvoked[0], "Callback should not be invoked synchronously");
  }

  @Test
  void testMultipleCallbacksForSameImage() {
    WebImageCache cache = new WebImageCache();
    int[] count = {0};
    Runnable cb1 = () -> count[0]++;
    Runnable cb2 = () -> count[0]++;

    cache.getOrLoad("game/images/test.png", cb1);
    cache.getOrLoad("game/images/test.png", cb2);
    // Both callbacks would be invoked when image loads in real environment
    assertEquals(0, count[0], "Callbacks should not be invoked synchronously");
  }
}
