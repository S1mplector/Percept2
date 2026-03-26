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
    String textBoxNarrationAssetPath,
    String textBoxColor,
    Double textBoxOpacity,
    String textBoxBoundsPoints,
    // --- Name box ---
    String nameBoxAssetPath,
    String nameBoxColor,
    String nameTextColor,
    String nameTextFontFamily,
    Integer nameTextFontSize,
    String nameTextFontWeight,
    Double nameTextXAlign,
    String nameBoxBoundsPoints,
    Double nameBoxOpacity,
    // --- Dialogue text ---
    String dialogueTextColor,
    String dialogueTextFontFamily,
    Integer dialogueTextFontSize,
    String dialogueTextFontWeight,
    Double dialogueTextXAlign,
    String dialogueTextBoundsPoints,
    // --- Choice button assets ---
    String choiceButtonAssetPath,
    String choiceButtonHoverAssetPath,
    String choiceButtonSelectedAssetPath,
    String choiceButtonDisabledAssetPath,
    String choiceButtonBoundsPoints,
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
    Double choiceTextXAlign,
    // --- Choice font ---
    String choiceFontFamily,
    Integer choiceFontSize,
    String choiceFontWeight,
    // --- Character framing ---
    Double characterHeightFactor,
    Double characterBaselineY,
    // --- NVL panel ---
    String nvlPanelAssetPath,
    String nvlPanelColor,
    Double nvlPanelOpacity,
    String nvlSpeakerTextColor,
    String nvlTextColor,
    // --- Bubble dialogue ---
    String bubbleAssetPath,
    String bubbleColor,
    Double bubbleOpacity,
    String bubbleBorderColor,
    String bubbleSpeakerTextColor,
    String bubbleTextColor,
    double bubbleCornerRadius,
    double bubbleBorderWidth
) {
  public VnUiStyleSpec(
      String textBoxAssetPath,
      String textBoxColor,
      Double textBoxOpacity,
      String textBoxBoundsPoints,
      String nameBoxAssetPath,
      String nameBoxColor,
      String nameTextColor,
      String nameTextFontFamily,
      Integer nameTextFontSize,
      String nameTextFontWeight,
      Double nameTextXAlign,
      String nameBoxBoundsPoints,
      Double nameBoxOpacity,
      String dialogueTextColor,
      String dialogueTextFontFamily,
      Integer dialogueTextFontSize,
      String dialogueTextFontWeight,
      Double dialogueTextXAlign,
      String dialogueTextBoundsPoints,
      String choiceButtonAssetPath,
      String choiceButtonHoverAssetPath,
      String choiceButtonSelectedAssetPath,
      String choiceButtonDisabledAssetPath,
      String choiceButtonBoundsPoints,
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
      double choiceTextBaselineOffset,
      Double choiceTextXAlign,
      String choiceFontFamily,
      Integer choiceFontSize,
      String choiceFontWeight,
      Double characterHeightFactor,
      Double characterBaselineY
  ) {
    this(
        textBoxAssetPath, null, textBoxColor, textBoxOpacity, textBoxBoundsPoints,
        nameBoxAssetPath, nameBoxColor, nameTextColor, nameTextFontFamily,
        nameTextFontSize, nameTextFontWeight, nameTextXAlign, nameBoxBoundsPoints,
        nameBoxOpacity, dialogueTextColor, dialogueTextFontFamily, dialogueTextFontSize,
        dialogueTextFontWeight, dialogueTextXAlign, dialogueTextBoundsPoints,
        choiceButtonAssetPath, choiceButtonHoverAssetPath, choiceButtonSelectedAssetPath,
        choiceButtonDisabledAssetPath, choiceButtonBoundsPoints, choiceBackgroundColor,
        choiceHoverColor, choiceSelectedColor, choiceDisabledColor, choiceTextColor,
        choiceHoverTextColor, choiceSelectedTextColor, choiceDisabledTextColor,
        choiceBorderColor, choiceHoverBorderColor, choiceSelectedBorderColor,
        choiceDisabledBorderColor, choiceCornerRadius, choiceBorderWidth,
        choiceTextBaselineOffset, choiceTextXAlign, choiceFontFamily, choiceFontSize,
        choiceFontWeight, characterHeightFactor, characterBaselineY
    );
  }

  public VnUiStyleSpec(
      String textBoxAssetPath,
      String textBoxNarrationAssetPath,
      String textBoxColor,
      Double textBoxOpacity,
      String textBoxBoundsPoints,
      String nameBoxAssetPath,
      String nameBoxColor,
      String nameTextColor,
      String nameTextFontFamily,
      Integer nameTextFontSize,
      String nameTextFontWeight,
      Double nameTextXAlign,
      String nameBoxBoundsPoints,
      Double nameBoxOpacity,
      String dialogueTextColor,
      String dialogueTextFontFamily,
      Integer dialogueTextFontSize,
      String dialogueTextFontWeight,
      Double dialogueTextXAlign,
      String dialogueTextBoundsPoints,
      String choiceButtonAssetPath,
      String choiceButtonHoverAssetPath,
      String choiceButtonSelectedAssetPath,
      String choiceButtonDisabledAssetPath,
      String choiceButtonBoundsPoints,
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
      double choiceTextBaselineOffset,
      Double choiceTextXAlign,
      String choiceFontFamily,
      Integer choiceFontSize,
      String choiceFontWeight,
      Double characterHeightFactor,
      Double characterBaselineY
  ) {
    this(
        textBoxAssetPath, textBoxNarrationAssetPath, textBoxColor, textBoxOpacity, textBoxBoundsPoints,
        nameBoxAssetPath, nameBoxColor, nameTextColor, nameTextFontFamily,
        nameTextFontSize, nameTextFontWeight, nameTextXAlign, nameBoxBoundsPoints,
        nameBoxOpacity, dialogueTextColor, dialogueTextFontFamily, dialogueTextFontSize,
        dialogueTextFontWeight, dialogueTextXAlign, dialogueTextBoundsPoints,
        choiceButtonAssetPath, choiceButtonHoverAssetPath, choiceButtonSelectedAssetPath,
        choiceButtonDisabledAssetPath, choiceButtonBoundsPoints, choiceBackgroundColor,
        choiceHoverColor, choiceSelectedColor, choiceDisabledColor, choiceTextColor,
        choiceHoverTextColor, choiceSelectedTextColor, choiceDisabledTextColor,
        choiceBorderColor, choiceHoverBorderColor, choiceSelectedBorderColor,
        choiceDisabledBorderColor, choiceCornerRadius, choiceBorderWidth,
        choiceTextBaselineOffset, choiceTextXAlign, choiceFontFamily, choiceFontSize,
        choiceFontWeight, characterHeightFactor, characterBaselineY,
        null, "#08111acc", 0.84, "#F7D89A", "#E8EDF6",
        null, "#152238ee", 0.96, "#A9BCD9", "#FFD78A", "#F1F5FF", 20.0, 2.0
    );
  }

  public VnUiStyleSpec(
      String textBoxAssetPath,
      String textBoxColor,
      Double textBoxOpacity,
      String textBoxBoundsPoints,
      String nameBoxAssetPath,
      String nameBoxColor,
      String nameTextColor,
      String nameTextFontFamily,
      Integer nameTextFontSize,
      String nameTextFontWeight,
      Double nameTextXAlign,
      String nameBoxBoundsPoints,
      Double nameBoxOpacity,
      String dialogueTextColor,
      String dialogueTextFontFamily,
      Integer dialogueTextFontSize,
      String dialogueTextFontWeight,
      Double dialogueTextXAlign,
      String dialogueTextBoundsPoints,
      String choiceButtonAssetPath,
      String choiceButtonHoverAssetPath,
      String choiceButtonSelectedAssetPath,
      String choiceButtonDisabledAssetPath,
      String choiceButtonBoundsPoints,
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
      double choiceTextBaselineOffset,
      Double choiceTextXAlign,
      String choiceFontFamily,
      Integer choiceFontSize,
      String choiceFontWeight,
      Double characterHeightFactor,
      Double characterBaselineY,
      String nvlPanelAssetPath,
      String nvlPanelColor,
      Double nvlPanelOpacity,
      String nvlSpeakerTextColor,
      String nvlTextColor,
      String bubbleAssetPath,
      String bubbleColor,
      Double bubbleOpacity,
      String bubbleBorderColor,
      String bubbleSpeakerTextColor,
      String bubbleTextColor,
      double bubbleCornerRadius,
      double bubbleBorderWidth
  ) {
    this(
        textBoxAssetPath, null, textBoxColor, textBoxOpacity, textBoxBoundsPoints,
        nameBoxAssetPath, nameBoxColor, nameTextColor, nameTextFontFamily,
        nameTextFontSize, nameTextFontWeight, nameTextXAlign, nameBoxBoundsPoints,
        nameBoxOpacity, dialogueTextColor, dialogueTextFontFamily, dialogueTextFontSize,
        dialogueTextFontWeight, dialogueTextXAlign, dialogueTextBoundsPoints,
        choiceButtonAssetPath, choiceButtonHoverAssetPath, choiceButtonSelectedAssetPath,
        choiceButtonDisabledAssetPath, choiceButtonBoundsPoints, choiceBackgroundColor,
        choiceHoverColor, choiceSelectedColor, choiceDisabledColor, choiceTextColor,
        choiceHoverTextColor, choiceSelectedTextColor, choiceDisabledTextColor,
        choiceBorderColor, choiceHoverBorderColor, choiceSelectedBorderColor,
        choiceDisabledBorderColor, choiceCornerRadius, choiceBorderWidth,
        choiceTextBaselineOffset, choiceTextXAlign, choiceFontFamily, choiceFontSize,
        choiceFontWeight, characterHeightFactor, characterBaselineY,
        nvlPanelAssetPath, nvlPanelColor, nvlPanelOpacity, nvlSpeakerTextColor, nvlTextColor,
        bubbleAssetPath, bubbleColor, bubbleOpacity, bubbleBorderColor, bubbleSpeakerTextColor,
        bubbleTextColor, bubbleCornerRadius, bubbleBorderWidth
    );
  }

  public VnUiStyleSpec {
    textBoxAssetPath = normalize(textBoxAssetPath);
    textBoxNarrationAssetPath = normalize(textBoxNarrationAssetPath);
    textBoxColor = normalize(textBoxColor);
    if (textBoxOpacity != null) textBoxOpacity = clamp(textBoxOpacity, 0.0, 1.0);
    textBoxBoundsPoints = normalize(textBoxBoundsPoints);
    nameBoxAssetPath = normalize(nameBoxAssetPath);
    nameBoxColor = normalize(nameBoxColor);
    nameTextColor = normalize(nameTextColor);
    nameTextFontFamily = normalize(nameTextFontFamily);
    if (nameTextFontSize != null && nameTextFontSize <= 0) nameTextFontSize = null;
    nameTextFontWeight = normalize(nameTextFontWeight);
    if (nameTextXAlign != null) nameTextXAlign = clamp(nameTextXAlign, 0.0, 1.0);
    nameBoxBoundsPoints = normalize(nameBoxBoundsPoints);
    if (nameBoxOpacity != null) nameBoxOpacity = clamp(nameBoxOpacity, 0.0, 1.0);
    dialogueTextColor = normalize(dialogueTextColor);
    dialogueTextFontFamily = normalize(dialogueTextFontFamily);
    if (dialogueTextFontSize != null && dialogueTextFontSize <= 0) dialogueTextFontSize = null;
    dialogueTextFontWeight = normalize(dialogueTextFontWeight);
    if (dialogueTextXAlign != null) dialogueTextXAlign = clamp(dialogueTextXAlign, 0.0, 1.0);
    dialogueTextBoundsPoints = normalize(dialogueTextBoundsPoints);

    choiceButtonAssetPath = normalize(choiceButtonAssetPath);
    choiceButtonHoverAssetPath = normalize(choiceButtonHoverAssetPath);
    choiceButtonSelectedAssetPath = normalize(choiceButtonSelectedAssetPath);
    choiceButtonDisabledAssetPath = normalize(choiceButtonDisabledAssetPath);
    choiceButtonBoundsPoints = normalize(choiceButtonBoundsPoints);

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
    if (choiceTextXAlign != null) choiceTextXAlign = clamp(choiceTextXAlign, 0.0, 1.0);
    choiceFontFamily = normalize(choiceFontFamily);
    if (choiceFontSize != null && choiceFontSize <= 0) choiceFontSize = null;
    choiceFontWeight = normalize(choiceFontWeight);
    if (characterHeightFactor != null) {
      characterHeightFactor = clamp(characterHeightFactor, 0.1, 3.0);
    }
    if (characterBaselineY != null) {
      characterBaselineY = clamp(characterBaselineY, -0.5, 2.0);
    }
    nvlPanelAssetPath = normalize(nvlPanelAssetPath);
    nvlPanelColor = normalize(nvlPanelColor);
    if (nvlPanelOpacity != null) nvlPanelOpacity = clamp(nvlPanelOpacity, 0.0, 1.0);
    nvlSpeakerTextColor = normalize(nvlSpeakerTextColor);
    nvlTextColor = normalize(nvlTextColor);
    bubbleAssetPath = normalize(bubbleAssetPath);
    bubbleColor = normalize(bubbleColor);
    if (bubbleOpacity != null) bubbleOpacity = clamp(bubbleOpacity, 0.0, 1.0);
    bubbleBorderColor = normalize(bubbleBorderColor);
    bubbleSpeakerTextColor = normalize(bubbleSpeakerTextColor);
    bubbleTextColor = normalize(bubbleTextColor);
    bubbleCornerRadius = clamp(sane(bubbleCornerRadius, 20.0), 0.0, 96.0);
    bubbleBorderWidth = clamp(sane(bubbleBorderWidth, 2.0), 0.0, 12.0);
  }

  public static VnUiStyleSpec defaults() {
    return new VnUiStyleSpec(
        null, null, "#0C1220E0", 0.88, null,                 // textbox: dark navy, high opacity
        null, "#14203890", "#FFD78A", "SansSerif", 18, null, null, // name box: dark tint, warm gold text
        null, null,                                           // name box: no font weight override, no opacity
        "#E8EDF6", "SansSerif", 22, null, null, null,        // dialogue: near-white, clean, no font weight override
        null, null, null, null, null,                         // choice button assets + bounds
        "#1A2640D8", "#243358E8", "#2A3D68E8", "#121826A0", // choice bg: dark blue tones
        "#D4DCF0", "#F0F4FF", "#FFD78A", "#6878A0",         // choice text: light, warm highlight
        "#3A5080A0", "#5888CCA0", "#C8A04880", "#28345060", // choice borders: blue/gold accents
        8.0, 1.5, 4.0, null,                                 // corner radius, border, baseline
        "SansSerif", 20, null,                               // choice font + weight
        0.85, 1.0,                                           // character framing
        null, "#08111acc", 0.84, "#F7D89A", "#E8EDF6",
        null, "#152238ee", 0.96, "#A9BCD9", "#FFD78A", "#F1F5FF", 20.0, 2.0
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
