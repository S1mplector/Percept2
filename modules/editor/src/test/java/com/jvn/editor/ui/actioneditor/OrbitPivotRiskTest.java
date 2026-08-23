package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitPivotRiskTest {

    private static void addRotationKeyframe(AnimationProject project, String entity) {
        EntityTrack track = project.getOrCreateTrack(entity);
        track.addKeyframe(PropertyType.ROTATION, new Keyframe(0.0, 0.0, Easing.Type.LINEAR));
        track.addKeyframe(PropertyType.ROTATION, new Keyframe(500.0, 90.0, Easing.Type.LINEAR));
    }

    private static AnimationProject.SceneEntitySnapshot validSnapshot(String name) {
        return new AnimationProject.SceneEntitySnapshot(
            name, "entity", "", 100.0, 100.0, 64.0, 64.0, 0.5, 0.5, 0.0, true, 1.0);
    }

    @Test
    void noRiskWithoutRotationKeyframes() {
        AnimationProject project = new AnimationProject();
        project.getOrCreateTrack("hero");

        assertFalse(project.isOrbitPivotAtRisk("hero"));
    }

    @Test
    void atRiskWhenRotationKeyframesExistButNoOrbitAnchor() {
        AnimationProject project = new AnimationProject();
        addRotationKeyframe(project, "hero");

        assertTrue(project.isOrbitPivotAtRisk("hero"));
    }

    @Test
    void noRiskWhenAnchorAndSnapshotAreValid() {
        AnimationProject project = new AnimationProject();
        addRotationKeyframe(project, "hero");
        project.setOrbitAnchor("hero", 120.0, 130.0);
        project.setSceneEntitySnapshots(java.util.List.of(validSnapshot("hero")));

        assertFalse(project.isOrbitPivotAtRisk("hero"));
    }

    @Test
    void atRiskWhenAnchorExistsButSnapshotIsMissing() {
        AnimationProject project = new AnimationProject();
        addRotationKeyframe(project, "hero");
        project.setOrbitAnchor("hero", 120.0, 130.0);

        assertTrue(project.isOrbitPivotAtRisk("hero"));
    }

    @Test
    void noRiskWhenSourceLinkedAnchorResolvesToValidSource() {
        AnimationProject project = new AnimationProject();
        addRotationKeyframe(project, "hero");
        project.getOrCreateTrack("torch");
        project.setOrbitAnchor("torch", 50.0, 50.0);
        project.setSceneEntitySnapshots(java.util.List.of(validSnapshot("torch")));
        project.setOrbitAnchorSource("hero", "torch");

        assertFalse(project.isOrbitPivotAtRisk("hero"));
    }

    @Test
    void atRiskWhenSourceLinkedAnchorSourceIsMissing() {
        AnimationProject project = new AnimationProject();
        addRotationKeyframe(project, "hero");
        project.setOrbitAnchor("hero", 10.0, 10.0);
        project.setOrbitAnchorSource("hero", "torch");
        // "torch" has no track/snapshot registered at all.

        assertTrue(project.isOrbitPivotAtRisk("hero"));
    }

    @Test
    void atRiskWhenSourceLinkedAnchorSourceHasNoValidSnapshot() {
        AnimationProject project = new AnimationProject();
        addRotationKeyframe(project, "hero");
        project.setOrbitAnchor("hero", 10.0, 10.0);
        project.getOrCreateTrack("torch");
        project.setOrbitAnchorSource("hero", "torch");
        // "torch" has a track but no scene snapshot.

        assertTrue(project.isOrbitPivotAtRisk("hero"));
    }
}
