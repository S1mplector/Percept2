package com.jvn.core.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runtime-facing diagnostics for {@link TimelineData}.
 *
 * <p>Puppeteer and other tooling can use this class to validate the exact data
 * that the engine will play, after editor-side transforms such as group baking
 * and layer-order expansion have already happened.</p>
 */
public final class TimelineDataDiagnostics {
    private static final double TIME_EPSILON_MS = 0.001;
    private static final String CAMERA_TRACK = "__camera__";

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public record Message(
        Severity severity,
        String target,
        String description,
        String quickFix
    ) {
        public Message {
            severity = severity == null ? Severity.INFO : severity;
            target = target == null || target.isBlank() ? "(timeline)" : target.trim();
            description = description == null ? "" : description.trim();
            quickFix = quickFix == null || quickFix.isBlank() ? null : quickFix.trim();
        }
    }

    private TimelineDataDiagnostics() {
    }

    public static List<Message> diagnose(TimelineData data) {
        List<Message> messages = new ArrayList<>();
        if (data == null) {
            messages.add(new Message(
                Severity.ERROR,
                "(timeline)",
                "Timeline data is null",
                "Create or reload the timeline before runtime registration"
            ));
            return Collections.unmodifiableList(messages);
        }

        double durationMs = data.getDurationMs();
        boolean finiteDuration = Double.isFinite(durationMs);
        if (data.getName() == null || data.getName().isBlank()) {
            messages.add(new Message(
                Severity.WARNING,
                "(timeline)",
                "Timeline name is empty",
                "Set a stable timeline name before registering it with runtime playback"
            ));
        }
        if (!finiteDuration || durationMs < 0.0) {
            messages.add(new Message(
                Severity.ERROR,
                "(timeline)",
                "Timeline duration is not a finite non-negative number",
                "Set the duration to a finite value in milliseconds"
            ));
        }
        if (data.getTracks().isEmpty() && data.getAudioCues().isEmpty() && data.getEventCues().isEmpty()) {
            messages.add(new Message(
                Severity.WARNING,
                "(timeline)",
                "Timeline has no tracks, audio cues, or event cues",
                "Add runtime content or skip registration for this timeline"
            ));
        }

        diagnoseTracks(data, finiteDuration ? durationMs : Double.NaN, messages);
        diagnoseAudioCues(data, finiteDuration ? durationMs : Double.NaN, messages);
        diagnoseEventCues(data, finiteDuration ? durationMs : Double.NaN, messages);

        return Collections.unmodifiableList(messages);
    }

    private static void diagnoseTracks(TimelineData data, double durationMs, List<Message> messages) {
        Set<String> seenTracks = new LinkedHashSet<>();
        for (TimelineData.Track track : data.getTracks()) {
            if (track == null) {
                messages.add(new Message(
                    Severity.ERROR,
                    "(track)",
                    "Timeline contains a null track",
                    "Remove the invalid track before registering the timeline"
                ));
                continue;
            }
            String target = track.getEntityName();
            if (target == null || target.isBlank()) {
                messages.add(new Message(
                    Severity.ERROR,
                    "(track)",
                    "Track has no entity target",
                    "Name the target entity or use __camera__ for camera animation"
                ));
            } else if (!seenTracks.add(target)) {
                messages.add(new Message(
                    Severity.WARNING,
                    target,
                    "Multiple runtime tracks target '" + target + "'",
                    "Merge duplicate tracks so playback order is unambiguous"
                ));
            }

            boolean cameraTrack = CAMERA_TRACK.equals(target);
            for (Map.Entry<TimelineData.Property, List<TimelineData.Keyframe>> entry : track.getAllKeyframes().entrySet()) {
                TimelineData.Property property = entry.getKey();
                if (property == null) {
                    messages.add(new Message(
                        Severity.ERROR,
                        target,
                        "Track contains keyframes for a null property",
                        "Remove the invalid property channel"
                    ));
                    continue;
                }
                if (property.name().startsWith("CAMERA_") && !cameraTrack) {
                    messages.add(new Message(
                        Severity.WARNING,
                        target,
                        "Camera property " + property.name() + " is stored on non-camera track '" + target + "'",
                        "Move camera keyframes to the __camera__ runtime track"
                    ));
                } else if (!property.name().startsWith("CAMERA_") && cameraTrack) {
                    messages.add(new Message(
                        Severity.WARNING,
                        target,
                        "Entity property " + property.name() + " is stored on the camera track and will not affect an entity",
                        "Move entity keyframes to their target entity track"
                    ));
                }
                diagnoseKeyframes(target, property.name(), property, entry.getValue(), durationMs, messages);
            }
            for (Map.Entry<String, List<TimelineData.Keyframe>> entry : track.getAllCustomKeyframes().entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) {
                    messages.add(new Message(
                        Severity.ERROR,
                        target,
                        "Track contains custom keyframes with an empty property key",
                        "Set a stable runtime custom property key"
                    ));
                    continue;
                }
                diagnoseKeyframes(target, key, null, entry.getValue(), durationMs, messages);
            }
        }
    }

    private static void diagnoseKeyframes(
        String target,
        String channel,
        TimelineData.Property property,
        List<TimelineData.Keyframe> keyframes,
        double durationMs,
        List<Message> messages
    ) {
        if (keyframes == null || keyframes.isEmpty()) return;
        Double previousTime = null;
        Set<Long> seenTimes = new LinkedHashSet<>();
        for (TimelineData.Keyframe keyframe : keyframes) {
            if (keyframe == null) {
                messages.add(new Message(
                    Severity.ERROR,
                    target,
                    channel + " contains a null keyframe",
                    "Remove the invalid keyframe"
                ));
                continue;
            }
            double timeMs = keyframe.getTimeMs();
            double value = keyframe.getValue();
            if (!Double.isFinite(timeMs)) {
                messages.add(new Message(
                    Severity.ERROR,
                    target,
                    channel + " contains a keyframe with non-finite time",
                    "Set a numeric millisecond timestamp"
                ));
            } else {
                if (timeMs < 0.0) {
                    messages.add(new Message(
                        Severity.WARNING,
                        target,
                        channel + " keyframe at " + format(timeMs) + "ms is before timeline start",
                        "Move the keyframe to 0ms or later"
                    ));
                }
                if (Double.isFinite(durationMs) && timeMs > durationMs + TIME_EPSILON_MS) {
                    messages.add(new Message(
                        Severity.WARNING,
                        target,
                        channel + " keyframe at " + format(timeMs) + "ms exceeds duration " + format(durationMs) + "ms",
                        "Fit the timeline duration to content or move the keyframe earlier"
                    ));
                }
                if (previousTime != null && timeMs + TIME_EPSILON_MS < previousTime) {
                    messages.add(new Message(
                        Severity.WARNING,
                        target,
                        channel + " keyframes are not sorted by time",
                        "Sort the runtime keyframes before playback"
                    ));
                }
                long timeBucket = Math.round(timeMs / TIME_EPSILON_MS);
                if (!seenTimes.add(timeBucket)) {
                    messages.add(new Message(
                        Severity.WARNING,
                        target,
                        channel + " has multiple keyframes at " + format(timeMs) + "ms",
                        "Keep one keyframe per property at each timestamp"
                    ));
                }
                previousTime = timeMs;
            }
            if (!Double.isFinite(value)) {
                messages.add(new Message(
                    Severity.ERROR,
                    target,
                    channel + " keyframe value is not finite",
                    "Replace NaN or Infinity with a numeric value"
                ));
            }
            diagnosePropertyValue(target, property, timeMs, value, messages);
        }
    }

    private static void diagnosePropertyValue(
        String target,
        TimelineData.Property property,
        double timeMs,
        double value,
        List<Message> messages
    ) {
        if (property == null || !Double.isFinite(value)) return;
        String at = Double.isFinite(timeMs) ? " at " + format(timeMs) + "ms" : "";
        switch (property) {
            case ALPHA -> {
                if (value < 0.0 || value > 1.0) {
                    messages.add(new Message(
                        Severity.WARNING,
                        target,
                        "Alpha value " + format(value) + at + " is outside [0,1]",
                        "Clamp alpha to the 0..1 range"
                    ));
                }
            }
            case PIVOT_X, PIVOT_Y -> {
                if (value < 0.0 || value > 1.0) {
                    messages.add(new Message(
                        Severity.WARNING,
                        target,
                        property.name() + " value " + format(value) + at + " is outside [0,1]",
                        "Clamp pivot values to the 0..1 range"
                    ));
                }
            }
            case CAMERA_ZOOM -> {
                if (value <= 0.0) {
                    messages.add(new Message(
                        Severity.WARNING,
                        target,
                        "Camera zoom " + format(value) + at + " is non-positive",
                        "Use a positive camera zoom value"
                    ));
                }
            }
            default -> {
            }
        }
    }

    private static void diagnoseAudioCues(TimelineData data, double durationMs, List<Message> messages) {
        for (TimelineData.AudioCue cue : data.getAudioCues()) {
            if (cue == null) {
                messages.add(new Message(Severity.ERROR, "(audio)", "Timeline contains a null audio cue", "Remove the invalid cue"));
                continue;
            }
            if (cue.getTrackPath() == null || cue.getTrackPath().isBlank()) {
                messages.add(new Message(Severity.ERROR, "(audio)", "Audio cue has an empty track path", "Choose an audio asset path"));
            }
            if (cue.getChannel() == null || cue.getChannel().isBlank()) {
                messages.add(new Message(Severity.ERROR, "(audio)", "Audio cue has an empty channel", "Use music, sound, or voice"));
            }
            diagnoseCueTime("(audio)", "Audio cue", cue.getTimeMs(), durationMs, messages);
        }
    }

    private static void diagnoseEventCues(TimelineData data, double durationMs, List<Message> messages) {
        for (TimelineData.EventCue cue : data.getEventCues()) {
            if (cue == null) {
                messages.add(new Message(Severity.ERROR, "(event)", "Timeline contains a null event cue", "Remove the invalid cue"));
                continue;
            }
            if (cue.getType() == null || cue.getType().isBlank()) {
                messages.add(new Message(Severity.ERROR, "(event)", "Event cue has an empty type", "Set a runtime event type"));
            }
            diagnoseCueTime("(event)", "Event cue", cue.getTimeMs(), durationMs, messages);
        }
    }

    private static void diagnoseCueTime(
        String target,
        String label,
        double timeMs,
        double durationMs,
        List<Message> messages
    ) {
        if (!Double.isFinite(timeMs)) {
            messages.add(new Message(Severity.ERROR, target, label + " has a non-finite trigger time", "Set a finite millisecond timestamp"));
            return;
        }
        if (timeMs < 0.0) {
            messages.add(new Message(Severity.WARNING, target, label + " at " + format(timeMs) + "ms is before timeline start", "Move the cue to 0ms or later"));
        }
        if (Double.isFinite(durationMs) && timeMs > durationMs + TIME_EPSILON_MS) {
            messages.add(new Message(
                Severity.WARNING,
                target,
                label + " at " + format(timeMs) + "ms exceeds duration " + format(durationMs) + "ms",
                "Fit the timeline duration to content or move the cue earlier"
            ));
        }
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) return String.valueOf(value);
        String raw = String.format(Locale.ROOT, "%.3f", value);
        while (raw.contains(".") && raw.endsWith("0")) raw = raw.substring(0, raw.length() - 1);
        if (raw.endsWith(".")) raw = raw.substring(0, raw.length() - 1);
        return raw;
    }
}
