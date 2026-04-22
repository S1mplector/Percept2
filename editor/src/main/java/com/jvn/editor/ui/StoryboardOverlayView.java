package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

public class StoryboardOverlayView extends BorderPane {
  private static final String STATE_FILE = ".jvn/storyboard-overlay.properties";
  private static final String KEY_FOLDER = "folder";
  private static final String KEY_FILTER = "filter";
  private static final String KEY_ENABLED = "enabled";
  private static final String KEY_OPACITY = "opacity";
  private static final String KEY_SELECTED = "selected";
  private static final String KEY_FOLLOW_ACTIVE = "followActive";

  private final Label titleLabel = new Label("Storyboard Overlay");
  private final Label summaryLabel =
      new Label("Ghost storyboard frames over JES and VNS previews for staging reference.");
  private final Label targetLabel = new Label("Active preview: open a JES or VNS tab.");
  private final Label sourceLabel = new Label("Source: not scanned");
  private final Label framesSummaryLabel = new Label("No frames loaded.");
  private final Label matchLabel = new Label("No active scene match.");
  private final Label statusLabel = new Label("");
  private final TextField folderField = new TextField();
  private final TextField filterField = new TextField();
  private final ListView<StoryboardFrame> framesList = new ListView<>();
  private final ImageView previewImage = new ImageView();
  private final Label previewPathLabel = new Label("Select a storyboard frame.");
  private final Label previewMetaLabel = new Label("");
  private final CheckBox enabledCheck = new CheckBox("Show overlay in preview");
  private final CheckBox followActiveCheck = new CheckBox("Follow active scene");
  private final Slider opacitySlider = new Slider(5, 100, 35);
  private final Label opacityValueLabel = new Label("35%");
  private final Button previousButton = new Button("Previous");
  private final Button nextButton = new Button("Next");
  private final Button matchButton = new Button("Jump To Match");
  private final Button revealButton = new Button("Reveal Frame");
  private final Button browseButton = new Button("Browse");
  private final Button autoButton = new Button("Auto");
  private final Button refreshButton = new Button("Refresh");

  private final List<StoryboardFrame> allFrames = new ArrayList<>();
  private final Map<Path, Image> imageCache = new HashMap<>();
  private final Properties persisted = new Properties();

  private File projectRoot;
  private File activeScriptFile;
  private StoryboardFrame activeMatchFrame;
  private boolean applyingState;
  private Consumer<StoryboardOverlayState> onOverlayChanged;
  private Task<StoryboardOverlayCatalog.ScanResult> scanTask;

  public StoryboardOverlayView() {
    getStyleClass().add("layout-launcher-root");
    titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");
    summaryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a3b8;");
    summaryLabel.setWrapText(true);
    targetLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #aeb6c7;");
    targetLabel.setWrapText(true);
    sourceLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #8899aa;");
    sourceLabel.setWrapText(true);
    framesSummaryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a3b8;");
    matchLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #d4b07c;");
    matchLabel.setWrapText(true);
    statusLabel.setStyle("-fx-font-size: 10px;");
    statusLabel.setWrapText(true);

    folderField.setPromptText("Auto-detect storyboard folder");
    folderField.getStyleClass().add("layout-launcher-field");
    filterField.setPromptText("Filter frames...");
    filterField.getStyleClass().add("layout-launcher-field");

    styleActionButton(browseButton, CssIcon.folder("#d5b36a"));
    styleActionButton(autoButton, CssIcon.search("#d6dbe5"));
    styleActionButton(refreshButton, CssIcon.redo("#7ec8e3"));
    styleActionButton(previousButton, CssIcon.arrowLeft("#d6dbe5"));
    styleActionButton(nextButton, CssIcon.arrowRight("#d6dbe5"));
    styleActionButton(matchButton, CssIcon.link("#f0c27a"));
    styleActionButton(revealButton, CssIcon.expand("#d6dbe5"));
    enabledCheck.setGraphic(CssIcon.visibility("#d6dbe5"));
    followActiveCheck.setGraphic(CssIcon.link("#d5b36a"));
    enabledCheck.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
    followActiveCheck.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);

    browseButton.setTooltip(new Tooltip("Choose a storyboard folder"));
    autoButton.setTooltip(new Tooltip("Return to automatic folder discovery"));
    refreshButton.setTooltip(new Tooltip("Rescan storyboard frames"));
    previousButton.setTooltip(new Tooltip("Select previous frame"));
    nextButton.setTooltip(new Tooltip("Select next frame"));
    matchButton.setTooltip(new Tooltip("Jump to the strongest match for the active JES/VNS file"));
    revealButton.setTooltip(new Tooltip("Open the selected frame file"));

    filterField.textProperty().addListener((obs, oldValue, newValue) -> {
      applyFilter();
      saveState();
    });
    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.DOWN && !framesList.getItems().isEmpty()) {
        framesList.requestFocus();
        int index = framesList.getSelectionModel().getSelectedIndex();
        framesList.getSelectionModel().select(index < 0 ? 0 : Math.min(index + 1, framesList.getItems().size() - 1));
        e.consume();
      } else if (e.getCode() == KeyCode.ENTER && framesList.getItems().size() == 1) {
        framesList.getSelectionModel().selectFirst();
        framesList.requestFocus();
        e.consume();
      } else if (e.getCode() == KeyCode.ESCAPE && !filterField.getText().isBlank()) {
        filterField.clear();
        e.consume();
      }
    });
    folderField.setOnAction(e -> refreshCatalog());
    folderField.focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) refreshCatalog();
    });
    browseButton.setOnAction(e -> chooseFolder());
    autoButton.setOnAction(e -> {
      folderField.clear();
      refreshCatalog();
    });
    refreshButton.setOnAction(e -> refreshCatalog());

    framesList.setPlaceholder(new Label("No storyboard frames found"));
    framesList.setCellFactory(list -> new StoryboardFrameCell());
    framesList.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        revealSelectedFrame();
        e.consume();
      } else if (e.getCode() == KeyCode.LEFT) {
        selectRelativeFrame(-1);
        e.consume();
      } else if (e.getCode() == KeyCode.RIGHT) {
        selectRelativeFrame(1);
        e.consume();
      } else if (e.getCode() == KeyCode.F && (e.isShortcutDown() || e.isMetaDown())) {
        filterField.requestFocus();
        filterField.selectAll();
        e.consume();
      }
    });
    framesList.getSelectionModel().selectedItemProperty().addListener((obs, oldFrame, newFrame) -> {
      updatePreview(newFrame);
      emitOverlayChanged();
      saveState();
    });
    framesList.setOnMouseClicked(e -> {
      if (e.getClickCount() >= 2) revealSelectedFrame();
    });

    enabledCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
      emitOverlayChanged();
      saveState();
    });
    followActiveCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
      if (newValue) applyActiveContextMatch(true);
      saveState();
    });
    opacitySlider.valueProperty().addListener((obs, oldValue, newValue) -> {
      opacityValueLabel.setText(Integer.toString((int) Math.round(newValue.doubleValue())) + "%");
      emitOverlayChanged();
      saveState();
    });

    previousButton.setOnAction(e -> selectRelativeFrame(-1));
    nextButton.setOnAction(e -> selectRelativeFrame(1));
    matchButton.setOnAction(e -> jumpToActiveMatch());
    revealButton.setOnAction(e -> revealSelectedFrame());

    previewImage.setPreserveRatio(true);
    previewImage.setSmooth(true);
    previewImage.setFitWidth(280);
    previewImage.setFitHeight(220);

    previewPathLabel.setWrapText(true);
    previewMetaLabel.setWrapText(true);
    previewMetaLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #99aabb;");

    Label filterHintLabel = new Label("Down: jump to list  •  Enter: select single result  •  Cmd/Ctrl+F: focus filter");
    filterHintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #7f8da0;");
    filterHintLabel.setWrapText(true);

    HBox folderRow = new HBox(6, folderField, browseButton, autoButton, refreshButton);
    HBox.setHgrow(folderField, Priority.ALWAYS);

    HBox overlayRow = new HBox(10, enabledCheck, followActiveCheck, new Label("Opacity"), opacitySlider, opacityValueLabel);
    overlayRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(opacitySlider, Priority.ALWAYS);

    HBox navigationRow = new HBox(8, previousButton, nextButton, matchButton, revealButton);
    navigationRow.setAlignment(Pos.CENTER_LEFT);

    VBox header = new VBox(6, titleLabel, summaryLabel, targetLabel, sourceLabel, framesSummaryLabel, matchLabel, folderRow, filterField, filterHintLabel, overlayRow, navigationRow, statusLabel);
    header.setPadding(new Insets(10, 10, 8, 10));

    VBox previewBox = new VBox(8, previewImage, previewPathLabel, previewMetaLabel);
    previewBox.setPadding(new Insets(10));
    previewBox.setStyle("-fx-border-color: #2a2f3a; -fx-border-width: 1 0 0 0;");

    VBox content = new VBox(framesList, new Separator(), previewBox);
    VBox.setVgrow(framesList, Priority.ALWAYS);

    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

    setTop(header);
    setCenter(scroll);

    updateControlAvailability();
  }

  private void styleActionButton(Button button, Region icon) {
    button.getStyleClass().add("layout-launcher-button");
    button.setGraphic(icon);
    button.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
    button.setGraphicTextGap(6);
  }

  public void setProjectRoot(File projectRoot) {
    if (Objects.equals(this.projectRoot, projectRoot)) {
      updateSummaryForProject();
      return;
    }
    this.projectRoot = projectRoot;
    loadState();
    refreshCatalog();
  }

  public void setActivePreviewLabel(String label) {
    if (label == null || label.isBlank()) {
      targetLabel.setText("Active preview: open a JES or VNS tab.");
      return;
    }
    targetLabel.setText(label);
  }

  public void setActiveScriptFile(File activeScriptFile) {
    File normalized = activeScriptFile == null ? null : activeScriptFile.getAbsoluteFile();
    if (Objects.equals(this.activeScriptFile, normalized)) return;
    this.activeScriptFile = normalized;
    applyActiveContextMatch(followActiveCheck.isSelected());
  }

  public void setOnOverlayChanged(Consumer<StoryboardOverlayState> onOverlayChanged) {
    this.onOverlayChanged = onOverlayChanged;
    emitOverlayChanged();
  }

  public void refreshCatalog() {
    saveState();
    if (scanTask != null) scanTask.cancel();
    allFrames.clear();
    framesList.getItems().clear();
    activeMatchFrame = null;
    previewImage.setImage(null);
    previewPathLabel.setText("Select a storyboard frame.");
    previewMetaLabel.setText("");
    imageCache.clear();
    updateControlAvailability();
    updateSummaryForProject();
    emitOverlayChanged();

    if (projectRoot == null || !projectRoot.isDirectory()) {
      sourceLabel.setText("Source: unavailable");
      statusLabel.setText("Open a project to browse storyboard frames.");
      return;
    }

    sourceLabel.setText("Source: scanning...");
    statusLabel.setText("Scanning storyboard frames...");

    final String folderOverride = folderField.getText();
    final File root = projectRoot;
    Task<StoryboardOverlayCatalog.ScanResult> task = new Task<>() {
      @Override
      protected StoryboardOverlayCatalog.ScanResult call() {
        return StoryboardOverlayCatalog.scan(root.toPath(), folderOverride);
      }
    };
    scanTask = task;
    task.setOnSucceeded(event -> {
      if (scanTask != task || task.isCancelled()) return;
      applyScanResult(task.getValue());
    });
    task.setOnFailed(event -> {
      if (scanTask != task || task.isCancelled()) return;
      Throwable failure = task.getException();
      sourceLabel.setText("Source: unavailable");
      statusLabel.setText("Storyboard scan failed: " + (failure == null ? "unknown error" : failure.getMessage()));
      updateControlAvailability();
    });
    Thread scanThread = new Thread(task, "jvn-storyboard-overlay-scan");
    scanThread.setDaemon(true);
    scanThread.start();
  }

  public void dispose() {
    if (scanTask != null) scanTask.cancel();
    saveState();
    imageCache.clear();
  }

  private void applyScanResult(StoryboardOverlayCatalog.ScanResult result) {
    sourceLabel.setText("Source: " + result.sourceLabel());
    statusLabel.setText(result.statusMessage());
    String desiredSelection = persisted.getProperty(KEY_SELECTED, "");
    StoryboardFrame previous = framesList.getSelectionModel().getSelectedItem();
    String previousSelection = previous == null ? desiredSelection : encodePath(previous.path());

    allFrames.clear();
    if (result.frames() != null) {
      for (Path frame : result.frames()) {
        allFrames.add(new StoryboardFrame(frame, StoryboardOverlayCatalog.displayPath(projectRoot == null ? null : projectRoot.toPath(), frame)));
      }
    }
    applyFilter();
    activeMatchFrame = findBestMatch(framesList.getItems());
    updateMatchLabel();
    reselectFrame(previousSelection);
    updateSummaryForProject();
    updateControlAvailability();
    emitOverlayChanged();
  }

  private void applyFilter() {
    StoryboardFrame currentSelection = framesList.getSelectionModel().getSelectedItem();
    String currentSelectionPath = currentSelection == null ? "" : encodePath(currentSelection.path());
    String query = filterField.getText();
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      framesList.setItems(FXCollections.observableArrayList(allFrames));
      reselectFilteredFrame(currentSelectionPath);
      activeMatchFrame = findBestMatch(framesList.getItems());
      updateFrameSummary();
      updateMatchLabel();
      updateControlAvailability();
      return;
    }
    List<StoryboardFrame> filtered = new ArrayList<>();
    for (StoryboardFrame frame : allFrames) {
      String haystack = frame.displayPath().toLowerCase(Locale.ROOT);
      if (haystack.contains(normalized)) filtered.add(frame);
    }
    framesList.setItems(FXCollections.observableArrayList(filtered));
    reselectFilteredFrame(currentSelectionPath);
    activeMatchFrame = findBestMatch(framesList.getItems());
    updateFrameSummary();
    updateMatchLabel();
    updateControlAvailability();
  }

  private void reselectFilteredFrame(String encodedPath) {
    if (encodedPath != null && !encodedPath.isBlank()) {
      for (StoryboardFrame frame : framesList.getItems()) {
        if (encodedPath.equals(encodePath(frame.path()))) {
          framesList.getSelectionModel().select(frame);
          return;
        }
      }
    }
    if (framesList.getSelectionModel().getSelectedItem() == null && !framesList.getItems().isEmpty()) {
      framesList.getSelectionModel().selectFirst();
    }
  }

  private void reselectFrame(String encodedPath) {
    if (encodedPath == null || encodedPath.isBlank()) {
      if (!framesList.getItems().isEmpty()) {
        framesList.getSelectionModel().selectFirst();
      }
      return;
    }
    for (StoryboardFrame frame : framesList.getItems()) {
      if (encodedPath.equals(encodePath(frame.path()))) {
        framesList.getSelectionModel().select(frame);
        framesList.scrollTo(frame);
        return;
      }
    }
    if (!framesList.getItems().isEmpty()) {
      framesList.getSelectionModel().selectFirst();
    }
  }

  private void updatePreview(StoryboardFrame frame) {
    if (frame == null) {
      previewImage.setImage(null);
      previewPathLabel.setText("Select a storyboard frame.");
      previewMetaLabel.setText("");
      updateControlAvailability();
      return;
    }
    previewPathLabel.setText(frame.displayPath());
    Image image = loadImage(frame.path());
    previewImage.setImage(image);
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(projectRoot);
    int selectedIndex = Math.max(0, framesList.getSelectionModel().getSelectedIndex()) + 1;
    int totalShown = framesList.getItems().size();
    if (image == null || image.isError()) {
      previewMetaLabel.setText("Preview unavailable.");
    } else {
      int imageWidth = (int) Math.round(image.getWidth());
      int imageHeight = (int) Math.round(image.getHeight());
      String match = imageWidth == dims.width() && imageHeight == dims.height()
          ? "Matches project viewport"
          : "Scaled to " + dims.width() + "x" + dims.height();
      previewMetaLabel.setText(imageWidth + "x" + imageHeight + "  •  " + match + "  •  Frame " + selectedIndex + " of " + totalShown);
    }
    updateControlAvailability();
  }

  private Image loadImage(Path path) {
    if (path == null) return null;
    Path normalized = path.toAbsolutePath().normalize();
    Image cached = imageCache.get(normalized);
    if (cached != null) return cached;
    try {
      Image image = new Image(normalized.toUri().toString(), false);
      imageCache.put(normalized, image);
      return image;
    } catch (Exception ex) {
      return null;
    }
  }

  private void emitOverlayChanged() {
    if (onOverlayChanged == null) return;
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    Image image = selected == null ? null : loadImage(selected.path());
    boolean enabled = enabledCheck.isSelected() && image != null && !image.isError();
    onOverlayChanged.accept(
        enabled
            ? new StoryboardOverlayState(true, image, opacitySlider.getValue() / 100.0, selected.displayPath())
            : StoryboardOverlayState.none());
  }

  private void selectRelativeFrame(int direction) {
    if (framesList.getItems().isEmpty()) return;
    int current = framesList.getSelectionModel().getSelectedIndex();
    if (current < 0) current = 0;
    int next = current + direction;
    if (next < 0) next = framesList.getItems().size() - 1;
    if (next >= framesList.getItems().size()) next = 0;
    framesList.getSelectionModel().select(next);
    framesList.scrollTo(next);
  }

  private void chooseFolder() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Select Storyboard Folder");
    Path initial = resolveInitialFolder();
    if (initial != null && Files.isDirectory(initial)) {
      chooser.setInitialDirectory(initial.toFile());
    } else if (projectRoot != null && projectRoot.isDirectory()) {
      chooser.setInitialDirectory(projectRoot);
    }
    File selected = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
    if (selected == null) return;
    folderField.setText(StoryboardOverlayCatalog.displayPath(projectRoot == null ? null : projectRoot.toPath(), selected.toPath()));
    refreshCatalog();
  }

  private void jumpToActiveMatch() {
    if (activeMatchFrame == null) return;
    framesList.getSelectionModel().select(activeMatchFrame);
    framesList.scrollTo(activeMatchFrame);
  }

  private void revealSelectedFrame() {
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    try {
      if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(selected.path().toFile());
      }
    } catch (Exception ex) {
      statusLabel.setText("Could not open frame: " + ex.getMessage());
    }
  }

  private Path resolveInitialFolder() {
    if (projectRoot == null) return null;
    String override = folderField.getText();
    if (override == null || override.isBlank()) return projectRoot.toPath();
    Path path = Path.of(override.trim());
    if (!path.isAbsolute()) path = projectRoot.toPath().resolve(path).normalize();
    return path.toAbsolutePath().normalize();
  }

  private void updateSummaryForProject() {
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(projectRoot);
    summaryLabel.setText(
        "Ghost storyboard frames over JES and VNS previews for staging reference. "
            + "Frames fit to "
            + dims.width()
            + "x"
            + dims.height()
            + ".");
    updateFrameSummary();
    updateMatchLabel();
  }

  private void updateControlAvailability() {
    boolean hasFrames = !framesList.getItems().isEmpty();
    boolean hasSelection = framesList.getSelectionModel().getSelectedItem() != null;
    enabledCheck.setDisable(!hasSelection);
    opacitySlider.setDisable(!hasSelection);
    followActiveCheck.setDisable(activeScriptFile == null);
    previousButton.setDisable(!hasFrames);
    nextButton.setDisable(!hasFrames);
    matchButton.setDisable(activeMatchFrame == null);
    revealButton.setDisable(!hasSelection);
  }

  private void loadState() {
    persisted.clear();
    applyingState = true;
    try {
      folderField.clear();
      filterField.clear();
      enabledCheck.setSelected(false);
      followActiveCheck.setSelected(true);
      opacitySlider.setValue(35.0);
      Path stateFile = stateFile();
      if (stateFile != null && Files.isRegularFile(stateFile)) {
        try (InputStream in = Files.newInputStream(stateFile)) {
          persisted.load(in);
        }
      }
      folderField.setText(persisted.getProperty(KEY_FOLDER, ""));
      filterField.setText(persisted.getProperty(KEY_FILTER, ""));
      enabledCheck.setSelected(Boolean.parseBoolean(persisted.getProperty(KEY_ENABLED, "false")));
      followActiveCheck.setSelected(Boolean.parseBoolean(persisted.getProperty(KEY_FOLLOW_ACTIVE, "true")));
      opacitySlider.setValue(parseOpacity(persisted.getProperty(KEY_OPACITY), 35.0));
      opacityValueLabel.setText(Integer.toString((int) Math.round(opacitySlider.getValue())) + "%");
    } catch (Exception ignored) {
    } finally {
      applyingState = false;
    }
  }

  private void saveState() {
    if (applyingState || projectRoot == null || !projectRoot.isDirectory()) return;
    Path stateFile = stateFile();
    if (stateFile == null) return;
    try {
      Files.createDirectories(stateFile.getParent());
      Properties props = new Properties();
      props.setProperty(KEY_FOLDER, textOrEmpty(folderField.getText()));
      props.setProperty(KEY_FILTER, textOrEmpty(filterField.getText()));
      props.setProperty(KEY_ENABLED, Boolean.toString(enabledCheck.isSelected()));
      props.setProperty(KEY_FOLLOW_ACTIVE, Boolean.toString(followActiveCheck.isSelected()));
      props.setProperty(KEY_OPACITY, Double.toString(opacitySlider.getValue()));
      StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
      props.setProperty(KEY_SELECTED, selected == null ? "" : encodePath(selected.path()));
      try (OutputStream out = Files.newOutputStream(stateFile)) {
        props.store(out, "JVN Storyboard Overlay");
      }
      persisted.clear();
      persisted.putAll(props);
    } catch (Exception ignored) {
    }
  }

  private Path stateFile() {
    if (projectRoot == null || !projectRoot.isDirectory()) return null;
    return projectRoot.toPath().resolve(STATE_FILE);
  }

  private String encodePath(Path path) {
    return StoryboardOverlayCatalog.displayPath(projectRoot == null ? null : projectRoot.toPath(), path);
  }

  private static String textOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private static double parseOpacity(String raw, double fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      double value = Double.parseDouble(raw.trim());
      if (!Double.isFinite(value)) return fallback;
      return Math.max(5.0, Math.min(100.0, value));
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private void updateFrameSummary() {
    int total = allFrames.size();
    int shown = framesList.getItems().size();
    StoryboardFrame selected = framesList.getSelectionModel().getSelectedItem();
    if (total <= 0) {
      framesSummaryLabel.setText("No frames loaded.");
      return;
    }
    StringBuilder text = new StringBuilder();
    text.append(total).append(" frame").append(total == 1 ? "" : "s");
    if (shown != total) {
      text.append("  •  ").append(shown).append(" shown");
    }
    if (selected != null) {
      int index = Math.max(0, framesList.getItems().indexOf(selected)) + 1;
      text.append("  •  selected ").append(index).append("/").append(shown);
    }
    framesSummaryLabel.setText(text.toString());
  }

  private void updateMatchLabel() {
    if (activeScriptFile == null) {
      matchLabel.setText("No active JES or VNS scene.");
      return;
    }
    if (activeMatchFrame == null) {
      matchLabel.setText("No probable storyboard match for " + activeScriptFile.getName() + ".");
      return;
    }
    matchLabel.setText("Best match for " + activeScriptFile.getName() + ": " + activeMatchFrame.displayPath());
  }

  private void applyActiveContextMatch(boolean autoSelect) {
    activeMatchFrame = findBestMatch(framesList.getItems());
    updateMatchLabel();
    if (autoSelect && activeMatchFrame != null) {
      framesList.getSelectionModel().select(activeMatchFrame);
      framesList.scrollTo(activeMatchFrame);
    }
    updateControlAvailability();
  }

  private StoryboardFrame findBestMatch(List<StoryboardFrame> candidates) {
    if (activeScriptFile == null || candidates == null || candidates.isEmpty()) return null;
    String scriptStem = stripExtension(activeScriptFile.getName()).toLowerCase(Locale.ROOT);
    if (scriptStem.isBlank()) return null;
    List<String> strongTokens = significantTokens(activeScriptFile.toPath());
    return candidates.stream()
        .map(frame -> new MatchCandidate(frame, scoreFrameMatch(frame, scriptStem, strongTokens)))
        .filter(candidate -> candidate.score > 0)
        .max(Comparator.comparingInt(MatchCandidate::score)
            .thenComparing(candidate -> candidate.frame.displayPath(), String.CASE_INSENSITIVE_ORDER))
        .map(MatchCandidate::frame)
        .orElse(null);
  }

  private static int scoreFrameMatch(StoryboardFrame frame, String scriptStem, List<String> tokens) {
    if (frame == null) return Integer.MIN_VALUE;
    String path = frame.displayPath().toLowerCase(Locale.ROOT);
    String fileStem = stripExtension(frame.fileName()).toLowerCase(Locale.ROOT);
    int score = 0;
    if (fileStem.equals(scriptStem)) score += 1000;
    if (path.contains("/" + scriptStem) || path.contains(scriptStem + "/")) score += 420;
    if (path.contains(scriptStem)) score += 240;
    for (String token : tokens) {
      if (token.length() < 2) continue;
      if (fileStem.equals(token)) score += 220;
      else if (fileStem.contains(token)) score += 120;
      if (path.contains("/" + token + "/")) score += 75;
      else if (path.contains(token)) score += 35;
      if (token.chars().allMatch(Character::isDigit)) score += path.contains(token) ? 40 : 0;
    }
    return score;
  }

  private static List<String> significantTokens(Path path) {
    LinkedHashSet<String> tokens = new LinkedHashSet<>();
    if (path == null) return List.of();
    for (Path part : path) {
      String value = part == null ? "" : part.toString();
      for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
        if (token.isBlank()) continue;
        if (token.length() == 1 && !Character.isDigit(token.charAt(0))) continue;
        if (isIgnoredMatchToken(token)) continue;
        tokens.add(token);
      }
    }
    return new ArrayList<>(tokens);
  }

  private static boolean isIgnoredMatchToken(String token) {
    return switch (token) {
      case "scripts", "script", "src", "main", "story", "stories", "jes", "vns", "game", "project", "assets" -> true;
      default -> false;
    };
  }

  private static String stripExtension(String value) {
    if (value == null) return "";
    int dot = value.lastIndexOf('.');
    return dot <= 0 ? value : value.substring(0, dot);
  }

  private record StoryboardFrame(Path path, String displayPath) {
    String fileName() {
      Path fileName = path == null ? null : path.getFileName();
      return fileName == null ? displayPath : fileName.toString();
    }

    String directoryPath() {
      String fileName = fileName();
      if (displayPath == null || displayPath.equals(fileName)) return "";
      int end = displayPath.length() - fileName.length();
      if (end <= 0) return displayPath;
      String prefix = displayPath.substring(0, end);
      return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }
  }

  private record MatchCandidate(StoryboardFrame frame, int score) {
  }

  private static final class StoryboardFrameCell extends ListCell<StoryboardFrame> {
    private final Label nameLabel = new Label();
    private final Label pathLabel = new Label();
    private final VBox content = new VBox(2, nameLabel, pathLabel);

    private StoryboardFrameCell() {
      nameLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");
      pathLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #8ea0b7;");
      pathLabel.setWrapText(true);
    }

    @Override
    protected void updateItem(StoryboardFrame item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setGraphic(null);
        return;
      }
      nameLabel.setText(item.fileName());
      pathLabel.setText(item.directoryPath());
      setText(null);
      setGraphic(content);
    }
  }
}
