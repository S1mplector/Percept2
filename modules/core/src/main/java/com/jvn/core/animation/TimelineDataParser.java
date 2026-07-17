package com.jvn.core.animation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.graphics.Camera2D;
import com.jvn.core.scene2d.Entity2D;

/**
 * Lightweight parser that converts inline JES timeline blocks into {@link TimelineData}.
 * Supports: move, depth, pivot, wait, rotate, scale, mirror, fade, visible,
 * brightness/exposure, cameraMove, cameraZoom, property, event cues, and playAudio.
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
    private static final double EPS = 1e-6;
    private static final String CAMERA_TRACK = "__camera__";

    private static final Pattern MOVE_PATTERN = Pattern.compile(
        "move\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEPTH_PATTERN = Pattern.compile(
        "depth\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIVOT_PATTERN = Pattern.compile(
        "pivot\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROTATE_PATTERN = Pattern.compile(
        "rotate\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCALE_PATTERN = Pattern.compile(
        "scale\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIRROR_PATTERN = Pattern.compile(
        "mirror\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern FADE_PATTERN = Pattern.compile(
        "fade\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRIGHTNESS_PATTERN = Pattern.compile(
        "(?:brightness|exposure)\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern VISIBLE_PATTERN = Pattern.compile(
        "visible\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile(
        "expression\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOW_PATTERN = Pattern.compile(
        "show\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern HIDE_PATTERN = Pattern.compile(
        "hide\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLACE_PATTERN = Pattern.compile(
        "replace\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCENE_PATTERN = Pattern.compile(
        "scene(?:\\s+\"([^\"]+)\")?\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAMERA_MOVE_PATTERN = Pattern.compile(
        "cameraMove\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAMERA_ZOOM_PATTERN = Pattern.compile(
        "cameraZoom\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAY_AUDIO_PATTERN = Pattern.compile(
        "playAudio\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_PATTERN = Pattern.compile(
        "event\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
        "property(?:\\s+\"([^\"]+)\")?\\s*\\{", Pattern.CASE_INSENSITIVE);
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
        block = PuppeteerMotifExpander.expand(block);
        String[] lines = expandInlineBlocks(block).split("\\r?\\n");

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
            if (trimmed.equalsIgnoreCase("parallel") || trimmed.equalsIgnoreCase("parallel {")) {
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

            Matcher propertyM = PROPERTY_PATTERN.matcher(trimmed);
            if (propertyM.find()) {
                String entity = propertyM.group(1);
                if (entity == null || entity.isBlank()) entity = CAMERA_TRACK;
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                String propertyKey = decodeStringLiteral(ab.getString("key", ""));
                if (propertyKey.isBlank()) continue;

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));
                double value = ab.getDouble("value", 0.0);
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                addCustomTweenKeyframe(
                    track,
                    propertyKey,
                    defaultCustomPropertyValue(entity, propertyKey),
                    cursor,
                    endTime,
                    value,
                    easingSpec,
                    interpolation
                );
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            // move "entity" { ... }
            Matcher moveM = MOVE_PATTERN.matcher(trimmed);
            if (moveM.find()) {
                String entity = moveM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));

                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.has("x")) {
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.X,
                        cursor,
                        endTime,
                        ab.getDouble("x", 0),
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
                }
                if (ab.has("y")) {
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.Y,
                        cursor,
                        endTime,
                        ab.getDouble("y", 0),
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
                }
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            Matcher depthM = DEPTH_PATTERN.matcher(trimmed);
            if (depthM.find()) {
                String entity = depthM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));

                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.has("z")) {
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.Z,
                        cursor,
                        endTime,
                        ab.getDouble("z", 0),
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
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

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.has("ox")) {
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.PIVOT_X,
                        cursor,
                        endTime,
                        ab.getDouble("ox", 0),
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
                }
                if (ab.has("oy")) {
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.PIVOT_Y,
                        cursor,
                        endTime,
                        ab.getDouble("oy", 0),
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
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

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.hasAny("deg", "angle", "rotation")) {
                    double val = ab.getDoubleAny(0, "deg", "angle", "rotation");
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.ROTATION,
                        cursor,
                        endTime,
                        val,
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
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

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.hasAny("sx", "x", "scale_x")) {
                    double val = ab.getDoubleAny(1, "sx", "x", "scale_x");
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.SCALE_X,
                        cursor,
                        endTime,
                        val,
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
                }
                if (ab.hasAny("sy", "y", "scale_y")) {
                    double val = ab.getDoubleAny(1, "sy", "y", "scale_y");
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.SCALE_Y,
                        cursor,
                        endTime,
                        val,
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
                }
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            // mirror "entity" { mirrorX: 1 dur: 400 }
            Matcher mirrorM = MIRROR_PATTERN.matcher(trimmed);
            if (mirrorM.find()) {
                String entity = mirrorM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.hasAny("mirrorx", "mirror_x", "flipx", "flip_x", "x", "value")) {
                    double val = ab.hasAny("flipx", "flip_x", "value")
                        ? (ab.getBooleanAny(false, "flipx", "flip_x", "value") ? 1.0 : ab.getDoubleAny(0.0, "flipx", "flip_x", "value"))
                        : ab.getDoubleAny(0.0, "mirrorx", "mirror_x", "x");
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.MIRROR_X,
                        cursor,
                        endTime,
                        val,
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
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

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, entity);

                if (ab.has("alpha")) {
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.ALPHA,
                        cursor,
                        endTime,
                        ab.getDouble("alpha", 1),
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
                }
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            Matcher brightnessM = BRIGHTNESS_PATTERN.matcher(trimmed);
            if (brightnessM.find()) {
                String entity = brightnessM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));
                double endTime = cursor + dur;
                double value = ab.getDoubleAny(1.0, "value", "brightness", "exposure");
                TimelineData.Track track = getOrCreateTrack(data, entity);

                addCustomTweenKeyframe(
                    track,
                    "effect.brightness",
                    defaultCustomPropertyValue(entity, "effect.brightness"),
                    cursor,
                    endTime,
                    value,
                    easingSpec,
                    interpolation
                );
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            Matcher visibleM = VISIBLE_PATTERN.matcher(trimmed);
            if (visibleM.find()) {
                String entity = visibleM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                TimelineData.Track track = getOrCreateTrack(data, entity);
                boolean visible = ab.has("value")
                    ? ab.getBoolean("value", true)
                    : ab.has("visible")
                        ? ab.getBoolean("visible", true)
                        : true;
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));
                track.addKeyframe(TimelineData.Property.VISIBILITY,
                    new TimelineData.Keyframe(cursor, visible ? 1.0 : 0.0, easingSpec, interpolation));
                if (cursor > maxTime) maxTime = cursor;
                continue;
            }

            // cameraMove { ... }
            Matcher cameraMoveM = CAMERA_MOVE_PATTERN.matcher(trimmed);
            if (cameraMoveM.find()) {
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, CAMERA_TRACK);

                if (ab.has("x")) {
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.CAMERA_X,
                        cursor,
                        endTime,
                        ab.getDouble("x", 0),
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
                }
                if (ab.has("y")) {
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.CAMERA_Y,
                        cursor,
                        endTime,
                        ab.getDouble("y", 0),
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
                }
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            // cameraZoom { ... }
            Matcher cameraZoomM = CAMERA_ZOOM_PATTERN.matcher(trimmed);
            if (cameraZoomM.find()) {
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                double dur = ab.getDuration();
                EasingSpec easingSpec = parseEasingSpec(ab.getString("easing", "linear"));
                Easing.Interpolation interpolation = parseInterpolation(ab.getString("interp", "tween"));
                double endTime = cursor + dur;
                TimelineData.Track track = getOrCreateTrack(data, CAMERA_TRACK);

                if (ab.has("zoom")) {
                    addTweenKeyframe(
                        track,
                        TimelineData.Property.CAMERA_ZOOM,
                        cursor,
                        endTime,
                        ab.getDouble("zoom", 1),
                        easingSpec,
                        interpolation,
                        easingSpec.getParameters()
                    );
                }
                if (endTime > maxTime) maxTime = endTime;
                continue;
            }

            // event "type" { ... }
            Matcher expressionM = EXPRESSION_PATTERN.matcher(trimmed);
            if (expressionM.find()) {
                String target = expressionM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;
                java.util.Map<String, String> payload = buildEventPayload(ab);
                payload.put("target", target);
                data.addEventCue(new TimelineData.EventCue(cursor, "expression", payload));
                if (cursor > maxTime) maxTime = cursor;
                continue;
            }

            Matcher showM = SHOW_PATTERN.matcher(trimmed);
            if (showM.find()) {
                String target = showM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;
                java.util.Map<String, String> payload = buildEventPayload(ab);
                payload.put("target", target);
                data.addEventCue(new TimelineData.EventCue(cursor, "show", payload));
                if (cursor > maxTime) maxTime = cursor;
                continue;
            }

            Matcher hideM = HIDE_PATTERN.matcher(trimmed);
            if (hideM.find()) {
                String target = hideM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;
                java.util.Map<String, String> payload = buildEventPayload(ab);
                payload.put("target", target);
                data.addEventCue(new TimelineData.EventCue(cursor, "hide", payload));
                if (cursor > maxTime) maxTime = cursor;
                continue;
            }

            Matcher replaceM = REPLACE_PATTERN.matcher(trimmed);
            if (replaceM.find()) {
                String target = replaceM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;
                java.util.Map<String, String> payload = buildEventPayload(ab);
                payload.put("target", target);
                data.addEventCue(new TimelineData.EventCue(cursor, "replace", payload));
                if (cursor > maxTime) maxTime = cursor;
                continue;
            }

            Matcher sceneM = SCENE_PATTERN.matcher(trimmed);
            if (sceneM.find()) {
                String target = sceneM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;
                java.util.Map<String, String> payload = buildEventPayload(ab);
                if (target != null && !target.isBlank()) {
                    payload.put("target", target);
                }
                data.addEventCue(new TimelineData.EventCue(cursor, "scene", payload));
                if (cursor > maxTime) maxTime = cursor;
                continue;
            }

            Matcher eventM = EVENT_PATTERN.matcher(trimmed);
            if (eventM.find()) {
                String eventType = eventM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                java.util.Map<String, String> payload = buildEventPayload(ab);
                data.addEventCue(new TimelineData.EventCue(cursor, eventType, payload));
                if (cursor > maxTime) maxTime = cursor;
                continue;
            }

            // playAudio "path" { ... }
            Matcher playAudioM = PLAY_AUDIO_PATTERN.matcher(trimmed);
            if (playAudioM.find()) {
                String path = playAudioM.group(1);
                i++;
                ActionBlock ab = readBlock(lines, i);
                i = ab.endIndex;

                boolean bgm = ab.getBoolean("bgm", false);
                String channel = ab.getString("channel", "");
                if (channel == null || channel.isBlank()) {
                    channel = bgm ? "music" : "sound";
                }
                double volume = ab.getDouble("volume", 1.0);
                boolean loop = ab.getBoolean("loop", bgm);
                double fadeInMs = ab.getDouble("fadeinms",
                    ab.getDouble("fadein_ms",
                        ab.getDouble("fadein",
                            ab.getDouble("fade_in", 0))));

                data.addAudioCue(new TimelineData.AudioCue(
                    cursor,
                    path,
                    channel,
                    volume,
                    loop,
                    fadeInMs
                ));
                if (cursor > maxTime) maxTime = cursor;
                continue;
            }

            // Unknown line — skip
            i++;
        }

        // Patch duration
        return new TimelineData(name, maxTime) {{
            for (TimelineData.Track t : data.getTracks()) addTrack(t);
            for (TimelineData.AudioCue cue : data.getAudioCues()) addAudioCue(cue);
            for (TimelineData.EventCue cue : data.getEventCues()) addEventCue(cue);
        }};
    }

    // ---- helpers ----

    private static String expandInlineBlocks(String source) {
        if (source == null || source.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        String[] rawLines = source.split("\\r?\\n", -1);
        for (String rawLine : rawLines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();
            int open = trimmed.indexOf('{');
            int close = trimmed.lastIndexOf('}');
            if (open >= 0 && close > open && looksLikeInlineAction(trimmed.substring(0, open).trim())) {
                String indent = line.substring(0, Math.max(0, line.indexOf(trimmed)));
                out.append(indent).append(trimmed, 0, open + 1).append('\n');
                for (java.util.Map.Entry<String, String> prop : parseInlineProperties(trimmed.substring(open + 1, close)).entrySet()) {
                    out.append(indent).append("  ")
                        .append(prop.getKey())
                        .append(": ")
                        .append(prop.getValue())
                        .append('\n');
                }
                out.append(indent).append('}').append('\n');
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static boolean looksLikeInlineAction(String header) {
        if (header == null || header.isBlank()) return false;
        String first = header.split("\\s+", 2)[0];
        return TimelineActionSchema.isKnownAction(first) || "parallel".equalsIgnoreCase(first);
    }

    private static java.util.Map<String, String> parseInlineProperties(String body) {
        java.util.Map<String, String> props = new java.util.LinkedHashMap<>();
        if (body == null || body.isBlank()) return props;
        int i = 0;
        int len = body.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(body.charAt(i))) i++;
            int keyStart = i;
            while (i < len) {
                char c = body.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    i++;
                } else {
                    break;
                }
            }
            if (keyStart == i) break;
            String key = body.substring(keyStart, i);
            while (i < len && Character.isWhitespace(body.charAt(i))) i++;
            if (i >= len || body.charAt(i) != ':') {
                while (i < len && !Character.isWhitespace(body.charAt(i))) i++;
                continue;
            }
            i++;
            while (i < len && Character.isWhitespace(body.charAt(i))) i++;
            int valueStart = i;
            boolean inQuote = false;
            boolean escaping = false;
            while (i < len) {
                char c = body.charAt(i);
                if (escaping) {
                    escaping = false;
                    i++;
                    continue;
                }
                if (c == '\\' && inQuote) {
                    escaping = true;
                    i++;
                    continue;
                }
                if (c == '"') {
                    inQuote = !inQuote;
                    i++;
                    continue;
                }
                if (!inQuote && Character.isWhitespace(c) && nextInlineKeyStarts(body, i + 1)) {
                    break;
                }
                i++;
            }
            props.put(key.toLowerCase(), body.substring(valueStart, i).trim());
        }
        return props;
    }

    private static boolean nextInlineKeyStarts(String text, int index) {
        if (text == null) return false;
        int i = index;
        int len = text.length();
        while (i < len && Character.isWhitespace(text.charAt(i))) i++;
        if (i >= len || !(Character.isLetter(text.charAt(i)) || text.charAt(i) == '_')) return false;
        i++;
        while (i < len) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                i++;
            } else {
                break;
            }
        }
        while (i < len && Character.isWhitespace(text.charAt(i))) i++;
        return i < len && text.charAt(i) == ':';
    }

    private static TimelineData.Track getOrCreateTrack(TimelineData data, String entity) {
        TimelineData.Track existing = data.getTrack(entity);
        if (existing != null) return existing;
        TimelineData.Track track = new TimelineData.Track(entity);
        data.addTrack(track);
        return track;
    }

    private static void addTweenKeyframe(
        TimelineData.Track track,
        TimelineData.Property property,
        double startTime,
        double endTime,
        double targetValue,
        EasingSpec easingSpec,
        Easing.Interpolation interpolation,
        double[] easingParams
    ) {
        if (track == null || property == null) return;
        double start = Math.max(0.0, startTime);
        double end = Math.max(0.0, endTime);
        if (end <= start + EPS) {
            track.addKeyframe(property, new TimelineData.Keyframe(
                start, targetValue, easingSpec, interpolation));
            return;
        }
        if (!hasKeyframeAt(track, property, start)) {
            double startValue = track.getValueAt(property, start);
            track.addKeyframe(property, new TimelineData.Keyframe(
                start,
                startValue,
                Easing.Type.LINEAR,
                Easing.Interpolation.TWEEN,
                null
            ));
        }
        track.addKeyframe(property, new TimelineData.Keyframe(
            end, targetValue, easingSpec, interpolation));
    }

    private static void addCustomTweenKeyframe(
        TimelineData.Track track,
        String propertyKey,
        double defaultValue,
        double startTime,
        double endTime,
        double targetValue,
        EasingSpec easingSpec,
        Easing.Interpolation interpolation
    ) {
        if (track == null || propertyKey == null || propertyKey.isBlank()) return;
        String normalized = propertyKey.trim();
        double start = Math.max(0.0, startTime);
        double end = Math.max(0.0, endTime);
        if (end <= start + EPS) {
            track.addCustomKeyframe(normalized, new TimelineData.Keyframe(
                start, targetValue, easingSpec, interpolation));
            return;
        }
        if (!hasCustomKeyframeAt(track, normalized, start)) {
            double startValue = track.getCustomValueAt(normalized, start, defaultValue);
            track.addCustomKeyframe(normalized, new TimelineData.Keyframe(
                start,
                startValue,
                Easing.Type.LINEAR,
                Easing.Interpolation.TWEEN,
                null
            ));
        }
        track.addCustomKeyframe(normalized, new TimelineData.Keyframe(
            end, targetValue, easingSpec, interpolation));
    }

    private static boolean hasKeyframeAt(TimelineData.Track track, TimelineData.Property property, double timeMs) {
        if (track == null || property == null) return false;
        for (TimelineData.Keyframe keyframe : track.getKeyframes(property)) {
            if (Math.abs(keyframe.getTimeMs() - timeMs) <= EPS) return true;
        }
        return false;
    }

    private static double defaultCustomPropertyValue(String entityName, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) return 0.0;
        if (CAMERA_TRACK.equals(entityName)) {
            var definition = Camera2D.getAnimatableProperty(propertyKey);
            return definition != null ? definition.getDefaultValue() : 0.0;
        }
        var definition = Entity2D.getAnimatableProperty(propertyKey);
        return definition != null ? definition.getDefaultValue() : 0.0;
    }

    private static boolean hasCustomKeyframeAt(TimelineData.Track track, String propertyKey, double timeMs) {
        if (track == null || propertyKey == null || propertyKey.isBlank()) return false;
        for (TimelineData.Keyframe keyframe : track.getCustomKeyframes(propertyKey)) {
            if (Math.abs(keyframe.getTimeMs() - timeMs) <= EPS) return true;
        }
        return false;
    }

    private static EasingSpec parseEasingSpec(String s) {
        return EasingSpec.parseOrDefault(s);
    }

    private static Easing.Interpolation parseInterpolation(String raw) {
        if (raw == null || raw.isBlank()) return Easing.Interpolation.TWEEN;
        String normalized = raw.trim().toLowerCase().replace('-', '_');
        return switch (normalized) {
            case "hold", "step_end", "step_hold", "constant" -> Easing.Interpolation.HOLD;
            case "step", "step_start", "instant", "jump" -> Easing.Interpolation.STEP;
            default -> Easing.Interpolation.TWEEN;
        };
    }

    private static java.util.Map<String, String> buildEventPayload(ActionBlock ab) {
        java.util.Map<String, String> payload = new java.util.LinkedHashMap<>();
        if (ab == null) return payload;
        for (java.util.Map.Entry<String, String> entry : ab.props.entrySet()) {
            payload.put(entry.getKey(), decodeStringLiteral(entry.getValue()));
        }
        return payload;
    }

    private static String decodeStringLiteral(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
            text = text.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return text;
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
        boolean hasAny(String... keys) {
            if (keys == null) return false;
            for (String key : keys) {
                if (has(key)) return true;
            }
            return false;
        }
        String getString(String key, String def) { return props.getOrDefault(key, def); }

        boolean getBoolean(String key, boolean def) {
            String v = props.get(key);
            if (v == null) return def;
            String t = v.trim();
            if ("true".equalsIgnoreCase(t) || "1".equals(t) || "yes".equalsIgnoreCase(t)) return true;
            if ("false".equalsIgnoreCase(t) || "0".equals(t) || "no".equalsIgnoreCase(t)) return false;
            return def;
        }

        boolean getBooleanAny(boolean def, String... keys) {
            if (keys == null) return def;
            for (String key : keys) {
                if (props.containsKey(key)) return getBoolean(key, def);
            }
            return def;
        }

        double getDouble(String key, double def) {
            String v = props.get(key);
            if (v == null) return def;
            try { return Double.parseDouble(v); } catch (Exception e) { return def; }
        }

        double getDoubleAny(double def, String... keys) {
            if (keys == null) return def;
            for (String key : keys) {
                if (props.containsKey(key)) return getDouble(key, def);
            }
            return def;
        }

        double getDuration() {
            return getDoubleAny(0, "dur", "duration");
        }
    }
}
