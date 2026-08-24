package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FrameRetainedCacheTest {

  @Test
  void retainsAnActiveMultiCharacterLayerWorkingSetAcrossFrames() {
    FrameRetainedCache<String> cache = new FrameRetainedCache<>();
    AtomicInteger loads = new AtomicInteger();

    cache.beginFrame();
    for (int layer = 0; layer < 18; layer++) {
      String key = "layer-" + layer;
      assertEquals(key, cache.getOrLoad(key, ignored -> {
        loads.incrementAndGet();
        return key;
      }));
    }
    cache.endFrame();

    assertEquals(18, loads.get());
    assertEquals(18, cache.size());

    cache.beginFrame();
    for (int layer = 0; layer < 18; layer++) {
      String key = "layer-" + layer;
      assertEquals(key, cache.getOrLoad(key, ignored -> {
        loads.incrementAndGet();
        return key;
      }));
    }
    cache.endFrame();

    assertEquals(18, loads.get(), "the second frame must not reload active timeline layers");
    assertEquals(18, cache.size());
  }

  @Test
  void releasesLayersThatLeaveTheRenderedFrame() {
    FrameRetainedCache<String> cache = new FrameRetainedCache<>();

    cache.beginFrame();
    cache.getOrLoad("john-body", key -> key);
    cache.getOrLoad("wendi-body", key -> key);
    cache.endFrame();
    assertEquals(2, cache.size());

    cache.beginFrame();
    cache.getOrLoad("wendi-body", key -> key);
    cache.endFrame();

    assertEquals(1, cache.size());
  }

  @Test
  void doesNotRetainFailedLoads() {
    FrameRetainedCache<String> cache = new FrameRetainedCache<>();

    cache.beginFrame();
    assertNull(cache.getOrLoad("missing", ignored -> null));
    cache.endFrame();

    assertEquals(0, cache.size());
  }
}
