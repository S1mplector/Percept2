package com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Generic "give me the same instance for the same key" cache.
 *
 * Every *WarningFactory that wants singleton-per-identity behavior holds one
 * of these (composition) instead of re-implementing its own static Map.
 * Keeps each factory's only real job to be "build this one warning" (SRP),
 * while this class's only job is "remember instances by key" (SRP too).
 */
public final class WarningInstanceCache<T> {

    private final Map<String, T> instances = new ConcurrentHashMap<>();

    public T getOrCreate(String key, Supplier<T> factory) {
        return instances.computeIfAbsent(key, k -> factory.get());
    }
}