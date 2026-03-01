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
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;

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
import javafx.scene.control.Tooltip;
import javafx.scene.control.TitledPane;
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
import javafx.stage.Window;

/**
 * Visual editor for dialogue UI layout (textbox/namebox/text bounds/choice bounds).
 * The view emits full properties text that can be synced with a code editor.
 */
public class DialogueLayoutEditorView extends BorderPane {
  private static final double PREVIEW_PADDING = 8.0;
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
      "dialogueTextHorizontalPadding",
      "dialogueTextTopPadding",
      "dialogueTextRightPadding",
      "dialogueTextBottomPadding",
      "choiceXCenter",
      "choiceYStart",
      "choiceWidthFactor",
      "choiceHeight",
      "choiceGap",
      "choiceTextXPadding"
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

  private final Spinner<Double> spNameBoxXOffset = spinner(-500, 500, 20, 1);
  private final Spinner<Double> spNameBoxYOffset = spinner(-500, 500, -40, 1);
  private final Spinner<Double> spNameBoxWidth = spinner(20, 1000, 200, 1);
  private final Spinner<Double> spNameBoxHeight = spinner(12, 300, 40, 1);
  private final Spinner<Double> spNameTextXOffset = spinner(-300, 300, 10, 1);
  private final Spinner<Double> spNameTextBaselineOffset = spinner(-300, 300, 25, 1);

  private final Spinner<Double> spDialoguePaddingX = spinner(0, 300, 20, 1);
  private final Spinner<Double> spDialoguePaddingTop = spinner(-300, 300, 40, 1);
  private final Spinner<Double> spDialoguePaddingRight = spinner(0, 300, 20, 1);
  private final Spinner<Double> spDialoguePaddingBottom = spinner(0, 300, 10, 1);

  private final Spinner<Double> spChoiceXCenter = spinner(0, 1, 0.5, 0.01);
  private final Spinner<Double> spChoiceYStart = spinner(-1, 1, -1, 0.01);
  private final Spinner<Double> spChoiceWidthFactor = spinner(0.1, 1, 0.6, 0.01);
  private final Spinner<Double> spChoiceHeight = spinner(14, 200, 50, 1);
  private final Spinner<Double> spChoiceGap = spinner(0, 120, 10, 1);
  private final Spinner<Double> spChoiceTextXPadding = spinner(0, 300, 20, 1);
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
    loadTextBoxAssetImage();
    loadChoiceAssetImages();
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
      // Keep defaults for invalid input.
    }
    VnUiLayoutLoader.LoadResult parsed = VnUiLayoutLoader.parseWithDiagnostics(
        rawProperties,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults()
    );
    spec = parsed.layout();
    style = parsed.style();
    textBoxButtons = new ArrayList<>(parsed.textBoxButtons());
    applySpecToControls(spec);
    applyStyleToControls(style);
    refreshTextBoxButtonList();
    setSelectedTextBoxButton(textBoxButtons.isEmpty() ? -1 : 0);
    loadTextBoxAssetImage();
    loadChoiceAssetImages();
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
    tfTextBoxAsset.setPromptText("assets/ui/textbox.png");
    tfTextBoxColor.setPromptText("#000000");
    tfChoiceButtonAsset.setPromptText("assets/ui/choice.png");
    tfChoiceButtonHoverAsset.setPromptText("assets/ui/choice_hover.png");
    tfChoiceButtonSelectedAsset.setPromptText("assets/ui/choice_selected.png");
    tfChoiceButtonDisabledAsset.setPromptText("assets/ui/choice_disabled.png");
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

    // --- Section: Textbox ---
    GridPane textboxGrid = sectionGrid();
    int row = 0;
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
    HBox historyButtons = new HBox(8, btnUndo, btnRedo);
    historyButtons.setPadding(new Insets(4, 8, 4, 8));

    Label hint = new Label("Drag blocks in preview to position or resize textbox/name/choices/text bounds/buttons. Right and bottom text paddings allow exact text area sizing.");
    hint.getStyleClass().add("muted");
    hint.setWrapText(true);
    hint.setPadding(new Insets(4, 8, 8, 8));

    validation.getStyleClass().add("muted");
    validation.setWrapText(true);
    validation.setPadding(new Insets(2, 8, 8, 8));

    javafx.scene.layout.VBox sections = new javafx.scene.layout.VBox(2,
        tpTextbox, tpName, tpTextBounds, tpChoiceLayout, tpChoiceColors, tpButtons,
        historyButtons, hint, validation
    );
    sections.setPadding(new Insets(4));

    UndoManager.installKeyboardShortcuts(this, this::performUndo, this::performRedo);

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
    controls.add(spDialoguePaddingX);
    controls.add(spDialoguePaddingTop);
    controls.add(spDialoguePaddingRight);
    controls.add(spDialoguePaddingBottom);
    controls.add(spChoiceXCenter);
    controls.add(spChoiceYStart);
    controls.add(spChoiceWidthFactor);
    controls.add(spChoiceHeight);
    controls.add(spChoiceGap);
    controls.add(spChoiceTextXPadding);
    controls.add(spChoiceCornerRadius);
    controls.add(spChoiceBorderWidth);
    controls.add(spChoiceTextBaselineOffset);
    controls.add(spTextBoxOverlayOpacity);
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
    lvTextBoxButtons.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
      if (suppressEvents) return;
      int idx = nv == null ? -1 : nv.intValue();
      setSelectedTextBoxButton(idx);
    });
  }

  private void onControlChanged() {
    if (suppressEvents) return;
    spec = readSpecFromControls();
    style = readStyleFromControls();
    syncSelectedTextBoxButtonFromControls();
    loadTextBoxAssetImage();
    loadChoiceAssetImages();
    validateState();
    redraw();
    emitText();
  }

  private void onStyleChanged() {
    if (suppressEvents) return;
    style = readStyleFromControls();
    syncSelectedTextBoxButtonFromControls();
    loadTextBoxAssetImage();
    loadChoiceAssetImages();
    validateState();
    redraw();
    emitText();
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
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.dialogueTextRightPadding(),
            dragStartSpec.dialogueTextBottomPadding(),
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding()
        );
      } else if (dragTarget == DragTarget.NAME_BOX) {
        next = new VnUiLayoutSpec(
            dragStartSpec.textBoxX(),
            dragStartSpec.textBoxY(),
            dragStartSpec.textBoxWidth(),
            dragStartSpec.textBoxHeight(),
            dragStartSpec.textBoxPadding(),
            dragStartSpec.nameBoxXOffset() + dx,
            dragStartSpec.nameBoxYOffset() + dy,
            dragStartSpec.nameBoxWidth(),
            dragStartSpec.nameBoxHeight(),
            dragStartSpec.nameTextXOffset(),
            dragStartSpec.nameTextBaselineOffset(),
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.dialogueTextRightPadding(),
            dragStartSpec.dialogueTextBottomPadding(),
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding()
        );
      } else if (dragTarget == DragTarget.CHOICE_BLOCK) {
        double currentChoiceStart = resolveChoiceYStart(dragStartSpec, h, 3);
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
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.dialogueTextRightPadding(),
            dragStartSpec.dialogueTextBottomPadding(),
            dragStartSpec.choiceXCenter() + (dx / w),
            nextYStartNorm,
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding()
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
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.dialogueTextRightPadding(),
            dragStartSpec.dialogueTextBottomPadding(),
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding()
        );
      } else if (dragTarget == DragTarget.CHOICE_RESIZE) {
        double newWidthFactor = Math.max(0.05, dragStartSpec.choiceWidthFactor() + (dx / w));
        double newChoiceHeight = Math.max(8, dragStartSpec.choiceHeight() + dy);
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
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.dialogueTextRightPadding(),
            dragStartSpec.dialogueTextBottomPadding(),
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            newWidthFactor,
            newChoiceHeight,
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding()
        );
      } else if (dragTarget == DragTarget.DIALOGUE_BOUNDS || dragTarget == DragTarget.DIALOGUE_BOUNDS_RESIZE) {
        TextBoxGeometry textBox = computeTextBoxGeometry(dragStartSpec, w, h);
        double boxW = Math.max(1, textBox.width());
        double boxH = Math.max(1, textBox.height());
        double minTextW = 40;
        double minTextH = 20;

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
          left = clamp(left0 + dx, 0, Math.max(0, boxW - width0));
          top = clamp(top0 + dy, 0, Math.max(0, boxH - height0));
          right = Math.max(0, boxW - left - width0);
          bottom = Math.max(0, boxH - top - height0);
        } else {
          double newTextW = clamp(width0 + dx, minTextW, Math.max(minTextW, boxW - left0));
          double newTextH = clamp(height0 + dy, minTextH, Math.max(minTextH, boxH - top0));
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
            left,
            top,
            right,
            bottom,
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding()
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
          if (dragTarget == DragTarget.TEXTBOX_BUTTON) {
            nx = clamp01(baseButton.x() + (dx / Math.max(1, textBox.width())));
            ny = clamp01(baseButton.y() + (dy / Math.max(1, textBox.height())));
          } else {
            nw = clamp(baseButton.width() + (dx / Math.max(1, textBox.width())), 0.01, 1.0);
            nh = clamp(baseButton.height() + (dy / Math.max(1, textBox.height())), 0.01, 1.0);
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
              nh
          );
          textBoxButtons = new ArrayList<>(dragStartButtons);
          textBoxButtons.set(dragButtonIndex, moved);
          setSelectedTextBoxButton(dragButtonIndex);
          validateState();
          redraw();
          return;
        }
      }

      spec = next;
      suppressEvents = true;
      applySpecToControls(spec);
      suppressEvents = false;
      validateState();
      redraw();
    });
  }

  private static final double HANDLE_SIZE = 10;

  private DragTarget hitTest(double x, double y) {
    LayoutRects r = computeRects(spec, preview.getWidth(), preview.getHeight());
    // Resize handles (bottom-right corners) take priority
    if (isNearCorner(x, y, r.textBox())) return DragTarget.TEXT_BOX_RESIZE;
    if (isNearCorner(x, y, r.choiceBlock())) return DragTarget.CHOICE_RESIZE;
    if (isNearCorner(x, y, r.dialogueBounds())) return DragTarget.DIALOGUE_BOUNDS_RESIZE;
    dragButtonIndex = hitTestButtonResizeIndex(x, y, r.textBox());
    if (dragButtonIndex >= 0) return DragTarget.TEXTBOX_BUTTON_RESIZE;
    dragButtonIndex = hitTestButtonIndex(x, y, r.textBox());
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
    GraphicsContext g = preview.getGraphicsContext2D();

    g.setFill(LayoutStudioPalette.CANVAS_BACKGROUND_ALT);
    g.fillRect(0, 0, w, h);

    g.setStroke(LayoutStudioPalette.GRID_LINE);
    g.setLineWidth(1);
    for (int i = 1; i < 6; i++) {
      double yy = (h / 6.0) * i;
      g.strokeLine(0, yy, w, yy);
    }

    LayoutRects rects = computeRects(spec, w, h);
    ChoicePreviewStyle choiceStyle = resolveChoicePreviewStyle();

    // Choice block preview.
    int choiceCount = Math.max(1, previewChoiceLabels.size());
    double y = rects.choiceBlock().y();
    for (int i = 0; i < choiceCount; i++) {
      boolean hovered = i == 0;
      boolean enabled = i != 2;
      Image buttonImage = enabled
          ? (hovered ? firstNonNull(choiceButtonHoverAssetImage, choiceButtonAssetImage) : choiceButtonAssetImage)
          : firstNonNull(choiceButtonDisabledAssetImage, choiceButtonAssetImage);
      if (buttonImage != null && buttonImage.getWidth() > 1 && buttonImage.getHeight() > 1) {
        g.drawImage(buttonImage, rects.choiceBlock().x(), y, rects.choiceBlock().w(), spec.choiceHeight());
      } else {
        Color fill = !enabled
            ? choiceStyle.disabledBackgroundColor()
            : (hovered ? choiceStyle.hoverBackgroundColor() : choiceStyle.backgroundColor());
        g.setFill(fill);
        g.fillRoundRect(
            rects.choiceBlock().x(),
            y,
            rects.choiceBlock().w(),
            spec.choiceHeight(),
            choiceStyle.cornerRadius(),
            choiceStyle.cornerRadius());
      }
      Color border = !enabled
          ? choiceStyle.disabledBorderColor()
          : (hovered ? choiceStyle.hoverBorderColor() : choiceStyle.borderColor());
      g.setStroke(border);
      g.setLineWidth(choiceStyle.borderWidth());
      g.strokeRoundRect(
          rects.choiceBlock().x(),
          y,
          rects.choiceBlock().w(),
          spec.choiceHeight(),
          choiceStyle.cornerRadius(),
          choiceStyle.cornerRadius());
      Color textColor = !enabled
          ? choiceStyle.disabledTextColor()
          : (hovered ? choiceStyle.hoverTextColor() : choiceStyle.textColor());
      g.setFill(textColor);
      g.setFont(Font.font(Font.getDefault().getFamily(), 14));
      String choiceLabel = i < previewChoiceLabels.size() ? previewChoiceLabels.get(i) : "Choice " + (i + 1);
      g.fillText(
          choiceLabel,
          rects.choiceBlock().x() + spec.choiceTextXPadding(),
          y + spec.choiceHeight() / 2 + choiceStyle.textBaselineOffset());
      y += spec.choiceHeight() + spec.choiceGap();
    }

    // Textbox and name box overlay.
    Color textBoxTint = parseColorValue(style.textBoxColor(), Color.BLACK);
    double overlayOpacity = style.textBoxOpacity() == null ? 0.28 : clamp(style.textBoxOpacity(), 0.0, 1.0);
    List<BoundsPointCodec.Point> textBoxBounds = parseBoundsPoints(style.textBoxBoundsPoints());
    List<BoundsPointCodec.Point> nameBoxBounds = parseBoundsPoints(style.nameBoxBoundsPoints());
    List<BoundsPointCodec.Point> dialogueBounds = parseBoundsPoints(style.dialogueTextBoundsPoints());
    if (hasPolygon(textBoxBounds)) g.save();
    if (hasPolygon(textBoxBounds)) clipToLocalPolygon(g, textBoxBounds, rects.textBox());
    if (textBoxAssetImage != null && textBoxAssetImage.getWidth() > 1 && textBoxAssetImage.getHeight() > 1) {
      g.drawImage(textBoxAssetImage, rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
      if (overlayOpacity > 0.001) {
        g.setFill(withOpacity(textBoxTint, overlayOpacity));
        g.fillRect(rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
      }
    } else {
      g.setFill(withOpacity(textBoxTint, clamp(Math.max(overlayOpacity, 0.62), 0.0, 1.0)));
      g.fillRect(rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
    }
    if (hasPolygon(textBoxBounds)) g.restore();
    g.setStroke(LayoutStudioPalette.ACCENT_BLUE);
    g.setLineWidth(2);
    if (hasPolygon(textBoxBounds)) {
      strokeLocalPolygon(g, textBoxBounds, rects.textBox());
    } else {
      g.strokeRect(rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
    }

    if (hasPolygon(nameBoxBounds)) g.save();
    if (hasPolygon(nameBoxBounds)) clipToLocalPolygon(g, nameBoxBounds, rects.nameBox());
    g.setFill(LayoutStudioPalette.DIALOGUE_NAME_FILL);
    g.fillRect(rects.nameBox().x(), rects.nameBox().y(), rects.nameBox().w(), rects.nameBox().h());
    g.setFill(LayoutStudioPalette.TEXT_PRIMARY);
    g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 14));
    g.fillText(previewSpeakerName, rects.nameBox().x() + spec.nameTextXOffset(), rects.nameBox().y() + spec.nameTextBaselineOffset());
    if (hasPolygon(nameBoxBounds)) g.restore();
    g.setStroke(LayoutStudioPalette.PANEL_BORDER_LIGHT);
    if (hasPolygon(nameBoxBounds)) {
      strokeLocalPolygon(g, nameBoxBounds, rects.nameBox());
    } else {
      g.strokeRect(rects.nameBox().x(), rects.nameBox().y(), rects.nameBox().w(), rects.nameBox().h());
    }

    // Dialogue text bounds.
    g.setStroke(LayoutStudioPalette.ACCENT_GOLD);
    g.setLineDashes(6);
    if (hasPolygon(dialogueBounds)) {
      strokeLocalPolygon(g, dialogueBounds, rects.dialogueBounds());
    } else {
      g.strokeRect(rects.dialogueBounds().x(), rects.dialogueBounds().y(), rects.dialogueBounds().w(), rects.dialogueBounds().h());
    }
    g.setLineDashes(0);
    drawResizeHandle(g, rects.dialogueBounds(), LayoutStudioPalette.ACCENT_GOLD);
    if (hasPolygon(dialogueBounds)) g.save();
    if (hasPolygon(dialogueBounds)) clipToLocalPolygon(g, dialogueBounds, rects.dialogueBounds());
    g.setFill(LayoutStudioPalette.TEXT_SECONDARY);
    g.setFont(Font.font(Font.getDefault().getFamily(), 13));
    g.fillText(previewDialogueLine1, rects.dialogueBounds().x() + 8, rects.dialogueBounds().y() + 18);
    g.fillText(previewDialogueLine2, rects.dialogueBounds().x() + 8, rects.dialogueBounds().y() + 36);
    if (hasPolygon(dialogueBounds)) g.restore();

    drawTextBoxButtonPreview(g, rects.textBox());

    // Labels
    drawTag(g, rects.textBox().x() + 6, rects.textBox().y() + 16, "Textbox");
    drawTag(g, rects.nameBox().x() + 6, rects.nameBox().y() - 4, "Name Box");
    drawTag(g, rects.choiceBlock().x() + 6, rects.choiceBlock().y() - 4, "Choices");
    drawTag(g, rects.dialogueBounds().x() + 6, rects.dialogueBounds().y() - 4, "Text Bounds");
  }

  private void drawTextBoxButtonPreview(GraphicsContext g, Rect textBoxRect) {
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return;
    for (int i = 0; i < textBoxButtons.size(); i++) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null) continue;
      Rect rect = computeTextBoxButtonRect(button, textBoxRect);
      boolean selected = i == selectedButtonIndex;

      Image asset = loadImageAsset(button.assetPath());
      Image hoverAsset = loadImageAsset(button.hoverAssetPath());
      Image drawAsset = selected ? firstNonNull(hoverAsset, asset) : asset;
      if (drawAsset != null && drawAsset.getWidth() > 1 && drawAsset.getHeight() > 1) {
        g.drawImage(drawAsset, rect.x(), rect.y(), rect.w(), rect.h());
      } else {
        g.setFill(selected ? Color.rgb(92, 136, 212, 0.72) : Color.rgb(39, 52, 80, 0.72));
        g.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 8, 8);
      }
      g.setStroke(selected ? LayoutStudioPalette.ACCENT_GOLD : LayoutStudioPalette.ACCENT_BLUE);
      g.setLineWidth(selected ? 2 : 1.2);
      g.strokeRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 8, 8);
      if (selected) {
        double handle = 8;
        double hx = rect.x() + rect.w() - handle;
        double hy = rect.y() + rect.h() - handle;
        g.setFill(LayoutStudioPalette.ACCENT_GOLD);
        g.fillRect(hx, hy, handle, handle);
        g.setStroke(LayoutStudioPalette.PANEL_BORDER);
        g.setLineWidth(1);
        g.strokeRect(hx, hy, handle, handle);
      }
      g.setFill(LayoutStudioPalette.TEXT_PRIMARY);
      g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, clamp(rect.h() * 0.36, 10, 16)));
      String label = normalizeAssetPath(button.label());
      if (label.isBlank()) label = button.id();
      double labelW = computeTextWidth(g, label, g.getFont());
      g.fillText(label, rect.x() + Math.max(8, (rect.w() - labelW) / 2.0), rect.y() + rect.h() * 0.62);
      drawTag(g, rect.x() + 4, rect.y() - 4, selected ? "Btn: " + button.id() + " (drag/resize)" : "Btn: " + button.id());
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
      double x = rect.x() + rect.w() * clamp01(point.x());
      double y = rect.y() + rect.h() * clamp01(point.y());
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
      double x = rect.x() + rect.w() * clamp01(point.x());
      double y = rect.y() + rect.h() * clamp01(point.y());
      if (i == 0) g.moveTo(x, y);
      else g.lineTo(x, y);
    }
    g.closePath();
    g.stroke();
  }

  private LayoutRects computeRects(VnUiLayoutSpec s, double w, double h) {
    double tbX = clamp(s.textBoxX() * w, 0, w);
    double tbY = clamp(s.textBoxY() * h, 0, h);
    double tbW = clamp(s.textBoxWidth() * w, 1, Math.max(1, w - tbX));
    double tbH = clamp(s.textBoxHeight() * h, 1, Math.max(1, h - tbY));

    double nbX = tbX + s.nameBoxXOffset();
    double nbY = tbY + s.nameBoxYOffset();
    double nbW = s.nameBoxWidth();
    double nbH = s.nameBoxHeight();

    double leftPad = s.dialogueTextHorizontalPadding();
    double topPad = s.dialogueTextTopPadding();
    double rightPad = s.dialogueTextRightPadding();
    double bottomPad = s.dialogueTextBottomPadding();
    double textX = tbX + leftPad;
    double textY = tbY + topPad;
    double textW = Math.max(40, tbW - leftPad - rightPad);
    double textH = Math.max(20, tbH - topPad - bottomPad);

    double choiceW = clamp(w * s.choiceWidthFactor(), 20, w);
    double choiceX = clamp(w * s.choiceXCenter() - choiceW / 2, 0, Math.max(0, w - choiceW));
    double totalChoiceH = 3 * s.choiceHeight() + 2 * s.choiceGap();
    double choiceStartY = resolveChoiceYStart(s, h, 3);
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

  private Rect computeTextBoxButtonRect(VnUiActionButtonSpec button, Rect textBoxRect) {
    if (button == null || textBoxRect == null) return new Rect(0, 0, 1, 1);
    double x = textBoxRect.x() + textBoxRect.w() * clamp01(button.x());
    double y = textBoxRect.y() + textBoxRect.h() * clamp01(button.y());
    double width = Math.max(8, textBoxRect.w() * clamp(button.width(), 0.01, 1.0));
    double height = Math.max(8, textBoxRect.h() * clamp(button.height(), 0.01, 1.0));
    return new Rect(x, y, width, height);
  }

  private int hitTestButtonIndex(double x, double y, Rect textBoxRect) {
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return -1;
    for (int i = textBoxButtons.size() - 1; i >= 0; i--) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null || !button.enabled()) continue;
      Rect rect = computeTextBoxButtonRect(button, textBoxRect);
      if (rect.contains(x, y)) return i;
    }
    return -1;
  }

  private int hitTestButtonResizeIndex(double x, double y, Rect textBoxRect) {
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return -1;
    for (int i = textBoxButtons.size() - 1; i >= 0; i--) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null || !button.enabled()) continue;
      Rect rect = computeTextBoxButtonRect(button, textBoxRect);
      if (isNearCorner(x, y, rect)) return i;
    }
    return -1;
  }

  private double resolveChoiceYStart(VnUiLayoutSpec s, double h, int count) {
    double total = count * s.choiceHeight() + Math.max(0, count - 1) * s.choiceGap();
    if (s.choiceYStart() < 0) return (h - total) / 2.0;
    return h * s.choiceYStart();
  }

  private VnUiLayoutSpec readSpecFromControls() {
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
        value(spDialoguePaddingX),
        value(spDialoguePaddingTop),
        value(spDialoguePaddingRight),
        value(spDialoguePaddingBottom),
        value(spChoiceXCenter),
        value(spChoiceYStart),
        value(spChoiceWidthFactor),
        value(spChoiceHeight),
        value(spChoiceGap),
        value(spChoiceTextXPadding)
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
  }

  private VnUiStyleSpec readStyleFromControls() {
    VnUiStyleSpec base = style == null ? VnUiStyleSpec.defaults() : style;
    return new VnUiStyleSpec(
        // Textbox
        normalizeAssetPath(tfTextBoxAsset.getText()),
        normalizeColorValue(tfTextBoxColor.getText()),
        chkTextBoxOverlayEnabled.isSelected() ? value(spTextBoxOverlayOpacity) : 0.0,
        base.textBoxBoundsPoints(),
        // Name box
        base.nameBoxAssetPath(),
        base.nameBoxColor(),
        base.nameTextColor(),
        base.nameTextFontFamily(),
        base.nameTextFontSize(),
        base.nameBoxBoundsPoints(),
        // Dialogue text
        base.dialogueTextColor(),
        base.dialogueTextFontFamily(),
        base.dialogueTextFontSize(),
        base.dialogueTextBoundsPoints(),
        // Choice button assets
        normalizeAssetPath(tfChoiceButtonAsset.getText()),
        normalizeAssetPath(tfChoiceButtonHoverAsset.getText()),
        normalizeAssetPath(tfChoiceButtonSelectedAsset.getText()),
        normalizeAssetPath(tfChoiceButtonDisabledAsset.getText()),
        base.choiceButtonBoundsPoints(),
        // Choice colors
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
        // Choice geometry
        value(spChoiceCornerRadius),
        value(spChoiceBorderWidth),
        value(spChoiceTextBaselineOffset),
        // Choice font
        base.choiceFontFamily(),
        base.choiceFontSize(),
        base.characterHeightFactor(),
        base.characterBaselineY()
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
        source.height()
    );
    int insertIndex = Math.min(selectedButtonIndex + 1, textBoxButtons.size());
    textBoxButtons.add(insertIndex, duplicate);
    refreshTextBoxButtonList();
    setSelectedTextBoxButton(insertIndex);
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

    tool.setBounds(List.of(new BoundsDrawingTool.BoundEntry(
        id,
        label,
        0.0,
        0.0,
        1.0,
        1.0,
        BoundsPointCodec.parse(boundsPoints)
    )));

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
      loadTextBoxAssetImage();
      loadChoiceAssetImages();
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

  private VnUiStyleSpec withBoundsPoints(
      String textBoxBoundsPoints,
      String nameBoxBoundsPoints,
      String dialogueTextBoundsPoints,
      String choiceButtonBoundsPoints
  ) {
    VnUiStyleSpec base = style == null ? VnUiStyleSpec.defaults() : style;
    return new VnUiStyleSpec(
        base.textBoxAssetPath(),
        base.textBoxColor(),
        base.textBoxOpacity(),
        textBoxBoundsPoints,
        base.nameBoxAssetPath(),
        base.nameBoxColor(),
        base.nameTextColor(),
        base.nameTextFontFamily(),
        base.nameTextFontSize(),
        nameBoxBoundsPoints,
        base.dialogueTextColor(),
        base.dialogueTextFontFamily(),
        base.dialogueTextFontSize(),
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
        base.choiceFontFamily(),
        base.choiceFontSize(),
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
              match.getX(), match.getY(), match.getW(), match.getH()
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
        value(spButtonHeight)
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
    List<String> warnings = new ArrayList<>();
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

    if (warnings.isEmpty()) {
      validation.setText("No issues detected.");
      validation.setTextFill(LayoutStudioPalette.TEXT_SUCCESS);
    } else {
      validation.setText(String.join(" | ", warnings));
      validation.setTextFill(LayoutStudioPalette.TEXT_WARNING);
    }
  }

  private void warnMissingAsset(List<String> warnings, String field, String assetPath) {
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
    out.append("# Dialogue UI layout").append(System.lineSeparator());
    out.append("# choiceYStart: -1 = auto-center").append(System.lineSeparator());
    for (String key : KNOWN_KEYS) {
      String value = merged.getProperty(key);
      if (value == null) continue;
      out.append(key).append("=").append(value).append(System.lineSeparator());
    }
    if (textBoxButtons != null && !textBoxButtons.isEmpty()) {
      out.append(System.lineSeparator()).append("# Textbox action buttons").append(System.lineSeparator());
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
    Color bg = parseColorValue(style.choiceBackgroundColor(), LayoutStudioPalette.PANEL_FILL);
    Color hoverBg = parseColorValue(
        firstNonBlank(style.choiceHoverColor(), style.choiceSelectedColor()),
        LayoutStudioPalette.PANEL_FILL_SELECTED);
    Color disabledBg = parseColorValue(style.choiceDisabledColor(), LayoutStudioPalette.PANEL_FILL_DISABLED);

    Color text = parseColorValue(style.choiceTextColor(), LayoutStudioPalette.TEXT_PRIMARY);
    Color hoverText = parseColorValue(
        firstNonBlank(style.choiceHoverTextColor(), style.choiceSelectedTextColor()),
        text);
    Color disabledText = parseColorValue(style.choiceDisabledTextColor(), LayoutStudioPalette.TEXT_DISABLED);

    Color border = parseColorValue(style.choiceBorderColor(), LayoutStudioPalette.PANEL_BORDER_LIGHT);
    Color hoverBorder = parseColorValue(
        firstNonBlank(style.choiceHoverBorderColor(), style.choiceSelectedBorderColor()),
        border);
    Color disabledBorder = parseColorValue(style.choiceDisabledBorderColor(), LayoutStudioPalette.PANEL_BORDER);

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

  private void loadTextBoxAssetImage() {
    textBoxAssetImage = loadImageAsset(style.textBoxAssetPath());
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

  private static String normalizeText(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
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
