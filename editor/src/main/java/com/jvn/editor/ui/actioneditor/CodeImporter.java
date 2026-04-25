package com.jvn.editor.ui.actioneditor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineDataParser;

/**
 * Imports a JES timeline DSL text into an editor-side {@link AnimationProject}.
 * This closes the round-trip: code -> model -> export -> model -> export.
 * <p>
 * Strategy: parse through {@link TimelineDataParser} to obtain a {@link TimelineData},
 * then map runtime structures back into editor model objects.
 */
public class CodeImporter {
    private static final String ENTITY_META_PREFIX = "// @jvn-puppeteer-entity";

    /**
     * Import a JES timeline DSL block into a new {@link AnimationProject}.
     *
     * @param name the timeline name
     * @param code the full timeline DSL text (may include outer {@code timeline \{...\}})
     * @return a fully populated AnimationProject
     */
    public static AnimationProject importCode(String name, String code) {
        if (code == null || code.isBlank()) {
            AnimationProject empty = new AnimationProject();
            empty.setName(name != null ? name : "Untitled");
            return empty;
        }

        TimelineData data = TimelineDataParser.parse(
            name != null ? name : "imported", code);

        AnimationProject project = fromTimelineData(data);
        applySceneMetadata(project, parseSceneEntitySnapshots(code));
        return project;
    }

    /**
     * Convert a runtime {@link TimelineData} back into an editor-side
     * {@link AnimationProject} preserving tracks, keyframes, audio cues,
     * and event cues.
     */
    public static AnimationProject fromTimelineData(TimelineData data) {
        AnimationProject project = new AnimationProject();
        project.setName(data.getName());
        project.setTotalDurationMs(Math.max(data.getDurationMs(), 100));
        project.setLooping(data.isLooping());

        for (TimelineData.Track runtimeTrack : data.getTracks()) {
            EntityTrack editorTrack = project.getOrCreateTrack(runtimeTrack.getEntityName());

            for (TimelineData.Property runtimeProp : TimelineData.Property.values()) {
                PropertyType editorProp = mapPropertyBack(runtimeProp);
                if (editorProp == null) continue;

                for (TimelineData.Keyframe runtimeKf : runtimeTrack.getKeyframes(runtimeProp)) {
                    Keyframe editorKf = new Keyframe(
                        runtimeKf.getTimeMs(),
                        runtimeKf.getValue(),
                        runtimeKf.getEasingSpec(),
                        runtimeKf.getInterpolation()
                    );
                    editorTrack.addKeyframe(editorProp, editorKf);
                }
            }

            // Z keyframes -> layerOrder (use first Z keyframe value)
            if (runtimeTrack.hasKeyframes(TimelineData.Property.Z)) {
                var zKfs = runtimeTrack.getKeyframes(TimelineData.Property.Z);
                if (!zKfs.isEmpty()) {
                    editorTrack.setLayerOrder((int) Math.round(zKfs.get(0).getValue()));
                }
            }

            for (var entry : runtimeTrack.getAllCustomKeyframes().entrySet()) {
                String propertyKey = entry.getKey();
                if (propertyKey == null || propertyKey.isBlank()) continue;
                PropertyType mapped = PropertyType.fromTimelineCustomKey(propertyKey);
                for (TimelineData.Keyframe runtimeKf : entry.getValue()) {
                    Keyframe editorKf = new Keyframe(
                        runtimeKf.getTimeMs(),
                        runtimeKf.getValue(),
                        runtimeKf.getEasingSpec(),
                        runtimeKf.getInterpolation()
                    );
                    if (mapped != null) {
                        editorTrack.addKeyframe(mapped, editorKf);
                    } else {
                        editorTrack.addCustomKeyframe(propertyKey, editorKf);
                    }
                }
            }
        }

        for (TimelineData.AudioCue runtimeCue : data.getAudioCues()) {
            AudioCue editorCue = new AudioCue(
                runtimeCue.getTimeMs(),
                runtimeCue.getTrackPath(),
                runtimeCue.getChannel()
            );
            editorCue.setVolume(runtimeCue.getVolume());
            if (runtimeCue.getFadeInMs() > 0) {
                editorCue.setFadeIn(true);
                editorCue.setFadeDurationMs(runtimeCue.getFadeInMs());
            }
            project.addAudioCue(editorCue);
        }

        // Event cues are stored on the project as editor-side EventCue instances
        for (TimelineData.EventCue runtimeEvt : data.getEventCues()) {
            project.addEditorEventCue(new EditorEventCue(
                runtimeEvt.getTimeMs(),
                runtimeEvt.getType(),
                new java.util.LinkedHashMap<>(runtimeEvt.getPayload())
            ));
        }

        return project;
    }

    private static PropertyType mapPropertyBack(TimelineData.Property p) {
        return switch (p) {
            case X -> PropertyType.X;
            case Y -> PropertyType.Y;
            case Z -> PropertyType.Z;
            case PIVOT_X -> PropertyType.PIVOT_X;
            case PIVOT_Y -> PropertyType.PIVOT_Y;
            case ROTATION -> PropertyType.ROTATION;
            case SCALE_X -> PropertyType.SCALE_X;
            case SCALE_Y -> PropertyType.SCALE_Y;
            case ALPHA -> PropertyType.ALPHA;
            case VISIBILITY -> PropertyType.VISIBILITY;
            case CAMERA_X -> PropertyType.CAMERA_X;
            case CAMERA_Y -> PropertyType.CAMERA_Y;
            case CAMERA_ZOOM -> PropertyType.CAMERA_ZOOM;
        };
    }

    private static void applySceneMetadata(
        AnimationProject project,
        List<AnimationProject.SceneEntitySnapshot> snapshots
    ) {
        if (project == null || snapshots == null || snapshots.isEmpty()) return;
        project.setSceneEntitySnapshots(snapshots);
        Map<String, Map<PropertyType, Double>> baseline = new LinkedHashMap<>();
        for (AnimationProject.SceneEntitySnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.name().isBlank()) continue;
            project.getOrCreateTrack(snapshot.name());
            Map<PropertyType, Double> props = new EnumMap<>(PropertyType.class);
            props.put(PropertyType.X, snapshot.x());
            props.put(PropertyType.Y, snapshot.y());
            props.put(PropertyType.Z, snapshot.z());
            props.put(PropertyType.PIVOT_X, snapshot.originX());
            props.put(PropertyType.PIVOT_Y, snapshot.originY());
            props.put(PropertyType.ROTATION, 0.0);
            props.put(PropertyType.SCALE_X, 1.0);
            props.put(PropertyType.SCALE_Y, 1.0);
            props.put(PropertyType.ALPHA, snapshot.alpha());
            props.put(PropertyType.VISIBILITY, snapshot.visible() ? 1.0 : 0.0);
            props.put(PropertyType.MATRIX_MXX, 1.0);
            props.put(PropertyType.MATRIX_MXY, 0.0);
            props.put(PropertyType.MATRIX_MYX, 0.0);
            props.put(PropertyType.MATRIX_MYY, 1.0);
            props.put(PropertyType.MATRIX_TX, 0.0);
            props.put(PropertyType.MATRIX_TY, 0.0);
            props.put(PropertyType.BLUR, 0.0);
            baseline.put(snapshot.name(), props);
        }
        project.setInitialSnapshot(baseline);
        project.rebaseAutogeneratedStartKeyframes(baseline);
    }

    private static List<AnimationProject.SceneEntitySnapshot> parseSceneEntitySnapshots(String code) {
        if (code == null || code.isBlank()) return List.of();
        List<AnimationProject.SceneEntitySnapshot> snapshots = new ArrayList<>();
        for (String rawLine : code.split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.startsWith(ENTITY_META_PREFIX)) continue;
            Map<String, String> attrs = parseAttributes(line.substring(ENTITY_META_PREFIX.length()).trim());
            String name = decode(attrs.get("name"));
            if (name.isBlank()) continue;
            snapshots.add(new AnimationProject.SceneEntitySnapshot(
                name,
                decode(attrs.get("type")),
                decode(attrs.get("image")),
                parseDouble(attrs.get("x"), 0.0),
                parseDouble(attrs.get("y"), 0.0),
                parseDouble(attrs.get("w"), 1.0),
                parseDouble(attrs.get("h"), 1.0),
                parseDouble(attrs.get("ox"), 0.0),
                parseDouble(attrs.get("oy"), 0.0),
                parseDouble(attrs.get("z"), 0.0),
                !"0".equals(attrs.getOrDefault("visible", "1")),
                parseDouble(attrs.get("alpha"), 1.0)
            ));
        }
        return snapshots;
    }

    private static Map<String, String> parseAttributes(String raw) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return attrs;
        for (String token : raw.split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals <= 0) continue;
            String key = token.substring(0, equals).trim();
            String value = token.substring(equals + 1).trim();
            if (!key.isBlank()) attrs.put(key, value);
        }
        return attrs;
    }

    private static String decode(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return raw;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
