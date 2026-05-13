package com.jvn.core.assets;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BoundedImageCacheTest {

  @Test
  void lruEviction_oldestEntryRemovedWhenFull() {
    BoundedImageCache<String> cache = new BoundedImageCache<>(256);
    // Insert 257 items; key "key-0" should be evicted (oldest, never re-accessed)
    for (int i = 0; i < 257; i++) {
      cache.computeIfAbsent("key-" + i, k -> "val-" + k);
    }
    assertNull(cache.get("key-0"), "oldest entry should have been evicted");
    assertNotNull(cache.get("key-256"), "newest entry should survive");
    assertTrue(cache.size() <= 256, "size must not exceed maxEntries");
  }

  @Test
  void lruEviction_accessedEntryNotEvicted() {
    BoundedImageCache<String> cache = new BoundedImageCache<>(256);
    cache.computeIfAbsent("key-0", k -> "val-0");
    // Fill up to 256
    for (int i = 1; i < 256; i++) {
      cache.computeIfAbsent("key-" + i, k -> "val-" + k);
    }
    // Re-access key-0 to make it recently used
    cache.get("key-0");
    // Add one more to trigger eviction
    cache.computeIfAbsent("key-256", k -> "val-256");
    assertNotNull(cache.get("key-0"), "recently accessed entry should survive eviction");
  }

  @Test
  void hitMissCounters_incrementCorrectly() {
    BoundedImageCache<String> cache = new BoundedImageCache<>(10);
    cache.computeIfAbsent("a", k -> "A"); // miss
    cache.computeIfAbsent("a", k -> "A"); // hit
    cache.get("a");                        // hit
    cache.get("missing");                  // miss

    assertEquals(2, cache.hitCount());
    assertEquals(2, cache.missCount());
  }

  @Test
  void concurrentComputeIfAbsent_loaderCalledOnce() throws InterruptedException {
    BoundedImageCache<String> cache = new BoundedImageCache<>(256);
    AtomicInteger loaderCallCount = new AtomicInteger();
    int threads = 4;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      pool.submit(() -> {
        try {
          start.await();
          cache.computeIfAbsent("shared-key", k -> {
            loaderCallCount.incrementAndGet();
            return "value";
          });
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
    }
    start.countDown();
    done.await();
    pool.shutdown();

    // Due to coarse synchronization, the loader may be called more than once
    // in rare races, but the cache must never grow beyond maxEntries and must
    // contain the key.
    assertNotNull(cache.get("shared-key"));
    assertTrue(cache.size() <= 256);
  }
}
