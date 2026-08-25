package com.jvn.scenerender.vn;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Retains values used by consecutive render frames and drops values as soon as a frame no longer
 * uses them.
 *
 * <p>This is intentionally a working-set cache rather than another fixed byte-budget cache. A
 * renderer must keep every independently transformed layer of the current frame available; if the
 * active layers exceed a smaller shared LRU budget, alternating characters evict and synchronously
 * reload one another on every frame. Values that disappear from the scene are released at the end
 * of the first frame that does not use them.</p>
 */
final class FrameRetainedCache<V> {
  private final Map<String, V> retained = new LinkedHashMap<>();
  private final Set<String> usedThisFrame = new LinkedHashSet<>();
  private boolean frameActive;

  void beginFrame() {
    usedThisFrame.clear();
    frameActive = true;
  }

  @Nullable V getOrLoad(String key, Function<String, V> loader) {
    if (key == null || key.isBlank() || loader == null) return null;
    if (!frameActive) return loader.apply(key);

    usedThisFrame.add(key);
    V value = retained.get(key);
    if (value != null) return value;

    value = loader.apply(key);
    if (value != null) retained.put(key, value);
    return value;
  }

  void endFrame() {
    if (!frameActive) return;
    retained.keySet().retainAll(usedThisFrame);
    usedThisFrame.clear();
    frameActive = false;
  }

  void clear() {
    retained.clear();
    usedThisFrame.clear();
    frameActive = false;
  }

  int size() {
    return retained.size();
  }
}
