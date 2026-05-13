package com.jvn.core.assets;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Generic LRU cache backed by {@link LinkedHashMap} in access-order mode.
 *
 * <p>Evicts the least-recently-used entry when {@code size() > maxEntries}.
 * Wrapped with {@link Collections#synchronizedMap} for coarse thread safety —
 * adequate for image caches which have low contention. Do NOT replace with
 * {@code ConcurrentHashMap}: it does not preserve access order needed for LRU.
 *
 * @param <V> value type (intentionally not locked to JavaFX Image so :core stays JavaFX-free)
 */
public class BoundedImageCache<V> {

  private final int maxEntries;
  private final Map<String, V> map;
  private final AtomicLong hits = new AtomicLong();
  private final AtomicLong misses = new AtomicLong();

  public BoundedImageCache(int maxEntries) {
    if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be >= 1");
    this.maxEntries = maxEntries;
    LinkedHashMap<String, V> lru = new LinkedHashMap<>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
        return size() > maxEntries;
      }
    };
    this.map = Collections.synchronizedMap(lru);
  }

  /** Returns the cached value, or {@code null} if absent. */
  public V get(String key) {
    V v = map.get(key);
    if (v != null) hits.incrementAndGet(); else misses.incrementAndGet();
    return v;
  }

  /**
   * Returns the cached value if present, otherwise calls {@code loader}, stores
   * the result, and returns it. The loader is called at most once per missing key
   * per call.
   */
  public V computeIfAbsent(String key, Function<String, V> loader) {
    V existing = map.get(key);
    if (existing != null) {
      hits.incrementAndGet();
      return existing;
    }
    misses.incrementAndGet();
    V computed = loader.apply(key);
    if (computed != null) {
      map.put(key, computed);
    }
    return computed;
  }

  /** Explicitly stores a value, subject to LRU eviction like any other entry. */
  public void put(String key, V value) {
    if (value != null) map.put(key, value);
  }

  /** Removes all entries from the cache. */
  public void clear() {
    map.clear();
  }

  /** Returns the current number of entries. */
  public int size() {
    return map.size();
  }

  /** Returns the configured maximum number of entries. */
  public int maxEntries() {
    return maxEntries;
  }

  /** Returns the cumulative hit count since construction or last reset. */
  public long hitCount() {
    return hits.get();
  }

  /** Returns the cumulative miss count since construction or last reset. */
  public long missCount() {
    return misses.get();
  }
}
