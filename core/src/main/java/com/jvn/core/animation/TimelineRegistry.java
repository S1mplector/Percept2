package com.jvn.core.animation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Global registry of named {@link TimelineData} instances.
 * Puppeteer registers timelines here; the VNS runtime looks them up
 * via {@code @external jes_timeline <name>}.
 */
public class TimelineRegistry {

    private static final Map<String, TimelineData> timelines = new LinkedHashMap<>();

    public static void register(TimelineData timeline) {
        if (timeline != null && timeline.getName() != null && !timeline.getName().isBlank()) {
            timelines.put(timeline.getName(), timeline);
        }
    }

    public static TimelineData get(String name) {
        return name != null ? timelines.get(name) : null;
    }

    public static boolean has(String name) {
        return name != null && timelines.containsKey(name);
    }

    public static void remove(String name) {
        if (name != null) timelines.remove(name);
    }

    public static void clear() {
        timelines.clear();
    }

    public static Set<String> names() {
        return Collections.unmodifiableSet(timelines.keySet());
    }

    public static int size() {
        return timelines.size();
    }
}
