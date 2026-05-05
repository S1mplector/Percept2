package com.jvn.core.scene2d;

import java.util.ArrayList;
import java.util.List;

public class TextMetrics {
  public static class Layout {
    public final List<String> lines;
    public final double width;
    public final double height;
    public Layout(List<String> lines, double width, double height) {
      this.lines = lines;
      this.width = width;
      this.height = height;
    }
  }

  public static Layout layout(Blitter2D b, String text, double size, boolean bold, double maxWidth, double lineHeight) {
    if (text == null) text = "";
    String[] words = text.split(" ");
    List<String> lines = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    double usedWidth = 0;
    for (String w : words) {
      String test = current.length() == 0 ? w : current + " " + w;
      double tw = b.measureTextWidth(test, size, bold);
      if (maxWidth > 0 && tw > maxWidth && current.length() > 0) {
        lines.add(current.toString());
        usedWidth = Math.max(usedWidth, b.measureTextWidth(current.toString(), size, bold));
        current = new StringBuilder(w);
      } else {
        current = new StringBuilder(test);
      }
    }
    if (current.length() > 0) {
      lines.add(current.toString());
      usedWidth = Math.max(usedWidth, b.measureTextWidth(current.toString(), size, bold));
    }
    double h = Math.max(lineHeight, lineHeight * Math.max(1, lines.size()));
    return new Layout(lines, usedWidth, h);
  }
}
