package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class NewProjectScaffoldTemplateWiringTest {

  private static final Pattern UNRESOLVED_TOKEN = Pattern.compile("\\{\\{[A-Z0-9_]+\\}\\}");

  private static final List<String> STORY_TEMPLATE_PATHS = List.of(
      "scripts/story/prologue_blank.vns",
      "scripts/story/prologue_sample.vns",
      "scripts/story/tutorial_hub.vns",
      "scripts/story/tutorial_hub_blank.vns",
      "scripts/story/branch_demo_blank.vns",
      "scripts/story/branch_demo_sample.vns",
      "scripts/story/epilogue_blank.vns",
      "scripts/story/epilogue_sample.vns");

  private static final List<String> TUTORIAL_TEMPLATE_PATHS = List.of(
      "scripts/tutorial/01_dialogue_basics.vns",
      "scripts/tutorial/02_narration_and_pacing.vns",
      "scripts/tutorial/03_expressions_and_characters.vns",
      "scripts/tutorial/04_images_and_backgrounds.vns",
      "scripts/tutorial/05_transitions_and_effects.vns",
      "scripts/tutorial/06_audio_and_music.vns",
      "scripts/tutorial/07_variables_and_conditions.vns",
      "scripts/tutorial/08_character_movement.vns",
      "scripts/tutorial/09_puppeteer_timeline.vns",
      "scripts/tutorial/10_choices_and_menus.vns",
      "scripts/tutorial/11_subroutines_and_flow.vns",
      "scripts/tutorial/12_best_practices.vns",
      "scripts/tutorial/13_camera_and_staging.vns",
      "scripts/tutorial/14_localization_and_textkeys.vns",
      "scripts/tutorial/15_ui_layout_and_theme.vns",
      "scripts/tutorial/16_testing_and_release.vns");

  private static final Map<String, String> TOKENS = buildTokens();

  @Test
  void storyTemplatesRenderWithoutUnresolvedTokens() throws Exception {
    assertEquals(8, STORY_TEMPLATE_PATHS.size(), "Story template inventory changed");
    for (String path : STORY_TEMPLATE_PATHS) {
      String rendered = render(path);
      assertFalse(
          UNRESOLVED_TOKEN.matcher(rendered).find(),
          () -> "Unresolved token found in rendered story template " + path + ":\n" + rendered);
    }
  }

  @Test
  void tutorialTemplatesRenderWithoutUnresolvedTokens() throws Exception {
    assertEquals(16, TUTORIAL_TEMPLATE_PATHS.size(), "Tutorial template inventory changed");
    for (String path : TUTORIAL_TEMPLATE_PATHS) {
      String rendered = render(path);
      assertFalse(
          UNRESOLVED_TOKEN.matcher(rendered).find(),
          () -> "Unresolved token found in rendered tutorial template " + path + ":\n" + rendered);
    }
  }

  @Test
  void tutorialTemplatesResetDialoguePresentationMode() throws Exception {
    for (String path : TUTORIAL_TEMPLATE_PATHS) {
      String rendered = render(path);
      assertTrue(
          rendered.contains("[mode dialogue standard]\n[inc tutorial_count]"),
          () -> "Tutorial does not reset dialogue presentation mode at entry: " + path);
    }

    String hub = render("scripts/story/tutorial_hub.vns");
    assertTrue(
        hub.contains("[mode dialogue standard]\n[textspeed 28]"),
        "Tutorial hub does not reset dialogue presentation mode at entry");
  }

  @Test
  void sampleStoryArcTemplatesPointAtExpectedTargets() throws Exception {
    String prologue = render("scripts/story/prologue_sample.vns");
    assertTrue(prologue.contains("[set tutorial_count 0]"));
    assertTrue(prologue.contains("[goto TutorialHub:start]"));
    assertTrue(prologue.contains("[goto BranchDemo:start]"));

    String branch = render("scripts/story/branch_demo_sample.vns");
    assertTrue(branch.contains("scripts/story/epilogue.vns"));
    assertTrue(branch.contains("[goto Epilogue:start]"));

    String epilogue = render("scripts/story/epilogue_sample.vns");
    assertTrue(epilogue.contains("config/timeline/story.timeline"));
    assertTrue(epilogue.contains("[end]"));
  }

  @Test
  void tutorialHubStillRoutesToAllSixteenLessons() throws Exception {
    String hub = render("scripts/story/tutorial_hub.vns");
    List<String> expectedTargets = List.of(
        "T01_Dialogue", "T02_Narration", "T03_Expressions", "T04_Images",
        "T05_Transitions", "T06_Audio", "T07_Variables", "T08_Movement",
        "T09_Puppeteer", "T10_Menus", "T11_Subroutines", "T12_BestPractices",
        "T13_Camera", "T14_Localization", "T15_UILayout", "T16_TestingRelease");
    for (String target : expectedTargets) {
      assertTrue(hub.contains("[goto " + target + ":start]"), "Missing hub route for " + target);
    }
    assertTrue(hub.contains("@label topics_page_1"));
    assertTrue(hub.contains("@label topics_page_2"));
    assertTrue(hub.contains("@label topics_page_3"));
  }

  @Test
  void blankTemplatesStillFormAPlayableMinimalArc() throws Exception {
    String prologue = render("scripts/story/prologue_blank.vns");
    String tutorialHub = render("scripts/story/tutorial_hub_blank.vns");
    String branch = render("scripts/story/branch_demo_blank.vns");
    String epilogue = render("scripts/story/epilogue_blank.vns");

    assertTrue(prologue.contains("[goto TutorialHub:start]"));
    assertTrue(prologue.contains("[goto BranchDemo:start]"));
    assertTrue(tutorialHub.contains("scripts/story/tutorial_hub.vns"));
    assertTrue(branch.contains("scripts/story/branch_demo.vns"));
    assertTrue(branch.contains("[goto Epilogue:start]"));
    assertTrue(epilogue.contains("[end]"));
  }

  private static String render(String relativePath) throws Exception {
    String template = loadTemplate(relativePath);
    String rendered = template;
    for (Map.Entry<String, String> entry : TOKENS.entrySet()) {
      rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
    }
    return rendered;
  }

  private static String loadTemplate(String relativePath) throws Exception {
    String resourcePath = "com/jvn/editor/templates/new-project/" + relativePath;
    try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      assertNotNull(in, "Missing scaffold template resource: " + resourcePath);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Map<String, String> buildTokens() {
    Map<String, String> tokens = new LinkedHashMap<>();
    tokens.put("PROJECT_NAME", "Test Project");
    tokens.put("SCENARIO_PREFIX", "test_project");
    tokens.put("CHARACTERS_INCLUDE", "/definitions/characters.vns");
    tokens.put("LAVENDER_EXPR", "idle");
    tokens.put("TUTORIAL_TARGET", "TutorialHub");
    tokens.put("BRANCH_TARGET", "BranchDemo");
    tokens.put("EPILOGUE_TARGET", "Epilogue");
    tokens.put("STORY_TUTORIAL_SCRIPT_PATH", "scripts/story/tutorial_hub.vns");
    tokens.put("STORY_BRANCH_SCRIPT_PATH", "scripts/story/branch_demo.vns");
    tokens.put("STORY_EPILOGUE_SCRIPT_PATH", "scripts/story/epilogue.vns");
    tokens.put("TIMELINE_PATH", "config/timeline/story.timeline");
    tokens.put("BG_DECL", "@background field_day assets/demo/backgrounds/game.png\n\n");
    tokens.put("BG_START", "[bg field_day]\n");
    tokens.put("BG_TRANSITION", "[transition fade 400]\n");
    tokens.put("DIALOGUE_TARGET", "T01_Dialogue");
    tokens.put("NARRATION_TARGET", "T02_Narration");
    tokens.put("EXPRESSIONS_TARGET", "T03_Expressions");
    tokens.put("IMAGES_TARGET", "T04_Images");
    tokens.put("TRANSITIONS_TARGET", "T05_Transitions");
    tokens.put("AUDIO_TARGET", "T06_Audio");
    tokens.put("VARIABLES_TARGET", "T07_Variables");
    tokens.put("MOVEMENT_TARGET", "T08_Movement");
    tokens.put("PUPPETEER_TARGET", "T09_Puppeteer");
    tokens.put("MENUS_TARGET", "T10_Menus");
    tokens.put("SUBROUTINES_TARGET", "T11_Subroutines");
    tokens.put("BEST_PRACTICES_TARGET", "T12_BestPractices");
    tokens.put("CAMERA_TARGET", "T13_Camera");
    tokens.put("LOCALIZATION_TARGET", "T14_Localization");
    tokens.put("UI_LAYOUT_TARGET", "T15_UILayout");
    tokens.put("TESTING_RELEASE_TARGET", "T16_TestingRelease");
    tokens.put("HUB_TARGET", "TutorialHub");
    tokens.put("CHARACTERS_SCRIPT_PATH", "scripts/definitions/characters.vns");
    tokens.put("BG_CROSSFADE", "[transition crossfade 600 field_evening]\n");
    tokens.put("BGM_START", "[bgm \"assets/demo/audio/theme.mp3\"]\n");
    tokens.put("BGM_FADE", "[bgm_fadeout 1200]\n[wait 1300]\n");
    tokens.put("EXPRESSION_HINT", "Lavender: This project uses layered presets for composited expressions.");
    return tokens;
  }
}
