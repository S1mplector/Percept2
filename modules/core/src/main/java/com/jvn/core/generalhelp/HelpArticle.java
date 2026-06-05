package com.jvn.core.generalhelp;

import java.util.Map;

/** A searchable help document used by the local TAGI general-help system. */
public record HelpArticle(
    String id,
    String title,
    String summary,
    String path,
    String body,
    Map<String, String> metadata
) {
  public HelpArticle {
    id = clean(id);
    title = clean(title);
    summary = clean(summary);
    path = clean(path);
    body = clean(body);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public static HelpArticle of(String id, String title, String summary, String path, String body) {
    return new HelpArticle(id, title, summary, path, body, Map.of());
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
