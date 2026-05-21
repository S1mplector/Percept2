package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jvn.core.animation.TimelineData;

class AnimationProjectGroupHierarchyTest {

  @Test
  void nestedGroupsCanBeDetachedBackToRoot() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("character");
    project.getOrCreateGroup("face");

    project.addGroupToGroup("face", "character");

    assertEquals("character", project.getGroup("face").getParentGroupName());
    assertTrue(project.getRootGroupNames().contains("character"));
    assertFalse(project.getRootGroupNames().contains("face"));

    project.removeGroupFromParent("face");

    assertNull(project.getGroup("face").getParentGroupName());
    assertTrue(project.getRootGroupNames().contains("face"));
  }

  @Test
  void childEntityReceivesAccumulatedParentGroupTranslation() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("character");
    project.getOrCreateGroup("face");
    project.addGroupToGroup("face", "character");

    EntityTrack head = project.getOrCreateTrack("head");
    head.upsertKeyframe(PropertyType.X, new Keyframe(0, 100));
    head.upsertKeyframe(PropertyType.Y, new Keyframe(0, 50));
    project.addEntityToGroup("head", "face");

    project.getGroup("character").getGroupTrack().upsertKeyframe(PropertyType.X, new Keyframe(0, 20));
    project.getGroup("character").getGroupTrack().upsertKeyframe(PropertyType.Y, new Keyframe(0, 10));
    project.getGroup("face").getGroupTrack().upsertKeyframe(PropertyType.X, new Keyframe(0, 5));
    project.getGroup("face").getGroupTrack().upsertKeyframe(PropertyType.Y, new Keyframe(0, 3));

    assertEquals(125.0, project.computeValueAt("head", PropertyType.X, 0), 0.0001);
    assertEquals(63.0, project.computeValueAt("head", PropertyType.Y, 0), 0.0001);
  }

  @Test
  void childEntityCombinesInheritedGroupRotationScaleAndAlpha() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("character");
    project.getOrCreateGroup("face");
    project.addGroupToGroup("face", "character");

    EntityTrack head = project.getOrCreateTrack("head");
    project.addEntityToGroup("head", "face");

    project.getGroup("character").getGroupTrack().upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 15));
    project.getGroup("face").getGroupTrack().upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 5));
    project.getGroup("character").getGroupTrack().upsertKeyframe(PropertyType.SCALE_X, new Keyframe(0, 1.5));
    project.getGroup("face").getGroupTrack().upsertKeyframe(PropertyType.SCALE_X, new Keyframe(0, 0.5));
    project.getGroup("character").getGroupTrack().upsertKeyframe(PropertyType.ALPHA, new Keyframe(0, 0.8));
    project.getGroup("face").getGroupTrack().upsertKeyframe(PropertyType.ALPHA, new Keyframe(0, 0.5));

    assertEquals(50.0, project.computeValueAt("head", PropertyType.ROTATION, 0, 30.0), 0.0001);
    assertEquals(1.5, project.computeValueAt("head", PropertyType.SCALE_X, 0, 2.0), 0.0001);
    assertEquals(0.32, project.computeValueAt("head", PropertyType.ALPHA, 0, 0.8), 0.0001);
  }

  @Test
  void removingNestedGroupPromotesChildrenIntoParentGroup() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("character");
    project.getOrCreateGroup("face");
    project.addGroupToGroup("face", "character");

    EntityTrack head = project.getOrCreateTrack("head");
    project.addEntityToGroup("head", "face");

    project.removeGroup("face");

    assertNull(project.getGroup("face"));
    assertEquals("character", head.getParentGroupName());
    assertTrue(project.getGroup("character").getChildEntityNames().contains("head"));
    assertFalse(project.getRootEntityNames().contains("head"));
  }

  @Test
  void renamingGroupPreservesHierarchyAndKeyframes() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("character");
    project.getOrCreateGroup("face");
    project.addGroupToGroup("face", "character");

    EntityTrack head = project.getOrCreateTrack("head");
    project.addEntityToGroup("head", "face");
    project.getGroup("face").getGroupTrack().upsertKeyframe(PropertyType.X, new Keyframe(0, 12));

    assertTrue(project.renameGroup("face", "features"));

    assertNull(project.getGroup("face"));
    assertEquals("features", project.getGroup("features").getName());
    assertEquals("character", project.getGroup("features").getParentGroupName());
    assertTrue(project.getGroup("character").getChildGroupNames().contains("features"));
    assertEquals("features", head.getParentGroupName());
    assertEquals(12.0, project.getGroup("features").getGroupTrack().getValueAt(PropertyType.X, 0), 0.0001);
  }

  @Test
  void groupRotationMovesLayerAroundSharedRigPivot() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("hero");

    EntityTrack anchor = project.getOrCreateTrack("anchor");
    anchor.upsertKeyframe(PropertyType.X, new Keyframe(0, 0));
    anchor.upsertKeyframe(PropertyType.Y, new Keyframe(0, 0));
    EntityTrack hand = project.getOrCreateTrack("hand");
    hand.upsertKeyframe(PropertyType.X, new Keyframe(0, 100));
    hand.upsertKeyframe(PropertyType.Y, new Keyframe(0, 0));
    project.addEntityToGroup("anchor", "hero");
    project.addEntityToGroup("hand", "hero");

    project.getGroup("hero").getGroupTrack().upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 90));

    AnimationProject.EffectiveEntityTransform transform = project.computeEffectiveEntityTransform("hand", 0);

    assertEquals(50.0, transform.x(), 0.0001);
    assertEquals(50.0, transform.y(), 0.0001);
    assertEquals(50.0, project.computeValueAt("hand", PropertyType.X, 0), 0.0001);
    assertEquals(50.0, project.computeValueAt("hand", PropertyType.Y, 0), 0.0001);
  }

  @Test
  void groupPivotKeyframesControlSharedRigRotationCenter() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("hero");

    EntityTrack anchor = project.getOrCreateTrack("anchor");
    anchor.upsertKeyframe(PropertyType.X, new Keyframe(0, 0));
    anchor.upsertKeyframe(PropertyType.Y, new Keyframe(0, 0));
    EntityTrack hand = project.getOrCreateTrack("hand");
    hand.upsertKeyframe(PropertyType.X, new Keyframe(0, 100));
    hand.upsertKeyframe(PropertyType.Y, new Keyframe(0, 0));
    project.addEntityToGroup("anchor", "hero");
    project.addEntityToGroup("hand", "hero");

    EntityTrack groupTrack = project.getGroup("hero").getGroupTrack();
    groupTrack.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(0, 0));
    groupTrack.upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 90));

    AnimationProject.EffectiveEntityTransform transform = project.computeEffectiveEntityTransform("hand", 0);

    assertEquals(0.0, transform.x(), 0.0001);
    assertEquals(100.0, transform.y(), 0.0001);
  }

  @Test
  void groupPivotUsesVisualSnapshotBoundsForPaddedSprites() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("head");
    project.getOrCreateTrack("head_layer");
    project.addEntityToGroup("head_layer", "head");
    project.setSceneEntitySnapshots(List.of(
        new AnimationProject.SceneEntitySnapshot(
            "head_layer",
            "sprite",
            "assets/head.png",
            100,
            100,
            200,
            200,
            0,
            0,
            0,
            true,
            1,
            150,
            150,
            170,
            250
        )
    ));

    EntityTrack headTrack = project.getGroup("head").getGroupTrack();
    headTrack.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(0, 0));
    headTrack.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(0, 1));
    headTrack.upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 90));

    AnimationProject.EffectiveEntityTransform transform =
        project.computeEffectiveEntityTransform("head_layer", 0);

    assertEquals(300.0, transform.x(), 0.0001);
    assertEquals(200.0, transform.y(), 0.0001);
    double[] center = project.computeGroupRotationCenter("head", 0);
    assertEquals(150.0, center[0], 0.0001);
    assertEquals(250.0, center[1], 0.0001);
  }

  @Test
  void groupRotationCenterUsesPivotAndGroupTranslation() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("arm");
    project.getOrCreateTrack("hand");
    project.addEntityToGroup("hand", "arm");
    project.setSceneEntitySnapshots(List.of(
        new AnimationProject.SceneEntitySnapshot("hand", "sprite", "", 100, 40, 20, 10, 0.5, 0.5, 0, true, 1)
    ));

    EntityTrack armTrack = project.getGroup("arm").getGroupTrack();
    armTrack.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(0, 0));
    armTrack.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(0, 0));
    armTrack.upsertKeyframe(PropertyType.X, new Keyframe(0, 20));
    armTrack.upsertKeyframe(PropertyType.Y, new Keyframe(0, 5));

    double[] center = project.computeGroupRotationCenter("arm", 0);

    assertEquals(110.0, center[0], 0.0001);
    assertEquals(40.0, center[1], 0.0001);
  }

  @Test
  void nestedGroupRotationCenterIsTransformedByParentGroup() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("body");
    project.getOrCreateGroup("arm");
    project.addGroupToGroup("arm", "body");
    project.getOrCreateTrack("anchor");
    project.getOrCreateTrack("hand");
    project.addEntityToGroup("anchor", "body");
    project.addEntityToGroup("hand", "arm");
    project.setSceneEntitySnapshots(List.of(
        new AnimationProject.SceneEntitySnapshot("anchor", "sprite", "", 0, 0, 1, 1, 0, 0, 0, true, 1),
        new AnimationProject.SceneEntitySnapshot("hand", "sprite", "", 100, 0, 1, 1, 0, 0, 0, true, 1)
    ));

    EntityTrack armTrack = project.getGroup("arm").getGroupTrack();
    armTrack.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(0, 0));
    armTrack.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(0, 0));

    EntityTrack bodyTrack = project.getGroup("body").getGroupTrack();
    bodyTrack.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(0, 0));
    bodyTrack.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(0, 0));
    bodyTrack.upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 90));

    double[] center = project.computeGroupRotationCenter("arm", 0);

    assertEquals(0.0, center[0], 0.0001);
    assertEquals(100.0, center[1], 0.0001);
  }

  @Test
  void groupTransformKeepsChildLayerMotionLocalBeforeParentMotion() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("hero");

    EntityTrack anchor = project.getOrCreateTrack("anchor");
    anchor.upsertKeyframe(PropertyType.X, new Keyframe(0, 0));
    anchor.upsertKeyframe(PropertyType.Y, new Keyframe(0, 0));
    EntityTrack hand = project.getOrCreateTrack("hand");
    hand.upsertKeyframe(PropertyType.X, new Keyframe(0, 100));
    hand.upsertKeyframe(PropertyType.Y, new Keyframe(0, 20));
    project.addEntityToGroup("anchor", "hero");
    project.addEntityToGroup("hand", "hero");

    project.getGroup("hero").getGroupTrack().upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 90));

    AnimationProject.EffectiveEntityTransform transform = project.computeEffectiveEntityTransform("hand", 0);

    assertEquals(40.0, transform.x(), 0.0001);
    assertEquals(60.0, transform.y(), 0.0001);
  }

  @Test
  void groupMotionUsesSceneSnapshotRestPoseWhenLayerHasNoPositionKeyframes() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("hero");
    project.getOrCreateTrack("anchor");
    project.getOrCreateTrack("hand");
    project.addEntityToGroup("anchor", "hero");
    project.addEntityToGroup("hand", "hero");
    project.setSceneEntitySnapshots(List.of(
        new AnimationProject.SceneEntitySnapshot("anchor", "sprite", "", 0, 0, 1, 1, 0, 0, 0, true, 1),
        new AnimationProject.SceneEntitySnapshot("hand", "sprite", "", 100, 0, 1, 1, 0, 0, 0, true, 1)
    ));

    project.getGroup("hero").getGroupTrack().upsertKeyframe(PropertyType.X, new Keyframe(0, 20));

    assertEquals(120.0, project.computeValueAt("hand", PropertyType.X, 0), 0.0001);
    assertEquals(120.0, project.computeEffectiveEntityTransform("hand", 0).x(), 0.0001);
  }

  @Test
  void effectivePositionKeyframesBakeCurvedGroupRotationSamples() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("hero");

    EntityTrack anchor = project.getOrCreateTrack("anchor");
    anchor.upsertKeyframe(PropertyType.X, new Keyframe(0, 0));
    anchor.upsertKeyframe(PropertyType.Y, new Keyframe(0, 0));
    EntityTrack hand = project.getOrCreateTrack("hand");
    hand.upsertKeyframe(PropertyType.X, new Keyframe(0, 100));
    hand.upsertKeyframe(PropertyType.Y, new Keyframe(0, 0));
    project.addEntityToGroup("anchor", "hero");
    project.addEntityToGroup("hand", "hero");

    EntityTrack groupTrack = project.getGroup("hero").getGroupTrack();
    groupTrack.upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 0));
    groupTrack.upsertKeyframe(PropertyType.ROTATION, new Keyframe(300, 90));

    List<Double> times = project.getEffectiveKeyframes("hand", PropertyType.X).stream()
        .map(Keyframe::getTimeMs)
        .toList();

    assertTrue(times.contains(0.0));
    assertTrue(times.contains(100.0));
    assertTrue(times.contains(200.0));
    assertTrue(times.contains(300.0));
  }

  @Test
  void timelineDataBakesGroupRotationIntoChildRuntimeTrack() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("hero");

    EntityTrack anchor = project.getOrCreateTrack("anchor");
    anchor.upsertKeyframe(PropertyType.X, new Keyframe(0, 0));
    anchor.upsertKeyframe(PropertyType.Y, new Keyframe(0, 0));
    EntityTrack hand = project.getOrCreateTrack("hand");
    hand.upsertKeyframe(PropertyType.X, new Keyframe(0, 100));
    hand.upsertKeyframe(PropertyType.Y, new Keyframe(0, 0));
    project.addEntityToGroup("anchor", "hero");
    project.addEntityToGroup("hand", "hero");

    EntityTrack groupTrack = project.getGroup("hero").getGroupTrack();
    groupTrack.upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 0));
    groupTrack.upsertKeyframe(PropertyType.ROTATION, new Keyframe(300, 90));

    TimelineData.Track runtimeHand = project.toTimelineData("hero_wave").getTrack("hand");

    assertEquals(50.0, runtimeHand.getValueAt(TimelineData.Property.X, 300), 0.0001);
    assertEquals(50.0, runtimeHand.getValueAt(TimelineData.Property.Y, 300), 0.0001);
    assertTrue(runtimeHand.getKeyframes(TimelineData.Property.X).size() >= 4);
  }

  @Test
  void parentChildConstraintFollowsEffectiveGroupedParentTransform() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateGroup("hero");

    EntityTrack shoulder = project.getOrCreateTrack("shoulder");
    shoulder.upsertKeyframe(PropertyType.X, new Keyframe(0, 100));
    shoulder.upsertKeyframe(PropertyType.Y, new Keyframe(0, 40));
    project.addEntityToGroup("shoulder", "hero");
    project.getGroup("hero").getGroupTrack().upsertKeyframe(PropertyType.X, new Keyframe(0, 20));
    project.getGroup("hero").getGroupTrack().upsertKeyframe(PropertyType.Y, new Keyframe(0, 5));

    ConstraintEvaluator.ConstrainedTransform transform = ConstraintEvaluator.evaluate(
        "hand",
        0,
        0,
        0,
        1,
        1,
        Constraint.parentChild("shoulder", 10, 3, true, true),
        project,
        0
    );

    assertEquals(130.0, transform.x, 0.0001);
    assertEquals(48.0, transform.y, 0.0001);
  }

  @Test
  void parentChildConstraintParticipatesInEffectiveTransformAndRuntimeBake() {
    AnimationProject project = new AnimationProject();
    EntityTrack shoulder = project.getOrCreateTrack("shoulder");
    shoulder.upsertKeyframe(PropertyType.X, new Keyframe(0, 100));
    shoulder.upsertKeyframe(PropertyType.Y, new Keyframe(0, 40));
    shoulder.upsertKeyframe(PropertyType.X, new Keyframe(300, 140));
    shoulder.upsertKeyframe(PropertyType.Y, new Keyframe(300, 50));
    project.getOrCreateTrack("hair_back");
    project.setConstraint("hair_back", Constraint.parentChild("shoulder", -8, 3, true, true));

    AnimationProject.EffectiveEntityTransform transform =
        project.computeEffectiveEntityTransform("hair_back", 300);

    assertEquals(132.0, transform.x(), 0.0001);
    assertEquals(53.0, transform.y(), 0.0001);

    TimelineData.Track baked = project.toTimelineData("linked_layers").getTrack("hair_back");
    assertNotNull(baked);
    assertEquals(132.0, baked.getValueAt(TimelineData.Property.X, 300), 0.0001);
    assertEquals(53.0, baked.getValueAt(TimelineData.Property.Y, 300), 0.0001);
  }
}
