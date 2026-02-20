package com.jvn.core.vn.ui;

/**
 * Typed style settings for VN dialogue and choice UI.
 *
 * <p>Geometry remains in {@link VnUiLayoutSpec}; this record handles visual
 * skin properties (assets, colors, and border/text offsets).
 */
public record VnUiStyleSpec(
    String textBoxAssetPath,
    String choiceButtonAssetPath,
    String choiceButtonHoverAssetPath,
    String choiceButtonSelectedAssetPath,
    String choiceButtonDisabledAssetPath,
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
    double choiceCornerRadius,
    double choiceBorderWidth,
    double choiceTextBaselineOffset
) {
  public VnUiStyleSpec {
    textBoxAssetPath = normalize(textBoxAssetPath);
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
  }

  public static VnUiStyleSpec defaults() {
    return new VnUiStyleSpec(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        10.0,
        2.0,
        5.0
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
