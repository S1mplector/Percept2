package com.jvn.web;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.teavm.jso.canvas.CanvasImageSource;

/**
 * Tests for {@link WebImageCache}.
 *
 * <p>The JVM tests exercise cache state and callbacks without invoking TeaVM DOM APIs.</p>
 */
public class WebImageCacheTest {

  @Test
  void testCacheClear() {
    WebImageCache cache = new WebImageCache();
    cache.clear();
    // Verify no exception is thrown - should work fine in JVM
    assertTrue(true, "Cache clear should not throw");
  }

  @Test
  void testCacheInstantiation() {
    WebImageCache cache = new WebImageCache();
    assertNotNull(cache, "Cache should be instantiated");
  }

  @Test
  void testCacheCanBeCreated() {
    // Verify the class can be loaded and instantiated
    WebImageCache cache = new WebImageCache();
    assertNotNull(cache);
  }

  @Test
  void testClearIsIdempotent() {
    WebImageCache cache = new WebImageCache();
    cache.clear();
    cache.clear();  // Should not throw
    assertTrue(true, "Multiple clears should be safe");
  }

  @Test
  void testLoadedImageClearsLoadingStateAndRunsCallbacks() throws Exception {
    WebImageCache cache = new WebImageCache();
    String classpath = "game/images/hero.png";
    boolean[] callbackInvoked = {false};
    CanvasImageSource image = new FakeImage();

    loading(cache).put(classpath, true);
    callbacks(cache).put(classpath, List.of(() -> callbackInvoked[0] = true));

    cache.onImageLoaded(classpath, image);

    assertFalse(loading(cache).containsKey(classpath), "Loaded image should no longer be marked loading");
    assertFalse(callbacks(cache).containsKey(classpath), "Callbacks should be drained after load");
    assertSame(image, cachedImages(cache).get(classpath), "Loaded image should be cached");
    assertTrue(callbackInvoked[0], "Load callback should be invoked");
  }

  @Test
  void testImageErrorClearsLoadingStateAndCallbacks() throws Exception {
    WebImageCache cache = new WebImageCache();
    String classpath = "game/images/missing.png";

    loading(cache).put(classpath, true);
    callbacks(cache).put(classpath, List.of(() -> fail("Error callbacks should be discarded")));

    cache.onImageError(classpath, "missing");

    assertFalse(loading(cache).containsKey(classpath), "Failed image should no longer be marked loading");
    assertFalse(callbacks(cache).containsKey(classpath), "Callbacks should be discarded after error");
    assertFalse(cachedImages(cache).containsKey(classpath), "Failed image should not be cached");
  }

  @Test
  void testClearRemovesAllCacheState() throws Exception {
    WebImageCache cache = new WebImageCache();
    String classpath = "game/images/hero.png";

    cachedImages(cache).put(classpath, new FakeImage());
    loading(cache).put(classpath, true);
    callbacks(cache).put(classpath, List.of(() -> {}));

    cache.clear();

    assertTrue(cachedImages(cache).isEmpty(), "Cached images should be cleared");
    assertTrue(loading(cache).isEmpty(), "Loading state should be cleared");
    assertTrue(callbacks(cache).isEmpty(), "Callbacks should be cleared");
  }

  @Test
  void dimensionsOfReturnsEmptyForAnUnrelatedCachedImageThatIsNotAnHTMLImageElement() throws Exception {
    WebImageCache cache = new WebImageCache();
    String classpath = "game/images/hero.png";
    cachedImages(cache).put(classpath, new FakeImage());

    java.util.Optional<double[]> dims = cache.dimensionsOf(classpath);

    assertTrue(dims.isEmpty(),
        "a CanvasImageSource that isn't an HTMLImageElement (e.g. this test's FakeImage stub) "
            + "has no naturalWidth/naturalHeight to read, so dimensionsOf must return empty rather "
            + "than throw a ClassCastException");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, CanvasImageSource> cachedImages(WebImageCache cache) throws Exception {
    return (Map<String, CanvasImageSource>) field(cache, "cache");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Boolean> loading(WebImageCache cache) throws Exception {
    return (Map<String, Boolean>) field(cache, "loading");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, List<Runnable>> callbacks(WebImageCache cache) throws Exception {
    return (Map<String, List<Runnable>>) field(cache, "loadingCallbacks");
  }

  private static Object field(WebImageCache cache, String fieldName) throws Exception {
    Field field = WebImageCache.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(cache);
  }

  private static final class FakeImage implements CanvasImageSource {}
}
