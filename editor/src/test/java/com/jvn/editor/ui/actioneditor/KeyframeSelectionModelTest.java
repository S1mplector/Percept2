package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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

        assertEquals(3, model.getSelectionCount());
    }

    @Test
    void boxSelectAdditivePreservesExisting() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.X, new Keyframe(100, 0));
        track.addKeyframe(PropertyType.X, new Keyframe(300, 50));

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.boxSelect(project, null, 50, 150, false);
        assertEquals(1, model.getSelectionCount());

        model.boxSelect(project, null, 250, 350, true);
        assertEquals(2, model.getSelectionCount());
    }

    @Test
    void boxSelectFiltersByEntity() {
        AnimationProject project = new AnimationProject();
        EntityTrack trackA = project.getOrCreateTrack("a");
        Keyframe ax = new Keyframe(100, 0);
        trackA.addKeyframe(PropertyType.X, ax);
        EntityTrack trackB = project.getOrCreateTrack("b");
        Keyframe bx = new Keyframe(100, 0);
        trackB.addKeyframe(PropertyType.X, bx);

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.boxSelect(project, "a", 0, 200, false);

        assertEquals(1, model.getSelectionCount());
        assertTrue(model.isSelected(KeyframeSelectionModel.ref("a", PropertyType.X, ax)));
        assertFalse(model.isSelected(KeyframeSelectionModel.ref("b", PropertyType.X, bx)));
    }

    @Test
    void moveSelectedShiftsKeyframeTimes() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.X, new Keyframe(100, 0));
        Keyframe selected = new Keyframe(200, 50);
        track.addKeyframe(PropertyType.X, selected);
        track.addKeyframe(PropertyType.X, new Keyframe(400, 100));

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, selected));

        model.moveSelected(project, 50, 0);

        assertEquals(250.0, selected.getTimeMs(), 0.01);
        assertTrue(model.isSelected(KeyframeSelectionModel.ref("sprite", PropertyType.X, selected)));
    }

    @Test
    void rippleRetimeShiftsSubsequentKeyframes() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.X, new Keyframe(100, 0));
        Keyframe selected = new Keyframe(200, 50);
        track.addKeyframe(PropertyType.X, selected);
        Keyframe trailing = new Keyframe(400, 100);
        track.addKeyframe(PropertyType.X, trailing);

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.setRippleRetimeEnabled(true);
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, selected));

        model.moveSelected(project, 50, 0);

        assertEquals(250.0, selected.getTimeMs(), 0.01);
        assertEquals(450.0, trailing.getTimeMs(), 0.01);
    }

    @Test
    void reverseSelectedMirrorsTimesAcrossRange() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        Keyframe a = new Keyframe(100, 0);
        Keyframe b = new Keyframe(200, 50);
        Keyframe c = new Keyframe(400, 100);
        track.addKeyframe(PropertyType.X, a);
        track.addKeyframe(PropertyType.X, b);
        track.addKeyframe(PropertyType.X, c);

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, a));
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, b));
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, c));

        model.reverseSelected(project, 0);

        assertEquals(400.0, a.getTimeMs(), 0.01);
        assertEquals(300.0, b.getTimeMs(), 0.01);
        assertEquals(100.0, c.getTimeMs(), 0.01);
    }

    @Test
    void distributeSelectedSpreadsKeysAcrossExistingRange() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        Keyframe a = new Keyframe(100, 0);
        Keyframe b = new Keyframe(160, 50);
        Keyframe c = new Keyframe(400, 100);
        track.addKeyframe(PropertyType.X, a);
        track.addKeyframe(PropertyType.X, b);
        track.addKeyframe(PropertyType.X, c);

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, a));
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, b));
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, c));

        model.distributeSelected(project, 0);

        assertEquals(100.0, a.getTimeMs(), 0.01);
        assertEquals(250.0, b.getTimeMs(), 0.01);
        assertEquals(400.0, c.getTimeMs(), 0.01);
    }

    @Test
    void stretchSelectedScalesOffsetsFromFirstKey() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        Keyframe a = new Keyframe(100, 0);
        Keyframe b = new Keyframe(200, 50);
        Keyframe c = new Keyframe(300, 100);
        track.addKeyframe(PropertyType.X, a);
        track.addKeyframe(PropertyType.X, b);
        track.addKeyframe(PropertyType.X, c);

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, a));
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, b));
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, c));

        model.stretchSelected(project, 1.5, 0);

        assertEquals(100.0, a.getTimeMs(), 0.01);
        assertEquals(250.0, b.getTimeMs(), 0.01);
        assertEquals(400.0, c.getTimeMs(), 0.01);
    }

    @Test
    void channelVisibilityFilterToggle() {
        KeyframeSelectionModel model = new KeyframeSelectionModel();

        assertTrue(model.isCategoryVisible(KeyframeSelectionModel.ChannelCategory.TRANSFORM));
        assertTrue(model.isCategoryVisible(KeyframeSelectionModel.ChannelCategory.CAMERA));

        model.setVisibleCategory(KeyframeSelectionModel.ChannelCategory.CAMERA, false);
        assertFalse(model.isCategoryVisible(KeyframeSelectionModel.ChannelCategory.CAMERA));
        assertTrue(model.isCategoryVisible(KeyframeSelectionModel.ChannelCategory.TRANSFORM));
        assertFalse(model.isCategoryVisible(PropertyType.CAMERA_X));
        assertFalse(model.isCategoryVisible(PropertyType.CAMERA_ZOOM));
        assertTrue(model.isCategoryVisible(PropertyType.X));
        assertTrue(model.isCategoryVisible(PropertyType.ALPHA));
    }

    @Test
    void boxSelectRespectsChannelFilter() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        Keyframe transform = new Keyframe(100, 0);
        Keyframe camera = new Keyframe(100, 0);
        track.addKeyframe(PropertyType.X, transform);
        track.addKeyframe(PropertyType.CAMERA_X, camera);

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.setVisibleCategory(KeyframeSelectionModel.ChannelCategory.CAMERA, false);

        model.boxSelect(project, null, 0, 200, false);

        assertEquals(1, model.getSelectionCount());
        assertTrue(model.isSelected(KeyframeSelectionModel.ref("sprite", PropertyType.X, transform)));
        assertFalse(model.isSelected(KeyframeSelectionModel.ref("sprite", PropertyType.CAMERA_X, camera)));
    }

    @Test
    void moveWithSnappingAlignsToGrid() {
        AnimationProject project = new AnimationProject();
        EntityTrack track = project.getOrCreateTrack("sprite");
        Keyframe keyframe = new Keyframe(100, 0);
        track.addKeyframe(PropertyType.X, keyframe);

        KeyframeSelectionModel model = new KeyframeSelectionModel();
        model.select(KeyframeSelectionModel.ref("sprite", PropertyType.X, keyframe));

        model.moveSelected(project, 37, 25);

        assertEquals(0.0, keyframe.getTimeMs() % 25, 0.01);
    }
}
