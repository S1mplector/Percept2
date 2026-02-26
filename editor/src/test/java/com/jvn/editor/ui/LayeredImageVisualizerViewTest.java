package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
