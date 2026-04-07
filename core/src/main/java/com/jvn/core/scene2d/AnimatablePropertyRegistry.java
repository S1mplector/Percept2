package com.jvn.core.scene2d;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

/**
 * Registry of named numeric properties that can be animated against a target type.
 *
 * <p>This is used by Puppeteer timelines to route arbitrary custom channels
 * onto engine objects without hardcoding every property into the timeline model.</p>
 */
public final class AnimatablePropertyRegistry<T> {
  public static final class Definition<T> {
    private final String key;
    private final double defaultValue;
    private final ToDoubleFunction<T> getter;
    private final ObjDoubleConsumer<T> setter;

    private Definition(
        String key,
        double defaultValue,
        ToDoubleFunction<T> getter,
        ObjDoubleConsumer<T> setter
    ) {
      this.key = key;
      this.defaultValue = defaultValue;
      this.getter = getter;
      this.setter = setter;
    }

    public String getKey() { return key; }
    public double getDefaultValue() { return defaultValue; }
    public boolean hasGetter() { return getter != null; }
    public boolean hasSetter() { return setter != null; }

    public double getValue(T target) {
      return getter != null && target != null ? getter.applyAsDouble(target) : defaultValue;
    }

    public void setValue(T target, double value) {
      if (setter != null && target != null) setter.accept(target, value);
    }
  }

  private final Map<String, Definition<T>> definitions = new LinkedHashMap<>();

  public synchronized Definition<T> register(
      String key,
      double defaultValue,
      ToDoubleFunction<T> getter,
      ObjDoubleConsumer<T> setter
  ) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Property key must not be blank");
    }
    Definition<T> definition = new Definition<>(key.trim(), defaultValue, getter, setter);
    definitions.put(definition.getKey(), definition);
    return definition;
  }

  public synchronized Definition<T> get(String key) {
    if (key == null || key.isBlank()) return null;
    return definitions.get(key.trim());
  }

  public synchronized boolean contains(String key) {
    if (key == null || key.isBlank()) return false;
    return definitions.containsKey(key.trim());
  }

  public synchronized Collection<Definition<T>> definitions() {
    return Collections.unmodifiableCollection(definitions.values());
  }
}
