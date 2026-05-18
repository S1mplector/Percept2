package com.jvn.editor.ui.actioneditor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
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

    public String getDisplayLabel() {
        String normalizedType = normalizedType();
        String typeLabel = normalizedType.isBlank() ? "event" : normalizedType;
        if ("expression".equals(normalizedType)) {
            String target = firstPayload("target");
            String value = firstPayload("value", "expression");
            String suffix = payload.containsKey("layers")
                ? " [layers]"
                : (payload.containsKey("path") ? " [path]" : "");
            return String.format(Locale.ROOT,
                "%.0fms  expression  %s -> %s%s",
                timeMs,
                blankFallback(target, "?"),
                blankFallback(value, "?"),
                suffix);
        }
        String target = firstPayload("target");
        String value = firstPayload("value", "expression", "id", "name");
        String suffix = "";
        if (!target.isBlank()) {
            suffix = "  -> " + target;
            if (!value.isBlank()) {
                suffix += " / " + value;
            }
        } else if (!value.isBlank()) {
            suffix = "  " + value;
        }
        return String.format(Locale.ROOT, "%.0fms  %s%s", timeMs, typeLabel, suffix);
    }

    public String getTimelineLabel() {
        String normalizedType = normalizedType();
        if ("expression".equals(normalizedType)) {
            String target = firstPayload("target");
            String value = firstPayload("value", "expression");
            String layerBadge = payload.containsKey("layers") ? "*" : "";
            return shorten("EXP " + blankFallback(target, "?") + ":" + blankFallback(value, "?") + layerBadge, 34);
        }
        String target = firstPayload("target");
        String base = switch (normalizedType) {
            case "show" -> "SHOW";
            case "hide" -> "HIDE";
            case "replace" -> "REPL";
            case "scene" -> "SCENE";
            case "dialogue_marker" -> "MARK";
            case "script_call" -> "CALL";
            default -> normalizedType.isBlank() ? "EVT" : normalizedType.toUpperCase(Locale.ROOT);
        };
        return shorten(target.isBlank() ? base : base + " " + target, 24);
    }

    public String getDslPreview() {
        String normalizedType = normalizedType();
        String target = firstPayload("target");
        boolean directAction = isDirectDslAction(normalizedType)
            && ("scene".equals(normalizedType) || !target.isBlank());
        String action = directAction ? normalizedType : "event";
        StringBuilder sb = new StringBuilder();
        sb.append(action);
        if (directAction && !target.isBlank()) {
            sb.append(" \"").append(escapeDsl(target)).append("\"");
        } else if (!directAction) {
            sb.append(" \"").append(escapeDsl(type)).append("\"");
        }
        sb.append(" {\n");

        LinkedHashMap<String, String> remaining = new LinkedHashMap<>(payload);
        remaining.remove("target");
        if ("expression".equals(normalizedType)) {
            String value = firstNonBlank(remaining.remove("value"), remaining.remove("expression"));
            appendDslLine(sb, "value", value);
        }
        for (Map.Entry<String, String> entry : remaining.entrySet()) {
            appendDslLine(sb, entry.getKey(), entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public int compareTo(EditorEventCue other) {
        return Double.compare(this.timeMs, other.timeMs);
    }

    @Override
    public String toString() {
        return String.format("EditorEventCue[t=%.0fms, %s, %s]", timeMs, type, payload);
    }

    private String normalizedType() {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isDirectDslAction(String normalizedType) {
        return switch (normalizedType) {
            case "expression", "show", "hide", "replace", "scene" -> true;
            default -> false;
        };
    }

    private String firstPayload(String... keys) {
        if (keys == null) return "";
        for (String key : keys) {
            if (key == null) continue;
            String value = payload.get(key);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String shorten(String text, int maxChars) {
        if (text == null) return "";
        String normalized = text.trim();
        if (normalized.length() <= maxChars) return normalized;
        return normalized.substring(0, Math.max(1, maxChars - 3)) + "...";
    }

    private static void appendDslLine(StringBuilder sb, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) return;
        sb.append("  ")
            .append(key.trim())
            .append(": \"")
            .append(escapeDsl(value.trim()))
            .append("\"\n");
    }

    private static String escapeDsl(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
