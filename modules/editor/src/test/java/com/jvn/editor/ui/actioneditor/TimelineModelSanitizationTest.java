package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineModelSanitizationTest {

    @Test
    void keyframeNormalizesNonFiniteInputs() {
        Keyframe kf = new Keyframe(Double.NaN, Double.POSITIVE_INFINITY, Easing.Type.LINEAR);
        assertEquals(0.0, kf.getTimeMs(), 0.0001);
        assertEquals(0.0, kf.getValue(), 0.0001);

        kf.setTimeMs(Double.NEGATIVE_INFINITY);
        kf.setValue(Double.NaN);
        assertEquals(0.0, kf.getTimeMs(), 0.0001);
        assertEquals(0.0, kf.getValue(), 0.0001);
    }

    @Test
    void audioCueNormalizesBlankChannelAndInvalidNumericInputs() {
        AudioCue cue = new AudioCue(Double.NaN, " assets/audio/sfx/click.wav ", "   ");
        assertEquals(0.0, cue.getTimeMs(), 0.0001);
        assertEquals("sound", cue.getChannel());
        assertEquals("assets/audio/sfx/click.wav", cue.getAudioFile());

        cue.setVolume(0.35);
        cue.setVolume(Double.NaN);
        assertEquals(0.35, cue.getVolume(), 0.0001);

        cue.setFadeDurationMs(120.0);
        cue.setFadeDurationMs(Double.NaN);
        assertEquals(120.0, cue.getFadeDurationMs(), 0.0001);
    }

    @Test
    void expressionEventCueHasDirectorFacingLabelsAndDslPreview() {
        EditorEventCue cue = new EditorEventCue(500.0, "expression", java.util.Map.of(
            "target", "hero",
            "value", "angry",
            "layers", "base=assets/hero/base.png;eyes=assets/hero/angry_eyes.png"
        ));

        assertEquals("500ms  expression  hero -> angry [layers]", cue.getDisplayLabel());
        assertEquals("EXP hero:angry*", cue.getTimelineLabel());
        assertTrue(cue.getDslPreview().contains("expression \"hero\""));
        assertTrue(cue.getDslPreview().contains("value: \"angry\""));
        assertTrue(cue.getDslPreview().contains("layers: \"base=assets/hero/base.png;eyes=assets/hero/angry_eyes.png\""));
    }

    @Test
    void customEventCueDslPreviewUsesEventWrapper() {
        EditorEventCue cue = new EditorEventCue(120.0, "script_call", java.util.Map.of("name", "flash"));

        assertTrue(cue.getDslPreview().startsWith("event \"script_call\""));
        assertTrue(cue.getDslPreview().contains("name: \"flash\""));
    }

    @Test
    void animationProjectClampsPlayheadAndLoopRegionToDuration() {
        AnimationProject project = new AnimationProject();
        project.setTotalDurationMs(1000.0);
        project.setPlayheadMs(1400.0);
        project.setLoopRegion(950.0, 2500.0);

        assertEquals(1000.0, project.getPlayheadMs(), 0.0001);
        assertTrue(project.hasLoopRegion());
        assertEquals(950.0, project.getLoopStartMs(), 0.0001);
        assertEquals(1000.0, project.getLoopEndMs(), 0.0001);
    }

    @Test
    void animationProjectNormalizesNonFiniteDurationAndPlayhead() {
        AnimationProject project = new AnimationProject();
        project.setTotalDurationMs(Double.NaN);
        assertEquals(100.0, project.getTotalDurationMs(), 0.0001);

        project.setPlayheadMs(Double.NaN);
        assertEquals(0.0, project.getPlayheadMs(), 0.0001);
    }

    @Test
    void animationProjectCopyPreservesLoopRegion() {
        AnimationProject project = new AnimationProject();
        project.setTotalDurationMs(1800.0);
        project.setLoopRegion(300.0, 900.0);

        AnimationProject copy = project.copy();
        assertTrue(copy.hasLoopRegion());
        assertEquals(300.0, copy.getLoopStartMs(), 0.0001);
        assertEquals(900.0, copy.getLoopEndMs(), 0.0001);
    }

    @Test
    void animationProjectReplaceFromCreatesIndependentCopies() {
        AnimationProject source = new AnimationProject();
        source.setTotalDurationMs(2200.0);
        source.setPlayheadMs(1600.0);
        source.setLoopRegion(400.0, 1400.0);

        EntityGroup group = source.getOrCreateGroup("groupA");
        AudioCue cue = new AudioCue(250.0, "assets/audio/sfx/click.wav", "music");
        EditorEventCue event = new EditorEventCue(500.0, "expression", null);
        source.addAudioCue(cue);
        source.addEditorEventCue(event);

        AnimationProject target = new AnimationProject();
        target.replaceFrom(source);

        assertNotSame(source.getAudioCues().get(0), target.getAudioCues().get(0));
        assertNotSame(source.getEditorEventCues().get(0), target.getEditorEventCues().get(0));
        assertNotNull(target.getGroup("groupA"));
        assertNotSame(group, target.getGroup("groupA"));
        assertEquals(400.0, target.getLoopStartMs(), 0.0001);
        assertEquals(1400.0, target.getLoopEndMs(), 0.0001);

        cue.setAudioFile("assets/audio/sfx/changed.wav");
        event.setType("script_call");
        group.setLayerOrder(9);
        source.setLoopRegion(700.0, 1800.0);

        assertEquals("assets/audio/sfx/click.wav", target.getAudioCues().get(0).getAudioFile());
        assertEquals("expression", target.getEditorEventCues().get(0).getType());
        assertEquals(0, target.getGroup("groupA").getLayerOrder());
        assertEquals(400.0, target.getLoopStartMs(), 0.0001);
        assertEquals(1400.0, target.getLoopEndMs(), 0.0001);
    }

    @Test
    void animationProjectCopiesAndReplacesOrbitAnchorState() {
        AnimationProject project = new AnimationProject();
        project.getOrCreateTrack("hero");
        project.getOrCreateTrack("npc");
        project.setOrbitAnchor("hero", 320.0, 240.0);
        project.setOrbitAnchorSource("hero", "npc");
        project.setOrbitAnchorSourceOffset("hero", 12.5, -8.0);

        AnimationProject copy = project.copy();
        assertTrue(copy.hasOrbitAnchor("hero"));
        assertArrayEquals(new double[]{320.0, 240.0}, copy.getOrbitAnchor("hero"), 0.0001);
        assertEquals("npc", copy.getOrbitAnchorSourcesView().get("hero"));
        assertArrayEquals(new double[]{12.5, -8.0}, copy.getOrbitAnchorSourceOffsetsView().get("hero"), 0.0001);

        AnimationProject replaced = new AnimationProject();
        replaced.replaceFrom(project);
        assertTrue(replaced.hasOrbitAnchor("hero"));
        assertArrayEquals(new double[]{320.0, 240.0}, replaced.getOrbitAnchor("hero"), 0.0001);
        assertEquals("npc", replaced.getOrbitAnchorSourcesView().get("hero"));
        assertArrayEquals(new double[]{12.5, -8.0}, replaced.getOrbitAnchorSourceOffsetsView().get("hero"), 0.0001);
    }

    @Test
    void pruneOrbitAnchorsDropsMissingTargetsAndSources() {
        AnimationProject project = new AnimationProject();
        project.setOrbitAnchor("hero", 10.0, 20.0);
        project.setOrbitAnchor("npc", 30.0, 40.0);
        project.setOrbitAnchorSource("hero", "npc");
        project.setOrbitAnchorSourceOffset("hero", 3.0, -2.0);
        project.setOrbitAnchorSource("npc", "ghost");
        project.setOrbitAnchorSourceOffset("npc", 5.0, 5.0);

        java.util.Set<String> valid = java.util.Set.of("hero", "npc");
        project.pruneOrbitAnchors(valid);

        assertTrue(project.hasOrbitAnchor("hero"));
        assertTrue(project.hasOrbitAnchor("npc"));
        assertEquals("npc", project.getOrbitAnchorSourcesView().get("hero"));
        assertArrayEquals(new double[]{3.0, -2.0}, project.getOrbitAnchorSourceOffsetsView().get("hero"), 0.0001);
        assertFalse(project.getOrbitAnchorSourcesView().containsKey("npc"));
        assertFalse(project.getOrbitAnchorSourceOffsetsView().containsKey("npc"));
    }

    @Test
    void pruneOrbitAnchorsCanRetainGroupTargets() {
        AnimationProject project = new AnimationProject();
        project.getOrCreateGroup("head");
        project.setOrbitAnchor("head", 120.0, 180.0);

        project.pruneOrbitAnchors(java.util.Set.of("head"));

        assertTrue(project.hasOrbitAnchor("head"));
        assertArrayEquals(new double[]{120.0, 180.0}, project.getOrbitAnchor("head"), 0.0001);
    }
}
