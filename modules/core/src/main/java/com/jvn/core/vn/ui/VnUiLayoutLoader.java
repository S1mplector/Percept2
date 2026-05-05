package com.jvn.core.vn.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.ui.BoundsPointCodec;

/**
 * Loader for dialogue layout configuration.
 *
 * <p>Supported locations (first match wins):
 * <ul>
 *   <li>configured by {@code dialogueLayout} in {@code jvn.project}</li>
 *   <li>{@code config/ui/dialogue.layout}</li>
 *   <li>{@code config/vn/dialogue.layout}</li>
 *   <li>{@code dialogue.layout}</li>
 * </ul>
 */
public final class VnUiLayoutLoader {
  private static final String[] DEFAULT_LAYOUT_PATHS = new String[] {
      "config/ui/dialogue.layout",
      "config/vn/dialogue.layout",
      "dialogue.layout"
  };

  private static final Set<String> KNOWN_DIALOGUE_LAYOUT_KEYS = Set.of(
      "textBoxX",
      "textBoxY",
      "textBoxWidth",
      "textBoxHeight",
      "textBoxPadding",
      "nameBoxXOffset",
      "nameBoxYOffset",
      "nameBoxWidth",
      "nameBoxHeight",
      "nameTextXOffset",
      "nameTextBaselineOffset",
      "nameTextTopPadding",
      "nameTextBottomPadding",
      "nameTextYAlign",
      "dialogueTextHorizontalPadding",
      "dialogueTextTopPadding",
      "dialogueTextRightPadding",
      "dialogueTextBottomPadding",
      "choiceXCenter",
      "choiceYStart",
      "choiceWidthFactor",
      "choiceHeight",
      "choiceGap",
      "choiceTextXPadding",
      "choiceTextTopPadding",
      "choiceTextBottomPadding",
      "choiceTextYAlign",
      "nameBoxAutoWidth",
      "nvlX",
      "nvlY",
      "nvlWidth",
      "nvlHeight",
      "nvlPadding",
      "nvlSpeakerWidth",
      "nvlEntryGap",
      "nvlMaxEntries",
      "bubbleWidthFactor",
      "bubbleMinHeight",
      "bubbleTextPadding",
      "bubbleYOffset",
      "bubbleTailSize"
  );

  private static final Set<String> KNOWN_DIALOGUE_STYLE_KEYS = Set.of(
      "textBoxAsset",
      "textBoxNarrationAsset",
      "textBoxColor",
      "textBoxOpacity",
      "textBoxBoundsPoints",
      "nameBoxAsset",
      "nameBoxColor",
      "nameTextColor",
      "nameTextFontFamily",
      "nameTextFontSize",
      "nameTextFontWeight",
      "nameTextXAlign",
      "nameBoxBoundsPoints",
      "nameBoxOpacity",
      "dialogueTextColor",
      "dialogueTextFontFamily",
      "dialogueTextFontSize",
      "dialogueTextFontWeight",
      "dialogueTextXAlign",
      "dialogueTextBoundsPoints",
      "choiceButtonAsset",
      "choiceButtonHoverAsset",
      "choiceButtonSelectedAsset",
      "choiceButtonDisabledAsset",
      "choiceButtonBoundsPoints",
      "choiceBackgroundColor",
      "choiceHoverColor",
      "choiceSelectedColor",
      "choiceDisabledColor",
      "choiceTextColor",
      "choiceHoverTextColor",
      "choiceSelectedTextColor",
      "choiceDisabledTextColor",
      "choiceBorderColor",
      "choiceHoverBorderColor",
      "choiceSelectedBorderColor",
      "choiceDisabledBorderColor",
      "choiceCornerRadius",
      "choiceBorderWidth",
      "choiceTextBaselineOffset",
      "choiceTextXAlign",
      "choiceFontFamily",
      "choiceFontSize",
      "choiceFontWeight",
      "characterHeightFactor",
      "characterBaselineY",
      "nvlPanelAsset",
      "nvlPanelColor",
      "nvlPanelOpacity",
      "nvlSpeakerTextColor",
      "nvlTextColor",
      "bubbleAsset",
      "bubbleColor",
      "bubbleOpacity",
      "bubbleBorderColor",
      "bubbleSpeakerTextColor",
      "bubbleTextColor",
      "bubbleCornerRadius",
      "bubbleBorderWidth"
  );

  private static final Set<String> KNOWN_TEXTBOX_BUTTON_FIELDS = Set.of(
      "label",
      "action",
      "target",
      "enabled",
      "space",
      "asset",
      "hoverAsset",
      "disabledAsset",
      "boundsPoints",
      "x",
      "y",
      "width",
      "height"
  );

  public record LoadResult(
      VnUiLayoutSpec layout,
      VnUiStyleSpec style,
      List<VnUiActionButtonSpec> textBoxButtons,
      List<String> diagnostics
  ) {
    public LoadResult {
      layout = layout == null ? VnUiLayoutSpec.defaults() : layout;
      style = style == null ? VnUiStyleSpec.defaults() : style;
      textBoxButtons = textBoxButtons == null ? List.of() : List.copyOf(textBoxButtons);
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
  }

  private VnUiLayoutLoader() {}

  public static VnUiLayoutSpec loadFromAssets() {
    return loadFromAssetsWithDiagnostics().layout();
  }

  public static VnUiLayoutSpec loadFromAssets(AssetCatalog assets) {
    return loadFromAssetsWithDiagnostics(assets).layout();
  }

  public static VnUiStyleSpec loadStyleFromAssets() {
    return loadFromAssetsWithDiagnostics().style();
  }

  public static VnUiStyleSpec loadStyleFromAssets(AssetCatalog assets) {
    return loadFromAssetsWithDiagnostics(assets).style();
  }

  public static VnUiLayoutSpec loadFromProjectRoot(File projectRoot) {
    return loadFromProjectRootWithDiagnostics(projectRoot).layout();
  }

  public static VnUiStyleSpec loadStyleFromProjectRoot(File projectRoot) {
    return loadFromProjectRootWithDiagnostics(projectRoot).style();
  }

  public static List<VnUiActionButtonSpec> loadTextBoxButtonsFromAssets() {
    return loadFromAssetsWithDiagnostics().textBoxButtons();
  }

  public static List<VnUiActionButtonSpec> loadTextBoxButtonsFromAssets(AssetCatalog assets) {
    return loadFromAssetsWithDiagnostics(assets).textBoxButtons();
  }

  public static List<VnUiActionButtonSpec> loadTextBoxButtonsFromProjectRoot(File projectRoot) {
    return loadFromProjectRootWithDiagnostics(projectRoot).textBoxButtons();
  }

  public static LoadResult loadFromAssetsWithDiagnostics() {
    return loadFromAssetsWithDiagnostics(new AssetCatalog());
  }

  public static LoadResult loadFromAssetsWithDiagnostics(AssetCatalog assets) {
    List<String> diagnostics = new ArrayList<>();
    Properties props = loadPropertiesFromAssetsInternal(assets, diagnostics);
    return parseWithDiagnostics(
        props,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults(),
        List.of(),
        diagnostics
    );
  }

  public static LoadResult loadFromProjectRootWithDiagnostics(File projectRoot) {
    List<String> diagnostics = new ArrayList<>();
    Properties props = loadPropertiesFromProjectRootInternal(projectRoot, diagnostics);
    return parseWithDiagnostics(
        props,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults(),
        List.of(),
        diagnostics
    );
  }

  public static Properties loadPropertiesFromAssets() {
    return loadPropertiesFromAssets(new AssetCatalog());
  }

  public static Properties loadPropertiesFromAssets(AssetCatalog assets) {
    return loadPropertiesFromAssetsInternal(assets, null);
  }

  public static Properties loadPropertiesFromProjectRoot(File projectRoot) {
    return loadPropertiesFromProjectRootInternal(projectRoot, null);
  }

  public static VnUiLayoutSpec parse(Properties props, VnUiLayoutSpec base) {
    return parseWithDiagnostics(props, base, VnUiStyleSpec.defaults()).layout();
  }

  public static VnUiStyleSpec parseStyle(Properties props, VnUiStyleSpec base) {
    return parseWithDiagnostics(props, VnUiLayoutSpec.defaults(), base).style();
  }

  public static LoadResult parseWithDiagnostics(
      Properties props,
      VnUiLayoutSpec baseLayout,
      VnUiStyleSpec baseStyle
  ) {
    return parseWithDiagnostics(props, baseLayout, baseStyle, List.of(), new ArrayList<>());
  }

  private static LoadResult parseWithDiagnostics(
      Properties props,
      VnUiLayoutSpec baseLayout,
      VnUiStyleSpec baseStyle,
      List<VnUiActionButtonSpec> baseButtons,
      List<String> diagnostics
  ) {
    VnUiLayoutSpec bLayout = baseLayout == null ? VnUiLayoutSpec.defaults() : baseLayout;
    VnUiStyleSpec bStyle = baseStyle == null ? VnUiStyleSpec.defaults() : baseStyle;
    List<VnUiActionButtonSpec> bButtons = baseButtons == null ? List.of() : baseButtons;
    if (props == null) return new LoadResult(bLayout, bStyle, bButtons, diagnostics);
    warnUnknownKeys(props, diagnostics);

    double textBoxX = parseDouble(props.getProperty("textBoxX"), bLayout.textBoxX(), diagnostics, "textBoxX");
    double textBoxY = parseDouble(props.getProperty("textBoxY"), bLayout.textBoxY(), diagnostics, "textBoxY");
    double textBoxWidth = parseDouble(props.getProperty("textBoxWidth"), bLayout.textBoxWidth(), diagnostics, "textBoxWidth");
    double textBoxHeight = parseDouble(props.getProperty("textBoxHeight"), bLayout.textBoxHeight(), diagnostics, "textBoxHeight");
    double textBoxPadding = parseDouble(props.getProperty("textBoxPadding"), bLayout.textBoxPadding(), diagnostics, "textBoxPadding");
    double nameBoxXOffset = parseDouble(props.getProperty("nameBoxXOffset"), bLayout.nameBoxXOffset(), diagnostics, "nameBoxXOffset");
    double nameBoxYOffset = parseDouble(props.getProperty("nameBoxYOffset"), bLayout.nameBoxYOffset(), diagnostics, "nameBoxYOffset");
    double nameBoxWidth = parseDouble(props.getProperty("nameBoxWidth"), bLayout.nameBoxWidth(), diagnostics, "nameBoxWidth");
    double nameBoxHeight = parseDouble(props.getProperty("nameBoxHeight"), bLayout.nameBoxHeight(), diagnostics, "nameBoxHeight");
    double nameTextXOffset = parseDouble(props.getProperty("nameTextXOffset"), bLayout.nameTextXOffset(), diagnostics, "nameTextXOffset");
    double nameTextBaselineOffset = parseDouble(props.getProperty("nameTextBaselineOffset"), bLayout.nameTextBaselineOffset(), diagnostics, "nameTextBaselineOffset");
    double nameTextTopPadding = parseDouble(props.getProperty("nameTextTopPadding"), bLayout.nameTextTopPadding(), diagnostics, "nameTextTopPadding");
    double nameTextBottomPadding = parseDouble(props.getProperty("nameTextBottomPadding"), bLayout.nameTextBottomPadding(), diagnostics, "nameTextBottomPadding");
    double nameTextYAlign = parseDouble(props.getProperty("nameTextYAlign"), bLayout.nameTextYAlign(), diagnostics, "nameTextYAlign");
    double dialogueTextHorizontalPadding = parseDouble(
        props.getProperty("dialogueTextHorizontalPadding"),
        bLayout.dialogueTextHorizontalPadding(),
        diagnostics,
        "dialogueTextHorizontalPadding");
    double dialogueTextTopPadding = parseDouble(
        props.getProperty("dialogueTextTopPadding"),
        bLayout.dialogueTextTopPadding(),
        diagnostics,
        "dialogueTextTopPadding");
    double dialogueTextRightPadding = parseDouble(
        props.getProperty("dialogueTextRightPadding"),
        dialogueTextHorizontalPadding,
        diagnostics,
        "dialogueTextRightPadding");
    double dialogueTextBottomPadding = parseDouble(
        props.getProperty("dialogueTextBottomPadding"),
        bLayout.dialogueTextBottomPadding(),
        diagnostics,
        "dialogueTextBottomPadding");
    double choiceXCenter = parseDouble(props.getProperty("choiceXCenter"), bLayout.choiceXCenter(), diagnostics, "choiceXCenter");
    double choiceYStart = parseDouble(props.getProperty("choiceYStart"), bLayout.choiceYStart(), diagnostics, "choiceYStart");
    double choiceWidthFactor = parseDouble(props.getProperty("choiceWidthFactor"), bLayout.choiceWidthFactor(), diagnostics, "choiceWidthFactor");
    double choiceHeight = parseDouble(props.getProperty("choiceHeight"), bLayout.choiceHeight(), diagnostics, "choiceHeight");
    double choiceGap = parseDouble(props.getProperty("choiceGap"), bLayout.choiceGap(), diagnostics, "choiceGap");
    double choiceTextXPadding = parseDouble(props.getProperty("choiceTextXPadding"), bLayout.choiceTextXPadding(), diagnostics, "choiceTextXPadding");
    double choiceTextTopPadding = parseDouble(props.getProperty("choiceTextTopPadding"), bLayout.choiceTextTopPadding(), diagnostics, "choiceTextTopPadding");
    double choiceTextBottomPadding = parseDouble(props.getProperty("choiceTextBottomPadding"), bLayout.choiceTextBottomPadding(), diagnostics, "choiceTextBottomPadding");
    double choiceTextYAlign = parseDouble(props.getProperty("choiceTextYAlign"), bLayout.choiceTextYAlign(), diagnostics, "choiceTextYAlign");
    boolean nameBoxAutoWidth = parseBoolean(props.getProperty("nameBoxAutoWidth"), bLayout.nameBoxAutoWidth(), diagnostics, "nameBoxAutoWidth");
    double nvlX = parseDouble(props.getProperty("nvlX"), bLayout.nvlX(), diagnostics, "nvlX");
    double nvlY = parseDouble(props.getProperty("nvlY"), bLayout.nvlY(), diagnostics, "nvlY");
    double nvlWidth = parseDouble(props.getProperty("nvlWidth"), bLayout.nvlWidth(), diagnostics, "nvlWidth");
    double nvlHeight = parseDouble(props.getProperty("nvlHeight"), bLayout.nvlHeight(), diagnostics, "nvlHeight");
    double nvlPadding = parseDouble(props.getProperty("nvlPadding"), bLayout.nvlPadding(), diagnostics, "nvlPadding");
    double nvlSpeakerWidth = parseDouble(props.getProperty("nvlSpeakerWidth"), bLayout.nvlSpeakerWidth(), diagnostics, "nvlSpeakerWidth");
    double nvlEntryGap = parseDouble(props.getProperty("nvlEntryGap"), bLayout.nvlEntryGap(), diagnostics, "nvlEntryGap");
    int nvlMaxEntries = parseInt(props.getProperty("nvlMaxEntries"), bLayout.nvlMaxEntries(), diagnostics, "nvlMaxEntries");
    double bubbleWidthFactor = parseDouble(props.getProperty("bubbleWidthFactor"), bLayout.bubbleWidthFactor(), diagnostics, "bubbleWidthFactor");
    double bubbleMinHeight = parseDouble(props.getProperty("bubbleMinHeight"), bLayout.bubbleMinHeight(), diagnostics, "bubbleMinHeight");
    double bubbleTextPadding = parseDouble(props.getProperty("bubbleTextPadding"), bLayout.bubbleTextPadding(), diagnostics, "bubbleTextPadding");
    double bubbleYOffset = parseDouble(props.getProperty("bubbleYOffset"), bLayout.bubbleYOffset(), diagnostics, "bubbleYOffset");
    double bubbleTailSize = parseDouble(props.getProperty("bubbleTailSize"), bLayout.bubbleTailSize(), diagnostics, "bubbleTailSize");

    VnUiLayoutSpec layout = new VnUiLayoutSpec(
        textBoxX,
        textBoxY,
        textBoxWidth,
        textBoxHeight,
        textBoxPadding,
        nameBoxXOffset,
        nameBoxYOffset,
        nameBoxWidth,
        nameBoxHeight,
        nameTextXOffset,
        nameTextBaselineOffset,
        nameTextTopPadding,
        nameTextBottomPadding,
        nameTextYAlign,
        dialogueTextHorizontalPadding,
        dialogueTextTopPadding,
        dialogueTextRightPadding,
        dialogueTextBottomPadding,
        choiceXCenter,
        choiceYStart,
        choiceWidthFactor,
        choiceHeight,
        choiceGap,
        choiceTextXPadding,
        choiceTextTopPadding,
        choiceTextBottomPadding,
        choiceTextYAlign,
        nameBoxAutoWidth,
        nvlX,
        nvlY,
        nvlWidth,
        nvlHeight,
        nvlPadding,
        nvlSpeakerWidth,
        nvlEntryGap,
        nvlMaxEntries,
        bubbleWidthFactor,
        bubbleMinHeight,
        bubbleTextPadding,
        bubbleYOffset,
        bubbleTailSize
    );

    warnAdjustedDouble("textBoxX", textBoxX, layout.textBoxX(), diagnostics);
    warnAdjustedDouble("textBoxY", textBoxY, layout.textBoxY(), diagnostics);
    warnAdjustedDouble("textBoxWidth", textBoxWidth, layout.textBoxWidth(), diagnostics);
    warnAdjustedDouble("textBoxHeight", textBoxHeight, layout.textBoxHeight(), diagnostics);
    warnAdjustedDouble("textBoxPadding", textBoxPadding, layout.textBoxPadding(), diagnostics);
    warnAdjustedDouble("nameBoxWidth", nameBoxWidth, layout.nameBoxWidth(), diagnostics);
    warnAdjustedDouble("nameBoxHeight", nameBoxHeight, layout.nameBoxHeight(), diagnostics);
    warnAdjustedDouble("nameTextTopPadding", nameTextTopPadding, layout.nameTextTopPadding(), diagnostics);
    warnAdjustedDouble("nameTextBottomPadding", nameTextBottomPadding, layout.nameTextBottomPadding(), diagnostics);
    warnAdjustedDouble("nameTextYAlign", nameTextYAlign, layout.nameTextYAlign(), diagnostics);
    warnAdjustedDouble("dialogueTextHorizontalPadding", dialogueTextHorizontalPadding, layout.dialogueTextHorizontalPadding(), diagnostics);
    warnAdjustedDouble("dialogueTextRightPadding", dialogueTextRightPadding, layout.dialogueTextRightPadding(), diagnostics);
    warnAdjustedDouble("dialogueTextBottomPadding", dialogueTextBottomPadding, layout.dialogueTextBottomPadding(), diagnostics);
    warnAdjustedDouble("choiceXCenter", choiceXCenter, layout.choiceXCenter(), diagnostics);
    warnAdjustedDouble("choiceYStart", choiceYStart, layout.choiceYStart(), diagnostics);
    warnAdjustedDouble("choiceWidthFactor", choiceWidthFactor, layout.choiceWidthFactor(), diagnostics);
    warnAdjustedDouble("choiceHeight", choiceHeight, layout.choiceHeight(), diagnostics);
    warnAdjustedDouble("choiceGap", choiceGap, layout.choiceGap(), diagnostics);
    warnAdjustedDouble("choiceTextXPadding", choiceTextXPadding, layout.choiceTextXPadding(), diagnostics);
    warnAdjustedDouble("choiceTextTopPadding", choiceTextTopPadding, layout.choiceTextTopPadding(), diagnostics);
    warnAdjustedDouble("choiceTextBottomPadding", choiceTextBottomPadding, layout.choiceTextBottomPadding(), diagnostics);
    warnAdjustedDouble("choiceTextYAlign", choiceTextYAlign, layout.choiceTextYAlign(), diagnostics);
    warnAdjustedDouble("nvlX", nvlX, layout.nvlX(), diagnostics);
    warnAdjustedDouble("nvlY", nvlY, layout.nvlY(), diagnostics);
    warnAdjustedDouble("nvlWidth", nvlWidth, layout.nvlWidth(), diagnostics);
    warnAdjustedDouble("nvlHeight", nvlHeight, layout.nvlHeight(), diagnostics);
    warnAdjustedDouble("nvlPadding", nvlPadding, layout.nvlPadding(), diagnostics);
    warnAdjustedDouble("nvlSpeakerWidth", nvlSpeakerWidth, layout.nvlSpeakerWidth(), diagnostics);
    warnAdjustedDouble("nvlEntryGap", nvlEntryGap, layout.nvlEntryGap(), diagnostics);
    warnAdjustedInt("nvlMaxEntries", nvlMaxEntries, layout.nvlMaxEntries(), diagnostics);
    warnAdjustedDouble("bubbleWidthFactor", bubbleWidthFactor, layout.bubbleWidthFactor(), diagnostics);
    warnAdjustedDouble("bubbleMinHeight", bubbleMinHeight, layout.bubbleMinHeight(), diagnostics);
    warnAdjustedDouble("bubbleTextPadding", bubbleTextPadding, layout.bubbleTextPadding(), diagnostics);
    warnAdjustedDouble("bubbleYOffset", bubbleYOffset, layout.bubbleYOffset(), diagnostics);
    warnAdjustedDouble("bubbleTailSize", bubbleTailSize, layout.bubbleTailSize(), diagnostics);

    Double textBoxOpacity = parseOptionalDouble(props.getProperty("textBoxOpacity"), bStyle.textBoxOpacity(), diagnostics, "textBoxOpacity");
    Integer nameTextFontSize = parseOptionalInt(props.getProperty("nameTextFontSize"), bStyle.nameTextFontSize(), diagnostics, "nameTextFontSize");
    Double nameTextXAlign = parseOptionalDouble(props.getProperty("nameTextXAlign"), bStyle.nameTextXAlign(), diagnostics, "nameTextXAlign");
    Double nameBoxOpacity = parseOptionalDouble(props.getProperty("nameBoxOpacity"), bStyle.nameBoxOpacity(), diagnostics, "nameBoxOpacity");
    Integer dialogueTextFontSize = parseOptionalInt(props.getProperty("dialogueTextFontSize"), bStyle.dialogueTextFontSize(), diagnostics, "dialogueTextFontSize");
    Double dialogueTextXAlign = parseOptionalDouble(props.getProperty("dialogueTextXAlign"), bStyle.dialogueTextXAlign(), diagnostics, "dialogueTextXAlign");
    double choiceCornerRadius = parseDouble(props.getProperty("choiceCornerRadius"), bStyle.choiceCornerRadius(), diagnostics, "choiceCornerRadius");
    double choiceBorderWidth = parseDouble(props.getProperty("choiceBorderWidth"), bStyle.choiceBorderWidth(), diagnostics, "choiceBorderWidth");
    double choiceTextBaselineOffset = parseDouble(
        props.getProperty("choiceTextBaselineOffset"),
        bStyle.choiceTextBaselineOffset(),
        diagnostics,
        "choiceTextBaselineOffset");
    Double choiceTextXAlign = parseOptionalDouble(props.getProperty("choiceTextXAlign"), bStyle.choiceTextXAlign(), diagnostics, "choiceTextXAlign");
    Integer choiceFontSize = parseOptionalInt(props.getProperty("choiceFontSize"), bStyle.choiceFontSize(), diagnostics, "choiceFontSize");
    Double characterHeightFactor = parseOptionalDouble(
        props.getProperty("characterHeightFactor"),
        bStyle.characterHeightFactor(),
        diagnostics,
        "characterHeightFactor");
    Double characterBaselineY = parseOptionalDouble(
        props.getProperty("characterBaselineY"),
        bStyle.characterBaselineY(),
        diagnostics,
        "characterBaselineY");
    Double nvlPanelOpacity = parseOptionalDouble(props.getProperty("nvlPanelOpacity"), bStyle.nvlPanelOpacity(), diagnostics, "nvlPanelOpacity");
    Double bubbleOpacity = parseOptionalDouble(props.getProperty("bubbleOpacity"), bStyle.bubbleOpacity(), diagnostics, "bubbleOpacity");
    double bubbleCornerRadius = parseDouble(props.getProperty("bubbleCornerRadius"), bStyle.bubbleCornerRadius(), diagnostics, "bubbleCornerRadius");
    double bubbleBorderWidth = parseDouble(props.getProperty("bubbleBorderWidth"), bStyle.bubbleBorderWidth(), diagnostics, "bubbleBorderWidth");

    VnUiStyleSpec style = new VnUiStyleSpec(
        // Textbox
        normalize(props.getProperty("textBoxAsset"), bStyle.textBoxAssetPath()),
        normalize(props.getProperty("textBoxNarrationAsset"), bStyle.textBoxNarrationAssetPath()),
        normalize(props.getProperty("textBoxColor"), bStyle.textBoxColor()),
        textBoxOpacity,
        normalize(props.getProperty("textBoxBoundsPoints"), bStyle.textBoxBoundsPoints()),
        // Name box
        normalize(props.getProperty("nameBoxAsset"), bStyle.nameBoxAssetPath()),
        normalize(props.getProperty("nameBoxColor"), bStyle.nameBoxColor()),
        normalize(props.getProperty("nameTextColor"), bStyle.nameTextColor()),
        normalize(props.getProperty("nameTextFontFamily"), bStyle.nameTextFontFamily()),
        nameTextFontSize,
        normalize(props.getProperty("nameTextFontWeight"), bStyle.nameTextFontWeight()),
        nameTextXAlign,
        normalize(props.getProperty("nameBoxBoundsPoints"), bStyle.nameBoxBoundsPoints()),
        nameBoxOpacity,
        // Dialogue text
        normalize(props.getProperty("dialogueTextColor"), bStyle.dialogueTextColor()),
        normalize(props.getProperty("dialogueTextFontFamily"), bStyle.dialogueTextFontFamily()),
        dialogueTextFontSize,
        normalize(props.getProperty("dialogueTextFontWeight"), bStyle.dialogueTextFontWeight()),
        dialogueTextXAlign,
        normalize(props.getProperty("dialogueTextBoundsPoints"), bStyle.dialogueTextBoundsPoints()),
        // Choice button assets
        normalize(props.getProperty("choiceButtonAsset"), bStyle.choiceButtonAssetPath()),
        normalize(props.getProperty("choiceButtonHoverAsset"), bStyle.choiceButtonHoverAssetPath()),
        normalize(props.getProperty("choiceButtonSelectedAsset"), bStyle.choiceButtonSelectedAssetPath()),
        normalize(props.getProperty("choiceButtonDisabledAsset"), bStyle.choiceButtonDisabledAssetPath()),
        normalize(props.getProperty("choiceButtonBoundsPoints"), bStyle.choiceButtonBoundsPoints()),
        // Choice colors
        normalize(props.getProperty("choiceBackgroundColor"), bStyle.choiceBackgroundColor()),
        normalize(props.getProperty("choiceHoverColor"), bStyle.choiceHoverColor()),
        normalize(props.getProperty("choiceSelectedColor"), bStyle.choiceSelectedColor()),
        normalize(props.getProperty("choiceDisabledColor"), bStyle.choiceDisabledColor()),
        normalize(props.getProperty("choiceTextColor"), bStyle.choiceTextColor()),
        normalize(props.getProperty("choiceHoverTextColor"), bStyle.choiceHoverTextColor()),
        normalize(props.getProperty("choiceSelectedTextColor"), bStyle.choiceSelectedTextColor()),
        normalize(props.getProperty("choiceDisabledTextColor"), bStyle.choiceDisabledTextColor()),
        normalize(props.getProperty("choiceBorderColor"), bStyle.choiceBorderColor()),
        normalize(props.getProperty("choiceHoverBorderColor"), bStyle.choiceHoverBorderColor()),
        normalize(props.getProperty("choiceSelectedBorderColor"), bStyle.choiceSelectedBorderColor()),
        normalize(props.getProperty("choiceDisabledBorderColor"), bStyle.choiceDisabledBorderColor()),
        // Choice geometry
        choiceCornerRadius,
        choiceBorderWidth,
        choiceTextBaselineOffset,
        choiceTextXAlign,
        // Choice font
        normalize(props.getProperty("choiceFontFamily"), bStyle.choiceFontFamily()),
        choiceFontSize,
        normalize(props.getProperty("choiceFontWeight"), bStyle.choiceFontWeight()),
        // Character framing
        characterHeightFactor,
        characterBaselineY,
        // NVL panel
        normalize(props.getProperty("nvlPanelAsset"), bStyle.nvlPanelAssetPath()),
        normalize(props.getProperty("nvlPanelColor"), bStyle.nvlPanelColor()),
        nvlPanelOpacity,
        normalize(props.getProperty("nvlSpeakerTextColor"), bStyle.nvlSpeakerTextColor()),
        normalize(props.getProperty("nvlTextColor"), bStyle.nvlTextColor()),
        // Bubble dialogue
        normalize(props.getProperty("bubbleAsset"), bStyle.bubbleAssetPath()),
        normalize(props.getProperty("bubbleColor"), bStyle.bubbleColor()),
        bubbleOpacity,
        normalize(props.getProperty("bubbleBorderColor"), bStyle.bubbleBorderColor()),
        normalize(props.getProperty("bubbleSpeakerTextColor"), bStyle.bubbleSpeakerTextColor()),
        normalize(props.getProperty("bubbleTextColor"), bStyle.bubbleTextColor()),
        bubbleCornerRadius,
        bubbleBorderWidth
    );

    warnAdjustedOptionalDouble("textBoxOpacity", textBoxOpacity, style.textBoxOpacity(), diagnostics);
    warnAdjustedOptionalDouble("nameBoxOpacity", nameBoxOpacity, style.nameBoxOpacity(), diagnostics);
    warnAdjustedOptionalInt("nameTextFontSize", nameTextFontSize, style.nameTextFontSize(), diagnostics);
    warnAdjustedOptionalDouble("nameTextXAlign", nameTextXAlign, style.nameTextXAlign(), diagnostics);
    warnAdjustedOptionalInt("dialogueTextFontSize", dialogueTextFontSize, style.dialogueTextFontSize(), diagnostics);
    warnAdjustedOptionalDouble("dialogueTextXAlign", dialogueTextXAlign, style.dialogueTextXAlign(), diagnostics);
    warnAdjustedDouble("choiceCornerRadius", choiceCornerRadius, style.choiceCornerRadius(), diagnostics);
    warnAdjustedDouble("choiceBorderWidth", choiceBorderWidth, style.choiceBorderWidth(), diagnostics);
    warnAdjustedDouble("choiceTextBaselineOffset", choiceTextBaselineOffset, style.choiceTextBaselineOffset(), diagnostics);
    warnAdjustedOptionalDouble("choiceTextXAlign", choiceTextXAlign, style.choiceTextXAlign(), diagnostics);
    warnAdjustedOptionalInt("choiceFontSize", choiceFontSize, style.choiceFontSize(), diagnostics);
    warnAdjustedOptionalDouble("characterHeightFactor", characterHeightFactor, style.characterHeightFactor(), diagnostics);
    warnAdjustedOptionalDouble("characterBaselineY", characterBaselineY, style.characterBaselineY(), diagnostics);
    warnAdjustedOptionalDouble("nvlPanelOpacity", nvlPanelOpacity, style.nvlPanelOpacity(), diagnostics);
    warnAdjustedOptionalDouble("bubbleOpacity", bubbleOpacity, style.bubbleOpacity(), diagnostics);
    warnAdjustedDouble("bubbleCornerRadius", bubbleCornerRadius, style.bubbleCornerRadius(), diagnostics);
    warnAdjustedDouble("bubbleBorderWidth", bubbleBorderWidth, style.bubbleBorderWidth(), diagnostics);

    validateBoundsPoints("textBoxBoundsPoints", style.textBoxBoundsPoints(), diagnostics);
    validateBoundsPoints("nameBoxBoundsPoints", style.nameBoxBoundsPoints(), diagnostics);
    validateBoundsPoints("dialogueTextBoundsPoints", style.dialogueTextBoundsPoints(), diagnostics);
    validateBoundsPoints("choiceButtonBoundsPoints", style.choiceButtonBoundsPoints(), diagnostics);

    List<VnUiActionButtonSpec> buttons = parseTextBoxButtons(props, bButtons, diagnostics);
    return new LoadResult(layout, style, buttons, diagnostics);
  }

  public static Properties toProperties(VnUiLayoutSpec spec) {
    VnUiLayoutSpec s = spec == null ? VnUiLayoutSpec.defaults() : spec;
    Properties p = new Properties();
    p.setProperty("textBoxX", format(s.textBoxX()));
    p.setProperty("textBoxY", format(s.textBoxY()));
    p.setProperty("textBoxWidth", format(s.textBoxWidth()));
    p.setProperty("textBoxHeight", format(s.textBoxHeight()));
    p.setProperty("textBoxPadding", format(s.textBoxPadding()));
    p.setProperty("nameBoxXOffset", format(s.nameBoxXOffset()));
    p.setProperty("nameBoxYOffset", format(s.nameBoxYOffset()));
    p.setProperty("nameBoxWidth", format(s.nameBoxWidth()));
    p.setProperty("nameBoxHeight", format(s.nameBoxHeight()));
    p.setProperty("nameTextXOffset", format(s.nameTextXOffset()));
    p.setProperty("nameTextBaselineOffset", format(s.nameTextBaselineOffset()));
    p.setProperty("nameTextTopPadding", format(s.nameTextTopPadding()));
    p.setProperty("nameTextBottomPadding", format(s.nameTextBottomPadding()));
    p.setProperty("nameTextYAlign", format(s.nameTextYAlign()));
    p.setProperty("dialogueTextHorizontalPadding", format(s.dialogueTextHorizontalPadding()));
    p.setProperty("dialogueTextTopPadding", format(s.dialogueTextTopPadding()));
    p.setProperty("dialogueTextRightPadding", format(s.dialogueTextRightPadding()));
    p.setProperty("dialogueTextBottomPadding", format(s.dialogueTextBottomPadding()));
    p.setProperty("choiceXCenter", format(s.choiceXCenter()));
    p.setProperty("choiceYStart", format(s.choiceYStart()));
    p.setProperty("choiceWidthFactor", format(s.choiceWidthFactor()));
    p.setProperty("choiceHeight", format(s.choiceHeight()));
    p.setProperty("choiceGap", format(s.choiceGap()));
    p.setProperty("choiceTextXPadding", format(s.choiceTextXPadding()));
    p.setProperty("choiceTextTopPadding", format(s.choiceTextTopPadding()));
    p.setProperty("choiceTextBottomPadding", format(s.choiceTextBottomPadding()));
    p.setProperty("choiceTextYAlign", format(s.choiceTextYAlign()));
    if (s.nameBoxAutoWidth()) p.setProperty("nameBoxAutoWidth", "true");
    p.setProperty("nvlX", format(s.nvlX()));
    p.setProperty("nvlY", format(s.nvlY()));
    p.setProperty("nvlWidth", format(s.nvlWidth()));
    p.setProperty("nvlHeight", format(s.nvlHeight()));
    p.setProperty("nvlPadding", format(s.nvlPadding()));
    p.setProperty("nvlSpeakerWidth", format(s.nvlSpeakerWidth()));
    p.setProperty("nvlEntryGap", format(s.nvlEntryGap()));
    p.setProperty("nvlMaxEntries", Integer.toString(s.nvlMaxEntries()));
    p.setProperty("bubbleWidthFactor", format(s.bubbleWidthFactor()));
    p.setProperty("bubbleMinHeight", format(s.bubbleMinHeight()));
    p.setProperty("bubbleTextPadding", format(s.bubbleTextPadding()));
    p.setProperty("bubbleYOffset", format(s.bubbleYOffset()));
    p.setProperty("bubbleTailSize", format(s.bubbleTailSize()));
    return p;
  }

  public static Properties toStyleProperties(VnUiStyleSpec style) {
    VnUiStyleSpec s = style == null ? VnUiStyleSpec.defaults() : style;
    Properties p = new Properties();
    setOptional(p, "textBoxAsset", s.textBoxAssetPath());
    setOptional(p, "textBoxNarrationAsset", s.textBoxNarrationAssetPath());
    setOptional(p, "textBoxColor", s.textBoxColor());
    setOptional(p, "textBoxOpacity", s.textBoxOpacity() == null ? null : format(s.textBoxOpacity()));
    setOptional(p, "textBoxBoundsPoints", s.textBoxBoundsPoints());

    setOptional(p, "nameBoxAsset", s.nameBoxAssetPath());
    setOptional(p, "nameBoxColor", s.nameBoxColor());
    setOptional(p, "nameTextColor", s.nameTextColor());
    setOptional(p, "nameTextFontFamily", s.nameTextFontFamily());
    setOptional(p, "nameTextFontSize", s.nameTextFontSize() == null ? null : Integer.toString(s.nameTextFontSize()));
    setOptional(p, "nameTextFontWeight", s.nameTextFontWeight());
    setOptional(p, "nameTextXAlign", s.nameTextXAlign() == null ? null : format(s.nameTextXAlign()));
    setOptional(p, "nameBoxBoundsPoints", s.nameBoxBoundsPoints());
    setOptional(p, "nameBoxOpacity", s.nameBoxOpacity() == null ? null : format(s.nameBoxOpacity()));

    setOptional(p, "dialogueTextColor", s.dialogueTextColor());
    setOptional(p, "dialogueTextFontFamily", s.dialogueTextFontFamily());
    setOptional(p, "dialogueTextFontSize", s.dialogueTextFontSize() == null ? null : Integer.toString(s.dialogueTextFontSize()));
    setOptional(p, "dialogueTextFontWeight", s.dialogueTextFontWeight());
    setOptional(p, "dialogueTextXAlign", s.dialogueTextXAlign() == null ? null : format(s.dialogueTextXAlign()));
    setOptional(p, "dialogueTextBoundsPoints", s.dialogueTextBoundsPoints());

    setOptional(p, "choiceButtonAsset", s.choiceButtonAssetPath());
    setOptional(p, "choiceButtonHoverAsset", s.choiceButtonHoverAssetPath());
    setOptional(p, "choiceButtonSelectedAsset", s.choiceButtonSelectedAssetPath());
    setOptional(p, "choiceButtonDisabledAsset", s.choiceButtonDisabledAssetPath());
    setOptional(p, "choiceButtonBoundsPoints", s.choiceButtonBoundsPoints());

    setOptional(p, "choiceBackgroundColor", s.choiceBackgroundColor());
    setOptional(p, "choiceHoverColor", s.choiceHoverColor());
    setOptional(p, "choiceSelectedColor", s.choiceSelectedColor());
    setOptional(p, "choiceDisabledColor", s.choiceDisabledColor());

    setOptional(p, "choiceTextColor", s.choiceTextColor());
    setOptional(p, "choiceHoverTextColor", s.choiceHoverTextColor());
    setOptional(p, "choiceSelectedTextColor", s.choiceSelectedTextColor());
    setOptional(p, "choiceDisabledTextColor", s.choiceDisabledTextColor());

    setOptional(p, "choiceBorderColor", s.choiceBorderColor());
    setOptional(p, "choiceHoverBorderColor", s.choiceHoverBorderColor());
    setOptional(p, "choiceSelectedBorderColor", s.choiceSelectedBorderColor());
    setOptional(p, "choiceDisabledBorderColor", s.choiceDisabledBorderColor());

    p.setProperty("choiceCornerRadius", format(s.choiceCornerRadius()));
    p.setProperty("choiceBorderWidth", format(s.choiceBorderWidth()));
    p.setProperty("choiceTextBaselineOffset", format(s.choiceTextBaselineOffset()));
    setOptional(p, "choiceTextXAlign", s.choiceTextXAlign() == null ? null : format(s.choiceTextXAlign()));
    setOptional(p, "choiceFontFamily", s.choiceFontFamily());
    setOptional(p, "choiceFontSize", s.choiceFontSize() == null ? null : Integer.toString(s.choiceFontSize()));
    setOptional(p, "choiceFontWeight", s.choiceFontWeight());
    setOptional(p, "characterHeightFactor", s.characterHeightFactor() == null ? null : format(s.characterHeightFactor()));
    setOptional(p, "characterBaselineY", s.characterBaselineY() == null ? null : format(s.characterBaselineY()));
    setOptional(p, "nvlPanelAsset", s.nvlPanelAssetPath());
    setOptional(p, "nvlPanelColor", s.nvlPanelColor());
    setOptional(p, "nvlPanelOpacity", s.nvlPanelOpacity() == null ? null : format(s.nvlPanelOpacity()));
    setOptional(p, "nvlSpeakerTextColor", s.nvlSpeakerTextColor());
    setOptional(p, "nvlTextColor", s.nvlTextColor());
    setOptional(p, "bubbleAsset", s.bubbleAssetPath());
    setOptional(p, "bubbleColor", s.bubbleColor());
    setOptional(p, "bubbleOpacity", s.bubbleOpacity() == null ? null : format(s.bubbleOpacity()));
    setOptional(p, "bubbleBorderColor", s.bubbleBorderColor());
    setOptional(p, "bubbleSpeakerTextColor", s.bubbleSpeakerTextColor());
    setOptional(p, "bubbleTextColor", s.bubbleTextColor());
    setOptional(p, "bubbleCornerRadius", format(s.bubbleCornerRadius()));
    setOptional(p, "bubbleBorderWidth", format(s.bubbleBorderWidth()));
    return p;
  }

  public static Properties toButtonProperties(List<VnUiActionButtonSpec> buttons) {
    Properties p = new Properties();
    if (buttons == null || buttons.isEmpty()) return p;

    List<String> ids = new ArrayList<>();
    Map<String, VnUiActionButtonSpec> unique = new LinkedHashMap<>();
    for (VnUiActionButtonSpec button : buttons) {
      if (button == null) continue;
      String id = normalize(button.id(), "");
      if (id == null || id.isBlank()) continue;
      if (!unique.containsKey(id)) ids.add(id);
      unique.put(id, button);
    }
    if (ids.isEmpty()) return p;
    p.setProperty("textBoxButton.ids", String.join(",", ids));
    for (String id : ids) {
      VnUiActionButtonSpec b = unique.get(id);
      if (b == null) continue;
      String prefix = "textBoxButton." + id + ".";
      setOptional(p, prefix + "label", b.label());
      setOptional(p, prefix + "action", b.action());
      setOptional(p, prefix + "target", b.target());
      p.setProperty(prefix + "enabled", Boolean.toString(b.enabled()));
      if ("viewport".equalsIgnoreCase(b.coordinateSpace())) {
        p.setProperty(prefix + "space", "viewport");
      }
      setOptional(p, prefix + "asset", b.assetPath());
      setOptional(p, prefix + "hoverAsset", b.hoverAssetPath());
      setOptional(p, prefix + "disabledAsset", b.disabledAssetPath());
      setOptional(p, prefix + "boundsPoints", b.boundsPoints());
      p.setProperty(prefix + "x", format(b.x()));
      p.setProperty(prefix + "y", format(b.y()));
      p.setProperty(prefix + "width", format(b.width()));
      p.setProperty(prefix + "height", format(b.height()));
    }
    return p;
  }

  public static Properties toProperties(VnUiLayoutSpec layout, VnUiStyleSpec style) {
    return toProperties(layout, style, List.of());
  }

  public static Properties toProperties(VnUiLayoutSpec layout, VnUiStyleSpec style, List<VnUiActionButtonSpec> buttons) {
    Properties merged = new Properties();
    Properties lp = toProperties(layout);
    for (String key : lp.stringPropertyNames()) {
      merged.setProperty(key, lp.getProperty(key));
    }
    Properties sp = toStyleProperties(style);
    for (String key : sp.stringPropertyNames()) {
      merged.setProperty(key, sp.getProperty(key));
    }
    Properties bp = toButtonProperties(buttons);
    for (String key : bp.stringPropertyNames()) {
      merged.setProperty(key, bp.getProperty(key));
    }
    return merged;
  }

  public static String defaultProjectRelativePath() {
    return DEFAULT_LAYOUT_PATHS[0];
  }

  private static String format(double value) {
    if (Math.rint(value) == value) return Long.toString((long) value);
    return String.format(java.util.Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static Properties loadPropertiesFromAssetsInternal(AssetCatalog assets, List<String> diagnostics) {
    if (assets == null) return new Properties();
    List<String> candidates = new ArrayList<>();
    String configured = readManifestLayoutPath(assets, diagnostics);
    if (configured != null) candidates.add(configured);
    for (String path : DEFAULT_LAYOUT_PATHS) candidates.add(path);

    boolean configuredTried = false;
    for (String path : candidates) {
      if (path == null || path.isBlank()) continue;
      if (configured != null && configured.equals(path)) configuredTried = true;
      Properties p = loadFromAssets(assets, path, diagnostics);
      if (p != null) return p;
      if (configured != null && configured.equals(path) && diagnostics != null) {
        diagnostics.add("Configured dialogueLayout not found: " + path);
      }
    }
    if (configured != null && !configuredTried && diagnostics != null) {
      diagnostics.add("Configured dialogueLayout was ignored due to invalid path: " + configured);
    }
    return new Properties();
  }

  private static Properties loadPropertiesFromProjectRootInternal(File projectRoot, List<String> diagnostics) {
    if (projectRoot == null) return new Properties();
    List<String> candidates = candidatePaths(projectRoot, diagnostics);
    String configured = readManifestLayoutPath(projectRoot, diagnostics);
    for (String rel : candidates) {
      File f = new File(projectRoot, rel);
      if (!f.exists() || !f.isFile()) {
        if (configured != null && configured.equals(rel) && diagnostics != null) {
          diagnostics.add("Configured dialogueLayout file does not exist: " + rel);
        }
        continue;
      }
      Properties p = loadFromFile(f, diagnostics);
      if (p != null) return p;
    }
    return new Properties();
  }

  private static Properties loadFromAssets(AssetCatalog assets, String path, List<String> diagnostics) {
    if (path == null || path.isBlank()) return null;
    try (InputStream in = assets.open(AssetType.SCRIPT, path)) {
      if (in == null) return null;
      Properties p = new Properties();
      p.load(in);
      return p;
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Failed to parse layout properties '" + path + "': " + simplify(ex));
      }
      return null;
    }
  }

  private static Properties loadFromFile(File file, List<String> diagnostics) {
    try (FileInputStream fis = new FileInputStream(file)) {
      Properties p = new Properties();
      p.load(fis);
      return p;
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Failed to parse layout properties '" + file.getPath() + "': " + simplify(ex));
      }
      return null;
    }
  }

  private static List<String> candidatePaths(File projectRoot, List<String> diagnostics) {
    Set<String> paths = new LinkedHashSet<>();
    String configured = readManifestLayoutPath(projectRoot, diagnostics);
    if (configured != null) paths.add(configured);
    for (String path : DEFAULT_LAYOUT_PATHS) paths.add(path);
    return new ArrayList<>(paths);
  }

  private static String readManifestLayoutPath(File projectRoot, List<String> diagnostics) {
    File manifest = new File(projectRoot, "jvn.project");
    if (!manifest.exists()) return null;
    try (FileInputStream fis = new FileInputStream(manifest)) {
      Properties p = new Properties();
      p.load(fis);
      String value = p.getProperty("dialogueLayout");
      if (value == null || value.isBlank()) return null;
      return value.trim();
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Failed to read jvn.project for dialogueLayout: " + simplify(ex));
      }
      return null;
    }
  }

  private static String readManifestLayoutPath(AssetCatalog assets, List<String> diagnostics) {
    if (assets == null) return null;
    try (InputStream in = assets.open(AssetType.SCRIPT, "jvn.project")) {
      if (in == null) return null;
      Properties p = new Properties();
      p.load(in);
      String value = p.getProperty("dialogueLayout");
      if (value == null || value.isBlank()) return null;
      return value.trim();
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Failed to read jvn.project from assets for dialogueLayout: " + simplify(ex));
      }
      return null;
    }
  }

  private static void warnUnknownKeys(Properties props, List<String> diagnostics) {
    if (props == null || diagnostics == null) return;
    for (String key : props.stringPropertyNames()) {
      if (key == null || key.isBlank()) continue;
      if (KNOWN_DIALOGUE_LAYOUT_KEYS.contains(key)) continue;
      if (KNOWN_DIALOGUE_STYLE_KEYS.contains(key)) continue;
      if ("textBoxButton.ids".equals(key)) continue;
      if (key.startsWith("textBoxButton.")) {
        int secondDot = key.indexOf('.', "textBoxButton.".length());
        if (secondDot <= "textBoxButton.".length() || secondDot >= key.length() - 1) {
          diagnostics.add("Malformed textbox button key '" + key + "'; expected textBoxButton.<id>.<field>");
          continue;
        }
        String field = key.substring(secondDot + 1);
        if (KNOWN_TEXTBOX_BUTTON_FIELDS.contains(field)) continue;
        String suggestion = closestKeyHint(field, KNOWN_TEXTBOX_BUTTON_FIELDS);
        diagnostics.add("Unknown textbox button field '" + field + "' in key '" + key + "'" + suggestion);
        continue;
      }
      String suggestion = closestKeyHint(key, allKnownDialogueKeys());
      diagnostics.add("Unknown dialogue layout key '" + key + "'" + suggestion);
    }
  }

  private static Set<String> allKnownDialogueKeys() {
    Set<String> combined = new LinkedHashSet<>();
    combined.addAll(KNOWN_DIALOGUE_LAYOUT_KEYS);
    combined.addAll(KNOWN_DIALOGUE_STYLE_KEYS);
    return combined;
  }

  private static String closestKeyHint(String key, Set<String> candidates) {
    if (key == null || key.isBlank() || candidates == null || candidates.isEmpty()) return "";
    String source = key.trim().toLowerCase(Locale.ROOT);
    String best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (String candidate : candidates) {
      if (candidate == null || candidate.isBlank()) continue;
      int distance = levenshteinDistance(source, candidate.toLowerCase(Locale.ROOT));
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    if (best == null || bestDistance > 2) return "";
    return " (did you mean '" + best + "'?)";
  }

  private static int levenshteinDistance(String a, String b) {
    if (a == null || b == null) return Integer.MAX_VALUE;
    int[][] dp = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
    for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        int deletion = dp[i - 1][j] + 1;
        int insertion = dp[i][j - 1] + 1;
        int substitution = dp[i - 1][j - 1] + cost;
        dp[i][j] = Math.min(Math.min(deletion, insertion), substitution);
      }
    }
    return dp[a.length()][b.length()];
  }

  private static void warnAdjustedDouble(String key, double raw, double normalized, List<String> diagnostics) {
    if (diagnostics == null) return;
    if (nearlyEqual(raw, normalized)) return;
    diagnostics.add("Value for '" + key + "' was adjusted to " + format(normalized) + " (from " + format(raw) + ")");
  }

  private static void warnAdjustedOptionalDouble(String key, Double raw, Double normalized, List<String> diagnostics) {
    if (diagnostics == null) return;
    if (raw == null && normalized == null) return;
    if (raw != null && normalized != null && nearlyEqual(raw, normalized)) return;
    diagnostics.add("Value for '" + key + "' was adjusted to " + (normalized == null ? "null" : format(normalized))
        + " (from " + (raw == null ? "null" : format(raw)) + ")");
  }

  private static void warnAdjustedOptionalInt(String key, Integer raw, Integer normalized, List<String> diagnostics) {
    if (diagnostics == null) return;
    if (raw == null && normalized == null) return;
    if (raw != null && raw.equals(normalized)) return;
    diagnostics.add("Value for '" + key + "' was adjusted to " + normalized + " (from " + raw + ")");
  }

  private static void warnAdjustedInt(String key, int raw, int normalized, List<String> diagnostics) {
    if (diagnostics == null) return;
    if (raw == normalized) return;
    diagnostics.add("Value for '" + key + "' was adjusted to " + normalized + " (from " + raw + ")");
  }

  private static boolean nearlyEqual(double a, double b) {
    return Math.abs(a - b) < 1e-9;
  }

  private static void validateBoundsPoints(String key, String raw, List<String> diagnostics) {
    if (raw == null || raw.isBlank() || diagnostics == null) return;
    List<BoundsPointCodec.Point> points = BoundsPointCodec.parse(raw);
    if (points.size() < 3) {
      diagnostics.add("Invalid bounds points for '" + key + "': at least 3 valid points are required");
    }
  }

  private static Double parseOptionalDouble(String raw, Double def, List<String> diagnostics, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      double value = Double.parseDouble(raw.trim());
      if (!Double.isFinite(value)) throw new NumberFormatException("non-finite");
      return value;
    } catch (Exception ex) {
      if (diagnostics != null) diagnostics.add("Invalid number for '" + key + "': '" + raw + "'");
      return def;
    }
  }

  private static Integer parseOptionalInt(String raw, Integer def, List<String> diagnostics, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ex) {
      if (diagnostics != null) diagnostics.add("Invalid integer for '" + key + "': '" + raw + "'");
      return def;
    }
  }

  private static int parseInt(String raw, int def, List<String> diagnostics, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ex) {
      if (diagnostics != null) diagnostics.add("Invalid integer for '" + key + "': '" + raw + "' (using " + def + ")");
      return def;
    }
  }

  private static double parseDouble(String raw, double def, List<String> diagnostics, String key) {
    if (raw == null || raw.isBlank()) return def;
    try {
      double value = Double.parseDouble(raw.trim());
      if (!Double.isFinite(value)) throw new NumberFormatException("non-finite");
      return value;
    } catch (Exception ex) {
      if (diagnostics != null) {
        diagnostics.add("Invalid number for '" + key + "': '" + raw + "' (using " + format(def) + ")");
      }
      return def;
    }
  }

  private static boolean parseBoolean(String raw, boolean def, List<String> diagnostics, String key) {
    if (raw == null || raw.isBlank()) return def;
    String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
    if ("true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value)) return true;
    if ("false".equals(value) || "0".equals(value) || "no".equals(value) || "off".equals(value)) return false;
    if (diagnostics != null) {
      diagnostics.add("Invalid boolean for '" + key + "': '" + raw + "' (using " + def + ")");
    }
    return def;
  }

  private static List<String> parseCsv(String raw) {
    List<String> out = new ArrayList<>();
    if (raw == null || raw.isBlank()) return out;
    String[] parts = raw.split(",");
    for (String part : parts) {
      String value = normalize(part, null);
      if (value != null) out.add(value);
    }
    return out;
  }

  private static List<VnUiActionButtonSpec> parseTextBoxButtons(
      Properties props,
      List<VnUiActionButtonSpec> baseButtons,
      List<String> diagnostics
  ) {
    Map<String, VnUiActionButtonSpec> baseById = new LinkedHashMap<>();
    if (baseButtons != null) {
      for (VnUiActionButtonSpec button : baseButtons) {
        if (button == null || button.id() == null || button.id().isBlank()) continue;
        baseById.put(button.id(), button);
      }
    }

    List<String> ids = parseCsv(props.getProperty("textBoxButton.ids"));
    if (ids.isEmpty()) ids = collectTextBoxButtonIds(props);
    if (ids.isEmpty() && !baseById.isEmpty()) ids = new ArrayList<>(baseById.keySet());

    List<VnUiActionButtonSpec> result = new ArrayList<>();
    Set<String> seenIds = new LinkedHashSet<>();
    for (String idRaw : ids) {
      String id = normalize(idRaw, null);
      if (id == null) continue;
      if (!seenIds.add(id)) {
        diagnostics.add("Duplicate textbox button id '" + id + "'; later declaration ignored");
        continue;
      }
      String prefix = "textBoxButton." + id + ".";
      VnUiActionButtonSpec base = baseById.get(id);
      if (base == null) base = VnUiActionButtonSpec.defaults(id);
      String label = normalize(props.getProperty(prefix + "label"), base.label());
      String action = normalize(props.getProperty(prefix + "action"), base.action());
      String target = normalize(props.getProperty(prefix + "target"), base.target());
      String inlineTarget = VnUiActionButtonActions.inlineTarget(action);
      if (target == null && inlineTarget != null) {
        target = inlineTarget;
      }
      boolean enabled = parseBoolean(props.getProperty(prefix + "enabled"), base.enabled(), diagnostics, prefix + "enabled");
      String requestedSpace = normalize(props.getProperty(prefix + "space"), base.coordinateSpace());
      String coordinateSpace = VnUiActionButtonSpec.normalizeCoordinateSpace(requestedSpace);
      if (requestedSpace != null
          && !"viewport".equalsIgnoreCase(requestedSpace)
          && !"screen".equalsIgnoreCase(requestedSpace)
          && !"global".equalsIgnoreCase(requestedSpace)
          && !"textbox".equalsIgnoreCase(requestedSpace)
          && diagnostics != null) {
        diagnostics.add("Invalid textbox button space '" + requestedSpace + "' for '" + id + "' (using textbox)");
      }
      String asset = normalize(props.getProperty(prefix + "asset"), base.assetPath());
      String hoverAsset = normalize(props.getProperty(prefix + "hoverAsset"), base.hoverAssetPath());
      String disabledAsset = normalize(props.getProperty(prefix + "disabledAsset"), base.disabledAssetPath());
      String boundsPoints = normalize(props.getProperty(prefix + "boundsPoints"), base.boundsPoints());
      double x = parseDouble(props.getProperty(prefix + "x"), base.x(), diagnostics, prefix + "x");
      double y = parseDouble(props.getProperty(prefix + "y"), base.y(), diagnostics, prefix + "y");
      double width = parseDouble(props.getProperty(prefix + "width"), base.width(), diagnostics, prefix + "width");
      double height = parseDouble(props.getProperty(prefix + "height"), base.height(), diagnostics, prefix + "height");
      VnUiActionButtonSpec button = new VnUiActionButtonSpec(
          id,
          label,
          action,
          target,
          enabled,
          asset,
          hoverAsset,
          disabledAsset,
          boundsPoints,
          x,
          y,
          width,
          height,
          coordinateSpace
      );
      warnAdjustedDouble(prefix + "x", x, button.x(), diagnostics);
      warnAdjustedDouble(prefix + "y", y, button.y(), diagnostics);
      warnAdjustedDouble(prefix + "width", width, button.width(), diagnostics);
      warnAdjustedDouble(prefix + "height", height, button.height(), diagnostics);
      validateBoundsPoints(prefix + "boundsPoints", button.boundsPoints(), diagnostics);

      String normalizedAction = VnUiActionButtonActions.normalize(button.action());
      if (!VnUiActionButtonActions.isSupported(button.action())) {
        diagnostics.add("Textbox button '" + id + "' uses unknown action '" + button.action() + "'");
      }
      if (VnUiActionButtonActions.requiresTarget(normalizedAction) && normalize(button.target(), "").isBlank()) {
        diagnostics.add("Textbox button '" + id + "' uses " + normalizedAction + " without target");
      }
      result.add(button);
    }
    return result;
  }

  private static List<String> collectTextBoxButtonIds(Properties props) {
    Set<String> ids = new LinkedHashSet<>();
    for (String key : props.stringPropertyNames()) {
      if (!key.startsWith("textBoxButton.")) continue;
      int dot = key.indexOf('.', "textBoxButton.".length());
      if (dot <= "textBoxButton.".length()) continue;
      String id = key.substring("textBoxButton.".length(), dot).trim();
      if (!id.isEmpty() && !"ids".equalsIgnoreCase(id)) ids.add(id);
    }
    return new ArrayList<>(ids);
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? fallback : trimmed;
  }

  private static void setOptional(Properties properties, String key, String value) {
    String normalized = normalize(value, null);
    if (normalized == null) properties.remove(key);
    else properties.setProperty(key, normalized);
  }

  private static String simplify(Exception ex) {
    if (ex == null) return "unknown error";
    String message = ex.getMessage();
    if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
    return ex.getClass().getSimpleName() + ": " + message;
  }
}
