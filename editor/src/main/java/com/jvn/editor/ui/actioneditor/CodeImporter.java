package com.jvn.editor.ui.actioneditor;

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

        return fromTimelineData(data);
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
                        runtimeKf.getEasing(),
                        runtimeKf.getInterpolation()
                    );
                    if (runtimeKf.getEasing() == com.jvn.core.animation.Easing.Type.CUSTOM
                        && runtimeKf.hasBezierParams()) {
                        double[] bezier = runtimeKf.getBezierParams();
                        editorKf.setBezierParams(bezier[0], bezier[1], bezier[2], bezier[3]);
                    }
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
            case PIVOT_X -> PropertyType.PIVOT_X;
            case PIVOT_Y -> PropertyType.PIVOT_Y;
            case ROTATION -> PropertyType.ROTATION;
            case SCALE_X -> PropertyType.SCALE_X;
            case SCALE_Y -> PropertyType.SCALE_Y;
            case ALPHA -> PropertyType.ALPHA;
            case CAMERA_X -> PropertyType.CAMERA_X;
            case CAMERA_Y -> PropertyType.CAMERA_Y;
            case CAMERA_ZOOM -> PropertyType.CAMERA_ZOOM;
            case Z -> null; // handled separately as layerOrder
        };
    }
}
