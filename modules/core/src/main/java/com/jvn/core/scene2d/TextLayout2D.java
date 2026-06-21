package com.jvn.core.scene2d;

import java.util.List;

/** Immutable multiline layout that can be drawn repeatedly without reflowing. */
public final class TextLayout2D {
  private final List<TextLayoutLine> lines;
  private final double width;
  private final double height;
  private final boolean truncated;
  private final int textLength;

  TextLayout2D(List<TextLayoutLine> lines, double width, double height, boolean truncated, int textLength) {
    this.lines = List.copyOf(lines);
    this.width = width;
    this.height = height;
    this.truncated = truncated;
    this.textLength = textLength;
  }

  public List<TextLayoutLine> lines() { return lines; }
  public double width() { return width; }
  public double height() { return height; }
  public boolean truncated() { return truncated; }
  public int textLength() { return textLength; }

  public TextHitResult hitTest(double x, double y) {
    if (lines.isEmpty()) return new TextHitResult(0, 0, false);
    int lineIndex = lines.size() - 1;
    for (int i = 0; i < lines.size(); i++) {
      TextLayoutLine line = lines.get(i);
      double top = line.baseline() - line.ascent();
      if (y < top + line.lineHeight()) { lineIndex = i; break; }
    }
    TextLayoutLine line = lines.get(lineIndex);
    if (line.runs().isEmpty()) return new TextHitResult(line.startIndex(), lineIndex, false);
    for (TextLayoutRun run : line.runs()) {
      if (x <= run.x() + run.width()) {
        int index = run.hitTest(x - run.x());
        return new TextHitResult(index, lineIndex, x > run.caretX(index));
      }
    }
    return new TextHitResult(line.endIndex(), lineIndex, true);
  }

  public double caretX(int characterIndex) {
    for (TextLayoutLine line : lines) {
      if (characterIndex < line.startIndex() || characterIndex > line.endIndex()) continue;
      for (TextLayoutRun run : line.runs()) {
        if (characterIndex >= run.startIndex() && characterIndex <= run.endIndex()) {
          return run.caretX(characterIndex);
        }
      }
    }
    if (lines.isEmpty() || lines.get(lines.size() - 1).runs().isEmpty()) return 0.0;
    TextLayoutRun last = lines.get(lines.size() - 1).runs().get(
        lines.get(lines.size() - 1).runs().size() - 1);
    return last.x() + last.width();
  }
}
