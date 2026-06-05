package com.jvn.core.generalhelp;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Always-available local Jane model trained on TAGI grounding and curated JVN expertise. */
public final class JaneExpertLocalModel implements LocalChatModel {
  @Override
  public String name() {
    return "Jane local expert";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public String generate(String query, HelpResponse grounding, List<ChatMessage> history) {
    if (grounding == null || grounding.recommendedArticles().isEmpty()) {
      return "I'm Jane. I do not have enough indexed JVN context for that yet. Ask with a concrete subsystem name such as VNS, JES, Puppeteer, menus, assets, packaging, or diagnostics.";
    }

    HelpArticle top = grounding.recommendedArticles().get(0);
    String domain = inferDomain(query, grounding);
    StringBuilder answer = new StringBuilder();
    answer.append("I'm Jane. For ").append(domain).append(", start with **")
        .append(top.title()).append("**.");
    if (!top.summary().isBlank()) {
      answer.append(" ").append(top.summary());
    }

    String evidence = bestEvidence(grounding, top.id());
    if (!evidence.isBlank()) {
      answer.append("\n\nWhat matters here: ").append(evidence);
    }

    answer.append("\n\nPractical workflow:");
    for (String step : workflowSteps(query, grounding)) {
      answer.append("\n- ").append(step);
    }

    answer.append("\n\nOpen these docs first:");
    int index = 1;
    for (HelpArticle article : grounding.recommendedArticles()) {
      answer.append("\n").append(index++).append(". ").append(article.title());
      if (!article.path().isBlank() && !article.path().startsWith("jane://")) {
        answer.append(" - ").append(article.path());
      }
    }
    return answer.toString();
  }

  private static String inferDomain(String query, HelpResponse grounding) {
    String text = ((query == null ? "" : query) + " "
        + grounding.recommendedArticles().stream()
            .map(article -> article.title() + " " + article.path())
            .collect(Collectors.joining(" "))).toLowerCase(Locale.ROOT);
    if (containsAny(text, "vns", "visual novel", "dialogue", "choice", "branch")) return "VNS story scripting";
    if (containsAny(text, "jes", "scene", "entity", "component", "tilemap", "physics")) return "JES gameplay scripting";
    if (containsAny(text, "puppeteer", "timeline", "keyframe", "animation")) return "animation and timelines";
    if (containsAny(text, "menu", "layout", "ui", "button", "theme")) return "menus and UI layout";
    if (containsAny(text, "asset", "audio", "save", "runtime", "package", "release")) return "runtime and project workflow";
    if (containsAny(text, "debug", "error", "diagnostic", "warning")) return "debugging";
    if (containsAny(text, "editor", "sidebar", "inspector", "console")) return "editor workflow";
    return "JVN";
  }

  private static List<String> workflowSteps(String query, HelpResponse grounding) {
    String text = ((query == null ? "" : query) + " " + grounding.answer()).toLowerCase(Locale.ROOT);
    if (containsAny(text, "vns", "choice", "dialogue", "branch")) {
      return List.of(
          "Author the smallest `.vns` scene that proves the dialogue or branch.",
          "Use labels, variables, and choices for flow before adding presentation polish.",
          "Run VNS diagnostics and open the label flow map if navigation is unclear.");
    }
    if (containsAny(text, "jes", "entity", "component", "physics", "tilemap")) {
      return List.of(
          "Start from a minimal `.jes` scene with one entity and one behavior.",
          "Add components incrementally: sprite or label first, then input, physics, AI, or RPG data.",
          "Use call handlers only after the scene works in isolation.");
    }
    if (containsAny(text, "timeline", "puppeteer", "keyframe", "animation")) {
      return List.of(
          "Block the motion in Puppeteer with stable asset paths.",
          "Add keyframes and easing, then preview before exporting timeline data.",
          "Trigger the exported timeline from VNS or JES only after the timeline plays cleanly.");
    }
    if (containsAny(text, "menu", "layout", "ui", "theme")) {
      return List.of(
          "Define the screen structure first, then refine layout and style.",
          "Use menu/layout diagnostics to catch missing IDs, bad bounds, and invalid references.",
          "Keep reusable button and dialogue patterns in shared profile/layout files.");
    }
    if (containsAny(text, "debug", "error", "diagnostic", "warning")) {
      return List.of(
          "Copy the exact error text from the run console or diagnostics panel.",
          "Open the top recommended diagnostic doc and compare its examples.",
          "Reduce the issue to one script, scene, menu, or asset path before changing multiple systems.");
    }
    return List.of(
        "Open the top recommended doc and scan its first headings.",
        "Create the smallest project file that exercises the feature.",
        "Use the relevant editor tool or diagnostics panel before expanding the workflow.");
  }

  private static String bestEvidence(HelpResponse grounding, String articleId) {
    return grounding.votes().stream()
        .filter(vote -> articleId == null || articleId.isBlank() || vote.articleId().equals(articleId))
        .filter(vote -> vote.snippet() != null && !vote.snippet().isBlank())
        .sorted(Comparator.comparingDouble(HelpAgentVote::confidence).reversed())
        .map(HelpAgentVote::snippet)
        .findFirst()
        .orElse("");
  }

  private static boolean containsAny(String text, String... needles) {
    if (text == null || needles == null) return false;
    for (String needle : needles) {
      if (needle != null && !needle.isBlank() && text.contains(needle)) return true;
    }
    return false;
  }
}
