package com.jvn.core.generalhelp;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline general-help assistant built from three deterministic agents.
 *
 * <p>TAGI here means "three-agent general intelligence": intent, evidence,
 * and workflow agents independently rank the local help corpus, then a weighted
 * consensus stage selects the answer. It does not call an LLM or any network
 * service.</p>
 */
public final class TagiGeneralHelpSystem {
  private static final int MAX_AGENT_VOTES = 5;
  private static final int MAX_ANSWER_ARTICLES = 4;
  private static final Map<String, Double> AGENT_WEIGHTS = Map.of(
      "intent", 1.0,
      "evidence", 1.2,
      "workflow", 0.9
  );
  private static final Set<String> STOP_WORDS = Set.of(
      "a", "about", "after", "all", "am", "an", "and", "any", "are", "as", "at",
      "be", "but", "by", "can", "do", "does", "for", "from", "get", "how", "i",
      "in", "into", "is", "it", "me", "my", "of", "on", "or", "please", "should",
      "the", "this", "to", "use", "want", "what", "when", "where", "with", "you"
  );
  private static final Set<String> WORKFLOW_WORDS = Set.of(
      "start", "setup", "create", "build", "package", "release", "workflow", "guide",
      "tutorial", "example", "best", "practice", "debug", "error", "fix", "diagnostic",
      "add", "make", "write", "author"
  );

  private final List<HelpArticle> articles = new ArrayList<>();
  private final List<Agent> agents = List.of(
      new IntentAgent(),
      new EvidenceAgent(),
      new WorkflowAgent()
  );

  public void setArticles(List<HelpArticle> nextArticles) {
    articles.clear();
    if (nextArticles == null) return;
    for (HelpArticle article : nextArticles) {
      addArticle(article);
    }
  }

  public void addArticle(HelpArticle article) {
    if (article == null) return;
    String id = !article.id().isBlank() ? article.id() : article.path();
    if (id.isBlank()) return;
    articles.removeIf(existing -> existing.id().equals(id));
    articles.add(article);
  }

  public void clearArticles() {
    articles.clear();
  }

  public List<HelpArticle> articles() {
    return List.copyOf(articles);
  }

  public HelpResponse ask(String query) {
    QueryProfile profile = QueryProfile.from(query);
    if (profile.normalized().isBlank()) {
      return new HelpResponse(query, "Ask a specific JVN question so TAGI can rank the local docs.", 0.0,
          List.of(), List.of(), Map.of());
    }
    if (articles.isEmpty()) {
      return new HelpResponse(query, "No help corpus is loaded yet. Index workspace or project docs before asking.", 0.0,
          List.of(), List.of(), Map.of());
    }

    List<HelpAgentVote> votes = new ArrayList<>();
    for (Agent agent : agents) {
      votes.addAll(agent.vote(profile, articles));
    }
    votes = votes.stream()
        .filter(v -> v.confidence() > 0.0)
        .sorted(Comparator.comparingDouble(HelpAgentVote::confidence).reversed()
            .thenComparing(HelpAgentVote::title, String.CASE_INSENSITIVE_ORDER))
        .toList();
    if (votes.isEmpty()) {
      return new HelpResponse(
          query,
          "TAGI did not find a confident match in the indexed docs. Try using concrete terms such as VNS, JES, menu, timeline, packaging, or diagnostics.",
          0.05,
          List.of(),
          List.of(),
          Map.of());
    }

    Map<String, Double> scores = consensusScores(votes);
    List<HelpArticle> recommended = topArticles(scores, profile);
    double confidence = recommended.isEmpty() ? 0.0 : Math.min(1.0, scores.getOrDefault(recommended.get(0).id(), 0.0));
    return new HelpResponse(query, buildAnswer(profile, recommended, votes, confidence), confidence,
        recommended, votes, scores);
  }

  private Map<String, Double> consensusScores(List<HelpAgentVote> votes) {
    Map<String, Double> totals = new HashMap<>();
    Map<String, Set<String>> agentsByArticle = new HashMap<>();
    for (HelpAgentVote vote : votes) {
      double weight = AGENT_WEIGHTS.getOrDefault(vote.agentName(), 1.0);
      totals.merge(vote.articleId(), vote.confidence() * weight, Double::sum);
      agentsByArticle.computeIfAbsent(vote.articleId(), ignored -> new LinkedHashSet<>()).add(vote.agentName());
    }
    Map<String, Double> normalized = new LinkedHashMap<>();
    totals.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .forEach(entry -> {
          int agreeingAgents = agentsByArticle.getOrDefault(entry.getKey(), Set.of()).size();
          double agreementBoost = agreeingAgents >= 3 ? 1.25 : agreeingAgents == 2 ? 1.1 : 0.85;
          normalized.put(entry.getKey(), Math.min(1.0, (entry.getValue() / agents.size()) * agreementBoost));
        });
    return normalized;
  }

  private List<HelpArticle> topArticles(Map<String, Double> scores, QueryProfile profile) {
    Map<String, HelpArticle> byId = new HashMap<>();
    for (HelpArticle article : articles) {
      byId.put(article.id(), article);
    }
    return scores.entrySet().stream()
        .sorted((left, right) -> Double.compare(
            right.getValue() + finalDomainBoost(profile, byId.get(right.getKey())),
            left.getValue() + finalDomainBoost(profile, byId.get(left.getKey()))))
        .limit(MAX_ANSWER_ARTICLES)
        .map(entry -> byId.get(entry.getKey()))
        .filter(article -> article != null)
        .toList();
  }

  private static double finalDomainBoost(QueryProfile profile, HelpArticle article) {
    if (profile == null || article == null) return 0.0;
    String path = article.path().toLowerCase(Locale.ROOT);
    String title = article.title().toLowerCase(Locale.ROOT);
    String id = article.id().toLowerCase(Locale.ROOT);
    double boost = 0.0;
    if (profile.hasAny("vns", "visual", "novel", "dialogue", "choice", "branch", "label")
        && (path.contains("/vns/") || path.contains("vns-by-example") || id.contains("jane-vns"))) {
      boost += 1.0;
    }
    if (profile.hasAny("choice", "branch") && (title.contains("choice") || path.contains("choice"))) {
      boost += 0.5;
    }
    if (profile.hasAny("jes", "scene", "entity", "component", "tilemap", "physics")
        && (path.contains("/jes/") || path.contains("jes-by-example") || id.contains("jane-jes"))) {
      boost += 1.0;
    }
    if (profile.hasAny("timeline", "animation", "puppeteer", "keyframe")
        && (path.contains("timeline") || path.contains("puppeteer") || id.contains("animation"))) {
      boost += 1.0;
    }
    if (profile.hasAny("menu", "layout", "ui", "button", "theme")
        && (path.contains("/ui/") || path.contains("menu") || id.contains("ui-menu"))) {
      boost += 1.0;
    }
    if (path.contains("/architecture/") || path.contains("tagi-general-help")) {
      boost -= 0.8;
    }
    return boost;
  }

  private String buildAnswer(
      QueryProfile profile,
      List<HelpArticle> recommended,
      List<HelpAgentVote> votes,
      double confidence
  ) {
    if (recommended.isEmpty()) {
      return "TAGI did not find a usable consensus answer.";
    }

    HelpArticle top = recommended.get(0);
    List<HelpAgentVote> topVotes = votes.stream()
        .filter(v -> v.articleId().equals(top.id()))
        .toList();

    StringBuilder answer = new StringBuilder();
    answer.append("TAGI consensus (").append(percent(confidence)).append("): start with ")
        .append(top.title()).append(".");
    if (!top.summary().isBlank()) {
      answer.append(" ").append(top.summary());
    }
    answer.append("\n\nWhy this matched:");
    for (HelpAgentVote vote : topVotes) {
      answer.append("\n- ").append(toTitle(vote.agentName())).append(": ").append(vote.reason());
    }
    answer.append("\n\nRecommended docs:");
    for (int i = 0; i < recommended.size(); i++) {
      HelpArticle article = recommended.get(i);
      answer.append("\n").append(i + 1).append(". ").append(article.title());
      if (!article.path().isBlank()) answer.append(" — ").append(article.path());
    }
    String suggestedStep = suggestedStep(profile, top);
    if (!suggestedStep.isBlank()) {
      answer.append("\n\nNext step: ").append(suggestedStep);
    }
    return answer.toString();
  }

  private String suggestedStep(QueryProfile profile, HelpArticle top) {
    if (profile.hasAny("error", "debug", "diagnostic", "failed", "fix")) {
      return "open the top diagnostic or troubleshooting doc, then compare the exact error text against its examples.";
    }
    if (profile.hasAny("start", "begin", "tutorial", "example", "new")) {
      return "follow the top guide in order before jumping into reference pages.";
    }
    if (profile.hasAny("script", "vns", "jes", "timeline", "menu")) {
      return "open the top reference and copy the smallest matching snippet into your project before expanding it.";
    }
    if (!top.path().isBlank()) {
      return "open " + top.path() + " and scan its first headings.";
    }
    return "";
  }

  private static String percent(double value) {
    return Math.round(value * 100.0) + "%";
  }

  private static String toTitle(String raw) {
    if (raw == null || raw.isBlank()) return "";
    return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
  }

  private interface Agent {
    List<HelpAgentVote> vote(QueryProfile profile, List<HelpArticle> articles);
  }

  private static final class IntentAgent implements Agent {
    @Override
    public List<HelpAgentVote> vote(QueryProfile profile, List<HelpArticle> articles) {
      return articles.stream()
          .map(article -> {
            double score = 0.0;
            score += overlap(profile.tokens(), tokenize(article.title())) * 0.50;
            score += overlap(profile.tokens(), tokenize(article.summary())) * 0.32;
            score += overlap(profile.tokens(), tokenize(article.path())) * 0.18;
            if (profile.normalized().contains(article.title().toLowerCase(Locale.ROOT))) score += 0.25;
            return agentVote("intent", article, score, "query terms match the document title, summary, or path",
                article.summary());
          })
          .filter(v -> v.confidence() > 0.0)
          .sorted(Comparator.comparingDouble(HelpAgentVote::confidence).reversed())
          .limit(MAX_AGENT_VOTES)
          .toList();
    }
  }

  private static final class EvidenceAgent implements Agent {
    @Override
    public List<HelpAgentVote> vote(QueryProfile profile, List<HelpArticle> articles) {
      return articles.stream()
          .map(article -> {
            String searchable = article.title() + "\n" + article.summary() + "\n" + article.body();
            double score = overlap(profile.tokens(), tokenize(searchable));
            String snippet = bestSnippet(article, profile.tokens());
            if (!snippet.isBlank()) score += 0.18;
            if (!profile.normalized().isBlank()
                && searchable.toLowerCase(Locale.ROOT).contains(profile.normalized())) {
              score += 0.22;
            }
            return agentVote("evidence", article, score, "local doc text contains the strongest evidence",
                snippet.isBlank() ? article.summary() : snippet);
          })
          .filter(v -> v.confidence() > 0.0)
          .sorted(Comparator.comparingDouble(HelpAgentVote::confidence).reversed())
          .limit(MAX_AGENT_VOTES)
          .toList();
    }
  }

  private static final class WorkflowAgent implements Agent {
    @Override
    public List<HelpAgentVote> vote(QueryProfile profile, List<HelpArticle> articles) {
      return articles.stream()
          .map(article -> {
            String path = article.path().toLowerCase(Locale.ROOT);
            String title = article.title().toLowerCase(Locale.ROOT);
            double score = 0.0;
            if (profile.tokens().stream().anyMatch(WORKFLOW_WORDS::contains)) {
              if (path.contains("guide") || path.contains("tutorial") || path.contains("example")) score += 0.34;
              if (path.contains("workflow") || title.contains("workflow")) score += 0.28;
              if (path.contains("diagnostic") || title.contains("diagnostic")) score += 0.24;
              if (path.contains("overview") || title.contains("overview")) score += 0.18;
            }
            score += domainBoost(profile, article);
            return agentVote("workflow", article, score, "document position fits the likely workflow",
                article.summary());
          })
          .filter(v -> v.confidence() > 0.0)
          .sorted(Comparator.comparingDouble(HelpAgentVote::confidence).reversed())
          .limit(MAX_AGENT_VOTES)
          .toList();
    }

    private double domainBoost(QueryProfile profile, HelpArticle article) {
      String path = article.path().toLowerCase(Locale.ROOT);
      String title = article.title().toLowerCase(Locale.ROOT);
      String id = article.id().toLowerCase(Locale.ROOT);
      double score = 0.0;
      if (profile.hasAny("vns", "visual", "novel", "dialogue", "choice")
          && (path.contains("/vns/") || path.contains("vns-by-example") || id.contains("jane-vns"))) {
        score += 0.75;
      }
      if (profile.hasAny("jes", "scene", "entity", "component")
          && (path.contains("/jes/") || path.contains("jes-by-example") || id.contains("jane-jes"))) {
        score += 0.75;
      }
      if (profile.hasAny("timeline", "animation", "puppeteer") && (path.contains("timeline") || path.contains("puppeteer"))) {
        score += 0.75;
      }
      if (profile.hasAny("menu", "layout", "ui", "button") && (path.contains("/ui/") || path.contains("menu"))) {
        score += 0.75;
      }
      if (profile.hasAny("package", "build", "release", "launcher") && (path.contains("runtime") || path.contains("project-setup"))) {
        score += 0.55;
      }
      if (profile.hasAny("asset", "audio", "save", "setting", "localization") && (path.contains("runtime") || path.contains("asset"))) {
        score += 0.28;
      }
      if (profile.hasAny("editor", "sidebar", "inspector", "diagnostic", "console") && path.contains("editor")) {
        score += 0.28;
      }
      if (profile.hasAny("debug", "error", "warning", "validation") && (path.contains("diagnostic") || path.contains("debug") || path.contains("quality"))) {
        score += 0.28;
      }
      if (path.contains("tagi-general-help") || title.contains("tagi general help") || path.contains("/architecture/")) {
        score -= 0.25;
      }
      return score;
    }
  }

  private static HelpAgentVote agentVote(
      String agent,
      HelpArticle article,
      double rawScore,
      String reason,
      String snippet
  ) {
    double confidence = Math.min(1.0, Math.max(0.0, rawScore));
    return new HelpAgentVote(agent, article.id(), article.title(), article.path(), confidence,
        reason, compact(snippet, 180));
  }

  private static double overlap(Set<String> queryTokens, Set<String> docTokens) {
    if (queryTokens.isEmpty() || docTokens.isEmpty()) return 0.0;
    int matches = 0;
    for (String token : queryTokens) {
      if (docTokens.contains(token)) matches++;
    }
    return (double) matches / Math.max(3, queryTokens.size());
  }

  private static String bestSnippet(HelpArticle article, Set<String> tokens) {
    String body = article.body().isBlank() ? article.summary() : article.body();
    if (body.isBlank() || tokens.isEmpty()) return "";
    BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
    iterator.setText(body);
    String best = "";
    int bestScore = 0;
    int start = iterator.first();
    for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
      String sentence = body.substring(start, end).trim();
      if (sentence.isBlank()) continue;
      Set<String> sentenceTokens = tokenize(sentence);
      int score = 0;
      for (String token : tokens) {
        if (sentenceTokens.contains(token)) score++;
      }
      if (score > bestScore) {
        bestScore = score;
        best = sentence;
      }
    }
    return bestScore <= 0 ? "" : best;
  }

  private static Set<String> tokenize(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    String[] parts = raw.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+");
    Set<String> tokens = new LinkedHashSet<>();
    for (String part : parts) {
      if (part.length() < 2 || STOP_WORDS.contains(part)) continue;
      tokens.add(stem(part));
    }
    return tokens;
  }

  private static String stem(String token) {
    if (token.endsWith("ies") && token.length() > 4) return token.substring(0, token.length() - 3) + "y";
    if (token.endsWith("ing") && token.length() > 5) return token.substring(0, token.length() - 3);
    if (token.endsWith("ed") && token.length() > 4) return token.substring(0, token.length() - 2);
    if (token.endsWith("s") && token.length() > 3) return token.substring(0, token.length() - 1);
    return token;
  }

  private static String compact(String raw, int maxLength) {
    if (raw == null) return "";
    String normalized = raw.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= maxLength) return normalized;
    return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + "...";
  }

  private record QueryProfile(String normalized, Set<String> tokens) {
    static QueryProfile from(String raw) {
      String normalized = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
      return new QueryProfile(normalized, tokenize(normalized));
    }

    boolean hasAny(String... values) {
      if (values == null) return false;
      for (String value : values) {
        if (value != null && tokens.contains(stem(value.toLowerCase(Locale.ROOT)))) return true;
      }
      return false;
    }
  }
}
