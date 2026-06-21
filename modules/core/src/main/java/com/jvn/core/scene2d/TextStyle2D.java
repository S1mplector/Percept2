package com.jvn.core.scene2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable typography and color for a text span. */
public final class TextStyle2D {
  private final List<String> fontFamilies;
  private final double size;
  private final boolean bold;
  private final TextColor2D color;

  public TextStyle2D(List<String> fontFamilies, double size, boolean bold, TextColor2D color) {
    if (!Double.isFinite(size) || size <= 0.0) {
      throw new IllegalArgumentException("Text size must be positive and finite");
    }
    List<String> families = new ArrayList<>();
    if (fontFamilies != null) {
      for (String family : fontFamilies) {
        if (family != null && !family.isBlank()) families.add(family.trim());
      }
    }
    if (families.isEmpty()) families.add("SansSerif");
    this.fontFamilies = List.copyOf(families);
    this.size = size;
    this.bold = bold;
    this.color = Objects.requireNonNull(color, "color");
  }

  public static TextStyle2D of(String family, double size, boolean bold) {
    return new TextStyle2D(List.of(family), size, bold, TextColor2D.inherit());
  }

  public List<String> fontFamilies() { return fontFamilies; }
  public double size() { return size; }
  public boolean bold() { return bold; }
  public TextColor2D color() { return color; }

  public TextStyle2D withColor(TextColor2D value) {
    return new TextStyle2D(fontFamilies, size, bold, value);
  }

  TextStyle2D resolved(Blitter2D blitter) {
    return new TextStyle2D(
        List.of(blitter.resolveFontFamily(fontFamilies)), size, bold, color);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof TextStyle2D style)) return false;
    return Double.compare(size, style.size) == 0
        && bold == style.bold
        && fontFamilies.equals(style.fontFamilies)
        && color.equals(style.color);
  }

  @Override public int hashCode() { return Objects.hash(fontFamilies, size, bold, color); }
}
