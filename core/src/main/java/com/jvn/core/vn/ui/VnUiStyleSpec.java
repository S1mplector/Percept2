package com.jvn.core.vn.ui;

/**
 * Typed style settings for VN dialogue and choice UI.
 *
 * <p>Geometry remains in {@link VnUiLayoutSpec}; this record handles visual
 * skin properties (assets, colors, and border/text offsets).
 */
public record VnUiStyleSpec(
    // --- Textbox ---
    String textBoxAssetPath,
    String textBoxColor,
    Double textBoxOpacity,
    // --- Name box ---
    String nameBoxAssetPath,
    String nameBoxColor,
    String nameTextColor,
    String nameTextFontFamily,
    Integer nameTextFontSize,
    // --- Dialogue text ---
    String dialogueTextColor,
    String dialogueTextFontFamily,
    Integer dialogueTextFontSize,
    // --- Choice button assets ---
    String choiceButtonAssetPath,
    String choiceButtonHoverAssetPath,
    String choiceButtonSelectedAssetPath,
    String choiceButtonDisabledAssetPath,
    // --- Choice colors ---
    String choiceBackgroundColor,
    String choiceHoverColor,
    String choiceSelectedColor,
    String choiceDisabledColor,
    String choiceTextColor,
    String choiceHoverTextColor,
    String choiceSelectedTextColor,
    String choiceDisabledTextColor,
    String choiceBorderColor,
    String choiceHoverBorderColor,
    String choiceSelectedBorderColor,
    String choiceDisabledBorderColor,
    // --- Choice geometry ---
    double choiceCornerRadius,
    double choiceBorderWidth,
    double choiceTextBaselineOffset,
    // --- Choice font ---
    String choiceFontFamily,
    Integer choiceFontSize
) {
  public VnUiStyleSpec {
    textBoxAssetPath = normalize(textBoxAssetPath);
    textBoxColor = normalize(textBoxColor);
    if (textBoxOpacity != null) textBoxOpacity = clamp(textBoxOpacity, 0.0, 1.0);
    nameBoxAssetPath = normalize(nameBoxAssetPath);
    nameBoxColor = normalize(nameBoxColor);
    nameTextColor = normalize(nameTextColor);
    nameTextFontFamily = normalize(nameTextFontFamily);
    if (nameTextFontSize != null && nameTextFontSize <= 0) nameTextFontSize = null;
    dialogueTextColor = normalize(dialogueTextColor);
    dialogueTextFontFamily = normalize(dialogueTextFontFamily);
    if (dialogueTextFontSize != null && dialogueTextFontSize <= 0) dialogueTextFontSize = null;

    choiceButtonAssetPath = normalize(choiceButtonAssetPath);
    choiceButtonHoverAssetPath = normalize(choiceButtonHoverAssetPath);
    choiceButtonSelectedAssetPath = normalize(choiceButtonSelectedAssetPath);
    choiceButtonDisabledAssetPath = normalize(choiceButtonDisabledAssetPath);

    choiceBackgroundColor = normalize(choiceBackgroundColor);
    choiceHoverColor = normalize(choiceHoverColor);
    choiceSelectedColor = normalize(choiceSelectedColor);
    choiceDisabledColor = normalize(choiceDisabledColor);

    choiceTextColor = normalize(choiceTextColor);
    choiceHoverTextColor = normalize(choiceHoverTextColor);
    choiceSelectedTextColor = normalize(choiceSelectedTextColor);
    choiceDisabledTextColor = normalize(choiceDisabledTextColor);

    choiceBorderColor = normalize(choiceBorderColor);
    choiceHoverBorderColor = normalize(choiceHoverBorderColor);
    choiceSelectedBorderColor = normalize(choiceSelectedBorderColor);
    choiceDisabledBorderColor = normalize(choiceDisabledBorderColor);

    choiceCornerRadius = clamp(sane(choiceCornerRadius, 10.0), 0.0, 96.0);
    choiceBorderWidth = clamp(sane(choiceBorderWidth, 2.0), 0.0, 12.0);
    choiceTextBaselineOffset = clamp(sane(choiceTextBaselineOffset, 5.0), -120.0, 120.0);
    choiceFontFamily = normalize(choiceFontFamily);
    if (choiceFontSize != null && choiceFontSize <= 0) choiceFontSize = null;
  }

  public static VnUiStyleSpec defaults() {
    return new VnUiStyleSpec(
        null, null, null,             // textbox asset, color, opacity
        null, null, null, null, null, // name box asset, color, text color, font, size
        null, null, null,             // dialogue text color, font, size
        null, null, null, null,       // choice button assets
        null, null, null, null,       // choice bg colors
        null, null, null, null,       // choice text colors
        null, null, null, null,       // choice border colors
        10.0, 2.0, 5.0,              // corner radius, border width, text baseline
        null, null                    // choice font family, size
    );
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static double sane(double value, double fallback) {
    if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
    return value;
  }

  private static double clamp(double value, double min, double max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }
}
