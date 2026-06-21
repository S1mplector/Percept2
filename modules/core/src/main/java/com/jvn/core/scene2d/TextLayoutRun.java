package com.jvn.core.scene2d;

import java.util.Arrays;
import java.util.Objects;

/** A positioned, consistently styled run within a laid-out line. */
public final class TextLayoutRun {
  private final String text;
  private final TextStyle2D style;
  private final int startIndex;
  private final int endIndex;
  private final double x;
  private final double width;
  private final int[] caretIndices;
  private final double[] caretOffsets;

  TextLayoutRun(
      String text,
      TextStyle2D style,
      int startIndex,
      int endIndex,
      double x,
      double width,
      int[] caretIndices,
      double[] caretOffsets
  ) {
    this.text = Objects.requireNonNull(text);
    this.style = Objects.requireNonNull(style);
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.x = x;
    this.width = width;
    this.caretIndices = caretIndices.clone();
    this.caretOffsets = caretOffsets.clone();
  }

  public String text() { return text; }
  public TextStyle2D style() { return style; }
  public int startIndex() { return startIndex; }
  public int endIndex() { return endIndex; }
  public double x() { return x; }
  public double width() { return width; }
  public int[] caretIndices() { return caretIndices.clone(); }
  public double[] caretOffsets() { return caretOffsets.clone(); }

  double caretX(int index) {
    int pos = Arrays.binarySearch(caretIndices, index);
    if (pos >= 0) return x + caretOffsets[pos];
    int insertion = -pos - 1;
    if (insertion <= 0) return x;
    if (insertion >= caretOffsets.length) return x + width;
    return x + caretOffsets[insertion - 1];
  }

  int hitTest(double localX) {
    if (localX <= 0.0) return caretIndices[0];
    for (int i = 1; i < caretOffsets.length; i++) {
      double midpoint = (caretOffsets[i - 1] + caretOffsets[i]) * 0.5;
      if (localX < midpoint) return caretIndices[i - 1];
    }
    return caretIndices[caretIndices.length - 1];
  }
}
