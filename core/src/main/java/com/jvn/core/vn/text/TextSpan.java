package com.jvn.core.vn.text;

/**
 * Represents a span of text with optional effects.
 * Effects are applied via inline markup in dialogue text.
 */
public class TextSpan {
  private final String text;
  private final TextEffect effect;
  private final String colorHex; // e.g., "#FF0000" for red
  private final float speedMultiplier; // 1.0 = normal, 0.5 = half speed, 2.0 = double speed
  private final int delayMs; // pause before this span

  public TextSpan(String text) {
    this(text, TextEffect.NONE, null, 1.0f, 0);
  }

  public TextSpan(String text, TextEffect effect, String colorHex, float speedMultiplier, int delayMs) {
    this.text = text;
    this.effect = effect;
    this.colorHex = colorHex;
    this.speedMultiplier = speedMultiplier;
    this.delayMs = delayMs;
  }

  public String getText() { return text; }
  public TextEffect getEffect() { return effect; }
  public String getColorHex() { return colorHex; }
  public float getSpeedMultiplier() { return speedMultiplier; }
  public int getDelayMs() { return delayMs; }

  public boolean hasEffect() { return effect != TextEffect.NONE; }
  public boolean hasColor() { return colorHex != null && !colorHex.isEmpty(); }
  public boolean hasSpeedChange() { return speedMultiplier != 1.0f; }
  public boolean hasDelay() { return delayMs > 0; }

  public int length() { return text != null ? text.length() : 0; }

  @Override
  public String toString() {
    return "TextSpan{" + text + ", effect=" + effect + ", color=" + colorHex + ", speed=" + speedMultiplier + "}";
  }
}
