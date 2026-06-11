package com.jvn.editor.ui.build;

import java.util.ArrayList;
import java.util.List;

public class BuildCliFormatter {

  public static String safeToken(String value) {
    String sanitized = (value == null ? "" : value.trim())
        .replaceAll("[^A-Za-z0-9._-]+", "-")
        .replaceAll("^[._-]+|[._-]+$", "");
    return sanitized.isBlank() ? "jvn-game" : sanitized;
  }

  public static String safeNativeVersionToken(String value) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(value == null ? "" : value);
    List<String> parts = new ArrayList<>();
    while (matcher.find() && parts.size() < 3) {
      parts.add(matcher.group());
    }
    while (parts.size() < 3) {
      parts.add("0");
    }
    if (parts.isEmpty() || "0".equals(parts.get(0))) {
      parts.set(0, "1");
    }
    return String.join(".", parts);
  }

  public static String shellQuote(String value) {
    if (value == null || value.isBlank()) return "''";
    if (value.matches("[A-Za-z0-9_./:=@-]+")) return value;
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  public static String buildCliCommand(String taskName, List<String> args) {
    StringBuilder cmd = new StringBuilder("./jvnw gradle");
    if (taskName != null && !taskName.isBlank()) {
      cmd.append(' ').append(shellQuote(taskName));
    }
    if (args != null) {
      for (String arg : args) {
        if (arg != null && !arg.isBlank()) {
          cmd.append(' ').append(shellQuote(arg));
        }
      }
    }
    return cmd.toString();
  }
}
