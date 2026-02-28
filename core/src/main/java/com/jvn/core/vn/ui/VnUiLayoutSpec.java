package com.jvn.core.vn.ui;

/**
 * Screen-space layout spec for VN dialogue and choice UI.
 *
 * <p>Fraction values are normalized to the viewport (0..1). Pixel values are
 * absolute and scale-independent.
 */
public record VnUiLayoutSpec(
    double textBoxX,
    double textBoxY,
    double textBoxWidth,
    double textBoxHeight,
    double textBoxPadding,
    double nameBoxXOffset,
    double nameBoxYOffset,
    double nameBoxWidth,
    double nameBoxHeight,
    double nameTextXOffset,
    double nameTextBaselineOffset,
    double dialogueTextHorizontalPadding,
    double dialogueTextTopPadding,
    double dialogueTextRightPadding,
    double dialogueTextBottomPadding,
    double choiceXCenter,
    double choiceYStart,
    double choiceWidthFactor,
    double choiceHeight,
    double choiceGap,
    double choiceTextXPadding
) {
  public VnUiLayoutSpec {
    textBoxX = clamp(sane(textBoxX, 0.0), 0.0, 1.0);
    textBoxY = clamp(sane(textBoxY, 0.75), 0.0, 1.0);
    textBoxWidth = clamp(sane(textBoxWidth, 1.0), 0.05, 1.0);
    textBoxHeight = clamp(sane(textBoxHeight, 0.25), 0.05, 1.0);
    if (textBoxX + textBoxWidth > 1.0) textBoxWidth = Math.max(0.05, 1.0 - textBoxX);
    if (textBoxY + textBoxHeight > 1.0) textBoxHeight = Math.max(0.05, 1.0 - textBoxY);

    textBoxPadding = max(sane(textBoxPadding, 20.0), 0.0);
    nameBoxXOffset = sane(nameBoxXOffset, 20.0);
    nameBoxYOffset = sane(nameBoxYOffset, -40.0);
    nameBoxWidth = max(sane(nameBoxWidth, 200.0), 20.0);
    nameBoxHeight = max(sane(nameBoxHeight, 40.0), 12.0);
    nameTextXOffset = sane(nameTextXOffset, 10.0);
    nameTextBaselineOffset = sane(nameTextBaselineOffset, 25.0);
    dialogueTextHorizontalPadding = max(sane(dialogueTextHorizontalPadding, 20.0), 0.0);
    dialogueTextTopPadding = sane(dialogueTextTopPadding, 40.0);
    dialogueTextRightPadding = max(sane(dialogueTextRightPadding, dialogueTextHorizontalPadding), 0.0);
    dialogueTextBottomPadding = max(sane(dialogueTextBottomPadding, 10.0), 0.0);

    choiceXCenter = clamp(sane(choiceXCenter, 0.5), 0.0, 1.0);
    choiceYStart = sane(choiceYStart, -1.0);
    if (choiceYStart >= 0.0) choiceYStart = clamp(choiceYStart, 0.0, 1.0);
    else choiceYStart = -1.0; // negative means "auto-center"
    choiceWidthFactor = clamp(sane(choiceWidthFactor, 0.6), 0.1, 1.0);
    choiceHeight = max(sane(choiceHeight, 50.0), 14.0);
    choiceGap = max(sane(choiceGap, 10.0), 0.0);
    choiceTextXPadding = max(sane(choiceTextXPadding, 20.0), 0.0);
  }

  public static VnUiLayoutSpec defaults() {
    return new VnUiLayoutSpec(
        0.0,
        0.75,
        1.0,
        0.25,
        20.0,
        20.0,
        -40.0,
        200.0,
        40.0,
        10.0,
        25.0,
        20.0,
        40.0,
        20.0,
        10.0,
        0.5,
        -1.0,
        0.6,
        50.0,
        10.0,
        20.0
    );
  }

  private static double sane(double v, double def) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return def;
    return v;
  }

  private static double clamp(double v, double min, double max) {
    if (v < min) return min;
    if (v > max) return max;
    return v;
  }

  private static double max(double v, double min) {
    return v < min ? min : v;
  }
}
