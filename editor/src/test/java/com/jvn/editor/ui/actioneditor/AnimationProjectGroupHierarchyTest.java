package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
}
