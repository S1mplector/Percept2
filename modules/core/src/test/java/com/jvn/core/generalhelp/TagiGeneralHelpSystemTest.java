package com.jvn.core.generalhelp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TagiGeneralHelpSystemTest {
  @Test
  void ranksRelevantDocsByThreeAgentConsensus() {
    TagiGeneralHelpSystem help = new TagiGeneralHelpSystem();
    help.setArticles(List.of(
        HelpArticle.of(
            "vns-choices",
            "VNS Choices",
            "Reference for branching choices in visual novel scripts.",
            "docs/scripting/vns/language/vns-choices.md",
            "Use choices to branch dialogue. A choice can jump to a label or set variables."),
        HelpArticle.of(
            "packaging",
            "Build And Release",
            "Packaging a JVN game for distribution.",
            "docs/project-setup/release/build-and-release.md",
            "The packaging task validates manifests and creates release artifacts."),
        HelpArticle.of(
            "timeline",
            "Timeline Animation",
            "Reference for keyframes and Puppeteer timelines.",
            "docs/scripting/timeline/animation/timeline-animation.md",
            "Timeline actions animate sprites and audio cues.")));

    HelpResponse response = help.ask("How do I add VNS branching choices?");

    assertFalse(response.recommendedArticles().isEmpty());
    assertEquals("vns-choices", response.recommendedArticles().get(0).id());
    assertTrue(response.confidence() > 0.2);
    assertTrue(response.votes().stream()
        .filter(v -> v.articleId().equals("vns-choices"))
        .map(HelpAgentVote::agentName)
        .distinct()
        .count() >= 2);
  }

  @Test
  void returnsCorpusFallbackWhenNoArticlesAreLoaded() {
    TagiGeneralHelpSystem help = new TagiGeneralHelpSystem();

    HelpResponse response = help.ask("timeline help");

    assertTrue(response.answer().contains("No help corpus"));
    assertEquals(0.0, response.confidence());
    assertTrue(response.recommendedArticles().isEmpty());
  }

  @Test
  void engineExposesGeneralHelpSubsystem() {
    Engine engine = new Engine(ApplicationConfig.builder().build());

    assertSame(engine.generalHelp(), engine.generalHelp());
    assertSame(engine.jane(), engine.jane());
    engine.generalHelp().addArticle(HelpArticle.of(
        "jes-overview",
        "JES Overview",
        "Scene scripting overview.",
        "docs/scripting/jes/overview/jes-scripting.md",
        "JES describes scenes, entities, components, and runtime systems."));

    assertTrue(engine.generalHelp().articles().stream().anyMatch(article -> article.id().equals("jes-overview")));
  }

  @Test
  void janeAnswersWithTagiGroundingWhenNoOnnxModelIsConfigured() {
    TagiGeneralHelpSystem help = new TagiGeneralHelpSystem();
    help.setArticles(List.of(HelpArticle.of(
        "timeline",
        "Timeline Animation",
        "Reference for keyframes and Puppeteer timelines.",
        "docs/scripting/timeline/animation/timeline-animation.md",
        "Timeline actions animate sprites and audio cues.")));
    JaneAssistant jane = new JaneAssistant(help);
    jane.setModel(new TagiGroundedChatModel());

    JaneChatResponse response = jane.ask("How do timeline keyframes work?");

    assertEquals("Jane", response.assistantName());
    assertFalse(response.modelUsed());
    assertTrue(response.answer().contains("I'm Jane"));
    assertFalse(response.grounding().recommendedArticles().isEmpty());
    assertEquals(2, response.transcript().size());
  }

  @Test
  void janeHasExpertTrainingWithoutIndexedDocs() {
    JaneAssistant jane = new JaneAssistant(new TagiGeneralHelpSystem());
    jane.setModel(new TagiGroundedChatModel());

    JaneChatResponse response = jane.ask("How do VNS variables affect scene flow?");

    assertFalse(response.grounding().recommendedArticles().isEmpty());
    assertTrue(response.answer().toLowerCase().contains("vns"));
  }

  @Test
  void janeDefaultModelIsLocalAndAvailable() {
    JaneAssistant jane = new JaneAssistant(new TagiGeneralHelpSystem());

    assertFalse(jane.model().name().isBlank());
    assertTrue(jane.model().isAvailable());
  }

  @Test
  void janeDoesNotAutoSelectQwenOnnxModel() {
    String previous = System.getProperty("jvn.jane.onnx.enabled");
    System.clearProperty("jvn.jane.onnx.enabled");
    try {
      JaneAssistant jane = new JaneAssistant(new TagiGeneralHelpSystem());

      assertFalse(jane.model().name().toLowerCase(Locale.ROOT).contains("qwen"));
    } finally {
      if (previous == null) {
        System.clearProperty("jvn.jane.onnx.enabled");
      } else {
        System.setProperty("jvn.jane.onnx.enabled", previous);
      }
    }
  }

  @Test
  void janeHandlesGreetingWithoutRetrieval() {
    JaneAssistant jane = new JaneAssistant(new TagiGeneralHelpSystem());

    JaneChatResponse response = jane.ask("Hey Jane");

    assertTrue(response.answer().startsWith("Hi, I'm Jane"));
    assertEquals(1.0, response.grounding().confidence());
    assertTrue(response.grounding().recommendedArticles().isEmpty());
  }

  @Test
  void janeHandlesPunctuatedGreetingWithoutModelGeneration() {
    JaneAssistant jane = new JaneAssistant(new TagiGeneralHelpSystem());

    JaneChatResponse response = jane.ask("Hey Jane!");

    assertTrue(response.answer().startsWith("Hi, I'm Jane"));
    assertFalse(response.modelUsed());
    assertEquals(1.0, response.grounding().confidence());
  }

  @Test
  void janeExplainsCapabilitiesWithoutNeedingConcreteContext() {
    JaneAssistant jane = new JaneAssistant(new TagiGeneralHelpSystem());

    JaneChatResponse response = jane.ask("What can you do?");

    assertTrue(response.answer().contains("VNS"));
    assertTrue(response.answer().contains("JES"));
    assertTrue(response.answer().contains("Puppeteer"));
    assertFalse(response.answer().contains("not have enough indexed"));
  }

  @Test
  void janeExplainsVnsDirectlyWithoutImmediateDocRecommendations() {
    JaneAssistant jane = new JaneAssistant(new TagiGeneralHelpSystem());

    JaneChatResponse response = jane.ask("What is VNS?");

    assertTrue(response.answer().contains("visual novel scripting language"));
    assertFalse(response.modelUsed());
    assertTrue(response.grounding().recommendedArticles().isEmpty());
  }

  @Test
  void janeExplainsJesDirectlyWithoutGeneratedDocFragments() {
    JaneAssistant jane = new JaneAssistant(new TagiGeneralHelpSystem());

    JaneChatResponse response = jane.ask("What is JES?");

    assertTrue(response.answer().contains("scene and gameplay scripting layer"));
    assertTrue(response.answer().contains("entities"));
    assertFalse(response.answer().contains("cannot be done"));
    assertFalse(response.answer().contains("JES By Example / Prerequisites"));
    assertFalse(response.modelUsed());
    assertTrue(response.grounding().recommendedArticles().isEmpty());
  }

  @Test
  void trainingCorpusAddsHeadingLevelChunks() {
    HelpArticle article = HelpArticle.of(
        "doc",
        "JVN Test Doc",
        "A test doc.",
        "docs/test.md",
        """
        # Overview
        General intro.

        ## Asset Workflow
        Put sprites and audio in stable project folders.

        ## Debugging
        Use diagnostics and the run console.
        """);

    List<HelpArticle> trained = JaneTrainingCorpus.train(List.of(article));

    assertTrue(trained.size() > JaneTrainingCorpus.expertArticles().size() + 1);
    assertTrue(trained.stream().anyMatch(a -> a.title().contains("Asset Workflow")));
    assertTrue(trained.stream().anyMatch(a -> "heading-chunk".equals(a.metadata().get("kind"))));
  }
}
