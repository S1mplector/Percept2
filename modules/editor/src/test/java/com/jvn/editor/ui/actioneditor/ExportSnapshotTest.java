package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.*;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshot / fixture tests for exported timelines with audio, camera, and events.
 * These serve as regression baselines — if the export format changes, these tests
 * will catch it (category H).
 */
class ExportSnapshotTest {

    @Test
    void exportedMoveContainsExpectedStructure() {
        AnimationProject project = new AnimationProject();
        project.setName("snapshot_move");
        EntityTrack track = project.getOrCreateTrack("hero");
        track.addKeyframe(PropertyType.X, new Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(PropertyType.X, new Keyframe(500, 200, Easing.Type.EASE_OUT_QUAD));
        track.addKeyframe(PropertyType.Y, new Keyframe(0, 100, Easing.Type.LINEAR));
        track.addKeyframe(PropertyType.Y, new Keyframe(500, 300, Easing.Type.EASE_OUT_QUAD));

        String exported = CodeExporter.export(project);
        assertNotNull(exported);
        assertTrue(exported.contains("timeline {"));
        assertTrue(exported.contains("move \"hero\""));
        assertTrue(exported.contains("x:"));
        assertTrue(exported.contains("y:"));
        assertTrue(exported.contains("easing: ease_out_quad"));
    }

    @Test
    void exportedCameraContainsExpectedStructure() {
        AnimationProject project = new AnimationProject();
        project.setName("snapshot_cam");
        EntityTrack cam = project.getOrCreateTrack("__camera__");
        cam.addKeyframe(PropertyType.CAMERA_X, new Keyframe(0, 0, Easing.Type.LINEAR));
        cam.addKeyframe(PropertyType.CAMERA_X, new Keyframe(500, 100, Easing.Type.EASE_IN_OUT_SINE));
        cam.addKeyframe(PropertyType.CAMERA_Y, new Keyframe(0, 0, Easing.Type.LINEAR));
        cam.addKeyframe(PropertyType.CAMERA_Y, new Keyframe(500, -20, Easing.Type.EASE_IN_OUT_SINE));
        cam.addKeyframe(PropertyType.CAMERA_ZOOM, new Keyframe(0, 1.0, Easing.Type.LINEAR));
        cam.addKeyframe(PropertyType.CAMERA_ZOOM, new Keyframe(500, 1.5, Easing.Type.EASE_IN_OUT_SINE));

        String exported = CodeExporter.export(project);
        assertTrue(exported.contains("cameraMove"));
        assertTrue(exported.contains("cameraZoom"));
        assertTrue(exported.contains("easing: ease_in_out_sine"));
    }

    @Test
    void exportedSpringAndNamedCurveUseDslForms() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("hero");
        track.addKeyframe(PropertyType.X, new Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(PropertyType.X, new Keyframe(400, 180,
            EasingSpec.spring(220, 24, 1.0, 0.0), Easing.Interpolation.TWEEN));
        track.addKeyframe(PropertyType.ROTATION, new Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(PropertyType.ROTATION, new Keyframe(400, 12, Easing.Type.HERO_POP));

        String exported = CodeExporter.export(project);
        assertTrue(exported.contains("easing: spring(220, 24, 1, 0)"));
        assertTrue(exported.contains("easing: hero_pop"));
    }

    @Test
    void exportedAudioContainsExpectedStructure() {
        AnimationProject project = new AnimationProject();
        project.setName("snapshot_audio");
        project.addAudioCue(new AudioCue(100, "assets/audio/bgm/theme.mp3", "music"));

        String exported = CodeExporter.export(project);
        assertTrue(exported.contains("playAudio \"assets/audio/bgm/theme.mp3\""));
        assertTrue(exported.contains("volume:"));
    }

    @Test
    void exportedEventContainsExpectedStructure() {
        AnimationProject project = new AnimationProject();
        project.setName("snapshot_event");
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("target", "lavender");
        payload.put("value", "smile");
        project.addEditorEventCue(new EditorEventCue(200, "expression", payload));

        Map<String, String> markerPayload = new LinkedHashMap<>();
        markerPayload.put("id", "beat_1");
        project.addEditorEventCue(new EditorEventCue(500, "dialogue_marker", markerPayload));

        String exported = CodeExporter.exportNamed(project, "snapshot_event");
        assertTrue(
            exported.contains("expression \"lavender\"")
                || exported.contains("event \"expression\""),
            "Should contain expression action/event block"
        );
        assertFalse(exported.contains("event \"dialogue_marker\""),
            "dialogue_marker cues are authoring metadata only and must not export as a runtime event block");
        assertTrue(exported.contains("@jvn-puppeteer-cue"),
            "Should contain @jvn-puppeteer-cue metadata comment for the dialogue marker");
        assertTrue(exported.contains("id=beat_1"),
            "Metadata comment should carry the dialogue marker's id");
        if (exported.contains("event \"expression\"")) {
            assertTrue(exported.contains("target:"));
        }
        assertTrue(exported.contains("value:"));
    }

    @Test
    void complexTimelineExportIsStableAcrossMultipleCalls() {
        AnimationProject project = buildComplexProject();

        String export1 = CodeExporter.export(project);
        String export2 = CodeExporter.export(project);
        String export3 = CodeExporter.export(project);

        assertEquals(export1, export2, "Export should be deterministic (1 vs 2)");
        assertEquals(export2, export3, "Export should be deterministic (2 vs 3)");
    }

    @Test
    void complexTimelineRoundTripPreservesSemantics() {
        AnimationProject original = buildComplexProject();
        String exported = CodeExporter.export(original);

        AnimationProject imported = CodeImporter.importCode("complex_rt", exported);
        assertNotNull(imported);

        // Verify key entities exist
        assertNotNull(imported.getTrack("hero"));
        assertNotNull(imported.getTrack("__camera__"));

        // Verify audio cues survived
        assertFalse(imported.getAudioCues().isEmpty());

        // Verify event cues survived
        assertFalse(imported.getEditorEventCues().isEmpty());
        assertTrue(imported.getEditorEventCues().stream()
            .anyMatch(e -> "expression".equals(e.getType())));
    }

    private AnimationProject buildComplexProject() {
        AnimationProject project = new AnimationProject();
        project.setName("complex_snapshot");
        project.setTotalDurationMs(2000);

        // Entity track with move + rotate + scale + fade
        EntityTrack hero = project.getOrCreateTrack("hero");
        hero.addKeyframe(PropertyType.X, new Keyframe(0, 100, Easing.Type.LINEAR));
        hero.addKeyframe(PropertyType.X, new Keyframe(500, 400, Easing.Type.EASE_OUT_QUAD));
        hero.addKeyframe(PropertyType.Y, new Keyframe(0, 200, Easing.Type.LINEAR));
        hero.addKeyframe(PropertyType.Y, new Keyframe(500, 350, Easing.Type.EASE_OUT_QUAD));
        hero.addKeyframe(PropertyType.ROTATION, new Keyframe(0, 0, Easing.Type.LINEAR));
        hero.addKeyframe(PropertyType.ROTATION, new Keyframe(800, -15, Easing.Type.EASE_IN_OUT_SINE));
        hero.addKeyframe(PropertyType.SCALE_X, new Keyframe(0, 1.0, Easing.Type.LINEAR));
        hero.addKeyframe(PropertyType.SCALE_X, new Keyframe(600, 1.2, Easing.Type.EASE_OUT_QUAD));
        hero.addKeyframe(PropertyType.SCALE_Y, new Keyframe(0, 1.0, Easing.Type.LINEAR));
        hero.addKeyframe(PropertyType.SCALE_Y, new Keyframe(600, 1.2, Easing.Type.EASE_OUT_QUAD));
        hero.addKeyframe(PropertyType.ALPHA, new Keyframe(0, 1.0, Easing.Type.LINEAR));
        hero.addKeyframe(PropertyType.ALPHA, new Keyframe(1000, 0.5, Easing.Type.LINEAR));

        // Camera track
        EntityTrack cam = project.getOrCreateTrack("__camera__");
        cam.addKeyframe(PropertyType.CAMERA_X, new Keyframe(0, 0, Easing.Type.LINEAR));
        cam.addKeyframe(PropertyType.CAMERA_X, new Keyframe(700, 50, Easing.Type.EASE_IN_OUT_SINE));
        cam.addKeyframe(PropertyType.CAMERA_Y, new Keyframe(0, 0, Easing.Type.LINEAR));
        cam.addKeyframe(PropertyType.CAMERA_Y, new Keyframe(700, -10, Easing.Type.EASE_IN_OUT_SINE));
        cam.addKeyframe(PropertyType.CAMERA_ZOOM, new Keyframe(0, 1.0, Easing.Type.LINEAR));
        cam.addKeyframe(PropertyType.CAMERA_ZOOM, new Keyframe(700, 1.15, Easing.Type.EASE_IN_OUT_SINE));

        // Audio cue
        project.addAudioCue(new AudioCue(200, "assets/audio/sfx/swoosh.wav", "sound"));

        // Event cues
        Map<String, String> exprPayload = new LinkedHashMap<>();
        exprPayload.put("target", "hero");
        exprPayload.put("value", "smile");
        project.addEditorEventCue(new EditorEventCue(100, "expression", exprPayload));

        Map<String, String> markerPayload = new LinkedHashMap<>();
        markerPayload.put("id", "complex_beat");
        project.addEditorEventCue(new EditorEventCue(500, "dialogue_marker", markerPayload));

        return project;
    }
}
