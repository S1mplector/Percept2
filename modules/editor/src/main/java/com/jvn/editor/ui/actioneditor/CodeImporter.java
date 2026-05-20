package com.jvn.editor.ui.actioneditor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineDataParser;
import com.jvn.core.vn.VnEyeFocusProfile;

/**
 * Imports a JES timeline DSL text into an editor-side {@link AnimationProject}.
 * This closes the round-trip: code -> model -> export -> model -> export.
 * <p>
 * Strategy: parse through {@link TimelineDataParser} to obtain a {@link TimelineData},
 * then map runtime structures back into editor model objects.
 */
public class CodeImporter {
    private static final String ENTITY_META_PREFIX = "// @jvn-puppeteer-entity";
    private static final String STAGE_META_PREFIX = "// @jvn-puppeteer-stage";
    private static final String PROJECT_META_PREFIX = "// @jvn-puppeteer-project";
    private static final String GROUP_META_PREFIX = "// @jvn-puppeteer-group";
    private static final String TRACK_META_PREFIX = "// @jvn-puppeteer-track";
    private static final String KEY_META_PREFIX = "// @jvn-puppeteer-key";
    private static final String CUSTOM_KEY_META_PREFIX = "// @jvn-puppeteer-custom-key";
    private static final String ORBIT_META_PREFIX = "// @jvn-puppeteer-orbit";
    private static final String CONSTRAINT_META_PREFIX = "// @jvn-puppeteer-constraint";
    private static final String ANCHOR_META_PREFIX = "// @jvn-puppeteer-anchor";
    private static final String EYE_FOCUS_META_PREFIX = "// @jvn-puppeteer-eye-focus";

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
        EditorProjectMetadata metadata = parseEditorProjectMetadata(code);
        boolean restoredEditorModel = false;
        if (metadata.hasEditorModel()) {
            project = metadata.restoreProject(project, name);
            restoredEditorModel = true;
        } else {
            metadata.applySettings(project);
        }
        applySceneMetadata(project, parseSceneEntitySnapshots(code), !restoredEditorModel);
        project.setStageContext(parseStageContext(code));
        project.setEyeFocusProfiles(metadata.eyeFocusProfiles);
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
            case MIRROR_X -> PropertyType.MIRROR_X;
            case ALPHA -> PropertyType.ALPHA;
            case VISIBILITY -> PropertyType.VISIBILITY;
            case CAMERA_X -> PropertyType.CAMERA_X;
            case CAMERA_Y -> PropertyType.CAMERA_Y;
            case CAMERA_ZOOM -> PropertyType.CAMERA_ZOOM;
        };
    }

    private static void applySceneMetadata(
        AnimationProject project,
        List<AnimationProject.SceneEntitySnapshot> snapshots,
        boolean rebaseAutogeneratedKeyframes
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
            props.put(PropertyType.MIRROR_X, 0.0);
            props.put(PropertyType.ALPHA, snapshot.alpha());
            props.put(PropertyType.VISIBILITY, snapshot.visible() ? 1.0 : 0.0);
            props.put(PropertyType.MATRIX_MXX, 1.0);
            props.put(PropertyType.MATRIX_MXY, 0.0);
            props.put(PropertyType.MATRIX_MYX, 0.0);
            props.put(PropertyType.MATRIX_MYY, 1.0);
            props.put(PropertyType.MATRIX_TX, 0.0);
            props.put(PropertyType.MATRIX_TY, 0.0);
            props.put(PropertyType.BLUR, 0.0);
            props.put(PropertyType.BRIGHTNESS, 1.0);
            baseline.put(snapshot.name(), props);
        }
        project.setInitialSnapshot(baseline);
        if (rebaseAutogeneratedKeyframes) {
            project.rebaseAutogeneratedStartKeyframes(baseline);
        }
    }

    private static EditorProjectMetadata parseEditorProjectMetadata(String code) {
        EditorProjectMetadata metadata = new EditorProjectMetadata();
        if (code == null || code.isBlank()) return metadata;
        for (String rawLine : code.split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.startsWith(PROJECT_META_PREFIX)) {
                metadata.applyProjectAttributes(parseAttributes(line.substring(PROJECT_META_PREFIX.length()).trim()));
            } else if (line.startsWith(GROUP_META_PREFIX)) {
                metadata.groups.add(GroupMeta.from(parseAttributes(line.substring(GROUP_META_PREFIX.length()).trim())));
            } else if (line.startsWith(TRACK_META_PREFIX)) {
                metadata.tracks.add(TrackMeta.from(parseAttributes(line.substring(TRACK_META_PREFIX.length()).trim())));
            } else if (line.startsWith(CUSTOM_KEY_META_PREFIX)) {
                KeyMeta key = KeyMeta.from(parseAttributes(line.substring(CUSTOM_KEY_META_PREFIX.length()).trim()), true);
                if (key != null) metadata.customKeys.add(key);
            } else if (line.startsWith(KEY_META_PREFIX)) {
                KeyMeta key = KeyMeta.from(parseAttributes(line.substring(KEY_META_PREFIX.length()).trim()), false);
                if (key != null) metadata.keys.add(key);
            } else if (line.startsWith(ORBIT_META_PREFIX)) {
                OrbitMeta orbit = OrbitMeta.from(parseAttributes(line.substring(ORBIT_META_PREFIX.length()).trim()));
                if (orbit != null) metadata.orbits.add(orbit);
            } else if (line.startsWith(CONSTRAINT_META_PREFIX)) {
                ConstraintMeta constraint = ConstraintMeta.from(parseAttributes(line.substring(CONSTRAINT_META_PREFIX.length()).trim()));
                if (constraint != null) metadata.constraints.add(constraint);
            } else if (line.startsWith(ANCHOR_META_PREFIX)) {
                AnchorMeta anchor = AnchorMeta.from(parseAttributes(line.substring(ANCHOR_META_PREFIX.length()).trim()));
                if (anchor != null) metadata.anchors.add(anchor);
            } else if (line.startsWith(EYE_FOCUS_META_PREFIX)) {
                VnEyeFocusProfile profile = parseEyeFocusProfile(
                    parseAttributes(line.substring(EYE_FOCUS_META_PREFIX.length()).trim()));
                if (profile != null) metadata.eyeFocusProfiles.add(profile);
            }
        }
        metadata.groups.removeIf(group -> group == null || group.name().isBlank());
        metadata.tracks.removeIf(track -> track == null || track.name().isBlank());
        return metadata;
    }

    private static final class EditorProjectMetadata {
        private Double durationMs;
        private Double playheadMs;
        private Boolean looping;
        private Double loopStartMs;
        private Double loopEndMs;
        private final List<GroupMeta> groups = new ArrayList<>();
        private final List<TrackMeta> tracks = new ArrayList<>();
        private final List<KeyMeta> keys = new ArrayList<>();
        private final List<KeyMeta> customKeys = new ArrayList<>();
        private final List<OrbitMeta> orbits = new ArrayList<>();
        private final List<ConstraintMeta> constraints = new ArrayList<>();
        private final List<AnchorMeta> anchors = new ArrayList<>();
        private final List<VnEyeFocusProfile> eyeFocusProfiles = new ArrayList<>();

        void applyProjectAttributes(Map<String, String> attrs) {
            durationMs = parseOptionalDouble(attrs.get("duration"));
            playheadMs = parseOptionalDouble(attrs.get("playhead"));
            looping = parseOptionalBoolean(attrs.get("looping"));
            loopStartMs = parseOptionalDouble(attrs.get("loopStart"));
            loopEndMs = parseOptionalDouble(attrs.get("loopEnd"));
        }

        boolean hasEditorModel() {
            return !groups.isEmpty()
                || !tracks.isEmpty()
                || !keys.isEmpty()
                || !customKeys.isEmpty()
                || !orbits.isEmpty()
                || !constraints.isEmpty()
                || !anchors.isEmpty();
        }

        void applySettings(AnimationProject project) {
            applySettings(project, null, null);
        }

        private void applySettings(AnimationProject project, AnimationProject runtimeProject, String fallbackName) {
            if (project == null) return;
            String resolvedName = fallbackName != null && !fallbackName.isBlank()
                ? fallbackName
                : runtimeProject != null ? runtimeProject.getName() : project.getName();
            project.setName(resolvedName);
            if (durationMs != null) {
                project.setTotalDurationMs(durationMs);
            } else if (runtimeProject != null) {
                project.setTotalDurationMs(runtimeProject.getTotalDurationMs());
            }
            if (playheadMs != null) {
                project.setPlayheadMs(playheadMs);
            } else if (runtimeProject != null) {
                project.setPlayheadMs(runtimeProject.getPlayheadMs());
            }
            if (looping != null) {
                project.setLooping(looping);
            } else if (runtimeProject != null) {
                project.setLooping(runtimeProject.isLooping());
            }
            if (loopStartMs != null && loopEndMs != null && loopEndMs > loopStartMs) {
                project.setLoopRegion(loopStartMs, loopEndMs);
            } else if (runtimeProject != null && runtimeProject.hasLoopRegion()) {
                project.setLoopRegion(runtimeProject.getLoopStartMs(), runtimeProject.getLoopEndMs());
            } else {
                project.clearLoopRegion();
            }
        }

        AnimationProject restoreProject(AnimationProject runtimeProject, String fallbackName) {
            AnimationProject restored = new AnimationProject();
            applySettings(restored, runtimeProject, fallbackName);

            for (AudioCue cue : runtimeProject.getAudioCues()) {
                restored.addAudioCue(cue.copy());
            }
            for (EditorEventCue event : runtimeProject.getEditorEventCues()) {
                restored.addEditorEventCue(event.copy());
            }

            for (GroupMeta group : groups) {
                EntityGroup entityGroup = restored.getOrCreateGroup(group.name());
                entityGroup.setLayerOrder(group.layer());
                entityGroup.setExpanded(group.expanded());
                entityGroup.setLocked(group.locked());
            }
            for (GroupMeta group : groups) {
                if (!group.parent().isBlank()) {
                    restored.getOrCreateGroup(group.parent());
                    restored.addGroupToGroup(group.name(), group.parent());
                }
            }

            for (TrackMeta trackMeta : tracks) {
                EntityTrack track = restored.getOrCreateTrack(trackMeta.name());
                track.setLayerOrder(trackMeta.layer());
                track.setExpanded(trackMeta.expanded());
                track.setVisible(trackMeta.visible());
                track.setLocked(trackMeta.locked());
                if (!trackMeta.parent().isBlank()) {
                    restored.getOrCreateGroup(trackMeta.parent());
                    restored.addEntityToGroup(trackMeta.name(), trackMeta.parent());
                }
            }

            for (KeyMeta key : keys) {
                EntityTrack track = resolveMetadataTrack(restored, key);
                if (track == null || key.property() == null) continue;
                track.upsertKeyframe(key.property(), key.toKeyframe());
            }
            for (KeyMeta key : customKeys) {
                EntityTrack track = resolveMetadataTrack(restored, key);
                if (track == null || key.customKey().isBlank()) continue;
                track.upsertCustomKeyframe(key.customKey(), key.toKeyframe());
            }
            for (OrbitMeta orbit : orbits) {
                restored.setOrbitAnchor(orbit.target(), orbit.x(), orbit.y());
                if (!orbit.source().isBlank()) {
                    restored.setOrbitAnchorSource(orbit.target(), orbit.source(), orbit.offsetX(), orbit.offsetY());
                }
            }
            for (ConstraintMeta constraint : constraints) {
                restored.setConstraint(constraint.target(), constraint.toConstraint());
            }
            for (AnchorMeta anchor : anchors) {
                restored.setAnchor(anchor.entity(), new Anchor(anchor.name(), anchor.x(), anchor.y(), anchor.relative()));
            }
            return restored;
        }

        private EntityTrack resolveMetadataTrack(AnimationProject project, KeyMeta key) {
            if (project == null || key == null || key.target().isBlank()) return null;
            if (key.group()) {
                return project.getOrCreateGroup(key.target()).getGroupTrack();
            }
            return project.getOrCreateTrack(key.target());
        }
    }

    private record GroupMeta(String name, String parent, int layer, boolean expanded, boolean locked) {
        static GroupMeta from(Map<String, String> attrs) {
            return new GroupMeta(
                decode(attrs.get("name")),
                decode(attrs.get("parent")),
                parseInt(attrs.get("layer"), 0),
                parseBoolean(attrs.get("expanded"), true),
                parseBoolean(attrs.get("locked"), false)
            );
        }
    }

    private record TrackMeta(String name, String parent, boolean visible, boolean expanded, boolean locked, int layer) {
        static TrackMeta from(Map<String, String> attrs) {
            return new TrackMeta(
                decode(attrs.get("name")),
                decode(attrs.get("parent")),
                parseBoolean(attrs.get("visible"), true),
                parseBoolean(attrs.get("expanded"), true),
                parseBoolean(attrs.get("locked"), false),
                parseInt(attrs.get("layer"), 0)
            );
        }
    }

    private record OrbitMeta(String target, double x, double y, String source, double offsetX, double offsetY) {
        static OrbitMeta from(Map<String, String> attrs) {
            String target = decode(attrs.get("target"));
            if (target.isBlank()) return null;
            return new OrbitMeta(
                target,
                parseDouble(attrs.get("x"), 0.0),
                parseDouble(attrs.get("y"), 0.0),
                decode(attrs.get("source")),
                parseDouble(attrs.get("offsetX"), 0.0),
                parseDouble(attrs.get("offsetY"), 0.0)
            );
        }
    }

    private record ConstraintMeta(
        String target,
        Constraint.Type type,
        String source,
        double offsetX,
        double offsetY,
        boolean inheritRotation,
        boolean inheritScale
    ) {
        static ConstraintMeta from(Map<String, String> attrs) {
            String target = decode(attrs.get("target"));
            String source = decode(attrs.get("source"));
            Constraint.Type type = parseConstraintType(decode(attrs.get("type")));
            if (target.isBlank() || source.isBlank() || type == null) return null;
            return new ConstraintMeta(
                target,
                type,
                source,
                parseDouble(attrs.get("offsetX"), 0.0),
                parseDouble(attrs.get("offsetY"), 0.0),
                parseBoolean(attrs.get("inheritRot"), true),
                parseBoolean(attrs.get("inheritScale"), true)
            );
        }

        Constraint toConstraint() {
            return new Constraint(type, source, offsetX, offsetY, inheritRotation, inheritScale);
        }
    }

    private record AnchorMeta(String entity, String name, double x, double y, boolean relative) {
        static AnchorMeta from(Map<String, String> attrs) {
            String entity = decode(attrs.get("entity"));
            String name = decode(attrs.get("name"));
            if (entity.isBlank() || name.isBlank()) return null;
            return new AnchorMeta(
                entity,
                name,
                parseDouble(attrs.get("x"), 0.5),
                parseDouble(attrs.get("y"), 0.5),
                parseBoolean(attrs.get("relative"), true)
            );
        }
    }

    private record KeyMeta(
        String target,
        boolean group,
        PropertyType property,
        String customKey,
        double timeMs,
        double value,
        EasingSpec easingSpec,
        Easing.Interpolation interpolation
    ) {
        static KeyMeta from(Map<String, String> attrs, boolean custom) {
            String target = decode(attrs.get("target"));
            if (target.isBlank()) return null;
            String kind = decode(attrs.get("kind"));
            PropertyType property = custom ? null : parseProperty(attrs.get("prop"));
            String customKey = custom ? decode(attrs.get("key")) : "";
            if (!custom && property == null) return null;
            if (custom && customKey.isBlank()) return null;
            EasingSpec easingSpec = EasingSpec.tryParse(decode(attrs.get("easing")));
            if (easingSpec == null) easingSpec = EasingSpec.of(Easing.Type.LINEAR);
            return new KeyMeta(
                target,
                "group".equalsIgnoreCase(kind),
                property,
                customKey,
                parseDouble(attrs.get("time"), 0.0),
                parseDouble(attrs.get("value"), 0.0),
                easingSpec,
                parseInterpolation(decode(attrs.get("interp")))
            );
        }

        Keyframe toKeyframe() {
            return new Keyframe(timeMs, value, easingSpec, interpolation);
        }
    }

    private static VnEyeFocusProfile parseEyeFocusProfile(Map<String, String> attrs) {
        if (attrs == null || attrs.isEmpty()) return null;
        String character = decode(attrs.get("character"));
        if (character.isBlank()) return null;
        Map<Integer, String> layers = new LinkedHashMap<>();
        for (int keypad = 1; keypad <= 9; keypad++) {
            String layer = decode(attrs.get("layer" + keypad));
            if (!layer.isBlank()) {
                layers.put(keypad, layer);
            }
        }
        return new VnEyeFocusProfile(
            character,
            decode(attrs.get("expression")),
            decode(attrs.get("sourceAnchor")),
            parseDouble(attrs.get("sourceX"), 0.5),
            parseDouble(attrs.get("sourceY"), 0.26),
            parseDouble(attrs.get("deadZone"), 0.12),
            parseDouble(attrs.get("maxNudge"), 3.0),
            parseDouble(attrs.get("strength"), 1.0),
            layers
        );
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
                parseDouble(attrs.get("alpha"), 1.0),
                parseDouble(attrs.get("vminx"), parseDouble(attrs.get("x"), 0.0) - parseDouble(attrs.get("ox"), 0.0) * parseDouble(attrs.get("w"), 1.0)),
                parseDouble(attrs.get("vminy"), parseDouble(attrs.get("y"), 0.0) - parseDouble(attrs.get("oy"), 0.0) * parseDouble(attrs.get("h"), 1.0)),
                parseDouble(attrs.get("vmaxx"), parseDouble(attrs.get("x"), 0.0) - parseDouble(attrs.get("ox"), 0.0) * parseDouble(attrs.get("w"), 1.0) + parseDouble(attrs.get("w"), 1.0)),
                parseDouble(attrs.get("vmaxy"), parseDouble(attrs.get("y"), 0.0) - parseDouble(attrs.get("oy"), 0.0) * parseDouble(attrs.get("h"), 1.0) + parseDouble(attrs.get("h"), 1.0)),
                parseDouble(attrs.get("rbx"), parseDouble(attrs.get("x"), 0.0)),
                parseDouble(attrs.get("rby"), parseDouble(attrs.get("y"), 0.0))
            ));
        }
        return snapshots;
    }

    private static AnimationProject.StageContext parseStageContext(String code) {
        if (code == null || code.isBlank()) return null;
        for (String rawLine : code.split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.startsWith(STAGE_META_PREFIX)) continue;
            Map<String, String> attrs = parseAttributes(line.substring(STAGE_META_PREFIX.length()).trim());
            String id = decode(attrs.get("id"));
            if (id.isBlank()) return null;
            return new AnimationProject.StageContext(
                id,
                decode(attrs.get("source")),
                decode(attrs.get("bg")),
                decode(attrs.get("subject")),
                Math.max(0, (int) parseDouble(attrs.get("lights"), 0.0)),
                Math.max(0, (int) parseDouble(attrs.get("occluders"), 0.0)),
                Math.max(0, (int) parseDouble(attrs.get("zones"), 0.0))
            );
        }
        return null;
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

    private static Double parseOptionalDouble(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static Boolean parseOptionalBoolean(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return parseBoolean(raw, false);
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)) return true;
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)) return false;
        return fallback;
    }

    private static Easing.Interpolation parseInterpolation(String raw) {
        if (raw == null || raw.isBlank()) return Easing.Interpolation.TWEEN;
        try {
            return Easing.Interpolation.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Easing.Interpolation.TWEEN;
        }
    }

    private static PropertyType parseProperty(String raw) {
        String code = decode(raw);
        if (code.isBlank()) return null;
        for (PropertyType property : PropertyType.values()) {
            if (property.getCode().equals(code)) return property;
        }
        return null;
    }

    private static Constraint.Type parseConstraintType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Constraint.Type.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
