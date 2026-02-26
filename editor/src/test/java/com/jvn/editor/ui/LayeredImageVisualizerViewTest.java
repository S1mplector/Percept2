package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LayeredImageVisualizerViewTest {

  @Test
  void sanitizeIdNormalizesToSnakeCaseLower() {
    assertEquals("body_face_happy", LayeredImageVisualizerView.sanitizeId("Body Face-Happy"));
    assertEquals("eyes_2", LayeredImageVisualizerView.sanitizeId(" Eyes 2 "));
    assertEquals("", LayeredImageVisualizerView.sanitizeId("###"));
  }

  @Test
  void sanitizeLabelKeepsReadableTokens() {
    assertEquals("Body_Face_Happy", LayeredImageVisualizerView.sanitizeLabel("Body Face-Happy"));
    assertEquals("eyes_2", LayeredImageVisualizerView.sanitizeLabel(" eyes 2 "));
  }

  @Test
  void pathHelpersSplitSegmentsSafely() {
    assertEquals("assets/characters/nora", LayeredImageVisualizerView.parentPath("assets/characters/nora/body_base.png"));
    assertEquals("body_base.png", LayeredImageVisualizerView.takeLastPathToken("assets/characters/nora/body_base.png"));
    assertEquals("", LayeredImageVisualizerView.parentPath("single.png"));
    assertEquals("single.png", LayeredImageVisualizerView.takeLastPathToken("single.png"));
  }

  @Test
  void deriveSetIdUsesStableAssetBuckets() {
    assertEquals("assets/characters/nora", LayeredImageVisualizerView.deriveSetIdFromRelative("assets/characters/nora/body/base.png"));
    assertEquals("assets/ui", LayeredImageVisualizerView.deriveSetIdFromRelative("assets/ui/icons/play.png"));
    assertEquals("demo-assets/demo_sprite_codel", LayeredImageVisualizerView.deriveSetIdFromRelative("demo-assets/demo_sprite_codel/Codel1.png"));
    assertEquals("(root)", LayeredImageVisualizerView.deriveSetIdFromRelative("root_image.png"));
  }

  @Test
  void chooseSetSelectionPrefersCharacterSetsByDefault() {
    List<String> visible = List.of("assets/bg", "assets/ui", "assets/characters/nora", "assets/characters/ryan");
    assertEquals(
        "assets/characters/nora",
        LayeredImageVisualizerView.chooseSetSelection(null, "assets/ui", visible));
  }

  @Test
  void chooseSetSelectionKeepsPreviousWhenStillVisible() {
    List<String> visible = List.of("assets/bg", "assets/ui", "assets/characters/nora");
    assertEquals(
        "assets/ui",
        LayeredImageVisualizerView.chooseSetSelection("assets/ui", "assets/characters/nora", visible));
  }

  @Test
  void chooseSetSelectionFallsBackCleanlyWithoutCharacterSets() {
    List<String> visible = List.of("assets/bg", "assets/ui");
    assertEquals("assets/ui", LayeredImageVisualizerView.chooseSetSelection(null, "assets/ui", visible));
    assertEquals("assets/bg", LayeredImageVisualizerView.chooseSetSelection(null, null, visible));
    assertNull(LayeredImageVisualizerView.chooseSetSelection(null, null, List.of()));
  }

  @Test
  void chooseSetSelectionPrefersLayeredSetOverBackgroundWhenNoCharacterSetExists() {
    List<String> visible = List.of("demo-assets/demo_bg_field", "demo-assets/demo_sprite_codel", "demo-assets/Lavender_test_sprite");
    Map<String, Integer> groups = Map.of(
        "demo-assets/demo_bg_field", 1,
        "demo-assets/demo_sprite_codel", 1,
        "demo-assets/Lavender_test_sprite", 3
    );
    assertEquals(
        "demo-assets/Lavender_test_sprite",
        LayeredImageVisualizerView.chooseSetSelection(null, "demo-assets/demo_bg_field", visible, groups));
  }

  @Test
  void inferFolderBasedGroupAndLabelForLayeredSpritePack() {
    String relative = "demo-assets/Lavender_test_sprite/eyes/lavender_test_sprite_eyes_angry.png";
    assertEquals("eyes", LayeredImageVisualizerView.inferGroupFromSetSubfolder(relative));
    assertEquals(
        "angry",
        LayeredImageVisualizerView.inferLabelFromFilenameForGroup("lavender_test_sprite_eyes_angry", "eyes"));
    assertEquals(
        "neutral",
        LayeredImageVisualizerView.inferLabelFromFilenameForGroup("lavender_test_sprite_mouth_neutral", "mouth"));
  }

  @Test
  void defaultOptionScorePrefersNeutralThenDefaultThenBase() {
    assertEquals(0, LayeredImageVisualizerView.defaultOptionScore("neutral"));
    assertEquals(1, LayeredImageVisualizerView.defaultOptionScore("default"));
    assertEquals(2, LayeredImageVisualizerView.defaultOptionScore("base"));
    assertEquals(10, LayeredImageVisualizerView.defaultOptionScore("angry"));
  }
}
