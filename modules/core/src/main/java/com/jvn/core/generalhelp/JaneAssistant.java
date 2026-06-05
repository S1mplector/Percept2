package com.jvn.core.generalhelp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Jane, JVN's assistant: a TAGI-grounded chatbot with an optional local model. */
public final class JaneAssistant {
  private static final int MAX_HISTORY_MESSAGES = 12;
  private static final String ONNX_ENABLED_PROPERTY = "jvn.jane.onnx.enabled";

  private final TagiGeneralHelpSystem tagi;
  private final LocalChatModel fallbackModel = new TagiGroundedChatModel();
  private final LocalChatModel builtInModel = new JaneExpertLocalModel();
  private final List<ChatMessage> history = new ArrayList<>();
  private LocalChatModel model;

  public JaneAssistant(TagiGeneralHelpSystem tagi) {
    this.tagi = tagi == null ? new TagiGeneralHelpSystem() : tagi;
    if (this.tagi.articles().isEmpty()) {
      this.tagi.setArticles(JaneTrainingCorpus.expertArticles());
    }
    reloadConfiguredModel();
  }

  public void setModel(LocalChatModel model) {
    this.model = model == null ? fallbackModel : model;
  }

  public LocalChatModel model() {
    return model;
  }

  public void reloadConfiguredModel() {
    this.model = configuredModel().orElse(builtInModel);
  }

  public List<ChatMessage> history() {
    return List.copyOf(history);
  }

  public void clearHistory() {
    history.clear();
  }

  public JaneChatResponse ask(String query) {
    String directAnswer = directAnswer(query);
    if (!directAnswer.isBlank()) {
      append(ChatMessage.user(query));
      append(ChatMessage.assistant(directAnswer));
      return new JaneChatResponse(
          "Jane",
          directAnswer,
          activeModel().name(),
          false,
          new HelpResponse(query, directAnswer, 1.0, List.of(), List.of(), java.util.Map.of()),
          history());
    }

    HelpResponse grounding = tagi.ask(query);
    List<ChatMessage> promptHistory = List.copyOf(history);
    String answer = "";
    boolean modelUsed = false;
    LocalChatModel active = activeModel();
    if (active.isAvailable()) {
      try {
        answer = active.generate(query, grounding, promptHistory);
        if (isLowQualityGeneratedText(answer)) {
          answer = "";
        }
        modelUsed = !(active instanceof TagiGroundedChatModel) && !answer.isBlank();
      } catch (RuntimeException ignored) {
        answer = "";
      }
    }
    if (answer.isBlank()) {
      answer = fallbackModel.generate(query, grounding, promptHistory);
      active = fallbackModel;
      modelUsed = false;
    }
    append(ChatMessage.user(query));
    append(ChatMessage.assistant(answer));
    return new JaneChatResponse("Jane", answer, active.name(), modelUsed, grounding, history());
  }

  private LocalChatModel activeModel() {
    return model == null ? fallbackModel : model;
  }

  private Optional<LocalChatModel> configuredModel() {
    Optional<LocalChatModel> gemini = GeminiChatModel.fromSystemProperties()
        .<LocalChatModel>map(model -> model);
    if (gemini.isPresent()) return gemini;
    if (!Boolean.getBoolean(ONNX_ENABLED_PROPERTY)) return Optional.empty();
    return OnnxChatModel.fromSystemProperties().map(model -> model);
  }

  private String directAnswer(String query) {
    String normalized = normalizeIntent(query);
    if (normalized.isBlank()) return "";
    if (isGreeting(normalized)) {
      return """
          Hi, I'm Jane. I can help with JVN authoring and engine work. Ask me about VNS, JES, Puppeteer timelines, menus and layouts, assets, packaging, diagnostics, or editor workflows.
          """.trim();
    }
    if (asksIdentity(normalized)) {
      return """
          I'm Jane, JVN's local assistant. I use TAGI to ground answers in the indexed JVN docs, then explain the practical workflow and point you to the right files or tools.
          """.trim();
    }
    if (asksCapabilities(normalized)) {
      return """
          I can help you navigate and use JVN.

          I can explain VNS story scripting, JES gameplay scenes, Puppeteer timelines, menu/layout UI, assets, audio, saves, packaging, diagnostics, and editor sidebars. I can also recommend the docs to open first, outline practical workflows, and keep short chat context while you ask follow-up questions.

          Try: "How do I add VNS choices?", "How do JES components work?", "How do I animate a sprite?", or "Why is my menu layout failing?"
          """.trim();
    }
    if (asksWhatIsVns(normalized)) {
      return """
          VNS is JVN's visual novel scripting language. It is the story-facing layer for dialogue, character presentation, choices, labels, variables, jumps, audio cues, transitions, and links into JES or timeline work.

          In practice, you use VNS for scene flow: write dialogue, branch with choices, move between labels, show characters and backgrounds, and call engine features when the story needs presentation or gameplay support.
          """.trim();
    }
    if (asksWhatIsJes(normalized)) {
      return """
          JES is JVN's scene and gameplay scripting layer. It is used for interactive scenes built from entities, components, tile maps, physics, input, AI behaviors, camera tracking, triggers, and runtime events.

          In practice, VNS handles story flow and dialogue, while JES handles the more game-like parts of a project: moving actors, collision, maps, player control, scene systems, and hooks that connect gameplay back into the story.
          """.trim();
    }
    if (asksVnsChoices(normalized)) {
      return """
          VNS choices define branching story flow. A choice presents options to the player, and each option usually jumps to a label, changes variables, or routes the scene into a different follow-up beat.

          A practical VNS workflow is to write the smallest branch first: one choice, two labels, and one variable if you need to remember the decision. Once the flow is correct, add character staging, audio cues, transitions, and any JES or timeline calls around the branch.
          """.trim();
    }
    if (asksBrokenVnsLabel(normalized)) {
      return """
          To debug a broken VNS label, first verify the label name exactly matches every jump or choice target, including spelling and casing. Then run VNS diagnostics and use the label flow map to confirm the target is reachable from the current scene.

          If the problem is still unclear, reduce the script to the smallest failing path: one entry label, the jump or choice that should route there, and the target label. Once navigation works, add variables, presentation commands, audio, and JES or timeline calls back in one layer at a time.
          """.trim();
    }
    return "";
  }

  private static String normalizeIntent(String query) {
    if (query == null) return "";
    String lower = query.trim().toLowerCase(java.util.Locale.ROOT);
    lower = lower.replaceAll("[^\\p{Alnum}/]+", " ");
    return lower.replaceAll("\\s+", " ").trim();
  }

  private static boolean isGreeting(String normalized) {
    return normalized.equals("hi")
        || normalized.equals("hello")
        || normalized.equals("hey")
        || normalized.equals("hey jane")
        || normalized.equals("hi jane")
        || normalized.equals("hello jane")
        || normalized.equals("yo jane");
  }

  private static boolean asksIdentity(String normalized) {
    return normalized.equals("who are you")
        || normalized.equals("what are you")
        || normalized.equals("what are you");
  }

  private static boolean asksCapabilities(String normalized) {
    return normalized.equals("help")
        || normalized.equals("/help")
        || normalized.equals("what can you do")
        || normalized.equals("what do you do")
        || normalized.equals("how can you help")
        || normalized.equals("how can you help");
  }

  private static boolean asksWhatIsVns(String normalized) {
    return normalized.equals("what is vns")
        || normalized.equals("whats vns")
        || normalized.equals("what is vns used for")
        || normalized.equals("what does vns do")
        || normalized.equals("explain vns");
  }

  private static boolean asksWhatIsJes(String normalized) {
    return normalized.equals("what is jes")
        || normalized.equals("whats jes")
        || normalized.equals("what is jes used for")
        || normalized.equals("what does jes do")
        || normalized.equals("explain jes");
  }

  private static boolean asksVnsChoices(String normalized) {
    return normalized.contains("vns")
        && (normalized.contains("choice") || normalized.contains("choices") || normalized.contains("branch")
            || normalized.contains("branching"));
  }

  private static boolean asksBrokenVnsLabel(String normalized) {
    return normalized.contains("vns")
        && normalized.contains("label")
        && (normalized.contains("debug") || normalized.contains("broken") || normalized.contains("fix")
            || normalized.contains("failing") || normalized.contains("not work"));
  }

  private static boolean isLowQualityGeneratedText(String value) {
    if (value == null || value.isBlank()) return true;
    if (value.indexOf('\uFFFD') >= 0) return true;
    String lower = value.trim().toLowerCase(java.util.Locale.ROOT);
    if (lower.startsWith("cannot be done")) return true;
    if (lower.matches("(?s).*\\b(vns|jes|puppeteer) by example\\s*/\\s*prerequisites\\b.*")) return true;
    String compact = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    if (compact.length() >= 24 && compact.chars().distinct().count() <= 7) return true;
    return hasDominantRepeatedNgram(compact);
  }

  private static boolean hasDominantRepeatedNgram(String compact) {
    if (compact == null || compact.length() < 24) return false;
    for (int width = 2; width <= 8; width++) {
      java.util.Map<String, Integer> counts = new java.util.HashMap<>();
      for (int i = 0; i + width <= compact.length(); i++) {
        String token = compact.substring(i, i + width);
        counts.merge(token, 1, Integer::sum);
      }
      int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
      if (max >= 5 && max * width >= compact.length() * 0.32) return true;
    }
    return false;
  }

  private void append(ChatMessage message) {
    if (message == null || message.content().isBlank()) return;
    history.add(message);
    while (history.size() > MAX_HISTORY_MESSAGES) {
      history.remove(0);
    }
  }
}
