package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
}
