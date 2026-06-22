package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AnimationProjectAnchorTest {

  @Test
  void entitiesWithoutAnchorsExposeAnEmptyMap() {
    AnimationProject project = new AnimationProject();
    project.getOrCreateTrack("hero");

    assertTrue(project.getAnchorsForEntity("hero").isEmpty());
    assertTrue(project.getAnchorsForEntity("missing").isEmpty());
    assertTrue(project.getAnchorsForEntity("  ").isEmpty());
  }

  @Test
  void entityAnchorsRemainAvailableThroughTheReadOnlyView() {
    AnimationProject project = new AnimationProject();
    Anchor hand = Anchor.relative("hand", 0.75, 0.5);
    project.setAnchor("hero", hand);

    Map<String, Anchor> anchors = project.getAnchorsForEntity("hero");

    assertEquals(Map.of("hand", hand), anchors);
  }
}
