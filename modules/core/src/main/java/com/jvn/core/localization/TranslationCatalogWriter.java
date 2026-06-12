package com.jvn.core.localization;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Writes UTF-8 Java properties catalogs while preserving existing translations.
 */
public final class TranslationCatalogWriter {
  private TranslationCatalogWriter() {
  }

  public static WriteResult write(
      Path output,
      List<TranslationEntry> entries,
      String locale,
      String sourceLocale,
      boolean emptyMissing
  ) throws IOException {
    if (output == null) throw new IllegalArgumentException("output path is required");
    List<TranslationEntry> safeEntries = entries == null ? List.of() : entries;
    Properties existing = new Properties();
    if (Files.exists(output)) {
      try (Reader reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
        existing.load(reader);
      }
    }

    if (output.getParent() != null) Files.createDirectories(output.getParent());
    StringBuilder sb = new StringBuilder();
    sb.append("# JVN translation catalog\n");
    sb.append("# Locale: ").append(locale == null || locale.isBlank() ? "en" : locale.trim()).append('\n');
    sb.append("# Source text keys are stable across line moves and are resolved at runtime.\n\n");

    Set<String> emitted = new LinkedHashSet<>();
    int added = 0;
    int preserved = 0;
    for (TranslationEntry entry : safeEntries) {
      if (entry == null || entry.key().isBlank()) continue;
      String previous = existing.getProperty(entry.key());
      String value;
      if (previous != null) {
        value = previous;
        preserved++;
      } else {
        value = emptyMissing && !sameLocale(locale, sourceLocale) ? "" : entry.sourceText();
        added++;
      }
      sb.append("# kind: ").append(entry.kind()).append('\n');
      if (!entry.occurrences().isEmpty()) {
        int limit = Math.min(4, entry.occurrences().size());
        for (int i = 0; i < limit; i++) {
          sb.append("# source: ").append(sanitizeComment(entry.occurrences().get(i))).append('\n');
        }
        if (entry.occurrences().size() > limit) {
          sb.append("# source: +").append(entry.occurrences().size() - limit).append(" more occurrence(s)\n");
        }
      }
      sb.append("# original: ").append(sanitizeComment(entry.sourceText())).append('\n');
      sb.append(escapeKey(entry.key())).append('=').append(escapeValue(value)).append("\n\n");
      emitted.add(entry.key());
    }

    int retained = 0;
    for (String key : existing.stringPropertyNames().stream().sorted().toList()) {
      if (emitted.contains(key)) continue;
      if (retained == 0) {
        sb.append("# Manual or obsolete entries retained from the previous catalog.\n");
      }
      sb.append(escapeKey(key)).append('=').append(escapeValue(existing.getProperty(key, ""))).append('\n');
      retained++;
    }

    Files.writeString(output, sb.toString(), StandardCharsets.UTF_8);
    return new WriteResult(output, safeEntries.size(), added, preserved, retained);
  }

  private static boolean sameLocale(String locale, String sourceLocale) {
    String a = locale == null || locale.isBlank() ? "en" : locale.trim();
    String b = sourceLocale == null || sourceLocale.isBlank() ? "en" : sourceLocale.trim();
    return a.equalsIgnoreCase(b);
  }

  private static String sanitizeComment(String text) {
    if (text == null) return "";
    return text.replace('\r', ' ').replace('\n', ' ').trim();
  }

  private static String escapeKey(String key) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (c == ' ' || c == ':' || c == '=' || c == '#' || c == '!' || c == '\\') out.append('\\');
      out.append(c);
    }
    return out.toString();
  }

  private static String escapeValue(String value) {
    if (value == null) return "";
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (i == 0 && c == ' ') out.append('\\');
          out.append(c);
        }
      }
    }
    return out.toString();
  }

  public record WriteResult(Path output, int entries, int added, int preserved, int retained) {
  }
}
