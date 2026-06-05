package com.jvn.core.generalhelp;

import java.util.List;

/** Built-in model-free Jane backend that formats TAGI evidence as a chat answer. */
public final class TagiGroundedChatModel implements LocalChatModel {
  @Override
  public String name() {
    return "TAGI grounded fallback";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public String generate(String query, HelpResponse grounding, List<ChatMessage> history) {
    if (grounding == null || grounding.answer().isBlank()) {
      return "I could not find a grounded answer in the indexed JVN docs yet.";
    }
    StringBuilder answer = new StringBuilder();
    answer.append("I'm Jane. ").append(grounding.answer());
    if (!grounding.recommendedArticles().isEmpty()) {
      answer.append("\n\nI would open these first:");
      int index = 1;
      for (HelpArticle article : grounding.recommendedArticles()) {
        answer.append("\n").append(index++).append(". ").append(article.title());
        if (!article.path().isBlank()) answer.append(" - ").append(article.path());
      }
    }
    return answer.toString();
  }
}
