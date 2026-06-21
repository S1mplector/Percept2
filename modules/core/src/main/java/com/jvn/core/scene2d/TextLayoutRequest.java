package com.jvn.core.scene2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable input for portable multiline text layout. */
public final class TextLayoutRequest {
  private final List<TextSpan2D> spans;
  private final double maxWidth;
  private final int maxLines;
  private final double lineHeightMultiplier;
  private final TextWrapMode wrapMode;
  private final TextHorizontalAlign alignment;
  private final TextDirection direction;
  private final Locale locale;
  private final String ellipsis;

  private TextLayoutRequest(Builder builder) {
    if (builder.spans.isEmpty()) throw new IllegalArgumentException("At least one text span is required");
    if ((!Double.isFinite(builder.maxWidth) && builder.maxWidth != Double.POSITIVE_INFINITY)
        || builder.maxWidth <= 0.0) {
      throw new IllegalArgumentException("maxWidth must be positive");
    }
    if (builder.maxLines <= 0) throw new IllegalArgumentException("maxLines must be positive");
    if (!Double.isFinite(builder.lineHeightMultiplier) || builder.lineHeightMultiplier <= 0.0) {
      throw new IllegalArgumentException("lineHeightMultiplier must be positive and finite");
    }
    this.spans = List.copyOf(builder.spans);
    this.maxWidth = builder.maxWidth;
    this.maxLines = builder.maxLines;
    this.lineHeightMultiplier = builder.lineHeightMultiplier;
    this.wrapMode = builder.wrapMode;
    this.alignment = builder.alignment;
    this.direction = builder.direction;
    this.locale = builder.locale;
    this.ellipsis = builder.ellipsis;
  }

  public static Builder builder() { return new Builder(); }

  public List<TextSpan2D> spans() { return spans; }
  public double maxWidth() { return maxWidth; }
  public int maxLines() { return maxLines; }
  public double lineHeightMultiplier() { return lineHeightMultiplier; }
  public TextWrapMode wrapMode() { return wrapMode; }
  public TextHorizontalAlign alignment() { return alignment; }
  public TextDirection direction() { return direction; }
  public Locale locale() { return locale; }
  public String ellipsis() { return ellipsis; }

  public static final class Builder {
    private final List<TextSpan2D> spans = new ArrayList<>();
    private double maxWidth = Double.POSITIVE_INFINITY;
    private int maxLines = Integer.MAX_VALUE;
    private double lineHeightMultiplier = 1.0;
    private TextWrapMode wrapMode = TextWrapMode.WORD;
    private TextHorizontalAlign alignment = TextHorizontalAlign.LEFT;
    private TextDirection direction = TextDirection.AUTO;
    private Locale locale = Locale.ROOT;
    private String ellipsis = "…";

    public Builder text(String text, TextStyle2D style) {
      spans.clear();
      spans.add(new TextSpan2D(text, style));
      return this;
    }

    public Builder addSpan(String text, TextStyle2D style) {
      spans.add(new TextSpan2D(text, style));
      return this;
    }

    public Builder maxWidth(double value) { maxWidth = value; return this; }
    public Builder maxLines(int value) { maxLines = value; return this; }
    public Builder lineHeightMultiplier(double value) { lineHeightMultiplier = value; return this; }
    public Builder wrapMode(TextWrapMode value) { wrapMode = Objects.requireNonNull(value); return this; }
    public Builder alignment(TextHorizontalAlign value) { alignment = Objects.requireNonNull(value); return this; }
    public Builder direction(TextDirection value) { direction = Objects.requireNonNull(value); return this; }
    public Builder locale(Locale value) { locale = Objects.requireNonNull(value); return this; }
    public Builder ellipsis(String value) { ellipsis = Objects.requireNonNull(value); return this; }
    public TextLayoutRequest build() { return new TextLayoutRequest(this); }
  }
}
