package com.jvn.core.generalhelp;

import java.util.List;
import java.util.Map;

/** Result returned by the three-agent consensus help system. */
public record HelpResponse(
    String query,
    String answer,
    double confidence,
    List<HelpArticle> recommendedArticles,
    List<HelpAgentVote> votes,
    Map<String, Double> consensusScores
) {
  public HelpResponse {
    query = clean(query);
    answer = clean(answer);
    confidence = clamp(confidence);
    recommendedArticles = recommendedArticles == null ? List.of() : List.copyOf(recommendedArticles);
    votes = votes == null ? List.of() : List.copyOf(votes);
    consensusScores = consensusScores == null ? Map.of() : Map.copyOf(consensusScores);
  }

  private static double clamp(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) return 0.0;
    if (value > 1.0) return 1.0;
    return value;
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
