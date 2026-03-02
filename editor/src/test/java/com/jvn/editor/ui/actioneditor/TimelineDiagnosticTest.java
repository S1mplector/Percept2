package com.jvn.editor.ui.actioneditor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for TimelineDiagnostic: value range checks, unknown entities,
 * easing validation, quick-fix suggestions (category G + H).
 */
class TimelineDiagnosticTest {

    @Test
    void detectsOutOfRangeAlpha() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.ALPHA, new Keyframe(0, 1.5));
        track.addKeyframe(PropertyType.ALPHA, new Keyframe(100, -0.3));

        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, null);
        assertEquals(2, msgs.size());
        assertTrue(msgs.stream().allMatch(m -> m.severity() == TimelineDiagnostic.Severity.WARNING));
        assertTrue(msgs.stream().anyMatch(m -> m.description().contains("1.5")));
        assertTrue(msgs.stream().anyMatch(m -> m.description().contains("-0.3")));
        // Quick fixes should suggest clamped values
        assertTrue(msgs.stream().allMatch(m -> m.quickFix() != null));
    }

    @Test
    void detectsNonPositiveCameraZoom() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("__camera__");
        track.addKeyframe(PropertyType.CAMERA_ZOOM, new Keyframe(0, 0.0));
        track.addKeyframe(PropertyType.CAMERA_ZOOM, new Keyframe(100, -1.0));

        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, null);
        assertTrue(msgs.size() >= 2);
        assertTrue(msgs.stream().anyMatch(m -> m.description().contains("non-positive")));
    }

    @Test
    void detectsExcessiveCameraZoom() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("__camera__");
        track.addKeyframe(PropertyType.CAMERA_ZOOM, new Keyframe(0, 15.0));

        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, null);
        assertTrue(msgs.stream().anyMatch(m -> m.description().contains("unusually large")));
    }

    @Test
    void detectsOutOfRangePivot() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.PIVOT_X, new Keyframe(0, 1.5));
        track.addKeyframe(PropertyType.PIVOT_Y, new Keyframe(0, -0.2));

        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, null);
        assertEquals(2, msgs.size());
        assertTrue(msgs.stream().allMatch(m -> m.quickFix() != null));
    }

    @Test
    void detectsMissingEntity() {
        AnimationProject project = new AnimationProject();
        project.getOrCreateTrack("ghost");

        Set<String> known = Set.of("hero", "guide");
        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, known);
        assertTrue(msgs.stream().anyMatch(m ->
            m.description().contains("ghost") && m.description().contains("not found")));
    }

    @Test
    void doesNotFlagCameraTrackAsMissingEntity() {
        AnimationProject project = new AnimationProject();
        project.getOrCreateTrack("__camera__");

        Set<String> known = Set.of("hero");
        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, known);
        assertTrue(msgs.stream().noneMatch(m ->
            m.description().contains("__camera__") && m.description().contains("not found")));
    }

    @Test
    void detectsEmptyEventCueType() {
        AnimationProject project = new AnimationProject();
        project.addEditorEventCue(new EditorEventCue(100, "", null));

        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, null);
        assertTrue(msgs.stream().anyMatch(m ->
            m.severity() == TimelineDiagnostic.Severity.ERROR
            && m.description().contains("empty type")));
    }

    @Test
    void cleanProjectProducesNoDiagnostics() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("hero");
        track.addKeyframe(PropertyType.X, new Keyframe(0, 100));
        track.addKeyframe(PropertyType.Y, new Keyframe(0, 200));
        track.addKeyframe(PropertyType.ALPHA, new Keyframe(0, 0.5));

        Set<String> known = Set.of("hero");
        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, known);
        assertTrue(msgs.isEmpty(), "Clean project should have no diagnostics");
    }

    @Test
    void suggestEasingFindsCloseMatch() {
        assertEquals("linear", TimelineDiagnostic.suggestEasing("linar"));
        assertEquals("ease_out_quad", TimelineDiagnostic.suggestEasing("ease_out_qad"));
        assertNotNull(TimelineDiagnostic.suggestEasing(""));
    }

    @Test
    void suggestEasingRejectsGarbage() {
        assertNull(TimelineDiagnostic.suggestEasing("xyzzyfoobarbaz"));
    }
}
