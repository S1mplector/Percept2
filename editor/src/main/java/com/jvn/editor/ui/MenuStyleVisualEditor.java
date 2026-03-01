package com.jvn.editor.ui;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Visual editor for menu style files (*.style).
 * Keeps properties text synchronized while exposing button skin controls.
 */
public class MenuStyleVisualEditor extends BorderPane {
  private static final String[] KNOWN_KEYS = new String[] {
      "itemColor", "itemSelectedColor", "itemHoverColor", "itemDisabledColor",
      "itemPrefix", "itemSelectedPrefix", "itemDisabledPrefix",
      "itemFontFamily", "itemFontWeight", "itemFontSize",
      "itemShadowColor", "itemShadowOffsetX", "itemShadowOffsetY", "itemOpacity",
      "buttonAsset", "buttonSelectedAsset", "buttonHoverAsset", "buttonDisabledAsset",
      "buttonTextPaddingX", "buttonTextPaddingY",
      "titleColor", "titleFontFamily", "titleFontWeight", "titleFontSize", "titleShadowColor",
      "hintsColor", "hintsFontFamily", "hintsFontSize",
      "backgroundAsset", "backgroundColor", "backgroundOpacity"
  };

  private final Canvas preview = new Canvas(860, 350);
  private StackPane previewPaneHost;
  private final TextField tfItemColor = new TextField("#D3D3D3");
  private final TextField tfItemSelectedColor = new TextField("#FFFF00");
  private final TextField tfItemHoverColor = new TextField();
  private final TextField tfItemDisabledColor = new TextField("#808080");
  private final TextField tfItemPrefix = new TextField("  ");
  private final TextField tfItemSelectedPrefix = new TextField("> ");
  private final TextField tfItemDisabledPrefix = new TextField("- ");
  private final ComboBox<String> cbItemFontFamily = new ComboBox<>();
  private final ChoiceBox<String> cbItemFontWeight = new ChoiceBox<>();
  private final Spinner<Integer> spItemFontSize = intSpinner(8, 96, 20, 1);
  private final TextField tfItemShadowColor = new TextField();
  private final Spinner<Double> spItemShadowOffsetX = doubleSpinner(-20, 20, 0, 0.5);
  private final Spinner<Double> spItemShadowOffsetY = doubleSpinner(-20, 20, 0, 0.5);
  private final Spinner<Double> spItemOpacity = doubleSpinner(0, 1, 1, 0.05);

  private final TextField tfButtonAsset = new TextField();
  private final TextField tfButtonSelectedAsset = new TextField();
  private final TextField tfButtonHoverAsset = new TextField();
  private final TextField tfButtonDisabledAsset = new TextField();
  private final Spinner<Double> spButtonTextPaddingX = doubleSpinner(0, 240, 18, 1);
  private final Spinner<Double> spButtonTextPaddingY = doubleSpinner(-120, 120, 0, 1);

  private final TextField tfTitleColor = new TextField();
  private final ComboBox<String> cbTitleFontFamily = new ComboBox<>();
  private final ChoiceBox<String> cbTitleFontWeight = new ChoiceBox<>();
  private final Spinner<Integer> spTitleFontSize = intSpinner(8, 120, 36, 1);
  private final TextField tfTitleShadowColor = new TextField();

  private final TextField tfHintsColor = new TextField();
  private final ComboBox<String> cbHintsFontFamily = new ComboBox<>();
  private final Spinner<Integer> spHintsFontSize = intSpinner(8, 48, 14, 1);

  private final TextField tfBackgroundAsset = new TextField();
  private final TextField tfBackgroundColor = new TextField();
  private final Spinner<Double> spBackgroundOpacity = doubleSpinner(0, 1, 1, 0.05);

  private final Properties rawProperties = new Properties();
  private Consumer<String> onStyleTextChanged;
  private boolean suppressEvents;
  private String lastLoadedText = "";
  private String lastEmittedText = "";
  private File projectRoot;

  private Image buttonAssetImage;
  private Image buttonSelectedAssetImage;
  private Image buttonHoverAssetImage;
  private Image buttonDisabledAssetImage;
  private final UndoManager undoManager = new UndoManager();
  private Button btnUndo;
  private Button btnRedo;
  private boolean applyingHistory = false;
  private final Label validation = new Label("No issues detected.");
  private final TableView<CustomProperty> customPropsTable = new TableView<>();
  private final ObservableList<CustomProperty> customProps = FXCollections.observableArrayList();
  private List<String> previewItems = List.of("> New Game", "  Load", "  Settings", "  Quit");

  public MenuStyleVisualEditor() {
    setPadding(new Insets(8));
    cbItemFontWeight.getItems().setAll("NORMAL", "BOLD");
    cbItemFontWeight.setValue("NORMAL");
    cbTitleFontWeight.getItems().setAll("NORMAL", "BOLD");
    cbTitleFontWeight.setValue("BOLD");
    initFontPicker();
    // Init title/hints font pickers with same font list
    List<String> families = Font.getFamilies();
    cbTitleFontFamily.getItems().setAll(families);
    cbTitleFontFamily.setValue(Font.getDefault().getFamily());
    cbTitleFontFamily.setEditable(true);
    cbHintsFontFamily.getItems().setAll(families);
    cbHintsFontFamily.setValue(Font.getDefault().getFamily());
    cbHintsFontFamily.setEditable(true);
    tfItemHoverColor.setPromptText("#ffe066");
    tfItemShadowColor.setPromptText("#00000088");
    tfTitleColor.setPromptText("#ffffff");
    tfTitleShadowColor.setPromptText("#000000");
    tfHintsColor.setPromptText("#aaaaaa");
    tfBackgroundAsset.setPromptText("assets/ui/menu/bg.png");
    tfBackgroundColor.setPromptText("#1a1a2e");
    tfButtonHoverAsset.setPromptText("assets/ui/menu/button_hover.png");

    preview.setManaged(false);
    previewPaneHost = new StackPane(preview);
    StackPane.setAlignment(preview, Pos.TOP_LEFT);
    previewPaneHost.getStyleClass().add("layout-studio-preview-host");
    previewPaneHost.setPadding(new Insets(8));
    setCenter(previewPaneHost);

    VBox controlsContent = new VBox(8, buildControls(), buildCustomPropertiesSection());
    ScrollPane controls = new ScrollPane(controlsContent);
    controls.setFitToWidth(true);
    controls.setPrefWidth(360);
    controls.getStyleClass().add("layout-studio-controls-pane");
    setRight(controls);

    previewPaneHost.widthProperty().addListener((o, ov, nv) -> updatePreviewSize(previewPaneHost));
    previewPaneHost.heightProperty().addListener((o, ov, nv) -> updatePreviewSize(previewPaneHost));
    preview.widthProperty().addListener((o, ov, nv) -> redrawPreview());
    preview.heightProperty().addListener((o, ov, nv) -> redrawPreview());

    registerListeners();
    updatePreviewSize(previewPaneHost);
    refreshValidation();
    redrawPreview();
  }

  private void initFontPicker() {
    List<String> families = Font.getFamilies();
    cbItemFontFamily.getItems().setAll(families);
    cbItemFontFamily.setValue(Font.getDefault().getFamily());
    cbItemFontFamily.setEditable(true);
    cbItemFontFamily.setCellFactory(lv -> new ListCell<>() {
      @Override
      protected void updateItem(String fontName, boolean empty) {
        super.updateItem(fontName, empty);
        if (empty || fontName == null) {
          setText(null);
          setStyle("");
        } else {
          setText(fontName);
          setStyle("-fx-font-family: '" + fontName + "';");
        }
      }
    });
    cbItemFontFamily.setButtonCell(new ListCell<>() {
      @Override
      protected void updateItem(String fontName, boolean empty) {
        super.updateItem(fontName, empty);
        if (empty || fontName == null) {
          setText(Font.getDefault().getFamily());
        } else {
          setText(fontName);
          setStyle("-fx-font-family: '" + fontName + "';");
        }
      }
    });
  }

  public void setOnStyleTextChanged(Consumer<String> onStyleTextChanged) {
    this.onStyleTextChanged = onStyleTextChanged;
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
    updatePreviewSize(previewPaneHost);
    loadButtonAssets();
    refreshValidation();
    redrawPreview();
  }

  public void setStyleText(String text) {
    String normalized = normalizeText(text);
    if (normalized.equals(lastLoadedText)) return;
    suppressEvents = true;
    rawProperties.clear();
    try {
      if (text != null && !text.isBlank()) rawProperties.load(new StringReader(text));
    } catch (Exception ignored) {
    }

    tfItemColor.setText(rawProperties.getProperty("itemColor", tfItemColor.getText()));
    tfItemSelectedColor.setText(rawProperties.getProperty("itemSelectedColor", tfItemSelectedColor.getText()));
    tfItemDisabledColor.setText(rawProperties.getProperty("itemDisabledColor", tfItemDisabledColor.getText()));
    tfItemPrefix.setText(rawProperties.getProperty("itemPrefix", tfItemPrefix.getText()));
    tfItemSelectedPrefix.setText(rawProperties.getProperty("itemSelectedPrefix", tfItemSelectedPrefix.getText()));
    tfItemDisabledPrefix.setText(rawProperties.getProperty("itemDisabledPrefix", tfItemDisabledPrefix.getText()));
    cbItemFontFamily.setValue(rawProperties.getProperty("itemFontFamily", cbItemFontFamily.getValue()));
    cbItemFontWeight.setValue(rawProperties.getProperty("itemFontWeight", cbItemFontWeight.getValue()));
    try {
      spItemFontSize.getValueFactory().setValue(Integer.parseInt(rawProperties.getProperty("itemFontSize", Integer.toString(spItemFontSize.getValue()))));
    } catch (Exception ignored) {
    }

    // Hover color
    tfItemHoverColor.setText(rawProperties.getProperty("itemHoverColor", ""));
    // Shadow / opacity
    tfItemShadowColor.setText(rawProperties.getProperty("itemShadowColor", ""));
    setSpinnerValue(spItemShadowOffsetX, parseDouble(rawProperties.getProperty("itemShadowOffsetX"), 0));
    setSpinnerValue(spItemShadowOffsetY, parseDouble(rawProperties.getProperty("itemShadowOffsetY"), 0));
    setSpinnerValue(spItemOpacity, parseDouble(rawProperties.getProperty("itemOpacity"), 1.0));

    // Button skins
    tfButtonAsset.setText(rawProperties.getProperty("buttonAsset", ""));
    tfButtonSelectedAsset.setText(rawProperties.getProperty("buttonSelectedAsset", ""));
    tfButtonHoverAsset.setText(rawProperties.getProperty("buttonHoverAsset", ""));
    tfButtonDisabledAsset.setText(rawProperties.getProperty("buttonDisabledAsset", ""));
    setSpinnerValue(spButtonTextPaddingX, parseDouble(rawProperties.getProperty("buttonTextPaddingX"), spButtonTextPaddingX.getValue()));
    setSpinnerValue(spButtonTextPaddingY, parseDouble(rawProperties.getProperty("buttonTextPaddingY"), spButtonTextPaddingY.getValue()));

    // Title styling
    tfTitleColor.setText(rawProperties.getProperty("titleColor", ""));
    cbTitleFontFamily.setValue(rawProperties.getProperty("titleFontFamily", cbTitleFontFamily.getValue()));
    cbTitleFontWeight.setValue(rawProperties.getProperty("titleFontWeight", cbTitleFontWeight.getValue()));
    try { spTitleFontSize.getValueFactory().setValue(Integer.parseInt(rawProperties.getProperty("titleFontSize", Integer.toString(spTitleFontSize.getValue())))); } catch (Exception ignored) {}
    tfTitleShadowColor.setText(rawProperties.getProperty("titleShadowColor", ""));

    // Hints styling
    tfHintsColor.setText(rawProperties.getProperty("hintsColor", ""));
    cbHintsFontFamily.setValue(rawProperties.getProperty("hintsFontFamily", cbHintsFontFamily.getValue()));
    try { spHintsFontSize.getValueFactory().setValue(Integer.parseInt(rawProperties.getProperty("hintsFontSize", Integer.toString(spHintsFontSize.getValue())))); } catch (Exception ignored) {}

    // Background
    tfBackgroundAsset.setText(rawProperties.getProperty("backgroundAsset", ""));
    tfBackgroundColor.setText(rawProperties.getProperty("backgroundColor", ""));
    setSpinnerValue(spBackgroundOpacity, parseDouble(rawProperties.getProperty("backgroundOpacity"), 1.0));

    customProps.clear();
    for (String key : rawProperties.stringPropertyNames()) {
      if (isKnownKey(key)) continue;
      customProps.add(new CustomProperty(key, rawProperties.getProperty(key, "")));
    }
    suppressEvents = false;
    loadButtonAssets();
    refreshValidation();
    redrawPreview();
    lastLoadedText = normalized;
    String serialized = serialize();
    lastEmittedText = normalizeText(serialized);
    if (!applyingHistory) {
      undoManager.setInitialState(serialized);
    }
  }

  public String getStyleText() {
    return serialize();
  }

  public void setPreviewContent(List<String> items) {
    this.previewItems = items != null && !items.isEmpty() ? items : List.of("> New Game", "  Load", "  Settings", "  Quit");
    redrawPreview();
  }

  private GridPane buildControls() {
    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);
    grid.setPadding(new Insets(8));

    int row = 0;
    row = addHeader(grid, row, "Text Style");
    row = addRow(grid, row, "Item Color", ColorFieldHelper.create(tfItemColor));
    row = addRow(grid, row, "Selected Color", ColorFieldHelper.create(tfItemSelectedColor));
    row = addRow(grid, row, "Hover Color", ColorFieldHelper.create(tfItemHoverColor));
    row = addRow(grid, row, "Disabled Color", ColorFieldHelper.create(tfItemDisabledColor));
    row = addRow(grid, row, "Prefix", tfItemPrefix);
    row = addRow(grid, row, "Selected Prefix", tfItemSelectedPrefix);
    row = addRow(grid, row, "Disabled Prefix", tfItemDisabledPrefix);
    row = addRow(grid, row, "Font Family", cbItemFontFamily);
    row = addRow(grid, row, "Font Weight", cbItemFontWeight);
    row = addRow(grid, row, "Font Size", spItemFontSize);

    row = addHeader(grid, row, "Text Effects");
    row = addRow(grid, row, "Shadow Color", ColorFieldHelper.create(tfItemShadowColor));
    row = addRow(grid, row, "Shadow Offset X", spItemShadowOffsetX);
    row = addRow(grid, row, "Shadow Offset Y", spItemShadowOffsetY);
    row = addRow(grid, row, "Item Opacity", spItemOpacity);

    row = addHeader(grid, row, "Button Skins");
    row = addAssetRow(grid, row, "Button Asset", tfButtonAsset);
    row = addAssetRow(grid, row, "Selected Asset", tfButtonSelectedAsset);
    row = addAssetRow(grid, row, "Hover Asset", tfButtonHoverAsset);
    row = addAssetRow(grid, row, "Disabled Asset", tfButtonDisabledAsset);
    row = addRow(grid, row, "Text Padding X", spButtonTextPaddingX);
    row = addRow(grid, row, "Text Offset Y", spButtonTextPaddingY);

    row = addHeader(grid, row, "Title Styling");
    row = addRow(grid, row, "Title Color", ColorFieldHelper.create(tfTitleColor));
    row = addRow(grid, row, "Title Font", cbTitleFontFamily);
    row = addRow(grid, row, "Title Weight", cbTitleFontWeight);
    row = addRow(grid, row, "Title Size", spTitleFontSize);
    row = addRow(grid, row, "Title Shadow", ColorFieldHelper.create(tfTitleShadowColor));

    row = addHeader(grid, row, "Hints Styling");
    row = addRow(grid, row, "Hints Color", ColorFieldHelper.create(tfHintsColor));
    row = addRow(grid, row, "Hints Font", cbHintsFontFamily);
    row = addRow(grid, row, "Hints Size", spHintsFontSize);

    row = addHeader(grid, row, "Background");
    row = addAssetRow(grid, row, "Background Asset", tfBackgroundAsset);
    row = addRow(grid, row, "Background Color", ColorFieldHelper.create(tfBackgroundColor));
    row = addRow(grid, row, "Background Opacity", spBackgroundOpacity);

    Label hint = new Label("All fields serialize into .style properties.\nBackground, title, hints, and text effects enable full visual customization.");
    hint.getStyleClass().add("muted");
    hint.setWrapText(true);
    grid.add(hint, 0, row++, 2, 1);
    validation.getStyleClass().add("muted");
    validation.setWrapText(true);
    grid.add(validation, 0, row++, 2, 1);

    row = addHeader(grid, row, "History");
    btnUndo = iconButton(CssIcon.undo(), "Undo");
    btnRedo = iconButton(CssIcon.redo(), "Redo");
    btnUndo.setDisable(true);
    btnRedo.setDisable(true);
    btnUndo.setOnAction(e -> performUndo());
    btnRedo.setOnAction(e -> performRedo());
    undoManager.setOnUndoAvailableChanged(available -> btnUndo.setDisable(!available));
    undoManager.setOnRedoAvailableChanged(available -> btnRedo.setDisable(!available));
    HBox historyButtons = new HBox(8, btnUndo, btnRedo);
    grid.add(historyButtons, 0, row, 2, 1);

    UndoManager.installKeyboardShortcuts(this, this::performUndo, this::performRedo);

    return grid;
  }

  @SuppressWarnings("unchecked")
  private VBox buildCustomPropertiesSection() {
    Label header = new Label("Custom Properties");
    header.setStyle("-fx-font-weight: bold;");

    customPropsTable.setEditable(true);
    customPropsTable.setItems(customProps);
    customPropsTable.setPrefHeight(160);
    customPropsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

    TableColumn<CustomProperty, String> keyCol = new TableColumn<>("Key");
    keyCol.setCellValueFactory(v -> v.getValue().keyProperty());
    keyCol.setCellFactory(TextFieldTableCell.forTableColumn());
    keyCol.setOnEditCommit(e -> {
      e.getRowValue().setKey(e.getNewValue() != null ? e.getNewValue().trim() : "");
      onControlChanged();
    });

    TableColumn<CustomProperty, String> valCol = new TableColumn<>("Value");
    valCol.setCellValueFactory(v -> v.getValue().valueProperty());
    valCol.setCellFactory(TextFieldTableCell.forTableColumn());
    valCol.setOnEditCommit(e -> {
      e.getRowValue().setValue(e.getNewValue() != null ? e.getNewValue().trim() : "");
      onControlChanged();
    });

    customPropsTable.getColumns().setAll(keyCol, valCol);

    Button addBtn = iconButton(CssIcon.plus("#8cd48c"), "Add custom property");
    addBtn.setOnAction(e -> {
      customProps.add(new CustomProperty("custom_key", ""));
      onControlChanged();
    });
    Button removeBtn = iconButton(CssIcon.minus("#e07070"), "Remove selected property");
    removeBtn.setOnAction(e -> {
      int idx = customPropsTable.getSelectionModel().getSelectedIndex();
      if (idx >= 0 && idx < customProps.size()) {
        customProps.remove(idx);
        onControlChanged();
      }
    });
    HBox buttons = new HBox(8, addBtn, removeBtn);

    VBox section = new VBox(4, header, customPropsTable, buttons);
    section.setPadding(new Insets(8));
    return section;
  }

  private int addHeader(GridPane grid, int row, String title) {
    if (row > 0) {
      javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
      grid.add(sep, 0, row++, 2, 1);
    }
    Label label = new Label(title);
    label.setFont(Font.font(label.getFont().getFamily(), FontWeight.BOLD, 12));
    grid.add(label, 0, row++, 2, 1);
    return row;
  }

  private int addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
    Label l = new Label(label);
    GridPane.setHgrow(control, Priority.ALWAYS);
    if (control instanceof TextField tf) tf.setMaxWidth(Double.MAX_VALUE);
    if (control instanceof Spinner<?> sp) sp.setMaxWidth(Double.MAX_VALUE);
    if (control instanceof ChoiceBox<?> cb) cb.setMaxWidth(Double.MAX_VALUE);
    grid.add(l, 0, row);
    grid.add(control, 1, row);
    return row + 1;
  }

  private int addAssetRow(GridPane grid, int row, String label, TextField field) {
    AssetPickerSupport.installAssetDrop(field, this::toProjectRelativePath);
    Button browse = iconButton(CssIcon.folder(), "Browse assets");
    browse.setOnAction(e -> browseAsset(field));
    Button importBtn = iconButton(CssIcon.download("#8cd48c"), "Import external asset");
    importBtn.setOnAction(e -> {
      String imported = importAsset(field);
      if (imported != null && !imported.isBlank()) {
        field.setText(imported);
      }
    });
    Button reveal = iconButton(CssIcon.link("#9cc7ff"), "Reveal in file manager");
    reveal.setOnAction(e -> revealAsset(field.getText()));
    Button clear = iconButton(CssIcon.clearX("#e07070"), "Clear asset path");
    clear.setOnAction(e -> field.setText(""));
    HBox box = new HBox(6, field, browse, importBtn, reveal, clear);
    HBox.setHgrow(field, Priority.ALWAYS);
    return addRow(grid, row, label, box);
  }

  private static Button iconButton(javafx.scene.Node icon, String tooltip) {
    Button button = new Button();
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltip));
    button.getStyleClass().addAll("layout-studio-action-button", "layout-studio-icon-button");
    return button;
  }

  private void registerListeners() {
    List<TextField> textFields = List.of(
        tfItemColor, tfItemSelectedColor, tfItemHoverColor, tfItemDisabledColor,
        tfItemPrefix, tfItemSelectedPrefix, tfItemDisabledPrefix,
        tfItemShadowColor,
        tfButtonAsset, tfButtonSelectedAsset, tfButtonHoverAsset, tfButtonDisabledAsset,
        tfTitleColor, tfTitleShadowColor,
        tfHintsColor,
        tfBackgroundAsset, tfBackgroundColor
    );
    for (TextField tf : textFields) {
      tf.textProperty().addListener((o, ov, nv) -> onControlChanged());
    }
    cbItemFontFamily.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    cbItemFontWeight.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spItemFontSize.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spItemShadowOffsetX.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spItemShadowOffsetY.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spItemOpacity.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spButtonTextPaddingX.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spButtonTextPaddingY.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    cbTitleFontFamily.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    cbTitleFontWeight.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spTitleFontSize.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    cbHintsFontFamily.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spHintsFontSize.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spBackgroundOpacity.valueProperty().addListener((o, ov, nv) -> onControlChanged());
  }

  private void onControlChanged() {
    if (suppressEvents) return;
    loadButtonAssets();
    refreshValidation();
    redrawPreview();
    emitText();
  }

  private void refreshValidation() {
    List<String> warnings = new ArrayList<>();
    validateColor(warnings, "itemColor", tfItemColor.getText(), true);
    validateColor(warnings, "itemSelectedColor", tfItemSelectedColor.getText(), true);
    validateColor(warnings, "itemDisabledColor", tfItemDisabledColor.getText(), true);
    validateColor(warnings, "itemHoverColor", tfItemHoverColor.getText(), false);
    validateColor(warnings, "itemShadowColor", tfItemShadowColor.getText(), false);
    validateColor(warnings, "titleColor", tfTitleColor.getText(), false);
    validateColor(warnings, "titleShadowColor", tfTitleShadowColor.getText(), false);
    validateColor(warnings, "hintsColor", tfHintsColor.getText(), false);
    validateColor(warnings, "backgroundColor", tfBackgroundColor.getText(), false);
    validateAssetPath(warnings, "buttonAsset", tfButtonAsset.getText());
    validateAssetPath(warnings, "buttonSelectedAsset", tfButtonSelectedAsset.getText());
    validateAssetPath(warnings, "buttonHoverAsset", tfButtonHoverAsset.getText());
    validateAssetPath(warnings, "buttonDisabledAsset", tfButtonDisabledAsset.getText());
    validateAssetPath(warnings, "backgroundAsset", tfBackgroundAsset.getText());
    Set<String> seen = new LinkedHashSet<>();
    for (CustomProperty prop : customProps) {
      String key = prop.getKey();
      if (key == null || key.isBlank()) {
        warnings.add("Custom property key cannot be empty.");
        continue;
      }
      if (isKnownKey(key)) {
        warnings.add("Custom property key conflicts with built-in key: " + key);
        continue;
      }
      if (!seen.add(key)) {
        warnings.add("Duplicate custom property key: " + key);
      }
    }
    if (warnings.isEmpty()) {
      validation.setText("No issues detected.");
      validation.setTextFill(LayoutStudioPalette.TEXT_SUCCESS);
    } else {
      validation.setText(String.join(" | ", warnings));
      validation.setTextFill(LayoutStudioPalette.TEXT_WARNING);
    }
  }

  private void validateColor(List<String> warnings, String key, String value, boolean required) {
    String normalized = normalize(value, "");
    if (normalized.isBlank()) {
      if (required) {
        warnings.add(key + " is required.");
      }
      return;
    }
    try {
      Color.web(normalized);
    } catch (Exception ignored) {
      warnings.add(key + " is not a valid color: " + normalized);
    }
  }

  private void validateAssetPath(List<String> warnings, String key, String path) {
    String normalized = normalize(path, "");
    if (normalized.isBlank()) return;
    File file = resolveAssetFile(normalized);
    if (file == null || !file.exists() || !file.isFile()) {
      warnings.add(key + " not found: " + normalized);
    }
  }

  private void emitText() {
    if (onStyleTextChanged == null) return;
    String text = serialize();
    String normalized = normalizeText(text);
    if (normalized.equals(lastEmittedText)) return;
    undoManager.captureState(text);
    lastEmittedText = normalized;
    onStyleTextChanged.accept(text);
  }

  private void performUndo() {
    String previous = undoManager.undo();
    if (previous == null) return;
    applyingHistory = true;
    suppressEvents = true;
    try {
      setStyleText(previous);
    } finally {
      suppressEvents = false;
      applyingHistory = false;
    }
    if (onStyleTextChanged != null) onStyleTextChanged.accept(previous);
  }

  private void performRedo() {
    String next = undoManager.redo();
    if (next == null) return;
    applyingHistory = true;
    suppressEvents = true;
    try {
      setStyleText(next);
    } finally {
      suppressEvents = false;
      applyingHistory = false;
    }
    if (onStyleTextChanged != null) onStyleTextChanged.accept(next);
  }

  private String serialize() {
    Properties merged = new Properties();
    for (String key : rawProperties.stringPropertyNames()) merged.setProperty(key, rawProperties.getProperty(key));

    merged.setProperty("itemColor", normalize(tfItemColor.getText(), "#D3D3D3"));
    merged.setProperty("itemSelectedColor", normalize(tfItemSelectedColor.getText(), "#FFFF00"));
    merged.setProperty("itemDisabledColor", normalize(tfItemDisabledColor.getText(), "#808080"));
    merged.setProperty("itemPrefix", normalize(tfItemPrefix.getText(), "  "));
    merged.setProperty("itemSelectedPrefix", normalize(tfItemSelectedPrefix.getText(), "> "));
    merged.setProperty("itemDisabledPrefix", normalize(tfItemDisabledPrefix.getText(), "- "));
    merged.setProperty("itemFontFamily", normalize(cbItemFontFamily.getValue(), Font.getDefault().getFamily()));
    merged.setProperty("itemFontWeight", normalize(cbItemFontWeight.getValue(), "NORMAL"));
    merged.setProperty("itemFontSize", Integer.toString(spItemFontSize.getValue()));

    setOptionalProperty(merged, "itemHoverColor", tfItemHoverColor.getText());
    setOptionalProperty(merged, "itemShadowColor", tfItemShadowColor.getText());
    setOptionalProperty(merged, "itemShadowOffsetX", formatDouble(spItemShadowOffsetX.getValue()));
    setOptionalProperty(merged, "itemShadowOffsetY", formatDouble(spItemShadowOffsetY.getValue()));
    if (spItemOpacity.getValue() < 1.0) merged.setProperty("itemOpacity", formatDouble(spItemOpacity.getValue()));

    setOptionalProperty(merged, "buttonAsset", tfButtonAsset.getText());
    setOptionalProperty(merged, "buttonSelectedAsset", tfButtonSelectedAsset.getText());
    setOptionalProperty(merged, "buttonHoverAsset", tfButtonHoverAsset.getText());
    setOptionalProperty(merged, "buttonDisabledAsset", tfButtonDisabledAsset.getText());
    merged.setProperty("buttonTextPaddingX", formatDouble(spButtonTextPaddingX.getValue()));
    merged.setProperty("buttonTextPaddingY", formatDouble(spButtonTextPaddingY.getValue()));

    setOptionalProperty(merged, "titleColor", tfTitleColor.getText());
    setOptionalProperty(merged, "titleFontFamily", cbTitleFontFamily.getValue());
    setOptionalProperty(merged, "titleFontWeight", cbTitleFontWeight.getValue());
    merged.setProperty("titleFontSize", Integer.toString(spTitleFontSize.getValue()));
    setOptionalProperty(merged, "titleShadowColor", tfTitleShadowColor.getText());

    setOptionalProperty(merged, "hintsColor", tfHintsColor.getText());
    setOptionalProperty(merged, "hintsFontFamily", cbHintsFontFamily.getValue());
    merged.setProperty("hintsFontSize", Integer.toString(spHintsFontSize.getValue()));

    setOptionalProperty(merged, "backgroundAsset", tfBackgroundAsset.getText());
    setOptionalProperty(merged, "backgroundColor", tfBackgroundColor.getText());
    if (spBackgroundOpacity.getValue() < 1.0) merged.setProperty("backgroundOpacity", formatDouble(spBackgroundOpacity.getValue()));

    StringBuilder out = new StringBuilder();
    out.append("# Menu style").append(System.lineSeparator());
    out.append("# Reusable per-button visuals and typography").append(System.lineSeparator());
    for (String key : KNOWN_KEYS) {
      String value = merged.getProperty(key);
      if (value == null) continue;
      out.append(key).append("=").append(value).append(System.lineSeparator());
    }
    for (String key : new ArrayList<>(merged.stringPropertyNames())) {
      if (!isKnownKey(key)) merged.remove(key);
    }
    for (CustomProperty cp : customProps) {
      String k = cp.getKey();
      if (k != null && !k.isBlank() && !isKnownKey(k)) {
        merged.setProperty(k, cp.getValue());
      }
    }
    List<String> extras = new ArrayList<>();
    for (String key : merged.stringPropertyNames()) {
      if (isKnownKey(key)) continue;
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

  private void redrawPreview() {
    GraphicsContext g = preview.getGraphicsContext2D();
    double w = Math.max(1, preview.getWidth());
    double h = Math.max(1, preview.getHeight());
    g.setFill(LayoutStudioPalette.CANVAS_BACKGROUND);
    g.fillRect(0, 0, w, h);

    g.setFill(LayoutStudioPalette.TEXT_PRIMARY);
    g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 20));
    g.fillText("Menu Style Preview", 20, 34);

    double buttonW = Math.min(560, w - 80);
    double buttonH = 58;
    double x = (w - buttonW) / 2.0;
    double y = 80;
    drawPreviewButton(g, x, y, buttonW, buttonH, "  Hovered Option", false, false, true);
    drawPreviewButton(g, x, y + 78, buttonW, buttonH, "> Selected Option", true, false, false);
    drawPreviewButton(g, x, y + 156, buttonW, buttonH, "- Locked Option", false, true, false);
  }

  private void drawPreviewButton(GraphicsContext g,
                                 double x,
                                 double y,
                                 double w,
                                 double h,
                                 String text,
                                 boolean selected,
                                 boolean disabled,
                                 boolean hovered) {
    Image bg;
    if (disabled) {
      bg = buttonDisabledAssetImage;
    } else if (selected) {
      bg = buttonSelectedAssetImage;
    } else if (hovered) {
      bg = buttonHoverAssetImage != null ? buttonHoverAssetImage : buttonAssetImage;
    } else {
      bg = buttonAssetImage;
    }
    if (bg != null && !bg.isError()) {
      g.drawImage(bg, x, y, w, h);
    } else {
      Color fill = disabled
          ? LayoutStudioPalette.PANEL_FILL_DISABLED
          : (selected
              ? LayoutStudioPalette.PANEL_FILL_SELECTED
              : (hovered ? LayoutStudioPalette.PANEL_FILL_SELECTED : LayoutStudioPalette.PANEL_FILL));
      g.setFill(fill);
      g.fillRoundRect(x, y, w, h, 10, 10);
      g.setStroke(selected ? LayoutStudioPalette.PANEL_BORDER_SELECTED : LayoutStudioPalette.PANEL_BORDER);
      g.setLineWidth(selected ? 2.0 : 1.0);
      g.strokeRoundRect(x, y, w, h, 10, 10);
    }

    Color textColor;
    if (disabled) {
      textColor = parseColor(tfItemDisabledColor.getText(), LayoutStudioPalette.ITEM_COLOR_DISABLED);
    } else if (selected) {
      textColor = parseColor(tfItemSelectedColor.getText(), LayoutStudioPalette.ITEM_COLOR_SELECTED);
    } else if (hovered) {
      textColor = parseColor(tfItemHoverColor.getText(), LayoutStudioPalette.ITEM_COLOR_SELECTED);
    } else {
      textColor = parseColor(tfItemColor.getText(), LayoutStudioPalette.ITEM_COLOR_DEFAULT);
    }
    Font font = resolvePreviewFont();
    g.setFill(textColor);
    g.setFont(font);
    double tw = textWidth(g, text);
    double padX = spButtonTextPaddingX.getValue();
    double offsetY = spButtonTextPaddingY.getValue();
    double textX = x + Math.max(padX, (w - tw) / 2.0);
    if (textX + tw > x + w - 8) textX = x + w - tw - 8;
    g.fillText(text, textX, y + h * 0.58 + offsetY);
  }

  private Font resolvePreviewFont() {
    String family = normalize(cbItemFontFamily.getValue(), Font.getDefault().getFamily());
    FontWeight weight = "BOLD".equalsIgnoreCase(normalize(cbItemFontWeight.getValue(), "NORMAL"))
        ? FontWeight.BOLD : FontWeight.NORMAL;
    return Font.font(family, weight, spItemFontSize.getValue());
  }

  private void browseAsset(TextField targetField) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Select Asset");
    AssetPickerSupport.addAssetFilters(chooser);
    File initial = resolveInitialAssetDirectory();
    if (initial != null && initial.exists() && initial.isDirectory()) chooser.setInitialDirectory(initial);
    Window owner = getScene() != null ? getScene().getWindow() : null;
    File selected = chooser.showOpenDialog(owner);
    if (selected == null) return;
    targetField.setText(toProjectRelativePath(selected));
  }

  private String importAsset(TextField targetField) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Import Asset");
    AssetPickerSupport.addAssetFilters(chooser);
    File initial = resolveInitialAssetDirectory();
    if (initial != null && initial.exists() && initial.isDirectory()) chooser.setInitialDirectory(initial);
    Window owner = getScene() != null ? getScene().getWindow() : null;
    File selected = chooser.showOpenDialog(owner);
    if (selected == null) return null;
    if (projectRoot == null) return toProjectRelativePath(selected);
    try {
      File targetDir = new File(projectRoot, "assets/ui");
      if (!targetDir.exists()) targetDir.mkdirs();
      File dest = new File(targetDir, selected.getName());
      if (dest.exists()) {
        String stem = selected.getName();
        String ext = "";
        int dot = stem.lastIndexOf('.');
        if (dot > 0) {
          ext = stem.substring(dot);
          stem = stem.substring(0, dot);
        }
        int idx = 1;
        while (dest.exists()) {
          dest = new File(targetDir, stem + "_" + idx + ext);
          idx++;
        }
      }
      java.nio.file.Files.copy(selected.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return toProjectRelativePath(dest);
    } catch (Exception ignored) {
      return targetField != null ? normalize(targetField.getText(), "") : null;
    }
  }

  private void revealAsset(String path) {
    File asset = resolveAssetFile(path);
    AssetPickerSupport.revealFile(asset);
  }

  private void loadButtonAssets() {
    buttonAssetImage = loadImage(tfButtonAsset.getText());
    buttonSelectedAssetImage = loadImage(tfButtonSelectedAsset.getText());
    buttonHoverAssetImage = loadImage(tfButtonHoverAsset.getText());
    buttonDisabledAssetImage = loadImage(tfButtonDisabledAsset.getText());
  }

  private Image loadImage(String path) {
    File asset = resolveAssetFile(path);
    if (asset == null || !asset.exists() || !asset.isFile()) return null;
    try {
      Image image = new Image(asset.toURI().toString(), false);
      return image.isError() ? null : image;
    } catch (Exception ignored) {
      return null;
    }
  }

  private File resolveInitialAssetDirectory() {
    if (projectRoot != null) {
      File ui = new File(projectRoot, "assets/ui");
      if (ui.exists() && ui.isDirectory()) return ui;
      if (projectRoot.isDirectory()) return projectRoot;
    }
    return new File(System.getProperty("user.home", "."));
  }

  private File resolveAssetFile(String path) {
    String value = normalize(path, "");
    if (value.isBlank()) return null;
    File direct = new File(value);
    if (direct.isAbsolute()) return direct;
    if (projectRoot != null) return new File(projectRoot, value);
    return direct;
  }

  private String toProjectRelativePath(File file) {
    if (file == null) return "";
    if (projectRoot == null) return file.getAbsolutePath().replace('\\', '/');
    try {
      Path root = projectRoot.toPath().toAbsolutePath().normalize();
      Path abs = file.toPath().toAbsolutePath().normalize();
      if (abs.startsWith(root)) {
        return root.relativize(abs).toString().replace('\\', '/');
      }
    } catch (Exception ignored) {
    }
    return file.getAbsolutePath().replace('\\', '/');
  }

  private static boolean isKnownKey(String key) {
    for (String known : KNOWN_KEYS) {
      if (known.equals(key)) return true;
    }
    return false;
  }

  private static void setOptionalProperty(Properties properties, String key, String value) {
    String normalized = normalize(value, "");
    if (normalized.isBlank()) properties.remove(key);
    else properties.setProperty(key, normalized);
  }

  private static Spinner<Integer> intSpinner(int min, int max, int initial, int step) {
    Spinner<Integer> spinner = new Spinner<>();
    spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial, step));
    spinner.setEditable(true);
    return spinner;
  }

  private static Spinner<Double> doubleSpinner(double min, double max, double initial, double step) {
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

  private static void setSpinnerValue(Spinner<Double> spinner, double value) {
    SpinnerValueFactory<Double> vf = spinner.getValueFactory();
    if (vf instanceof SpinnerValueFactory.DoubleSpinnerValueFactory dsvf) dsvf.setValue(value);
    else vf.setValue(value);
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String t = value.trim();
    return t.isBlank() ? fallback : t;
  }

  private static double parseDouble(String raw, double fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static String formatDouble(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static String normalizeText(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static Color parseColor(String raw, Color fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Color.web(raw.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static double textWidth(GraphicsContext g, String text) {
    javafx.scene.text.Text helper = new javafx.scene.text.Text(text);
    helper.setFont(g.getFont());
    return helper.getLayoutBounds().getWidth();
  }

  private void updatePreviewSize(StackPane previewPane) {
    double availableW = sanitizeCanvasDimension(previewPane.getWidth() - 16.0);
    double availableH = sanitizeCanvasDimension(previewPane.getHeight() - 16.0);
    double aspect = ProjectViewportSpec.resolve(projectRoot).aspect();
    double w = availableW;
    double h = w / Math.max(0.0001, aspect);
    if (h > availableH) {
      h = availableH;
      w = h * aspect;
    }
    if (Math.abs(preview.getWidth() - w) >= 0.5) preview.setWidth(w);
    if (Math.abs(preview.getHeight() - h) >= 0.5) preview.setHeight(h);
    double x = 8.0 + (availableW - w) * 0.5;
    double y = 8.0 + (availableH - h) * 0.5;
    if (Math.abs(preview.getLayoutX() - x) >= 0.5) preview.setLayoutX(x);
    if (Math.abs(preview.getLayoutY() - y) >= 0.5) preview.setLayoutY(y);
  }

  private static double sanitizeCanvasDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return Math.max(1.0, Math.min(8192.0, value));
  }

  static final class CustomProperty {
    private final StringProperty key = new SimpleStringProperty("");
    private final StringProperty value = new SimpleStringProperty("");

    CustomProperty(String key, String value) {
      setKey(key);
      setValue(value);
    }

    String getKey() { return key.get(); }
    void setKey(String v) { key.set(v == null ? "" : v.trim()); }
    StringProperty keyProperty() { return key; }

    String getValue() { return value.get(); }
    void setValue(String v) { value.set(v == null ? "" : v.trim()); }
    StringProperty valueProperty() { return value; }
  }
}
