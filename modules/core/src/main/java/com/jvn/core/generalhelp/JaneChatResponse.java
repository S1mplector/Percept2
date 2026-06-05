package com.jvn.core.generalhelp;

import java.util.List;

/** Chatbot response from Jane, JVN's assistant. */
public record JaneChatResponse(
    String assistantName,
    String answer,
    String modelName,
    boolean modelUsed,
    HelpResponse grounding,
    List<ChatMessage> transcript
) {
  public JaneChatResponse {
    assistantName = clean(assistantName).isBlank() ? "Jane" : clean(assistantName);
    answer = clean(answer);
    modelName = clean(modelName);
    grounding = grounding == null
        ? new HelpResponse("", "", 0.0, List.of(), List.of(), java.util.Map.of())
        : grounding;
    transcript = transcript == null ? List.of() : List.copyOf(transcript);
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
