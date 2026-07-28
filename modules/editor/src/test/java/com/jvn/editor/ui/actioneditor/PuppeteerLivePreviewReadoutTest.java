package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PuppeteerLivePreviewReadoutTest {

    @Test
    void describesActiveExpressionAndUpcomingSelectedKeyframe() {
        AnimationProject project = new AnimationProject();
        project.setName("preview");
        project.setTotalDurationMs(2_000);
        project.setPlayheadMs(500);

        EntityTrack track = new EntityTrack("alice");
        track.addKeyframe(PropertyType.X, new Keyframe(0, 100));
        track.addKeyframe(PropertyType.X, new Keyframe(750, 300));
        project.addTrack(track);
        project.addEditorEventCue(new EditorEventCue(250, "expression", Map.of(
            "target", "alice",
            "value", "smile",
            "layers", "base.png|eyes.png|mouth.png"
        )));

        PuppeteerLivePreviewReadout.Snapshot snapshot = PuppeteerLivePreviewReadout.build(
            project, "alice", false, false, PropertyType.X, false);

        assertEquals("LIVE PREVIEW", snapshot.mode());
        assertEquals("00:00.500 / 00:02.000", snapshot.clock());
        assertTrue(snapshot.target().contains("alice"));
        assertEquals("Expression  •  smile  •  3 layers", snapshot.state());
        assertEquals("Next  •  Position X  in 250 ms", snapshot.upcoming());
        assertEquals(0.25, snapshot.progress(), 0.0001);
    }

    @Test
    void fallsBackGracefullyForEmptyTimelineAndRuntimeCamera() {
        AnimationProject project = new AnimationProject();
        project.setName("empty");
        project.setTotalDurationMs(0);

        PuppeteerLivePreviewReadout.Snapshot snapshot = PuppeteerLivePreviewReadout.build(
            project, TimelinePanel.RUNTIME_CAMERA_TARGET, false, true, PropertyType.CAMERA_ZOOM, true);

        assertEquals("RUNTIME PARITY", snapshot.mode());
        assertEquals("Runtime Camera  •  Camera Zoom", snapshot.target());
        assertEquals("Add a keyframe to begin", snapshot.upcoming());
        assertEquals(0.0, snapshot.progress(), 0.0001);
    }
}
