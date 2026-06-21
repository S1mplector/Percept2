package com.jvn.core.scene2d;

/** Width and vertical font metrics in logical pixels. */
public record TextFontMetrics2D(double width, double ascent, double descent, double leading) {
  public TextFontMetrics2D {
    if (width < 0 || ascent < 0 || descent < 0 || leading < 0) {
      throw new IllegalArgumentException("Text metrics cannot be negative");
    }
  }

  public double lineHeight() { return ascent + descent + leading; }
}
