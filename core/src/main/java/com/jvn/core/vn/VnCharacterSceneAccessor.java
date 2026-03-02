package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jvn.core.animation.SceneAccessor;
import com.jvn.core.scene2d.Entity2D;

/**
 * Lightweight {@link SceneAccessor} for the VNS preview that creates virtual
 * {@link Entity2D} proxies for VN character names.  When an inline timeline
 * animates x/y on a proxy, the VN renderer can read those coordinates directly
 * instead of using the slot-based positioning.
 */
public class VnCharacterSceneAccessor implements SceneAccessor {
    private final Map<String, Entity2D> proxies = new ConcurrentHashMap<>();
    private final List<EventRecord> eventLog = new ArrayList<>();
    private String lastExpressionTarget = "";
    private String lastExpressionValue = "";

    /** Immutable record of a dispatched event cue for diagnostics / preview log. */
    public record EventRecord(String type, Map<String, String> payload) {}

    @Override
    public Entity2D findEntity(String name) {
        if (name == null || name.isBlank()) return null;
        return proxies.computeIfAbsent(name, k -> new Entity2D());
    }

    @Override
    public void onEventCue(String type, Map<String, String> payload) {
        eventLog.add(new EventRecord(type, payload));
        if ("expression".equals(type)) {
            lastExpressionTarget = payload.getOrDefault("target", "");
            lastExpressionValue = payload.getOrDefault("value", "");
        }
    }

    /**
     * Returns the proxy entity for a character if one was created by a timeline,
     * or {@code null} if no timeline has referenced this name.
     */
    public Entity2D getProxy(String characterName) {
        return proxies.get(characterName);
    }

    /** Returns true if any proxy has been created. */
    public boolean hasProxies() {
        return !proxies.isEmpty();
    }

    /** Returns an unmodifiable view of all event cues dispatched so far. */
    public List<EventRecord> getEventLog() {
        return Collections.unmodifiableList(eventLog);
    }

    public String getLastExpressionTarget() { return lastExpressionTarget; }
    public String getLastExpressionValue() { return lastExpressionValue; }

    /** Clears all proxy entities and event log (call when reloading a scenario). */
    public void clear() {
        proxies.clear();
        eventLog.clear();
        lastExpressionTarget = "";
        lastExpressionValue = "";
    }
}
