package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ImageAttributesToolViewTest {

  @Test
  void deriveTagIdUsesStableBuckets() {
    assertEquals("assets/characters/lavender", ImageAttributesToolView.deriveTagIdFromRelative("assets/characters/lavender/base/body.png"));
    assertEquals("assets/ui", ImageAttributesToolView.deriveTagIdFromRelative("assets/ui/icons/play.png"));
    assertEquals("demo-assets/Lavender_test_sprite", ImageAttributesToolView.deriveTagIdFromRelative("demo-assets/Lavender_test_sprite/eyes/e1.png"));
    assertEquals("(root)", ImageAttributesToolView.deriveTagIdFromRelative("single.png"));
  }

  @Test
  void inferGroupFromTagSubfolderUsesRemainderFolder() {
    assertEquals(
        "eyes",
        ImageAttributesToolView.inferGroupFromTagSubfolder(
            "demo-assets/Lavender_test_sprite/eyes/lavender_test_sprite_eyes_angry.png",
            "demo-assets/Lavender_test_sprite"));
    assertEquals(
        "",
        ImageAttributesToolView.inferGroupFromTagSubfolder(
            "assets/characters/lavender.png",
            "assets/characters/lavender"));
  }

  @Test
  void inferLabelFromFilenameUsesGroupTokenWhenPresent() {
    assertEquals("angry", ImageAttributesToolView.inferLabelFromFilenameForGroup("lavender_test_sprite_eyes_angry", "eyes"));
    assertEquals("smile", ImageAttributesToolView.inferLabelFromFilenameForGroup("lavender_test_sprite_mouth_smile", "mouth"));
    assertEquals("default", ImageAttributesToolView.inferLabelFromFilenameForGroup("lavender_base_default", "base"));
  }

  @Test
  void parseAttributeAssignmentsSupportsEqualColonAndUnderscore() {
    Map<String, String> parsed = ImageAttributesToolView.parseAttributeAssignments("eyes=angry mouth:smile hair_long ignoredtoken");
    assertEquals(
        Map.of(
            "eyes", "angry",
            "mouth", "smile",
            "hair", "long"),
        parsed);
  }

  @Test
  void parseAttributeShortformsIgnoresCommentsAndInvalidLines() {
    String text = """
        # comment
        happy = eyes=neutral mouth=happy

        serious=eyes=cross_closed mouth=neutral
        invalid line
        """;

    Map<String, String> parsed = ImageAttributesToolView.parseAttributeShortforms(text);
    assertEquals(
        Map.of(
            "happy", "eyes=neutral mouth=happy",
            "serious", "eyes=cross_closed mouth=neutral"),
        parsed);
  }

  @Test
  void defaultOptionScoreKeepsNeutralPreferred() {
    assertEquals(0, ImageAttributesToolView.defaultOptionScore("neutral"));
    assertEquals(1, ImageAttributesToolView.defaultOptionScore("default"));
    assertEquals(2, ImageAttributesToolView.defaultOptionScore("base"));
    assertEquals(10, ImageAttributesToolView.defaultOptionScore("angry"));
  }
}
