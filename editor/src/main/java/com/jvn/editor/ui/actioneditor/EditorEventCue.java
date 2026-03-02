package com.jvn.editor.ui.actioneditor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Editor-side representation of a VN-native event cue on the timeline.
 * Maps to {@link com.jvn.core.animation.TimelineData.EventCue} at export time.
 * <p>
 * Supported types: "expression", "dialogue_marker", "script_call", or any custom string.
 */
public class EditorEventCue implements Comparable<EditorEventCue> {
    private double timeMs;
    private String type;
    private final Map<String, String> payload;

    public EditorEventCue(double timeMs, String type, Map<String, String> payload) {
        this.timeMs = Math.max(0, timeMs);
        this.type = type != null ? type.trim() : "";
        this.payload = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
    }

    public double getTimeMs() { return timeMs; }
    public void setTimeMs(double timeMs) { this.timeMs = Math.max(0, timeMs); }

    public String getType() { return type; }
    public void setType(String type) { this.type = type != null ? type.trim() : ""; }

    public Map<String, String> getPayload() { return payload; }
    public String getPayloadValue(String key) { return payload.getOrDefault(key, ""); }
    public void setPayloadValue(String key, String value) { payload.put(key, value); }

    public Map<String, String> getPayloadView() {
        return Collections.unmodifiableMap(payload);
    }

    public EditorEventCue copy() {
        return new EditorEventCue(timeMs, type, new LinkedHashMap<>(payload));
    }

    @Override
    public int compareTo(EditorEventCue other) {
        return Double.compare(this.timeMs, other.timeMs);
    }

    @Override
    public String toString() {
        return String.format("EditorEventCue[t=%.0fms, %s, %s]", timeMs, type, payload);
    }
}
