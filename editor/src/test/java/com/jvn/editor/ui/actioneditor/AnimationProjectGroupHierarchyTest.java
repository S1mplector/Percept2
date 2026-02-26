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
}
