package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for VnSlotHelper: character placement, expression cue insertion (category F + H).
 */
class VnSlotHelperTest {

    @Test
    void slotXComputesCorrectPositions() {
        double w = 1920;
        assertEquals(192.0, VnSlotHelper.slotX(VnSlotHelper.Slot.FAR_LEFT, w), 0.01);
        assertEquals(480.0, VnSlotHelper.slotX(VnSlotHelper.Slot.LEFT, w), 0.01);
        assertEquals(960.0, VnSlotHelper.slotX(VnSlotHelper.Slot.CENTER, w), 0.01);
        assertEquals(1440.0, VnSlotHelper.slotX(VnSlotHelper.Slot.RIGHT, w), 0.01);
        assertEquals(1728.0, VnSlotHelper.slotX(VnSlotHelper.Slot.FAR_RIGHT, w), 0.01);
    }

    @Test
    void baselineYUsesBottomThird() {
        double h = 1080;
        double y = VnSlotHelper.baselineY(h);
        assertTrue(y > h * 0.5 && y < h, "Baseline should be in lower half");
        assertEquals(702.0, y, 0.01);
    }

    @Test
    void placeAtSlotInsertsKeyframes() {
        AnimationProject project = new AnimationProject();
        VnSlotHelper.placeAtSlot(project, "hero", VnSlotHelper.Slot.CENTER,
            0, 1920, 1080);

        EntityTrack track = project.getTrack("hero");
        assertNotNull(track);
        assertTrue(track.hasKeyframes(PropertyType.X));
        assertTrue(track.hasKeyframes(PropertyType.Y));
        assertEquals(960.0, track.getValueAt(PropertyType.X, 0), 0.01);
        assertEquals(702.0, track.getValueAt(PropertyType.Y, 0), 0.01);
    }

    @Test
    void insertExpressionCueAddsEventToProject() {
        AnimationProject project = new AnimationProject();
        VnSlotHelper.insertExpressionCue(project, "lavender", "smile", 500);

        assertEquals(1, project.getEditorEventCues().size());
        EditorEventCue evt = project.getEditorEventCues().get(0);
        assertEquals("expression", evt.getType());
        assertEquals(500.0, evt.getTimeMs(), 0.01);
        assertEquals("lavender", evt.getPayloadValue("target"));
        assertEquals("smile", evt.getPayloadValue("value"));
    }

    @Test
    void insertExpressionCueCanEmbedLayeredSpritePayload() {
        AnimationProject project = new AnimationProject();
        Map<String, String> layers = new LinkedHashMap<>();
        layers.put("body", "assets/chars/lavender/body.png");
        layers.put("face", "assets/chars/lavender/face_angry.png");

        VnSlotHelper.insertExpressionCue(
            project,
            "lavender",
            "angry",
            750,
            "assets/chars/lavender/body.png | assets/chars/lavender/face_angry.png",
            layers
        );

        EditorEventCue evt = project.getEditorEventCues().get(0);
        assertEquals("expression", evt.getType());
        assertEquals("lavender", evt.getPayloadValue("target"));
        assertEquals("angry", evt.getPayloadValue("value"));
        assertEquals(
            "assets/chars/lavender/body.png | assets/chars/lavender/face_angry.png",
            evt.getPayloadValue("path")
        );
        assertEquals(
            "body=assets/chars/lavender/body.png | face=assets/chars/lavender/face_angry.png",
            evt.getPayloadValue("layers")
        );
    }

    @Test
    void insertDialogueMarkerAddsEventToProject() {
        AnimationProject project = new AnimationProject();
        VnSlotHelper.insertDialogueMarker(project, "intro_beat_3", 1000);

        assertEquals(1, project.getEditorEventCues().size());
        EditorEventCue evt = project.getEditorEventCues().get(0);
        assertEquals("dialogue_marker", evt.getType());
        assertEquals(1000.0, evt.getTimeMs(), 0.01);
        assertEquals("intro_beat_3", evt.getPayloadValue("id"));
    }

    @Test
    void multipleSlotPlacementsOnSameTrack() {
        AnimationProject project = new AnimationProject();
        VnSlotHelper.placeAtSlot(project, "hero", VnSlotHelper.Slot.LEFT, 0, 1920, 1080);
        VnSlotHelper.placeAtSlot(project, "hero", VnSlotHelper.Slot.RIGHT, 500, 1920, 1080);

        EntityTrack track = project.getTrack("hero");
        assertEquals(2, track.getKeyframes(PropertyType.X).size());
        assertEquals(480.0, track.getValueAt(PropertyType.X, 0), 0.01);
        assertEquals(1440.0, track.getValueAt(PropertyType.X, 500), 0.01);
    }
}
