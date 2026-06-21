package com.jvn.core.scene2d;

/** Optional normalized RGBA override for a styled text run. */
public record TextColor2D(double red, double green, double blue, double alpha, boolean inherited) {
  private static final TextColor2D INHERITED = new TextColor2D(0, 0, 0, 0, true);

  public TextColor2D {
    if (!inherited && (!channel(red) || !channel(green) || !channel(blue) || !channel(alpha))) {
      throw new IllegalArgumentException("Text color channels must be finite and in [0, 1]");
    }
  }

  public static TextColor2D inherit() { return INHERITED; }

  public static TextColor2D rgba(double red, double green, double blue, double alpha) {
    return new TextColor2D(red, green, blue, alpha, false);
  }

  private static boolean channel(double value) {
    return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
  }
}
