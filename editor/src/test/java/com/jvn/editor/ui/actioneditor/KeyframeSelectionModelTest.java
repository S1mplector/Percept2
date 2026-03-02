package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for KeyframeSelectionModel: box select, ripple retime, channel filters (category C + H).
 */
class KeyframeSelectionModelTest {

    @Test
    void boxSelectFindsKeyframesInTimeRange() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.X, new Keyframe(100, 0));
        track.addKeyframe(PropertyType.X, new Keyframe(200, 50));
        track.addKeyframe(PropertyType.X, new Keyframe(400, 100));
        track.addKeyframe(PropertyType.Y, new Keyframe(150, 10));
        track.addKeyframe(PropertyType.Y, new Keyframe(350, 80));

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.boxSelect(project, null, 100, 250, false);

        // Should select X@100, X@200, Y@150
        assertEquals(3, model.getSelectionCount());
    }

    @Test
    void boxSelectAdditivePreservesExisting() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.X, new Keyframe(100, 0));
        track.addKeyframe(PropertyType.X, new Keyframe(300, 50));

        KeyframeSelectionModel model = new KeyframeSelectionModel();

        // First selection
        model.boxSelect(project, null, 50, 150, false);
        assertEquals(1, model.getSelectionCount());

        // Additive selection
        model.boxSelect(project, null, 250, 350, true);
        assertEquals(2, model.getSelectionCount());
    }

    @Test
    void boxSelectFiltersByEntity() {
        AnimationProject project = new AnimationProject();
        EntityTrack trackA = project.getOrCreateTrack("a");
        trackA.addKeyframe(PropertyType.X, new Keyframe(100, 0));
        EntityTrack trackB = project.getOrCreateTrack("b");
        trackB.addKeyframe(PropertyType.X, new Keyframe(100, 0));

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.boxSelect(project, "a", 0, 200, false);

        assertEquals(1, model.getSelectionCount());
        assertTrue(model.isSelected(
            new KeyframeSelectionModel.KeyframeRef("a", PropertyType.X, 0)));
        assertFalse(model.isSelected(
            new KeyframeSelectionModel.KeyframeRef("b", PropertyType.X, 0)));
    }

    @Test
    void moveSelectedShiftsKeyframeTimes() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.X, new Keyframe(100, 0));
        track.addKeyframe(PropertyType.X, new Keyframe(200, 50));
        track.addKeyframe(PropertyType.X, new Keyframe(400, 100));

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.select(new KeyframeSelectionModel.KeyframeRef("sprite", PropertyType.X, 1));

        model.moveSelected(project, 50, 0);

        // Keyframe at index 1 should now be at 250
        assertEquals(250.0, track.getKeyframes(PropertyType.X).get(1).getTimeMs(), 0.01);
        // Others unchanged
        assertEquals(100.0, track.getKeyframes(PropertyType.X).get(0).getTimeMs(), 0.01);
        assertEquals(400.0, track.getKeyframes(PropertyType.X).get(2).getTimeMs(), 0.01);
    }

    @Test
    void rippleRetimeShiftsSubsequentKeyframes() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.X, new Keyframe(100, 0));
        track.addKeyframe(PropertyType.X, new Keyframe(200, 50));
        track.addKeyframe(PropertyType.X, new Keyframe(400, 100));

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.setRippleRetimeEnabled(true);
        model.select(new KeyframeSelectionModel.KeyframeRef("sprite", PropertyType.X, 1));

        model.moveSelected(project, 50, 0);

        // Selected keyframe moved to 250
        assertEquals(250.0, track.getKeyframes(PropertyType.X).get(1).getTimeMs(), 0.01);
        // Subsequent keyframe at 400 should also shift by 50 -> 450
        assertEquals(450.0, track.getKeyframes(PropertyType.X).get(2).getTimeMs(), 0.01);
        // Prior keyframe unchanged
        assertEquals(100.0, track.getKeyframes(PropertyType.X).get(0).getTimeMs(), 0.01);
    }

    @Test
    void channelVisibilityFilterToggle() {
        KeyframeSelectionModel model = new KeyframeSelectionModel();

        // All visible by default
        assertTrue(model.isCategoryVisible(KeyframeSelectionModel.ChannelCategory.TRANSFORM));
        assertTrue(model.isCategoryVisible(KeyframeSelectionModel.ChannelCategory.CAMERA));

        model.setVisibleCategory(KeyframeSelectionModel.ChannelCategory.CAMERA, false);
        assertFalse(model.isCategoryVisible(KeyframeSelectionModel.ChannelCategory.CAMERA));
        assertTrue(model.isCategoryVisible(KeyframeSelectionModel.ChannelCategory.TRANSFORM));

        // Camera properties should not be visible
        assertFalse(model.isCategoryVisible(PropertyType.CAMERA_X));
        assertFalse(model.isCategoryVisible(PropertyType.CAMERA_ZOOM));
        // Transform properties should still be visible
        assertTrue(model.isCategoryVisible(PropertyType.X));
        assertTrue(model.isCategoryVisible(PropertyType.ALPHA));
    }

    @Test
    void boxSelectRespectsChannelFilter() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.X, new Keyframe(100, 0));
        track.addKeyframe(PropertyType.CAMERA_X, new Keyframe(100, 0));

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.setVisibleCategory(KeyframeSelectionModel.ChannelCategory.CAMERA, false);

        model.boxSelect(project, null, 0, 200, false);

        // Only transform channel should be selected
        assertEquals(1, model.getSelectionCount());
        assertTrue(model.isSelected(
            new KeyframeSelectionModel.KeyframeRef("sprite", PropertyType.X, 0)));
    }

    @Test
    void moveWithSnapping() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.X, new Keyframe(100, 0));

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.select(new KeyframeSelectionModel.KeyframeRef("sprite", PropertyType.X, 0));

        model.moveSelected(project, 37, 25); // snap to 25ms grid

        double time = track.getKeyframes(PropertyType.X).get(0).getTimeMs();
        // 100 + 37 = 137, snapped to 25 -> 125 or 150
        assertEquals(0.0, time % 25, 0.01, "Time should be snapped to 25ms grid");
    }
}
