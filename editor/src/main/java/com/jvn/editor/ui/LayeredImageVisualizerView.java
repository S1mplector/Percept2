package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiStyleSpec;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;

/**
 * Sidebar utility for layered sprite exploration and snippet generation.
 *
 * Hardening goals:
 * - per-set persistence (selected layers, crop/focus/zoom, ids)
 * - save/load/delete presets
 * - active-group randomization and manual render-order controls
 * - multiple snippet export formats
 */
public class LayeredImageVisualizerView extends BorderPane {
  private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");
  private static final String NONE_LABEL = "(none)";
  private static final String STATE_FILE = ".jvn/layered-image-visualizer.properties";
  private static final double DEFAULT_CHARACTER_HEIGHT_FACTOR = 0.85;
  private static final double DEFAULT_CHARACTER_BASELINE_Y = 1.0;
  private static final Map<String, String> GROUP_TOKEN_ALIASES = Map.ofEntries(
      Map.entry("eye", "eyes"),
      Map.entry("eyes", "eyes"),
      Map.entry("mouth", "mouth"),
      Map.entry("lip", "mouth"),
      Map.entry("lips", "mouth"),
      Map.entry("brow", "brow"),
      Map.entry("eyebrow", "brow"),
      Map.entry("eyebrows", "brow"),
      Map.entry("base", "base"),
      Map.entry("body", "body"),
      Map.entry("hair", "hair"),
      Map.entry("face", "face"),
      Map.entry("outfit", "outfit"),
      Map.entry("clothes", "outfit"),
      Map.entry("accessory", "accessory"),
      Map.entry("accessories", "accessory"),
      Map.entry("acc", "accessory")
  );

  private static final String SNIPPET_COMBINED = "@charimg + [show]";
  private static final String SNIPPET_CHARIMG = "@charimg only";
  private static final String SNIPPET_SHOW = "[show] only";
  private static final String SNIPPET_RECIPE = "Recipe comments";
  private static final String DEFAULT_SHORTFORMS = String.join("\n",
      "# Example:",
      "# happy = eyes=neutral mouth=happy",
      "# serious = eyes=cross_closed mouth=neutral");
  private static final String TUTORIAL_TEXT = String.join("\n",
      "Image Attributes Tool (JVN)",
      "",
      "1) Pick a layer set and image tag.",
      "2) In Attributes, each row is a conflict group; one option per group.",
      "3) Use filter to find attributes quickly.",
      "4) Mark swap groups and use swap buttons for rapid eye/mouth cycling.",
      "5) Type attributes in Typed tab (examples: eyes=angry mouth=smile, eyes_angry).",
      "6) Define shortforms in Shortforms tab (name = attributes...), then apply instantly.",
      "7) Copy export strings with show+tag+attributes or attributes-only.");

  private final Label summaryLabel = new Label("Open a project to inspect layered image sets.");
  private final Label statusLabel = new Label("");
  private final Label previewInfoLabel = new Label("No layers selected.");

  private final TextField filterField = new TextField();
  private final ComboBox<String> setBox = new ComboBox<>();
  private final TextField characterIdField = new TextField();
  private final TextField expressionField = new TextField();
  private final CheckBox autoExpression = new CheckBox("Auto expression from selected layers");

  private final ComboBox<String> presetBox = new ComboBox<>();
  private final TextField presetNameField = new TextField();

  private final ComboBox<String> snippetFormatBox = new ComboBox<>();
  private final CheckBox randomizeActiveOnly = new CheckBox("Randomize active groups only");
  private final CheckBox matchGameFraming = new CheckBox("Match game framing");
  private final TextField attributeFilterField = new TextField();
  private final TextField typedAttributesField = new TextField();
  private final CheckBox typedRealtime = new CheckBox("Realtime preview");
  private final TextArea shortformsArea = new TextArea(DEFAULT_SHORTFORMS);
  private final ComboBox<String> shortformBox = new ComboBox<>();
  private final TextArea tutorialArea = new TextArea(TUTORIAL_TEXT);

  private final Canvas previewCanvas = new Canvas(320, 250);
  private final Slider focusXSlider = slider(0, 100, 50);
  private final Slider focusYSlider = slider(0, 100, 50);
  private final Slider cropSlider = slider(20, 100, 100);
  private final Slider zoomSlider = slider(50, 300, 100);

  private final VBox groupBox = new VBox(8);

  private final Map<String, ComboBox<LayerOption>> selectors = new LinkedHashMap<>();
  private final Map<String, CheckBox> activeGroupChecks = new LinkedHashMap<>();
  private final Map<String, CheckBox> swapGroupChecks = new LinkedHashMap<>();
  private final Map<String, HBox> groupRows = new LinkedHashMap<>();
  private final Map<String, String> shortforms = new LinkedHashMap<>();
  private final List<String> groupOrder = new ArrayList<>();
  private final Map<String, LayeredSet> sets = new LinkedHashMap<>();
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Map<String, String> presetNameToKey = new LinkedHashMap<>();
  private final Map<Canvas, ViewportFrame> viewportFrames = new IdentityHashMap<>();

  private final Properties persisted = new Properties();
  private final Random random = new Random();

  private File projectRoot;
  private String currentSetId;
  private String preferredSetId;
  private boolean applyingState;
  private Button fullscreenButton;
  private Runnable fullscreenToggleHandler;
  private boolean fullscreenActive;
  private Canvas dragCanvas;
  private double dragLastX;
  private double dragLastY;
  private boolean dragDirty;
  private double gameCharacterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
  private double gameCharacterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;

  public LayeredImageVisualizerView() {
    setPadding(new Insets(8));

    Label title = new Label("Image Attributes Tool");
    title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

    filterField.setPromptText("Filter sets...");
    filterField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshSetOptions();
    });
    filterField.setOnAction(e -> persistGlobalState());
    filterField.focusedProperty().addListener((o, ov, nv) -> {
      if (!nv) persistGlobalState();
    });

    setBox.setPromptText("Select layered set");
    setBox.setConverter(new StringConverter<>() {
      @Override public String toString(String object) { return object == null ? "" : object; }
      @Override public String fromString(String string) { return string; }
    });
    setBox.setOnAction(e -> onSetSelectionChanged());

    Button refreshButton = iconButton(CssIcon.redo("#7ec8e3"), "Refresh set scan", this::refreshCatalog);

    HBox setRow = new HBox(8, new Label("Set"), setBox, refreshButton);
    setRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(setBox, Priority.ALWAYS);

    HBox filterRow = new HBox(8, new Label("Filter"), filterField);
    filterRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(filterField, Priority.ALWAYS);

    // Presets toolbar
    presetBox.setPromptText("Preset");
    HBox.setHgrow(presetBox, Priority.ALWAYS);

    Button loadPresetButton = iconButton(CssIcon.download("#8ab4f8"), "Load selected preset", this::loadSelectedPreset);
    Button deletePresetButton = iconButton(CssIcon.clearX("#f38ba8"), "Delete selected preset", this::deleteSelectedPreset);
    HBox presetLoadRow = new HBox(6, new Label("Preset"), presetBox, loadPresetButton, deletePresetButton);
    presetLoadRow.setAlignment(Pos.CENTER_LEFT);

    presetNameField.setPromptText("Preset name");
    HBox.setHgrow(presetNameField, Priority.ALWAYS);
    Button savePresetButton = iconButton(CssIcon.save("#9ed67a"), "Save preset", this::savePreset);
    HBox presetSaveRow = new HBox(6, new Label("Name"), presetNameField, savePresetButton);
    presetSaveRow.setAlignment(Pos.CENTER_LEFT);

    VBox top = new VBox(8, title, summaryLabel, filterRow, setRow, presetLoadRow, presetSaveRow, new Separator());
    setTop(top);

    // Preview pane
    StackPane previewPane = new StackPane(previewCanvas);
    previewPane.setMinHeight(180);
    previewPane.setPrefHeight(260);
    previewPane.setStyle("-fx-background-color: #121720; -fx-border-color: #2b3445; -fx-border-radius: 6; -fx-background-radius: 6;");
    previewPane.widthProperty().addListener((o, ov, nv) -> {
      previewCanvas.setWidth(Math.max(120, nv.doubleValue() - 4));
      redrawPreview();
    });
    previewPane.heightProperty().addListener((o, ov, nv) -> {
      previewCanvas.setHeight(Math.max(120, nv.doubleValue() - 4));
      redrawPreview();
    });

    installSlider(focusXSlider);
    installSlider(focusYSlider);
    installSlider(cropSlider);
    installSlider(zoomSlider);
    installViewportInteractions(previewCanvas);

    autoExpression.setSelected(true);
    autoExpression.selectedProperty().addListener((o, ov, nv) -> {
      if (Boolean.TRUE.equals(nv)) updateExpressionFromSelection();
      if (!applyingState) persistCurrentSetState();
    });

    randomizeActiveOnly.setSelected(true);
    randomizeActiveOnly.selectedProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistCurrentSetState();
    });

    matchGameFraming.setTooltip(new Tooltip("Use runtime VN character framing (characterHeightFactor / characterBaselineY)"));
    matchGameFraming.selectedProperty().addListener((o, ov, nv) -> {
      updateViewportControlState();
      redrawPreview();
      if (!applyingState) persistCurrentSetState();
    });

    characterIdField.setPromptText("Image tag");
    expressionField.setPromptText("Expression id");
    characterIdField.textProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistCurrentSetState();
    });
    characterIdField.setOnAction(e -> syncSetFromImageTag());
    characterIdField.focusedProperty().addListener((o, ov, focused) -> {
      if (!focused && !applyingState) syncSetFromImageTag();
    });
    expressionField.textProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistCurrentSetState();
    });

    HBox idRow = new HBox(8, new Label("Tag"), characterIdField, new Label("Expr"), expressionField);
    idRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(characterIdField, Priority.ALWAYS);
    HBox.setHgrow(expressionField, Priority.ALWAYS);

    Button copyShowAttrsButton = iconButton(CssIcon.copy("#9ad19c"), "Copy: show <tag> <attributes>", () -> copyTagAttributes(true, true));
    Button copyAttrsOnlyButton = iconButton(CssIcon.copy("#d6b4ff"), "Copy: <attributes> only", () -> copyTagAttributes(false, false));
    HBox attrCopyRow = new HBox(8, new Label("Attrs"), copyShowAttrsButton, copyAttrsOnlyButton);
    attrCopyRow.setAlignment(Pos.CENTER_LEFT);

    // Snippet export
    snippetFormatBox.getItems().setAll(SNIPPET_COMBINED, SNIPPET_CHARIMG, SNIPPET_SHOW, SNIPPET_RECIPE);
    snippetFormatBox.getSelectionModel().select(SNIPPET_COMBINED);
    HBox.setHgrow(snippetFormatBox, Priority.ALWAYS);

    Button copySnippetButton = iconButton(CssIcon.copy("#9ad19c"), "Copy selected snippet format", this::copySnippet);
    Button copyRecipeButton = iconButton(CssIcon.copy("#d6b4ff"), "Copy detailed layer recipe comments", this::copyLayerRecipe);

    HBox snippetRow = new HBox(8, new Label("Export"), snippetFormatBox, copySnippetButton, copyRecipeButton);
    snippetRow.setAlignment(Pos.CENTER_LEFT);

    // Action row
    Button randomizeButton = iconButton(CssIcon.sort("#f0b673"), "Randomize layer choices", this::randomizeSelection);
    Button defaultsButton = iconButton(CssIcon.home("#9ed67a"), "Restore default first option per group", this::applyDefaultSelection);
    Button noneButton = iconButton(CssIcon.clearX("#f38ba8"), "Clear all selected layers", this::applyNoneSelection);
    Button swapPrevButton = iconButton(CssIcon.undo("#8ab4f8"), "Swap previous on marked groups", () -> swapMarkedGroups(-1));
    Button swapNextButton = iconButton(CssIcon.redo("#8ab4f8"), "Swap next on marked groups", () -> swapMarkedGroups(1));
    Button resetViewButton = iconButton(CssIcon.expand("#8ab4f8"), "Reset preview focus and zoom", () -> {
      applyingState = true;
      focusXSlider.setValue(50);
      focusYSlider.setValue(50);
      cropSlider.setValue(100);
      zoomSlider.setValue(100);
      applyingState = false;
      redrawPreview();
      persistCurrentSetState();
    });
    fullscreenButton = iconButton(CssIcon.expand("#f5c46b"), "Fullscreen this panel in the current editor window", this::requestFullscreenToggle);
    updateFullscreenButtonUi();

    HBox toolRow = new HBox(
        8,
        randomizeButton,
        defaultsButton,
        noneButton,
        swapPrevButton,
        swapNextButton,
        resetViewButton,
        fullscreenButton,
        randomizeActiveOnly);
    toolRow.setAlignment(Pos.CENTER_LEFT);
    HBox framingRow = new HBox(8, matchGameFraming);
    framingRow.setAlignment(Pos.CENTER_LEFT);

    VBox controls = new VBox(
        6,
        sliderRow("Focus X", focusXSlider),
        sliderRow("Focus Y", focusYSlider),
        sliderRow("Crop", cropSlider),
        sliderRow("Zoom", zoomSlider));
    TitledPane viewControlsPane = new TitledPane("View controls", controls);
    viewControlsPane.setExpanded(false);
    viewControlsPane.setAnimated(false);
    viewControlsPane.setCollapsible(true);

    VBox previewSection = new VBox(
        8,
        previewPane,
        previewInfoLabel,
        viewControlsPane,
        idRow,
        autoExpression,
        toolRow,
        framingRow,
        attrCopyRow,
        snippetRow,
        statusLabel);
    previewSection.setPadding(new Insets(0, 0, 4, 0));

    Label groupsLabel = new Label("Layer Groups (up/down changes render order)");
    groupsLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");

    Button activeAllButton = iconButton(CssIcon.check("#9ed67a"), "Mark all groups active for randomization", () -> setAllGroupsActive(true));
    Button activeNoneButton = iconButton(CssIcon.minus("#f0b673"), "Mark all groups inactive for randomization", () -> setAllGroupsActive(false));
    Button swapAllButton = iconButton(CssIcon.check("#8ab4f8"), "Mark all groups for swap", () -> setAllSwapGroups(true));
    Button swapNoneButton = iconButton(CssIcon.minus("#8ab4f8"), "Clear swap marks", () -> setAllSwapGroups(false));
    HBox groupTools = new HBox(6, activeAllButton, activeNoneButton, swapAllButton, swapNoneButton);
    groupTools.setAlignment(Pos.CENTER_LEFT);

    attributeFilterField.setPromptText("Filter attributes/groups...");
    attributeFilterField.textProperty().addListener((o, ov, nv) -> {
      if (!applyingState) {
        refreshGroupRows();
        persistCurrentSetState();
      }
    });
    HBox filterRowAttrs = new HBox(8, new Label("Filter"), attributeFilterField);
    filterRowAttrs.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(attributeFilterField, Priority.ALWAYS);

    VBox groupsRoot = new VBox(8, filterRowAttrs, groupsLabel, groupTools, groupBox);
    groupsRoot.setPadding(new Insets(2));
    ScrollPane groupsScroll = new ScrollPane(groupsRoot);
    groupsScroll.setFitToWidth(true);

    typedAttributesField.setPromptText("eyes=angry mouth=smile or eyes_angry mouth_smile");
    typedAttributesField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState || !typedRealtime.isSelected()) return;
      applyTypedAttributes(false);
    });
    typedRealtime.setSelected(true);
    typedRealtime.selectedProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistCurrentSetState();
    });
    Button applyTypedButton = iconButton(CssIcon.check("#9ed67a"), "Apply typed attributes", () -> applyTypedAttributes(true));
    HBox typedHeader = new HBox(8, typedRealtime, applyTypedButton);
    typedHeader.setAlignment(Pos.CENTER_LEFT);
    VBox typedRoot = new VBox(8, new Label("Type attributes to preview"), typedAttributesField, typedHeader);
    typedRoot.setPadding(new Insets(8));

    shortformsArea.setPrefRowCount(8);
    shortformsArea.setWrapText(false);
    shortformsArea.textProperty().addListener((o, ov, nv) -> {
      refreshShortforms();
      if (!applyingState) persistCurrentSetState();
    });
    shortformBox.setPromptText("Shortform");
    HBox.setHgrow(shortformBox, Priority.ALWAYS);
    Button applyShortformButton = iconButton(CssIcon.check("#9ed67a"), "Apply selected shortform", this::applySelectedShortform);
    Button copyShortformButton = iconButton(CssIcon.copy("#d6b4ff"), "Copy selected shortform expression", this::copySelectedShortform);
    HBox shortformRow = new HBox(8, shortformBox, applyShortformButton, copyShortformButton);
    shortformRow.setAlignment(Pos.CENTER_LEFT);
    VBox shortformsRoot = new VBox(8, new Label("Format: name = attribute expression"), shortformsArea, shortformRow);
    shortformsRoot.setPadding(new Insets(8));

    tutorialArea.setEditable(false);
    tutorialArea.setWrapText(true);
    tutorialArea.setPrefRowCount(10);
    VBox tutorialRoot = new VBox(8, tutorialArea);
    tutorialRoot.setPadding(new Insets(8));

    TabPane groupsTabs = new TabPane();
    groupsTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    Tab attributesTab = new Tab("Attributes", groupsScroll);
    Tab typedTab = new Tab("Typed", typedRoot);
    Tab shortformsTab = new Tab("Shortforms", shortformsRoot);
    Tab tutorialTab = new Tab("Tutorial", tutorialRoot);
    groupsTabs.getTabs().addAll(attributesTab, typedTab, shortformsTab, tutorialTab);

    SplitPane split = new SplitPane(previewSection, groupsTabs);
    split.setOrientation(Orientation.VERTICAL);
    split.setDividerPositions(0.58);

    setCenter(split);
    updateViewportControlState();
    refreshShortforms();
    redrawPreview();
  }

  public void setProjectRoot(File projectRoot) {
    if (Objects.equals(this.projectRoot, projectRoot)) return;
    persistCurrentSetState();
    persistGlobalState();
    this.projectRoot = projectRoot;
    refreshCatalog();
  }

  public void setOnToggleFullscreen(Runnable handler) {
    fullscreenToggleHandler = handler;
  }

  public void setFullscreenActive(boolean active) {
    if (fullscreenActive == active) return;
    fullscreenActive = active;
    updateFullscreenButtonUi();
  }

  public void refreshCatalog() {
    persistCurrentSetState();
    persistGlobalState();

    loadPersistentState();
    reloadGameFramingSettings();
    preferredSetId = persisted.getProperty("global.selectedSet", "");

    applyingState = true;
    filterField.setText(persisted.getProperty("global.filter", ""));
    applyingState = false;

    sets.clear();
    selectors.clear();
    activeGroupChecks.clear();
    swapGroupChecks.clear();
    groupRows.clear();
    groupOrder.clear();
    groupBox.getChildren().clear();
    imageCache.clear();
    viewportFrames.clear();
    presetNameToKey.clear();
    presetBox.getItems().clear();

    if (projectRoot == null || !projectRoot.isDirectory()) {
      currentSetId = null;
      summaryLabel.setText("Open a project to inspect layered image sets.");
      setBox.getItems().clear();
      redrawPreview();
      return;
    }

    Path root = projectRoot.toPath();
    int imageCount = 0;
    try (Stream<Path> stream = Files.walk(root, 10)) {
      List<Path> files = stream
          .filter(Files::isRegularFile)
          .filter(this::isImageFile)
          .filter(path -> !isIgnoredPath(root.relativize(path).toString().replace('\\', '/')))
          .sorted()
          .toList();
      for (Path p : files) {
        String relative = root.relativize(p).toString().replace('\\', '/');
        LayerOption option = parseOption(relative, p.toFile());
        if (option == null) continue;
        String setId = deriveSetId(relative);
        sets.computeIfAbsent(setId, LayeredSet::new).add(option);
        imageCount++;
      }
    } catch (IOException ex) {
      currentSetId = null;
      summaryLabel.setText("Failed to scan project assets: " + ex.getMessage());
      setBox.getItems().clear();
      redrawPreview();
      return;
    }

    summaryLabel.setText("Layer sets: " + sets.size() + "  |  Images: " + imageCount);
    refreshSetOptions();
  }

  private void refreshSetOptions() {
    String filter = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
    String previous = setBox.getValue();

    List<String> visible = new ArrayList<>();
    for (LayeredSet set : sets.values()) {
      if (filter.isEmpty() || set.id.toLowerCase(Locale.ROOT).contains(filter)) {
        visible.add(set.id);
      }
    }

    setBox.getItems().setAll(visible);

    Map<String, Integer> groupCounts = new HashMap<>();
    for (String setId : visible) {
      LayeredSet set = sets.get(setId);
      groupCounts.put(setId, set == null ? 0 : set.groups.size());
    }
    String target = chooseSetSelection(previous, preferredSetId, visible, groupCounts);

    if (target != null) {
      applyingState = true;
      setBox.getSelectionModel().select(target);
      applyingState = false;
      onSetSelectionChanged();
    } else {
      currentSetId = null;
      selectors.clear();
      activeGroupChecks.clear();
      swapGroupChecks.clear();
      groupRows.clear();
      groupOrder.clear();
      groupBox.getChildren().clear();
      presetNameToKey.clear();
      presetBox.getItems().clear();
      redrawPreview();
    }
  }

  private void onSetSelectionChanged() {
    String selectedSet = setBox.getValue();
    if (selectedSet == null || selectedSet.isBlank()) return;

    if (currentSetId != null && !currentSetId.equals(selectedSet)) {
      persistCurrentSetState();
    }

    currentSetId = selectedSet;

    selectors.clear();
    activeGroupChecks.clear();
    swapGroupChecks.clear();
    groupRows.clear();
    groupOrder.clear();
    groupBox.getChildren().clear();

    LayeredSet set = sets.get(selectedSet);
    if (set == null || set.groups.isEmpty()) {
      redrawPreview();
      return;
    }

    List<String> groupNames = new ArrayList<>(set.groups.keySet());
    groupNames.sort(Comparator.naturalOrder());

    for (String groupName : groupNames) {
      List<LayerOption> options = new ArrayList<>(set.groups.get(groupName));
      options.sort(Comparator
          .comparingInt((LayerOption o) -> o.sortKey)
          .thenComparing(o -> o.label.toLowerCase(Locale.ROOT)));

      ComboBox<LayerOption> combo = new ComboBox<>();
      combo.setConverter(new StringConverter<>() {
        @Override public String toString(LayerOption object) {
          return object == null ? "" : object.label;
        }
        @Override public LayerOption fromString(String string) { return null; }
      });

      combo.getItems().add(LayerOption.none());
      combo.getItems().addAll(options);
      selectPreferredLayerOption(combo);
      combo.setMaxWidth(Double.MAX_VALUE);

      CheckBox activeCheck = new CheckBox();
      activeCheck.setSelected(true);
      activeCheck.setTooltip(new Tooltip("Include this group when randomizing with active-only mode"));
      activeCheck.selectedProperty().addListener((o, ov, nv) -> {
        if (applyingState) return;
        persistCurrentSetState();
      });

      CheckBox swapCheck = new CheckBox();
      swapCheck.setSelected(false);
      swapCheck.setTooltip(new Tooltip("Mark this group for quick swapping"));
      swapCheck.selectedProperty().addListener((o, ov, nv) -> {
        if (applyingState) return;
        persistCurrentSetState();
      });

      Button nextButton = iconButton(CssIcon.redo("#8ab4f8"), "Cycle this group", () -> {
        int idx = combo.getSelectionModel().getSelectedIndex();
        if (idx < 1) idx = 0;
        int next = idx + 1;
        if (next >= combo.getItems().size()) next = 1;
        combo.getSelectionModel().select(next);
      });

      Button openButton = iconButton(CssIcon.folder("#7ec8e3"), "Open selected image in OS viewer", () -> openSelectedImage(combo.getValue()));

      Button upButton = iconButton(CssIcon.arrowUp("#b0b8c8"), "Move this group up in render order", () -> moveGroup(groupName, -1));

      Button downButton = iconButton(CssIcon.arrowDown("#b0b8c8"), "Move this group down in render order", () -> moveGroup(groupName, 1));

      combo.valueProperty().addListener((o, ov, nv) -> {
        if (applyingState) {
          redrawPreview();
          return;
        }
        updateExpressionFromSelection();
        redrawPreview();
        persistCurrentSetState();
      });

      Label groupLabel = new Label(groupName);
      groupLabel.setMinWidth(110);
      groupLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");

      HBox row = new HBox(6, activeCheck, swapCheck, groupLabel, combo, nextButton, openButton, upButton, downButton);
      row.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(combo, Priority.ALWAYS);

      selectors.put(groupName, combo);
      activeGroupChecks.put(groupName, activeCheck);
      swapGroupChecks.put(groupName, swapCheck);
      groupRows.put(groupName, row);
      groupOrder.add(groupName);
    }

    if (characterIdField.getText() == null || characterIdField.getText().isBlank()) {
      characterIdField.setText(sanitizeId(takeLastPathToken(selectedSet)));
    }

    applyStateFromPrefix(statePrefix(selectedSet));
    refreshPresetList();

    persistGlobalState();
    persistCurrentSetState();
    redrawPreview();
  }

  private void refreshGroupRows() {
    groupBox.getChildren().clear();
    String filter = sanitizeId(attributeFilterField.getText());
    for (String groupName : groupOrder) {
      if (!matchesAttributeFilter(groupName, filter)) continue;
      HBox row = groupRows.get(groupName);
      if (row != null) groupBox.getChildren().add(row);
    }
    if (groupBox.getChildren().isEmpty()) {
      Label empty = new Label("No attributes match the current filter.");
      empty.setStyle("-fx-text-fill: rgba(255,255,255,0.7);");
      groupBox.getChildren().add(empty);
    }
  }

  private void moveGroup(String groupName, int delta) {
    int idx = groupOrder.indexOf(groupName);
    if (idx < 0) return;
    int next = idx + delta;
    if (next < 0 || next >= groupOrder.size()) return;
    Collections.swap(groupOrder, idx, next);
    refreshGroupRows();
    redrawPreview();
    persistCurrentSetState();
  }

  private void setAllGroupsActive(boolean active) {
    for (CheckBox check : activeGroupChecks.values()) {
      check.setSelected(active);
    }
    persistCurrentSetState();
  }

  private void setAllSwapGroups(boolean active) {
    for (CheckBox check : swapGroupChecks.values()) {
      check.setSelected(active);
    }
    persistCurrentSetState();
  }

  private void swapMarkedGroups(int direction) {
    int swapped = 0;
    applyingState = true;
    for (String group : groupOrder) {
      CheckBox mark = swapGroupChecks.get(group);
      if (mark == null || !mark.isSelected()) continue;
      ComboBox<LayerOption> combo = selectors.get(group);
      if (combo == null || combo.getItems().size() <= 1) continue;
      int idx = combo.getSelectionModel().getSelectedIndex();
      if (idx < 1) idx = 1;
      int next = idx + (direction < 0 ? -1 : 1);
      if (next < 1) next = combo.getItems().size() - 1;
      if (next >= combo.getItems().size()) next = 1;
      combo.getSelectionModel().select(next);
      swapped++;
    }
    applyingState = false;
    if (swapped > 0) {
      updateExpressionFromSelection();
      redrawPreview();
      persistCurrentSetState();
      status("Swapped " + swapped + " marked groups.");
    } else {
      status("No marked groups available for swapping.");
    }
  }

  private boolean matchesAttributeFilter(String groupName, String normalizedFilter) {
    if (normalizedFilter == null || normalizedFilter.isBlank()) return true;
    String groupKey = sanitizeId(groupName);
    if (groupKey.contains(normalizedFilter)) return true;
    ComboBox<LayerOption> combo = selectors.get(groupName);
    if (combo == null) return false;
    for (LayerOption option : combo.getItems()) {
      if (option == null || option.isNone()) continue;
      if (sanitizeId(option.label).contains(normalizedFilter)) return true;
    }
    return false;
  }

  private void syncSetFromImageTag() {
    if (applyingState || sets.isEmpty()) return;
    String tag = sanitizeId(characterIdField.getText());
    if (tag.isBlank()) return;
    String selected = setBox.getValue();
    if (selected != null && sanitizeId(takeLastPathToken(selected)).equals(tag)) return;
    String preferred = null;
    for (String setId : setBox.getItems()) {
      String token = sanitizeId(takeLastPathToken(setId));
      if (token.equals(tag)) {
        preferred = setId;
        break;
      }
      if (preferred == null && token.contains(tag)) preferred = setId;
    }
    if (preferred == null) return;
    applyingState = true;
    try {
      setBox.getSelectionModel().select(preferred);
    } finally {
      applyingState = false;
    }
    onSetSelectionChanged();
  }

  private void refreshShortforms() {
    Map<String, String> parsed = parseAttributeShortforms(shortformsArea.getText());
    shortforms.clear();
    shortforms.putAll(parsed);
    String keep = shortformBox.getValue();
    shortformBox.getItems().setAll(shortforms.keySet());
    if (keep != null && shortforms.containsKey(keep)) {
      shortformBox.getSelectionModel().select(keep);
    } else if (!shortforms.isEmpty()) {
      shortformBox.getSelectionModel().select(0);
    }
  }

  private void applySelectedShortform() {
    String key = shortformBox.getValue();
    if (key == null || key.isBlank()) {
      status("Select a shortform first.");
      return;
    }
    String expression = shortforms.get(key);
    if (expression == null || expression.isBlank()) {
      status("Shortform is empty.");
      return;
    }
    typedAttributesField.setText(expression);
    applyTypedAttributes(true);
    status("Applied shortform: " + key);
  }

  private void copySelectedShortform() {
    String key = shortformBox.getValue();
    if (key == null || key.isBlank()) {
      status("Select a shortform first.");
      return;
    }
    String expression = shortforms.get(key);
    if (expression == null || expression.isBlank()) {
      status("Shortform is empty.");
      return;
    }
    copy(expression);
    status("Copied shortform: " + key);
  }

  private void applyTypedAttributes(boolean persist) {
    Map<String, String> assignments = parseAttributeAssignments(typedAttributesField.getText());
    if (assignments.isEmpty()) {
      if (persist) status("No valid attributes found in typed input.");
      return;
    }
    int applied = applyAttributeAssignments(assignments);
    if (applied > 0) {
      updateExpressionFromSelection();
      redrawPreview();
      if (persist) persistCurrentSetState();
      status("Applied " + applied + " typed attributes.");
    } else if (persist) {
      status("Typed attributes did not match current groups.");
    }
  }

  private int applyAttributeAssignments(Map<String, String> assignments) {
    if (assignments == null || assignments.isEmpty()) return 0;
    int applied = 0;
    applyingState = true;
    try {
      for (Map.Entry<String, String> entry : assignments.entrySet()) {
        String group = resolveGroupName(entry.getKey());
        if (group == null || group.isBlank()) continue;
        ComboBox<LayerOption> combo = selectors.get(group);
        if (combo == null) continue;
        LayerOption target = findOptionByValue(combo, entry.getValue());
        if (target == null) continue;
        combo.getSelectionModel().select(target);
        applied++;
      }
    } finally {
      applyingState = false;
    }
    return applied;
  }

  private String resolveGroupName(String rawGroup) {
    String key = sanitizeId(rawGroup);
    if (key.isBlank()) return null;
    String alias = GROUP_TOKEN_ALIASES.getOrDefault(key, key);
    for (String group : selectors.keySet()) {
      if (sanitizeId(group).equals(alias)) return group;
    }
    for (String group : selectors.keySet()) {
      if (sanitizeId(group).contains(alias) || alias.contains(sanitizeId(group))) return group;
    }
    return null;
  }

  private LayerOption findOptionByValue(ComboBox<LayerOption> combo, String rawValue) {
    if (combo == null) return null;
    String value = sanitizeId(rawValue);
    if (value.isBlank()) return null;
    if ("none".equals(value) || "off".equals(value) || "clear".equals(value) || "-".equals(value)) {
      return combo.getItems().isEmpty() ? null : combo.getItems().get(0);
    }
    for (LayerOption option : combo.getItems()) {
      if (option == null || option.isNone()) continue;
      if (sanitizeId(option.label).equals(value)) return option;
    }
    for (LayerOption option : combo.getItems()) {
      if (option == null || option.isNone()) continue;
      if (sanitizeId(option.label).contains(value) || value.contains(sanitizeId(option.label))) return option;
    }
    return null;
  }

  private void copyTagAttributes(boolean includeShowKeyword, boolean includeTag) {
    String attrs = String.join(" ", buildAttributeTokens());
    String tag = sanitizeId(characterIdField.getText());
    if (tag.isBlank()) tag = "character";
    StringBuilder out = new StringBuilder();
    if (includeShowKeyword) out.append("show ");
    if (includeTag) {
      out.append(tag);
      if (!attrs.isBlank()) out.append(' ');
    }
    out.append(attrs);
    copy(out.toString().trim());
    status("Copied attribute string.");
  }

  private List<String> buildAttributeTokens() {
    List<String> tokens = new ArrayList<>();
    for (String group : groupOrder) {
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption option = combo == null ? null : combo.getValue();
      if (option == null || option.isNone()) continue;
      String groupToken = sanitizeId(group);
      String optionToken = sanitizeId(option.label);
      if (groupToken.isBlank() || optionToken.isBlank()) continue;
      tokens.add(groupToken + "_" + optionToken);
    }
    return tokens;
  }

  private void applyDefaultSelection() {
    applyingState = true;
    for (ComboBox<LayerOption> combo : selectors.values()) {
      selectPreferredLayerOption(combo);
    }
    applyingState = false;
    updateExpressionFromSelection();
    redrawPreview();
    persistCurrentSetState();
    status("Restored default layer picks.");
  }

  private void applyNoneSelection() {
    applyingState = true;
    for (ComboBox<LayerOption> combo : selectors.values()) {
      combo.getSelectionModel().select(0);
    }
    applyingState = false;
    updateExpressionFromSelection();
    redrawPreview();
    persistCurrentSetState();
    status("Cleared all layer picks.");
  }

  private void randomizeSelection() {
    if (selectors.isEmpty()) return;
    int changed = 0;
    applyingState = true;
    for (String group : groupOrder) {
      if (randomizeActiveOnly.isSelected()) {
        CheckBox check = activeGroupChecks.get(group);
        if (check != null && !check.isSelected()) continue;
      }
      ComboBox<LayerOption> combo = selectors.get(group);
      if (combo == null) continue;
      int size = combo.getItems().size();
      if (size <= 1) continue;
      int idx = 1 + random.nextInt(size - 1);
      combo.getSelectionModel().select(idx);
      changed++;
    }
    applyingState = false;
    updateExpressionFromSelection();
    redrawPreview();
    persistCurrentSetState();
    status("Randomized " + changed + " groups.");
  }

  private void redrawPreview() {
    renderPreviewToCanvas(previewCanvas, true);
  }

  private void renderPreviewToCanvas(Canvas canvas, boolean updateInfoLabel) {
    if (canvas == null) return;
    double w = Math.max(1, canvas.getWidth());
    double h = Math.max(1, canvas.getHeight());
    GraphicsContext g = canvas.getGraphicsContext2D();

    drawCheckerBackground(g, w, h);

    List<LayerOption> active = selectedLayers();
    if (active.isEmpty()) {
      viewportFrames.remove(canvas);
      drawCenteredText(g, w, h, "Select layer options to preview");
      if (updateInfoLabel) previewInfoLabel.setText("No layers selected.");
      return;
    }

    List<Image> layers = new ArrayList<>();
    double maxW = 0;
    double maxH = 0;
    for (LayerOption option : active) {
      Image img = loadImage(option);
      if (img == null || img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) continue;
      layers.add(img);
      if (img.getWidth() > maxW) maxW = img.getWidth();
      if (img.getHeight() > maxH) maxH = img.getHeight();
    }
    if (layers.isEmpty()) {
      viewportFrames.remove(canvas);
      drawCenteredText(g, w, h, "Selected images failed to load");
      if (updateInfoLabel) previewInfoLabel.setText("Selected images could not be decoded.");
      return;
    }

    if (matchGameFraming.isSelected()) {
      viewportFrames.remove(canvas);
      renderGameFramedPreview(g, layers, w, h, maxW, maxH);
      if (updateInfoLabel) {
        previewInfoLabel.setText(
            "Layers: " + active.size()
                + "  |  Active groups: " + activeGroupCount()
                + "  |  Virtual size: " + (int) maxW + "x" + (int) maxH
                + "  |  Game framing: h=" + formatDouble(gameCharacterHeightFactor)
                + ", baseline=" + formatDouble(gameCharacterBaselineY));
      }
      return;
    }

    viewportFrames.put(canvas, renderViewportPreview(g, layers, w, h, maxW, maxH));
    if (updateInfoLabel) {
      previewInfoLabel.setText(
          "Layers: " + active.size()
              + "  |  Active groups: " + activeGroupCount()
              + "  |  Virtual size: " + (int) maxW + "x" + (int) maxH);
    }
  }

  private void renderGameFramedPreview(GraphicsContext g, List<Image> layers, double canvasWidth, double canvasHeight, double maxW, double maxH) {
    double referenceAspect = maxH > 0 ? (maxW / maxH) : 0.5;
    if (!(referenceAspect > 0)) referenceAspect = 0.5;

    double spriteHeight = canvasHeight * gameCharacterHeightFactor;
    double spriteWidth = spriteHeight * referenceAspect;
    double x = (canvasWidth - spriteWidth) * 0.5;
    double y = (canvasHeight * gameCharacterBaselineY) - spriteHeight;

    for (Image img : layers) {
      g.drawImage(img, x, y, spriteWidth, spriteHeight);
    }

    g.setStroke(Color.color(1, 1, 1, 0.22));
    g.strokeRect(x + 0.5, y + 0.5, spriteWidth - 1, spriteHeight - 1);
    g.strokeLine(canvasWidth / 2.0, y, canvasWidth / 2.0, y + spriteHeight);
    g.strokeLine(x, canvasHeight / 2.0, x + spriteWidth, canvasHeight / 2.0);
  }

  private ViewportFrame renderViewportPreview(GraphicsContext g, List<Image> layers, double canvasWidth, double canvasHeight, double maxW, double maxH) {
    double focusX = focusXSlider.getValue() / 100.0;
    double focusY = focusYSlider.getValue() / 100.0;
    double crop = cropSlider.getValue() / 100.0;
    double zoom = zoomSlider.getValue() / 100.0;
    if (zoom < 0.1) zoom = 0.1;
    crop = clamp(crop, 0.2, 1.0);

    double srcW = clamp((maxW * crop) / zoom, 1, maxW);
    double srcH = clamp((maxH * crop) / zoom, 1, maxH);

    double centerX = focusX * maxW;
    double centerY = focusY * maxH;
    double srcX = clamp(centerX - srcW * 0.5, 0, maxW - srcW);
    double srcY = clamp(centerY - srcH * 0.5, 0, maxH - srcH);

    double fit = Math.min(canvasWidth / srcW, canvasHeight / srcH);
    double destW = srcW * fit;
    double destH = srcH * fit;
    double destX = (canvasWidth - destW) * 0.5;
    double destY = (canvasHeight - destH) * 0.5;

    for (Image img : layers) {
      double sx = srcX * img.getWidth() / maxW;
      double sy = srcY * img.getHeight() / maxH;
      double sw = srcW * img.getWidth() / maxW;
      double sh = srcH * img.getHeight() / maxH;
      g.drawImage(img, sx, sy, sw, sh, destX, destY, destW, destH);
    }

    g.setStroke(Color.color(1, 1, 1, 0.22));
    g.strokeRect(destX + 0.5, destY + 0.5, destW - 1, destH - 1);
    g.strokeLine(canvasWidth / 2.0, destY, canvasWidth / 2.0, destY + destH);
    g.strokeLine(destX, canvasHeight / 2.0, destX + destW, canvasHeight / 2.0);
    return new ViewportFrame(maxW, maxH, srcX, srcY, srcW, srcH, destX, destY, destW, destH);
  }

  private int activeGroupCount() {
    int count = 0;
    for (CheckBox check : activeGroupChecks.values()) {
      if (check.isSelected()) count++;
    }
    return count;
  }

  private void installSlider(Slider slider) {
    slider.valueProperty().addListener((o, ov, nv) -> {
      redrawPreview();
      if (!slider.isValueChanging() && !applyingState) persistCurrentSetState();
    });
    slider.valueChangingProperty().addListener((o, ov, nv) -> {
      if (!nv && !applyingState) persistCurrentSetState();
    });
  }

  private void installViewportInteractions(Canvas canvas) {
    if (canvas == null) return;

    canvas.setOnMousePressed(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (matchGameFraming.isSelected()) return;
      if (!viewportFrames.containsKey(canvas)) return;
      dragCanvas = canvas;
      dragLastX = e.getX();
      dragLastY = e.getY();
      dragDirty = false;
      e.consume();
    });

    canvas.setOnMouseDragged(e -> {
      if (dragCanvas != canvas || !e.isPrimaryButtonDown()) return;
      double dx = e.getX() - dragLastX;
      double dy = e.getY() - dragLastY;
      dragLastX = e.getX();
      dragLastY = e.getY();
      if (Math.abs(dx) < 0.0001 && Math.abs(dy) < 0.0001) return;
      if (panViewportByPixels(canvas, dx, dy)) {
        dragDirty = true;
      }
      e.consume();
    });

    canvas.setOnMouseReleased(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (dragCanvas != canvas) return;
      dragCanvas = null;
      if (dragDirty) {
        dragDirty = false;
        persistCurrentSetState();
      }
      e.consume();
    });

    canvas.setOnScroll(e -> {
      if (Math.abs(e.getDeltaY()) < 0.0001) return;
      if (zoomViewportAt(canvas, e.getX(), e.getY(), e.getDeltaY())) {
        persistCurrentSetState();
        e.consume();
      }
    });
  }

  private boolean panViewportByPixels(Canvas canvas, double dx, double dy) {
    if (canvas == null || matchGameFraming.isSelected()) return false;
    ViewportFrame frame = viewportFrames.get(canvas);
    if (frame == null || !frame.valid()) return false;

    double focusX = focusXSlider.getValue() / 100.0;
    double focusY = focusYSlider.getValue() / 100.0;
    double nextFocusX = draggedFocus(focusX, dx, frame.srcW(), frame.destW(), frame.maxW());
    double nextFocusY = draggedFocus(focusY, dy, frame.srcH(), frame.destH(), frame.maxH());
    return setViewportState(nextFocusX, nextFocusY, null);
  }

  private boolean zoomViewportAt(Canvas canvas, double canvasX, double canvasY, double deltaY) {
    if (canvas == null || matchGameFraming.isSelected()) return false;
    ViewportFrame frame = viewportFrames.get(canvas);
    if (frame == null || !frame.valid()) return false;

    double minZoom = zoomSlider.getMin() / 100.0;
    double maxZoom = zoomSlider.getMax() / 100.0;
    double currentZoom = clamp(zoomSlider.getValue() / 100.0, minZoom, maxZoom);
    double nextZoom = zoomFromScroll(currentZoom, deltaY, minZoom, maxZoom);
    if (Math.abs(nextZoom - currentZoom) < 0.000001) return false;

    double px = clamp(canvasX, frame.destX(), frame.destX() + frame.destW());
    double py = clamp(canvasY, frame.destY(), frame.destY() + frame.destH());
    double ratioX = frame.destW() <= 0 ? 0.5 : (px - frame.destX()) / frame.destW();
    double ratioY = frame.destH() <= 0 ? 0.5 : (py - frame.destY()) / frame.destH();

    double anchorX = frame.srcX() + ratioX * frame.srcW();
    double anchorY = frame.srcY() + ratioY * frame.srcH();
    double crop = clamp(cropSlider.getValue() / 100.0, 0.2, 1.0);
    double newSrcW = clamp((frame.maxW() * crop) / nextZoom, 1.0, frame.maxW());
    double newSrcH = clamp((frame.maxH() * crop) / nextZoom, 1.0, frame.maxH());

    double nextFocusX = anchoredFocus(anchorX, ratioX, newSrcW, frame.maxW());
    double nextFocusY = anchoredFocus(anchorY, ratioY, newSrcH, frame.maxH());
    return setViewportState(nextFocusX, nextFocusY, nextZoom * 100.0);
  }

  private boolean setViewportState(double focusX, double focusY, Double zoomPercent) {
    double clampedFocusX = clamp(focusX, 0.0, 1.0) * 100.0;
    double clampedFocusY = clamp(focusY, 0.0, 1.0) * 100.0;
    double clampedZoom = zoomPercent == null
        ? zoomSlider.getValue()
        : clamp(zoomPercent, zoomSlider.getMin(), zoomSlider.getMax());

    boolean changed = false;
    applyingState = true;
    try {
      if (Math.abs(focusXSlider.getValue() - clampedFocusX) >= 0.0001) {
        focusXSlider.setValue(clampedFocusX);
        changed = true;
      }
      if (Math.abs(focusYSlider.getValue() - clampedFocusY) >= 0.0001) {
        focusYSlider.setValue(clampedFocusY);
        changed = true;
      }
      if (zoomPercent != null && Math.abs(zoomSlider.getValue() - clampedZoom) >= 0.0001) {
        zoomSlider.setValue(clampedZoom);
        changed = true;
      }
    } finally {
      applyingState = false;
    }

    if (changed) redrawPreview();
    return changed;
  }

  static double draggedFocus(double currentFocus, double deltaPixels, double srcSpan, double destSpan, double maxSpan) {
    if (!(destSpan > 0) || !(maxSpan > 0)) return clamp(currentFocus, 0.0, 1.0);
    double sourceDelta = deltaPixels * (srcSpan / destSpan);
    return clamp(currentFocus - (sourceDelta / maxSpan), 0.0, 1.0);
  }

  static double anchoredFocus(double anchorSource, double anchorRatio, double srcSpan, double maxSpan) {
    if (!(maxSpan > 0)) return 0.5;
    double ratio = clamp(anchorRatio, 0.0, 1.0);
    double span = clamp(srcSpan, 1.0, maxSpan);
    double start = clamp(anchorSource - ratio * span, 0.0, Math.max(0.0, maxSpan - span));
    return clamp((start + span * 0.5) / maxSpan, 0.0, 1.0);
  }

  static double zoomFromScroll(double currentZoom, double deltaY, double minZoom, double maxZoom) {
    double base = currentZoom > 0 ? currentZoom : minZoom;
    double scaled = base * Math.pow(1.12, deltaY / 40.0);
    return clamp(scaled, minZoom, maxZoom);
  }

  private void updateViewportControlState() {
    boolean gameFraming = matchGameFraming.isSelected();
    focusXSlider.setDisable(gameFraming);
    focusYSlider.setDisable(gameFraming);
    cropSlider.setDisable(gameFraming);
    zoomSlider.setDisable(gameFraming);
  }

  private Button iconButton(Region icon, String tooltip, Runnable action) {
    Button button = new Button();
    button.setGraphic(icon);
    button.setMinSize(28, 28);
    button.setPrefSize(28, 28);
    button.setMaxSize(28, 28);
    button.setFocusTraversable(false);
    if (tooltip != null && !tooltip.isBlank()) {
      button.setTooltip(new Tooltip(tooltip));
    }
    button.setOnAction(e -> {
      if (action != null) action.run();
    });
    return button;
  }

  private void requestFullscreenToggle() {
    if (fullscreenToggleHandler != null) {
      fullscreenToggleHandler.run();
      return;
    }
    status("Fullscreen toggle is unavailable in this host.");
  }

  private void updateFullscreenButtonUi() {
    if (fullscreenButton == null) return;
    if (fullscreenActive) {
      fullscreenButton.setGraphic(CssIcon.clearX("#f38ba8"));
      fullscreenButton.setTooltip(new Tooltip("Exit panel fullscreen"));
    } else {
      fullscreenButton.setGraphic(CssIcon.expand("#f5c46b"));
      fullscreenButton.setTooltip(new Tooltip("Fullscreen this panel in the current editor window"));
    }
  }

  private void copySnippet() {
    String format = snippetFormatBox.getValue();
    if (format == null || format.isBlank()) format = SNIPPET_COMBINED;
    String snippet = buildSnippet(format);
    copy(snippet);
    status("Copied snippet: " + format + ".");
  }

  private String buildSnippet(String format) {
    String characterId = sanitizeId(characterIdField.getText());
    String expression = sanitizeId(expressionField.getText());
    if (characterId.isBlank()) characterId = "character_id";
    if (expression.isBlank()) expression = "neutral";

    String layerSpec = buildLayerPathSpec();
    String fallbackPath = "assets/characters/" + characterId + "/" + expression + ".png";
    String imageSpec = layerSpec.isBlank() ? fallbackPath : layerSpec;
    String charimg = "@charimg " + characterId + " " + expression + " " + imageSpec;
    String show = "[show " + characterId + " center " + expression + "]";

    if (SNIPPET_CHARIMG.equals(format)) return charimg + "\n";
    if (SNIPPET_SHOW.equals(format)) return show + "\n";
    if (SNIPPET_RECIPE.equals(format)) {
      StringBuilder out = new StringBuilder();
      out.append("# Layer recipe generated by JVN Layered Image Visualizer\n");
      out.append("# Multi-layer @charimg is supported via: pathA | pathB | pathC\n");
      if (currentSetId != null && !currentSetId.isBlank()) {
        out.append("# Source set: ").append(currentSetId).append('\n');
      }
      for (String group : groupOrder) {
        ComboBox<LayerOption> combo = selectors.get(group);
        LayerOption option = combo != null ? combo.getValue() : null;
        if (option == null || option.isNone()) continue;
        out.append("# ").append(group).append(" -> ").append(option.relativePath).append('\n');
      }
      out.append(charimg).append('\n').append(show).append('\n');
      return out.toString();
    }

    return charimg + "\n" + show + "\n";
  }

  private String buildLayerPathSpec() {
    StringBuilder spec = new StringBuilder();
    for (LayerOption option : selectedLayers()) {
      if (option == null || option.isNone() || option.relativePath == null || option.relativePath.isBlank()) continue;
      if (spec.length() > 0) spec.append(" | ");
      spec.append(option.relativePath.trim());
    }
    return spec.toString();
  }

  private void copyLayerRecipe() {
    copy(buildSnippet(SNIPPET_RECIPE));
    status("Copied detailed layer recipe.");
  }

  private void updateExpressionFromSelection() {
    if (!autoExpression.isSelected()) return;
    List<String> names = new ArrayList<>();
    for (String group : groupOrder) {
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption option = combo != null ? combo.getValue() : null;
      if (option == null || option.isNone()) continue;
      names.add(sanitizeId(option.label));
    }
    String expr = names.isEmpty() ? "neutral" : String.join("_", names);
    applyingState = true;
    expressionField.setText(expr);
    applyingState = false;
  }

  private List<LayerOption> selectedLayers() {
    List<String> selectedGroupNames = new ArrayList<>();
    for (String group : groupOrder) {
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption option = combo != null ? combo.getValue() : null;
      if (option == null || option.isNone()) continue;
      selectedGroupNames.add(group);
    }
    boolean suppressBackground = shouldSuppressBackgroundGroups(selectedGroupNames);

    List<LayerOption> out = new ArrayList<>();
    for (String group : groupOrder) {
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption option = combo != null ? combo.getValue() : null;
      if (option == null || option.isNone()) continue;
      if (suppressBackground && isLikelyBackgroundGroupName(group)) continue;
      out.add(option);
    }
    return out;
  }

  private void selectPreferredLayerOption(ComboBox<LayerOption> combo) {
    if (combo == null || combo.getItems().isEmpty()) return;
    if (combo.getItems().size() == 1) {
      combo.getSelectionModel().select(0);
      return;
    }
    int preferredIdx = 1;
    int preferredScore = Integer.MAX_VALUE;
    for (int i = 1; i < combo.getItems().size(); i++) {
      LayerOption option = combo.getItems().get(i);
      int score = defaultOptionScore(option == null ? null : option.label);
      if (score < preferredScore) {
        preferredScore = score;
        preferredIdx = i;
      }
    }
    combo.getSelectionModel().select(preferredIdx);
  }

  static int defaultOptionScore(String label) {
    String key = sanitizeId(label);
    if ("neutral".equals(key)) return 0;
    if ("default".equals(key)) return 1;
    if ("base".equals(key)) return 2;
    if (key.contains("neutral")) return 3;
    if (key.contains("default")) return 4;
    if (key.contains("base")) return 5;
    return 10;
  }

  static boolean isLikelyBackgroundGroupName(String groupName) {
    String g = sanitizeId(groupName);
    if (g.isBlank()) return false;
    return g.equals("bg")
        || g.contains("background")
        || g.equals("field")
        || g.equals("scene")
        || g.equals("location")
        || g.equals("room")
        || g.equals("environment")
        || g.equals("menu")
        || g.equals("mainmenu")
        || g.equals("title")
        || g.equals("backdrop");
  }

  static boolean shouldSuppressBackgroundGroups(List<String> selectedGroupNames) {
    if (selectedGroupNames == null || selectedGroupNames.isEmpty()) return false;
    boolean hasBackground = false;
    boolean hasForeground = false;
    for (String groupName : selectedGroupNames) {
      if (isLikelyBackgroundGroupName(groupName)) {
        hasBackground = true;
      } else {
        hasForeground = true;
      }
    }
    return hasBackground && hasForeground;
  }

  private Image loadImage(LayerOption option) {
    if (option == null || option.file == null) return null;
    String key = option.file.getAbsolutePath();
    Image cached = imageCache.get(key);
    if (cached != null) return cached;
    Image img = new Image(option.file.toURI().toString(), false);
    if (!img.isError()) imageCache.put(key, img);
    return img;
  }

  private void openSelectedImage(LayerOption option) {
    if (option == null || option.file == null) return;
    try {
      if (!Desktop.isDesktopSupported()) {
        status("Desktop open is not supported on this platform.");
        return;
      }
      Desktop.getDesktop().open(option.file);
      status("Opened " + option.file.getName());
    } catch (Exception ex) {
      status("Failed to open image: " + ex.getMessage());
    }
  }

  private void refreshPresetList() {
    presetNameToKey.clear();
    presetBox.getItems().clear();
    if (currentSetId == null || currentSetId.isBlank()) return;

    String prefix = "preset." + encodeKey(currentSetId) + ".";
    Map<String, String> namesToKeys = new HashMap<>();

    for (String key : persisted.stringPropertyNames()) {
      if (!key.startsWith(prefix) || !key.endsWith(".name")) continue;
      String presetKey = key.substring(prefix.length(), key.length() - ".name".length());
      String name = persisted.getProperty(key, "").trim();
      if (name.isBlank()) continue;
      String uniqueName = name;
      int counter = 2;
      while (namesToKeys.containsKey(uniqueName)) {
        uniqueName = name + " (" + counter + ")";
        counter++;
      }
      namesToKeys.put(uniqueName, presetKey);
    }

    List<String> names = new ArrayList<>(namesToKeys.keySet());
    names.sort(String.CASE_INSENSITIVE_ORDER);
    presetBox.getItems().setAll(names);
    for (String name : names) {
      presetNameToKey.put(name, namesToKeys.get(name));
    }

    String lastPreset = persisted.getProperty(statePrefix(currentSetId) + "lastPreset", "");
    if (!lastPreset.isBlank()) {
      for (Map.Entry<String, String> e : presetNameToKey.entrySet()) {
        if (lastPreset.equals(e.getValue())) {
          presetBox.getSelectionModel().select(e.getKey());
          presetNameField.setText(e.getKey());
          return;
        }
      }
    }

    if (!names.isEmpty()) {
      presetBox.getSelectionModel().select(0);
      presetNameField.setText(names.get(0));
    } else {
      presetNameField.clear();
    }
  }

  private void savePreset() {
    if (currentSetId == null || currentSetId.isBlank()) {
      status("Select a set before saving presets.");
      return;
    }

    String name = presetNameField.getText() == null ? "" : presetNameField.getText().trim();
    if (name.isBlank()) {
      String expr = sanitizeId(expressionField.getText());
      name = expr.isBlank() ? "preset" : expr;
    }

    String existing = presetNameToKey.get(name);
    String presetKey = existing != null ? existing : uniquePresetKey(currentSetId, name);
    String prefix = presetPrefix(currentSetId, presetKey);

    captureStateToPrefix(prefix);
    persisted.setProperty(prefix + "name", name);
    persisted.setProperty(statePrefix(currentSetId) + "lastPreset", presetKey);

    savePersistentState();
    refreshPresetList();
    presetBox.getSelectionModel().select(name);
    status("Saved preset: " + name);
  }

  private void loadSelectedPreset() {
    if (currentSetId == null || currentSetId.isBlank()) {
      status("Select a set before loading presets.");
      return;
    }

    String name = presetBox.getValue();
    if (name == null || name.isBlank()) {
      status("Select a preset first.");
      return;
    }

    String presetKey = presetNameToKey.get(name);
    if (presetKey == null || presetKey.isBlank()) {
      status("Preset key not found.");
      return;
    }

    applyStateFromPrefix(presetPrefix(currentSetId, presetKey));
    persisted.setProperty(statePrefix(currentSetId) + "lastPreset", presetKey);
    savePersistentState();
    status("Loaded preset: " + name);
  }

  private void deleteSelectedPreset() {
    if (currentSetId == null || currentSetId.isBlank()) {
      status("Select a set before deleting presets.");
      return;
    }

    String name = presetBox.getValue();
    if (name == null || name.isBlank()) {
      status("Select a preset first.");
      return;
    }

    String presetKey = presetNameToKey.get(name);
    if (presetKey == null || presetKey.isBlank()) {
      status("Preset key not found.");
      return;
    }

    clearPrefix(presetPrefix(currentSetId, presetKey));
    String stateKey = statePrefix(currentSetId) + "lastPreset";
    if (presetKey.equals(persisted.getProperty(stateKey, ""))) {
      persisted.remove(stateKey);
    }

    savePersistentState();
    refreshPresetList();
    status("Deleted preset: " + name);
  }

  private String uniquePresetKey(String setId, String baseName) {
    String seed = sanitizeId(baseName);
    if (seed.isBlank()) seed = "preset";

    String key = seed;
    int idx = 2;
    while (persisted.containsKey(presetPrefix(setId, key) + "name")) {
      key = seed + "_" + idx;
      idx++;
    }
    return key;
  }

  private void persistGlobalState() {
    if (projectRoot == null) return;
    persisted.setProperty("global.filter", filterField.getText() == null ? "" : filterField.getText().trim());
    String selectedSet = setBox.getValue();
    if (selectedSet != null && !selectedSet.isBlank()) {
      persisted.setProperty("global.selectedSet", selectedSet);
    }
    savePersistentState();
  }

  private void persistCurrentSetState() {
    if (projectRoot == null) return;
    if (currentSetId == null || currentSetId.isBlank()) return;
    captureStateToPrefix(statePrefix(currentSetId));
    savePersistentState();
  }

  private void captureStateToPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) return;

    clearPrefix(prefix + "sel.");
    clearPrefix(prefix + "active.");
    clearPrefix(prefix + "swap.");

    persisted.setProperty(prefix + "charId", sanitizeId(characterIdField.getText()));
    persisted.setProperty(prefix + "expr", sanitizeId(expressionField.getText()));
    persisted.setProperty(prefix + "autoExpr", Boolean.toString(autoExpression.isSelected()));
    persisted.setProperty(prefix + "randomActiveOnly", Boolean.toString(randomizeActiveOnly.isSelected()));
    persisted.setProperty(prefix + "matchGameFraming", Boolean.toString(matchGameFraming.isSelected()));
    persisted.setProperty(prefix + "attributeFilter", attributeFilterField.getText() == null ? "" : attributeFilterField.getText().trim());
    persisted.setProperty(prefix + "typedAttributes", typedAttributesField.getText() == null ? "" : typedAttributesField.getText().trim());
    persisted.setProperty(prefix + "typedRealtime", Boolean.toString(typedRealtime.isSelected()));
    persisted.setProperty(prefix + "shortforms", shortformsArea.getText() == null ? "" : shortformsArea.getText());
    persisted.setProperty(prefix + "focusX", formatDouble(focusXSlider.getValue()));
    persisted.setProperty(prefix + "focusY", formatDouble(focusYSlider.getValue()));
    persisted.setProperty(prefix + "crop", formatDouble(cropSlider.getValue()));
    persisted.setProperty(prefix + "zoom", formatDouble(zoomSlider.getValue()));
    persisted.setProperty(prefix + "groupOrder", encodeCsv(groupOrder));

    for (Map.Entry<String, ComboBox<LayerOption>> entry : selectors.entrySet()) {
      String group = entry.getKey();
      String groupKey = encodeKey(group);
      LayerOption option = entry.getValue().getValue();
      if (option != null && !option.isNone()) {
        persisted.setProperty(prefix + "sel." + groupKey, option.relativePath);
      }
      CheckBox active = activeGroupChecks.get(group);
      persisted.setProperty(prefix + "active." + groupKey, Boolean.toString(active == null || active.isSelected()));
      CheckBox swap = swapGroupChecks.get(group);
      persisted.setProperty(prefix + "swap." + groupKey, Boolean.toString(swap != null && swap.isSelected()));
    }
  }

  private void applyStateFromPrefix(String prefix) {
    applyingState = true;
    try {
      String fallbackChar = sanitizeId(takeLastPathToken(currentSetId));
      String charId = persisted.getProperty(prefix + "charId", fallbackChar);
      String expr = persisted.getProperty(prefix + "expr", "");

      characterIdField.setText(charId);
      expressionField.setText(expr);
      autoExpression.setSelected(parseBoolean(persisted.getProperty(prefix + "autoExpr"), true));
      randomizeActiveOnly.setSelected(parseBoolean(persisted.getProperty(prefix + "randomActiveOnly"), true));
      matchGameFraming.setSelected(parseBoolean(persisted.getProperty(prefix + "matchGameFraming"), false));
      attributeFilterField.setText(persisted.getProperty(prefix + "attributeFilter", ""));
      typedAttributesField.setText(persisted.getProperty(prefix + "typedAttributes", ""));
      typedRealtime.setSelected(parseBoolean(persisted.getProperty(prefix + "typedRealtime"), true));
      shortformsArea.setText(persisted.getProperty(prefix + "shortforms", DEFAULT_SHORTFORMS));
      refreshShortforms();

      focusXSlider.setValue(parseDouble(persisted.getProperty(prefix + "focusX"), focusXSlider.getValue()));
      focusYSlider.setValue(parseDouble(persisted.getProperty(prefix + "focusY"), focusYSlider.getValue()));
      cropSlider.setValue(parseDouble(persisted.getProperty(prefix + "crop"), cropSlider.getValue()));
      zoomSlider.setValue(parseDouble(persisted.getProperty(prefix + "zoom"), zoomSlider.getValue()));

      List<String> restoredOrder = decodeCsv(persisted.getProperty(prefix + "groupOrder", ""));
      if (!restoredOrder.isEmpty()) {
        List<String> normalized = new ArrayList<>();
        for (String group : restoredOrder) {
          if (selectors.containsKey(group) && !normalized.contains(group)) normalized.add(group);
        }
        for (String group : selectors.keySet()) {
          if (!normalized.contains(group)) normalized.add(group);
        }
        groupOrder.clear();
        groupOrder.addAll(normalized);
      }

      for (String group : selectors.keySet()) {
        ComboBox<LayerOption> combo = selectors.get(group);
        String groupKey = encodeKey(group);
        String selectedPath = persisted.getProperty(prefix + "sel." + groupKey, "");
        if (!selectedPath.isBlank()) {
          LayerOption selected = findByRelativePath(combo, selectedPath);
          if (selected != null) combo.getSelectionModel().select(selected);
        }
        CheckBox active = activeGroupChecks.get(group);
        if (active != null) {
          active.setSelected(parseBoolean(persisted.getProperty(prefix + "active." + groupKey), true));
        }
        CheckBox swap = swapGroupChecks.get(group);
        if (swap != null) {
          swap.setSelected(parseBoolean(persisted.getProperty(prefix + "swap." + groupKey), false));
        }
      }

      refreshGroupRows();

      if (autoExpression.isSelected() && (expressionField.getText() == null || expressionField.getText().isBlank())) {
        updateExpressionFromSelection();
      }
    } finally {
      applyingState = false;
    }
    redrawPreview();
  }

  private LayerOption findByRelativePath(ComboBox<LayerOption> combo, String relativePath) {
    if (combo == null || relativePath == null || relativePath.isBlank()) return null;
    for (LayerOption option : combo.getItems()) {
      if (option != null && !option.isNone() && relativePath.equals(option.relativePath)) {
        return option;
      }
    }
    return null;
  }

  private void loadPersistentState() {
    persisted.clear();
    Path file = statePath();
    if (file == null || !Files.exists(file)) return;
    try (InputStream in = Files.newInputStream(file)) {
      persisted.load(in);
    } catch (Exception ignored) {
    }
  }

  private void reloadGameFramingSettings() {
    gameCharacterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
    gameCharacterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;
    if (projectRoot == null || !projectRoot.isDirectory()) return;
    try {
      VnUiStyleSpec style = VnUiLayoutLoader.loadFromProjectRootWithDiagnostics(projectRoot).style();
      if (style == null) return;
      gameCharacterHeightFactor = clamp(
          style.characterHeightFactor() == null ? DEFAULT_CHARACTER_HEIGHT_FACTOR : style.characterHeightFactor(),
          0.1,
          3.0
      );
      gameCharacterBaselineY = clamp(
          style.characterBaselineY() == null ? DEFAULT_CHARACTER_BASELINE_Y : style.characterBaselineY(),
          -0.5,
          2.0
      );
    } catch (Exception ignored) {
    }
  }

  private void savePersistentState() {
    Path file = statePath();
    if (file == null) return;
    try {
      Path parent = file.getParent();
      if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
      try (OutputStream out = Files.newOutputStream(file)) {
        persisted.store(out, "JVN Layered Image Visualizer State");
      }
    } catch (Exception ignored) {
    }
  }

  private Path statePath() {
    if (projectRoot == null || !projectRoot.isDirectory()) return null;
    return projectRoot.toPath().resolve(STATE_FILE);
  }

  private void clearPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) return;
    List<String> keys = new ArrayList<>(persisted.stringPropertyNames());
    for (String key : keys) {
      if (key.startsWith(prefix)) persisted.remove(key);
    }
  }

  private static String statePrefix(String setId) {
    return "state." + encodeKey(setId) + ".";
  }

  private static String presetPrefix(String setId, String presetKey) {
    return "preset." + encodeKey(setId) + "." + presetKey + ".";
  }

  private static String encodeKey(String raw) {
    if (raw == null || raw.isBlank()) return "_";
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static String formatDouble(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private static String encodeCsv(List<String> values) {
    if (values == null || values.isEmpty()) return "";
    StringBuilder out = new StringBuilder();
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      if (out.length() > 0) out.append(',');
      out.append(encodeKey(value));
    }
    return out.toString();
  }

  private static List<String> decodeCsv(String csv) {
    List<String> out = new ArrayList<>();
    if (csv == null || csv.isBlank()) return out;
    String[] parts = csv.split(",");
    for (String part : parts) {
      String decoded = decodeKey(part.trim());
      if (!decoded.isBlank() && !out.contains(decoded)) out.add(decoded);
    }
    return out;
  }

  private static String decodeKey(String encoded) {
    if (encoded == null || encoded.isBlank() || "_".equals(encoded)) return "";
    try {
      byte[] bytes = Base64.getUrlDecoder().decode(encoded);
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return "";
    }
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    if (raw == null) return fallback;
    String v = raw.trim().toLowerCase(Locale.ROOT);
    if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v)) return true;
    if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "n".equals(v)) return false;
    return fallback;
  }

  private static double parseDouble(String raw, double fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private void copy(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private void status(String text) {
    statusLabel.setText(text == null ? "" : text);
  }

  private boolean isImageFile(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".png")
        || name.endsWith(".jpg")
        || name.endsWith(".jpeg")
        || name.endsWith(".webp");
  }

  private boolean isIgnoredPath(String relative) {
    String path = relative.toLowerCase(Locale.ROOT);
    return path.startsWith(".git/")
        || path.startsWith("build/")
        || path.startsWith(".gradle/")
        || path.contains("/build/")
        || path.startsWith("out/")
        || path.contains("/out/")
        || path.startsWith(".jvn/");
  }

  private LayerOption parseOption(String relative, File file) {
    if (relative == null || file == null) return null;
    String normalizedRelative = relative.replace('\\', '/');
    String fileName = file.getName();
    int dot = fileName.lastIndexOf('.');
    String base = dot > 0 ? fileName.substring(0, dot) : fileName;
    if (base.isBlank()) return null;

    String group = inferGroupFromSetSubfolder(normalizedRelative);
    String label;
    if (!group.isBlank()) {
      label = inferLabelFromFilenameForGroup(base, group);
    } else {
      InferredGroupLabel inferred = inferGroupAndLabelFromFilename(base);
      group = inferred.group();
      label = inferred.label();
    }
    if (group.isBlank()) {
      group = sanitizeId(takeLastPathToken(parentPath(normalizedRelative)));
    }
    if (group.isBlank()) group = "layer";
    if (label.isBlank()) label = "default";

    int sortKey = Integer.MAX_VALUE / 2;
    Matcher m = LEADING_NUMBER.matcher(base);
    if (m.find()) {
      try {
        sortKey = Integer.parseInt(m.group(1));
      } catch (NumberFormatException ignore) {
      }
    }
    return new LayerOption(label, group, normalizedRelative, file, sortKey);
  }

  private String deriveSetId(String relative) {
    return deriveSetIdFromRelative(relative);
  }

  static String deriveSetIdFromRelative(String relative) {
    String path = relative == null ? "" : relative.replace('\\', '/');
    String parent = parentPath(path);
    if (parent.isBlank()) return "(root)";

    String[] parts = parent.split("/");
    if (parts.length >= 3 && "assets".equals(parts[0]) && "characters".equals(parts[1])) {
      return "assets/characters/" + parts[2];
    }
    if (parts.length >= 2 && "assets".equals(parts[0])) {
      return "assets/" + parts[1];
    }
    if (parts.length >= 2) {
      return parts[0] + "/" + parts[1];
    }
    return parent;
  }

  static String chooseSetSelection(String previous, String preferred, List<String> visibleSetIds) {
    return chooseSetSelection(previous, preferred, visibleSetIds, Map.of());
  }

  static String chooseSetSelection(String previous, String preferred, List<String> visibleSetIds, Map<String, Integer> groupCounts) {
    if (visibleSetIds == null || visibleSetIds.isEmpty()) return null;

    if (previous != null && !previous.isBlank() && visibleSetIds.contains(previous)) {
      return previous;
    }

    String firstCharacterSet = findFirstCharacterSet(visibleSetIds);
    String layeredDefault = findLayeredDefaultSet(visibleSetIds, groupCounts);
    if (preferred != null && !preferred.isBlank() && visibleSetIds.contains(preferred)) {
      if (isCharacterSetId(preferred) || firstCharacterSet == null) {
        if (isCharacterSetId(preferred)) return preferred;
        if (layeredSetSize(preferred, groupCounts) > 1) return preferred;
        return layeredDefault == null ? preferred : layeredDefault;
      }
      return firstCharacterSet;
    }

    if (firstCharacterSet != null) {
      return firstCharacterSet;
    }
    if (layeredDefault != null) {
      return layeredDefault;
    }
    return visibleSetIds.get(0);
  }

  static boolean isCharacterSetId(String setId) {
    return setId != null && setId.startsWith("assets/characters/");
  }

  private static String findFirstCharacterSet(List<String> visibleSetIds) {
    if (visibleSetIds == null) return null;
    for (String id : visibleSetIds) {
      if (isCharacterSetId(id)) return id;
    }
    return null;
  }

  private static String findLayeredDefaultSet(List<String> visibleSetIds, Map<String, Integer> groupCounts) {
    if (visibleSetIds == null || visibleSetIds.isEmpty()) return null;
    int bestGroups = 1;
    String best = null;
    for (String id : visibleSetIds) {
      int groups = layeredSetSize(id, groupCounts);
      if (groups > bestGroups) {
        bestGroups = groups;
        best = id;
      }
    }
    return best;
  }

  private static int layeredSetSize(String setId, Map<String, Integer> groupCounts) {
    if (setId == null || groupCounts == null) return 0;
    Integer size = groupCounts.get(setId);
    return size == null ? 0 : Math.max(0, size);
  }

  static String inferGroupFromSetSubfolder(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return "";
    String normalized = relativePath.replace('\\', '/');
    String parent = parentPath(normalized);
    if (parent.isBlank()) return "";
    String setId = deriveSetIdFromRelative(normalized);
    if (setId == null || setId.isBlank() || "(root)".equals(setId)) return "";
    String prefix = setId + "/";
    if (!parent.startsWith(prefix)) return "";
    String remainder = parent.substring(prefix.length());
    if (remainder.isBlank()) return "";
    return sanitizeId(takeLastPathToken(remainder));
  }

  static String inferLabelFromFilenameForGroup(String baseName, String group) {
    String[] tokens = splitTokens(baseName);
    if (tokens.length == 0) return "";
    String normalizedGroup = sanitizeId(group);
    int match = -1;
    for (int i = 0; i < tokens.length; i++) {
      String tokenGroup = normalizeGroupToken(tokens[i]);
      if (!tokenGroup.isBlank() && tokenGroup.equals(normalizedGroup)) {
        match = i;
      }
    }
    if (match >= 0 && match + 1 < tokens.length) {
      return sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(tokens, match + 1, tokens.length)));
    }
    if (match >= 0) {
      return sanitizeLabel(tokens[match]);
    }
    return sanitizeLabel(tokens[tokens.length - 1]);
  }

  private static InferredGroupLabel inferGroupAndLabelFromFilename(String baseName) {
    String[] tokens = splitTokens(baseName);
    if (tokens.length == 0) {
      return new InferredGroupLabel("", "");
    }

    int groupTokenIndex = -1;
    String normalizedGroup = "";
    for (int i = 0; i < tokens.length; i++) {
      String mapped = normalizeGroupToken(tokens[i]);
      if (!mapped.isBlank()) {
        groupTokenIndex = i;
        normalizedGroup = mapped;
      }
    }
    if (groupTokenIndex >= 0) {
      return new InferredGroupLabel(normalizedGroup, inferLabelFromFilenameForGroup(baseName, normalizedGroup));
    }

    if (tokens.length >= 2) {
      String group = sanitizeId(tokens[0]);
      String label = sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(tokens, 1, tokens.length)));
      return new InferredGroupLabel(group, label);
    }

    return new InferredGroupLabel("", sanitizeLabel(tokens[0]));
  }

  private static String[] splitTokens(String raw) {
    if (raw == null || raw.isBlank()) return new String[0];
    return raw.split("[\\s._-]+");
  }

  private static String normalizeGroupToken(String rawToken) {
    String token = sanitizeId(rawToken);
    if (token.isBlank()) return "";
    return GROUP_TOKEN_ALIASES.getOrDefault(token, "");
  }

  static Map<String, String> parseAttributeAssignments(String raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) return out;
    String[] tokens = raw.split("[,\\s]+");
    for (String token : tokens) {
      if (token == null) continue;
      String part = token.trim();
      if (part.isEmpty()) continue;
      String[] kv;
      if (part.contains("=")) {
        kv = part.split("=", 2);
      } else if (part.contains(":")) {
        kv = part.split(":", 2);
      } else if (part.contains("_")) {
        kv = part.split("_", 2);
      } else {
        continue;
      }
      if (kv.length != 2) continue;
      String group = sanitizeId(kv[0]);
      String value = sanitizeId(kv[1]);
      if (group.isBlank() || value.isBlank()) continue;
      out.put(group, value);
    }
    return out;
  }

  static Map<String, String> parseAttributeShortforms(String raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) return out;
    String[] lines = raw.split("\\R");
    for (String line : lines) {
      if (line == null) continue;
      String work = line.trim();
      if (work.isBlank() || work.startsWith("#")) continue;
      int eq = work.indexOf('=');
      if (eq <= 0) continue;
      String key = sanitizeId(work.substring(0, eq));
      String value = work.substring(eq + 1).trim();
      if (key.isBlank() || value.isBlank()) continue;
      out.put(key, value);
    }
    return out;
  }

  private static Slider slider(double min, double max, double value) {
    Slider s = new Slider(min, max, value);
    s.setBlockIncrement(1);
    s.setMajorTickUnit((max - min) / 4.0);
    s.setMinorTickCount(4);
    return s;
  }

  private static HBox sliderRow(String label, Slider slider) {
    Label left = new Label(label);
    left.setMinWidth(52);
    Label value = new Label((int) slider.getValue() + "%");
    slider.valueProperty().addListener((o, ov, nv) -> value.setText((int) Math.round(nv.doubleValue()) + "%"));
    HBox row = new HBox(8, left, slider, value);
    row.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(slider, Priority.ALWAYS);
    return row;
  }

  static String sanitizeId(String raw) {
    if (raw == null) return "";
    String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    s = s.replaceAll("^_+", "").replaceAll("_+$", "");
    return s;
  }

  static String sanitizeLabel(String raw) {
    if (raw == null) return "";
    String s = raw.trim().replaceAll("[\\s]+", "_");
    s = s.replaceAll("[^a-zA-Z0-9_]+", "_");
    s = s.replaceAll("^_+", "").replaceAll("_+$", "");
    return s;
  }

  static String parentPath(String path) {
    if (path == null || path.isBlank()) return "";
    int slash = path.lastIndexOf('/');
    return slash <= 0 ? "" : path.substring(0, slash);
  }

  static String takeLastPathToken(String path) {
    if (path == null || path.isBlank()) return "";
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  private static double clamp(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }

  private static void drawCheckerBackground(GraphicsContext g, double w, double h) {
    double size = 18.0;
    Color c1 = Color.web("#171d28");
    Color c2 = Color.web("#20293a");
    for (int y = 0; y <= h / size + 1; y++) {
      for (int x = 0; x <= w / size + 1; x++) {
        boolean odd = ((x + y) & 1) == 1;
        g.setFill(odd ? c1 : c2);
        g.fillRect(x * size, y * size, size, size);
      }
    }
  }

  private static void drawCenteredText(GraphicsContext g, double w, double h, String text) {
    g.setFill(Color.color(1, 1, 1, 0.65));
    g.setTextAlign(TextAlignment.CENTER);
    g.fillText(text, w / 2.0, h / 2.0);
  }

  private record ViewportFrame(
      double maxW,
      double maxH,
      double srcX,
      double srcY,
      double srcW,
      double srcH,
      double destX,
      double destY,
      double destW,
      double destH) {
    boolean valid() {
      return maxW > 0 && maxH > 0 && srcW > 0 && srcH > 0 && destW > 0 && destH > 0;
    }
  }

  private static final class LayeredSet {
    final String id;
    final Map<String, List<LayerOption>> groups = new LinkedHashMap<>();

    LayeredSet(String id) {
      this.id = Objects.requireNonNullElse(id, "(set)");
    }

    void add(LayerOption option) {
      if (option == null) return;
      groups.computeIfAbsent(option.group, k -> new ArrayList<>()).add(option);
    }
  }

  private static final class LayerOption {
    final String label;
    final String group;
    final String relativePath;
    final File file;
    final int sortKey;

    LayerOption(String label, String group, String relativePath, File file, int sortKey) {
      this.label = label;
      this.group = group;
      this.relativePath = relativePath;
      this.file = file;
      this.sortKey = sortKey;
    }

    static LayerOption none() {
      return new LayerOption(NONE_LABEL, "none", "", null, Integer.MIN_VALUE);
    }

    boolean isNone() {
      return file == null;
    }
  }

  private record InferredGroupLabel(String group, String label) {}
}
