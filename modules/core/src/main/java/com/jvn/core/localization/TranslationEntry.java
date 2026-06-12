package com.jvn.core.localization;

import java.util.List;

/**
 * One extracted translatable source string and the places it appears.
 */
public record TranslationEntry(
    String key,
    String sourceText,
    String kind,
    List<String> occurrences
) {
  public TranslationEntry {
    key = normalize(key, "");
    sourceText = sourceText == null ? "" : sourceText;
    kind = normalize(kind, "text");
    occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? fallback : trimmed;
  }
}
