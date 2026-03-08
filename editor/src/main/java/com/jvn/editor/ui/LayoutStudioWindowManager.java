package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Opens specialized layout/menu visual editors in external Studio windows.
 * This keeps the main editor focused while providing larger purpose-built workspaces.
 */
public class LayoutStudioWindowManager {
  public enum Kind {
    MENU_SCREEN,
    MENU_LAYOUT,
    MENU_STYLE,
    DIALOGUE_LAYOUT
  }

  private final Stage owner;
  private final Consumer<File> runProjectHandler;
  private final Map<String, LayoutStudioWindow> windows = new LinkedHashMap<>();

  public LayoutStudioWindowManager(Stage owner) {
    this(owner, null);
  }

  public LayoutStudioWindowManager(Stage owner, Consumer<File> runProjectHandler) {
    this.owner = owner;
    this.runProjectHandler = runProjectHandler;
  }

  public boolean supports(File file) {
    return detectKind(file) != null;
  }

  public void open(File file, File projectRoot, Consumer<String> statusSink) {
    if (file == null) return;
    Kind kind = detectKind(file);
    if (kind == null) return;

    String key = canonicalPath(file);
    LayoutStudioWindow existing = windows.get(key);
    if (existing != null) {
      existing.setProjectRoot(projectRoot);
      existing.focus();
      if (statusSink != null) statusSink.accept("Focused Studio: " + file.getName());
      return;
    }

    LayoutStudioWindow window = new LayoutStudioWindow(
        owner,
        file,
        kind,
        projectRoot,
        statusSink,
        runProjectHandler,
        () -> windows.remove(key)
    );
    windows.put(key, window);
    window.show();
    if (statusSink != null) statusSink.accept("Opened in Studio: " + file.getName());
  }

  public boolean requestCloseAll() {
    List<LayoutStudioWindow> copy = new ArrayList<>(windows.values());
    for (LayoutStudioWindow window : copy) {
      if (window == null) continue;
      if (!window.requestClose()) return false;
    }
    return true;
  }

  public boolean saveAllDirty(Consumer<String> statusSink) {
    boolean ok = true;
    List<LayoutStudioWindow> copy = new ArrayList<>(windows.values());
    for (LayoutStudioWindow window : copy) {
      if (window == null || !window.isDirty()) continue;
      if (!window.saveIfDirty()) {
        ok = false;
        if (statusSink != null) {
          statusSink.accept("Failed to save Studio file: " + window.fileName());
        }
      }
    }
    return ok;
  }

  private static Kind detectKind(File file) {
    if (file == null) return null;
    String name = file.getName().toLowerCase(Locale.ROOT);
    String path = file.getPath().replace('\\', '/').toLowerCase(Locale.ROOT);

    if (name.endsWith(".menu")) return Kind.MENU_SCREEN;
    if (name.endsWith(".layout") && (path.contains("/config/menu/layouts/") || path.contains("/menu/layouts/") || path.contains("/config/menu/"))) {
      return Kind.MENU_LAYOUT;
    }
    if (name.endsWith(".style") || path.contains("/config/menu/styles/")) return Kind.MENU_STYLE;
    if ("dialogue.layout".equals(name) || (name.endsWith(".layout") && (path.contains("/config/ui/") || path.contains("/config/vn/")))) {
      return Kind.DIALOGUE_LAYOUT;
    }
    return null;
  }

  private static String canonicalPath(File file) {
    if (file == null) return "";
    try {
      return file.getCanonicalPath();
    } catch (Exception ignored) {
      return file.getAbsolutePath();
    }
  }

  private static void applyLinuxDefaultWindowState(Stage stage) {
    if (stage == null || !isLinux()) return;
    stage.setIconified(false);
    stage.setMaximized(true);
    Platform.runLater(() -> {
      stage.setIconified(false);
      stage.setMaximized(true);
    });
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
  }

  private static String normalizeLineEndings(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String t = value.trim();
    return t.isBlank() ? fallback : t;
  }

  private static String sanitizeId(String raw) {
    if (raw == null) return "";
    String t = raw.trim().toLowerCase(Locale.ROOT);
    if (t.isBlank()) return "";
    t = t.replace('-', '_').replace(' ', '_');
    t = t.replaceAll("[^a-z0-9_]", "");
    t = t.replaceAll("_+", "_");
    if (t.startsWith("_")) t = t.substring(1);
    if (t.endsWith("_")) t = t.substring(0, t.length() - 1);
    return t;
  }

  private static final class LayoutStudioWindow {
    private final Stage stage;
    private final File file;
    private final Kind kind;
    private final Consumer<String> externalStatus;
    private final Consumer<File> runProjectHandler;
    private final Runnable onClosed;

    private File projectRoot;
    private boolean syncing;
    private boolean dirty;
    private String savedSnapshot = "";

    private final JavaCodeEditor codeEditor = new JavaCodeEditor();
    private final Label status = new Label("Ready.");
    private final Label fileLabel = new Label();
    private final Label dirtyBadge = new Label();

    private final ToggleButton bDesign = new ToggleButton();
    private final ToggleButton bCode = new ToggleButton();
    private final ToggleButton bSplit = new ToggleButton();
    private final ToggleButton previewToggle = new ToggleButton("Preview");

    private final BorderPane centerHost = new BorderPane();
    private final SplitPane split = new SplitPane();
    private final BorderPane designHost;

    private final TextField assetPathField = new TextField();
    private final ComboBox<String> assetKeyBox = new ComboBox<>();
    private final TextField assetItemIdField = new TextField();

    private final Button saveButton = new Button();
    private final Button runButton = new Button();
    private final Button reloadButton = new Button();
    private final Button revealButton = new Button();
    private final Button maximizeButton = new Button();

    private final Button browseAssetButton = new Button();
    private final Button importAssetButton = new Button();
    private final Button revealAssetButton = new Button();
    private final Button clearAssetButton = new Button();
    private final Button copyPathButton = new Button();
    private final Button applyPathButton = new Button();

    private final MenuScreenVisualEditor menuScreenVisualEditor;
    private final MenuLayoutVisualEditor menuLayoutVisualEditor;
    private final MenuStyleVisualEditor menuStyleVisualEditor;
    private final DialogueLayoutEditorView dialogueLayoutVisualEditor;
    private final Node designNode;
    private final boolean designPreviewEnabled;

    LayoutStudioWindow(Stage owner,
                       File file,
                       Kind kind,
                       File projectRoot,
                       Consumer<String> statusSink,
                       Consumer<File> runProjectHandler,
                       Runnable onClosed) {
      this.file = file;
      this.kind = kind;
      this.projectRoot = projectRoot;
      this.externalStatus = statusSink;
      this.runProjectHandler = runProjectHandler;
      this.onClosed = onClosed;
      this.designPreviewEnabled = true;

      this.menuScreenVisualEditor = (kind == Kind.MENU_SCREEN) ? new MenuScreenVisualEditor() : null;
      this.menuLayoutVisualEditor = (kind == Kind.MENU_LAYOUT) ? new MenuLayoutVisualEditor() : null;
      this.menuStyleVisualEditor = (kind == Kind.MENU_STYLE) ? new MenuStyleVisualEditor() : null;
      this.dialogueLayoutVisualEditor = (kind == Kind.DIALOGUE_LAYOUT) ? new DialogueLayoutEditorView() : null;
      this.designNode = resolveDesignNode();
      this.designHost = buildDesignHost();
      codeEditor.useDslHighlighting();

      this.stage = new Stage();
      stage.setResizable(true);
      stage.setMinWidth(1200);
      stage.setMinHeight(760);

      BorderPane root = new BorderPane();
      root.getStyleClass().add("layout-studio-root");
      root.setTop(buildToolbar());
      root.setCenter(buildContent());
      root.setRight(buildUtilitiesPane());
      root.setBottom(buildStatusBar());

      Scene scene = new Scene(root, 1460, 900);
      scene.getAccelerators().put(
          new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.SHORTCUT_DOWN),
          this::save
      );
      scene.getAccelerators().put(
          new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.ENTER, javafx.scene.input.KeyCombination.SHORTCUT_DOWN),
          this::saveAndRunProject
      );
      EditorTheme.apply(scene);
      stage.setScene(scene);
      applyLinuxDefaultWindowState(stage);

      configureVisualEditors();
      bindSync();
      bindButtons();
      applyMode(Mode.CODE);
      loadFromDisk();

      stage.setOnCloseRequest(e -> {
        if (!confirmCloseIfDirty()) {
          e.consume();
        }
      });
      stage.setOnHidden(e -> {
        if (onClosed != null) onClosed.run();
      });
    }

    void show() {
      stage.show();
      applyLinuxDefaultWindowState(stage);
      Platform.runLater(() -> {
        stage.toFront();
        codeEditor.requestFocus();
      });
    }

    void focus() {
      if (!stage.isShowing()) {
        stage.show();
      }
      stage.toFront();
      stage.requestFocus();
    }

    boolean requestClose() {
      if (!confirmCloseIfDirty()) return false;
      stage.hide();
      return true;
    }

    boolean isDirty() {
      return dirty;
    }

    boolean saveIfDirty() {
      if (!dirty) return true;
      return save();
    }

    String fileName() {
      return file == null ? "unknown" : file.getName();
    }

    void setProjectRoot(File projectRoot) {
      this.projectRoot = projectRoot;
      configureVisualEditors();
      updateFileLabel();
      updateAssetUtilityState();
    }

    private Node resolveDesignNode() {
      if (kind == Kind.MENU_SCREEN && menuScreenVisualEditor != null) return menuScreenVisualEditor;
      if (kind == Kind.MENU_LAYOUT && menuLayoutVisualEditor != null) return menuLayoutVisualEditor;
      if (kind == Kind.MENU_STYLE && menuStyleVisualEditor != null) return menuStyleVisualEditor;
      if (kind == Kind.DIALOGUE_LAYOUT && dialogueLayoutVisualEditor != null) return dialogueLayoutVisualEditor;
      return new Label("Unsupported studio file type");
    }

    private Node buildToolbar() {
      Label title = new Label(studioTitle());
      title.getStyleClass().add("layout-studio-title");

      dirtyBadge.getStyleClass().add("layout-studio-dirty");

      ToggleGroup modeGroup = new ToggleGroup();
      bDesign.setToggleGroup(modeGroup);
      bCode.setToggleGroup(modeGroup);
      bSplit.setToggleGroup(modeGroup);
      bCode.setSelected(true);

      bDesign.setOnAction(e -> applyMode(Mode.DESIGN));
      bCode.setOnAction(e -> applyMode(Mode.CODE));
      bSplit.setOnAction(e -> applyMode(Mode.SPLIT));
      previewToggle.setOnAction(e -> applyMode(previewToggle.isSelected() ? Mode.SPLIT : Mode.CODE));

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);

      configureIconToggle(bDesign, CssIcon.palette("#b0b8c8"), "Design Mode");
      configureIconToggle(bCode, CssIcon.list("#b0b8c8"), "Code Mode");
      configureIconToggle(bSplit, CssIcon.grid("#b0b8c8"), "Split Mode");
      previewToggle.setTooltip(new Tooltip("Enable/disable preview"));
      configureIconButton(saveButton, CssIcon.save("#8cd48c"), "Save");
      configureIconButton(runButton, CssIcon.play("#8cd48c"), "Save and Run Runtime (Ctrl/Cmd+Enter)");
      configureIconButton(reloadButton, CssIcon.redo("#7ec8e3"), "Reload");
      configureIconButton(revealButton, CssIcon.folder("#d4a8e8"), "Reveal File");
      configureIconButton(maximizeButton, CssIcon.expand("#b0b8c8"), "Maximize / Restore");
      maximizeButton.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));

      HBox row = new HBox(8,
          title,
          dirtyBadge,
          spacer,
          previewToggle,
          bDesign,
          bCode,
          bSplit,
          saveButton,
          runButton,
          reloadButton,
          revealButton,
          maximizeButton
      );
      row.getStyleClass().add("layout-studio-toolbar");
      row.setAlignment(Pos.CENTER_LEFT);
      row.setPadding(new Insets(8));

      bDesign.getStyleClass().add("layout-studio-toolbar-toggle");
      bCode.getStyleClass().add("layout-studio-toolbar-toggle");
      bSplit.getStyleClass().add("layout-studio-toolbar-toggle");
      previewToggle.getStyleClass().add("layout-studio-toolbar-toggle");
      saveButton.getStyleClass().add("layout-studio-toolbar-button");
      runButton.getStyleClass().add("layout-studio-toolbar-button");
      reloadButton.getStyleClass().add("layout-studio-toolbar-button");
      revealButton.getStyleClass().add("layout-studio-toolbar-button");

      if (!designPreviewEnabled) {
        previewToggle.setManaged(false);
        previewToggle.setVisible(false);
        bDesign.setManaged(false);
        bDesign.setVisible(false);
        bSplit.setManaged(false);
        bSplit.setVisible(false);
      }

      return row;
    }

    private Node buildContent() {
      if (!designPreviewEnabled) {
        centerHost.setCenter(codeEditor);
        centerHost.getStyleClass().add("layout-studio-center");
        return centerHost;
      }
      split.setOrientation(Orientation.HORIZONTAL);
      ensureSplitContent();
      split.setDividerPositions(0.62);
      centerHost.setCenter(split);
      centerHost.getStyleClass().add("layout-studio-center");
      return centerHost;
    }

    private BorderPane buildDesignHost() {
      BorderPane pane = new BorderPane(designNode);
      pane.getStyleClass().add("layout-studio-design-host");
      return pane;
    }

    private void ensureSplitContent() {
      double divider = 0.62;
      if (!split.getDividers().isEmpty()) {
        divider = split.getDividers().get(0).getPosition();
      }
      if (split.getItems().size() != 2
          || split.getItems().get(0) != designHost
          || split.getItems().get(1) != codeEditor) {
        split.getItems().setAll(designHost, codeEditor);
        split.setDividerPositions(Math.max(0.2, Math.min(0.8, divider)));
      }
    }

    private Node buildUtilitiesPane() {
      Label utilTitle = new Label("Asset Utilities");
      utilTitle.getStyleClass().add("layout-studio-section-title");

      assetPathField.setPromptText("assets/ui/button.png");
      AssetPickerSupport.installAssetDrop(assetPathField, this::toRelativePath);
      assetPathField.textProperty().addListener((o, ov, nv) -> updateAssetUtilityState());

      assetItemIdField.setPromptText("menu item id");
      assetItemIdField.textProperty().addListener((o, ov, nv) -> updateAssetUtilityState());

      configureAssetKeys();
      assetKeyBox.valueProperty().addListener((o, ov, nv) -> updateAssetUtilityState());

      VBox form = new VBox(6,
          new Label("Value (Asset Path or Literal)"),
          assetPathField,
          new Label("Apply Key"),
          assetKeyBox
      );
      form.getChildren().add(assetItemIdField);

      Label tip = new Label(assetTip());
      tip.setWrapText(true);
      tip.getStyleClass().add("muted");

      VBox buttons = new VBox(6,
          browseAssetButton,
          importAssetButton,
          revealAssetButton,
          clearAssetButton,
          copyPathButton,
          applyPathButton
      );
      configureIconButton(browseAssetButton, CssIcon.folder("#b0b8c8"), "Browse Asset");
      configureIconButton(importAssetButton, CssIcon.download("#8cd48c"), "Import Asset");
      configureIconButton(revealAssetButton, CssIcon.link("#9cc7ff"), "Reveal Asset");
      configureIconButton(clearAssetButton, CssIcon.clearX("#e07070"), "Clear");
      configureIconButton(copyPathButton, CssIcon.copy("#9cc7ff"), "Copy Path");
      configureIconButton(applyPathButton, CssIcon.check("#8cd48c"), "Apply to File");
      for (Node node : buttons.getChildren()) {
        if (node instanceof Button b) {
          b.setMaxWidth(Region.USE_PREF_SIZE);
          b.getStyleClass().add("layout-studio-utility-button");
        }
      }

      VBox panel = new VBox(10, utilTitle, form, buttons, new Separator(), tip);
      panel.getStyleClass().add("layout-studio-utils");
      panel.setPadding(new Insets(10));
      panel.setPrefWidth(320);
      panel.setMinWidth(280);
      updateAssetUtilityState();
      return panel;
    }

    private Node buildStatusBar() {
      fileLabel.getStyleClass().add("muted");
      status.getStyleClass().add("layout-studio-status");

      VBox box = new VBox(3, fileLabel, status);
      box.setPadding(new Insets(6, 10, 8, 10));
      box.getStyleClass().add("layout-studio-status-bar");
      updateFileLabel();
      return box;
    }

    private void configureVisualEditors() {
      if (!designPreviewEnabled) return;
      if (menuScreenVisualEditor != null) {
        menuScreenVisualEditor.setProjectRoot(projectRoot);
        menuScreenVisualEditor.setScreenIdHint(screenIdFromFile(file));
      }
      if (menuStyleVisualEditor != null) menuStyleVisualEditor.setProjectRoot(projectRoot);
      if (dialogueLayoutVisualEditor != null) dialogueLayoutVisualEditor.setProjectRoot(projectRoot);
    }

    private static final long SYNC_DEBOUNCE_MS = 300;
    private PauseTransition syncDebounce;

    private void bindSync() {
      syncDebounce = new PauseTransition(Duration.millis(SYNC_DEBOUNCE_MS));
      codeEditor.setOnTextChanged(text -> {
        if (syncing) return;
        updateDirtyState();
        if (designPreviewEnabled) {
          syncDebounce.setOnFinished(e -> {
            if (syncing) return;
            syncing = true;
            try {
              applyCodeToDesign(text);
            } catch (Exception ex) {
              setStatus("Design sync warning: " + normalize(ex.getMessage(), "Invalid content"));
            }
            syncing = false;
          });
          syncDebounce.playFromStart();
        }
      });

      if (!designPreviewEnabled) return;
      if (menuScreenVisualEditor != null) {
        menuScreenVisualEditor.setOnMenuTextChanged(text -> pushDesignTextToCode(text));
      }
      if (menuLayoutVisualEditor != null) {
        menuLayoutVisualEditor.setOnLayoutTextChanged(text -> pushDesignTextToCode(text));
      }
      if (menuStyleVisualEditor != null) {
        menuStyleVisualEditor.setOnStyleTextChanged(text -> pushDesignTextToCode(text));
      }
      if (dialogueLayoutVisualEditor != null) {
        dialogueLayoutVisualEditor.setOnLayoutTextChanged(text -> pushDesignTextToCode(text));
      }
    }

    private void pushDesignTextToCode(String text) {
      if (syncing) return;
      String current = normalizeLineEndings(codeEditor.getText());
      String incoming = normalizeLineEndings(text);
      if (Objects.equals(current, incoming)) return;
      syncing = true;
      codeEditor.setTextNoEvent(text);
      syncing = false;
      updateDirtyState();
    }

    private void applyCodeToDesign(String text) {
      if (menuScreenVisualEditor != null) {
        menuScreenVisualEditor.setMenuText(text);
      } else if (menuLayoutVisualEditor != null) {
        menuLayoutVisualEditor.setLayoutText(text);
      } else if (menuStyleVisualEditor != null) {
        menuStyleVisualEditor.setStyleText(text);
      } else if (dialogueLayoutVisualEditor != null) {
        dialogueLayoutVisualEditor.setLayoutText(text);
      }
      refreshCodeDiagnostics(text);
    }

    private static final Set<String> LAYOUT_KEYS = Set.of(
        "listYStart", "lineHeight", "listWidthFactor", "textAlign", "hintsBottomMargin", "titleY",
        "listXCenter", "titleX", "maxVisibleItems", "titleAlign", "hintsAlign", "hintsX");
    private static final Set<String> STYLE_KEYS = Set.of(
        "itemColor", "itemSelectedColor", "itemHoverColor", "itemDisabledColor",
        "itemPrefix", "itemSelectedPrefix", "itemDisabledPrefix",
        "itemFontFamily", "itemFontWeight", "itemFontSize",
        "itemShadowColor", "itemShadowOffsetX", "itemShadowOffsetY", "itemOpacity",
        "buttonAsset", "buttonSelectedAsset", "buttonHoverAsset", "buttonDisabledAsset",
        "buttonTextPaddingX", "buttonTextPaddingY",
        "titleColor", "titleFontFamily", "titleFontWeight", "titleFontSize", "titleShadowColor",
        "hintsColor", "hintsFontFamily", "hintsFontWeight", "hintsFontSize",
        "backgroundAsset", "backgroundColor", "backgroundOpacity");
    private static final Set<String> SCREEN_TOP_KEYS = Set.of(
        "titleText", "hintsText", "layout", "layoutId", "defaultItemStyle", "wrapSelection", "items");
    private static final Set<String> SCREEN_ITEM_KEYS = Set.of(
        "label", "style", "icon", "enabled", "action", "target",
        "bgAsset", "bgSelectedAsset", "bgDisabledAsset",
        "boundsX", "boundsY", "boundsWidth", "boundsHeight",
        "slotPreviewEnabled", "slotPreviewPlaceholderAsset", "slotPreviewFrameAsset",
        "slotPreviewX", "slotPreviewY", "slotPreviewWidth", "slotPreviewHeight");

    private void refreshCodeDiagnostics(String text) {
      List<String> rawIssues;
      switch (kind) {
        case MENU_SCREEN -> rawIssues = DslPropertyDiagnostics.menuScreenIssues(text, SCREEN_TOP_KEYS, SCREEN_ITEM_KEYS);
        case MENU_LAYOUT -> rawIssues = DslPropertyDiagnostics.menuLayoutIssues(text, LAYOUT_KEYS);
        case MENU_STYLE -> rawIssues = DslPropertyDiagnostics.menuStyleIssues(text, STYLE_KEYS);
        case DIALOGUE_LAYOUT -> rawIssues = DslPropertyDiagnostics.dialogueIssues(text, List.of());
        default -> rawIssues = List.of();
      };
      codeEditor.setDiagnostics(parseDiagnosticStrings(rawIssues));
    }

    private static List<JavaCodeEditor.Diagnostic> parseDiagnosticStrings(List<String> raw) {
      if (raw == null || raw.isEmpty()) return List.of();
      List<JavaCodeEditor.Diagnostic> out = new ArrayList<>();
      Pattern linePattern = Pattern.compile("^L(\\d+)\\s+(\\S+?):\\s+(.+?)(?:\\s+Quick fix:\\s+(.+))?$");
      for (String s : raw) {
        if (s == null || s.isBlank()) continue;
        Matcher m = linePattern.matcher(s.trim());
        if (m.matches()) {
          int line = Integer.parseInt(m.group(1));
          String key = m.group(2);
          String message = m.group(3);
          String quickFix = m.group(4);
          JavaCodeEditor.Diagnostic.Severity severity =
              message.toLowerCase(Locale.ROOT).contains("unknown") || message.toLowerCase(Locale.ROOT).contains("invalid")
                  ? JavaCodeEditor.Diagnostic.Severity.ERROR
                  : JavaCodeEditor.Diagnostic.Severity.WARNING;
          out.add(new JavaCodeEditor.Diagnostic(line, key, message, quickFix, severity));
        } else {
          out.add(new JavaCodeEditor.Diagnostic(1, "dsl", s, null));
        }
      }
      return out;
    }

    private void bindButtons() {
      saveButton.setOnAction(e -> save());
      runButton.setOnAction(e -> saveAndRunProject());
      reloadButton.setOnAction(e -> reload());
      revealButton.setOnAction(e -> revealInFinder());

      browseAssetButton.setOnAction(e -> browseExistingAsset());
      importAssetButton.setOnAction(e -> importExternalAsset());
      revealAssetButton.setOnAction(e -> revealSelectedAsset());
      clearAssetButton.setOnAction(e -> {
        assetPathField.clear();
        setStatus("Asset value cleared.");
      });
      copyPathButton.setOnAction(e -> copyAssetPath());
      applyPathButton.setOnAction(e -> applyAssetPathToCode());
    }

    private void loadFromDisk() {
      try {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        syncing = true;
        codeEditor.setTextNoEvent(text);
        if (designPreviewEnabled) {
          try {
            applyCodeToDesign(text);
          } catch (Exception ex) {
            setStatus("Loaded with design warnings: " + normalize(ex.getMessage(), "invalid content"));
          }
        }
        syncing = false;

        savedSnapshot = normalizeLineEndings(text);
        dirty = false;
        updateWindowTitle();
        setStatus("Loaded: " + file.getName());
      } catch (Exception ex) {
        syncing = false;
        setStatus("Load failed: " + ex.getMessage());
      }
    }

    private boolean save() {
      try {
        Path target = file.toPath();
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);

        String content = codeEditor.getText();
        if (content == null) content = "";
        writeAtomically(target, content);

        savedSnapshot = normalizeLineEndings(content);
        dirty = false;
        updateWindowTitle();
        setStatus("Saved: " + file.getName());
        return true;
      } catch (Exception ex) {
        setStatus("Save failed: " + ex.getMessage());
        return false;
      }
    }

    private void saveAndRunProject() {
      if (!save()) {
        setStatus("Run cancelled: save failed.");
        return;
      }
      if (runProjectHandler == null) {
        setStatus("Run is unavailable for this Studio window.");
        return;
      }
      File root = resolveProjectRootForRun();
      if (root == null) {
        setStatus("Run cancelled: project root not found.");
        return;
      }
      setStatus("Running project...");
      try {
        runProjectHandler.accept(root);
      } catch (Exception ex) {
        setStatus("Run failed: " + normalize(ex.getMessage(), "unknown error"));
      }
    }

    private void reload() {
      if (dirty && !confirmDiscard("Reload from disk")) return;
      loadFromDisk();
    }

    private void revealInFinder() {
      try {
        File parent = file.getParentFile();
        if (parent == null || !parent.exists()) return;
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(parent);
        setStatus("Opened: " + toRelativePath(parent));
      } catch (Exception ex) {
        setStatus("Reveal failed: " + ex.getMessage());
      }
    }

    private File resolveProjectRootForRun() {
      if (projectRoot != null && projectRoot.isDirectory()) return projectRoot;
      File cursor = file == null ? null : file.getParentFile();
      while (cursor != null) {
        File marker = new File(cursor, "jvn.project");
        if (marker.isFile()) return cursor;
        cursor = cursor.getParentFile();
      }
      return null;
    }

    private void browseExistingAsset() {
      FileChooser chooser = new FileChooser();
      chooser.setTitle("Select Project Asset");
      AssetPickerSupport.addAssetFilters(chooser);
      File init = preferredAssetDirectory();
      if (init != null && init.exists() && init.isDirectory()) chooser.setInitialDirectory(init);
      File selected = chooser.showOpenDialog(stage);
      if (selected == null) return;
      assetPathField.setText(toRelativePath(selected));
      setStatus("Asset selected: " + toRelativePath(selected));
    }

    private void importExternalAsset() {
      FileChooser chooser = new FileChooser();
      chooser.setTitle("Import External Asset");
      AssetPickerSupport.addAssetFilters(chooser);
      File selected = chooser.showOpenDialog(stage);
      if (selected == null) return;

      try {
        File destinationDir = preferredAssetDirectory();
        if (destinationDir == null) {
          assetPathField.setText(selected.getAbsolutePath().replace('\\', '/'));
          setStatus("Asset selected outside project.");
          return;
        }

        if (!destinationDir.exists()) destinationDir.mkdirs();

        File destination = uniqueDestination(destinationDir, selected.getName());
        Path source = selected.toPath().toAbsolutePath().normalize();
        Path destinationPath = destination.toPath().toAbsolutePath().normalize();
        if (source.equals(destinationPath)) {
          String relExisting = toRelativePath(destination);
          assetPathField.setText(relExisting);
          setStatus("Asset already in project: " + relExisting);
          return;
        }

        Files.copy(source, destinationPath, StandardCopyOption.REPLACE_EXISTING);

        String rel = toRelativePath(destination);
        assetPathField.setText(rel);
        setStatus("Imported asset: " + rel);
      } catch (Exception ex) {
        setStatus("Import failed: " + ex.getMessage());
      }
    }

    private void revealSelectedAsset() {
      String path = normalize(assetPathField.getText(), "");
      if (path.isBlank()) {
        setStatus("Value is empty.");
        return;
      }
      File file = new File(path);
      if (!file.isAbsolute() && projectRoot != null) {
        file = new File(projectRoot, path);
      }
      if (AssetPickerSupport.revealFile(file)) {
        setStatus("Revealed: " + path);
      } else {
        setStatus("Could not reveal: " + path);
      }
    }

    private void copyAssetPath() {
      String path = normalize(assetPathField.getText(), "");
      if (path.isBlank()) {
        setStatus("Value is empty.");
        return;
      }
      ClipboardContent content = new ClipboardContent();
      content.putString(path);
      Clipboard.getSystemClipboard().setContent(content);
      setStatus("Copied: " + path);
    }

    private void applyAssetPathToCode() {
      String path = normalize(assetPathField.getText(), "");
      if (path.isBlank()) {
        setStatus("Value is empty.");
        return;
      }

      String keyTemplate = normalize(assetKeyBox.getValue(), "");
      if (keyTemplate.isBlank()) {
        setStatus("No key selected.");
        return;
      }

      String effectiveKey = keyTemplate;
      if (effectiveKey.contains("<itemId>")) {
        String itemId = sanitizeId(assetItemIdField.getText());
        if (itemId.isBlank()) {
          setStatus("Enter a menu item id for this key.");
          return;
        }
        effectiveKey = effectiveKey.replace("<itemId>", itemId);
      }

      String before = codeEditor.getText();
      String after = upsertProperty(before, effectiveKey, path);
      if (!Objects.equals(before, after)) {
        codeEditor.setText(after);
        setStatus("Applied asset to: " + effectiveKey);
      } else {
        setStatus("No change for key: " + effectiveKey);
      }
    }

    private void configureAssetKeys() {
      List<String> keys = new ArrayList<>();
      if (kind == Kind.DIALOGUE_LAYOUT) {
        keys.add("textBoxAsset");
        keys.add("choiceButtonAsset");
        keys.add("choiceButtonHoverAsset");
        keys.add("choiceButtonSelectedAsset");
        keys.add("choiceButtonDisabledAsset");
        keys.add("textBoxButton.<itemId>.asset");
        keys.add("textBoxButton.<itemId>.hoverAsset");
        keys.add("textBoxButton.<itemId>.disabledAsset");
        keys.add("choiceBackgroundColor");
        keys.add("choiceHoverColor");
        keys.add("choiceDisabledColor");
        keys.add("choiceTextColor");
        keys.add("choiceHoverTextColor");
        keys.add("choiceDisabledTextColor");
        keys.add("choiceBorderColor");
        keys.add("choiceHoverBorderColor");
        keys.add("choiceDisabledBorderColor");
        keys.add("choiceCornerRadius");
        keys.add("choiceBorderWidth");
        keys.add("choiceTextBaselineOffset");
      } else if (kind == Kind.MENU_STYLE) {
        keys.add("buttonAsset");
        keys.add("buttonSelectedAsset");
        keys.add("buttonDisabledAsset");
      } else if (kind == Kind.MENU_SCREEN) {
        keys.add("item.<itemId>.bgAsset");
        keys.add("item.<itemId>.bgSelectedAsset");
        keys.add("item.<itemId>.bgDisabledAsset");
        keys.add("item.<itemId>.icon");
        keys.add("item.<itemId>.slotPreviewEnabled");
        keys.add("item.<itemId>.slotPreviewPlaceholderAsset");
        keys.add("item.<itemId>.slotPreviewFrameAsset");
        keys.add("item.<itemId>.slotPreviewX");
        keys.add("item.<itemId>.slotPreviewY");
        keys.add("item.<itemId>.slotPreviewWidth");
        keys.add("item.<itemId>.slotPreviewHeight");
      } else {
        keys.add("# no direct asset key");
      }

      assetKeyBox.getItems().setAll(keys);
      if (!keys.isEmpty()) assetKeyBox.getSelectionModel().select(0);
      boolean usesItemId = kind == Kind.MENU_SCREEN || kind == Kind.DIALOGUE_LAYOUT;
      assetItemIdField.setManaged(usesItemId);
      assetItemIdField.setVisible(usesItemId);
    }

    private void updateAssetUtilityState() {
      String key = normalize(assetKeyBox.getValue(), "");
      String path = normalize(assetPathField.getText(), "");
      String itemId = sanitizeId(assetItemIdField.getText());
      boolean requiresItemId = key.contains("<itemId>");
      boolean hasItemId = !requiresItemId || !itemId.isBlank();
      boolean canApply = !path.isBlank() && !key.isBlank() && !key.startsWith("#") && hasItemId;
      applyPathButton.setDisable(!canApply);
      copyPathButton.setDisable(path.isBlank());
      revealAssetButton.setDisable(path.isBlank());
      clearAssetButton.setDisable(path.isBlank());
    }

    private String assetTip() {
      return switch (kind) {
        case DIALOGUE_LAYOUT -> "Import textbox/choice button skins and map them to dialogue layout keys.\n"
            + "Use textBoxButton.<itemId>.* to map per-textbox action button assets (enter id in the field below).";
        case MENU_STYLE -> designPreviewEnabled
            ? "Import button textures and map them to style keys.\nUse Split mode to verify visual + file output together."
            : "Import button textures and map them to style keys.\nUse Run Project to validate changes in the runtime quickly.";
        case MENU_SCREEN -> "Assign per-item button/icon assets using item.<itemId> keys.\n"
            + "For save/load screens, use slotPreview* keys to configure inline save thumbnails and frame skins.";
        case MENU_LAYOUT -> "This file is geometry-focused. Asset tools are still available for copying paths into custom properties.";
      };
    }

    private File preferredAssetDirectory() {
      if (projectRoot == null || !projectRoot.isDirectory()) return null;
      // Try kind-specific directories first, fall back to project root
      File candidate = switch (kind) {
        case DIALOGUE_LAYOUT -> new File(projectRoot, "assets/ui");
        case MENU_STYLE -> new File(projectRoot, "assets/ui");
        case MENU_SCREEN -> new File(projectRoot, "assets/ui");
        case MENU_LAYOUT -> new File(projectRoot, "assets");
      };
      return (candidate.exists() && candidate.isDirectory()) ? candidate : projectRoot;
    }

    private File uniqueDestination(File dir, String name) {
      String safeName = normalize(name, "asset.bin");
      File out = new File(dir, safeName);
      if (!out.exists()) return out;

      String base = safeName;
      String ext = "";
      int dot = safeName.lastIndexOf('.');
      if (dot > 0) {
        base = safeName.substring(0, dot);
        ext = safeName.substring(dot);
      }
      int i = 2;
      while (true) {
        File candidate = new File(dir, base + "_" + i + ext);
        if (!candidate.exists()) return candidate;
        i++;
      }
    }

    private String toRelativePath(File pathFile) {
      if (pathFile == null) return "";
      if (projectRoot == null) return pathFile.getAbsolutePath().replace('\\', '/');
      try {
        Path root = projectRoot.toPath().toAbsolutePath().normalize();
        Path abs = pathFile.toPath().toAbsolutePath().normalize();
        if (abs.startsWith(root)) {
          return root.relativize(abs).toString().replace('\\', '/');
        }
      } catch (Exception ignored) {
      }
      return pathFile.getAbsolutePath().replace('\\', '/');
    }

    private void updateDirtyState() {
      String current = normalizeLineEndings(codeEditor.getText());
      dirty = !Objects.equals(savedSnapshot, current);
      updateWindowTitle();
    }

    private void updateWindowTitle() {
      String title = "JVN " + studioTitle() + " - " + file.getName();
      if (dirty) title += " *";
      stage.setTitle(title);
      dirtyBadge.setText(dirty ? "Unsaved" : "Saved");
      dirtyBadge.getStyleClass().removeAll("layout-studio-dirty-saved", "layout-studio-dirty-unsaved");
      dirtyBadge.getStyleClass().add(dirty ? "layout-studio-dirty-unsaved" : "layout-studio-dirty-saved");
      updateFileLabel();
    }

    private void updateFileLabel() {
      fileLabel.setText("File: " + toRelativePath(file));
    }

    private String studioTitle() {
      return switch (kind) {
        case MENU_SCREEN -> "Menu Screen Studio";
        case MENU_LAYOUT -> "Menu Layout Studio";
        case MENU_STYLE -> "Menu Style Studio";
        case DIALOGUE_LAYOUT -> "Dialogue Layout Studio";
      };
    }

    private void setStatus(String message) {
      String msg = normalize(message, "Ready.");
      status.setText(msg);
      if (externalStatus != null) externalStatus.accept(msg);
    }

    private static void configureIconButton(Button button, Node icon, String tooltipText) {
      button.setText("");
      button.setGraphic(icon);
      button.setTooltip(new Tooltip(tooltipText));
      button.setMinWidth(30);
      button.setPrefWidth(30);
    }

    private static void configureIconToggle(ToggleButton button, Node icon, String tooltipText) {
      button.setText("");
      button.setGraphic(icon);
      button.setTooltip(new Tooltip(tooltipText));
      button.setMinWidth(30);
      button.setPrefWidth(30);
    }

    private boolean confirmDiscard(String operation) {
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      EditorTheme.apply(alert);
      alert.setHeaderText(operation + "? Unsaved changes will be lost.");
      alert.setTitle("Unsaved Changes");
      alert.setContentText("Continue without saving?");
      ButtonType continueBtn = new ButtonType("Continue", ButtonBar.ButtonData.OK_DONE);
      alert.getButtonTypes().setAll(continueBtn, ButtonType.CANCEL);
      Optional<ButtonType> result = alert.showAndWait();
      return result.isPresent() && result.get() == continueBtn;
    }

    private boolean confirmCloseIfDirty() {
      if (!dirty) return true;

      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      EditorTheme.apply(alert);
      alert.setTitle("Unsaved Changes");
      alert.setHeaderText("Save changes to " + file.getName() + " before closing?");
      alert.setContentText("Choose Save to keep changes, Discard to close without saving.");

      ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
      ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
      alert.getButtonTypes().setAll(save, discard, ButtonType.CANCEL);
      Optional<ButtonType> result = alert.showAndWait();
      if (result.isEmpty() || result.get() == ButtonType.CANCEL) return false;
      if (result.get() == discard) return true;
      return save();
    }

    private enum Mode { DESIGN, CODE, SPLIT }

    private void applyMode(Mode mode) {
      if (!designPreviewEnabled) mode = Mode.CODE;
      if (mode == null) mode = Mode.SPLIT;
      if (mode == Mode.DESIGN) {
        centerHost.setCenter(designHost);
      } else if (mode == Mode.CODE) {
        centerHost.setCenter(codeEditor);
      } else {
        ensureSplitContent();
        centerHost.setCenter(split);
      }

      previewToggle.setSelected(designPreviewEnabled && mode != Mode.CODE);
      bDesign.setSelected(designPreviewEnabled && mode == Mode.DESIGN);
      bCode.setSelected(mode == Mode.CODE);
      bSplit.setSelected(designPreviewEnabled && mode == Mode.SPLIT);
    }

    private static String upsertProperty(String originalText, String key, String value) {
      String text = originalText == null ? "" : originalText;
      String k = normalize(key, "");
      if (k.isBlank()) return text;
      String v = value == null ? "" : value;

      String line = k + "=" + v;
      Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(k) + "\\s*=.*$");
      Matcher matcher = pattern.matcher(text);
      if (matcher.find()) {
        return matcher.replaceFirst(Matcher.quoteReplacement(line));
      }

      StringBuilder sb = new StringBuilder(text);
      if (!text.isEmpty() && !text.endsWith("\n")) sb.append('\n');
      sb.append(line).append('\n');
      return sb.toString();
    }

    private static void writeAtomically(Path target, String content) throws Exception {
      Path parent = target.getParent();
      if (parent == null) {
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return;
      }

      Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
      try {
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
          Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
          Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        try {
          Files.deleteIfExists(temp);
        } catch (Exception ignored) {
        }
      }
    }

    private static String screenIdFromFile(File file) {
      if (file == null) return "main";
      String name = file.getName();
      int dot = name.lastIndexOf('.');
      return dot > 0 ? name.substring(0, dot) : name;
    }
  }
}
