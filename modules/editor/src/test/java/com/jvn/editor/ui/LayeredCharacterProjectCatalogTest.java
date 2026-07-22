package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LayeredCharacterProjectCatalogTest {

  @TempDir
  Path tempDir;

  @Test
  void projectExpressionCatalogExposesLayeredPresetsToPuppeteer() throws Exception {
    Path scripts = Files.createDirectories(tempDir.resolve("scripts/definitions"));
    Files.writeString(scripts.resolve("characters.vns"), """
        @charlayer hero base assets/hero/base.png
        @charlayer hero eyes_happy assets/hero/eyes_happy.png
        @charlayer hero mouth_smile assets/hero/mouth_smile.png
        @charlayer hero mouth_open assets/hero/mouth_open.png
        @charpreset hero happy $base | $eyes_happy | $mouth_smile
        @charpreset hero talking $base | $eyes_happy | $mouth_open
        """);

    Map<String, Map<String, List<LayeredCharacterProjectCatalog.ExpressionLayer>>> catalog =
        LayeredCharacterProjectCatalog.loadExpressionPresets(tempDir.toFile());

    assertEquals(List.of("happy", "talking"), catalog.get("hero").keySet().stream().toList());
    assertEquals(
        List.of("base", "eyes", "mouth"),
        catalog.get("hero").get("talking").stream()
            .map(LayeredCharacterProjectCatalog.ExpressionLayer::layerId)
            .toList());
  }

  @Test
  void parseSourcesSplitsCooccurringBaseLayersIntoTheirOwnGroups() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("definitions/characters.vns", """
        @charlayer john body_default assets/characters/john_doe/body/John_Doe_body_-_default.png
        @charlayer john arm_front_default assets/characters/john_doe/arm front 01/John_Doe_arm_front_01_-_default.png
        @charlayer john eyes_base assets/characters/john_doe/head/eyes/John_Doe_head_-_eyes_base.png
        @charlayer john eyes_05 assets/characters/john_doe/head/eyes/John_Doe_head_-_eyes_05.png
        @charlayer john eyes_06 assets/characters/john_doe/head/eyes/John_Doe_head_-_eyes_06.png
        @charlayer john face_default assets/characters/john_doe/head/faces (+heads)/John_Doe_faces_-_default.png
        @charlayer john mouth_default assets/characters/john_doe/head/mouth/John_Doe_mouth_-_default.png
        @charpreset john neutral $body_default | $arm_front_default | $eyes_base | $eyes_06 | $face_default | $mouth_default
        @charpreset john worried $body_default | $arm_front_default | $eyes_base | $eyes_05 | $face_default | $mouth_default
        """);

    LayeredCharacterProjectCatalog.Catalog catalog = LayeredCharacterProjectCatalog.parseSources(sources);
    LayeredCharacterProjectCatalog.DeclaredSet john = catalog.setsById().get("assets/characters/john_doe");

    assertNotNull(john);
    assertEquals("john", john.characterId());
    assertEquals(List.of("body", "arm_front", "eyes_base", "eyes", "face", "mouth"), john.groupOrder());
    assertEquals(
        List.of("eyes_base"),
        john.groups().get("eyes_base").stream().map(LayeredCharacterProjectCatalog.DeclaredOption::layerId).toList());
    assertEquals(
        List.of("eyes_05", "eyes_06"),
        john.groups().get("eyes").stream().map(LayeredCharacterProjectCatalog.DeclaredOption::layerId).toList());
    assertEquals("neutral", john.defaultPresetName());
    assertEquals(
        Map.of(
            "body", "assets/characters/john_doe/body/John_Doe_body_-_default.png",
            "arm_front", "assets/characters/john_doe/arm front 01/John_Doe_arm_front_01_-_default.png",
            "eyes_base", "assets/characters/john_doe/head/eyes/John_Doe_head_-_eyes_base.png",
            "eyes", "assets/characters/john_doe/head/eyes/John_Doe_head_-_eyes_06.png",
            "face", "assets/characters/john_doe/head/faces (+heads)/John_Doe_faces_-_default.png",
            "mouth", "assets/characters/john_doe/head/mouth/John_Doe_mouth_-_default.png"),
        john.presets().get("neutral").selectionsByGroup());
  }

  @Test
  void parseSourcesResolvesNestedPresetRefs() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("definitions/characters.vns", """
        @charlayer lavender base assets/demo/characters/lavender/base.png
        @charlayer lavender body_school assets/demo/characters/lavender/body_school.png
        @charlayer lavender eyes_half_closed assets/demo/characters/lavender/eyes_half_closed.png
        @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth_smile.png
        @charpreset lavender neutral $base | $body=school | $eyes=half_closed
        @charpreset lavender talking @neutral | $mouth=smile
        """);

    LayeredCharacterProjectCatalog.Catalog catalog = LayeredCharacterProjectCatalog.parseSources(sources);
    LayeredCharacterProjectCatalog.DeclaredSet lavender = catalog.setsById().get("assets/demo/characters/lavender");

    assertNotNull(lavender);
    assertEquals("lavender", lavender.characterId());
    assertTrue(lavender.presets().containsKey("talking"));
    assertEquals(
        Map.of(
            "base", "assets/demo/characters/lavender/base.png",
            "body", "assets/demo/characters/lavender/body_school.png",
            "eyes", "assets/demo/characters/lavender/eyes_half_closed.png",
            "mouth", "assets/demo/characters/lavender/mouth_smile.png"),
        lavender.presets().get("talking").selectionsByGroup());
  }

  @Test
  void parseSourcesResolvesCharacterLayerGroupsInPresets() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("definitions/characters.vns", """
        @charlayer john body_default assets/john/body.png
        @charlayer john head_base assets/john/head.png
        @charlayer john eyes_neutral assets/john/eyes.png
        @charlayer john mouth_smile assets/john/mouth.png
        @chargroup john head $head_base | $eyes_neutral | $mouth_smile
        @charpreset john neutral $body_default | $head
        """);

    LayeredCharacterProjectCatalog.Catalog catalog = LayeredCharacterProjectCatalog.parseSources(sources);
    LayeredCharacterProjectCatalog.DeclaredSet john = catalog.setsById().get("assets/john");

    assertNotNull(john);
    assertTrue(john.presets().containsKey("neutral"));
    assertEquals(
        Map.of(
            "body", "assets/john/body.png",
            "head", "assets/john/head.png",
            "eyes", "assets/john/eyes.png",
            "mouth", "assets/john/mouth.png"),
        john.presets().get("neutral").selectionsByGroup());
  }
}
