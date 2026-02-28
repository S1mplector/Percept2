package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

  @Test
  void resolvePresetLayerTagsResolvesDefaultCharacterLayerRefs() {
    Map<String, Map<String, String>> layers = new LinkedHashMap<>();
    layers.put("lavender", Map.of(
        "base", "assets/demo/characters/lavender/base/lavender_base.png",
        "eyes_happy", "assets/demo/characters/lavender/eyes/lavender_eyes_happy.png"));

    List<String> resolved = ImageTintToolView.resolvePresetLayerTags(
        layers,
        "lavender",
        "$base | $eyes_happy");

    assertEquals(List.of(
        "assets/demo/characters/lavender/base/lavender_base.png",
        "assets/demo/characters/lavender/eyes/lavender_eyes_happy.png"), resolved);
  }

  @Test
  void resolvePresetLayerTagsSupportsExplicitCharacterLayerRefsAndPaths() {
    Map<String, Map<String, String>> layers = new LinkedHashMap<>();
    layers.put("lavender", Map.of("base", "assets/demo/characters/lavender/base/lavender_base.png"));
    layers.put("props", Map.of("flower", "assets/demo/props/flower.png"));

    List<String> resolved = ImageTintToolView.resolvePresetLayerTags(
        layers,
        "lavender",
        "$base | $props.flower | assets/demo/fx/glow.png");

    assertEquals(List.of(
        "assets/demo/characters/lavender/base/lavender_base.png",
        "assets/demo/props/flower.png",
        "assets/demo/fx/glow.png"), resolved);
  }

  @Test
  void buildPresetTagUsesStablePrefix() {
    assertEquals("preset:lavender/talking", ImageTintToolView.buildPresetTag("lavender", "talking"));
  }
}
