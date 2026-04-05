package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import javafx.scene.image.WritableImage;

class ImageTintToolViewTest {

  @Test
  void buildDefaultTintExportStemHandlesPresetAndAssetTags() {
    assertEquals("lavender_neutral_tint", ImageTintToolView.buildDefaultTintExportStem("preset:lavender/neutral"));
    assertEquals("lavender_base_tint", ImageTintToolView.buildDefaultTintExportStem("assets/demo/characters/lavender/base.png"));
    assertEquals("lavender_base_tint", ImageTintToolView.buildDefaultTintExportStem(
        "assets/demo/characters/lavender/base/lavender_base.png"));
  }

  @Test
  void buildExportFileNameAvoidsDuplicateExtensions() {
    assertEquals("john_tint.png", ImageTintToolView.buildExportFileName("john_tint", "png"));
    assertEquals("john_tint.png", ImageTintToolView.buildExportFileName("john_tint.png", "png"));
  }

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

  @Test
  void filterCharacterTagsForScopeSupportsPresetAndCharacterModes() {
    List<String> imageTags = List.of(
        "assets/demo/backgrounds/field_day.png",
        "assets/demo/characters/lavender/base.png",
        "assets/demo/characters/lavender/eyes_happy.png");
    List<String> presetTags = List.of(
        "preset:lavender/neutral",
        "preset:lavender/talking");

    assertEquals(
        List.of("preset:lavender/neutral", "preset:lavender/talking"),
        ImageTintToolView.filterCharacterTagsForScope(imageTags, presetTags, "Charpresets only"));
    assertEquals(
        List.of(
            "assets/demo/characters/lavender/base.png",
            "assets/demo/characters/lavender/eyes_happy.png"),
        ImageTintToolView.filterCharacterTagsForScope(imageTags, presetTags, "Character assets only"));
    assertEquals(
        List.of(
            "assets/demo/backgrounds/field_day.png",
            "assets/demo/characters/lavender/base.png",
            "assets/demo/characters/lavender/eyes_happy.png",
            "preset:lavender/neutral",
            "preset:lavender/talking"),
        ImageTintToolView.filterCharacterTagsForScope(imageTags, presetTags, "All image assets + charpresets"));
  }

  @Test
  void matchesTagSearchSupportsScopedTokens() {
    assertTrue(ImageTintToolView.matchesTagSearch("preset:john/neutral", "preset:john/neut"));
    assertTrue(ImageTintToolView.matchesTagSearch("assets/characters/john_doe/base.png", "character:john"));
    assertTrue(ImageTintToolView.matchesTagSearch("assets/backgrounds/field_day.png", "bg:field"));
    assertEquals(false, ImageTintToolView.matchesTagSearch("assets/backgrounds/field_day.png", "character:john"));
    assertEquals(false, ImageTintToolView.matchesTagSearch("preset:john/neutral", "asset:john"));
  }

  @Test
  void describeAssetTagFormatsPresetAndCompactAssetLabels() {
    assertEquals(
        "Preset · lavender/neutral",
        ImageTintToolView.describeAssetTag("preset:lavender/neutral", true));
    assertEquals(
        ".../characters/lavender/base/lavender_test_sprite_base.png",
        ImageTintToolView.describeAssetTag(
            "assets/demo/characters/lavender/base/lavender_test_sprite_base.png",
            true));
    assertEquals(
        "assets/demo/characters/lavender/base/lavender_test_sprite_base.png",
        ImageTintToolView.describeAssetTag(
            "assets/demo/characters/lavender/base/lavender_test_sprite_base.png",
            false));
  }

  @Test
  void filterAssetDropdownItemsUsesSearchTokensAndPreservesOrder() {
    List<String> items = List.of(
        "assets/demo/backgrounds/field_day.png",
        "assets/demo/characters/lavender/base.png",
        "preset:lavender/neutral",
        "assets/demo/characters/john/base.png");

    assertEquals(
        List.of(
            "assets/demo/characters/lavender/base.png",
            "preset:lavender/neutral"),
        ImageTintToolView.filterAssetDropdownItems(items, "lavender"));
    assertEquals(
        List.of("assets/demo/backgrounds/field_day.png"),
        ImageTintToolView.filterAssetDropdownItems(items, "bg:field"));
  }

  @Test
  void smoothFreehandStrokeBuildsStableClosedShapeFromNoisyRectangle() {
    List<double[]> stroke = new ArrayList<>();
    for (int i = 0; i <= 40; i++) stroke.add(new double[]{40 + i * 3, 40 + Math.sin(i * 0.25)});
    for (int i = 0; i <= 28; i++) stroke.add(new double[]{160 + Math.cos(i * 0.2), 40 + i * 3});
    for (int i = 0; i <= 40; i++) stroke.add(new double[]{160 - i * 3, 124 + Math.sin(i * 0.2)});
    for (int i = 0; i <= 28; i++) stroke.add(new double[]{39 + Math.cos(i * 0.2), 124 - i * 3});

    List<double[]> smoothed = ImageTintToolView.smoothFreehandStroke(
        stroke, 12.0, 2.4, 1.8, 0.9, 220);

    assertTrue(smoothed.size() >= 8, "Expected enough vertices for a polygonal shape");
    assertTrue(smoothed.size() <= 220, "Expected max vertex guard");
    double area = polygonAreaAbs(smoothed);
    assertTrue(area > 6000.0 && area < 11000.0, "Expected stable area, got " + area);
  }

  @Test
  void smoothFreehandStrokeReturnsEmptyForDegenerateInput() {
    List<double[]> stroke = List.of(
        new double[]{10, 10},
        new double[]{10.2, 10.1},
        new double[]{10.3, 10.1}
    );
    List<double[]> smoothed = ImageTintToolView.smoothFreehandStroke(
        stroke, 12.0, 2.4, 1.8, 0.9, 220);
    assertTrue(smoothed.isEmpty());
  }

  @Test
  void smoothFreehandStrokeRespectsVertexCap() {
    List<double[]> stroke = new ArrayList<>();
    for (int i = 0; i < 1200; i++) {
      double a = i * (Math.PI * 2.0 / 1200.0);
      double r = 80.0 + Math.sin(i * 0.17) * 2.0;
      stroke.add(new double[]{
          200.0 + Math.cos(a) * r,
          180.0 + Math.sin(a) * r
      });
    }
    List<double[]> smoothed = ImageTintToolView.smoothFreehandStroke(
        stroke, 8.0, 1.6, 1.2, 0.6, 64);
    assertTrue(smoothed.size() <= 64, "Expected capped vertex count, got " + smoothed.size());
    assertTrue(smoothed.size() >= 16, "Expected enough retained structure, got " + smoothed.size());
  }

  @Test
  void sceneLightWeightFallsOffFromCenterToEdge() {
    double center = ImageTintToolView.sceneLightWeightPx(100.0, 100.0, 100.0, 100.0, 80.0, 0.55);
    double mid = ImageTintToolView.sceneLightWeightPx(140.0, 100.0, 100.0, 100.0, 80.0, 0.55);
    double edge = ImageTintToolView.sceneLightWeightPx(180.0, 100.0, 100.0, 100.0, 80.0, 0.55);

    assertTrue(center > mid, "Expected center intensity to exceed mid falloff");
    assertTrue(mid > 0.0, "Expected mid point to keep some influence");
    assertEquals(0.0, edge);
  }

  @Test
  void sceneLightWeightSoftnessChangesFalloffProfile() {
    double hard = ImageTintToolView.sceneLightWeightPx(145.0, 100.0, 100.0, 100.0, 80.0, 0.1);
    double soft = ImageTintToolView.sceneLightWeightPx(145.0, 100.0, 100.0, 100.0, 80.0, 0.9);

    assertTrue(soft > hard, "Softer lights should retain more energy toward the edge");
  }

  @Test
  void scenePolygonLightWeightKeepsInteriorAndSoftOuterFeather() {
    List<double[]> poly = List.of(
        new double[]{40.0, 40.0},
        new double[]{140.0, 40.0},
        new double[]{140.0, 140.0},
        new double[]{40.0, 140.0}
    );

    double center = ImageTintToolView.scenePolygonLightWeightPx(90.0, 90.0, poly, 24.0, 0.55);
    double innerEdge = ImageTintToolView.scenePolygonLightWeightPx(44.0, 90.0, poly, 24.0, 0.55);
    double outerEdge = ImageTintToolView.scenePolygonLightWeightPx(152.0, 90.0, poly, 24.0, 0.55);
    double farOutside = ImageTintToolView.scenePolygonLightWeightPx(180.0, 90.0, poly, 24.0, 0.55);

    assertTrue(center > innerEdge, "Deep interior should remain brighter than the softened inner edge");
    assertTrue(innerEdge > 0.0, "Soft edge should keep some energy inside the polygon");
    assertTrue(outerEdge > 0.0, "Feather should extend influence slightly outside the polygon");
    assertEquals(0.0, farOutside, "Influence should end outside the feather distance");
  }

  @Test
  void alphaEdgeWeightDetectsCharacterSilhouetteEdges() {
    WritableImage image = new WritableImage(7, 7);
    for (int y = 1; y <= 5; y++) {
      for (int x = 1; x <= 5; x++) {
        image.getPixelWriter().setArgb(x, y, 0xFFFFFFFF);
      }
    }
    image.getPixelWriter().setArgb(1, 3, 0x00FFFFFF);

    double edge = ImageTintToolView.alphaEdgeWeight(image.getPixelReader(), 2, 3, 7, 7);
    double interior = ImageTintToolView.alphaEdgeWeight(image.getPixelReader(), 4, 4, 7, 7);

    assertTrue(edge > interior, "Pixel adjacent to transparency should register as a stronger silhouette edge");
    assertTrue(edge > 0.0, "Silhouette edge should produce a positive weight");
  }

  private static double polygonAreaAbs(List<double[]> polygon) {
    if (polygon == null || polygon.size() < 3) return 0.0;
    double area = 0.0;
    int n = polygon.size();
    for (int i = 0; i < n; i++) {
      double[] a = polygon.get(i);
      double[] b = polygon.get((i + 1) % n);
      area += a[0] * b[1] - b[0] * a[1];
    }
    return Math.abs(area) * 0.5;
  }
}
