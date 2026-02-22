package com.jvn.core.animation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight parser that converts inline JES timeline blocks into {@link TimelineData}.
 * Supports: move, pivot, wait, rotate, scale, fade, visible.
 *
 * <pre>
 * timeline {
 *   move "entity" {
 *     x: 640
 *     y: 468
 *     dur: 340
 *     easing: ease_in_out
 *   }
 *   wait 200
 *   fade "entity" {
 *     alpha: 0
 *     dur: 500
 *   }
 * }
 * </pre>
 */
public class TimelineDataParser {

    private static final Pattern MOVE_PATTERN = Pattern.compile(
        "move\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIVOT_PATTERN = Pattern.compile(
        "pivot\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROTATE_PATTERN = Pattern.compile(
        "rotate\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCALE_PATTERN = Pattern.compile(
        "scale\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern FADE_PATTERN = Pattern.compile(
        "fade\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern WAIT_PATTERN = Pattern.compile(
        "wait\\s+(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROP_PATTERN = Pattern.compile(
        "^\\s*(\\w+)\\s*:\\s*(.+)\\s*$");

    /**
     * Parse an inline timeline block (the text between outer {@code timeline \{} and {@code \}})
     * into a {@link TimelineData}.
     *
     * @param name the timeline name (can be auto-generated)
     * @param block the text content inside the timeline braces
     * @return parsed TimelineData, never null
     */
    public static TimelineData parse(String name, String block) {
        if (block == null) block = "";
        String[] lines = block.split("\\r?\\n");

        double cursor = 0;
        double maxTime = 0;
        TimelineData data = new TimelineData(name, 0);

        int i = 0;
        while (i < lines.length) {
            String trimmed = lines[i].trim();

            // Skip empty lines, comments, and braces
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                i++;
                continue;
            }

            // timeline { ... } wrapper — skip the outer braces
            if (trimmed.startsWith("timeline")) {
                i++;
                continue;
            }
            if (trimmed.equals("{") || trimmed.equals("}")) {
                i++;
                continue;
            }

            // wait <ms>
            Matcher waitM = WAIT_PATTERN.matcher(trimmed);
            if (waitM.matches()) {
                cursor += Double.parseDouble(waitM.group(1));
                if (cursor > maxTime) maxTime = cursor;
                i++;
                continue;
            }

            // move "entity" { ... }
            Matcher moveM = MOVE_PATTERN.matcher(trimmed);
            if (moveM.find()) {
                String entity = moveM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDouble("dur", ab.getDouble("duration", 0));
                Easing.Type easing = parseEasing(ab.getString("easing", "linear"));

                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.has("x")) {
                    track.addKeyframe(TimelineData.Property.X,
                        new TimelineData.Keyframe(endTime, ab.getDouble("x", 0), easing));
                }
                if (ab.has("y")) {
                    track.addKeyframe(TimelineData.Property.Y,
                        new TimelineData.Keyframe(endTime, ab.getDouble("y", 0), easing));
                }
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            // pivot "entity" { ... }
            Matcher pivotM = PIVOT_PATTERN.matcher(trimmed);
            if (pivotM.find()) {
                String entity = pivotM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDouble("dur", ab.getDouble("duration", 0));
                Easing.Type easing = parseEasing(ab.getString("easing", "linear"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.has("ox")) {
                    track.addKeyframe(TimelineData.Property.PIVOT_X,
                        new TimelineData.Keyframe(endTime, ab.getDouble("ox", 0), easing));
                }
                if (ab.has("oy")) {
                    track.addKeyframe(TimelineData.Property.PIVOT_Y,
                        new TimelineData.Keyframe(endTime, ab.getDouble("oy", 0), easing));
                }
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            // rotate "entity" { ... }
            Matcher rotM = ROTATE_PATTERN.matcher(trimmed);
            if (rotM.find()) {
                String entity = rotM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDouble("dur", ab.getDouble("duration", 0));
                Easing.Type easing = parseEasing(ab.getString("easing", "linear"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.has("angle") || ab.has("rotation")) {
                    double val = ab.has("angle") ? ab.getDouble("angle", 0) : ab.getDouble("rotation", 0);
                    track.addKeyframe(TimelineData.Property.ROTATION,
                        new TimelineData.Keyframe(endTime, val, easing));
                }
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            // scale "entity" { ... }
            Matcher scaleM = SCALE_PATTERN.matcher(trimmed);
            if (scaleM.find()) {
                String entity = scaleM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDouble("dur", ab.getDouble("duration", 0));
                Easing.Type easing = parseEasing(ab.getString("easing", "linear"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.has("x") || ab.has("scale_x")) {
                    double val = ab.has("scale_x") ? ab.getDouble("scale_x", 1) : ab.getDouble("x", 1);
                    track.addKeyframe(TimelineData.Property.SCALE_X,
                        new TimelineData.Keyframe(endTime, val, easing));
                }
                if (ab.has("y") || ab.has("scale_y")) {
                    double val = ab.has("scale_y") ? ab.getDouble("scale_y", 1) : ab.getDouble("y", 1);
                    track.addKeyframe(TimelineData.Property.SCALE_Y,
                        new TimelineData.Keyframe(endTime, val, easing));
                }
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            // fade "entity" { ... }
            Matcher fadeM = FADE_PATTERN.matcher(trimmed);
            if (fadeM.find()) {
                String entity = fadeM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDouble("dur", ab.getDouble("duration", 0));
                Easing.Type easing = parseEasing(ab.getString("easing", "linear"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.has("alpha")) {
                    track.addKeyframe(TimelineData.Property.ALPHA,
                        new TimelineData.Keyframe(endTime, ab.getDouble("alpha", 1), easing));
                }
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            // Unknown line — skip
            i++;
        }

        // Patch duration
        return new TimelineData(name, maxTime) {{
            for (TimelineData.Track t : data.getTracks()) addTrack(t);
        }};
    }

    // ---- helpers ----

    private static TimelineData.Track getOrCreateTrack(TimelineData data, String entity) {
        TimelineData.Track existing = data.getTrack(entity);
        if (existing != null) return existing;
        TimelineData.Track track = new TimelineData.Track(entity);
        data.addTrack(track);
        return track;
    }

    private static Easing.Type parseEasing(String s) {
        if (s == null || s.isBlank()) return Easing.Type.LINEAR;
        try {
            return Easing.Type.valueOf(s.toUpperCase().replace('-', '_'));
        } catch (Exception ignored) {
            return Easing.Type.LINEAR;
        }
    }

    private static ActionBlock readBlock(String[] lines, int start) {
        ActionBlock ab = new ActionBlock();
        int i = start;
        int depth = 0;
        while (i < lines.length) {
            String t = lines[i].trim();
            if (t.equals("{")) { depth++; i++; continue; }
            if (t.equals("}") || t.endsWith("}")) {
                if (depth > 0) { depth--; i++; continue; }
                i++;
                break;
            }
            Matcher pm = PROP_PATTERN.matcher(t);
            if (pm.matches()) {
                ab.put(pm.group(1).trim().toLowerCase(), pm.group(2).trim());
            }
            i++;
        }
        ab.endIndex = i;
        return ab;
    }

    private static class ActionBlock {
        private final java.util.Map<String, String> props = new java.util.LinkedHashMap<>();
        int endIndex;

        void put(String key, String value) { props.put(key, value); }
        boolean has(String key) { return props.containsKey(key); }
        String getString(String key, String def) { return props.getOrDefault(key, def); }

        double getDouble(String key, double def) {
            String v = props.get(key);
            if (v == null) return def;
            try { return Double.parseDouble(v); } catch (Exception e) { return def; }
        }
    }
}
