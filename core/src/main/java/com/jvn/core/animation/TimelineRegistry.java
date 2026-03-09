package com.jvn.core.animation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Global, static registry of named {@link TimelineData} instances.
 *
 * <p>The Puppeteer editor (or any other producer) registers compiled
 * timelines here at load time. The VNS runtime retrieves them during
 * script execution via {@code @external jes_timeline <name>} and hands
 * them to a {@link TimelineRunner} for playback.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>The registry is not synchronised. All registration should occur on the
 * main thread before playback begins.</p>
 *
 * @see TimelineData
 * @see TimelineRunner
 * @see TimelineDataParser
 */
public class TimelineRegistry {

    /** Name → timeline lookup table. Insertion order preserved. */
    private static final Map<String, TimelineData> timelines = new LinkedHashMap<>();

    /**
     * Register a timeline. Timelines with {@code null} or blank names are
     * silently ignored.
     *
     * @param timeline the timeline to register
     */
    public static void register(TimelineData timeline) {
        if (timeline != null && timeline.getName() != null && !timeline.getName().isBlank()) {
            timelines.put(timeline.getName(), timeline);
        }
    }

    /**
     * Retrieve a timeline by name.
     *
     * @param name the timeline name
     * @return the matching timeline, or {@code null}
     */
    public static TimelineData get(String name) {
        return name != null ? timelines.get(name) : null;
    }

    /** @return {@code true} if a timeline with the given name exists */
    public static boolean has(String name) {
        return name != null && timelines.containsKey(name);
    }

    /** Remove a timeline by name. */
    public static void remove(String name) {
        if (name != null) timelines.remove(name);
    }

    /** Remove all registered timelines. */
    public static void clear() {
        timelines.clear();
    }

    /** @return an unmodifiable set of all registered timeline names */
    public static Set<String> names() {
        return Collections.unmodifiableSet(timelines.keySet());
    }

    /** @return the number of registered timelines */
    public static int size() {
        return timelines.size();
    }
}
