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
    void detectsEventCuesThatWouldDoNothingAtRuntime() {
        AnimationProject project = new AnimationProject();
        project.addEditorEventCue(new EditorEventCue(100, "expression", java.util.Map.of("target", "hero")));
        project.addEditorEventCue(new EditorEventCue(200, "hide", java.util.Map.of()));
        project.addEditorEventCue(new EditorEventCue(300, "script_call", java.util.Map.of("count", "4")));

        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, null);

        assertTrue(msgs.stream().anyMatch(m ->
            m.description().contains("has no expression or image path")));
        assertTrue(msgs.stream().anyMatch(m ->
            m.description().contains("has no target")));
        assertTrue(msgs.stream().anyMatch(m ->
            m.description().contains("has no handler")));
    }

    @Test
    void detectsAudioCueConsistencyProblems() {
        AnimationProject project = new AnimationProject();
        project.setTotalDurationMs(1000);

        AudioCue emptyPath = new AudioCue(100, "", "music");
        AudioCue duplicateA = new AudioCue(220, "assets/audio/a.wav", "sound");
        AudioCue duplicateB = new AudioCue(220, "assets/audio/b.wav", "sound");
        AudioCue unknownChannel = new AudioCue(340, "assets/audio/c.wav", "ambience");
        AudioCue fadeWithoutDuration = new AudioCue(420, "assets/audio/d.wav", "music");
        fadeWithoutDuration.setFadeIn(true);
        fadeWithoutDuration.setFadeDurationMs(0);
        AudioCue beyondDuration = new AudioCue(1400, "assets/audio/e.wav", "voice");

        project.addAudioCue(emptyPath);
        project.addAudioCue(duplicateA);
        project.addAudioCue(duplicateB);
        project.addAudioCue(unknownChannel);
        project.addAudioCue(fadeWithoutDuration);
        project.addAudioCue(beyondDuration);

        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, null);
        assertTrue(msgs.stream().anyMatch(m -> m.description().contains("empty audio path")));
        assertTrue(msgs.stream().anyMatch(m -> m.description().contains("Multiple audio cues share channel 'sound'")));
        assertTrue(msgs.stream().anyMatch(m -> m.description().contains("channel 'ambience'")));
        assertTrue(msgs.stream().anyMatch(m -> m.description().contains("fade-in is enabled")));
        assertTrue(msgs.stream().anyMatch(m -> m.description().contains("exceeds timeline duration")));
    }

    @Test
    void detectsKeyframeBeyondTimelineDuration() {
        AnimationProject project = new AnimationProject();
        project.setTotalDurationMs(800);
        EntityTrack track = project.getOrCreateTrack("hero");
        track.addKeyframe(PropertyType.X, new Keyframe(1600, 400));

        List<TimelineDiagnostic.Message> msgs = TimelineDiagnostic.diagnose(project, Set.of("hero"));
        assertTrue(msgs.stream().anyMatch(m ->
            m.description().contains("keyframe") && m.description().contains("exceeds timeline duration")));
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
