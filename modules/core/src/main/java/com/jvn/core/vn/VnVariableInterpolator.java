package com.jvn.core.vn;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Performs one-pass VN variable interpolation for text templates.
 *
 * <p>Supported syntax: <code>${varName}</code></p>
 * <p>Missing variables resolve to an empty string.</p>
 */
public final class VnVariableInterpolator {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)\\}");

  private VnVariableInterpolator() {
  }

  public static String interpolate(String template, Map<String, ?> variables) {
    if (template == null) return "";
    if (template.isEmpty()) return template;
    if (variables == null || variables.isEmpty()) {
      return PLACEHOLDER.matcher(template).replaceAll("");
    }

    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuffer out = new StringBuffer();
    while (matcher.find()) {
      String key = matcher.group(1);
      Object value = variables.get(key);
      String replacement = value == null ? "" : String.valueOf(value);
      matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(out);
    return out.toString();
  }
}
