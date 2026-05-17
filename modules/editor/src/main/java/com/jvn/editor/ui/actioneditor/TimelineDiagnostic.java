package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.core.animation.TimelineActionSchema;

/**
 * Diagnostic engine for Puppeteer timelines. Scans an {@link AnimationProject}
 * and timeline DSL text and produces non-blocking warning/error messages with
 * optional quick-fix suggestions.
 */
public class TimelineDiagnostic {

    public enum Severity { INFO, WARNING, ERROR }

    public record Message(
        Severity severity,
        String entityOrTrack,
        String description,
        String quickFix,
        int line
    ) {
        public Message(Severity severity, String entityOrTrack, String description, String quickFix) {
            this(severity, entityOrTrack, description, quickFix, -1);
        }

        public boolean hasLine() {
            return line > 0;
        }
    }

    private static final Set<String> KNOWN_EASINGS;
    private static final Set<String> KNOWN_INTERPOLATIONS;
    private static final Set<String> KNOWN_ACTIONS;
    private static final Set<String> KNOWN_AUDIO_CHANNELS;
    private static final double TIME_EPSILON_MS = 0.001;
    private static final Pattern WAIT_PATTERN = Pattern.compile("^wait\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_WITH_TARGET_PATTERN = Pattern.compile("^(\\w+)\\s+\"([^\"]+)\"\\s*\\{\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_NO_TARGET_PATTERN = Pattern.compile("^(\\w+)\\s*\\{\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROP_PATTERN = Pattern.compile("^(\\w+)\\s*:\\s*(.+)$");
    private static final Map<String, Set<String>> ACTION_KEYS;
    private static final double SUB_FRAME_TWEEN_MS = 16.0;
    private static final double OVERLAP_SEGMENT_MS = 1.0;
    private static final double VALUE_EPSILON = 0.0001;

    static {
        Set<String> easings = new LinkedHashSet<>();
        for (Easing.Type t : Easing.Type.values()) {
            String name = t.name().toLowerCase(Locale.ROOT);
            if (!"custom".equals(name)) easings.add(name);
        }
        easings.add("cubic_bezier");
        KNOWN_EASINGS = Collections.unmodifiableSet(easings);
        KNOWN_INTERPOLATIONS = Collections.unmodifiableSet(lowerSet(Set.of(
            "tween", "hold", "step"
        )));

        Set<String> actions = new LinkedHashSet<>(TimelineActionSchema.knownActions());
        actions.add("move");
        actions.add("depth");
        actions.add("pivot");
        actions.add("rotate");
        actions.add("scale");
        actions.add("mirror");
        actions.add("fade");
        actions.add("brightness");
        actions.add("exposure");
        actions.add("visible");
        actions.add("expression");
        actions.add("show");
        actions.add("hide");
        actions.add("replace");
        actions.add("scene");
        actions.add("cameraMove");
        actions.add("cameraZoom");
        actions.add("event");
        actions.add("property");
        actions.add("playAudio");
        actions.add("parallel");
        actions.add("timeline");
        actions.add("wait");
        KNOWN_ACTIONS = Collections.unmodifiableSet(lowerSet(actions));
        KNOWN_AUDIO_CHANNELS = Collections.unmodifiableSet(lowerSet(Set.of("music", "sound", "voice")));

        Map<String, Set<String>> keys = new LinkedHashMap<>();
        keys.put("move", lowerSet(Set.of("x", "y", "dur", "duration", "easing", "interp")));
        keys.put("depth", lowerSet(Set.of("z", "dur", "duration", "easing", "interp")));
        keys.put("pivot", lowerSet(Set.of("ox", "oy", "dur", "duration", "easing", "interp")));
        keys.put("rotate", lowerSet(Set.of("angle", "rotation", "deg", "dur", "duration", "easing", "interp")));
        keys.put("scale", lowerSet(Set.of("x", "y", "sx", "sy", "scale_x", "scale_y", "dur", "duration", "easing", "interp")));
        keys.put("mirror", lowerSet(Set.of("mirrorx", "mirror_x", "flipx", "flip_x", "x", "value", "dur", "duration", "easing", "interp")));
        keys.put("fade", lowerSet(Set.of("alpha", "dur", "duration", "easing", "interp")));
        keys.put("brightness", lowerSet(Set.of("value", "brightness", "exposure", "dur", "duration", "easing", "interp")));
        keys.put("exposure", lowerSet(Set.of("value", "brightness", "exposure", "dur", "duration", "easing", "interp")));
        keys.put("visible", lowerSet(Set.of("value", "visible")));
        keys.put("expression", lowerSet(Set.of("value", "expression", "path", "layers", "position")));
        keys.put("show", lowerSet(Set.of("target", "expression", "value", "path", "layers", "position", "layer")));
        keys.put("hide", lowerSet(Set.of("target")));
        keys.put("replace", lowerSet(Set.of("target", "expression", "value", "path", "layers")));
        keys.put("scene", lowerSet(Set.of("target", "id", "path", "value")));
        keys.put("cameramove", lowerSet(Set.of("x", "y", "dur", "duration", "easing", "interp")));
        keys.put("camerazoom", lowerSet(Set.of("zoom", "dur", "duration", "easing", "interp")));
        keys.put("property", lowerSet(Set.of("key", "value", "dur", "duration", "easing", "interp")));
        keys.put("playaudio", lowerSet(Set.of("channel", "volume", "loop", "bgm", "fadeinms", "fadein_ms", "fadein", "fade_in")));
        for (String action : TimelineActionSchema.knownActions()) {
            Set<String> accepted = TimelineActionSchema.acceptedKeys(action);
            if (accepted.isEmpty()) continue;
            keys.merge(action, accepted, (existing, shared) -> {
                LinkedHashSet<String> merged = new LinkedHashSet<>(existing);
                merged.addAll(shared);
                return Collections.unmodifiableSet(merged);
            });
        }
        ACTION_KEYS = Collections.unmodifiableMap(keys);
    }

    /**
     * Run diagnostics on the given project and return a list of messages.
     */
    public static List<Message> diagnose(AnimationProject project, Set<String> knownEntities) {
        if (project == null) return Collections.emptyList();
        List<Message> msgs = new ArrayList<>();
        double durationMs = project.getTotalDurationMs();

        for (EntityTrack track : project.getTracks()) {
            String entity = track.getEntityName();

            if (knownEntities != null && !knownEntities.isEmpty()
                && !entity.startsWith("__") && !knownEntities.contains(entity)) {
                msgs.add(new Message(Severity.WARNING, entity,
                    "Entity \"" + entity + "\" not found in current scene",
                    "Create placeholder entity or check spelling"));
            }

            for (PropertyType prop : PropertyType.values()) {
                List<Keyframe> kfs = track.getKeyframes(prop);
                for (Keyframe kf : kfs) {
                    if (!Double.isFinite(kf.getTimeMs())) {
                        msgs.add(new Message(Severity.ERROR, entity,
                            prop.getDisplayName() + " has a non-finite keyframe time",
                            "Set a numeric keyframe timestamp"));
                        continue;
                    }
                    if (!Double.isFinite(kf.getValue())) {
                        msgs.add(new Message(Severity.ERROR, entity,
                            prop.getDisplayName() + " has a non-finite keyframe value at "
                                + formatNumber(kf.getTimeMs()) + "ms",
                            "Replace NaN/Infinity with a numeric value"));
                        continue;
                    }
                    if (Double.isFinite(durationMs) && kf.getTimeMs() > durationMs + TIME_EPSILON_MS) {
                        msgs.add(new Message(Severity.WARNING, entity,
                            prop.getDisplayName() + " keyframe at " + formatNumber(kf.getTimeMs())
                                + "ms exceeds timeline duration " + formatNumber(durationMs) + "ms",
                            "Fit timeline duration to content or move keyframe earlier"));
                    }
                    if (prop == PropertyType.ALPHA) {
                        if (kf.getValue() < 0.0 || kf.getValue() > 1.0) {
                            msgs.add(new Message(Severity.WARNING, entity,
                                "Alpha value " + kf.getValue() + " at " + kf.getTimeMs()
                                    + "ms is out of [0,1] range",
                                "Clamp to " + Math.max(0, Math.min(1, kf.getValue()))));
                        }
                    }
                    if (prop == PropertyType.VISIBILITY) {
                        if (kf.getValue() < 0.0 || kf.getValue() > 1.0) {
                            msgs.add(new Message(Severity.WARNING, entity,
                                "Visibility value " + formatNumber(kf.getValue()) + " at "
                                    + formatNumber(kf.getTimeMs()) + "ms is out of [0,1] range",
                                "Use 0/1 for visibility, or animate Opacity for fades"));
                        } else if (Math.abs(kf.getValue()) > VALUE_EPSILON
                            && Math.abs(kf.getValue() - 1.0) > VALUE_EPSILON) {
                            msgs.add(new Message(Severity.INFO, entity,
                                "Visibility value " + formatNumber(kf.getValue()) + " at "
                                    + formatNumber(kf.getTimeMs()) + "ms is fractional",
                                "Use Opacity for partial transparency and Visibility for on/off cuts"));
                        }
                    }
                    if (prop == PropertyType.CAMERA_ZOOM) {
                        if (kf.getValue() <= 0.0) {
                            msgs.add(new Message(Severity.WARNING, entity,
                                "Camera zoom " + kf.getValue() + " at " + kf.getTimeMs()
                                    + "ms is non-positive",
                                "Set to 0.01 minimum"));
                        }
                        if (kf.getValue() > 10.0) {
                            msgs.add(new Message(Severity.WARNING, entity,
                                "Camera zoom " + kf.getValue() + " at " + kf.getTimeMs()
                                    + "ms is unusually large (>10x)",
                                null));
                        }
                    }
                    if (prop == PropertyType.PIVOT_X || prop == PropertyType.PIVOT_Y) {
                        if (kf.getValue() < 0.0 || kf.getValue() > 1.0) {
                            msgs.add(new Message(Severity.WARNING, entity,
                                prop.getDisplayName() + " value " + kf.getValue()
                                    + " at " + kf.getTimeMs() + "ms is out of [0,1] range",
                                "Clamp to " + Math.max(0, Math.min(1, kf.getValue()))));
                        }
                    }
                    if ((prop == PropertyType.SCALE_X || prop == PropertyType.SCALE_Y)
                        && Math.abs(kf.getValue()) < VALUE_EPSILON) {
                        msgs.add(new Message(Severity.WARNING, entity,
                            prop.getDisplayName() + " is near zero at "
                                + formatNumber(kf.getTimeMs()) + "ms",
                            "Use a tiny non-zero scale or fade opacity if the entity should disappear"));
                    }
                    if (kf.getEasing() != null && kf.getEasing() != Easing.Type.CUSTOM) {
                        String easingName = kf.getEasing().name().toLowerCase(Locale.ROOT);
                        if (!KNOWN_EASINGS.contains(easingName)) {
                            msgs.add(new Message(Severity.ERROR, entity,
                                "Unknown easing \"" + easingName + "\" at " + kf.getTimeMs() + "ms",
                                "Use 'linear' or another known easing"));
                        }
                    }
                }
                diagnoseKeyframeSpacing(msgs, entity, prop, kfs);
            }
        }

        Map<String, List<Double>> cueTimesByChannel = new LinkedHashMap<>();
        for (AudioCue cue : project.getAudioCues()) {
            String rawChannel = cue.getChannel() == null ? "" : cue.getChannel().trim();
            String normalizedChannel = rawChannel.toLowerCase(Locale.ROOT);

            if (cue.getAudioFile() == null || cue.getAudioFile().trim().isEmpty()) {
                msgs.add(new Message(Severity.ERROR, "(audio)",
                    "Audio cue at " + formatNumber(cue.getTimeMs()) + "ms has empty audio path",
                    "Set an asset path like assets/audio/sfx/click.wav"));
            }
            if (rawChannel.isBlank()) {
                msgs.add(new Message(Severity.ERROR, "(audio)",
                    "Audio cue at " + formatNumber(cue.getTimeMs()) + "ms has empty channel",
                    "Use 'music', 'sound', or 'voice'"));
            } else if (!KNOWN_AUDIO_CHANNELS.contains(normalizedChannel)) {
                msgs.add(new Message(Severity.WARNING, "(audio)",
                    "Audio cue channel '" + rawChannel + "' is non-standard",
                    "Prefer music/sound/voice for runtime consistency"));
            }
            if (!Double.isFinite(cue.getTimeMs())) {
                msgs.add(new Message(Severity.ERROR, "(audio)",
                    "Audio cue has non-finite trigger time",
                    "Set a finite millisecond timestamp"));
            } else if (Double.isFinite(durationMs) && cue.getTimeMs() > durationMs + TIME_EPSILON_MS) {
                msgs.add(new Message(Severity.WARNING, "(audio)",
                    "Audio cue at " + formatNumber(cue.getTimeMs())
                        + "ms exceeds timeline duration " + formatNumber(durationMs) + "ms",
                    "Fit timeline duration to content or move cue earlier"));
            }
            if (!Double.isFinite(cue.getVolume()) || cue.getVolume() < 0.0 || cue.getVolume() > 1.0) {
                msgs.add(new Message(Severity.ERROR, "(audio)",
                    "Audio cue volume " + cue.getVolume() + " is out of [0,1] range",
                    "Clamp volume to 0..1"));
            }
            if (cue.isFadeIn() && cue.getFadeDurationMs() <= 0.0) {
                msgs.add(new Message(Severity.WARNING, "(audio)",
                    "Audio cue fade-in is enabled but fade duration is 0ms",
                    "Set fade duration > 0ms or disable fade-in"));
            }

            if (Double.isFinite(cue.getTimeMs())) {
                String key = rawChannel.isBlank() ? "(blank)" : normalizedChannel;
                List<Double> seenTimes = cueTimesByChannel.computeIfAbsent(key, k -> new ArrayList<>());
                for (double seen : seenTimes) {
                    if (Math.abs(seen - cue.getTimeMs()) <= TIME_EPSILON_MS) {
                        msgs.add(new Message(Severity.WARNING, "(audio)",
                            "Multiple audio cues share channel '" + (rawChannel.isBlank() ? "(blank)" : rawChannel)
                                + "' at " + formatNumber(cue.getTimeMs()) + "ms",
                            "Offset one cue time or use a different channel"));
                        break;
                    }
                }
                seenTimes.add(cue.getTimeMs());
            }
        }

        for (EditorEventCue evt : project.getEditorEventCues()) {
            String rawType = evt.getType() == null ? "" : evt.getType().trim();
            String type = rawType.toLowerCase(Locale.ROOT);
            Map<String, String> payload = evt.getPayloadView();
            if (rawType.isBlank()) {
                msgs.add(new Message(Severity.ERROR, "(event)",
                    "Event cue at " + evt.getTimeMs() + "ms has empty type",
                    "Set a type like 'expression', 'dialogue_marker', or 'script_call'"));
            }
            if (!Double.isFinite(evt.getTimeMs())) {
                msgs.add(new Message(Severity.ERROR, "(event)",
                    "Event cue has non-finite trigger time",
                    "Set a finite millisecond timestamp"));
            } else if (Double.isFinite(durationMs) && evt.getTimeMs() > durationMs + TIME_EPSILON_MS) {
                msgs.add(new Message(Severity.WARNING, "(event)",
                    "Event cue at " + formatNumber(evt.getTimeMs())
                        + "ms exceeds timeline duration " + formatNumber(durationMs) + "ms",
                    "Fit timeline duration to content or move cue earlier"));
            }
            validateEditorEventCuePayload(msgs, evt, type, payload);
        }

        return Collections.unmodifiableList(msgs);
    }

    private static void diagnoseKeyframeSpacing(
        List<Message> out,
        String entity,
        PropertyType property,
        List<Keyframe> keyframes
    ) {
        if (out == null || property == null || keyframes == null || keyframes.size() < 2) return;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe start = keyframes.get(i);
            Keyframe end = keyframes.get(i + 1);
            double span = end.getTimeMs() - start.getTimeMs();
            double delta = Math.abs(end.getValue() - start.getValue());
            if (delta <= VALUE_EPSILON) continue;

            if (span >= 0.0 && span <= OVERLAP_SEGMENT_MS) {
                out.add(new Message(Severity.WARNING, entity,
                    property.getDisplayName() + " has two different values within "
                        + formatNumber(Math.max(0.0, span)) + "ms near "
                        + formatNumber(end.getTimeMs()) + "ms",
                    "Separate the keyframes or use HOLD/STEP for an intentional cut"));
                continue;
            }

            if (end.getInterpolation() == Easing.Interpolation.TWEEN && span > 0.0 && span < SUB_FRAME_TWEEN_MS) {
                out.add(new Message(Severity.INFO, entity,
                    property.getDisplayName() + " tween from " + formatNumber(start.getTimeMs())
                        + "ms to " + formatNumber(end.getTimeMs())
                        + "ms is shorter than one 60fps frame",
                    "Extend the segment or switch to HOLD/STEP if this should be a pop"));
            }

            if (property == PropertyType.VISIBILITY && end.getInterpolation() == Easing.Interpolation.TWEEN) {
                out.add(new Message(Severity.WARNING, entity,
                    "Visibility uses TWEEN between " + formatNumber(start.getTimeMs())
                        + "ms and " + formatNumber(end.getTimeMs()) + "ms",
                    "Use HOLD/STEP for visibility cuts, or animate Opacity for fades"));
            }
        }
    }

    /**
     * Diagnostics for raw timeline DSL text shown in the code editor.
     * Produces per-line issues for unknown actions/keys and invalid values.
     */
    public static List<Message> diagnoseDsl(String dslCode) {
        if (dslCode == null || dslCode.isBlank()) return Collections.emptyList();

        List<Message> out = new ArrayList<>();
        String[] lines = dslCode.split("\\r?\\n", -1);

        int i = 0;
        while (i < lines.length) {
            String rawLine = lines[i];
            String line = stripComments(rawLine).trim();
            int lineNo = i + 1;

            if (line.isEmpty() || "{".equals(line) || "}".equals(line)) {
                i++;
                continue;
            }
            if (line.equalsIgnoreCase("timeline") || line.equalsIgnoreCase("timeline {")) {
                i++;
                continue;
            }
            if (line.equalsIgnoreCase("parallel") || line.equalsIgnoreCase("parallel {")) {
                i++;
                continue;
            }

            Matcher wait = WAIT_PATTERN.matcher(line);
            if (wait.matches()) {
                validateNonNegativeNumber(out, "timeline", wait.group(1), "wait", lineNo);
                i++;
                continue;
            }

            Matcher withTarget = ACTION_WITH_TARGET_PATTERN.matcher(line);
            Matcher noTarget = ACTION_NO_TARGET_PATTERN.matcher(line);

            if (!withTarget.matches() && !noTarget.matches()) {
                String first = firstToken(line);
                if (!first.isBlank() && Character.isLetter(first.charAt(0))
                    && !KNOWN_ACTIONS.contains(first.toLowerCase(Locale.ROOT))) {
                    String suggestion = suggestToken(first, KNOWN_ACTIONS);
                    out.add(new Message(
                        Severity.WARNING,
                        "timeline",
                        "Unknown timeline action '" + first + "'",
                        suggestion != null ? "Did you mean '" + suggestion + "'?" : "Use a known action like move/scale/fade",
                        lineNo
                    ));
                }
                i++;
                continue;
            }

            String action = withTarget.matches() ? withTarget.group(1) : noTarget.group(1);
            String normalizedAction = action.toLowerCase(Locale.ROOT);

            if (!KNOWN_ACTIONS.contains(normalizedAction)) {
                String suggestion = suggestToken(action, KNOWN_ACTIONS);
                out.add(new Message(
                    Severity.WARNING,
                    "timeline",
                    "Unknown timeline action '" + action + "'",
                    suggestion != null ? "Did you mean '" + suggestion + "'?" : "Use a known action like move/scale/fade",
                    lineNo
                ));
                i++;
                continue;
            }

            if (!line.endsWith("{")) {
                i++;
                continue;
            }

            int depth = 1;
            int j = i + 1;
            Map<String, String> blockProps = new LinkedHashMap<>();
            while (j < lines.length && depth > 0) {
                String blockRaw = lines[j];
                String block = stripComments(blockRaw).trim();
                int blockLineNo = j + 1;

                depth += count(block, '{');
                depth -= count(block, '}');

                if (depth <= 0) {
                    j++;
                    break;
                }

                Matcher prop = PROP_PATTERN.matcher(block);
                if (prop.matches()) {
                    String key = prop.group(1).trim();
                    String value = prop.group(2).trim();
                    blockProps.put(key.toLowerCase(Locale.ROOT), value);
                    validateActionProperty(out, action, key, value, blockLineNo);
                } else if (!block.isBlank() && !"{".equals(block) && !"}".equals(block)) {
                    out.add(new Message(
                        Severity.WARNING,
                        action,
                        "Unrecognized line in " + action + " block",
                        "Use 'key: value' syntax",
                        blockLineNo
                    ));
                }
                j++;
            }

            if (depth > 0) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    "Unterminated block for action '" + action + "'",
                    "Add a closing '}'",
                    lineNo
                ));
                break;
            }

            validateActionBlock(out, action, withTarget.matches() ? withTarget.group(2) : "", blockProps, lineNo);
            i = j;
        }

        return Collections.unmodifiableList(out);
    }

    private static void validateEditorEventCuePayload(
        List<Message> out,
        EditorEventCue cue,
        String type,
        Map<String, String> payload
    ) {
        if (cue == null) return;
        Map<String, String> safePayload = payload == null ? Map.of() : payload;
        String target = safePayload.getOrDefault("target", "").trim();
        if (Set.of("expression", "show", "hide", "replace").contains(type) && target.isBlank()) {
            out.add(new Message(
                Severity.WARNING,
                "(event)",
                "Event cue '" + type + "' at " + formatNumber(cue.getTimeMs()) + "ms has no target",
                "Set the Target field so the cue affects a character or entity"
            ));
        }
        if ("expression".equals(type)
            && safePayload.getOrDefault("value", "").isBlank()
            && safePayload.getOrDefault("expression", "").isBlank()
            && safePayload.getOrDefault("path", "").isBlank()
            && safePayload.getOrDefault("layers", "").isBlank()) {
            out.add(new Message(
                Severity.WARNING,
                target.isBlank() ? "(event)" : target,
                "Expression cue at " + formatNumber(cue.getTimeMs()) + "ms has no expression or image path",
                "Set Value/Expression or Path before playback"
            ));
        }
        if ("show".equals(type)
            && safePayload.getOrDefault("value", "").isBlank()
            && safePayload.getOrDefault("expression", "").isBlank()
            && safePayload.getOrDefault("path", "").isBlank()
            && safePayload.getOrDefault("layers", "").isBlank()) {
            out.add(new Message(
                Severity.INFO,
                target.isBlank() ? "(event)" : target,
                "Show cue at " + formatNumber(cue.getTimeMs()) + "ms only toggles visibility",
                "Add Expression or Path if the cue should also select an image"
            ));
        }
        if ("scene".equals(type)
            && safePayload.getOrDefault("target", "").isBlank()
            && safePayload.getOrDefault("id", "").isBlank()
            && safePayload.getOrDefault("value", "").isBlank()
            && safePayload.getOrDefault("path", "").isBlank()) {
            out.add(new Message(
                Severity.WARNING,
                "(event)",
                "Scene cue at " + formatNumber(cue.getTimeMs()) + "ms has no scene id or path",
                "Set Scene/Value or Path so the cue can resolve a background"
            ));
        }
        if ("script_call".equals(type)
            && safePayload.getOrDefault("handler", "").isBlank()
            && safePayload.getOrDefault("call", "").isBlank()
            && safePayload.getOrDefault("name", "").isBlank()
            && safePayload.getOrDefault("target", "").isBlank()) {
            out.add(new Message(
                Severity.WARNING,
                "(event)",
                "script_call cue at " + formatNumber(cue.getTimeMs()) + "ms has no handler",
                "Set handler, call, name, or target to route the cue"
            ));
        }
        for (Map.Entry<String, String> entry : safePayload.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.isBlank()) {
                out.add(new Message(
                    Severity.ERROR,
                    "(event)",
                    "Event cue at " + formatNumber(cue.getTimeMs()) + "ms contains an empty payload key",
                    "Remove the blank payload row or give it a stable key"
                ));
            }
        }
    }

    private static void validateActionBlock(
        List<Message> out,
        String action,
        String target,
        Map<String, String> props,
        int lineNo
    ) {
        String actionNorm = action == null ? "" : action.toLowerCase(Locale.ROOT);
        String safeTarget = target == null ? "" : target.trim();
        Map<String, String> safeProps = props == null ? Map.of() : props;

        if (requiresQuotedTarget(actionNorm) && safeTarget.isBlank()) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "Action '" + action + "' requires a quoted target",
                "Use syntax like " + action + " \"hero\" { ... }",
                lineNo
            ));
        }

        if ("property".equals(actionNorm)) {
            String key = valueFor(safeProps, "key");
            String value = valueFor(safeProps, "value");
            if (stripQuotes(key).isBlank()) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    "property action is missing key",
                    "Set key to a custom channel such as effect.blur or dof.strength",
                    lineNo
                ));
            }
            if (value.isBlank()) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    "property action is missing numeric value",
                    "Set value to the target number for this custom channel",
                    lineNo
                ));
            } else if (parseNumber(value) == null) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    "property value must be numeric",
                    "Use a number such as 0, 1, or 0.35",
                    lineNo
                ));
            }
        }

        if ("visible".equals(actionNorm)) {
            String raw = valueFor(safeProps, "value");
            if (raw.isBlank()) raw = valueFor(safeProps, "visible");
            if (raw.isBlank()) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    "visible action is missing value",
                    "Set value to true/false or 1/0",
                    lineNo
                ));
            } else if (!isBooleanLike(raw) && parseNumber(raw) == null) {
                out.add(new Message(
                    Severity.WARNING,
                    action,
                    "visible value '" + stripQuotes(raw) + "' is not a standard boolean or number",
                    "Use true/false or 1/0",
                    lineNo
                ));
            }
        }

        if ("brightness".equals(actionNorm) || "exposure".equals(actionNorm)) {
            String raw = valueFor(safeProps, "value");
            if (raw.isBlank()) raw = valueFor(safeProps, "brightness");
            if (raw.isBlank()) raw = valueFor(safeProps, "exposure");
            if (raw.isBlank()) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    action + " action is missing value",
                    "Set value to 1 for neutral, below 1 for darker, or above 1 for brighter",
                    lineNo
                ));
            } else if (parseNumber(raw) == null) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    action + " value must be numeric",
                    "Use a number such as 0.6, 1, or 1.25",
                    lineNo
                ));
            }
        }

        if ("playaudio".equals(actionNorm)
            && safeTarget.isBlank()
            && stripQuotes(valueFor(safeProps, "path")).isBlank()
            && stripQuotes(valueFor(safeProps, "id")).isBlank()) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "playAudio is missing an audio path",
                "Use playAudio \"assets/audio/sfx/click.wav\" { ... }",
                lineNo
            ));
        }

        if ("event".equals(actionNorm) && safeTarget.isBlank()) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "event action requires a quoted event type",
                "Use event \"script_call\" { ... }",
                lineNo
            ));
        }
    }

    /**
     * Validate an easing name string and return the closest known match, or null.
     */
    public static String suggestEasing(String input) {
        if (input == null || input.isBlank()) return "linear";
        EasingSpec parsed = EasingSpec.tryParse(input);
        if (parsed != null) {
            return parsed.toDslString();
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        int paren = normalized.indexOf('(');
        String candidate = paren >= 0 ? normalized.substring(0, paren) : normalized;
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String known : KNOWN_EASINGS) {
            int dist = editDistance(candidate, known);
            if (dist < bestDist) {
                bestDist = dist;
                best = known;
            }
        }
        return bestDist <= 3 ? best : null;
    }

    private static void validateActionProperty(List<Message> out, String action, String key, String value, int lineNo) {
        String actionNorm = action.toLowerCase(Locale.ROOT);
        String keyNorm = key.toLowerCase(Locale.ROOT);

        Set<String> known = ACTION_KEYS.get(actionNorm);
        if (known != null && !known.contains(keyNorm)) {
            String suggestion = suggestToken(keyNorm, known);
            String knownKeys = String.join(", ", known);
            out.add(new Message(
                Severity.WARNING,
                action,
                "Unknown key '" + key + "' for action '" + action + "'",
                suggestion != null
                    ? "Did you mean '" + suggestion + "'?"
                    : "Known keys: " + knownKeys,
                lineNo
            ));
            return;
        }

        if ("easing".equals(keyNorm)) {
            validateEasing(out, action, value, lineNo);
            return;
        }

        if ("interp".equals(keyNorm)) {
            validateInterpolation(out, action, value, lineNo);
            return;
        }

        if (("dur".equals(keyNorm) || "duration".equals(keyNorm))
            && !isNonNegativeNumber(value)) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "Duration must be a non-negative number",
                "Use values like 0, 250, 800",
                lineNo
            ));
            return;
        }

        if ("fade".equals(actionNorm) && "alpha".equals(keyNorm)) {
            Double n = parseNumber(value);
            if (n == null) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    "Alpha must be numeric",
                    "Use a value between 0 and 1",
                    lineNo
                ));
            } else if (n < 0.0 || n > 1.0) {
                out.add(new Message(
                    Severity.WARNING,
                    action,
                    "Alpha " + n + " is out of [0,1] range",
                    "Clamp to " + clamp01(n),
                    lineNo
                ));
            }
            return;
        }

        if ("pivot".equals(actionNorm) && ("ox".equals(keyNorm) || "oy".equals(keyNorm))) {
            Double n = parseNumber(value);
            if (n == null) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    key + " must be numeric",
                    "Use a value between 0 and 1",
                    lineNo
                ));
            } else if (n < 0.0 || n > 1.0) {
                out.add(new Message(
                    Severity.WARNING,
                    action,
                    key + " " + n + " is out of [0,1] range",
                    "Clamp to " + clamp01(n),
                    lineNo
                ));
            }
            return;
        }

        if ("camerazoom".equals(actionNorm) && "zoom".equals(keyNorm)) {
            Double n = parseNumber(value);
            if (n == null) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    "zoom must be numeric",
                    "Use a value like 1.0",
                    lineNo
                ));
            } else if (n <= 0.0) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    "zoom must be > 0",
                    "Set to 0.01 minimum",
                    lineNo
                ));
            }
            return;
        }

        if ("mirror".equals(actionNorm)
            && ("mirrorx".equals(keyNorm)
                || "mirror_x".equals(keyNorm)
                || "flipx".equals(keyNorm)
                || "flip_x".equals(keyNorm)
                || "x".equals(keyNorm)
                || "value".equals(keyNorm))) {
            if (!isBooleanLike(value) && parseNumber(value) == null) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    key + " must be numeric or boolean",
                    "Use 0/1 for mirror amount, or true/false for a full flip",
                    lineNo
                ));
            }
            return;
        }

        if (("brightness".equals(actionNorm) || "exposure".equals(actionNorm))
            && ("value".equals(keyNorm) || "brightness".equals(keyNorm) || "exposure".equals(keyNorm))) {
            Double n = parseNumber(value);
            if (n == null) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    key + " must be numeric",
                    "Use a number such as 0.6, 1, or 1.25",
                    lineNo
                ));
            } else if (n < 0.0) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    key + " must be >= 0",
                    "Use 0 for black or 1 for neutral brightness",
                    lineNo
                ));
            }
            return;
        }

        if ("playaudio".equals(actionNorm) && "channel".equals(keyNorm)) {
            String normalized = stripQuotes(value).toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    "channel cannot be empty",
                    "Use music, sound, or voice",
                    lineNo
                ));
            } else if (!KNOWN_AUDIO_CHANNELS.contains(normalized)) {
                out.add(new Message(
                    Severity.WARNING,
                    action,
                    "Unknown audio channel '" + stripQuotes(value) + "'",
                    "Prefer music/sound/voice for runtime consistency",
                    lineNo
                ));
            }
            return;
        }

        if ("playaudio".equals(actionNorm) && "volume".equals(keyNorm)) {
            Double n = parseNumber(value);
            if (n == null) {
                out.add(new Message(
                    Severity.ERROR,
                    action,
                    "volume must be numeric",
                    "Use a value between 0 and 1",
                    lineNo
                ));
            } else if (n < 0.0 || n > 1.0) {
                out.add(new Message(
                    Severity.WARNING,
                    action,
                    "volume " + n + " is out of [0,1] range",
                    "Clamp to " + clamp01(n),
                    lineNo
                ));
            }
            return;
        }

        if ("playaudio".equals(actionNorm)
            && ("fadeinms".equals(keyNorm) || "fadein_ms".equals(keyNorm) || "fadein".equals(keyNorm) || "fade_in".equals(keyNorm))) {
            validateNonNegativeNumber(out, action, value, key, lineNo);
            return;
        }

        if ("playaudio".equals(actionNorm) && "loop".equals(keyNorm)) {
            String normalized = stripQuotes(value).toLowerCase(Locale.ROOT);
            if (!("true".equals(normalized) || "false".equals(normalized)
                || "on".equals(normalized) || "off".equals(normalized)
                || "1".equals(normalized) || "0".equals(normalized)
                || "yes".equals(normalized) || "no".equals(normalized))) {
                out.add(new Message(
                    Severity.WARNING,
                    action,
                    "loop value '" + stripQuotes(value) + "' is not a standard boolean token",
                    "Use true/false, on/off, or 1/0",
                    lineNo
                ));
            }
        }
    }

    private static void validateEasing(List<Message> out, String action, String rawValue, int lineNo) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "easing cannot be empty",
                "Use 'linear' or an easing like 'ease_in_out_sine'",
                lineNo
            ));
            return;
        }

        if (EasingSpec.tryParse(value) != null) {
            return;
        }

        String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.startsWith("cubic_bezier(")) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "cubic_bezier requires 4 numeric values",
                "Use: cubic_bezier(0.25, 0.1, 0.25, 1.0)",
                lineNo
            ));
            return;
        }
        if ("cubic_bezier".equals(normalized)) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "cubic_bezier requires 4 numeric values",
                "Use: cubic_bezier(0.25, 0.1, 0.25, 1.0)",
                lineNo
            ));
            return;
        }
        if (normalized.startsWith("curve(")) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "curve requires an even number of numeric x/y values",
                "Use: curve(0.25, 0.1, 0.5, 0.8, 0.75, 1.0)",
                lineNo
            ));
            return;
        }
        if ("curve".equals(normalized)) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "curve requires x/y point pairs",
                "Use: curve(0.25, 0.1, 0.5, 0.8, 0.75, 1.0)",
                lineNo
            ));
            return;
        }
        if (normalized.startsWith("spring(")) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "spring accepts up to 4 numeric values",
                "Use: spring(180, 20, 1, 0)",
                lineNo
            ));
            return;
        }
        if (normalized.startsWith("damped_spring(")) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "damped_spring accepts up to 4 numeric values",
                "Use: damped_spring(2.2, 0.38, 1.0, 0)",
                lineNo
            ));
            return;
        }

        if (KNOWN_EASINGS.contains(normalized)) return;

        String suggestion = suggestEasing(value);
        out.add(new Message(
            Severity.WARNING,
            action,
            "Unknown easing '" + value + "'",
            suggestion != null ? "Did you mean '" + suggestion + "'?" : "Use known easing names (e.g. linear, ease_in_out_sine)",
            lineNo
        ));
    }

    private static void validateInterpolation(List<Message> out, String action, String rawValue, int lineNo) {
        String value = rawValue == null ? "" : stripQuotes(rawValue).trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            out.add(new Message(
                Severity.ERROR,
                action,
                "interp cannot be empty",
                "Use tween, hold, or step",
                lineNo
            ));
            return;
        }
        String normalized = value.replace('-', '_');
        if ("step_start".equals(normalized) || "instant".equals(normalized) || "jump".equals(normalized)) {
            return;
        }
        if ("step_end".equals(normalized) || "constant".equals(normalized)) {
            return;
        }
        if (KNOWN_INTERPOLATIONS.contains(normalized)) return;

        String suggestion = suggestToken(normalized, KNOWN_INTERPOLATIONS);
        out.add(new Message(
            Severity.WARNING,
            action,
            "Unknown interpolation '" + value + "'",
            suggestion != null ? "Did you mean '" + suggestion + "'?" : "Use tween, hold, or step",
            lineNo
        ));
    }

    private static void validateNonNegativeNumber(List<Message> out, String scope, String raw, String field, int lineNo) {
        Double n = parseNumber(raw);
        if (n == null) {
            out.add(new Message(
                Severity.ERROR,
                scope,
                field + " must be numeric",
                "Use non-negative milliseconds",
                lineNo
            ));
            return;
        }
        if (n < 0.0) {
            out.add(new Message(
                Severity.ERROR,
                scope,
                field + " must be >= 0",
                "Clamp to 0 or higher",
                lineNo
            ));
        }
    }

    private static String stripComments(String line) {
        if (line == null) return "";
        int hash = line.indexOf('#');
        int slash = line.indexOf("//");
        int cut = -1;
        if (hash >= 0) cut = hash;
        if (slash >= 0) cut = cut < 0 ? slash : Math.min(cut, slash);
        return cut >= 0 ? line.substring(0, cut) : line;
    }

    private static String stripQuotes(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() < 2) return trimmed;
        char first = trimmed.charAt(0);
        char last = trimmed.charAt(trimmed.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static boolean requiresQuotedTarget(String actionNorm) {
        return Set.of(
            "move", "depth", "pivot", "rotate", "scale", "fade", "brightness", "exposure", "visible",
            "expression", "show", "hide", "replace", "playaudio"
        ).contains(actionNorm);
    }

    private static String valueFor(Map<String, String> props, String key) {
        if (props == null || key == null) return "";
        String value = props.get(key.toLowerCase(Locale.ROOT));
        return value == null ? "" : value.trim();
    }

    private static boolean isBooleanLike(String value) {
        String normalized = stripQuotes(value).toLowerCase(Locale.ROOT);
        return "true".equals(normalized)
            || "false".equals(normalized)
            || "on".equals(normalized)
            || "off".equals(normalized)
            || "yes".equals(normalized)
            || "no".equals(normalized)
            || "1".equals(normalized)
            || "0".equals(normalized);
    }

    private static String firstToken(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.isEmpty()) return "";
        int idx = t.indexOf(' ');
        return idx >= 0 ? t.substring(0, idx) : t;
    }

    private static String suggestToken(String input, Set<String> options) {
        if (input == null || input.isBlank() || options == null || options.isEmpty()) return null;
        String candidate = input.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String option : options) {
            int dist = editDistance(candidate, option);
            if (dist < bestDist) {
                bestDist = dist;
                best = option;
            }
        }
        return bestDist <= 3 ? best : null;
    }

    private static Set<String> lowerSet(Set<String> input) {
        Set<String> out = new LinkedHashSet<>();
        for (String value : input) {
            if (value != null) out.add(value.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static int count(String text, char ch) {
        if (text == null || text.isEmpty()) return 0;
        int c = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ch) c++;
        }
        return c;
    }

    private static boolean isNonNegativeNumber(String text) {
        Double n = parseNumber(text);
        return n != null && n >= 0.0;
    }

    private static Double parseNumber(String text) {
        if (text == null) return null;
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            return null;
        }
    }

    private static String clamp01(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return formatNumber(clamped);
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) return Long.toString((long) Math.rint(value));
        String s = String.format(Locale.ROOT, "%.4f", value);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static int editDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}
