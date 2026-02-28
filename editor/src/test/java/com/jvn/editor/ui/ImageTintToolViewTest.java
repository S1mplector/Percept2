package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ImageTintToolViewTest {

  @Test
  void pickDefaultCharacterTagPrefersNonBackground() {
    List<String> tags = List.of(
        "assets/demo/backgrounds/field_day.png",
        "assets/demo/characters/lavender/base.png",
        "assets/demo/characters/lavender/eyes_angry.png");

    assertEquals("assets/demo/characters/lavender/base.png", ImageTintToolView.pickDefaultCharacterTag(tags));
  }

  @Test
  void pickDefaultCharacterTagFallsBackToFirstWhenAllBackgrounds() {
    List<String> tags = List.of(
        "assets/demo/backgrounds/field_day.png",
        "assets/demo/backgrounds/field_night.png");

    assertEquals("assets/demo/backgrounds/field_day.png", ImageTintToolView.pickDefaultCharacterTag(tags));
  }

  @Test
  void pickDefaultBackgroundTagPrefersBackgroundLikeTags() {
    List<String> tags = List.of(
        "assets/demo/characters/lavender/base.png",
        "assets/demo/backgrounds/field_day.png",
        "assets/demo/characters/lavender/eyes_smile.png");

    assertEquals("assets/demo/backgrounds/field_day.png", ImageTintToolView.pickDefaultBackgroundTag(tags));
  }

  @Test
  void pickDefaultBackgroundTagReturnsEmptyWhenMissing() {
    List<String> tags = List.of(
        "assets/demo/characters/lavender/base.png",
        "assets/demo/characters/lavender/eyes_smile.png");

    assertEquals("", ImageTintToolView.pickDefaultBackgroundTag(tags));
  }
}
