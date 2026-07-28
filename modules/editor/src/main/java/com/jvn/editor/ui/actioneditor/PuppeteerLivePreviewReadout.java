package com.jvn.editor.ui.actioneditor;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/** Builds the compact playhead-aware readout shown over Puppeteer's preview. */
final class PuppeteerLivePreviewReadout {
    private static final double TIME_EPSILON_MS = 0.001;

    record Snapshot(
        String mode,
        String clock,
        String target,
        String state,
        String upcoming,
        double progress
    ) {}

    private record Upcoming(double timeMs, String label) {}

    private PuppeteerLivePreviewReadout() {}

    static Snapshot build(
        @Nullable AnimationProject project,
        @Nullable String selectedTarget,
        boolean selectedGroup,
        boolean runtimeCamera,
        @Nullable PropertyType selectedProperty,
        boolean runtimeParity
    ) {
        if (project == null) {
            return new Snapshot("PREVIEW", "00:00.000 / 00:00.000", "Full scene",
                "No timeline loaded", "Add a keyframe to begin", 0.0);
        }

        double timeMs = sanitizeTime(project.getPlayheadMs());
        double durationMs = Math.max(0.0, sanitizeTime(project.getTotalDurationMs()));
        String mode = runtimeParity
            ? "RUNTIME PARITY"
            : (project.isPlaying() ? "LIVE PLAYBACK" : "LIVE PREVIEW");
        String clock = formatTime(timeMs) + " / " + formatTime(durationMs);
        String target = describeTarget(project, selectedTarget, selectedGroup, runtimeCamera, selectedProperty);
        String state = describeState(project, selectedTarget, selectedGroup, runtimeCamera, timeMs);
        Upcoming upcoming = findUpcoming(project, selectedTarget, selectedGroup, runtimeCamera, timeMs);
        String upcomingText = upcoming == null
            ? (!hasAuthoredMotion(project) ? "Add a keyframe to begin" : "End of authored motion")
            : "Next  •  " + upcoming.label() + "  " + formatDelta(upcoming.timeMs() - timeMs);
        double progress = durationMs <= TIME_EPSILON_MS
            ? 0.0
            : Math.max(0.0, Math.min(1.0, timeMs / durationMs));

        return new Snapshot(mode, clock, target, state, upcomingText, progress);
    }

    private static boolean hasAuthoredMotion(AnimationProject project) {
        if (!project.getEditorEventCues().isEmpty()) return true;
        for (EntityTrack track : project.getTracks()) {
            if (animatedChannelCount(track) > 0) return true;
        }
        return false;
    }

    private static String describeTarget(
        AnimationProject project,
        @Nullable String selectedTarget,
        boolean selectedGroup,
        boolean runtimeCamera,
        @Nullable PropertyType selectedProperty
    ) {
        if (runtimeCamera) {
            return selectedProperty == null
                ? "Runtime Camera"
                : "Runtime Camera  •  " + selectedProperty.getDisplayName();
        }
        if (selectedTarget != null && !selectedTarget.isBlank()) {
            String kind = selectedGroup ? "Group" : "Entity";
            return selectedProperty == null
                ? kind + "  •  " + selectedTarget.trim()
                : kind + "  •  " + selectedTarget.trim() + "  •  " + selectedProperty.getDisplayName();
        }
        int count = project.getTrackCount();
        return count == 1 ? "Full scene  •  1 track" : "Full scene  •  " + count + " tracks";
    }

    private static String describeState(
        AnimationProject project,
        @Nullable String selectedTarget,
        boolean selectedGroup,
        boolean runtimeCamera,
        double timeMs
    ) {
        if (!selectedGroup && !runtimeCamera && selectedTarget != null && !selectedTarget.isBlank()) {
            EditorEventCue activeExpression = latestExpressionCue(project, selectedTarget, timeMs);
            if (activeExpression != null) {
                String expression = firstNonBlank(
                    activeExpression.getPayloadValue("value"),
                    activeExpression.getPayloadValue("expression"),
                    "unnamed");
                int layerCount = countLayerPaths(activeExpression);
                return layerCount > 0
                    ? "Expression  •  " + expression + "  •  " + layerCount
                        + (layerCount == 1 ? " layer" : " layers")
                    : "Expression  •  " + expression;
            }
        }

        EntityTrack track = resolveTrack(project, selectedTarget, selectedGroup);
        if (track != null) {
            int channelCount = animatedChannelCount(track);
            int keyedNow = keyframesAt(track, timeMs);
            if (keyedNow > 0) {
                return keyedNow + (keyedNow == 1 ? " key" : " keys") + " at playhead  •  "
                    + channelCount + (channelCount == 1 ? " channel" : " channels");
            }
            return channelCount == 0
                ? "No authored channels on selection"
                : channelCount + (channelCount == 1 ? " animated channel" : " animated channels");
        }

        int eventCount = project.getEditorEventCues().size();
        return eventCount == 0
            ? "Scene preview  •  no event cues"
            : "Scene preview  •  " + eventCount + (eventCount == 1 ? " event cue" : " event cues");
    }

    private static @Nullable Upcoming findUpcoming(
        AnimationProject project,
        @Nullable String selectedTarget,
        boolean selectedGroup,
        boolean runtimeCamera,
        double timeMs
    ) {
        Upcoming best = null;
        EntityTrack selectedTrack = resolveTrack(project, selectedTarget, selectedGroup);
        if (runtimeCamera) {
            for (EntityTrack track : project.getTracks()) {
                if (TimelinePanel.isRuntimeCameraTarget(track.getEntityName())) {
                    best = earlier(best, upcomingForTrack(track, timeMs));
                }
            }
        } else if (selectedTrack != null) {
            best = upcomingForTrack(selectedTrack, timeMs);
        } else {
            for (EntityTrack track : project.getTracks()) {
                best = earlier(best, upcomingForTrack(track, timeMs));
            }
        }

        for (EditorEventCue cue : project.getEditorEventCues()) {
            if (cue == null || cue.getTimeMs() <= timeMs + TIME_EPSILON_MS) continue;
            String target = cue.getPayloadValue("target");
            if (selectedTarget != null && !selectedTarget.isBlank()
                && !target.isBlank()
                && !TimelinePanel.expressionTargetMatchesEntity(target, selectedTarget)) {
                continue;
            }
            best = earlier(best, new Upcoming(cue.getTimeMs(), eventLabel(cue)));
        }
        return best;
    }

    private static @Nullable Upcoming upcomingForTrack(@Nullable EntityTrack track, double timeMs) {
        if (track == null) return null;
        Upcoming best = null;
        for (PropertyType property : PropertyType.values()) {
            for (Keyframe keyframe : track.getKeyframes(property)) {
                if (keyframe.getTimeMs() > timeMs + TIME_EPSILON_MS) {
                    best = earlier(best, new Upcoming(keyframe.getTimeMs(), property.getDisplayName()));
                    break;
                }
            }
        }
        for (String key : track.getAnimatedCustomProperties()) {
            for (Keyframe keyframe : track.getCustomKeyframes(key)) {
                if (keyframe.getTimeMs() > timeMs + TIME_EPSILON_MS) {
                    best = earlier(best, new Upcoming(keyframe.getTimeMs(), humanizeKey(key)));
                    break;
                }
            }
        }
        return best;
    }

    private static @Nullable Upcoming earlier(
        @Nullable Upcoming current,
        @Nullable Upcoming candidate
    ) {
        if (candidate == null) return current;
        return current == null || candidate.timeMs() < current.timeMs() ? candidate : current;
    }

    private static @Nullable EntityTrack resolveTrack(
        AnimationProject project,
        @Nullable String selectedTarget,
        boolean selectedGroup
    ) {
        if (project == null || selectedTarget == null || selectedTarget.isBlank()) return null;
        if (!selectedGroup) return project.getTrack(selectedTarget);
        EntityGroup group = project.getGroup(selectedTarget);
        return group == null ? null : group.getGroupTrack();
    }

    private static @Nullable EditorEventCue latestExpressionCue(
        AnimationProject project,
        String target,
        double timeMs
    ) {
        EditorEventCue latest = null;
        for (EditorEventCue cue : project.getEditorEventCues()) {
            if (cue == null || cue.getTimeMs() > timeMs + TIME_EPSILON_MS) break;
            if (!"expression".equalsIgnoreCase(cue.getType())) continue;
            if (TimelinePanel.expressionTargetMatchesEntity(cue.getPayloadValue("target"), target)) {
                latest = cue;
            }
        }
        return latest;
    }

    private static int animatedChannelCount(EntityTrack track) {
        int count = 0;
        for (PropertyType property : PropertyType.values()) {
            if (track.hasKeyframes(property)) count++;
        }
        for (String ignored : track.getAnimatedCustomProperties()) count++;
        return count;
    }

    private static int keyframesAt(EntityTrack track, double timeMs) {
        int count = 0;
        for (PropertyType property : PropertyType.values()) {
            if (track.findKeyframeAt(property, timeMs) != null) count++;
        }
        for (String key : track.getAnimatedCustomProperties()) {
            for (Keyframe keyframe : track.getCustomKeyframes(key)) {
                if (Math.abs(keyframe.getTimeMs() - timeMs) <= TIME_EPSILON_MS) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static int countLayerPaths(EditorEventCue cue) {
        String layers = cue.getPayloadValue("layers");
        if (layers.isBlank()) return 0;
        int count = 0;
        for (String path : layers.split("\\|")) {
            if (!path.isBlank()) count++;
        }
        return count;
    }

    private static String eventLabel(EditorEventCue cue) {
        if ("expression".equalsIgnoreCase(cue.getType())) {
            String expression = firstNonBlank(
                cue.getPayloadValue("value"),
                cue.getPayloadValue("expression"),
                "");
            return expression.isBlank() ? "Expression" : "Expression  •  " + expression;
        }
        return cue.getType() == null || cue.getType().isBlank() ? "Event" : humanizeKey(cue.getType());
    }

    private static String humanizeKey(String value) {
        if (value == null || value.isBlank()) return "Custom channel";
        String normalized = value.trim().replace('.', ' ').replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String formatTime(double millis) {
        long totalMillis = Math.max(0L, Math.round(sanitizeTime(millis)));
        long minutes = totalMillis / 60_000L;
        long seconds = (totalMillis / 1_000L) % 60L;
        long ms = totalMillis % 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, ms);
    }

    private static String formatDelta(double deltaMs) {
        double safe = Math.max(0.0, sanitizeTime(deltaMs));
        if (safe < 1_000.0) {
            return String.format(Locale.ROOT, "in %.0f ms", safe);
        }
        return String.format(Locale.ROOT, "in %.2f s", safe / 1_000.0);
    }

    private static double sanitizeTime(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) return value.trim();
            }
        }
        return "";
    }
}
