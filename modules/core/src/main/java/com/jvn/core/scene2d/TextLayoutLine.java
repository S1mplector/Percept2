package com.jvn.core.scene2d;

import java.util.List;

public record TextLayoutLine(
    List<TextLayoutRun> runs,
    double width,
    double baseline,
    double ascent,
    double descent,
    double lineHeight,
    boolean rightToLeft,
    int startIndex,
    int endIndex
) {
  public TextLayoutLine {
    runs = List.copyOf(runs);
  }
}
