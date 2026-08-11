package com.jvn.fx.vn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Computes a viewport-bounded, content-sized layout for VN HUD toasts. */
final class HudToastLayout {
  static final double HORIZONTAL_PADDING = 12.0;
  static final double VERTICAL_PADDING = 10.0;

  private HudToastLayout() {
  }

  record Layout(double width, double height, List<String> lines) {
    Layout {
      lines = lines == null ? List.of() : List.copyOf(lines);
    }
  }

  static Layout compute(
      String message,
      double viewportWidth,
      double lineHeight,
      ToDoubleFunction<String> measure
  ) {
    String text = message == null ? "" : message.strip();
    double safeViewportWidth = Math.max(1.0, viewportWidth);
    double maxBoxWidth = Math.max(
        40.0,
        Math.min(Math.min(720.0, safeViewportWidth * 0.72), safeViewportWidth - 32.0));
    double maxTextWidth = Math.max(16.0, maxBoxWidth - HORIZONTAL_PADDING * 2.0);
    double safeLineHeight = Math.max(1.0, lineHeight);

    List<String> lines = wrap(text, maxTextWidth, measure);
    if (lines.isEmpty()) lines = List.of("");
    double longestLine = lines.stream().mapToDouble(measure).max().orElse(0.0);
    double minimumWidth = Math.min(140.0, maxBoxWidth);
    double boxWidth = Math.min(
        maxBoxWidth,
        Math.max(minimumWidth, longestLine + HORIZONTAL_PADDING * 2.0));
    double boxHeight = VERTICAL_PADDING * 2.0 + safeLineHeight * lines.size();
    return new Layout(boxWidth, boxHeight, lines);
  }

  private static List<String> wrap(
      String text,
      double maxWidth,
      ToDoubleFunction<String> measure
  ) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isBlank()) return lines;
    for (String paragraph : text.split("\\R", -1)) {
      if (paragraph.isBlank()) {
        lines.add("");
        continue;
      }
      StringBuilder current = new StringBuilder();
      for (String word : paragraph.trim().split("\\s+")) {
        String candidate = current.isEmpty() ? word : current + " " + word;
        if (measure.applyAsDouble(candidate) <= maxWidth) {
          current.setLength(0);
          current.append(candidate);
          continue;
        }
        if (!current.isEmpty()) {
          lines.add(current.toString());
          current.setLength(0);
        }
        appendBrokenWord(lines, current, word, maxWidth, measure);
      }
      if (!current.isEmpty()) lines.add(current.toString());
    }
    return lines;
  }

  private static void appendBrokenWord(
      List<String> lines,
      StringBuilder current,
      String word,
      double maxWidth,
      ToDoubleFunction<String> measure
  ) {
    String remaining = word == null ? "" : word;
    while (!remaining.isEmpty() && measure.applyAsDouble(remaining) > maxWidth) {
      int end = fittingEnd(remaining, maxWidth, measure);
      lines.add(remaining.substring(0, end));
      remaining = remaining.substring(end);
    }
    current.append(remaining);
  }

  private static int fittingEnd(
      String text,
      double maxWidth,
      ToDoubleFunction<String> measure
  ) {
    int low = 1;
    int high = text.length();
    while (low < high) {
      int middle = (low + high + 1) >>> 1;
      if (measure.applyAsDouble(text.substring(0, middle)) <= maxWidth) {
        low = middle;
      } else {
        high = middle - 1;
      }
    }
    return low;
  }
}
