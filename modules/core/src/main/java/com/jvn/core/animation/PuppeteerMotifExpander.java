package com.jvn.core.animation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands named, parameterized Puppeteer {@code motif} fragments. */
public final class PuppeteerMotifExpander {
  private static final Pattern DEFINITION = Pattern.compile(
      "(?m)^\\s*motif\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*\\{");
  private static final Pattern USE = Pattern.compile(
      "(?m)^(\\s*)use\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*$");

  private record Motif(String body, LinkedHashMap<String, String> defaults) { }

  private PuppeteerMotifExpander() { }

  public static String expand(String source) {
    if (source == null || source.isBlank()) return source == null ? "" : source;
    Map<String, Motif> motifs = new LinkedHashMap<>();
    StringBuilder remaining = new StringBuilder(source);
    int searchFrom = 0;
    while (true) {
      Matcher matcher = DEFINITION.matcher(remaining);
      if (!matcher.find(searchFrom)) break;
      int close = matchingBrace(remaining, matcher.end() - 1);
      if (close < 0) break;
      motifs.put(matcher.group(1), new Motif(
          remaining.substring(matcher.end(), close), parseParameters(matcher.group(2))));
      remaining.delete(matcher.start(), close + 1);
      searchFrom = matcher.start();
    }

    String expanded = remaining.toString();
    for (int pass = 0; pass < 16; pass++) {
      Matcher use = USE.matcher(expanded);
      StringBuffer out = new StringBuffer();
      boolean changed = false;
      while (use.find()) {
        Motif motif = motifs.get(use.group(2));
        if (motif == null) continue;
        LinkedHashMap<String, String> values = new LinkedHashMap<>(motif.defaults());
        values.putAll(parseArguments(use.group(3)));
        String body = motif.body();
        for (Map.Entry<String, String> entry : values.entrySet()) {
          body = body.replace("${" + entry.getKey() + "}", unquote(entry.getValue()));
        }
        String indent = use.group(1);
        body = indentBody(body.strip(), indent);
        use.appendReplacement(out, Matcher.quoteReplacement(body));
        changed = true;
      }
      use.appendTail(out);
      expanded = out.toString();
      if (!changed) break;
    }
    return expanded;
  }

  private static LinkedHashMap<String, String> parseArguments(String raw) {
    LinkedHashMap<String, String> values = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) return values;
    List<String> parts = splitComma(raw);
    int positional = 0;
    for (String part : parts) {
      int equals = part.indexOf('=');
      if (equals > 0) values.put(part.substring(0, equals).trim(), part.substring(equals + 1).trim());
      else values.put(Integer.toString(positional++), part.trim());
    }
    return values;
  }

  private static LinkedHashMap<String, String> parseParameters(String raw) {
    LinkedHashMap<String, String> values = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) return values;
    for (String part : splitComma(raw)) {
      int equals = part.indexOf('=');
      String name = (equals > 0 ? part.substring(0, equals) : part).trim();
      String value = equals > 0 ? part.substring(equals + 1).trim() : "";
      if (!name.isEmpty()) values.put(name, value);
    }
    return values;
  }

  private static List<String> splitComma(String raw) {
    List<String> out = new ArrayList<>();
    StringBuilder part = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '"' && (i == 0 || raw.charAt(i - 1) != '\\')) quoted = !quoted;
      if (c == ',' && !quoted) {
        out.add(part.toString().trim());
        part.setLength(0);
      } else part.append(c);
    }
    if (!part.isEmpty()) out.add(part.toString().trim());
    return out;
  }

  private static int matchingBrace(CharSequence source, int open) {
    int depth = 0;
    boolean quoted = false;
    for (int i = open; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '"' && (i == 0 || source.charAt(i - 1) != '\\')) quoted = !quoted;
      if (quoted) continue;
      if (c == '{') depth++;
      else if (c == '}' && --depth == 0) return i;
    }
    return -1;
  }

  private static String indentBody(String body, String indent) {
    String[] lines = body.split("\\R", -1);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) out.append('\n');
      out.append(indent).append(lines[i]);
    }
    return out.toString();
  }

  private static String unquote(String value) {
    if (value == null) return "";
    String trimmed = value.trim();
    return trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")
        ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
  }
}
