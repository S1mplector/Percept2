package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizes interop argument strings with support for:
 * - double-quoted segments with spaces
 * - backslash escapes (for quotes, spaces, and backslashes)
 */
public final class VnArgTokenizer {
  private VnArgTokenizer() {
  }

  public static List<String> tokenize(String input) {
    List<String> out = new ArrayList<>();
    if (input == null || input.isBlank()) return out;

    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    boolean escaping = false;

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (escaping) {
        current.append(c);
        escaping = false;
        continue;
      }
      if (c == '\\') {
        escaping = true;
        continue;
      }
      if (inQuotes) {
        if (c == '"') {
          inQuotes = false;
        } else {
          current.append(c);
        }
        continue;
      }
      if (c == '"') {
        inQuotes = true;
        continue;
      }
      if (Character.isWhitespace(c)) {
        if (current.length() > 0) {
          out.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(c);
    }

    if (escaping) current.append('\\');
    if (current.length() > 0) out.add(current.toString());
    return out;
  }

  public static String[] tokenizeToArray(String input) {
    List<String> tokens = tokenize(input);
    return tokens.toArray(new String[0]);
  }
}
