package com.jvn.core.generalhelp;

/** A single deterministic agent's recommendation for one help article. */
public record HelpAgentVote(
    String agentName,
    String articleId,
    String title,
    String path,
    double confidence,
    String reason,
    String snippet
) {
  public HelpAgentVote {
    agentName = clean(agentName);
    articleId = clean(articleId);
    title = clean(title);
    path = clean(path);
    confidence = clamp(confidence);
    reason = clean(reason);
    snippet = clean(snippet);
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
