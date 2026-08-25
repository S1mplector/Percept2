package com.jvn.core.assets;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Generic LRU cache backed by {@link LinkedHashMap} in access-order mode.
 *
 * <p>Evicts least-recently-used entries when either the entry-count or optional
 * weight budget is exceeded. The weighted form is important for raster caches:
 * limiting a cache to 256 images is not safe when each image can occupy several
 * megabytes. Public operations use coarse synchronization, which is adequate
 * for the low-contention renderer caches and preserves access order.
 *
 * @param <V> value type (intentionally not locked to JavaFX Image so :core stays JavaFX-free)
 */
public class BoundedImageCache<V> {

  private int maxEntries;
  private final long maxWeight;
  private final ToLongFunction<? super V> weightFunction;
  private final BiConsumer<String, V> onEvict;
  private final LinkedHashMap<String, V> map;
  private final AtomicLong hits = new AtomicLong();
  private final AtomicLong misses = new AtomicLong();
  private long currentWeight;

  public BoundedImageCache(int maxEntries) {
    this(maxEntries, Long.MAX_VALUE, ignored -> 1L, null);
  }

  /**
   * Creates an LRU cache constrained by both entry count and total value weight.
   * Values whose individual weight exceeds the budget are returned to callers
   * but not retained by the cache.
   */
  public BoundedImageCache(
      int maxEntries,
      long maxWeight,
      ToLongFunction<? super V> weightFunction
  ) {
    this(maxEntries, maxWeight, weightFunction, null);
  }

  /**
   * Creates an LRU cache constrained by both entry count and total value weight, with an
   * optional listener invoked exactly once for every value that leaves the map (explicit
   * removal, budget eviction, clear, or key replacement). Pass {@code null} to preserve the
   * previous silent-drop behavior.
   */
  public BoundedImageCache(
      int maxEntries,
      long maxWeight,
      ToLongFunction<? super V> weightFunction,
      BiConsumer<String, V> onEvict
  ) {
    if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be >= 1");
    if (maxWeight < 1L) throw new IllegalArgumentException("maxWeight must be >= 1");
    if (weightFunction == null) throw new IllegalArgumentException("weightFunction cannot be null");
    this.maxEntries = maxEntries;
    this.maxWeight = maxWeight;
    this.weightFunction = weightFunction;
    this.onEvict = onEvict;
    this.map = new LinkedHashMap<>(16, 0.75f, true);
  }

  /** Returns the cached value, or {@code null} if absent. */
  public synchronized V get(String key) {
    V v = map.get(key);
    if (v != null) hits.incrementAndGet(); else misses.incrementAndGet();
    return v;
  }

  /**
   * Returns the cached value if present, otherwise calls {@code loader}, stores
   * the result, and returns it. The loader is called at most once per missing key
   * per call.
   */
  public synchronized V computeIfAbsent(String key, Function<String, V> loader) {
    V existing = map.get(key);
    if (existing != null) {
      hits.incrementAndGet();
      return existing;
    }
    misses.incrementAndGet();
    V computed = loader.apply(key);
    if (computed != null) {
      putInternal(key, computed);
    }
    return computed;
  }

  /** Explicitly stores a value, subject to LRU eviction like any other entry. */
  public synchronized void put(String key, V value) {
    if (value != null) putInternal(key, value);
  }

  /** Removes and returns one entry, or {@code null} when the key is absent. */
  public synchronized V remove(String key) {
    V removed = map.remove(key);
    if (removed != null) {
      currentWeight = Math.max(0L, currentWeight - weightOf(removed));
      fireEvict(key, removed);
    }
    return removed;
  }

  /** Removes every entry whose key matches the predicate. */
  public synchronized void removeKeysIf(Predicate<String> predicate) {
    if (predicate == null) return;
    Iterator<Map.Entry<String, V>> iterator = map.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, V> entry = iterator.next();
      if (predicate.test(entry.getKey())) {
        currentWeight = Math.max(0L, currentWeight - weightOf(entry.getValue()));
        fireEvict(entry.getKey(), entry.getValue());
        iterator.remove();
      }
    }
  }

  /** Removes all entries from the cache. */
  public synchronized void clear() {
    if (onEvict != null) {
      for (Map.Entry<String, V> entry : map.entrySet()) {
        fireEvict(entry.getKey(), entry.getValue());
      }
    }
    map.clear();
    currentWeight = 0L;
  }

  /** Returns the current number of entries. */
  public synchronized int size() {
    return map.size();
  }

  /** Returns the configured maximum number of entries. */
  public synchronized int maxEntries() {
    return maxEntries;
  }

  /** Changes the entry ceiling and immediately evicts LRU entries if necessary. */
  public synchronized void setMaxEntries(int maxEntries) {
    if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be >= 1");
    this.maxEntries = maxEntries;
    evictToBudget();
  }

  /** Returns the configured total value-weight budget. */
  public long maxWeight() { return maxWeight; }

  /** Returns the total estimated weight currently retained. */
  public synchronized long currentWeight() { return currentWeight; }

  /** Returns the cumulative hit count since construction or last reset. */
  public long hitCount() {
    return hits.get();
  }

  /** Returns the cumulative miss count since construction or last reset. */
  public long missCount() {
    return misses.get();
  }

  private void putInternal(String key, V value) {
    long weight = weightOf(value);
    V previous = map.remove(key);
    if (previous != null) {
      currentWeight = Math.max(0L, currentWeight - weightOf(previous));
      fireEvict(key, previous);
    }
    if (weight > maxWeight) return;

    map.put(key, value);
    currentWeight = saturatedAdd(currentWeight, weight);
    evictToBudget();
  }

  private void evictToBudget() {
    Iterator<Map.Entry<String, V>> iterator = map.entrySet().iterator();
    while ((map.size() > maxEntries || currentWeight > maxWeight) && iterator.hasNext()) {
      Map.Entry<String, V> eldest = iterator.next();
      currentWeight = Math.max(0L, currentWeight - weightOf(eldest.getValue()));
      fireEvict(eldest.getKey(), eldest.getValue());
      iterator.remove();
    }
  }

  private void fireEvict(String key, V value) {
    if (onEvict != null) onEvict.accept(key, value);
  }

  private long weightOf(V value) {
    if (value == null) return 0L;
    try {
      return Math.max(1L, weightFunction.applyAsLong(value));
    } catch (RuntimeException ignored) {
      return 1L;
    }
  }

  private static long saturatedAdd(long left, long right) {
    if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
    return left + right;
  }
}
