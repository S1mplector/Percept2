package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LayeredImageVisualizerViewTest {

  @Test
  void buildDefaultExportStemUsesCharacterIdAndExpression() {
    assertEquals(
        "john_doe_happy",
        LayeredImageVisualizerView.buildDefaultExportStem("assets/characters/john_doe", "john_doe", "happy"));
    assertEquals(
        "lavender",
        LayeredImageVisualizerView.buildDefaultExportStem("assets/demo/characters/lavender", "lavender", ""));
    assertEquals(
        "ryan_pack_smile",
        LayeredImageVisualizerView.buildDefaultExportStem("assets/characters/ryan_pack", "", "smile"));
  }

  @Test
  void buildExportFileNameKeepsSingleExtension() {
    assertEquals("john_doe_happy.png", LayeredImageVisualizerView.buildExportFileName("john_doe_happy", "png"));
    assertEquals("john_doe_happy.png", LayeredImageVisualizerView.buildExportFileName("john_doe_happy.png", "png"));
  }

  @Test
  void buildCharpresetSnippetFileNameUsesRuntimeFacingSuffix() {
    assertEquals("john_doe_happy_charpreset.vns",
        LayeredImageVisualizerView.buildCharpresetSnippetFileName("john_doe_happy"));
    assertEquals("john_doe_happy_charpreset.vns",
        LayeredImageVisualizerView.buildCharpresetSnippetFileName("john_doe_happy_charpreset"));
  }

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
    assertEquals("assets/demo/characters/lavender", LayeredImageVisualizerView.deriveSetIdFromRelative("assets/demo/characters/lavender/eyes/neutral.png"));
    assertEquals("game/images/characters/john_doe", LayeredImageVisualizerView.deriveSetIdFromRelative("game/images/characters/john_doe/head/eyes/eyes_01.png"));
    assertEquals("assets/ui", LayeredImageVisualizerView.deriveSetIdFromRelative("assets/ui/icons/play.png"));
    assertEquals("demo-assets/demo_sprite_codel", LayeredImageVisualizerView.deriveSetIdFromRelative("demo-assets/demo_sprite_codel/Codel1.png"));
    assertEquals("(root)", LayeredImageVisualizerView.deriveSetIdFromRelative("root_image.png"));
  }

  @Test
  void characterPathDetectionHandlesCommonProjectLayouts() {
    assertEquals(true, LayeredImageVisualizerView.isCharacterAssetPath("assets/characters/nora/base.png"));
    assertEquals(true, LayeredImageVisualizerView.isCharacterAssetPath("assets/demo/characters/lavender/eyes/neutral.png"));
    assertEquals(true, LayeredImageVisualizerView.isCharacterAssetPath("game/images/characters/john_doe/head/eyes_01.png"));
    assertEquals(false, LayeredImageVisualizerView.isCharacterAssetPath("assets/backgrounds/school_day.png"));
    assertEquals(false, LayeredImageVisualizerView.shouldIncludePathForScan("assets/backgrounds/school_day.png", true));
    assertEquals(true, LayeredImageVisualizerView.shouldIncludePathForScan("assets/backgrounds/school_day.png", false));
    assertEquals(true, LayeredImageVisualizerView.isIgnoredLayerPreviewPath("assets/characters/john_doe/null/john_doe_null_ref.png"));
  }

  @Test
  void chooseSetSelectionPrefersCharacterSetsByDefault() {
    List<String> visible = List.of("assets/bg", "assets/ui", "assets/demo/characters/nora", "assets/characters/ryan");
    assertEquals(
        "assets/demo/characters/nora",
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
  void inferFolderBasedGroupHandlesBaseEyesMouthSubfolders() {
    assertEquals(
        "base",
        LayeredImageVisualizerView.inferGroupFromSetSubfolder(
            "demo-assets/Lavender_test_sprite/base/lavender_test_sprite_base.png"));
    assertEquals(
        "eyes",
        LayeredImageVisualizerView.inferGroupFromSetSubfolder(
            "demo-assets/Lavender_test_sprite/eyes/lavender_test_sprite_eyes_half_closed.png"));
    assertEquals(
        "mouth",
        LayeredImageVisualizerView.inferGroupFromSetSubfolder(
            "demo-assets/Lavender_test_sprite/mouth/lavender_test_sprite_mouth_smile.png"));
  }

  @Test
  void inferFolderBasedGroupNormalizesNestedCharacterFolders() {
    assertEquals(
        "arm_behind",
        LayeredImageVisualizerView.inferGroupFromSetSubfolder(
            "assets/characters/john_doe/arm behind/holding phone/John_Doe_arm_behind_-_holding_phone_full.png"));
    assertEquals(
        "body_arms",
        LayeredImageVisualizerView.inferGroupFromSetSubfolder(
            "assets/characters/john_doe/body/body arms/John_Doe_body_arm_-_grabbing_neck.png"));
    assertEquals(
        "eyes",
        LayeredImageVisualizerView.inferGroupFromSetSubfolder(
            "assets/characters/lily_langley/head/normal/eyes/Lilily_Langley_normal_head_-_eyes_06.png"));
    assertEquals(
        "face",
        LayeredImageVisualizerView.inferGroupFromSetSubfolder(
            "assets/characters/lily_langley/head/normal/faces (+heads)/Lilily_Langley_normal_faces_-_default.png"));
    assertEquals(
        "snoot",
        LayeredImageVisualizerView.inferGroupFromSetSubfolder(
            "assets/characters/lily_langley/head/tilted/snoots/Lilily_Langley_tilted_snoots_-_mouth_open.png"));
    assertEquals(
        "add",
        LayeredImageVisualizerView.inferGroupFromSetSubfolder(
            "assets/characters/lily_langley/head/normal/additions/Lilily_Langley_normal_additions_-_glasses.png"));
    assertEquals(
        "arm_front",
        LayeredImageVisualizerView.inferGroupFromSetSubfolder(
            "assets/characters/lily_langley/arm front 02/Lilily_Langley_arm_front_-_psst.png"));
  }

  @Test
  void inferLabelFromFilenameFallsBackToTailTokenWhenGroupTokenMissing() {
    assertEquals(
        "base",
        LayeredImageVisualizerView.inferLabelFromFilenameForGroup("lavender_test_sprite_base", "base"));
    assertEquals(
        "smile",
        LayeredImageVisualizerView.inferLabelFromFilenameForGroup("lavender_test_sprite_mouth_smile", "mouth"));
    assertEquals(
        "angry",
        LayeredImageVisualizerView.inferLabelFromFilenameForGroup("lavender_test_sprite_angry", "eyes"));
  }

  @Test
  void inferLabelsPreserveLilyPoseContext() {
    assertEquals(
        "normal_very_sad_eyes_closed",
        LayeredImageVisualizerView.inferLabelFromPathAndFilename(
            "assets/characters/lily_langley/head/normal/faces (+heads)/Lilily_Langley_normal_faces_-_very_sad_eyes_closed.png",
            "Lilily_Langley_normal_faces_-_very_sad_eyes_closed",
            "face"));
    assertEquals(
        "tilted_01",
        LayeredImageVisualizerView.inferLabelFromPathAndFilename(
            "assets/characters/lily_langley/head/tilted/eyes/Lilily_Langley_tilted_head_-_eyes_01.png",
            "Lilily_Langley_tilted_head_-_eyes_01",
            "eyes"));
    assertEquals(
        "02_psst",
        LayeredImageVisualizerView.inferLabelFromPathAndFilename(
            "assets/characters/lily_langley/arm front 02/Lilily_Langley_arm_front_-_psst.png",
            "Lilily_Langley_arm_front_-_psst",
            "arm_front"));
  }

  @Test
  void scriptBackedScannedGroupsResolveToDeclaredGroupsWhenEquivalent() {
    assertEquals(
        "face",
        LayeredImageVisualizerView.resolveGroupForSet(java.util.Set.of("face"), "faces_heads"));
    assertEquals(
        "arm_front",
        LayeredImageVisualizerView.resolveGroupForSet(java.util.Set.of("arm_front"), "arm_front_02"));
    assertEquals(
        "tail",
        LayeredImageVisualizerView.resolveGroupForSet(java.util.Set.of("tail"), "tail_front"));
    assertEquals(
        "eyes_06",
        LayeredImageVisualizerView.resolveGroupForSet(java.util.Set.of("eyes_base", "eyes_06"), "eyes"));
  }

  @Test
  void topLevelSetImageDetectionIdentifiesCompositeReferenceImages() {
    assertEquals(
        true,
        LayeredImageVisualizerView.isTopLevelSetImage(
            "assets/characters/lily_langley/Lilily_Langley.png",
            "assets/characters/lily_langley"));
    assertEquals(
        false,
        LayeredImageVisualizerView.isTopLevelSetImage(
            "assets/characters/lily_langley/head/normal/eyes/Lilily_Langley_normal_head_-_eyes_06.png",
            "assets/characters/lily_langley"));
  }

  @Test
  void chooseSetSelectionKeepsPreferredLayeredSetWhenItIsAlreadyBest() {
    List<String> visible = List.of("demo-assets/demo_bg_field", "demo-assets/Lavender_test_sprite");
    Map<String, Integer> groups = Map.of(
        "demo-assets/demo_bg_field", 1,
        "demo-assets/Lavender_test_sprite", 3
    );
    assertEquals(
        "demo-assets/Lavender_test_sprite",
        LayeredImageVisualizerView.chooseSetSelection(null, "demo-assets/Lavender_test_sprite", visible, groups));
  }

  @Test
  void defaultOptionScorePrefersNeutralThenDefaultThenBase() {
    assertEquals(0, LayeredImageVisualizerView.defaultOptionScore("neutral"));
    assertEquals(1, LayeredImageVisualizerView.defaultOptionScore("default"));
    assertEquals(2, LayeredImageVisualizerView.defaultOptionScore("base"));
    assertEquals(10, LayeredImageVisualizerView.defaultOptionScore("angry"));
  }

  @Test
  void searchableHelpersMatchTextAndLayerPaths() {
    assertEquals(true, LayeredImageVisualizerView.matchesSearchableText("assets/characters/john_doe", "john doe"));
    assertEquals(
        "offer  ·  assets/characters/john_doe/arm behind/hand gesture/John_Doe_arm_offer.png",
        LayeredImageVisualizerView.layerOptionPopupText(
            "offer",
            "assets/characters/john_doe/arm behind/hand gesture/John_Doe_arm_offer.png"));
    assertEquals(
        true,
        LayeredImageVisualizerView.matchesLayerOptionSearch(
            "offer",
            "arm_behind",
            "assets/characters/john_doe/arm behind/hand gesture/John_Doe_arm_offer.png",
            "hand gesture"));
    assertEquals(
        true,
        LayeredImageVisualizerView.matchesLayerOptionSearch(
            "offer",
            "arm_behind",
            "assets/characters/john_doe/arm behind/hand gesture/John_Doe_arm_offer.png",
            "arm behind"));
  }

  @Test
  void backgroundGroupDetectionRecognizesCommonNames() {
    assertEquals(true, LayeredImageVisualizerView.isLikelyBackgroundGroupName("field"));
    assertEquals(true, LayeredImageVisualizerView.isLikelyBackgroundGroupName("mainmenu"));
    assertEquals(true, LayeredImageVisualizerView.isLikelyBackgroundGroupName("background"));
    assertEquals(false, LayeredImageVisualizerView.isLikelyBackgroundGroupName("eyes"));
    assertEquals(false, LayeredImageVisualizerView.isLikelyBackgroundGroupName("mouth"));
    assertEquals(false, LayeredImageVisualizerView.isLikelyBackgroundGroupName("codel"));
  }

  @Test
  void optionalOverlayGroupDetectionTargetsArmsAndProps() {
    assertEquals(true, LayeredImageVisualizerView.isLikelyOptionalOverlayGroup("arm behind"));
    assertEquals(true, LayeredImageVisualizerView.isLikelyOptionalOverlayGroup("body_arms"));
    assertEquals(true, LayeredImageVisualizerView.isLikelyOptionalOverlayGroup("additions"));
    assertEquals(false, LayeredImageVisualizerView.isLikelyOptionalOverlayGroup("eyes"));
    assertEquals(false, LayeredImageVisualizerView.isLikelyOptionalOverlayGroup("body"));
  }

  @Test
  void backgroundSuppressionTriggersOnlyWhenMixedWithForeground() {
    assertEquals(
        true,
        LayeredImageVisualizerView.shouldSuppressBackgroundGroups(List.of("codel", "field")));
    assertEquals(
        false,
        LayeredImageVisualizerView.shouldSuppressBackgroundGroups(List.of("field", "background")));
    assertEquals(
        false,
        LayeredImageVisualizerView.shouldSuppressBackgroundGroups(List.of("eyes", "mouth", "base")));
    assertEquals(
        false,
        LayeredImageVisualizerView.shouldSuppressBackgroundGroups(List.of("mainmenu")));
  }

  @Test
  void draggedFocusMovesViewportOppositeToPointerMotion() {
    assertEquals(
        0.40,
        LayeredImageVisualizerView.draggedFocus(0.50, 100.0, 500.0, 500.0, 1000.0),
        0.0001);
    assertEquals(
        0.60,
        LayeredImageVisualizerView.draggedFocus(0.50, -100.0, 500.0, 500.0, 1000.0),
        0.0001);
    assertEquals(
        0.0,
        LayeredImageVisualizerView.draggedFocus(0.05, 500.0, 500.0, 500.0, 1000.0),
        0.0001);
  }

  @Test
  void anchoredFocusKeepsZoomTargetWithinBounds() {
    assertEquals(
        0.50,
        LayeredImageVisualizerView.anchoredFocus(500.0, 0.5, 300.0, 1000.0),
        0.0001);
    assertEquals(
        0.15,
        LayeredImageVisualizerView.anchoredFocus(10.0, 0.5, 300.0, 1000.0),
        0.0001);
    assertEquals(
        0.85,
        LayeredImageVisualizerView.anchoredFocus(990.0, 0.5, 300.0, 1000.0),
        0.0001);
  }

  @Test
  void zoomFromScrollScalesAndClamps() {
    assertEquals(
        1.12,
        LayeredImageVisualizerView.zoomFromScroll(1.0, 40.0, 0.5, 3.0),
        0.0001);
    assertEquals(
        0.5,
        LayeredImageVisualizerView.zoomFromScroll(0.6, -400.0, 0.5, 3.0),
        0.0001);
    assertEquals(
        3.0,
        LayeredImageVisualizerView.zoomFromScroll(2.8, 400.0, 0.5, 3.0),
        0.0001);
  }

  @Test
  void parseAttributeAssignmentsSupportsCommonForms() {
    Map<String, String> parsed = LayeredImageVisualizerView.parseAttributeAssignments(
        "eyes=angry mouth:smile hair_long invalidtoken");
    assertEquals(
        Map.of(
            "eyes", "angry",
            "mouth", "smile",
            "hair", "long"),
        parsed);
  }

  @Test
  void parseAttributeShortformsIgnoresCommentsAndBlankLines() {
    String text = """
        # comment
        happy = eyes=neutral mouth=happy

        serious=eyes=cross_closed mouth=neutral
        invalid line
        """;
    Map<String, String> parsed = LayeredImageVisualizerView.parseAttributeShortforms(text);
    assertEquals(
        Map.of(
            "happy", "eyes=neutral mouth=happy",
            "serious", "eyes=cross_closed mouth=neutral"),
        parsed);
  }

  @Test
  void formatPresetShowExpressionTokenUsesExplicitPresetSyntax() {
    assertEquals("@happy", LayeredImageVisualizerView.formatPresetShowExpressionToken("happy"));
    assertEquals("@thinking_hat", LayeredImageVisualizerView.formatPresetShowExpressionToken("Thinking Hat"));
    assertEquals("@neutral", LayeredImageVisualizerView.formatPresetShowExpressionToken("###"));
  }

  @Test
  void formatInlineLayerExpressionTokenBuildsCompositeLayerRefs() {
    assertEquals(
        "$base+$eyes=happy+$mouth=smile",
        LayeredImageVisualizerView.formatInlineLayerExpressionToken(
            List.of("base", "eyes=happy", "mouth=smile")));
    assertEquals(
        "$eyes=happy+$mouth_smile",
        LayeredImageVisualizerView.formatInlineLayerExpressionToken(
            List.of("###", "eyes=happy", "mouth smile")));
    assertEquals("", LayeredImageVisualizerView.formatInlineLayerExpressionToken(List.of()));
  }

  @Test
  void formatShowSnippetProducesCenteredShowCommand() {
    assertEquals(
        "[show lavender center @happy]",
        LayeredImageVisualizerView.formatShowSnippet("lavender", "@happy"));
    assertEquals(
        "[show lavender_test center $base+$eyes_happy]",
        LayeredImageVisualizerView.formatShowSnippet("Lavender Test", "$base+$eyes_happy"));
    assertEquals(
        "[show character_id center @neutral]",
        LayeredImageVisualizerView.formatShowSnippet("###", ""));
  }
}
