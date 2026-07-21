package com.jvn.core.animation;

import com.jvn.plugin.api.ExtensionEntry;
import com.jvn.plugin.api.ExtensionRegistry;
import com.jvn.plugin.api.animation.AnimationEasing;
import com.jvn.plugin.api.animation.AnimationEasingFrame;
import com.jvn.plugin.api.animation.AnimationParameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Bridge between dependency-light plugin easing contracts and core animation evaluation. */
public final class EasingExtensions {
  private static volatile ExtensionRegistry<AnimationEasing> registry;

  private EasingExtensions() {}

  /** Installs the live host view used by parsers, previews, and runtime playback. */
  public static void install(ExtensionRegistry<AnimationEasing> extensions) {
    registry = extensions;
  }

  /** Removes a previously installed host view. Primarily useful for isolated tests. */
  public static void clear() { registry = null; }

  public static Optional<AnimationEasing> find(String id) {
    ExtensionRegistry<AnimationEasing> current = registry;
    if (current == null || id == null || id.isBlank()) return Optional.empty();
    return current.find(normalizeId(id));
  }

  public static List<ExtensionEntry<AnimationEasing>> entries() {
    ExtensionRegistry<AnimationEasing> current = registry;
    return current == null ? List.of() : current.entries();
  }

  static Map<String, Double> resolveParameters(AnimationEasing easing, Map<String, Double> supplied) {
    LinkedHashMap<String, Double> resolved = new LinkedHashMap<>();
    for (AnimationParameter parameter : easing.parameters()) {
      double value = supplied != null && supplied.containsKey(parameter.name())
          ? supplied.get(parameter.name()) : parameter.defaultValue();
      if (!Double.isFinite(value) || value < parameter.minimum() || value > parameter.maximum()) {
        throw new IllegalArgumentException("Parameter '" + parameter.name() + "' must be between "
            + parameter.minimum() + " and " + parameter.maximum());
      }
      resolved.put(parameter.name(), value);
    }
    if (supplied != null) {
      for (String name : supplied.keySet()) {
        if (!resolved.containsKey(name)) throw new IllegalArgumentException("Unknown easing parameter: " + name);
      }
    }
    return Map.copyOf(resolved);
  }

  static double apply(String id, Map<String, Double> supplied, double progress) {
    AnimationEasing easing = find(id).orElse(null);
    if (easing == null) return progress;
    try {
      double value = easing.evaluate(new AnimationEasingFrame(
          progress, resolveParameters(easing, supplied)));
      return Double.isFinite(value) ? value : progress;
    } catch (RuntimeException ignored) {
      return progress;
    }
  }

  static String normalizeId(String id) {
    return id.trim().toLowerCase(Locale.ROOT);
  }
}
