package com.jvn.core.generalhelp;

/** One message in Jane's short help-chat transcript. */
public record ChatMessage(String role, String content) {
  public ChatMessage {
    role = clean(role).isBlank() ? "user" : clean(role);
    content = clean(content);
  }

  public static ChatMessage user(String content) {
    return new ChatMessage("user", content);
  }

  public static ChatMessage assistant(String content) {
    return new ChatMessage("assistant", content);
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
