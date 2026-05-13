package com.jvn.editor.ui;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import com.jvn.core.ui.BoundsPointCodec;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.text.TextEffect;
import com.jvn.core.vn.text.TextParser;
import com.jvn.core.vn.text.TextSpan;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import com.jvn.fx.ui.ProjectFontResolver;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Visual editor for dialogue UI layout (textbox/namebox/text bounds/choice bounds).
 * The view emits full properties text that can be synced with a code editor.
 */
public class DialogueLayoutEditorView extends BorderPane {
  private static final double PREVIEW_PADDING = 8.0;
  private static final String DEFAULT_FONT_FAMILY = "Arial";
  private static final int DEFAULT_NAME_FONT_SIZE = 18;
  private static final int DEFAULT_DIALOGUE_FONT_SIZE = 16;
  private static final int DEFAULT_CHOICE_FONT_SIZE = 16;
  private static final Color RUNTIME_TEXTBOX_COLOR = Color.rgb(0, 0, 0, 0.62);
  private static final Color RUNTIME_NAME_BOX_COLOR = Color.rgb(30, 30, 50, 0.9);
  private static final Color RUNTIME_TEXT_COLOR = Color.WHITE;
  private static final Color RUNTIME_CHOICE_BG_COLOR = Color.rgb(50, 50, 70, 0.9);
  private static final Color RUNTIME_CHOICE_HOVER_COLOR = Color.rgb(70, 70, 100, 0.9);
  private static final Color RUNTIME_CHOICE_DISABLED_COLOR = Color.rgb(60, 60, 60, 0.6);
  private static final Color RUNTIME_TEXT_COLOR_DISABLED = Color.color(1, 1, 1, 0.5);
  private static final Color RUNTIME_CHOICE_DISABLED_BORDER_COLOR = Color.color(1, 1, 1, 0.55);
  private static final String[] KNOWN_KEYS = new String[] {
      "textBoxX",
      "textBoxY",
      "textBoxWidth",
      "textBoxHeight",
      "textBoxPadding",
      "textBoxAsset",
      "textBoxColor",
      "textBoxOpacity",
      "textBoxBoundsPoints",
      "nameBoxBoundsPoints",
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
      "nameBoxXOffset",
      "nameBoxYOffset",
      "nameBoxWidth",
      "nameBoxHeight",
      "nameTextXOffset",
      "nameTextBaselineOffset",
      "nameTextTopPadding",
      "nameTextBottomPadding",
      "nameTextYAlign",
      "nameTextXAlign",
      "dialogueTextHorizontalPadding",
      "dialogueTextTopPadding",
      "dialogueTextRightPadding",
      "dialogueTextBottomPadding",
      "dialogueTextXAlign",
      "choiceXCenter",
      "choiceYStart",
      "choiceWidthFactor",
      "choiceHeight",
      "choiceGap",
      "choiceTextXPadding",
      "choiceTextTopPadding",
      "choiceTextBottomPadding",
      "choiceTextYAlign",
      "choiceTextXAlign",
      "nameBoxAutoWidth",
      "characterHeightFactor",
      "characterBaselineY",
      "nvlX",
      "nvlY",
      "nvlWidth",
      "nvlHeight",
      "nvlPadding",
      "nvlSpeakerWidth",
      "nvlEntryGap",
      "nvlMaxEntries",
      "nvlPanelAsset",
      "nvlPanelColor",
      "nvlPanelOpacity",
      "nvlSpeakerTextColor",
      "nvlTextColor",
      "bubbleWidthFactor",
      "bubbleMinHeight",
      "bubbleTextPadding",
      "bubbleYOffset",
      "bubbleTailSize",
      "bubbleAsset",
      "bubbleColor",
      "bubbleOpacity",
      "bubbleBorderColor",
      "bubbleSpeakerTextColor",
      "bubbleTextColor",
      "bubbleCornerRadius",
      "bubbleBorderWidth"
  };

  private final Canvas preview = new Canvas(920, 430);
  private StackPane previewPaneHost;
  private final Properties rawProperties = new Properties();
  private VnUiLayoutSpec spec = VnUiLayoutSpec.defaults();
  private VnUiStyleSpec style = VnUiStyleSpec.defaults();
  private Consumer<String> onLayoutTextChanged;
  private boolean suppressEvents = false;
  private String lastLoadedText = "";
  private String lastEmittedText = "";
  private File projectRoot;
  private Image textBoxAssetImage;
  private Image nameBoxAssetImage;
  private Image choiceButtonAssetImage;
  private Image choiceButtonHoverAssetImage;
  private Image choiceButtonDisabledAssetImage;
  private final Map<String, Image> previewAssetCache = new LinkedHashMap<>();
  private List<VnUiActionButtonSpec> textBoxButtons = new ArrayList<>();
  private final UndoManager undoManager = new UndoManager();
  private Button btnUndo;
  private Button btnRedo;
  private boolean applyingHistory = false;
  private final Label validation = new Label("No issues detected.");
  private List<String> parserDiagnostics = List.of();
  private final List<String> lineDiagnostics = new ArrayList<>();

  private String previewSpeakerName = "Speaker Name";
  private String previewDialogueLine1 = "Narrator: The GUI editor now controls dialogue bounds.";
  private String previewDialogueLine2 = "Drag the highlighted blocks or edit numeric fields.";
  private List<String> previewChoiceLabels = List.of("Choice 1", "Choice 2", "Choice 3");

  private final Spinner<Double> spTextBoxX = spinner(0, 1, 0, 0.01);
  private final Spinner<Double> spTextBoxY = spinner(0, 1, 0.75, 0.01);
  private final Spinner<Double> spTextBoxWidth = spinner(0.05, 1, 1, 0.01);
  private final Spinner<Double> spTextBoxHeight = spinner(0.05, 1, 0.25, 0.01);
  private final Spinner<Double> spTextBoxPadding = spinner(0, 200, 20, 1);
  private final Spinner<Double> spTextBoxOverlayOpacity = spinner(0, 1, 0.28, 0.01);
  private final CheckBox chkTextBoxOverlayEnabled = new CheckBox("Enable overlay tint");
  private final TextField tfTextBoxAsset = new TextField();
  private final TextField tfTextBoxColor = new TextField();
  private final TextField tfChoiceButtonAsset = new TextField();
  private final TextField tfChoiceButtonHoverAsset = new TextField();
  private final TextField tfChoiceButtonSelectedAsset = new TextField();
  private final TextField tfChoiceButtonDisabledAsset = new TextField();

  private final ComboBox<String> cbNameTextFontWeight = new ComboBox<>();
  private final Spinner<Double> spNameBoxOpacity = spinner(0, 1, 1, 0.05);
  private final CheckBox cbNameBoxAutoWidth = new CheckBox("Auto-width");
  private final ComboBox<String> cbDialogueTextFontWeight = new ComboBox<>();
  private final ComboBox<String> cbChoiceFontWeight = new ComboBox<>();

  private final Spinner<Double> spNameBoxXOffset = spinner(-500, 500, 20, 1);
  private final Spinner<Double> spNameBoxYOffset = spinner(-500, 500, -40, 1);
  private final Spinner<Double> spNameBoxWidth = spinner(20, 1000, 200, 1);
  private final Spinner<Double> spNameBoxHeight = spinner(12, 300, 40, 1);
  private final Spinner<Double> spNameTextXOffset = spinner(-300, 300, 10, 1);
  private final Spinner<Double> spNameTextBaselineOffset = spinner(-300, 300, 25, 1);
  private final Spinner<Double> spNameTextTopPadding = spinner(0, 300, 0, 1);
  private final Spinner<Double> spNameTextBottomPadding = spinner(0, 300, 0, 1);
  private final Spinner<Double> spNameTextYAlign = spinner(-1, 1, -1, 0.05);
  private final Spinner<Double> spNameTextXAlign = spinner(0, 1, 0, 0.05);

  private final Spinner<Double> spDialoguePaddingX = spinner(0, 300, 20, 1);
  private final Spinner<Double> spDialoguePaddingTop = spinner(-300, 300, 40, 1);
  private final Spinner<Double> spDialoguePaddingRight = spinner(0, 300, 20, 1);
  private final Spinner<Double> spDialoguePaddingBottom = spinner(0, 300, 10, 1);
  private final Spinner<Double> spDialogueTextXAlign = spinner(0, 1, 0, 0.05);

  private final Spinner<Double> spChoiceXCenter = spinner(0, 1, 0.5, 0.01);
  private final Spinner<Double> spChoiceYStart = spinner(-1, 1, -1, 0.01);
  private final Spinner<Double> spChoiceWidthFactor = spinner(0.1, 1, 0.6, 0.01);
  private final Spinner<Double> spChoiceHeight = spinner(14, 200, 50, 1);
  private final Spinner<Double> spChoiceGap = spinner(0, 120, 10, 1);
  private final Spinner<Double> spChoiceTextXPadding = spinner(0, 300, 20, 1);
  private final Spinner<Double> spChoiceTextTopPadding = spinner(0, 300, 0, 1);
  private final Spinner<Double> spChoiceTextBottomPadding = spinner(0, 300, 0, 1);
  private final Spinner<Double> spChoiceTextYAlign = spinner(-1, 1, -1, 0.05);
  private final Spinner<Double> spChoiceTextXAlign = spinner(0, 1, 0, 0.05);
  private final TextField tfChoiceBgColor = new TextField();
  private final TextField tfChoiceHoverColor = new TextField();
  private final TextField tfChoiceSelectedColor = new TextField();
  private final TextField tfChoiceDisabledColor = new TextField();
  private final TextField tfChoiceTextColor = new TextField();
  private final TextField tfChoiceHoverTextColor = new TextField();
  private final TextField tfChoiceSelectedTextColor = new TextField();
  private final TextField tfChoiceDisabledTextColor = new TextField();
  private final TextField tfChoiceBorderColor = new TextField();
  private final TextField tfChoiceHoverBorderColor = new TextField();
  private final TextField tfChoiceSelectedBorderColor = new TextField();
  private final TextField tfChoiceDisabledBorderColor = new TextField();
  private final Spinner<Double> spChoiceCornerRadius = spinner(0, 96, 10, 1);
  private final Spinner<Double> spChoiceBorderWidth = spinner(0, 12, 2, 0.1);
  private final Spinner<Double> spChoiceTextBaselineOffset = spinner(-120, 120, 5, 1);
  private final ComboBox<String> cbPreviewMode = new ComboBox<>();
  private final ComboBox<String> cbBubblePreviewAnchor = new ComboBox<>();

  private final Spinner<Double> spNvlX = spinner(0, 1, 0.08, 0.01);
  private final Spinner<Double> spNvlY = spinner(0, 1, 0.10, 0.01);
  private final Spinner<Double> spNvlWidth = spinner(0.1, 1, 0.84, 0.01);
  private final Spinner<Double> spNvlHeight = spinner(0.1, 1, 0.72, 0.01);
  private final Spinner<Double> spNvlPadding = spinner(0, 300, 24, 1);
  private final Spinner<Double> spNvlSpeakerWidth = spinner(40, 600, 160, 1);
  private final Spinner<Double> spNvlEntryGap = spinner(0, 160, 18, 1);
  private final Spinner<Double> spNvlMaxEntries = spinner(1, 20, 6, 1);
  private final TextField tfNvlPanelAsset = new TextField();
  private final TextField tfNvlPanelColor = new TextField();
  private final Spinner<Double> spNvlPanelOpacity = spinner(0, 1, 0.84, 0.01);
  private final TextField tfNvlSpeakerTextColor = new TextField();
  private final TextField tfNvlTextColor = new TextField();

  private final Spinner<Double> spBubbleWidthFactor = spinner(0.1, 0.8, 0.28, 0.01);
  private final Spinner<Double> spBubbleMinHeight = spinner(32, 300, 92, 1);
  private final Spinner<Double> spBubbleTextPadding = spinner(4, 160, 18, 1);
  private final Spinner<Double> spBubbleYOffset = spinner(-200, 200, 26, 1);
  private final Spinner<Double> spBubbleTailSize = spinner(4, 64, 18, 1);
  private final TextField tfBubbleAsset = new TextField();
  private final TextField tfBubbleColor = new TextField();
  private final Spinner<Double> spBubbleOpacity = spinner(0, 1, 0.96, 0.01);
  private final TextField tfBubbleBorderColor = new TextField();
  private final TextField tfBubbleSpeakerTextColor = new TextField();
  private final TextField tfBubbleTextColor = new TextField();
  private final Spinner<Double> spBubbleCornerRadius = spinner(0, 96, 20, 1);
  private final Spinner<Double> spBubbleBorderWidth = spinner(0, 12, 2, 0.1);

  private final ListView<String> lvTextBoxButtons = new ListView<>();
  private final TextField tfButtonId = new TextField();
  private final TextField tfButtonLabel = new TextField();
  private final ComboBox<String> cbButtonAction = new ComboBox<>();
  private final TextField tfButtonTarget = new TextField();
  private final CheckBox chkButtonEnabled = new CheckBox("Enabled");
  private final Spinner<Double> spButtonX = spinner(0, 1, 0.75, 0.01);
  private final Spinner<Double> spButtonY = spinner(0, 1, 0.08, 0.01);
  private final Spinner<Double> spButtonWidth = spinner(0.01, 1, 0.12, 0.01);
  private final Spinner<Double> spButtonHeight = spinner(0.01, 1, 0.25, 0.01);
  private final TextField tfButtonAsset = new TextField();
  private final TextField tfButtonHoverAsset = new TextField();
  private final TextField tfButtonDisabledAsset = new TextField();

  private DragTarget dragTarget = DragTarget.NONE;
  private double dragStartX;
  private double dragStartY;
  private VnUiLayoutSpec dragStartSpec = VnUiLayoutSpec.defaults();
  private List<VnUiActionButtonSpec> dragStartButtons = List.of();
  private int selectedButtonIndex = -1;
  private int dragButtonIndex = -1;
  private Stage runtimePreviewStage;
  private VnPreviewView runtimePreviewView;
  private AnimationTimer runtimePreviewTimer;
  private long runtimePreviewLastNs = -1L;

  private enum DragTarget {
    NONE,
    TEXT_BOX,
    TEXT_BOX_RESIZE,
    NAME_BOX,
    CHOICE_BLOCK,
    CHOICE_RESIZE,
    DIALOGUE_BOUNDS,
    DIALOGUE_BOUNDS_RESIZE,
    TEXTBOX_BUTTON,
    TEXTBOX_BUTTON_RESIZE
  }

  private enum PreviewMode {
    STANDARD,
    NVL,
    BUBBLE
  }

  public DialogueLayoutEditorView() {
    setPadding(new Insets(8));

    preview.setManaged(false);
    previewPaneHost = new StackPane(preview);
    StackPane.setAlignment(preview, Pos.TOP_LEFT);
    previewPaneHost.getStyleClass().add("layout-studio-preview-host");
    previewPaneHost.setPadding(new Insets(PREVIEW_PADDING));
    setCenter(previewPaneHost);

    ScrollPane controls = new ScrollPane(buildControls());
    controls.setFitToWidth(true);
    controls.setPrefWidth(350);
    controls.getStyleClass().add("layout-studio-controls-pane");
    setRight(controls);

    previewPaneHost.widthProperty().addListener((o, ov, nv) -> updatePreviewSize(previewPaneHost));
    previewPaneHost.heightProperty().addListener((o, ov, nv) -> updatePreviewSize(previewPaneHost));
    preview.widthProperty().addListener((o, ov, nv) -> redraw());
    preview.heightProperty().addListener((o, ov, nv) -> redraw());

    registerPreviewDrag();
    registerControlListeners();
    updatePreviewSize(previewPaneHost);
    validateState();
    redraw();
  }

  public void setOnLayoutTextChanged(Consumer<String> onLayoutTextChanged) {
    this.onLayoutTextChanged = onLayoutTextChanged;
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    previewAssetCache.clear();
    loadPreviewAssets();
    updatePreviewSize(previewPaneHost);
    validateState();
    redraw();
  }

  public void setLayoutText(String text) {
    String normalizedInput = normalizeText(text);
    if (normalizedInput.equals(lastLoadedText)) return;
    suppressEvents = true;
    rawProperties.clear();
    try {
      if (text != null && !text.isBlank()) rawProperties.load(new StringReader(text));
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      // Keep defaults for invalid input.
    }
    VnUiLayoutLoader.LoadResult parsed = VnUiLayoutLoader.parseWithDiagnostics(
        rawProperties,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults()
    );
    parserDiagnostics = parsed.diagnostics();
    lineDiagnostics.clear();
    lineDiagnostics.addAll(DslPropertyDiagnostics.dialogueIssues(text, parserDiagnostics));
    spec = parsed.layout();
    style = parsed.style();
    textBoxButtons = new ArrayList<>(parsed.textBoxButtons());
    applySpecToControls(spec);
    applyStyleToControls(style);
    refreshTextBoxButtonList();
    setSelectedTextBoxButton(textBoxButtons.isEmpty() ? -1 : 0);
    loadPreviewAssets();
    suppressEvents = false;
    validateState();
    redraw();
    lastLoadedText = normalizedInput;
    String serialized = serialize(spec, style, textBoxButtons, rawProperties);
    lastEmittedText = normalizeText(serialized);
    if (!applyingHistory) {
      undoManager.setInitialState(serialized);
    }
  }

  public String getLayoutText() {
    return serialize(spec, style, textBoxButtons, rawProperties);
  }

  public void setPreviewContent(String speakerName, String line1, String line2, List<String> choices) {
    this.previewSpeakerName = speakerName != null ? speakerName : "Speaker Name";
    this.previewDialogueLine1 = line1 != null ? line1 : "";
    this.previewDialogueLine2 = line2 != null ? line2 : "";
    this.previewChoiceLabels = choices != null ? choices : List.of();
    redraw();
  }

  private javafx.scene.Node buildControls() {
    cbPreviewMode.getItems().setAll("Standard", "NVL", "Bubble");
    cbPreviewMode.setValue("Standard");
    cbBubblePreviewAnchor.getItems().setAll("Auto", "Left", "Center", "Right");
    cbBubblePreviewAnchor.setValue("Auto");
    cbBubblePreviewAnchor.setDisable(true);
    cbNameTextFontWeight.getItems().setAll("NORMAL", "BOLD");
    cbNameTextFontWeight.setValue("BOLD");
    cbDialogueTextFontWeight.getItems().setAll("NORMAL", "BOLD");
    cbDialogueTextFontWeight.setValue("NORMAL");
    cbChoiceFontWeight.getItems().setAll("NORMAL", "BOLD");
    cbChoiceFontWeight.setValue("NORMAL");
    tfTextBoxAsset.setPromptText("assets/ui/textbox.png");
    tfTextBoxColor.setPromptText("#000000");
    tfChoiceButtonAsset.setPromptText("assets/ui/choice.png");
    tfChoiceButtonHoverAsset.setPromptText("assets/ui/choice_hover.png");
    tfChoiceButtonSelectedAsset.setPromptText("assets/ui/choice_selected.png");
    tfChoiceButtonDisabledAsset.setPromptText("assets/ui/choice_disabled.png");
    tfNvlPanelAsset.setPromptText("assets/ui/nvl_panel.png");
    tfNvlPanelColor.setPromptText("#08111acc");
    tfNvlSpeakerTextColor.setPromptText("#F7D89A");
    tfNvlTextColor.setPromptText("#E8EDF6");
    tfBubbleAsset.setPromptText("assets/ui/bubble.png");
    tfBubbleColor.setPromptText("#152238ee");
    tfBubbleBorderColor.setPromptText("#A9BCD9");
    tfBubbleSpeakerTextColor.setPromptText("#FFD78A");
    tfBubbleTextColor.setPromptText("#F1F5FF");
    tfChoiceBgColor.setPromptText("#3a3f54");
    tfChoiceHoverColor.setPromptText("#4a5570");
    tfChoiceSelectedColor.setPromptText("#4a5570");
    tfChoiceDisabledColor.setPromptText("#3c3f4a");
    tfChoiceTextColor.setPromptText("#ffffff");
    tfChoiceHoverTextColor.setPromptText("#ffffff");
    tfChoiceSelectedTextColor.setPromptText("#ffffff");
    tfChoiceDisabledTextColor.setPromptText("#8b90a0");
    tfChoiceBorderColor.setPromptText("#b2c5ff");
    tfChoiceHoverBorderColor.setPromptText("#c5d3ff");
    tfChoiceSelectedBorderColor.setPromptText("#c5d3ff");
    tfChoiceDisabledBorderColor.setPromptText("#7a8194");
    tfButtonId.setPromptText("save");
    tfButtonLabel.setPromptText("Save");
    tfButtonTarget.setPromptText("optional target");
    tfButtonAsset.setPromptText("assets/ui/save_btn.png");
    tfButtonHoverAsset.setPromptText("assets/ui/save_btn_hover.png");
    tfButtonDisabledAsset.setPromptText("assets/ui/save_btn_disabled.png");
    cbButtonAction.getItems().setAll(
        "noop", "advance", "quick_save", "quick_load",
        "save_slots", "load_slots", "save_menu", "load_menu",
        "settings_menu", "main_menu", "open_menu",
        "toggle_history", "toggle_skip", "toggle_auto", "toggle_ui"
    );
    cbButtonAction.setEditable(true);
    cbButtonAction.getSelectionModel().select("noop");
    lvTextBoxButtons.setPrefHeight(132);
    spNameTextYAlign.setTooltip(new Tooltip("Vertical align inside the padded name box. Set to -1 to keep legacy baseline mode."));
    spChoiceTextYAlign.setTooltip(new Tooltip("Vertical align inside the padded choice button. Set to -1 to keep legacy baseline mode."));

    GridPane previewGrid = sectionGrid();
    int row = 0;
    row = addRow(previewGrid, row, "Preview Mode", cbPreviewMode);
    row = addRow(previewGrid, row, "Bubble Anchor", cbBubblePreviewAnchor);
    TitledPane tpPreview = collapsibleSection("Presentation Preview", previewGrid, true);

    // --- Section: Textbox ---
    GridPane textboxGrid = sectionGrid();
    row = 0;
    row = addRow(textboxGrid, row, "TextBox X", spTextBoxX);
    row = addRow(textboxGrid, row, "TextBox Y", spTextBoxY);
    row = addRow(textboxGrid, row, "TextBox Width", spTextBoxWidth);
    row = addRow(textboxGrid, row, "TextBox Height", spTextBoxHeight);
    row = addRow(textboxGrid, row, "TextBox Padding", spTextBoxPadding);
    row = addRow(textboxGrid, row, "TextBox Asset", assetFieldRow(tfTextBoxAsset, "Select Textbox Asset"));
    row = addRow(textboxGrid, row, "TextBox Tint", ColorFieldHelper.create(tfTextBoxColor));
    chkTextBoxOverlayEnabled.setSelected(true);
    spTextBoxOverlayOpacity.setDisable(false);
    chkTextBoxOverlayEnabled.selectedProperty().addListener((o, ov, nv) -> spTextBoxOverlayOpacity.setDisable(!Boolean.TRUE.equals(nv)));
    HBox overlayRow = new HBox(8, chkTextBoxOverlayEnabled, spTextBoxOverlayOpacity);
    overlayRow.setAlignment(Pos.CENTER_LEFT);
    row = addRow(textboxGrid, row, "Asset Overlay", overlayRow);
    Button textBoxBoundsStudioBtn = iconButton(CssIcon.grid("#7ec8e3"), "Open textbox bounds studio");
    textBoxBoundsStudioBtn.setOnAction(e -> openTextBoxBoundsStudio());
    row = addRow(textboxGrid, row, "Bounds Studio", textBoxBoundsStudioBtn);
    TitledPane tpTextbox = collapsibleSection("Textbox", textboxGrid, true);

    // --- Section: Name Box ---
    GridPane nameGrid = sectionGrid();
    row = 0;
    row = addRow(nameGrid, row, "Name X Offset", spNameBoxXOffset);
    row = addRow(nameGrid, row, "Name Y Offset", spNameBoxYOffset);
    row = addRow(nameGrid, row, "Name Width", spNameBoxWidth);
    row = addRow(nameGrid, row, "Name Height", spNameBoxHeight);
    row = addRow(nameGrid, row, "Name Text X Offset", spNameTextXOffset);
    row = addRow(nameGrid, row, "Name Text Baseline", spNameTextBaselineOffset);
    row = addRow(nameGrid, row, "Name Text Top Pad", spNameTextTopPadding);
    row = addRow(nameGrid, row, "Name Text Bottom Pad", spNameTextBottomPadding);
    row = addRow(nameGrid, row, "Name Text Y Align", spNameTextYAlign);
    row = addRow(nameGrid, row, "Name Text X Align", spNameTextXAlign);
    row = addRow(nameGrid, row, "Name Font Weight", cbNameTextFontWeight);
    row = addRow(nameGrid, row, "Name Box Opacity", spNameBoxOpacity);
    row = addRow(nameGrid, row, "Name Box Auto-Width", cbNameBoxAutoWidth);
    Button nameBoundsStudioBtn = iconButton(CssIcon.grid("#7ec8e3"), "Open name box bounds studio");
    nameBoundsStudioBtn.setOnAction(e -> openNameBoxBoundsStudio());
    row = addRow(nameGrid, row, "Bounds Studio", nameBoundsStudioBtn);
    TitledPane tpName = collapsibleSection("Name Box", nameGrid, false);

    // --- Section: Dialogue Text Bounds ---
    GridPane textBoundsGrid = sectionGrid();
    row = 0;
    row = addRow(textBoundsGrid, row, "Text Left Padding", spDialoguePaddingX);
    row = addRow(textBoundsGrid, row, "Text Top Padding", spDialoguePaddingTop);
    row = addRow(textBoundsGrid, row, "Text Right Padding", spDialoguePaddingRight);
    row = addRow(textBoundsGrid, row, "Text Bottom Padding", spDialoguePaddingBottom);
    row = addRow(textBoundsGrid, row, "Dialogue Text X Align", spDialogueTextXAlign);
    row = addRow(textBoundsGrid, row, "Dialogue Font Weight", cbDialogueTextFontWeight);
    Button dialogueBoundsStudioBtn = iconButton(CssIcon.grid("#7ec8e3"), "Open dialogue text bounds studio");
    dialogueBoundsStudioBtn.setOnAction(e -> openDialogueTextBoundsStudio());
    row = addRow(textBoundsGrid, row, "Bounds Studio", dialogueBoundsStudioBtn);
    TitledPane tpTextBounds = collapsibleSection("Dialogue Text Bounds", textBoundsGrid, false);

    // --- Section: Choice Layout ---
    GridPane choiceLayoutGrid = sectionGrid();
    row = 0;
    row = addRow(choiceLayoutGrid, row, "Choice X Center", spChoiceXCenter);
    row = addRow(choiceLayoutGrid, row, "Choice Y Start", spChoiceYStart);
    row = addRow(choiceLayoutGrid, row, "Choice Width Factor", spChoiceWidthFactor);
    row = addRow(choiceLayoutGrid, row, "Choice Height", spChoiceHeight);
    row = addRow(choiceLayoutGrid, row, "Choice Gap", spChoiceGap);
    row = addRow(choiceLayoutGrid, row, "Choice Text Padding", spChoiceTextXPadding);
    row = addRow(choiceLayoutGrid, row, "Choice Text Top Pad", spChoiceTextTopPadding);
    row = addRow(choiceLayoutGrid, row, "Choice Text Bottom Pad", spChoiceTextBottomPadding);
    row = addRow(choiceLayoutGrid, row, "Choice Text Y Align", spChoiceTextYAlign);
    row = addRow(choiceLayoutGrid, row, "Choice Text X Align", spChoiceTextXAlign);
    row = addRow(choiceLayoutGrid, row, "Choice Font Weight", cbChoiceFontWeight);
    row = addRow(choiceLayoutGrid, row, "Button Asset", assetFieldRow(tfChoiceButtonAsset, "Select Choice Button Asset"));
    row = addRow(choiceLayoutGrid, row, "Hover Asset", assetFieldRow(tfChoiceButtonHoverAsset, "Select Choice Hover Asset"));
    row = addRow(choiceLayoutGrid, row, "Selected Asset", assetFieldRow(tfChoiceButtonSelectedAsset, "Select Choice Selected Asset"));
    row = addRow(choiceLayoutGrid, row, "Disabled Asset", assetFieldRow(tfChoiceButtonDisabledAsset, "Select Choice Disabled Asset"));
    Button choiceBoundsStudioBtn = iconButton(CssIcon.grid("#7ec8e3"), "Open choice button bounds studio");
    choiceBoundsStudioBtn.setOnAction(e -> openChoiceButtonBoundsStudio());
    row = addRow(choiceLayoutGrid, row, "Bounds Studio", choiceBoundsStudioBtn);
    TitledPane tpChoiceLayout = collapsibleSection("Choice Layout & Assets", choiceLayoutGrid, true);

    // --- Section: Choice Colors ---
    GridPane choiceColorGrid = sectionGrid();
    row = 0;
    row = addRow(choiceColorGrid, row, "Background Color", ColorFieldHelper.create(tfChoiceBgColor));
    row = addRow(choiceColorGrid, row, "Hover Color", ColorFieldHelper.create(tfChoiceHoverColor));
    row = addRow(choiceColorGrid, row, "Selected Color", ColorFieldHelper.create(tfChoiceSelectedColor));
    row = addRow(choiceColorGrid, row, "Disabled Color", ColorFieldHelper.create(tfChoiceDisabledColor));
    row = addRow(choiceColorGrid, row, "Text Color", ColorFieldHelper.create(tfChoiceTextColor));
    row = addRow(choiceColorGrid, row, "Hover Text Color", ColorFieldHelper.create(tfChoiceHoverTextColor));
    row = addRow(choiceColorGrid, row, "Selected Text Color", ColorFieldHelper.create(tfChoiceSelectedTextColor));
    row = addRow(choiceColorGrid, row, "Disabled Text Color", ColorFieldHelper.create(tfChoiceDisabledTextColor));
    row = addRow(choiceColorGrid, row, "Border Color", ColorFieldHelper.create(tfChoiceBorderColor));
    row = addRow(choiceColorGrid, row, "Hover Border", ColorFieldHelper.create(tfChoiceHoverBorderColor));
    row = addRow(choiceColorGrid, row, "Selected Border", ColorFieldHelper.create(tfChoiceSelectedBorderColor));
    row = addRow(choiceColorGrid, row, "Disabled Border", ColorFieldHelper.create(tfChoiceDisabledBorderColor));
    row = addRow(choiceColorGrid, row, "Corner Radius", spChoiceCornerRadius);
    row = addRow(choiceColorGrid, row, "Border Width", spChoiceBorderWidth);
    row = addRow(choiceColorGrid, row, "Text Baseline", spChoiceTextBaselineOffset);
    TitledPane tpChoiceColors = collapsibleSection("Choice Colors & Borders", choiceColorGrid, false);

    // --- Section: NVL ---
    GridPane nvlGrid = sectionGrid();
    row = 0;
    row = addRow(nvlGrid, row, "NVL X", spNvlX);
    row = addRow(nvlGrid, row, "NVL Y", spNvlY);
    row = addRow(nvlGrid, row, "NVL Width", spNvlWidth);
    row = addRow(nvlGrid, row, "NVL Height", spNvlHeight);
    row = addRow(nvlGrid, row, "NVL Padding", spNvlPadding);
    row = addRow(nvlGrid, row, "Speaker Width", spNvlSpeakerWidth);
    row = addRow(nvlGrid, row, "Entry Gap", spNvlEntryGap);
    row = addRow(nvlGrid, row, "Max Entries", spNvlMaxEntries);
    row = addRow(nvlGrid, row, "Panel Asset", assetFieldRow(tfNvlPanelAsset, "Select NVL Panel Asset"));
    row = addRow(nvlGrid, row, "Panel Color", ColorFieldHelper.create(tfNvlPanelColor));
    row = addRow(nvlGrid, row, "Panel Opacity", spNvlPanelOpacity);
    row = addRow(nvlGrid, row, "Speaker Color", ColorFieldHelper.create(tfNvlSpeakerTextColor));
    row = addRow(nvlGrid, row, "Text Color", ColorFieldHelper.create(tfNvlTextColor));
    TitledPane tpNvl = collapsibleSection("NVL Presentation", nvlGrid, false);

    // --- Section: Bubble ---
    GridPane bubbleGrid = sectionGrid();
    row = 0;
    row = addRow(bubbleGrid, row, "Bubble Width Factor", spBubbleWidthFactor);
    row = addRow(bubbleGrid, row, "Bubble Min Height", spBubbleMinHeight);
    row = addRow(bubbleGrid, row, "Text Padding", spBubbleTextPadding);
    row = addRow(bubbleGrid, row, "Bubble Y Offset", spBubbleYOffset);
    row = addRow(bubbleGrid, row, "Tail Size", spBubbleTailSize);
    row = addRow(bubbleGrid, row, "Bubble Asset", assetFieldRow(tfBubbleAsset, "Select Bubble Asset"));
    row = addRow(bubbleGrid, row, "Bubble Color", ColorFieldHelper.create(tfBubbleColor));
    row = addRow(bubbleGrid, row, "Bubble Opacity", spBubbleOpacity);
    row = addRow(bubbleGrid, row, "Border Color", ColorFieldHelper.create(tfBubbleBorderColor));
    row = addRow(bubbleGrid, row, "Speaker Color", ColorFieldHelper.create(tfBubbleSpeakerTextColor));
    row = addRow(bubbleGrid, row, "Text Color", ColorFieldHelper.create(tfBubbleTextColor));
    row = addRow(bubbleGrid, row, "Corner Radius", spBubbleCornerRadius);
    row = addRow(bubbleGrid, row, "Border Width", spBubbleBorderWidth);
    TitledPane tpBubble = collapsibleSection("Bubble Presentation", bubbleGrid, false);

    // --- Section: Textbox Buttons ---
    GridPane btnGrid = sectionGrid();
    row = 0;
    HBox buttonToolbar = new HBox(6);
    buttonToolbar.setAlignment(Pos.CENTER_LEFT);
    Button addButton = iconButton(CssIcon.plus("#8cd48c"), "Add textbox button");
    Button duplicateButton = iconButton(CssIcon.copy("#9cc7ff"), "Duplicate selected button");
    Button removeButton = iconButton(CssIcon.minus("#e07070"), "Remove selected button");
    Button moveUpButton = iconButton(CssIcon.arrowUp(), "Move selected button up");
    Button moveDownButton = iconButton(CssIcon.arrowDown(), "Move selected button down");
    Button boundsStudioBtn = iconButton(CssIcon.grid("#7ec8e3"), "Open textbox button bounds studio");
    boundsStudioBtn.setOnAction(e -> openTextBoxButtonBoundsStudio());
    buttonToolbar.getChildren().addAll(addButton, duplicateButton, removeButton, moveUpButton, moveDownButton, boundsStudioBtn);
    row = addRow(btnGrid, row, "Buttons", buttonToolbar);
    row = addRow(btnGrid, row, "Button List", lvTextBoxButtons);
    row = addRow(btnGrid, row, "Button Id", tfButtonId);
    row = addRow(btnGrid, row, "Button Label", tfButtonLabel);
    row = addRow(btnGrid, row, "Button Action", cbButtonAction);
    row = addRow(btnGrid, row, "Button Target", tfButtonTarget);
    row = addRow(btnGrid, row, "Button State", chkButtonEnabled);
    row = addRow(btnGrid, row, "Button X", spButtonX);
    row = addRow(btnGrid, row, "Button Y", spButtonY);
    row = addRow(btnGrid, row, "Button Width", spButtonWidth);
    row = addRow(btnGrid, row, "Button Height", spButtonHeight);
    row = addRow(btnGrid, row, "Button Asset", assetFieldRow(tfButtonAsset, "Select Button Asset"));
    row = addRow(btnGrid, row, "Button Hover Asset", assetFieldRow(tfButtonHoverAsset, "Select Button Hover Asset"));
    row = addRow(btnGrid, row, "Button Disabled Asset", assetFieldRow(tfButtonDisabledAsset, "Select Button Disabled Asset"));
    addButton.setOnAction(e -> addTextBoxButton());
    duplicateButton.setOnAction(e -> duplicateSelectedTextBoxButton());
    removeButton.setOnAction(e -> removeSelectedTextBoxButton());
    moveUpButton.setOnAction(e -> moveSelectedTextBoxButton(-1));
    moveDownButton.setOnAction(e -> moveSelectedTextBoxButton(1));
    TitledPane tpButtons = collapsibleSection("Textbox Buttons", btnGrid, false);

    // --- History + hint ---
    btnUndo = iconButton(CssIcon.undo(), "Undo");
    btnRedo = iconButton(CssIcon.redo(), "Redo");
    btnUndo.setDisable(true);
    btnRedo.setDisable(true);
    btnUndo.setOnAction(e -> performUndo());
    btnRedo.setOnAction(e -> performRedo());
    undoManager.setOnUndoAvailableChanged(available -> btnUndo.setDisable(!available));
    undoManager.setOnRedoAvailableChanged(available -> btnRedo.setDisable(!available));
    Button btnRuntimePreview = iconButton(CssIcon.speech("#8cd48c"), "Run runtime VN preview with current layout/style");
    btnRuntimePreview.setOnAction(e -> openRuntimeScriptPreview());
    HBox historyButtons = new HBox(8, btnUndo, btnRedo, btnRuntimePreview);
    historyButtons.setPadding(new Insets(4, 8, 4, 8));

    Label hint = new Label("Drag blocks in preview to position or resize textbox/name/choices/text bounds/buttons. Right and bottom text paddings allow exact text area sizing.");
    hint.getStyleClass().add("muted");
    hint.setWrapText(true);
    hint.setPadding(new Insets(4, 8, 8, 8));

    validation.getStyleClass().add("muted");
    validation.setWrapText(true);
    validation.setPadding(new Insets(2, 8, 8, 8));

    javafx.scene.layout.VBox sections = new javafx.scene.layout.VBox(2,
        tpPreview, tpTextbox, tpName, tpTextBounds, tpChoiceLayout, tpChoiceColors, tpNvl, tpBubble, tpButtons,
        historyButtons, hint, validation
    );
    sections.setPadding(new Insets(4));

    UndoManager.installKeyboardShortcuts(this, this::performUndo, this::performRedo);
    updatePreviewModeControls();

    return sections;
  }

  private static GridPane sectionGrid() {
    GridPane g = new GridPane();
    g.setHgap(8);
    g.setVgap(6);
    g.setPadding(new Insets(4, 4, 4, 4));
    return g;
  }

  private static TitledPane collapsibleSection(String title, javafx.scene.Node content, boolean expanded) {
    TitledPane tp = new TitledPane(title, content);
    tp.setExpanded(expanded);
    tp.setAnimated(false);
    tp.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #e6e6e6;");
    return tp;
  }

  private HBox assetFieldRow(TextField field, String dialogTitle) {
    AssetPickerSupport.installAssetDrop(field, this::toProjectRelativePath);
    Button browse = iconButton(CssIcon.folder(), "Browse project assets");
    browse.setOnAction(e -> browseAsset(field, dialogTitle));
    Button importBtn = iconButton(CssIcon.download("#8cd48c"), "Import external asset");
    importBtn.setOnAction(e -> {
      String imported = importAsset(field, dialogTitle);
      if (imported != null && !imported.isBlank()) {
        field.setText(imported);
      }
    });
    Button reveal = iconButton(CssIcon.link("#9cc7ff"), "Reveal in file manager");
    reveal.setOnAction(e -> revealAsset(field.getText()));
    Button clear = iconButton(CssIcon.clearX("#e07070"), "Clear asset path");
    clear.setOnAction(e -> field.setText(""));
    HBox row = new HBox(6, field, browse, importBtn, reveal, clear);
    HBox.setHgrow(field, Priority.ALWAYS);
    return row;
  }

  private static Button iconButton(javafx.scene.Node icon, String tooltip) {
    Button button = new Button();
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltip));
    button.getStyleClass().addAll("layout-studio-action-button", "layout-studio-icon-button");
    return button;
  }

  private int addHeader(GridPane grid, int row, String title) {
    if (row > 0) grid.add(new Separator(), 0, row++, 2, 1);
    Label label = new Label(title);
    label.setFont(Font.font(label.getFont().getFamily(), FontWeight.BOLD, 12));
    grid.add(label, 0, row++, 2, 1);
    return row;
  }

  private int addRow(GridPane grid, int row, String label, Spinner<Double> spinner) {
    return addRow(grid, row, label, (javafx.scene.Node) spinner);
  }

  private int addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
    Label l = new Label(label);
    GridPane.setHgrow(control, Priority.ALWAYS);
    if (control instanceof Spinner<?> spinner) spinner.setMaxWidth(Double.MAX_VALUE);
    if (control instanceof HBox box) box.setMaxWidth(Double.MAX_VALUE);
    if (control instanceof TextField textField) textField.setMaxWidth(Double.MAX_VALUE);
    grid.add(l, 0, row);
    grid.add(control, 1, row);
    return row + 1;
  }

  private void registerControlListeners() {
    List<Spinner<Double>> controls = new ArrayList<>();
    controls.add(spTextBoxX);
    controls.add(spTextBoxY);
    controls.add(spTextBoxWidth);
    controls.add(spTextBoxHeight);
    controls.add(spTextBoxPadding);
    controls.add(spNameBoxXOffset);
    controls.add(spNameBoxYOffset);
    controls.add(spNameBoxWidth);
    controls.add(spNameBoxHeight);
    controls.add(spNameTextXOffset);
    controls.add(spNameTextBaselineOffset);
    controls.add(spNameTextTopPadding);
    controls.add(spNameTextBottomPadding);
    controls.add(spNameTextYAlign);
    controls.add(spNameTextXAlign);
    controls.add(spDialoguePaddingX);
    controls.add(spDialoguePaddingTop);
    controls.add(spDialoguePaddingRight);
    controls.add(spDialoguePaddingBottom);
    controls.add(spDialogueTextXAlign);
    controls.add(spChoiceXCenter);
    controls.add(spChoiceYStart);
    controls.add(spChoiceWidthFactor);
    controls.add(spChoiceHeight);
    controls.add(spChoiceGap);
    controls.add(spChoiceTextXPadding);
    controls.add(spChoiceTextTopPadding);
    controls.add(spChoiceTextBottomPadding);
    controls.add(spChoiceTextYAlign);
    controls.add(spChoiceTextXAlign);
    controls.add(spChoiceCornerRadius);
    controls.add(spChoiceBorderWidth);
    controls.add(spChoiceTextBaselineOffset);
    controls.add(spNvlX);
    controls.add(spNvlY);
    controls.add(spNvlWidth);
    controls.add(spNvlHeight);
    controls.add(spNvlPadding);
    controls.add(spNvlSpeakerWidth);
    controls.add(spNvlEntryGap);
    controls.add(spNvlMaxEntries);
    controls.add(spNvlPanelOpacity);
    controls.add(spBubbleWidthFactor);
    controls.add(spBubbleMinHeight);
    controls.add(spBubbleTextPadding);
    controls.add(spBubbleYOffset);
    controls.add(spBubbleTailSize);
    controls.add(spBubbleOpacity);
    controls.add(spBubbleCornerRadius);
    controls.add(spBubbleBorderWidth);
    controls.add(spTextBoxOverlayOpacity);
    controls.add(spNameBoxOpacity);
    controls.add(spButtonX);
    controls.add(spButtonY);
    controls.add(spButtonWidth);
    controls.add(spButtonHeight);
    for (Spinner<Double> control : controls) {
      control.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    }
    List<TextField> styleFields = List.of(
        tfTextBoxAsset,
        tfTextBoxColor,
        tfChoiceButtonAsset,
        tfChoiceButtonHoverAsset,
        tfChoiceButtonSelectedAsset,
        tfChoiceButtonDisabledAsset,
        tfNvlPanelAsset,
        tfNvlPanelColor,
        tfNvlSpeakerTextColor,
        tfNvlTextColor,
        tfBubbleAsset,
        tfBubbleColor,
        tfBubbleBorderColor,
        tfBubbleSpeakerTextColor,
        tfBubbleTextColor,
        tfChoiceBgColor,
        tfChoiceHoverColor,
        tfChoiceSelectedColor,
        tfChoiceDisabledColor,
        tfChoiceTextColor,
        tfChoiceHoverTextColor,
        tfChoiceSelectedTextColor,
        tfChoiceDisabledTextColor,
        tfChoiceBorderColor,
        tfChoiceHoverBorderColor,
        tfChoiceSelectedBorderColor,
        tfChoiceDisabledBorderColor,
        tfButtonId,
        tfButtonLabel,
        tfButtonTarget,
        tfButtonAsset,
        tfButtonHoverAsset,
        tfButtonDisabledAsset
    );
    for (TextField field : styleFields) {
      field.textProperty().addListener((o, ov, nv) -> onStyleChanged());
    }
    chkTextBoxOverlayEnabled.selectedProperty().addListener((o, ov, nv) -> onStyleChanged());
    chkButtonEnabled.selectedProperty().addListener((o, ov, nv) -> onStyleChanged());
    cbButtonAction.valueProperty().addListener((o, ov, nv) -> onStyleChanged());
    cbPreviewMode.valueProperty().addListener((o, ov, nv) -> {
      updatePreviewModeControls();
      redraw();
    });
    cbBubblePreviewAnchor.valueProperty().addListener((o, ov, nv) -> redraw());
    cbNameTextFontWeight.valueProperty().addListener((o, ov, nv) -> onStyleChanged());
    cbNameBoxAutoWidth.selectedProperty().addListener((o, ov, nv) -> onControlChanged());
    cbDialogueTextFontWeight.valueProperty().addListener((o, ov, nv) -> onStyleChanged());
    cbChoiceFontWeight.valueProperty().addListener((o, ov, nv) -> onStyleChanged());
    lvTextBoxButtons.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
      if (suppressEvents) return;
      int idx = nv == null ? -1 : nv.intValue();
      setSelectedTextBoxButton(idx);
    });
  }

  private void updatePreviewModeControls() {
    cbBubblePreviewAnchor.setDisable(previewMode() != PreviewMode.BUBBLE);
  }

  private void onControlChanged() {
    if (suppressEvents) return;
    spec = readSpecFromControls();
    style = readStyleFromControls();
    syncSelectedTextBoxButtonFromControls();
    loadPreviewAssets();
    refreshDiagnosticsFromUiState();
    validateState();
    redraw();
    emitText();
  }

  private void onStyleChanged() {
    if (suppressEvents) return;
    style = readStyleFromControls();
    syncSelectedTextBoxButtonFromControls();
    loadPreviewAssets();
    refreshDiagnosticsFromUiState();
    validateState();
    redraw();
    emitText();
  }

  private void refreshDiagnosticsFromUiState() {
    String currentText = serialize(spec, style, textBoxButtons, rawProperties);
    lineDiagnostics.clear();
    lineDiagnostics.addAll(DslPropertyDiagnostics.dialogueIssues(currentText, parserDiagnostics));
  }

  private void browseAsset(TextField target, String dialogTitle) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(dialogTitle == null || dialogTitle.isBlank() ? "Select Asset" : dialogTitle);
    AssetPickerSupport.addAssetFilters(chooser);

    File initialDir = resolveInitialAssetDirectory();
    if (initialDir != null && initialDir.isDirectory()) {
      chooser.setInitialDirectory(initialDir);
    }

    Window owner = getScene() != null ? getScene().getWindow() : null;
    File picked = chooser.showOpenDialog(owner);
    if (picked == null) return;
    String relative = toProjectRelativePath(picked);
    target.setText(relative);
  }

  private String importAsset(TextField target, String dialogTitle) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle((dialogTitle == null || dialogTitle.isBlank()) ? "Import Asset" : dialogTitle.replace("Select", "Import"));
    AssetPickerSupport.addAssetFilters(chooser);
    File initialDir = resolveInitialAssetDirectory();
    if (initialDir != null && initialDir.isDirectory()) {
      chooser.setInitialDirectory(initialDir);
    }
    Window owner = getScene() != null ? getScene().getWindow() : null;
    File picked = chooser.showOpenDialog(owner);
    if (picked == null) return null;
    if (projectRoot == null) return toProjectRelativePath(picked);
    try {
      File targetDir = new File(projectRoot, "assets/ui");
      if (!targetDir.exists()) targetDir.mkdirs();
      File destination = new File(targetDir, picked.getName());
      if (destination.exists()) {
        String name = picked.getName();
        String stem = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
          stem = name.substring(0, dot);
          ext = name.substring(dot);
        }
        int idx = 1;
        while (destination.exists()) {
          destination = new File(targetDir, stem + "_" + idx + ext);
          idx++;
        }
      }
      java.nio.file.Files.copy(
          picked.toPath(),
          destination.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return toProjectRelativePath(destination);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return target != null ? normalizeAssetPath(target.getText()) : null;
    }
  }

  private void revealAsset(String path) {
    File file = resolveAssetFile(path);
    AssetPickerSupport.revealFile(file);
  }

  private void registerPreviewDrag() {
    preview.setOnMouseMoved(e -> preview.setCursor(cursorForDragTarget(hitTest(e.getX(), e.getY()))));
    preview.setOnMouseExited(e -> preview.setCursor(Cursor.DEFAULT));
    preview.setOnMousePressed(e -> {
      dragStartX = e.getX();
      dragStartY = e.getY();
      dragStartSpec = spec;
      dragStartButtons = new ArrayList<>(textBoxButtons);
      dragTarget = hitTest(e.getX(), e.getY());
      if ((dragTarget == DragTarget.TEXTBOX_BUTTON || dragTarget == DragTarget.TEXTBOX_BUTTON_RESIZE) && dragButtonIndex >= 0) {
        setSelectedTextBoxButton(dragButtonIndex);
      }
    });
    preview.setOnMouseReleased(e -> {
      boolean wasDragging = dragTarget != DragTarget.NONE;
      dragTarget = DragTarget.NONE;
      dragButtonIndex = -1;
      preview.setCursor(cursorForDragTarget(hitTest(e.getX(), e.getY())));
      if (wasDragging) emitText();
    });
    preview.setOnMouseDragged(e -> {
      if (dragTarget == DragTarget.NONE) return;
      double w = Math.max(1, preview.getWidth());
      double h = Math.max(1, preview.getHeight());
      double scale = previewScale();
      double invScale = scale > 0.0001 ? 1.0 / scale : 1.0;
      double dx = e.getX() - dragStartX;
      double dy = e.getY() - dragStartY;

      VnUiLayoutSpec next = dragStartSpec;
      if (dragTarget == DragTarget.TEXT_BOX) {
        next = new VnUiLayoutSpec(
            dragStartSpec.textBoxX() + (dx / w),
            dragStartSpec.textBoxY() + (dy / h),
            dragStartSpec.textBoxWidth(),
            dragStartSpec.textBoxHeight(),
            dragStartSpec.textBoxPadding(),
            dragStartSpec.nameBoxXOffset(),
            dragStartSpec.nameBoxYOffset(),
            dragStartSpec.nameBoxWidth(),
            dragStartSpec.nameBoxHeight(),
            dragStartSpec.nameTextXOffset(),
            dragStartSpec.nameTextBaselineOffset(),
            dragStartSpec.nameTextTopPadding(),
            dragStartSpec.nameTextBottomPadding(),
            dragStartSpec.nameTextYAlign(),
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.dialogueTextRightPadding(),
            dragStartSpec.dialogueTextBottomPadding(),
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding(),
            dragStartSpec.choiceTextTopPadding(),
            dragStartSpec.choiceTextBottomPadding(),
            dragStartSpec.choiceTextYAlign(),
            dragStartSpec.nameBoxAutoWidth(),
            dragStartSpec.nvlX(),
            dragStartSpec.nvlY(),
            dragStartSpec.nvlWidth(),
            dragStartSpec.nvlHeight(),
            dragStartSpec.nvlPadding(),
            dragStartSpec.nvlSpeakerWidth(),
            dragStartSpec.nvlEntryGap(),
            dragStartSpec.nvlMaxEntries(),
            dragStartSpec.bubbleWidthFactor(),
            dragStartSpec.bubbleMinHeight(),
            dragStartSpec.bubbleTextPadding(),
            dragStartSpec.bubbleYOffset(),
            dragStartSpec.bubbleTailSize()
        );
      } else if (dragTarget == DragTarget.NAME_BOX) {
        next = new VnUiLayoutSpec(
            dragStartSpec.textBoxX(),
            dragStartSpec.textBoxY(),
            dragStartSpec.textBoxWidth(),
            dragStartSpec.textBoxHeight(),
            dragStartSpec.textBoxPadding(),
            dragStartSpec.nameBoxXOffset() + dx * invScale,
            dragStartSpec.nameBoxYOffset() + dy * invScale,
            dragStartSpec.nameBoxWidth(),
            dragStartSpec.nameBoxHeight(),
            dragStartSpec.nameTextXOffset(),
            dragStartSpec.nameTextBaselineOffset(),
            dragStartSpec.nameTextTopPadding(),
            dragStartSpec.nameTextBottomPadding(),
            dragStartSpec.nameTextYAlign(),
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.dialogueTextRightPadding(),
            dragStartSpec.dialogueTextBottomPadding(),
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding(),
            dragStartSpec.choiceTextTopPadding(),
            dragStartSpec.choiceTextBottomPadding(),
            dragStartSpec.choiceTextYAlign(),
            dragStartSpec.nameBoxAutoWidth(),
            dragStartSpec.nvlX(),
            dragStartSpec.nvlY(),
            dragStartSpec.nvlWidth(),
            dragStartSpec.nvlHeight(),
            dragStartSpec.nvlPadding(),
            dragStartSpec.nvlSpeakerWidth(),
            dragStartSpec.nvlEntryGap(),
            dragStartSpec.nvlMaxEntries(),
            dragStartSpec.bubbleWidthFactor(),
            dragStartSpec.bubbleMinHeight(),
            dragStartSpec.bubbleTextPadding(),
            dragStartSpec.bubbleYOffset(),
            dragStartSpec.bubbleTailSize()
        );
      } else if (dragTarget == DragTarget.CHOICE_BLOCK) {
        double currentChoiceStart = resolveChoiceYStart(dragStartSpec, h, 3, scale);
        double nextStart = currentChoiceStart + dy;
        double nextYStartNorm = clamp01(nextStart / h);
        next = new VnUiLayoutSpec(
            dragStartSpec.textBoxX(),
            dragStartSpec.textBoxY(),
            dragStartSpec.textBoxWidth(),
            dragStartSpec.textBoxHeight(),
            dragStartSpec.textBoxPadding(),
            dragStartSpec.nameBoxXOffset(),
            dragStartSpec.nameBoxYOffset(),
            dragStartSpec.nameBoxWidth(),
            dragStartSpec.nameBoxHeight(),
            dragStartSpec.nameTextXOffset(),
            dragStartSpec.nameTextBaselineOffset(),
            dragStartSpec.nameTextTopPadding(),
            dragStartSpec.nameTextBottomPadding(),
            dragStartSpec.nameTextYAlign(),
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.dialogueTextRightPadding(),
            dragStartSpec.dialogueTextBottomPadding(),
            dragStartSpec.choiceXCenter() + (dx / w),
            nextYStartNorm,
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding(),
            dragStartSpec.choiceTextTopPadding(),
            dragStartSpec.choiceTextBottomPadding(),
            dragStartSpec.choiceTextYAlign(),
            dragStartSpec.nameBoxAutoWidth(),
            dragStartSpec.nvlX(),
            dragStartSpec.nvlY(),
            dragStartSpec.nvlWidth(),
            dragStartSpec.nvlHeight(),
            dragStartSpec.nvlPadding(),
            dragStartSpec.nvlSpeakerWidth(),
            dragStartSpec.nvlEntryGap(),
            dragStartSpec.nvlMaxEntries(),
            dragStartSpec.bubbleWidthFactor(),
            dragStartSpec.bubbleMinHeight(),
            dragStartSpec.bubbleTextPadding(),
            dragStartSpec.bubbleYOffset(),
            dragStartSpec.bubbleTailSize()
        );
      } else if (dragTarget == DragTarget.TEXT_BOX_RESIZE) {
        double newWidth = Math.max(0.05, dragStartSpec.textBoxWidth() + (dx / w));
        double newHeight = Math.max(0.05, dragStartSpec.textBoxHeight() + (dy / h));
        next = new VnUiLayoutSpec(
            dragStartSpec.textBoxX(),
            dragStartSpec.textBoxY(),
            newWidth,
            newHeight,
            dragStartSpec.textBoxPadding(),
            dragStartSpec.nameBoxXOffset(),
            dragStartSpec.nameBoxYOffset(),
            dragStartSpec.nameBoxWidth(),
            dragStartSpec.nameBoxHeight(),
            dragStartSpec.nameTextXOffset(),
            dragStartSpec.nameTextBaselineOffset(),
            dragStartSpec.nameTextTopPadding(),
            dragStartSpec.nameTextBottomPadding(),
            dragStartSpec.nameTextYAlign(),
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.dialogueTextRightPadding(),
            dragStartSpec.dialogueTextBottomPadding(),
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding(),
            dragStartSpec.choiceTextTopPadding(),
            dragStartSpec.choiceTextBottomPadding(),
            dragStartSpec.choiceTextYAlign(),
            dragStartSpec.nameBoxAutoWidth(),
            dragStartSpec.nvlX(),
            dragStartSpec.nvlY(),
            dragStartSpec.nvlWidth(),
            dragStartSpec.nvlHeight(),
            dragStartSpec.nvlPadding(),
            dragStartSpec.nvlSpeakerWidth(),
            dragStartSpec.nvlEntryGap(),
            dragStartSpec.nvlMaxEntries(),
            dragStartSpec.bubbleWidthFactor(),
            dragStartSpec.bubbleMinHeight(),
            dragStartSpec.bubbleTextPadding(),
            dragStartSpec.bubbleYOffset(),
            dragStartSpec.bubbleTailSize()
        );
      } else if (dragTarget == DragTarget.CHOICE_RESIZE) {
        double newWidthFactor = Math.max(0.05, dragStartSpec.choiceWidthFactor() + (dx / w));
        double newChoiceHeight = Math.max(8, dragStartSpec.choiceHeight() + dy * invScale);
        next = new VnUiLayoutSpec(
            dragStartSpec.textBoxX(),
            dragStartSpec.textBoxY(),
            dragStartSpec.textBoxWidth(),
            dragStartSpec.textBoxHeight(),
            dragStartSpec.textBoxPadding(),
            dragStartSpec.nameBoxXOffset(),
            dragStartSpec.nameBoxYOffset(),
            dragStartSpec.nameBoxWidth(),
            dragStartSpec.nameBoxHeight(),
            dragStartSpec.nameTextXOffset(),
            dragStartSpec.nameTextBaselineOffset(),
            dragStartSpec.nameTextTopPadding(),
            dragStartSpec.nameTextBottomPadding(),
            dragStartSpec.nameTextYAlign(),
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.dialogueTextRightPadding(),
            dragStartSpec.dialogueTextBottomPadding(),
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            newWidthFactor,
            newChoiceHeight,
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding(),
            dragStartSpec.choiceTextTopPadding(),
            dragStartSpec.choiceTextBottomPadding(),
            dragStartSpec.choiceTextYAlign(),
            dragStartSpec.nameBoxAutoWidth(),
            dragStartSpec.nvlX(),
            dragStartSpec.nvlY(),
            dragStartSpec.nvlWidth(),
            dragStartSpec.nvlHeight(),
            dragStartSpec.nvlPadding(),
            dragStartSpec.nvlSpeakerWidth(),
            dragStartSpec.nvlEntryGap(),
            dragStartSpec.nvlMaxEntries(),
            dragStartSpec.bubbleWidthFactor(),
            dragStartSpec.bubbleMinHeight(),
            dragStartSpec.bubbleTextPadding(),
            dragStartSpec.bubbleYOffset(),
            dragStartSpec.bubbleTailSize()
        );
      } else if (dragTarget == DragTarget.DIALOGUE_BOUNDS || dragTarget == DragTarget.DIALOGUE_BOUNDS_RESIZE) {
        ProjectViewportSpec.Dimensions vp = ProjectViewportSpec.resolve(projectRoot);
        TextBoxGeometry textBox = computeTextBoxGeometry(dragStartSpec, vp.width(), vp.height());
        double boxW = Math.max(1, textBox.width());
        double boxH = Math.max(1, textBox.height());
        double minTextW = 40;
        double minTextH = 20;
        double dxSpec = dx * invScale;
        double dySpec = dy * invScale;

        double left0 = clamp(dragStartSpec.dialogueTextHorizontalPadding(), 0, Math.max(0, boxW - minTextW));
        double top0 = clamp(dragStartSpec.dialogueTextTopPadding(), 0, Math.max(0, boxH - minTextH));
        double right0 = clamp(dragStartSpec.dialogueTextRightPadding(), 0, Math.max(0, boxW - left0 - minTextW));
        double bottom0 = clamp(dragStartSpec.dialogueTextBottomPadding(), 0, Math.max(0, boxH - top0 - minTextH));

        double width0 = Math.max(minTextW, boxW - left0 - right0);
        double height0 = Math.max(minTextH, boxH - top0 - bottom0);

        double left = left0;
        double top = top0;
        double right = right0;
        double bottom = bottom0;

        if (dragTarget == DragTarget.DIALOGUE_BOUNDS) {
          left = clamp(left0 + dxSpec, 0, Math.max(0, boxW - width0));
          top = clamp(top0 + dySpec, 0, Math.max(0, boxH - height0));
          right = Math.max(0, boxW - left - width0);
          bottom = Math.max(0, boxH - top - height0);
        } else {
          double newTextW = clamp(width0 + dxSpec, minTextW, Math.max(minTextW, boxW - left0));
          double newTextH = clamp(height0 + dySpec, minTextH, Math.max(minTextH, boxH - top0));
          right = Math.max(0, boxW - left0 - newTextW);
          bottom = Math.max(0, boxH - top0 - newTextH);
        }

        next = new VnUiLayoutSpec(
            dragStartSpec.textBoxX(),
            dragStartSpec.textBoxY(),
            dragStartSpec.textBoxWidth(),
            dragStartSpec.textBoxHeight(),
            dragStartSpec.textBoxPadding(),
            dragStartSpec.nameBoxXOffset(),
            dragStartSpec.nameBoxYOffset(),
            dragStartSpec.nameBoxWidth(),
            dragStartSpec.nameBoxHeight(),
            dragStartSpec.nameTextXOffset(),
            dragStartSpec.nameTextBaselineOffset(),
            dragStartSpec.nameTextTopPadding(),
            dragStartSpec.nameTextBottomPadding(),
            dragStartSpec.nameTextYAlign(),
            left,
            top,
            right,
            bottom,
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding(),
            dragStartSpec.choiceTextTopPadding(),
            dragStartSpec.choiceTextBottomPadding(),
            dragStartSpec.choiceTextYAlign(),
            dragStartSpec.nameBoxAutoWidth(),
            dragStartSpec.nvlX(),
            dragStartSpec.nvlY(),
            dragStartSpec.nvlWidth(),
            dragStartSpec.nvlHeight(),
            dragStartSpec.nvlPadding(),
            dragStartSpec.nvlSpeakerWidth(),
            dragStartSpec.nvlEntryGap(),
            dragStartSpec.nvlMaxEntries(),
            dragStartSpec.bubbleWidthFactor(),
            dragStartSpec.bubbleMinHeight(),
            dragStartSpec.bubbleTextPadding(),
            dragStartSpec.bubbleYOffset(),
            dragStartSpec.bubbleTailSize()
        );
      } else if ((dragTarget == DragTarget.TEXTBOX_BUTTON || dragTarget == DragTarget.TEXTBOX_BUTTON_RESIZE)
          && dragButtonIndex >= 0 && dragButtonIndex < dragStartButtons.size()) {
        TextBoxGeometry textBox = computeTextBoxGeometry(dragStartSpec, w, h);
        VnUiActionButtonSpec baseButton = dragStartButtons.get(dragButtonIndex);
        if (baseButton != null) {
          double nx = baseButton.x();
          double ny = baseButton.y();
          double nw = baseButton.width();
          double nh = baseButton.height();
          double refWidth = baseButton.viewportSpace() ? Math.max(1.0, w) : Math.max(1.0, textBox.width());
          double refHeight = baseButton.viewportSpace() ? Math.max(1.0, h) : Math.max(1.0, textBox.height());
          if (dragTarget == DragTarget.TEXTBOX_BUTTON) {
            nx = clamp01(baseButton.x() + (dx / refWidth));
            ny = clamp01(baseButton.y() + (dy / refHeight));
          } else {
            nw = clamp(baseButton.width() + (dx / refWidth), 0.01, 1.0);
            nh = clamp(baseButton.height() + (dy / refHeight), 0.01, 1.0);
          }
          VnUiActionButtonSpec moved = new VnUiActionButtonSpec(
              baseButton.id(),
              baseButton.label(),
              baseButton.action(),
              baseButton.target(),
              baseButton.enabled(),
              baseButton.assetPath(),
              baseButton.hoverAssetPath(),
              baseButton.disabledAssetPath(),
              baseButton.boundsPoints(),
              nx,
              ny,
              nw,
              nh,
              baseButton.coordinateSpace()
          );
          textBoxButtons = new ArrayList<>(dragStartButtons);
          textBoxButtons.set(dragButtonIndex, moved);
          setSelectedTextBoxButton(dragButtonIndex);
          refreshDiagnosticsFromUiState();
          validateState();
          redraw();
          return;
        }
      }

      spec = next;
      suppressEvents = true;
      applySpecToControls(spec);
      suppressEvents = false;
      refreshDiagnosticsFromUiState();
      validateState();
      redraw();
    });
  }

  private static final double HANDLE_SIZE = 10;

  private DragTarget hitTest(double x, double y) {
    LayoutRects r = computeRects(spec, preview.getWidth(), preview.getHeight(), previewChoiceCount(), previewScale());
    // Resize handles (bottom-right corners) take priority
    if (isNearCorner(x, y, r.textBox())) return DragTarget.TEXT_BOX_RESIZE;
    if (isNearCorner(x, y, r.choiceBlock())) return DragTarget.CHOICE_RESIZE;
    if (isNearCorner(x, y, r.dialogueBounds())) return DragTarget.DIALOGUE_BOUNDS_RESIZE;
    dragButtonIndex = hitTestButtonResizeIndex(x, y, r.textBox(), preview.getWidth(), preview.getHeight());
    if (dragButtonIndex >= 0) return DragTarget.TEXTBOX_BUTTON_RESIZE;
    dragButtonIndex = hitTestButtonIndex(x, y, r.textBox(), preview.getWidth(), preview.getHeight());
    if (dragButtonIndex >= 0) return DragTarget.TEXTBOX_BUTTON;
    if (isNearBorder(x, y, r.dialogueBounds(), 6.0)) return DragTarget.DIALOGUE_BOUNDS;
    if (r.nameBox().contains(x, y)) return DragTarget.NAME_BOX;
    if (r.choiceBlock().contains(x, y)) return DragTarget.CHOICE_BLOCK;
    if (r.textBox().contains(x, y)) return DragTarget.TEXT_BOX;
    return DragTarget.NONE;
  }

  private static boolean isNearCorner(double x, double y, Rect rect) {
    double cx = rect.x() + rect.w();
    double cy = rect.y() + rect.h();
    return Math.abs(x - cx) <= HANDLE_SIZE && Math.abs(y - cy) <= HANDLE_SIZE;
  }

  private static boolean isNearBorder(double x, double y, Rect rect, double thickness) {
    if (!rect.contains(x, y)) return false;
    double right = rect.x() + rect.w();
    double bottom = rect.y() + rect.h();
    return Math.abs(x - rect.x()) <= thickness
        || Math.abs(x - right) <= thickness
        || Math.abs(y - rect.y()) <= thickness
        || Math.abs(y - bottom) <= thickness;
  }

  private static Cursor cursorForDragTarget(DragTarget target) {
    if (target == null) return Cursor.DEFAULT;
    return switch (target) {
      case TEXT_BOX, NAME_BOX, CHOICE_BLOCK, DIALOGUE_BOUNDS, TEXTBOX_BUTTON -> Cursor.MOVE;
      case TEXT_BOX_RESIZE, CHOICE_RESIZE, DIALOGUE_BOUNDS_RESIZE, TEXTBOX_BUTTON_RESIZE -> Cursor.SE_RESIZE;
      default -> Cursor.DEFAULT;
    };
  }

  private void redraw() {
    double w = Math.max(1, preview.getWidth());
    double h = Math.max(1, preview.getHeight());
    double scale = previewScale();
    GraphicsContext g = preview.getGraphicsContext2D();

    g.setFill(LayoutStudioPalette.CANVAS_BACKGROUND_ALT);
    g.fillRect(0, 0, w, h);

    g.setStroke(LayoutStudioPalette.GRID_LINE);
    g.setLineWidth(1);
    for (int i = 1; i < 6; i++) {
      double yy = (h / 6.0) * i;
      g.strokeLine(0, yy, w, yy);
    }

    if (previewMode() == PreviewMode.NVL) {
      drawNvlPreview(g, w, h, scale);
      return;
    }
    if (previewMode() == PreviewMode.BUBBLE) {
      drawBubblePreview(g, w, h, scale);
      return;
    }

    int choiceCount = previewChoiceCount();
    LayoutRects rects = computeRects(spec, w, h, choiceCount, scale);
    ChoicePreviewStyle choiceStyle = resolveChoicePreviewStyle();
    Font nameFont = resolveNamePreviewFont(scale);
    Font dialogueFont = resolveDialoguePreviewFont(scale);
    Font choiceFont = resolveChoicePreviewFont(scale);
    List<BoundsPointCodec.Point> textBoxBounds = parseBoundsPoints(style.textBoxBoundsPoints());
    List<BoundsPointCodec.Point> nameBoxBounds = parseBoundsPoints(style.nameBoxBoundsPoints());
    List<BoundsPointCodec.Point> dialogueBounds = parseBoundsPoints(style.dialogueTextBoundsPoints());
    List<BoundsPointCodec.Point> choiceButtonBounds = parseBoundsPoints(style.choiceButtonBoundsPoints());

    // Choice block preview (runtime-like).
    double choiceHeight = Math.max(12 * scale, spec.choiceHeight() * scale);
    double choiceGap = Math.max(0, spec.choiceGap() * scale);
    double y = rects.choiceBlock().y();
    for (int i = 0; i < choiceCount; i++) {
      boolean hovered = i == 0;
      boolean enabled = i != 2;
      Image buttonImage = enabled
          ? (hovered ? firstNonNull(choiceButtonHoverAssetImage, choiceButtonAssetImage) : choiceButtonAssetImage)
          : firstNonNull(choiceButtonDisabledAssetImage, choiceButtonAssetImage);
      boolean clipChoiceButton = hasPolygon(choiceButtonBounds);
      double scaledCornerRadius = choiceStyle.cornerRadius() * scale;
      if (buttonImage != null && buttonImage.getWidth() > 1 && buttonImage.getHeight() > 1) {
        if (clipChoiceButton) {
          g.save();
          clipToLocalPolygon(g, choiceButtonBounds, new Rect(rects.choiceBlock().x(), y, rects.choiceBlock().w(), choiceHeight));
          g.drawImage(buttonImage, rects.choiceBlock().x(), y, rects.choiceBlock().w(), choiceHeight);
          g.restore();
        } else {
          g.drawImage(buttonImage, rects.choiceBlock().x(), y, rects.choiceBlock().w(), choiceHeight);
        }
      } else {
        Color fill = !enabled
            ? choiceStyle.disabledBackgroundColor()
            : (hovered ? choiceStyle.hoverBackgroundColor() : choiceStyle.backgroundColor());
        if (clipChoiceButton) {
          g.save();
          clipToLocalPolygon(g, choiceButtonBounds, new Rect(rects.choiceBlock().x(), y, rects.choiceBlock().w(), choiceHeight));
          g.setFill(fill);
          g.fillRect(rects.choiceBlock().x(), y, rects.choiceBlock().w(), choiceHeight);
          g.restore();
        } else {
          g.setFill(fill);
          g.fillRoundRect(
              rects.choiceBlock().x(),
              y,
              rects.choiceBlock().w(),
              choiceHeight,
              scaledCornerRadius,
              scaledCornerRadius);
        }
      }
      Color border = !enabled
          ? choiceStyle.disabledBorderColor()
          : (hovered ? choiceStyle.hoverBorderColor() : choiceStyle.borderColor());
      g.setStroke(border);
      g.setLineWidth(choiceStyle.borderWidth() * scale);
      if (clipChoiceButton) {
        strokeLocalPolygon(g, choiceButtonBounds, new Rect(rects.choiceBlock().x(), y, rects.choiceBlock().w(), choiceHeight));
      } else {
        g.strokeRoundRect(
            rects.choiceBlock().x(),
            y,
            rects.choiceBlock().w(),
            choiceHeight,
            scaledCornerRadius,
            scaledCornerRadius);
      }
      Color textColor = !enabled
          ? choiceStyle.disabledTextColor()
          : (hovered ? choiceStyle.hoverTextColor() : choiceStyle.textColor());
      g.setFill(textColor);
      g.setFont(choiceFont);
      String choiceLabel = i < previewChoiceLabels.size() ? previewChoiceLabels.get(i) : "Choice " + (i + 1);
      double choiceTextWidth = computeTextWidth(g, choiceLabel, choiceFont);
      double choiceTextLeft = rects.choiceBlock().x() + spec.choiceTextXPadding() * scale;
      double choiceContentWidth = Math.max(0, rects.choiceBlock().w() - spec.choiceTextXPadding() * scale * 2.0);
      double choiceTextBaseline = spec.choiceTextYAlign() >= 0.0
          ? resolvePaddedTextBaselineY(
              y,
              choiceHeight,
              spec.choiceTextTopPadding() * scale,
              spec.choiceTextBottomPadding() * scale,
              choiceFont,
              spec.choiceTextYAlign())
          : y + choiceHeight / 2 + choiceStyle.textBaselineOffset() * scale;
      g.fillText(
          choiceLabel,
          resolveAlignedTextX(choiceTextLeft, choiceContentWidth, choiceTextWidth, style.choiceTextXAlign() == null ? 0.0 : style.choiceTextXAlign()),
          choiceTextBaseline);
      y += choiceHeight + choiceGap;
    }

    // Textbox and name box overlay (runtime-like).
    Color textBoxTint = parseColorValue(style.textBoxColor(), RUNTIME_TEXTBOX_COLOR);
    double overlayOpacity = style.textBoxOpacity() == null ? 0.28 : clamp(style.textBoxOpacity(), 0.0, 1.0);
    boolean clipTextBox = hasPolygon(textBoxBounds);
    if (clipTextBox) {
      g.save();
      clipToLocalPolygon(g, textBoxBounds, rects.textBox());
    }
    if (textBoxAssetImage != null && textBoxAssetImage.getWidth() > 1 && textBoxAssetImage.getHeight() > 1) {
      g.drawImage(textBoxAssetImage, rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
      if (overlayOpacity > 0.001) {
        g.setFill(withOpacity(textBoxTint, overlayOpacity));
        g.fillRect(rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
      }
    } else {
      g.setFill(textBoxTint);
      g.fillRect(rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
    }
    if (clipTextBox) {
      g.restore();
    }

    if (!previewSpeakerName.isBlank()) {
      boolean clipNameBox = hasPolygon(nameBoxBounds);
      if (clipNameBox) {
        g.save();
        clipToLocalPolygon(g, nameBoxBounds, rects.nameBox());
      }
      if (nameBoxAssetImage != null && nameBoxAssetImage.getWidth() > 1 && nameBoxAssetImage.getHeight() > 1) {
        g.drawImage(nameBoxAssetImage, rects.nameBox().x(), rects.nameBox().y(), rects.nameBox().w(), rects.nameBox().h());
      } else {
        g.setFill(RUNTIME_NAME_BOX_COLOR);
        g.fillRect(rects.nameBox().x(), rects.nameBox().y(), rects.nameBox().w(), rects.nameBox().h());
      }
      if (clipNameBox) g.restore();

      g.setFill(RUNTIME_TEXT_COLOR);
      g.setFont(nameFont);
      double nameTextLeft = rects.nameBox().x() + spec.nameTextXOffset() * scale;
      double nameTextWidth = computeTextWidth(g, previewSpeakerName, nameFont);
      double nameContentWidth = Math.max(0, rects.nameBox().w() - spec.nameTextXOffset() * scale * 2.0);
      double nameTextBaseline = spec.nameTextYAlign() >= 0.0
          ? resolvePaddedTextBaselineY(
              rects.nameBox().y(),
              rects.nameBox().h(),
              spec.nameTextTopPadding() * scale,
              spec.nameTextBottomPadding() * scale,
              nameFont,
              spec.nameTextYAlign())
          : rects.nameBox().y() + spec.nameTextBaselineOffset() * scale;
      g.fillText(
          previewSpeakerName,
          resolveAlignedTextX(nameTextLeft, nameContentWidth, nameTextWidth, style.nameTextXAlign() == null ? 0.0 : style.nameTextXAlign()),
          nameTextBaseline);
    }

    String fullText = previewDialogueText();
    List<TextSpan> spans = TextParser.parse(fullText);
    int revealedLength = TextParser.plainLength(fullText);
    g.save();
    if (hasPolygon(dialogueBounds)) {
      clipToLocalPolygon(g, dialogueBounds, rects.dialogueBounds());
    } else {
      g.beginPath();
      g.rect(
          rects.dialogueBounds().x(),
          rects.dialogueBounds().y(),
          rects.dialogueBounds().w(),
          rects.dialogueBounds().h()
      );
      g.closePath();
      g.clip();
    }
    drawStyledPreviewText(
        g,
        spans,
        revealedLength,
        rects.dialogueBounds().x(),
        rects.dialogueBounds().y() + computeTextAscent(dialogueFont),
        rects.dialogueBounds().w(),
        dialogueFont,
        style.dialogueTextXAlign() == null ? 0.0 : style.dialogueTextXAlign());
    g.restore();

    // Runtime-like textbox action buttons.
    drawTextBoxButtonPreview(g, rects.textBox(), preview.getWidth(), preview.getHeight());

    // Editor overlays.
    g.setStroke(LayoutStudioPalette.ACCENT_BLUE);
    g.setLineWidth(2);
    if (hasPolygon(textBoxBounds)) strokeLocalPolygon(g, textBoxBounds, rects.textBox());
    else g.strokeRect(rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
    drawResizeHandle(g, rects.textBox(), LayoutStudioPalette.ACCENT_BLUE);

    g.setStroke(LayoutStudioPalette.PANEL_BORDER_LIGHT);
    if (hasPolygon(nameBoxBounds)) strokeLocalPolygon(g, nameBoxBounds, rects.nameBox());
    else g.strokeRect(rects.nameBox().x(), rects.nameBox().y(), rects.nameBox().w(), rects.nameBox().h());

    g.setStroke(LayoutStudioPalette.ACCENT_GOLD);
    g.setLineDashes(6);
    if (hasPolygon(dialogueBounds)) strokeLocalPolygon(g, dialogueBounds, rects.dialogueBounds());
    else g.strokeRect(rects.dialogueBounds().x(), rects.dialogueBounds().y(), rects.dialogueBounds().w(), rects.dialogueBounds().h());
    g.setLineDashes(0);
    drawResizeHandle(g, rects.dialogueBounds(), LayoutStudioPalette.ACCENT_GOLD);
    drawResizeHandle(g, rects.choiceBlock(), LayoutStudioPalette.ACCENT_GOLD);

    drawTag(g, rects.textBox().x() + 6, rects.textBox().y() + 16, "Textbox");
    if (!previewSpeakerName.isBlank()) {
      drawTag(g, rects.nameBox().x() + 6, rects.nameBox().y() - 4, "Name Box");
    }
    drawTag(g, rects.choiceBlock().x() + 6, rects.choiceBlock().y() - 4, "Choices");
    drawTag(g, rects.dialogueBounds().x() + 6, rects.dialogueBounds().y() - 4, "Text Bounds");
  }

  private void drawTextBoxButtonPreview(GraphicsContext g, Rect textBoxRect, double viewportWidth, double viewportHeight) {
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return;
    for (int i = 0; i < textBoxButtons.size(); i++) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null) continue;
      Rect rect = computeTextBoxButtonRect(button, textBoxRect, viewportWidth, viewportHeight);
      boolean hovered = i == selectedButtonIndex;
      boolean enabled = button.enabled();
      Image asset = loadImageAsset(button.assetPath());
      Image hoverAsset = loadImageAsset(button.hoverAssetPath());
      Image disabledAsset = loadImageAsset(button.disabledAssetPath());
      Image drawAsset = !enabled
          ? firstNonNull(disabledAsset, asset)
          : (hovered ? firstNonNull(hoverAsset, asset) : asset);
      List<BoundsPointCodec.Point> polygon = parseBoundsPoints(button.boundsPoints());
      boolean clipButton = hasPolygon(polygon);
      if (drawAsset != null && drawAsset.getWidth() > 1 && drawAsset.getHeight() > 1) {
        if (clipButton) {
          g.save();
          clipToLocalPolygon(g, polygon, rect);
        }
        if (!enabled) g.setGlobalAlpha(0.55);
        g.drawImage(drawAsset, rect.x(), rect.y(), rect.w(), rect.h());
        g.setGlobalAlpha(1.0);
        if (clipButton) g.restore();
      } else {
        Color fill = !enabled
            ? Color.rgb(38, 40, 48, 0.7)
            : (hovered ? Color.rgb(90, 120, 180, 0.8) : Color.rgb(32, 36, 46, 0.78));
        if (clipButton) {
          g.save();
          clipToLocalPolygon(g, polygon, rect);
          g.setFill(fill);
          g.fillRect(rect.x(), rect.y(), rect.w(), rect.h());
          g.restore();
        } else {
          g.setFill(fill);
          g.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 8, 8);
        }
      }
      g.setStroke(!enabled
          ? Color.rgb(120, 125, 136, 0.75)
          : (hovered ? Color.rgb(170, 210, 255, 0.95) : Color.rgb(120, 135, 170, 0.82)));
      g.setLineWidth(hovered ? 2.0 : 1.2);
      if (clipButton) {
        strokeLocalPolygon(g, polygon, rect);
      } else {
        g.strokeRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 8, 8);
      }
      if (i == selectedButtonIndex) {
        double handle = 8;
        double hx = rect.x() + rect.w() - handle;
        double hy = rect.y() + rect.h() - handle;
        g.setFill(LayoutStudioPalette.ACCENT_GOLD);
        g.fillRect(hx, hy, handle, handle);
        g.setStroke(LayoutStudioPalette.PANEL_BORDER);
        g.setLineWidth(1);
        g.strokeRect(hx, hy, handle, handle);
      }
      g.setFill(!enabled ? Color.rgb(172, 176, 188, 0.75) : (hovered ? Color.rgb(245, 252, 255) : Color.rgb(225, 232, 246)));
      Font choiceFont = resolveChoicePreviewFont();
      g.setFont(Font.font(choiceFont.getFamily(), FontWeight.BOLD, clamp(rect.h() * 0.42, 10, 18)));
      String label = normalizeAssetPath(button.label());
      if (label.isBlank()) label = button.id();
      double labelW = computeTextWidth(g, label, g.getFont());
      g.fillText(label, rect.x() + Math.max(8, (rect.w() - labelW) / 2.0), rect.y() + rect.h() * 0.64);
      drawTag(g, rect.x() + 4, rect.y() - 4, i == selectedButtonIndex ? "Btn: " + button.id() + " (drag/resize)" : "Btn: " + button.id());
    }
  }

  private void drawTag(GraphicsContext g, double x, double y, String text) {
    double w = Math.max(54, text.length() * 7.2 + 12);
    g.setFill(LayoutStudioPalette.TAG_BG);
    g.fillRoundRect(x, y - 12, w, 16, 6, 6);
    g.setStroke(LayoutStudioPalette.TAG_BORDER);
    g.strokeRoundRect(x, y - 12, w, 16, 6, 6);
    g.setFill(LayoutStudioPalette.TAG_TEXT);
    g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 11));
    g.fillText(text, x + 6, y);
  }

  private void drawResizeHandle(GraphicsContext g, Rect rect, Color color) {
    if (g == null || rect == null) return;
    double size = 8;
    double hx = rect.x() + rect.w() - size;
    double hy = rect.y() + rect.h() - size;
    g.setFill(color == null ? LayoutStudioPalette.ACCENT_GOLD : color);
    g.fillRect(hx, hy, size, size);
    g.setStroke(LayoutStudioPalette.PANEL_BORDER);
    g.setLineWidth(1);
    g.strokeRect(hx, hy, size, size);
  }

  private Font resolveNamePreviewFont() { return resolveNamePreviewFont(1.0); }
  private Font resolveNamePreviewFont(double scale) {
    String family = normalizeFontFamily(style.nameTextFontFamily(), DEFAULT_FONT_FAMILY);
    double size = clamp(style.nameTextFontSize() == null ? DEFAULT_NAME_FONT_SIZE : style.nameTextFontSize(), 6, 220);
    return ProjectFontResolver.resolve(projectRoot, family, FontWeight.BOLD, size * scale, DEFAULT_FONT_FAMILY);
  }

  private Font resolveDialoguePreviewFont() { return resolveDialoguePreviewFont(1.0); }
  private Font resolveDialoguePreviewFont(double scale) {
    String family = normalizeFontFamily(style.dialogueTextFontFamily(), DEFAULT_FONT_FAMILY);
    double size = clamp(style.dialogueTextFontSize() == null ? DEFAULT_DIALOGUE_FONT_SIZE : style.dialogueTextFontSize(), 6, 220);
    return ProjectFontResolver.resolve(projectRoot, family, FontWeight.NORMAL, size * scale, DEFAULT_FONT_FAMILY);
  }

  private Font resolveChoicePreviewFont() { return resolveChoicePreviewFont(1.0); }
  private Font resolveChoicePreviewFont(double scale) {
    String family = normalizeFontFamily(style.choiceFontFamily(), DEFAULT_FONT_FAMILY);
    double size = clamp(style.choiceFontSize() == null ? DEFAULT_CHOICE_FONT_SIZE : style.choiceFontSize(), 6, 220);
    return ProjectFontResolver.resolve(projectRoot, family, FontWeight.NORMAL, size * scale, DEFAULT_FONT_FAMILY);
  }

  private static String normalizeFontFamily(String raw, String fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    return raw.trim();
  }

  private String previewDialogueText() {
    String line1 = previewDialogueLine1 == null ? "" : previewDialogueLine1.trim();
    String line2 = previewDialogueLine2 == null ? "" : previewDialogueLine2.trim();
    if (line1.isEmpty()) return line2;
    if (line2.isEmpty()) return line1;
    return line1 + " " + line2;
  }

  private PreviewMode previewMode() {
    String raw = cbPreviewMode.getValue();
    if (raw == null) return PreviewMode.STANDARD;
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "nvl" -> PreviewMode.NVL;
      case "bubble" -> PreviewMode.BUBBLE;
      default -> PreviewMode.STANDARD;
    };
  }

  private String bubblePreviewAnchor() {
    String raw = cbBubblePreviewAnchor.getValue();
    return raw == null ? "auto" : raw.trim().toLowerCase(Locale.ROOT);
  }

  private void drawNvlPreview(GraphicsContext g, double w, double h, double scale) {
    Rect panel = new Rect(
        spec.nvlX() * w,
        spec.nvlY() * h,
        spec.nvlWidth() * w,
        spec.nvlHeight() * h
    );
    Image panelImage = loadImageAsset(style.nvlPanelAssetPath());
    Color panelColor = parseColorValue(style.nvlPanelColor(), Color.rgb(8, 17, 26, 0.84));
    double opacity = style.nvlPanelOpacity() == null ? 0.84 : clamp(style.nvlPanelOpacity(), 0.0, 1.0);
    if (panelImage != null && panelImage.getWidth() > 1 && panelImage.getHeight() > 1) {
      g.drawImage(panelImage, panel.x(), panel.y(), panel.w(), panel.h());
      g.setFill(withOpacity(panelColor, opacity));
      g.fillRect(panel.x(), panel.y(), panel.w(), panel.h());
    } else {
      g.setFill(withOpacity(panelColor, opacity));
      g.fillRoundRect(panel.x(), panel.y(), panel.w(), panel.h(), 18 * scale, 18 * scale);
    }
    g.setStroke(LayoutStudioPalette.ACCENT_BLUE);
    g.setLineWidth(2);
    g.strokeRoundRect(panel.x(), panel.y(), panel.w(), panel.h(), 18 * scale, 18 * scale);
    drawTag(g, panel.x() + 6, panel.y() + 16, "NVL Panel");

    Font speakerFont = resolveNamePreviewFont(scale);
    Font textFont = resolveDialoguePreviewFont(scale);
    Color speakerColor = parseColorValue(style.nvlSpeakerTextColor(), Color.web("#F7D89A"));
    Color textColor = parseColorValue(style.nvlTextColor(), Color.web("#E8EDF6"));
    double pad = spec.nvlPadding() * scale;
    double speakerWidth = spec.nvlSpeakerWidth() * scale;
    double gap = 16 * scale;
    double xSpeaker = panel.x() + pad;
    double xBody = xSpeaker + speakerWidth + gap;
    double bodyWidth = Math.max(40 * scale, panel.w() - pad * 2 - speakerWidth - gap);
    double cursorY = panel.y() + pad + speakerFont.getSize();
    int maxEntries = Math.max(1, (int) Math.round(spec.nvlMaxEntries()));
    List<String[]> entries = List.of(
        new String[] {"Narrator", "NVL mode stacks more of the recent conversation on screen."},
        new String[] {previewSpeakerName, previewDialogueText()},
        new String[] {"Guide", "Speaker and body columns stay configurable instead of hardcoded."},
        new String[] {"System", "Use [mode dialogue nvl] to switch at runtime."}
    );
    int count = Math.min(entries.size(), maxEntries);
    for (int i = 0; i < count; i++) {
      String[] entry = entries.get(i);
      g.setFont(speakerFont);
      g.setFill(speakerColor);
      g.fillText(entry[0], xSpeaker, cursorY);
      List<String> lines = wrapPlainText(g, entry[1], textFont, bodyWidth);
      g.setFont(textFont);
      g.setFill(textColor);
      double lineHeight = Math.max(12, textFont.getSize() * 1.35);
      for (int j = 0; j < lines.size(); j++) {
        g.fillText(lines.get(j), xBody, cursorY + j * lineHeight);
      }
      double entryHeight = Math.max(speakerFont.getSize(), lines.size() * lineHeight);
      cursorY += entryHeight + spec.nvlEntryGap() * scale;
      if (cursorY > panel.y() + panel.h() - pad) break;
      g.setStroke(Color.color(1, 1, 1, 0.08));
      g.strokeLine(panel.x() + pad, cursorY - spec.nvlEntryGap() * scale * 0.5, panel.x() + panel.w() - pad, cursorY - spec.nvlEntryGap() * scale * 0.5);
    }
  }

  private void drawBubblePreview(GraphicsContext g, double w, double h, double scale) {
    double groundY = h * 0.8;
    double anchorX = switch (bubblePreviewAnchor()) {
      case "left" -> w * 0.24;
      case "center", "auto" -> w * 0.5;
      case "right" -> w * 0.76;
      default -> w * 0.5;
    };
    drawBubbleActorMarkers(g, w, groundY, anchorX, scale);

    Font speakerFont = resolveNamePreviewFont(scale);
    Font textFont = resolveDialoguePreviewFont(scale);
    double bubbleWidth = Math.max(160 * scale, Math.min(w * spec.bubbleWidthFactor(), w * 0.7));
    double textPadding = spec.bubbleTextPadding() * scale;
    double contentWidth = Math.max(60 * scale, bubbleWidth - textPadding * 2);
    List<String> lines = wrapPlainText(g, previewDialogueText(), textFont, contentWidth);
    double lineHeight = Math.max(12, textFont.getSize() * 1.35);
    double contentHeight = speakerFont.getSize() + 8 * scale + lines.size() * lineHeight;
    double bubbleHeight = Math.max(spec.bubbleMinHeight() * scale, contentHeight + textPadding * 2);
    double bubbleX = switch (bubblePreviewAnchor()) {
      case "left" -> anchorX;
      case "center", "auto" -> anchorX - bubbleWidth / 2.0;
      case "right" -> anchorX - bubbleWidth;
      default -> anchorX - bubbleWidth / 2.0;
    };
    bubbleX = clamp(bubbleX, PREVIEW_PADDING * scale, w - bubbleWidth - PREVIEW_PADDING * scale);
    double bubbleY = groundY - spec.bubbleYOffset() * scale - bubbleHeight;
    Rect bubble = new Rect(bubbleX, bubbleY, bubbleWidth, bubbleHeight);

    Image bubbleImage = loadImageAsset(style.bubbleAssetPath());
    Color bubbleColor = parseColorValue(style.bubbleColor(), Color.web("#152238ee"));
    Color borderColor = parseColorValue(style.bubbleBorderColor(), Color.web("#A9BCD9"));
    double bubbleOpacity = style.bubbleOpacity() == null ? 0.96 : clamp(style.bubbleOpacity(), 0.0, 1.0);
    double radius = style.bubbleCornerRadius() * scale;
    if (bubbleImage != null && bubbleImage.getWidth() > 1 && bubbleImage.getHeight() > 1) {
      g.drawImage(bubbleImage, bubble.x(), bubble.y(), bubble.w(), bubble.h());
      g.setFill(withOpacity(bubbleColor, bubbleOpacity));
      g.fillRoundRect(bubble.x(), bubble.y(), bubble.w(), bubble.h(), radius, radius);
    } else {
      g.setFill(withOpacity(bubbleColor, bubbleOpacity));
      g.fillRoundRect(bubble.x(), bubble.y(), bubble.w(), bubble.h(), radius, radius);
    }
    g.setStroke(borderColor);
    g.setLineWidth(Math.max(1.0, style.bubbleBorderWidth() * scale));
    g.strokeRoundRect(bubble.x(), bubble.y(), bubble.w(), bubble.h(), radius, radius);
    drawBubbleTail(g, bubble, anchorX, groundY - 12 * scale, spec.bubbleTailSize() * scale, bubbleColor, borderColor);

    g.setFont(speakerFont);
    g.setFill(parseColorValue(style.bubbleSpeakerTextColor(), Color.web("#FFD78A")));
    g.fillText(previewSpeakerName, bubble.x() + textPadding, bubble.y() + textPadding + speakerFont.getSize());
    g.setFont(textFont);
    g.setFill(parseColorValue(style.bubbleTextColor(), Color.web("#F1F5FF")));
    for (int i = 0; i < lines.size(); i++) {
      g.fillText(lines.get(i), bubble.x() + textPadding, bubble.y() + textPadding + speakerFont.getSize() + 10 * scale + i * lineHeight);
    }

    g.setStroke(LayoutStudioPalette.ACCENT_BLUE);
    g.setLineWidth(2);
    g.strokeRoundRect(bubble.x(), bubble.y(), bubble.w(), bubble.h(), radius, radius);
    drawTag(g, bubble.x() + 6, bubble.y() - 4, "Bubble");
  }

  private void drawBubbleActorMarkers(GraphicsContext g, double width, double groundY, double activeAnchorX, double scale) {
    double[] anchors = {width * 0.24, width * 0.5, width * 0.76};
    for (double x : anchors) {
      boolean active = Math.abs(x - activeAnchorX) < 1.0;
      g.setStroke(active ? LayoutStudioPalette.ACCENT_GOLD : LayoutStudioPalette.PANEL_BORDER_LIGHT);
      g.setLineWidth(active ? 3 : 2);
      g.strokeOval(x - 20 * scale, groundY - 120 * scale, 40 * scale, 40 * scale);
      g.strokeLine(x, groundY - 80 * scale, x, groundY - 10 * scale);
      g.strokeLine(x - 24 * scale, groundY - 46 * scale, x + 24 * scale, groundY - 46 * scale);
      g.strokeLine(x, groundY - 10 * scale, x - 20 * scale, groundY + 40 * scale);
      g.strokeLine(x, groundY - 10 * scale, x + 20 * scale, groundY + 40 * scale);
    }
  }

  private void drawBubbleTail(GraphicsContext g, Rect bubble, double anchorX, double tipY, double tailSize, Color fill, Color stroke) {
    double baseX = clamp(anchorX, bubble.x() + tailSize * 1.5, bubble.x() + bubble.w() - tailSize * 1.5);
    double baseY = bubble.y() + bubble.h();
    double[] xs = {baseX - tailSize, baseX + tailSize, anchorX};
    double[] ys = {baseY - 1, baseY - 1, tipY};
    g.setFill(withOpacity(fill, 1.0));
    g.fillPolygon(xs, ys, 3);
    g.setStroke(stroke);
    g.setLineWidth(Math.max(1.0, style.bubbleBorderWidth()));
    g.strokePolygon(xs, ys, 3);
  }

  private List<String> wrapPlainText(GraphicsContext g, String text, Font font, double maxWidth) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isBlank()) {
      lines.add("");
      return lines;
    }
    g.setFont(font);
    String[] paragraphs = text.split("\\n");
    for (String paragraph : paragraphs) {
      String[] words = paragraph.split("\\s+");
      StringBuilder current = new StringBuilder();
      for (String word : words) {
        if (word == null || word.isBlank()) continue;
        String candidate = current.isEmpty() ? word : current + " " + word;
        if (!current.isEmpty() && computeTextWidth(g, candidate, font) > maxWidth) {
          lines.add(current.toString());
          current.setLength(0);
          current.append(word);
        } else {
          if (!current.isEmpty()) current.append(' ');
          current.append(word);
        }
      }
      lines.add(current.isEmpty() ? "" : current.toString());
    }
    return lines.isEmpty() ? List.of("") : lines;
  }

  private void drawStyledPreviewText(
      GraphicsContext g,
      List<TextSpan> spans,
      int revealedChars,
      double startX,
      double startY,
      double maxWidth,
      Font baseFont,
      double xAlign
  ) {
    if (g == null || spans == null || baseFont == null) return;
    g.setFont(baseFont);
    double lineHeight = Math.max(12, baseFont.getSize() * 1.35);
    List<PreviewLine> lines = new ArrayList<>();
    List<PreviewGlyph> currentLine = new ArrayList<>();
    double currentLineWidth = 0.0;
    int charCount = 0;
    int glyphIndex = 0;
    long animationTime = System.currentTimeMillis();

    for (TextSpan span : spans) {
      if (span == null) continue;
      String text = span.getText();
      if (text == null || text.isEmpty()) continue;
      int spanLen = text.length();

      int visibleChars = 0;
      if (charCount < revealedChars) {
        visibleChars = Math.min(spanLen, revealedChars - charCount);
      }

      if (visibleChars > 0) {
        String visibleText = text.substring(0, visibleChars);

        if (span.hasColor()) g.setFill(parseColorHex(span.getColorHex()));
        else g.setFill(RUNTIME_TEXT_COLOR);

        Font effectFont = baseFont;
        if (span.getEffect() == TextEffect.BOLD) {
          effectFont = Font.font(baseFont.getFamily(), FontWeight.BOLD, baseFont.getSize());
        } else if (span.getEffect() == TextEffect.ITALIC) {
          effectFont = Font.font(baseFont.getFamily(), FontWeight.NORMAL, baseFont.getSize());
        }
        g.setFont(effectFont);

        for (int i = 0; i < visibleText.length(); i++) {
          char c = visibleText.charAt(i);
          if (c == '\n') {
            lines.add(new PreviewLine(List.copyOf(currentLine), currentLineWidth));
            currentLine.clear();
            currentLineWidth = 0.0;
            continue;
          }
          double charWidth = computeTextWidth(g, String.valueOf(c), effectFont);
          if (!currentLine.isEmpty() && currentLineWidth + charWidth > maxWidth) {
            lines.add(new PreviewLine(List.copyOf(currentLine), currentLineWidth));
            currentLine.clear();
            currentLineWidth = 0.0;
          }
          currentLine.add(new PreviewGlyph(c, effectFont, span.getEffect(), span.hasColor() ? parseColorHex(span.getColorHex()) : RUNTIME_TEXT_COLOR, glyphIndex++, charWidth));
          currentLineWidth += charWidth;
        }
        g.setFont(baseFont);
      }
      charCount += spanLen;
    }

    if (!currentLine.isEmpty() || lines.isEmpty()) {
      lines.add(new PreviewLine(List.copyOf(currentLine), currentLineWidth));
    }

    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      PreviewLine line = lines.get(lineIndex);
      double x = resolveAlignedTextX(startX, maxWidth, line.width(), xAlign);
      double y = startY + lineIndex * lineHeight;
      for (PreviewGlyph glyph : line.glyphs()) {
        g.setFont(glyph.font());
        g.setFill(glyph.color());
        double offsetX = 0;
        double offsetY = 0;
        double effectPhase = (animationTime * 0.01) + glyph.glyphIndex() * 0.3;
        switch (glyph.effect()) {
            case SHAKE -> {
              offsetX = (Math.random() - 0.5) * 3;
              offsetY = (Math.random() - 0.5) * 3;
            }
            case WAVE -> offsetY = Math.sin(effectPhase) * 3;
            case BOUNCE -> offsetY = Math.abs(Math.sin(effectPhase * 2)) * -4;
            case RAINBOW -> {
              double hue = (effectPhase * 50) % 360;
              g.setFill(Color.hsb(hue, 0.8, 1.0));
            }
            default -> {
            }
          }
        g.fillText(String.valueOf(glyph.value()), x + offsetX, y + offsetY);
        x += glyph.width();
      }
    }
  }

  private record PreviewGlyph(char value, Font font, TextEffect effect, Color color, int glyphIndex, double width) {}

  private record PreviewLine(List<PreviewGlyph> glyphs, double width) {}

  private static double resolveAlignedTextX(double contentX, double contentWidth, double textWidth, double xAlign) {
    double clamped = clamp(xAlign, 0.0, 1.0);
    double freeSpace = Math.max(0.0, contentWidth - textWidth);
    return contentX + freeSpace * clamped;
  }

  private Color parseColorHex(String hex) {
    if (hex == null || hex.isEmpty()) return RUNTIME_TEXT_COLOR;
    try {
      String raw = hex.startsWith("#") ? hex.substring(1) : hex;
      if (raw.length() == 6) {
        int r = Integer.parseInt(raw.substring(0, 2), 16);
        int g = Integer.parseInt(raw.substring(2, 4), 16);
        int b = Integer.parseInt(raw.substring(4, 6), 16);
        return Color.rgb(r, g, b);
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return RUNTIME_TEXT_COLOR;
    }
    return RUNTIME_TEXT_COLOR;
  }

  private int previewChoiceCount() {
    return Math.max(1, previewChoiceLabels == null ? 0 : previewChoiceLabels.size());
  }

  private static List<BoundsPointCodec.Point> parseBoundsPoints(String raw) {
    List<BoundsPointCodec.Point> parsed = BoundsPointCodec.parse(raw);
    return parsed != null && parsed.size() >= 3 ? parsed : List.of();
  }

  private static boolean hasPolygon(List<BoundsPointCodec.Point> points) {
    return points != null && points.size() >= 3;
  }

  private static void clipToLocalPolygon(GraphicsContext g, List<BoundsPointCodec.Point> points, Rect rect) {
    if (g == null || rect == null || !hasPolygon(points)) return;
    g.beginPath();
    for (int i = 0; i < points.size(); i++) {
      BoundsPointCodec.Point point = points.get(i);
      double x = rect.x() + rect.w() * point.x();
      double y = rect.y() + rect.h() * point.y();
      if (i == 0) g.moveTo(x, y);
      else g.lineTo(x, y);
    }
    g.closePath();
    g.clip();
  }

  private static void strokeLocalPolygon(GraphicsContext g, List<BoundsPointCodec.Point> points, Rect rect) {
    if (g == null || rect == null || !hasPolygon(points)) return;
    g.beginPath();
    for (int i = 0; i < points.size(); i++) {
      BoundsPointCodec.Point point = points.get(i);
      double x = rect.x() + rect.w() * point.x();
      double y = rect.y() + rect.h() * point.y();
      if (i == 0) g.moveTo(x, y);
      else g.lineTo(x, y);
    }
    g.closePath();
    g.stroke();
  }

  private LayoutRects computeRects(VnUiLayoutSpec s, double w, double h, int choiceCount) {
    return computeRects(s, w, h, choiceCount, 1.0);
  }

  private LayoutRects computeRects(VnUiLayoutSpec s, double w, double h, int choiceCount, double scale) {
    double tbX = clamp(s.textBoxX() * w, 0, w);
    double tbY = clamp(s.textBoxY() * h, 0, h);
    double tbW = clamp(s.textBoxWidth() * w, 1, Math.max(1, w - tbX));
    double tbH = clamp(s.textBoxHeight() * h, 1, Math.max(1, h - tbY));

    double nbX = tbX + s.nameBoxXOffset() * scale;
    double nbY = tbY + s.nameBoxYOffset() * scale;
    double nbW = s.nameBoxWidth() * scale;
    double nbH = s.nameBoxHeight() * scale;

    double leftPad = s.dialogueTextHorizontalPadding() * scale;
    double topPad = s.dialogueTextTopPadding() * scale;
    double rightPad = s.dialogueTextRightPadding() * scale;
    double bottomPad = s.dialogueTextBottomPadding() * scale;
    double textX = tbX + leftPad;
    double textY = tbY + topPad;
    double textW = Math.max(60 * scale, tbW - leftPad - rightPad);
    double textH = Math.max(20 * scale, tbH - topPad - bottomPad);

    double choiceW = clamp(w * s.choiceWidthFactor(), 20 * scale, w);
    double choiceX = clamp(w * s.choiceXCenter() - choiceW / 2, 0, Math.max(0, w - choiceW));
    int count = Math.max(1, choiceCount);
    double choiceH = Math.max(12 * scale, s.choiceHeight() * scale);
    double choiceGap = Math.max(0, s.choiceGap() * scale);
    double totalChoiceH = count * choiceH + Math.max(0, count - 1) * choiceGap;
    double choiceStartY = resolveChoiceYStart(s, h, count, scale);
    choiceStartY = clamp(choiceStartY, 0, Math.max(0, h - totalChoiceH));

    return new LayoutRects(
        new Rect(tbX, tbY, tbW, tbH),
        new Rect(nbX, nbY, nbW, nbH),
        new Rect(textX, textY, textW, textH),
        new Rect(choiceX, choiceStartY, choiceW, totalChoiceH)
    );
  }

  private TextBoxGeometry computeTextBoxGeometry(VnUiLayoutSpec s, double w, double h) {
    double tbX = clamp(s.textBoxX() * w, 0, w);
    double tbY = clamp(s.textBoxY() * h, 0, h);
    double tbW = clamp(s.textBoxWidth() * w, 1, Math.max(1, w - tbX));
    double tbH = clamp(s.textBoxHeight() * h, 1, Math.max(1, h - tbY));
    return new TextBoxGeometry(tbX, tbY, tbW, tbH);
  }

  private Rect computeTextBoxButtonRect(VnUiActionButtonSpec button, Rect textBoxRect, double viewportWidth, double viewportHeight) {
    if (button == null || textBoxRect == null) return new Rect(0, 0, 1, 1);
    double baseX = button.viewportSpace() ? 0.0 : textBoxRect.x();
    double baseY = button.viewportSpace() ? 0.0 : textBoxRect.y();
    double baseW = button.viewportSpace() ? Math.max(1.0, viewportWidth) : textBoxRect.w();
    double baseH = button.viewportSpace() ? Math.max(1.0, viewportHeight) : textBoxRect.h();
    double x = baseX + baseW * button.x();
    double y = baseY + baseH * button.y();
    double width = Math.max(8, baseW * button.width());
    double height = Math.max(8, baseH * button.height());
    return new Rect(x, y, width, height);
  }

  private int hitTestButtonIndex(double x, double y, Rect textBoxRect, double viewportWidth, double viewportHeight) {
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return -1;
    for (int i = textBoxButtons.size() - 1; i >= 0; i--) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null || !button.enabled()) continue;
      Rect rect = computeTextBoxButtonRect(button, textBoxRect, viewportWidth, viewportHeight);
      List<BoundsPointCodec.Point> points = parseBoundsPoints(button.boundsPoints());
      if (hasPolygon(points)) {
        if (BoundsPointCodec.containsInRect(points, rect.x(), rect.y(), rect.w(), rect.h(), x, y)) return i;
      } else if (rect.contains(x, y)) {
        return i;
      }
    }
    return -1;
  }

  private int hitTestButtonResizeIndex(double x, double y, Rect textBoxRect, double viewportWidth, double viewportHeight) {
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return -1;
    for (int i = textBoxButtons.size() - 1; i >= 0; i--) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null || !button.enabled()) continue;
      Rect rect = computeTextBoxButtonRect(button, textBoxRect, viewportWidth, viewportHeight);
      if (isNearCorner(x, y, rect)) return i;
    }
    return -1;
  }

  private double resolveChoiceYStart(VnUiLayoutSpec s, double h, int count) {
    return resolveChoiceYStart(s, h, count, 1.0);
  }

  private double resolveChoiceYStart(VnUiLayoutSpec s, double h, int count, double scale) {
    double choiceH = s.choiceHeight() * scale;
    double choiceGap = s.choiceGap() * scale;
    double total = count * choiceH + Math.max(0, count - 1) * choiceGap;
    if (s.choiceYStart() < 0) return (h - total) / 2.0;
    return h * s.choiceYStart();
  }

  private VnUiLayoutSpec readSpecFromControls() {
    VnUiLayoutSpec base = spec == null ? VnUiLayoutSpec.defaults() : spec;
    return new VnUiLayoutSpec(
        value(spTextBoxX),
        value(spTextBoxY),
        value(spTextBoxWidth),
        value(spTextBoxHeight),
        value(spTextBoxPadding),
        value(spNameBoxXOffset),
        value(spNameBoxYOffset),
        value(spNameBoxWidth),
        value(spNameBoxHeight),
        value(spNameTextXOffset),
        value(spNameTextBaselineOffset),
        value(spNameTextTopPadding),
        value(spNameTextBottomPadding),
        value(spNameTextYAlign),
        value(spDialoguePaddingX),
        value(spDialoguePaddingTop),
        value(spDialoguePaddingRight),
        value(spDialoguePaddingBottom),
        value(spChoiceXCenter),
        value(spChoiceYStart),
        value(spChoiceWidthFactor),
        value(spChoiceHeight),
        value(spChoiceGap),
        value(spChoiceTextXPadding),
        value(spChoiceTextTopPadding),
        value(spChoiceTextBottomPadding),
        value(spChoiceTextYAlign),
        cbNameBoxAutoWidth.isSelected(),
        value(spNvlX),
        value(spNvlY),
        value(spNvlWidth),
        value(spNvlHeight),
        value(spNvlPadding),
        value(spNvlSpeakerWidth),
        value(spNvlEntryGap),
        (int) Math.round(value(spNvlMaxEntries)),
        value(spBubbleWidthFactor),
        value(spBubbleMinHeight),
        value(spBubbleTextPadding),
        value(spBubbleYOffset),
        value(spBubbleTailSize)
    );
  }

  private void applySpecToControls(VnUiLayoutSpec s) {
    setValue(spTextBoxX, s.textBoxX());
    setValue(spTextBoxY, s.textBoxY());
    setValue(spTextBoxWidth, s.textBoxWidth());
    setValue(spTextBoxHeight, s.textBoxHeight());
    setValue(spTextBoxPadding, s.textBoxPadding());
    setValue(spNameBoxXOffset, s.nameBoxXOffset());
    setValue(spNameBoxYOffset, s.nameBoxYOffset());
    setValue(spNameBoxWidth, s.nameBoxWidth());
    setValue(spNameBoxHeight, s.nameBoxHeight());
    setValue(spNameTextXOffset, s.nameTextXOffset());
    setValue(spNameTextBaselineOffset, s.nameTextBaselineOffset());
    setValue(spNameTextTopPadding, s.nameTextTopPadding());
    setValue(spNameTextBottomPadding, s.nameTextBottomPadding());
    setValue(spNameTextYAlign, s.nameTextYAlign());
    setValue(spDialoguePaddingX, s.dialogueTextHorizontalPadding());
    setValue(spDialoguePaddingTop, s.dialogueTextTopPadding());
    setValue(spDialoguePaddingRight, s.dialogueTextRightPadding());
    setValue(spDialoguePaddingBottom, s.dialogueTextBottomPadding());
    setValue(spChoiceXCenter, s.choiceXCenter());
    setValue(spChoiceYStart, s.choiceYStart());
    setValue(spChoiceWidthFactor, s.choiceWidthFactor());
    setValue(spChoiceHeight, s.choiceHeight());
    setValue(spChoiceGap, s.choiceGap());
    setValue(spChoiceTextXPadding, s.choiceTextXPadding());
    setValue(spChoiceTextTopPadding, s.choiceTextTopPadding());
    setValue(spChoiceTextBottomPadding, s.choiceTextBottomPadding());
    setValue(spChoiceTextYAlign, s.choiceTextYAlign());
    cbNameBoxAutoWidth.setSelected(s.nameBoxAutoWidth());
    setValue(spNvlX, s.nvlX());
    setValue(spNvlY, s.nvlY());
    setValue(spNvlWidth, s.nvlWidth());
    setValue(spNvlHeight, s.nvlHeight());
    setValue(spNvlPadding, s.nvlPadding());
    setValue(spNvlSpeakerWidth, s.nvlSpeakerWidth());
    setValue(spNvlEntryGap, s.nvlEntryGap());
    setValue(spNvlMaxEntries, s.nvlMaxEntries());
    setValue(spBubbleWidthFactor, s.bubbleWidthFactor());
    setValue(spBubbleMinHeight, s.bubbleMinHeight());
    setValue(spBubbleTextPadding, s.bubbleTextPadding());
    setValue(spBubbleYOffset, s.bubbleYOffset());
    setValue(spBubbleTailSize, s.bubbleTailSize());
  }

  private VnUiStyleSpec readStyleFromControls() {
    VnUiStyleSpec base = style == null ? VnUiStyleSpec.defaults() : style;
    return new VnUiStyleSpec(
        normalizeAssetPath(tfTextBoxAsset.getText()),
        base.textBoxNarrationAssetPath(),
        normalizeColorValue(tfTextBoxColor.getText()),
        chkTextBoxOverlayEnabled.isSelected() ? value(spTextBoxOverlayOpacity) : 0.0,
        base.textBoxBoundsPoints(),
        base.nameBoxAssetPath(),
        base.nameBoxColor(),
        base.nameTextColor(),
        base.nameTextFontFamily(),
        base.nameTextFontSize(),
        normalizeFontWeight(cbNameTextFontWeight.getValue()),
        value(spNameTextXAlign),
        base.nameBoxBoundsPoints(),
        value(spNameBoxOpacity),
        base.dialogueTextColor(),
        base.dialogueTextFontFamily(),
        base.dialogueTextFontSize(),
        normalizeFontWeight(cbDialogueTextFontWeight.getValue()),
        value(spDialogueTextXAlign),
        base.dialogueTextBoundsPoints(),
        normalizeAssetPath(tfChoiceButtonAsset.getText()),
        normalizeAssetPath(tfChoiceButtonHoverAsset.getText()),
        normalizeAssetPath(tfChoiceButtonSelectedAsset.getText()),
        normalizeAssetPath(tfChoiceButtonDisabledAsset.getText()),
        base.choiceButtonBoundsPoints(),
        normalizeColorValue(tfChoiceBgColor.getText()),
        normalizeColorValue(tfChoiceHoverColor.getText()),
        normalizeColorValue(tfChoiceSelectedColor.getText()),
        normalizeColorValue(tfChoiceDisabledColor.getText()),
        normalizeColorValue(tfChoiceTextColor.getText()),
        normalizeColorValue(tfChoiceHoverTextColor.getText()),
        normalizeColorValue(tfChoiceSelectedTextColor.getText()),
        normalizeColorValue(tfChoiceDisabledTextColor.getText()),
        normalizeColorValue(tfChoiceBorderColor.getText()),
        normalizeColorValue(tfChoiceHoverBorderColor.getText()),
        normalizeColorValue(tfChoiceSelectedBorderColor.getText()),
        normalizeColorValue(tfChoiceDisabledBorderColor.getText()),
        value(spChoiceCornerRadius),
        value(spChoiceBorderWidth),
        value(spChoiceTextBaselineOffset),
        value(spChoiceTextXAlign),
        base.choiceFontFamily(),
        base.choiceFontSize(),
        normalizeFontWeight(cbChoiceFontWeight.getValue()),
        base.characterHeightFactor(),
        base.characterBaselineY(),
        normalizeAssetPath(tfNvlPanelAsset.getText()),
        normalizeColorValue(tfNvlPanelColor.getText()),
        value(spNvlPanelOpacity),
        normalizeColorValue(tfNvlSpeakerTextColor.getText()),
        normalizeColorValue(tfNvlTextColor.getText()),
        normalizeAssetPath(tfBubbleAsset.getText()),
        normalizeColorValue(tfBubbleColor.getText()),
        value(spBubbleOpacity),
        normalizeColorValue(tfBubbleBorderColor.getText()),
        normalizeColorValue(tfBubbleSpeakerTextColor.getText()),
        normalizeColorValue(tfBubbleTextColor.getText()),
        value(spBubbleCornerRadius),
        value(spBubbleBorderWidth)
    );
  }

  private void applyStyleToControls(VnUiStyleSpec s) {
    tfTextBoxAsset.setText(normalizeAssetPath(s.textBoxAssetPath()));
    tfTextBoxColor.setText(normalizeColorValue(s.textBoxColor()));
    double overlayOpacity = s.textBoxOpacity() == null ? 0.28 : clamp(s.textBoxOpacity(), 0.0, 1.0);
    setValue(spTextBoxOverlayOpacity, overlayOpacity);
    chkTextBoxOverlayEnabled.setSelected(overlayOpacity > 0.001);
    tfChoiceButtonAsset.setText(normalizeAssetPath(s.choiceButtonAssetPath()));
    tfChoiceButtonHoverAsset.setText(normalizeAssetPath(s.choiceButtonHoverAssetPath()));
    tfChoiceButtonSelectedAsset.setText(normalizeAssetPath(s.choiceButtonSelectedAssetPath()));
    tfChoiceButtonDisabledAsset.setText(normalizeAssetPath(s.choiceButtonDisabledAssetPath()));
    tfNvlPanelAsset.setText(normalizeAssetPath(s.nvlPanelAssetPath()));
    tfNvlPanelColor.setText(normalizeColorValue(s.nvlPanelColor()));
    setValue(spNvlPanelOpacity, s.nvlPanelOpacity() == null ? 0.84 : clamp(s.nvlPanelOpacity(), 0.0, 1.0));
    tfNvlSpeakerTextColor.setText(normalizeColorValue(s.nvlSpeakerTextColor()));
    tfNvlTextColor.setText(normalizeColorValue(s.nvlTextColor()));
    tfBubbleAsset.setText(normalizeAssetPath(s.bubbleAssetPath()));
    tfBubbleColor.setText(normalizeColorValue(s.bubbleColor()));
    setValue(spBubbleOpacity, s.bubbleOpacity() == null ? 0.96 : clamp(s.bubbleOpacity(), 0.0, 1.0));
    tfBubbleBorderColor.setText(normalizeColorValue(s.bubbleBorderColor()));
    tfBubbleSpeakerTextColor.setText(normalizeColorValue(s.bubbleSpeakerTextColor()));
    tfBubbleTextColor.setText(normalizeColorValue(s.bubbleTextColor()));

    tfChoiceBgColor.setText(normalizeColorValue(s.choiceBackgroundColor()));
    tfChoiceHoverColor.setText(normalizeColorValue(s.choiceHoverColor()));
    tfChoiceSelectedColor.setText(normalizeColorValue(s.choiceSelectedColor()));
    tfChoiceDisabledColor.setText(normalizeColorValue(s.choiceDisabledColor()));
    tfChoiceTextColor.setText(normalizeColorValue(s.choiceTextColor()));
    tfChoiceHoverTextColor.setText(normalizeColorValue(s.choiceHoverTextColor()));
    tfChoiceSelectedTextColor.setText(normalizeColorValue(s.choiceSelectedTextColor()));
    tfChoiceDisabledTextColor.setText(normalizeColorValue(s.choiceDisabledTextColor()));
    tfChoiceBorderColor.setText(normalizeColorValue(s.choiceBorderColor()));
    tfChoiceHoverBorderColor.setText(normalizeColorValue(s.choiceHoverBorderColor()));
    tfChoiceSelectedBorderColor.setText(normalizeColorValue(s.choiceSelectedBorderColor()));
    tfChoiceDisabledBorderColor.setText(normalizeColorValue(s.choiceDisabledBorderColor()));

    setValue(spChoiceCornerRadius, s.choiceCornerRadius());
    setValue(spChoiceBorderWidth, s.choiceBorderWidth());
    setValue(spChoiceTextBaselineOffset, s.choiceTextBaselineOffset());
    setValue(spBubbleCornerRadius, s.bubbleCornerRadius());
    setValue(spBubbleBorderWidth, s.bubbleBorderWidth());
    setValue(spNameTextXAlign, s.nameTextXAlign() == null ? 0.0 : s.nameTextXAlign());
    setValue(spDialogueTextXAlign, s.dialogueTextXAlign() == null ? 0.0 : s.dialogueTextXAlign());
    setValue(spChoiceTextXAlign, s.choiceTextXAlign() == null ? 0.0 : s.choiceTextXAlign());

    cbNameTextFontWeight.setValue(s.nameTextFontWeight() != null ? s.nameTextFontWeight() : "BOLD");
    double nameBoxOpacity = s.nameBoxOpacity() == null ? 1.0 : clamp(s.nameBoxOpacity(), 0.0, 1.0);
    setValue(spNameBoxOpacity, nameBoxOpacity);
    cbDialogueTextFontWeight.setValue(s.dialogueTextFontWeight() != null ? s.dialogueTextFontWeight() : "NORMAL");
    cbChoiceFontWeight.setValue(s.choiceFontWeight() != null ? s.choiceFontWeight() : "NORMAL");
  }

  private void addTextBoxButton() {
    if (suppressEvents) return;
    String id = nextButtonId();
    VnUiActionButtonSpec created = new VnUiActionButtonSpec(
        id,
        titleizeId(id),
        "noop",
        null,
        true,
        "",
        "",
        "",
        null,
        0.78,
        0.08,
        0.12,
        0.25
    );
    textBoxButtons.add(created);
    refreshTextBoxButtonList();
    setSelectedTextBoxButton(textBoxButtons.size() - 1);
    refreshDiagnosticsFromUiState();
    validateState();
    redraw();
    emitText();
  }

  private void duplicateSelectedTextBoxButton() {
    if (suppressEvents) return;
    if (selectedButtonIndex < 0 || selectedButtonIndex >= textBoxButtons.size()) return;
    VnUiActionButtonSpec source = textBoxButtons.get(selectedButtonIndex);
    if (source == null) return;
    String newId = nextButtonId();
    VnUiActionButtonSpec duplicate = new VnUiActionButtonSpec(
        newId,
        source.label(),
        source.action(),
        source.target(),
        source.enabled(),
        source.assetPath(),
        source.hoverAssetPath(),
        source.disabledAssetPath(),
        source.boundsPoints(),
        clamp01(source.x() + 0.02),
        clamp01(source.y() + 0.02),
        source.width(),
        source.height(),
        source.coordinateSpace()
    );
    int insertIndex = Math.min(selectedButtonIndex + 1, textBoxButtons.size());
    textBoxButtons.add(insertIndex, duplicate);
    refreshTextBoxButtonList();
    setSelectedTextBoxButton(insertIndex);
    refreshDiagnosticsFromUiState();
    validateState();
    redraw();
    emitText();
  }

  private void removeSelectedTextBoxButton() {
    if (suppressEvents) return;
    if (selectedButtonIndex < 0 || selectedButtonIndex >= textBoxButtons.size()) return;
    textBoxButtons.remove(selectedButtonIndex);
    refreshTextBoxButtonList();
    if (textBoxButtons.isEmpty()) {
      setSelectedTextBoxButton(-1);
    } else {
      setSelectedTextBoxButton(Math.min(selectedButtonIndex, textBoxButtons.size() - 1));
    }
    refreshDiagnosticsFromUiState();
    validateState();
    redraw();
    emitText();
  }

  private void moveSelectedTextBoxButton(int delta) {
    if (suppressEvents) return;
    if (selectedButtonIndex < 0 || selectedButtonIndex >= textBoxButtons.size()) return;
    int next = selectedButtonIndex + delta;
    if (next < 0 || next >= textBoxButtons.size()) return;
    VnUiActionButtonSpec current = textBoxButtons.remove(selectedButtonIndex);
    textBoxButtons.add(next, current);
    refreshTextBoxButtonList();
    setSelectedTextBoxButton(next);
    refreshDiagnosticsFromUiState();
    validateState();
    redraw();
    emitText();
  }

  private void openTextBoxBoundsStudio() {
    ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
    openStyleBoundsStudio(
        "Textbox Bounds Studio",
        "textbox",
        "Textbox",
        style.textBoxBoundsPoints(),
        textBoxAssetImage,
        computeTextBoxWorkspaceAspect(viewport),
        encodedPoints -> style = withBoundsPoints(
            encodedPoints,
            style.nameBoxBoundsPoints(),
            style.dialogueTextBoundsPoints(),
            style.choiceButtonBoundsPoints()
        )
    );
  }

  private void openNameBoxBoundsStudio() {
    ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
    Image nameBoxAsset = loadImageAsset(style.nameBoxAssetPath());
    openStyleBoundsStudio(
        "Name Box Bounds Studio",
        "namebox",
        "Name Box",
        style.nameBoxBoundsPoints(),
        firstNonNull(nameBoxAsset, textBoxAssetImage),
        computeNameBoxWorkspaceAspect(viewport),
        encodedPoints -> style = withBoundsPoints(
            style.textBoxBoundsPoints(),
            encodedPoints,
            style.dialogueTextBoundsPoints(),
            style.choiceButtonBoundsPoints()
        )
    );
  }

  private void openDialogueTextBoundsStudio() {
    ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
    openStyleBoundsStudio(
        "Dialogue Text Bounds Studio",
        "dialogue_text",
        "Dialogue Text",
        style.dialogueTextBoundsPoints(),
        textBoxAssetImage,
        computeDialogueTextWorkspaceAspect(viewport),
        encodedPoints -> style = withBoundsPoints(
            style.textBoxBoundsPoints(),
            style.nameBoxBoundsPoints(),
            encodedPoints,
            style.choiceButtonBoundsPoints()
        )
    );
  }

  private void openChoiceButtonBoundsStudio() {
    ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
    openStyleBoundsStudio(
        "Choice Button Bounds Studio",
        "choice_button",
        "Choice Button",
        style.choiceButtonBoundsPoints(),
        firstNonNull(choiceButtonAssetImage, choiceButtonHoverAssetImage),
        computeChoiceButtonWorkspaceAspect(viewport),
        encodedPoints -> style = withBoundsPoints(
            style.textBoxBoundsPoints(),
            style.nameBoxBoundsPoints(),
            style.dialogueTextBoundsPoints(),
            encodedPoints
        )
    );
  }

  private void openStyleBoundsStudio(
      String title,
      String id,
      String label,
      String boundsPoints,
      Image background,
      double workspaceAspect,
      Consumer<String> onApply
  ) {
    BoundsDrawingTool tool = new BoundsDrawingTool();
    tool.setWorkspaceAspect(workspaceAspect);
    if (background != null && background.getWidth() > 1) tool.setBackgroundImage(background);

    tool.setBounds(List.of(decodeStyleBoundsEntry(id, label, boundsPoints)));

    javafx.scene.Scene dialogScene = new javafx.scene.Scene(tool, 960, 620);
    EditorTheme.apply(dialogScene);
    javafx.stage.Stage dialog = new javafx.stage.Stage();
    dialog.setTitle(title);
    dialog.setScene(dialogScene);
    dialog.initOwner(getScene() != null ? getScene().getWindow() : null);
    dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);

    Runnable applyBounds = () -> {
      List<BoundsDrawingTool.BoundEntry> result = tool.getBounds();
      BoundsDrawingTool.BoundEntry selected = null;
      for (BoundsDrawingTool.BoundEntry candidate : result) {
        if (candidate == null) continue;
        if (id.equals(candidate.getId())) {
          selected = candidate;
          break;
        }
        if (selected == null) selected = candidate;
      }
      if (selected != null && (selected.getW() < 0.005 || selected.getH() < 0.005)) {
        selected = null;
      }
      String encoded = selected == null ? null : encodeStyleBounds(selected);
      suppressEvents = true;
      try {
        if (onApply != null) onApply.accept(encoded);
        applyStyleToControls(style);
      } finally {
        suppressEvents = false;
      }
      loadPreviewAssets();
      refreshDiagnosticsFromUiState();
      validateState();
      redraw();
      emitText();
    };
    tool.setOnSaveRequested(applyBounds);
    dialog.setOnHidden(ev -> applyBounds.run());

    if (isLinux()) {
      dialog.setIconified(false);
      dialog.setMaximized(true);
    }
    dialog.show();
  }

  private static double safeAspect(double candidate, double fallback) {
    if (Double.isFinite(candidate) && candidate > 0.0) return candidate;
    if (Double.isFinite(fallback) && fallback > 0.0) return fallback;
    return 16.0 / 9.0;
  }

  private double computeTextBoxWorkspaceAspect(ProjectViewportSpec.Dimensions viewport) {
    double vw = Math.max(1.0, viewport.width());
    double vh = Math.max(1.0, viewport.height());
    double tbW = Math.max(1.0, spec.textBoxWidth() * vw);
    double tbH = Math.max(1.0, spec.textBoxHeight() * vh);
    return safeAspect(tbW / tbH, viewport.aspect());
  }

  private double computeNameBoxWorkspaceAspect(ProjectViewportSpec.Dimensions viewport) {
    double nbW = Math.max(1.0, spec.nameBoxWidth());
    double nbH = Math.max(1.0, spec.nameBoxHeight());
    return safeAspect(nbW / nbH, viewport.aspect());
  }

  private double computeDialogueTextWorkspaceAspect(ProjectViewportSpec.Dimensions viewport) {
    double vw = Math.max(1.0, viewport.width());
    double vh = Math.max(1.0, viewport.height());
    double tbW = Math.max(1.0, spec.textBoxWidth() * vw);
    double tbH = Math.max(1.0, spec.textBoxHeight() * vh);
    double textW = Math.max(40.0, tbW - spec.dialogueTextHorizontalPadding() - spec.dialogueTextRightPadding());
    double textH = Math.max(20.0, tbH - spec.dialogueTextTopPadding() - spec.dialogueTextBottomPadding());
    return safeAspect(textW / textH, computeTextBoxWorkspaceAspect(viewport));
  }

  private double computeChoiceButtonWorkspaceAspect(ProjectViewportSpec.Dimensions viewport) {
    double vw = Math.max(1.0, viewport.width());
    double choiceW = clamp(vw * spec.choiceWidthFactor(), 20.0, vw);
    double choiceH = Math.max(8.0, spec.choiceHeight());
    return safeAspect(choiceW / choiceH, viewport.aspect());
  }

  private String encodeStyleBounds(BoundsDrawingTool.BoundEntry entry) {
    if (entry == null) return null;

    double x = clamp01(entry.getX());
    double y = clamp01(entry.getY());
    double w = clamp(entry.getW(), 0.0, Math.max(0.0, 1.0 - x));
    double h = clamp(entry.getH(), 0.0, Math.max(0.0, 1.0 - y));
    if (w < 0.005 || h < 0.005) return null;

    List<BoundsPointCodec.Point> source = entry.getLocalPoints();
    List<BoundsPointCodec.Point> absolute = new ArrayList<>();
    if (source != null && source.size() >= 3) {
      for (BoundsPointCodec.Point point : source) {
        if (point == null) continue;
        double px = x + clamp01(point.x()) * w;
        double py = y + clamp01(point.y()) * h;
        absolute.add(new BoundsPointCodec.Point(clamp01(px), clamp01(py)));
      }
    } else {
      absolute.add(new BoundsPointCodec.Point(x, y));
      absolute.add(new BoundsPointCodec.Point(clamp01(x + w), y));
      absolute.add(new BoundsPointCodec.Point(clamp01(x + w), clamp01(y + h)));
      absolute.add(new BoundsPointCodec.Point(x, clamp01(y + h)));
    }
    if (absolute.size() < 3) return null;
    String encoded = BoundsPointCodec.encode(absolute);
    return encoded == null || encoded.isBlank() ? null : encoded;
  }

  private static BoundsDrawingTool.BoundEntry decodeStyleBoundsEntry(String id, String label, String rawPoints) {
    List<BoundsPointCodec.Point> parsed = parseBoundsPoints(rawPoints);
    if (!hasPolygon(parsed)) {
      return new BoundsDrawingTool.BoundEntry(id, label, 0.0, 0.0, 1.0, 1.0, List.of());
    }

    List<BoundsPointCodec.Point> clamped = new ArrayList<>(parsed.size());
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (BoundsPointCodec.Point point : parsed) {
      if (point == null) continue;
      double px = clamp01(point.x());
      double py = clamp01(point.y());
      clamped.add(new BoundsPointCodec.Point(px, py));
      minX = Math.min(minX, px);
      minY = Math.min(minY, py);
      maxX = Math.max(maxX, px);
      maxY = Math.max(maxY, py);
    }
    if (!hasPolygon(clamped)) {
      return new BoundsDrawingTool.BoundEntry(id, label, 0.0, 0.0, 1.0, 1.0, List.of());
    }

    double x = clamp01(minX);
    double y = clamp01(minY);
    double w = clamp(maxX - minX, 0.0, 1.0 - x);
    double h = clamp(maxY - minY, 0.0, 1.0 - y);
    if (w < 0.005 || h < 0.005) {
      return new BoundsDrawingTool.BoundEntry(id, label, 0.0, 0.0, 1.0, 1.0, clamped);
    }

    List<BoundsPointCodec.Point> local = new ArrayList<>(clamped.size());
    for (BoundsPointCodec.Point point : clamped) {
      double lx = clamp01((point.x() - x) / w);
      double ly = clamp01((point.y() - y) / h);
      local.add(new BoundsPointCodec.Point(lx, ly));
    }
    return new BoundsDrawingTool.BoundEntry(id, label, x, y, w, h, local);
  }

  private VnUiStyleSpec withBoundsPoints(
      String textBoxBoundsPoints,
      String nameBoxBoundsPoints,
      String dialogueTextBoundsPoints,
      String choiceButtonBoundsPoints
  ) {
    VnUiStyleSpec base = style == null ? VnUiStyleSpec.defaults() : style;
    return new VnUiStyleSpec(
        base.textBoxAssetPath(),
        base.textBoxNarrationAssetPath(),
        base.textBoxColor(),
        base.textBoxOpacity(),
        textBoxBoundsPoints,
        base.nameBoxAssetPath(),
        base.nameBoxColor(),
        base.nameTextColor(),
        base.nameTextFontFamily(),
        base.nameTextFontSize(),
        base.nameTextFontWeight(),
        base.nameTextXAlign(),
        nameBoxBoundsPoints,
        base.nameBoxOpacity(),
        base.dialogueTextColor(),
        base.dialogueTextFontFamily(),
        base.dialogueTextFontSize(),
        base.dialogueTextFontWeight(),
        base.dialogueTextXAlign(),
        dialogueTextBoundsPoints,
        base.choiceButtonAssetPath(),
        base.choiceButtonHoverAssetPath(),
        base.choiceButtonSelectedAssetPath(),
        base.choiceButtonDisabledAssetPath(),
        choiceButtonBoundsPoints,
        base.choiceBackgroundColor(),
        base.choiceHoverColor(),
        base.choiceSelectedColor(),
        base.choiceDisabledColor(),
        base.choiceTextColor(),
        base.choiceHoverTextColor(),
        base.choiceSelectedTextColor(),
        base.choiceDisabledTextColor(),
        base.choiceBorderColor(),
        base.choiceHoverBorderColor(),
        base.choiceSelectedBorderColor(),
        base.choiceDisabledBorderColor(),
        base.choiceCornerRadius(),
        base.choiceBorderWidth(),
        base.choiceTextBaselineOffset(),
        base.choiceTextXAlign(),
        base.choiceFontFamily(),
        base.choiceFontSize(),
        base.choiceFontWeight(),
        base.characterHeightFactor(),
        base.characterBaselineY()
    );
  }

  private void openTextBoxButtonBoundsStudio() {
    BoundsDrawingTool tool = new BoundsDrawingTool();
    ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
    tool.setWorkspaceAspect(computeTextBoxWorkspaceAspect(viewport));

    // Use textbox asset as background if available
    if (textBoxAssetImage != null && textBoxAssetImage.getWidth() > 1) {
      tool.setBackgroundImage(textBoxAssetImage);
    }

    // Pre-populate with existing textbox button bounds (relative to textbox)
    List<BoundsDrawingTool.BoundEntry> entries = new ArrayList<>();
    for (VnUiActionButtonSpec btn : textBoxButtons) {
      entries.add(new BoundsDrawingTool.BoundEntry(
          btn.id(),
          btn.label(),
          btn.x(),
          btn.y(),
          btn.width(),
          btn.height(),
          BoundsPointCodec.parse(btn.boundsPoints())
      ));
    }
    tool.setBounds(entries);

    javafx.scene.Scene dialogScene = new javafx.scene.Scene(tool, 960, 620);
    EditorTheme.apply(dialogScene);
    javafx.stage.Stage dialog = new javafx.stage.Stage();
    dialog.setTitle("Textbox Button Bounds Studio");
    dialog.setScene(dialogScene);
    dialog.initOwner(getScene() != null ? getScene().getWindow() : null);
    dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);

    Runnable applyBounds = () -> {
      List<BoundsDrawingTool.BoundEntry> result = tool.getBounds();
      // Rebuild textbox buttons list from drawn bounds
      Map<String, BoundsDrawingTool.BoundEntry> byId = new java.util.LinkedHashMap<>();
      for (BoundsDrawingTool.BoundEntry be : result) byId.put(be.getId(), be);

      suppressEvents = true;
      // Update existing buttons
      for (int i = 0; i < textBoxButtons.size(); i++) {
        VnUiActionButtonSpec existing = textBoxButtons.get(i);
        BoundsDrawingTool.BoundEntry match = byId.remove(existing.id());
        if (match != null && match.getW() > 0.005 && match.getH() > 0.005) {
          String boundsPoints = match.getLocalPoints() != null && match.getLocalPoints().size() >= 3
              ? BoundsPointCodec.encode(match.getLocalPoints())
              : null;
          textBoxButtons.set(i, new VnUiActionButtonSpec(
              existing.id(), existing.label(), existing.action(), existing.target(),
              existing.enabled(), existing.assetPath(), existing.hoverAssetPath(),
              existing.disabledAssetPath(), boundsPoints,
              match.getX(), match.getY(), match.getW(), match.getH(),
              existing.coordinateSpace()
          ));
        }
      }
      // Add new entries from bounds studio
      for (BoundsDrawingTool.BoundEntry extra : byId.values()) {
        if (extra.getW() < 0.005 || extra.getH() < 0.005) continue;
        String boundsPoints = extra.getLocalPoints() != null && extra.getLocalPoints().size() >= 3
            ? BoundsPointCodec.encode(extra.getLocalPoints())
            : null;
        textBoxButtons.add(new VnUiActionButtonSpec(
            extra.getId(),
            extra.getLabel() != null ? extra.getLabel() : titleizeId(extra.getId()),
            "noop", null, true, "", "", "", boundsPoints,
            extra.getX(), extra.getY(), extra.getW(), extra.getH()
        ));
      }
      suppressEvents = false;
      refreshTextBoxButtonList();
      setSelectedTextBoxButton(textBoxButtons.isEmpty() ? -1 : 0);
      refreshDiagnosticsFromUiState();
      validateState();
      redraw();
      emitText();
    };
    tool.setOnSaveRequested(applyBounds);
    dialog.setOnHidden(ev -> applyBounds.run());

    if (isLinux()) {
      dialog.setIconified(false);
      dialog.setMaximized(true);
    }
    dialog.show();
  }

  private void openRuntimeScriptPreview() {
    if (runtimePreviewView == null) {
      runtimePreviewView = new VnPreviewView();
    }

    runtimePreviewView.setProjectRoot(projectRoot);
    runtimePreviewView.setUiOverrides(spec, style, textBoxButtons);
    runtimePreviewView.runScenario(buildRuntimePreviewScenario(), null);

    if (runtimePreviewStage == null) {
      StackPane host = new StackPane(runtimePreviewView);
      host.setStyle("-fx-background-color: #05070c;");
      javafx.scene.Scene scene = new javafx.scene.Scene(host, 980, 620);
      EditorTheme.apply(scene);

      Stage stage = new Stage();
      stage.setTitle("Dialogue Runtime Preview");
      stage.setScene(scene);
      stage.initOwner(getScene() != null ? getScene().getWindow() : null);
      stage.setOnHidden(ev -> disposeRuntimePreviewWindow());
      scene.widthProperty().addListener((o, ov, nv) -> updateRuntimePreviewSize(scene));
      scene.heightProperty().addListener((o, ov, nv) -> updateRuntimePreviewSize(scene));
      runtimePreviewStage = stage;
    }

    updateRuntimePreviewSize(runtimePreviewStage.getScene());
    if (!runtimePreviewStage.isShowing()) runtimePreviewStage.show();
    runtimePreviewStage.toFront();
    runtimePreviewView.requestFocus();
    startRuntimePreviewTimer();
  }

  private VnScenario buildRuntimePreviewScenario() {
    String backgroundPath = resolveDefaultPreviewBackgroundAsset();
    VnScenarioBuilder builder = new VnScenarioBuilder("dialogue_layout_runtime_preview");
    if (!backgroundPath.isBlank()) {
      builder.addBackground("field", backgroundPath);
      builder.background("field");
    }
    builder.dialogue(
        "Lavender",
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
    builder.dialogue(
        "Lavender",
        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.");
    builder.end();
    return builder.build();
  }

  private String resolveDefaultPreviewBackgroundAsset() {
    String[] candidates = new String[] {
        "assets/demo/backgrounds/field/field.jpg",
        "assets/backgrounds/field/field.jpg",
        "assets/demo/backgrounds/field/field.png",
        "assets/backgrounds/field/field.png",
        "game/images/backgrounds/classroom_day.png"
    };
    for (String candidate : candidates) {
      if (loadImageAsset(candidate) != null) return candidate;
    }
    return "";
  }

  private void startRuntimePreviewTimer() {
    if (runtimePreviewTimer != null) return;
    runtimePreviewLastNs = -1L;
    runtimePreviewTimer = new AnimationTimer() {
      @Override
      public void handle(long now) {
        if (runtimePreviewStage == null || !runtimePreviewStage.isShowing() || runtimePreviewView == null) return;
        if (runtimePreviewLastNs < 0L) {
          runtimePreviewLastNs = now;
          return;
        }
        long dt = Math.max(1L, (now - runtimePreviewLastNs) / 1_000_000L);
        runtimePreviewLastNs = now;
        runtimePreviewView.render(dt);
      }
    };
    runtimePreviewTimer.start();
  }

  private void stopRuntimePreviewTimer() {
    if (runtimePreviewTimer == null) return;
    runtimePreviewTimer.stop();
    runtimePreviewTimer = null;
    runtimePreviewLastNs = -1L;
  }

  private void disposeRuntimePreviewWindow() {
    stopRuntimePreviewTimer();
    if (runtimePreviewView != null) {
      runtimePreviewView.dispose();
    }
    runtimePreviewView = null;
    runtimePreviewStage = null;
  }

  private void updateRuntimePreviewSize(javafx.scene.Scene scene) {
    if (scene == null || runtimePreviewView == null) return;
    double availableW = sanitizeCanvasDimension(scene.getWidth());
    double availableH = sanitizeCanvasDimension(scene.getHeight());
    double aspect = ProjectViewportSpec.resolve(projectRoot).aspect();
    double w = availableW;
    double h = w / Math.max(0.0001, aspect);
    if (h > availableH) {
      h = availableH;
      w = h * aspect;
    }
    runtimePreviewView.setSize(w, h);
  }

  private void setSelectedTextBoxButton(int index) {
    if (index < 0 || index >= textBoxButtons.size()) {
      selectedButtonIndex = -1;
      suppressEvents = true;
      lvTextBoxButtons.getSelectionModel().clearSelection();
      tfButtonId.clear();
      tfButtonLabel.clear();
      cbButtonAction.setValue("noop");
      tfButtonTarget.clear();
      chkButtonEnabled.setSelected(true);
      tfButtonAsset.clear();
      tfButtonHoverAsset.clear();
      tfButtonDisabledAsset.clear();
      setValue(spButtonX, 0.0);
      setValue(spButtonY, 0.0);
      setValue(spButtonWidth, 0.12);
      setValue(spButtonHeight, 0.25);
      suppressEvents = false;
      validateState();
      redraw();
      return;
    }
    selectedButtonIndex = index;
    VnUiActionButtonSpec button = textBoxButtons.get(index);
    suppressEvents = true;
    if (lvTextBoxButtons.getSelectionModel().getSelectedIndex() != index) {
      lvTextBoxButtons.getSelectionModel().select(index);
    }
    tfButtonId.setText(normalizeAssetPath(button.id()));
    tfButtonLabel.setText(normalizeAssetPath(button.label()));
    String actionValue = normalizeAssetPath(button.action());
    cbButtonAction.setValue(actionValue.isBlank() ? "noop" : actionValue);
    tfButtonTarget.setText(normalizeAssetPath(button.target()));
    chkButtonEnabled.setSelected(button.enabled());
    tfButtonAsset.setText(normalizeAssetPath(button.assetPath()));
    tfButtonHoverAsset.setText(normalizeAssetPath(button.hoverAssetPath()));
    tfButtonDisabledAsset.setText(normalizeAssetPath(button.disabledAssetPath()));
    setValue(spButtonX, button.x());
    setValue(spButtonY, button.y());
    setValue(spButtonWidth, button.width());
    setValue(spButtonHeight, button.height());
    suppressEvents = false;
    validateState();
    redraw();
  }

  private void syncSelectedTextBoxButtonFromControls() {
    if (selectedButtonIndex < 0 || selectedButtonIndex >= textBoxButtons.size()) return;
    VnUiActionButtonSpec current = textBoxButtons.get(selectedButtonIndex);
    String id = sanitizeButtonId(tfButtonId.getText(), current.id());
    String label = normalizeAssetPath(tfButtonLabel.getText());
    if (label.isBlank()) label = titleizeId(id);
    String action = normalizeAssetPath(cbButtonAction.getValue());
    if (action.isBlank()) action = "noop";
    String target = normalizeAssetPath(tfButtonTarget.getText());
    if (target.isBlank()) target = null;

    VnUiActionButtonSpec updated = new VnUiActionButtonSpec(
        id,
        label,
        action,
        target,
        chkButtonEnabled.isSelected(),
        normalizeAssetPath(tfButtonAsset.getText()),
        normalizeAssetPath(tfButtonHoverAsset.getText()),
        normalizeAssetPath(tfButtonDisabledAsset.getText()),
        current.boundsPoints(),
        value(spButtonX),
        value(spButtonY),
        value(spButtonWidth),
        value(spButtonHeight),
        current.coordinateSpace()
    );

    textBoxButtons.set(selectedButtonIndex, updated);
    refreshTextBoxButtonList();
    if (selectedButtonIndex >= 0 && selectedButtonIndex < lvTextBoxButtons.getItems().size()) {
      suppressEvents = true;
      lvTextBoxButtons.getSelectionModel().select(selectedButtonIndex);
      suppressEvents = false;
    }
    validateState();
  }

  private void refreshTextBoxButtonList() {
    List<String> labels = new ArrayList<>();
    for (VnUiActionButtonSpec button : textBoxButtons) {
      if (button == null) continue;
      String id = button.id();
      String action = button.action();
      String enabled = button.enabled() ? "" : " (disabled)";
      labels.add(id + " [" + action + "]" + enabled);
    }
    suppressEvents = true;
    lvTextBoxButtons.getItems().setAll(labels);
    if (selectedButtonIndex >= 0 && selectedButtonIndex < labels.size()) {
      lvTextBoxButtons.getSelectionModel().select(selectedButtonIndex);
    }
    suppressEvents = false;
  }

  private String nextButtonId() {
    Set<String> ids = new LinkedHashSet<>();
    for (VnUiActionButtonSpec button : textBoxButtons) {
      if (button != null && button.id() != null && !button.id().isBlank()) ids.add(button.id());
    }
    int idx = 1;
    while (true) {
      String candidate = "button_" + idx;
      if (!ids.contains(candidate)) return candidate;
      idx++;
    }
  }

  private String sanitizeButtonId(String value, String fallback) {
    String text = normalizeAssetPath(value).toLowerCase(Locale.ROOT);
    if (text.isBlank()) return fallback == null || fallback.isBlank() ? "button" : fallback;
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '-') out.append(c);
      else if (Character.isWhitespace(c)) out.append('_');
    }
    String normalized = out.toString();
    if (normalized.isBlank()) return fallback == null || fallback.isBlank() ? "button" : fallback;
    return normalized;
  }

  private String titleizeId(String id) {
    String src = normalizeAssetPath(id);
    if (src.isBlank()) return "Button";
    src = src.replace('_', ' ').replace('-', ' ');
    StringBuilder out = new StringBuilder();
    boolean upper = true;
    for (int i = 0; i < src.length(); i++) {
      char c = src.charAt(i);
      if (Character.isWhitespace(c)) {
        upper = true;
        out.append(c);
      } else if (upper) {
        out.append(Character.toUpperCase(c));
        upper = false;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private void emitText() {
    if (onLayoutTextChanged == null) return;
    String text = serialize(spec, style, textBoxButtons, rawProperties);
    String normalized = normalizeText(text);
    if (normalized.equals(lastEmittedText)) return;
    undoManager.captureState(text);
    lastEmittedText = normalized;
    onLayoutTextChanged.accept(text);
  }

  private void performUndo() {
    String previous = undoManager.undo();
    if (previous == null) return;
    applyingHistory = true;
    suppressEvents = true;
    try {
      setLayoutText(previous);
    } finally {
      suppressEvents = false;
      applyingHistory = false;
    }
    if (onLayoutTextChanged != null) onLayoutTextChanged.accept(previous);
  }

  private void performRedo() {
    String next = undoManager.redo();
    if (next == null) return;
    applyingHistory = true;
    suppressEvents = true;
    try {
      setLayoutText(next);
    } finally {
      suppressEvents = false;
      applyingHistory = false;
    }
    if (onLayoutTextChanged != null) onLayoutTextChanged.accept(next);
  }

  private void validateState() {
    LinkedHashSet<String> warnings = new LinkedHashSet<>();
    warnings.addAll(lineDiagnostics);
    Set<String> ids = new LinkedHashSet<>();

    if (spec.textBoxWidth() <= 0.0 || spec.textBoxHeight() <= 0.0) {
      warnings.add("Textbox width/height must be > 0.");
    }
    if (spec.choiceWidthFactor() <= 0.0 || spec.choiceHeight() <= 0.0) {
      warnings.add("Choice block width/height must be > 0.");
    }

    for (VnUiActionButtonSpec button : textBoxButtons) {
      if (button == null) continue;
      String id = normalizeAssetPath(button.id());
      if (id.isBlank()) {
        warnings.add("Textbox button id is empty.");
      } else if (!ids.add(id)) {
        warnings.add("Duplicate textbox button id: " + id);
      }
      if (button.width() <= 0.0 || button.height() <= 0.0) {
        warnings.add("Textbox button '" + id + "' has non-positive width/height.");
      }
      String action = normalizeAssetPath(button.action()).toLowerCase(Locale.ROOT);
      if ("open_menu".equals(action) && normalizeAssetPath(button.target()).isBlank()) {
        warnings.add("Textbox button '" + id + "': open_menu requires target.");
      }

      warnMissingAsset(warnings, "textBoxButton." + id + ".asset", button.assetPath());
      warnMissingAsset(warnings, "textBoxButton." + id + ".hoverAsset", button.hoverAssetPath());
      warnMissingAsset(warnings, "textBoxButton." + id + ".disabledAsset", button.disabledAssetPath());
    }

    warnMissingAsset(warnings, "textBoxAsset", style.textBoxAssetPath());
    warnMissingAsset(warnings, "choiceButtonAsset", style.choiceButtonAssetPath());
    warnMissingAsset(warnings, "choiceButtonHoverAsset", style.choiceButtonHoverAssetPath());
    warnMissingAsset(warnings, "choiceButtonSelectedAsset", style.choiceButtonSelectedAssetPath());
    warnMissingAsset(warnings, "choiceButtonDisabledAsset", style.choiceButtonDisabledAssetPath());
    warnMissingAsset(warnings, "nvlPanelAsset", style.nvlPanelAssetPath());
    warnMissingAsset(warnings, "bubbleAsset", style.bubbleAssetPath());

    if (warnings.isEmpty()) {
      validation.setText("No issues detected.");
      validation.setTextFill(LayoutStudioPalette.TEXT_SUCCESS);
    } else {
      validation.setText(String.join("\n", warnings));
      validation.setTextFill(LayoutStudioPalette.TEXT_WARNING);
    }
  }

  private void warnMissingAsset(Set<String> warnings, String field, String assetPath) {
    if (projectRoot == null) return;
    String normalized = normalizeAssetPath(assetPath);
    if (normalized.isBlank()) return;
    File file = resolveAssetFile(normalized);
    if (file == null || !file.exists()) {
      warnings.add(field + " not found: " + normalized);
    }
  }

  private static String serialize(
      VnUiLayoutSpec spec,
      VnUiStyleSpec style,
      List<VnUiActionButtonSpec> textBoxButtons,
      Properties base
  ) {
    Properties merged = new Properties();
    if (base != null) {
      for (String key : base.stringPropertyNames()) merged.setProperty(key, base.getProperty(key));
    }
    Properties generated = VnUiLayoutLoader.toProperties(spec, style, textBoxButtons);
    for (String key : generated.stringPropertyNames()) {
      merged.setProperty(key, generated.getProperty(key));
    }

    StringBuilder out = new StringBuilder();
    out.append("# Dialogue UI layout (.layout)").append(System.lineSeparator());
    out.append("# Text-first workflow: edit -> save -> run runtime -> validate -> iterate.").append(System.lineSeparator());
    out.append("# Format: key=value (Java .properties)").append(System.lineSeparator());
    out.append("# Units: fractions (0..1) are viewport-relative; paddings/offsets are pixels.").append(System.lineSeparator());
    out.append("# choiceYStart: -1 = auto-center choices vertically, otherwise use a 0..1 fraction.").append(System.lineSeparator());
    for (String key : KNOWN_KEYS) {
      String value = merged.getProperty(key);
      if (value == null) continue;
      if ("textBoxX".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Text box container ---").append(System.lineSeparator());
        out.append("# textBoxX/textBoxY anchor the panel. width/height are viewport fractions.").append(System.lineSeparator());
      } else if ("textBoxAsset".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Text box visual style / shape ---").append(System.lineSeparator());
        out.append("# Asset keys are optional and can be mixed with color fallbacks.").append(System.lineSeparator());
      } else if ("nameBoxBoundsPoints".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Advanced bounds polygons (optional) ---").append(System.lineSeparator());
        out.append("# boundsPoints values are encoded point lists for custom clickable shapes.").append(System.lineSeparator());
      } else if ("choiceButtonAsset".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Choice button assets / colors ---").append(System.lineSeparator());
      } else if ("nameBoxXOffset".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Name box placement ---").append(System.lineSeparator());
        out.append("# Offsets are relative to the textbox top-left corner.").append(System.lineSeparator());
      } else if ("dialogueTextHorizontalPadding".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Dialogue text inner bounds ---").append(System.lineSeparator());
      } else if ("choiceXCenter".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Choice list geometry ---").append(System.lineSeparator());
        out.append("# choiceWidthFactor is viewport-relative; height/gap/text padding are pixels.").append(System.lineSeparator());
      } else if ("nvlX".equals(key)) {
        out.append(System.lineSeparator()).append("# --- NVL presentation mode ---").append(System.lineSeparator());
        out.append("# Enable at runtime with [mode dialogue nvl]; return with [mode dialogue standard].").append(System.lineSeparator());
      } else if ("nvlPanelAsset".equals(key)) {
        out.append(System.lineSeparator()).append("# --- NVL panel style ---").append(System.lineSeparator());
      } else if ("bubbleWidthFactor".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Bubble presentation mode ---").append(System.lineSeparator());
        out.append("# Enable at runtime with [mode bubble on] and disable with [mode bubble off].").append(System.lineSeparator());
      } else if ("bubbleAsset".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Bubble visual style ---").append(System.lineSeparator());
      } else if ("characterHeightFactor".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Character framing tweaks ---").append(System.lineSeparator());
      }
      out.append(key).append("=").append(value).append(System.lineSeparator());
    }
    if (textBoxButtons != null && !textBoxButtons.isEmpty()) {
      out.append(System.lineSeparator()).append("# --- Textbox action buttons ---").append(System.lineSeparator());
      out.append("# textBoxButton.ids controls order and active ids.").append(System.lineSeparator());
      out.append("# Per-button keys use textBoxButton.<id>.*.").append(System.lineSeparator());
      out.append("# space=textbox (default) or space=viewport for screen-space anchoring.").append(System.lineSeparator());
      out.append("# action examples: save_menu, load_menu, settings_menu, main_menu, open_menu, back.").append(System.lineSeparator());
      List<String> ids = new ArrayList<>();
      for (VnUiActionButtonSpec button : textBoxButtons) {
        if (button == null || button.id() == null || button.id().isBlank()) continue;
        ids.add(button.id());
      }
      if (!ids.isEmpty()) {
        out.append("textBoxButton.ids=").append(String.join(",", ids)).append(System.lineSeparator());
      }
      for (VnUiActionButtonSpec button : textBoxButtons) {
        if (button == null || button.id() == null || button.id().isBlank()) continue;
        String prefix = "textBoxButton." + button.id() + ".";
        out.append(prefix).append("label=").append(button.label()).append(System.lineSeparator());
        out.append(prefix).append("action=").append(button.action()).append(System.lineSeparator());
        if (button.target() != null && !button.target().isBlank()) {
          out.append(prefix).append("target=").append(button.target()).append(System.lineSeparator());
        }
        out.append(prefix).append("enabled=").append(button.enabled()).append(System.lineSeparator());
        if ("viewport".equalsIgnoreCase(button.coordinateSpace())) {
          out.append(prefix).append("space=viewport").append(System.lineSeparator());
        }
        if (button.assetPath() != null && !button.assetPath().isBlank()) {
          out.append(prefix).append("asset=").append(button.assetPath()).append(System.lineSeparator());
        }
        if (button.hoverAssetPath() != null && !button.hoverAssetPath().isBlank()) {
          out.append(prefix).append("hoverAsset=").append(button.hoverAssetPath()).append(System.lineSeparator());
        }
        if (button.disabledAssetPath() != null && !button.disabledAssetPath().isBlank()) {
          out.append(prefix).append("disabledAsset=").append(button.disabledAssetPath()).append(System.lineSeparator());
        }
        if (button.boundsPoints() != null && !button.boundsPoints().isBlank()) {
          out.append(prefix).append("boundsPoints=").append(button.boundsPoints()).append(System.lineSeparator());
        }
        out.append(prefix).append("x=").append(formatDouble(button.x())).append(System.lineSeparator());
        out.append(prefix).append("y=").append(formatDouble(button.y())).append(System.lineSeparator());
        out.append(prefix).append("width=").append(formatDouble(button.width())).append(System.lineSeparator());
        out.append(prefix).append("height=").append(formatDouble(button.height())).append(System.lineSeparator());
      }
    }
    List<String> extras = new ArrayList<>();
    for (String key : merged.stringPropertyNames()) {
      if (isKnownKey(key)) continue;
      if (key.startsWith("textBoxButton.")) continue;
      extras.add(key);
    }
    extras.sort(String::compareTo);
    if (!extras.isEmpty()) {
      out.append(System.lineSeparator()).append("# Additional custom keys").append(System.lineSeparator());
      for (String key : extras) {
        out.append(key).append("=").append(merged.getProperty(key, "")).append(System.lineSeparator());
      }
    }
    return out.toString();
  }

  private static String formatDouble(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
  }

  private static boolean isKnownKey(String key) {
    for (String known : KNOWN_KEYS) {
      if (known.equals(key)) return true;
    }
    return false;
  }

  private static Spinner<Double> spinner(double min, double max, double initial, double step) {
    Spinner<Double> spinner = new Spinner<>();
    SpinnerValueFactory.DoubleSpinnerValueFactory vf =
        new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step);
    vf.setConverter(new javafx.util.StringConverter<>() {
      @Override
      public String toString(Double value) {
        if (value == null) return "";
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.4f", value)
            .replaceAll("0+$", "")
            .replaceAll("\\.$", "");
      }

      @Override
      public Double fromString(String string) {
        if (string == null || string.isBlank()) return vf.getValue();
        try {
          return Double.parseDouble(string.trim());
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
          return vf.getValue();
        }
      }
    });
    spinner.setValueFactory(vf);
    spinner.setEditable(true);
    return spinner;
  }

  private static double value(Spinner<Double> spinner) {
    Double v = spinner.getValue();
    return v == null ? 0 : v;
  }

  private static void setValue(Spinner<Double> spinner, double value) {
    SpinnerValueFactory<Double> vf = spinner.getValueFactory();
    if (vf instanceof SpinnerValueFactory.DoubleSpinnerValueFactory dsvf) {
      dsvf.setValue(value);
    } else {
      vf.setValue(value);
    }
  }

  private static double clamp(double v, double min, double max) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return min;
    if (v < min) return min;
    if (v > max) return max;
    return v;
  }

  private static double clamp01(double v) {
    return clamp(v, 0, 1);
  }

  private ChoicePreviewStyle resolveChoicePreviewStyle() {
    Color bg = parseColorValue(style.choiceBackgroundColor(), RUNTIME_CHOICE_BG_COLOR);
    Color hoverBg = parseColorValue(
        firstNonBlank(style.choiceHoverColor(), style.choiceSelectedColor()),
        RUNTIME_CHOICE_HOVER_COLOR);
    Color disabledBg = parseColorValue(style.choiceDisabledColor(), RUNTIME_CHOICE_DISABLED_COLOR);

    Color text = parseColorValue(style.choiceTextColor(), RUNTIME_TEXT_COLOR);
    Color hoverText = parseColorValue(
        firstNonBlank(style.choiceHoverTextColor(), style.choiceSelectedTextColor()),
        text);
    Color disabledText = parseColorValue(style.choiceDisabledTextColor(), RUNTIME_TEXT_COLOR_DISABLED);

    Color border = parseColorValue(style.choiceBorderColor(), RUNTIME_TEXT_COLOR);
    Color hoverBorder = parseColorValue(
        firstNonBlank(style.choiceHoverBorderColor(), style.choiceSelectedBorderColor()),
        border);
    Color disabledBorder = parseColorValue(style.choiceDisabledBorderColor(), RUNTIME_CHOICE_DISABLED_BORDER_COLOR);

    double cornerRadius = clamp(style.choiceCornerRadius(), 0.0, 96.0);
    double borderWidth = clamp(style.choiceBorderWidth(), 0.0, 12.0);
    double textBaselineOffset = clamp(style.choiceTextBaselineOffset(), -120.0, 120.0);

    return new ChoicePreviewStyle(
        bg,
        hoverBg,
        disabledBg,
        text,
        hoverText,
        disabledText,
        border,
        hoverBorder,
        disabledBorder,
        cornerRadius,
        borderWidth,
        textBaselineOffset
    );
  }

  private Color parseColorValue(String raw, Color fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Color.web(raw.trim());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  private static Color withOpacity(Color color, double opacity) {
    Color base = color == null ? Color.BLACK : color;
    double a = opacity;
    if (Double.isNaN(a) || Double.isInfinite(a)) a = 0.0;
    if (a < 0.0) a = 0.0;
    if (a > 1.0) a = 1.0;
    return Color.color(base.getRed(), base.getGreen(), base.getBlue(), a);
  }

  private static <T> T firstNonNull(T first, T second) {
    return first != null ? first : second;
  }

  private void loadPreviewAssets() {
    loadTextBoxAssetImage();
    loadChoiceAssetImages();
  }

  private void loadTextBoxAssetImage() {
    textBoxAssetImage = loadImageAsset(style.textBoxAssetPath());
    nameBoxAssetImage = loadImageAsset(style.nameBoxAssetPath());
  }

  private void loadChoiceAssetImages() {
    choiceButtonAssetImage = loadImageAsset(style.choiceButtonAssetPath());
    choiceButtonHoverAssetImage = loadImageAsset(
        firstNonBlank(style.choiceButtonHoverAssetPath(), style.choiceButtonSelectedAssetPath()));
    choiceButtonDisabledAssetImage = loadImageAsset(style.choiceButtonDisabledAssetPath());
  }

  private Image loadImageAsset(String assetPath) {
    String normalized = normalizeAssetPath(assetPath);
    if (normalized.isBlank()) return null;
    Image cached = previewAssetCache.get(normalized);
    if (cached != null) return cached;
    File assetFile = resolveAssetFile(normalized);
    if (assetFile == null || !assetFile.exists() || !assetFile.isFile()) {
      previewAssetCache.remove(normalized);
      return null;
    }
    try {
      String url = assetFile.toURI().toURL().toExternalForm();
      Image image = new Image(url, false);
      if (image.isError()) {
        previewAssetCache.remove(normalized);
        return null;
      }
      previewAssetCache.put(normalized, image);
      return image;
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      previewAssetCache.remove(normalized);
      return null;
    }
  }

  private File resolveInitialAssetDirectory() {
    if (projectRoot != null) {
      File uiDir = new File(projectRoot, "assets/ui");
      if (uiDir.exists() && uiDir.isDirectory()) return uiDir;
      if (projectRoot.exists() && projectRoot.isDirectory()) return projectRoot;
    }
    return new File(System.getProperty("user.home", "."));
  }

  private File resolveAssetFile(String path) {
    String normalized = normalizeAssetPath(path);
    if (normalized.isBlank()) return null;

    File direct = new File(normalized);
    if (direct.isAbsolute()) return direct;

    if (projectRoot != null) {
      File fromRoot = new File(projectRoot, normalized);
      if (fromRoot.exists()) return fromRoot;
    }
    return direct;
  }

  private String toProjectRelativePath(File file) {
    if (file == null) return "";
    if (projectRoot == null) {
      return file.getAbsolutePath().replace('\\', '/');
    }
    try {
      Path root = projectRoot.toPath().toAbsolutePath().normalize();
      Path abs = file.toPath().toAbsolutePath().normalize();
      if (abs.startsWith(root)) {
        return root.relativize(abs).toString().replace('\\', '/');
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      // Fall through to absolute path.
    }
    return file.getAbsolutePath().replace('\\', '/');
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) return first;
    if (second != null && !second.isBlank()) return second;
    return "";
  }

  private static String normalizeAssetPath(String value) {
    if (value == null) return "";
    return value.trim().replace('\\', '/');
  }

  private static String normalizeColorValue(String value) {
    if (value == null) return "";
    return value.trim();
  }

  private static String normalizeFontWeight(String value) {
    if (value == null || value.isBlank()) return null;
    String upper = value.trim().toUpperCase(Locale.ROOT);
    return "NORMAL".equals(upper) || "BOLD".equals(upper) || "SEMI_BOLD".equals(upper) ? upper : null;
  }

  private static String normalizeText(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
  }

  private double previewScale() {
    double viewportW = ProjectViewportSpec.resolve(projectRoot).width();
    double canvasW = Math.max(1, preview.getWidth());
    double scale = canvasW / Math.max(1, viewportW);
    return Double.isFinite(scale) && scale > 0 ? scale : 1.0;
  }

  private void updatePreviewSize(StackPane previewPane) {
    if (previewPane == null) return;
    double availableW = sanitizeCanvasDimension(previewPane.getWidth() - PREVIEW_PADDING * 2.0);
    double availableH = sanitizeCanvasDimension(previewPane.getHeight() - PREVIEW_PADDING * 2.0);
    double aspect = ProjectViewportSpec.resolve(projectRoot).aspect();
    double w = availableW;
    double h = w / Math.max(0.0001, aspect);
    if (h > availableH) {
      h = availableH;
      w = h * aspect;
    }
    if (Math.abs(preview.getWidth() - w) >= 0.5) preview.setWidth(w);
    if (Math.abs(preview.getHeight() - h) >= 0.5) preview.setHeight(h);
    double x = PREVIEW_PADDING + (availableW - w) * 0.5;
    double y = PREVIEW_PADDING + (availableH - h) * 0.5;
    if (Math.abs(preview.getLayoutX() - x) >= 0.5) preview.setLayoutX(x);
    if (Math.abs(preview.getLayoutY() - y) >= 0.5) preview.setLayoutY(y);
  }

  private static double sanitizeCanvasDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return clamp(value, 1.0, 8192.0);
  }

  private double computeTextWidth(GraphicsContext g, String text, Font font) {
    if (text == null || text.isEmpty()) return 0;
    javafx.scene.text.Text helper = new javafx.scene.text.Text(text);
    helper.setFont(font);
    return helper.getLayoutBounds().getWidth();
  }

  private double computeTextAscent(Font font) {
    javafx.scene.text.Text helper = new javafx.scene.text.Text("Hg");
    helper.setFont(font);
    double ascent = -helper.getLayoutBounds().getMinY();
    return ascent > 0.0 ? ascent : Math.max(1.0, font.getSize() * 0.8);
  }

  private double computeTextHeight(Font font) {
    javafx.scene.text.Text helper = new javafx.scene.text.Text("Hg");
    helper.setFont(font);
    double height = helper.getLayoutBounds().getHeight();
    return height > 0.0 ? height : Math.max(1.0, font.getSize());
  }

  private double resolvePaddedTextBaselineY(
      double boxY,
      double boxHeight,
      double topPadding,
      double bottomPadding,
      Font font,
      double yAlign
  ) {
    double contentTop = boxY + Math.max(0.0, topPadding);
    double contentHeight = Math.max(1.0, boxHeight - Math.max(0.0, topPadding) - Math.max(0.0, bottomPadding));
    double textHeight = computeTextHeight(font);
    double ascent = computeTextAscent(font);
    double clampedAlign = clamp(yAlign, 0.0, 1.0);
    double extra = Math.max(0.0, contentHeight - textHeight);
    return contentTop + ascent + extra * clampedAlign;
  }

  private record Rect(double x, double y, double w, double h) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + w && py >= y && py <= y + h;
    }
  }

  private record TextBoxGeometry(double x, double y, double width, double height) {}

  private record ChoicePreviewStyle(
      Color backgroundColor,
      Color hoverBackgroundColor,
      Color disabledBackgroundColor,
      Color textColor,
      Color hoverTextColor,
      Color disabledTextColor,
      Color borderColor,
      Color hoverBorderColor,
      Color disabledBorderColor,
      double cornerRadius,
      double borderWidth,
      double textBaselineOffset
  ) {}

  private record LayoutRects(Rect textBox, Rect nameBox, Rect dialogueBounds, Rect choiceBlock) {}
}
