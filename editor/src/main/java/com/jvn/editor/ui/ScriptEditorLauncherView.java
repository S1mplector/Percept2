package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jvn.editor.ui.ScriptEditorWorkspaceModel.ScriptFileEntry;
import com.jvn.editor.ui.ScriptEditorWorkspaceModel.WorkspaceSnapshot;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Sidebar script explorer + launcher for the dedicated VNS editor window.
 * Designed to behave more like a small IDE explorer than a static launcher card.
 */
public class ScriptEditorLauncherView extends BorderPane {
  private static final DateTimeFormatter MODIFIED_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.0");

  private final Label projectLabel = new Label("No project loaded");
  private final Label scriptsRootLabel = new Label("No scripts directory detected");
  private final Label scriptsStat = statValue("0");
  private final Label foldersStat = statValue("0");
  private final Label labelsStat = statValue("0");

  private final Button openInEditorButton = new Button("Open in Editor");
  private final Button openWindowButton = new Button("Pop Out IDE");
  private final Button newScriptButton = new Button("New Script");
  private final Button refreshButton = new Button("Refresh");
  private final Button revealButton = new Button("Reveal");

  private final TextField filterField = new TextField();
  private final TextField searchField = new TextField();
  private final VBox searchResults = new VBox(4);
  private final TreeView<ExplorerNode> explorerTree = new TreeView<>();
  private final Label explorerHint = new Label("Double-click or press Enter to open the selected script in the main editor.");

  private final Label selectionTitle = new Label("Workspace Overview");
  private final Label selectionPath = new Label("Select a script to inspect it.");
  private final Label selectionMeta = new Label("");
  private final VBox outlineList = new VBox(4);
  private final VBox includesList = new VBox(4);
  private final VBox includedByList = new VBox(4);

  private File projectRoot;
  private File workspaceRoot;
  private Consumer<String> onStatus;
  private Consumer<File> onOpenFile;
  private java.util.function.BiConsumer<File, Integer> onOpenFileAtLine;
  private double codeEditorFontSize = 13.0;

  private Stage editorWindow;
  private TabPane editorWindowTabs;
  private Label editorWindowStatus;
  private File editorWindowLaunchRoot;

  private WorkspaceSnapshot snapshot = ScriptEditorWorkspaceModel.emptySnapshot();
  private ExplorerNode selectedNode;

  public ScriptEditorLauncherView() {
    buildUi();
    refreshWorkspace();
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    refreshWorkspace();
  }

  public void setWorkspaceRoot(File root) {
    this.workspaceRoot = root;
    refreshWorkspace();
  }

  public void setOnStatus(Consumer<String> onStatus) {
    this.onStatus = onStatus;
  }

  public void setOnOpenFile(Consumer<File> onOpenFile) {
    this.onOpenFile = onOpenFile;
    updateActionState();
  }

  public void setOnOpenFileAtLine(java.util.function.BiConsumer<File, Integer> onOpenFileAtLine) {
    this.onOpenFileAtLine = onOpenFileAtLine;
  }

  public void setCodeEditorFontSize(double fontSizePx) {
    codeEditorFontSize = Math.max(8.0, Math.min(30.0, fontSizePx));
    if (editorWindowTabs == null) return;
    for (Tab tab : editorWindowTabs.getTabs()) {
      Object editor = tab.getProperties().get("editor");
      if (editor instanceof VnsCodeEditor vnsEditor) {
        vnsEditor.setFontSizePx(codeEditorFontSize);
      }
    }
  }

  public File getProjectRoot() {
    return projectRoot;
  }

  public void launchEditorWindow() {
    launchEditorWindow(selectedNode != null ? selectedNode.file() : null);
  }

  private void buildUi() {
    getStyleClass().add("script-editor-launcher-root");

    VBox header = new VBox(10);
    header.getStyleClass().add("script-editor-launcher-header");
    header.setPadding(new Insets(12, 12, 10, 12));

    Label titleLabel = new Label("Script Editor");
    titleLabel.getStyleClass().add("script-editor-launcher-title");
    HBox titleRow = new HBox(8, CssIcon.list("#cfd6e6"), titleLabel);
    titleRow.setAlignment(Pos.CENTER_LEFT);

    Label desc = new Label(
        "A focused VNS explorer for your project. Filter scripts, inspect labels, open files in the main editor, or pop out the dedicated tabbed code editor.");
    desc.setWrapText(true);
    desc.getStyleClass().add("script-editor-launcher-description");

    VBox projectCard = new VBox(4,
        labelledMeta("Project", projectLabel),
        labelledMeta("Scripts Root", scriptsRootLabel));
    projectCard.setPadding(new Insets(10));
    projectCard.getStyleClass().add("script-editor-card");

    HBox statsRow = new HBox(8,
        statCard("Scripts", scriptsStat),
        statCard("Folders", foldersStat),
        statCard("Labels", labelsStat));

    HBox primaryActions = new HBox(8,
        styleActionButton(openInEditorButton, CssIcon.list("#dbe6f4"), "Open the selected script in the main editor", true),
        styleActionButton(openWindowButton, CssIcon.popOut("#f5c46b"), "Open the standalone tabbed script IDE", false));
    HBox secondaryActions = new HBox(8,
        styleActionButton(newScriptButton, CssIcon.plus("#8bcf98"), "Create a new VNS script", false),
        styleActionButton(refreshButton, CssIcon.redo("#c7d0df"), "Refresh the script workspace", false),
        styleActionButton(revealButton, CssIcon.folder("#d5b36a"), "Reveal the current scripts folder in Finder", false));
    HBox.setHgrow(openInEditorButton, Priority.ALWAYS);
    HBox.setHgrow(openWindowButton, Priority.ALWAYS);
    HBox.setHgrow(newScriptButton, Priority.ALWAYS);
    HBox.setHgrow(refreshButton, Priority.ALWAYS);
    HBox.setHgrow(revealButton, Priority.ALWAYS);

    filterField.setPromptText("Filter scripts, paths, or labels...");
    filterField.getStyleClass().add("script-editor-field");

    searchField.setPromptText("Search in all scripts…");
    searchField.getStyleClass().add("script-editor-field");
    searchResults.setPadding(new Insets(4, 0, 0, 0));

    header.getChildren().addAll(titleRow, desc, projectCard, statsRow, primaryActions, secondaryActions,
        sectionLabel("Explorer Filter"), filterField,
        sectionLabel("Search in Scripts"), searchField);

    explorerTree.setShowRoot(true);
    explorerTree.getStyleClass().add("script-editor-tree");
    explorerTree.setCellFactory(tree -> new TreeCell<>() {
      @Override
      protected void updateItem(ExplorerNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          return;
        }
        Region icon = item.directory
            ? CssIcon.folder(item.relativePath == null ? "#f0c66c" : "#d4b169")
            : CssIcon.list("#bcbcbc");
        Label name = new Label(item.displayName);
        name.getStyleClass().add(item.directory ? "script-editor-tree-dir-label" : "script-editor-tree-file-label");
        HBox row = new HBox(6, icon, name);
        row.setAlignment(Pos.CENTER_LEFT);
        if (item.directory && item.scriptCount > 0) {
          Region spacer = new Region();
          HBox.setHgrow(spacer, Priority.ALWAYS);
          Label badge = new Label(Integer.toString(item.scriptCount));
          badge.getStyleClass().add("script-editor-tree-badge");
          row.getChildren().addAll(spacer, badge);
        }
        setText(null);
        setGraphic(row);
      }
    });

    explorerHint.setWrapText(true);
    explorerHint.getStyleClass().add("script-editor-hint");

    VBox searchResultsCard = new VBox(6, searchResults);
    searchResultsCard.setPadding(new Insets(6));
    searchResultsCard.getStyleClass().add("script-editor-card");
    searchResultsCard.setVisible(false);
    searchResultsCard.setManaged(false);

    VBox explorerBox = new VBox(8, sectionLabel("Project Explorer"), explorerTree, explorerHint, searchResultsCard);
    explorerBox.setPadding(new Insets(10));
    explorerBox.getStyleClass().add("script-editor-launcher-pane");
    VBox.setVgrow(explorerTree, Priority.ALWAYS);

    selectionTitle.getStyleClass().add("script-editor-selection-title");
    selectionPath.setWrapText(true);
    selectionPath.getStyleClass().add("script-editor-selection-path");
    selectionMeta.setWrapText(true);
    selectionMeta.getStyleClass().add("script-editor-selection-meta");

    VBox outlineCard = new VBox(6, sectionLabel("Label Outline"), outlineList);
    outlineCard.setPadding(new Insets(10));
    outlineCard.getStyleClass().add("script-editor-card");
    VBox includesCard = new VBox(6, sectionLabel("Includes"), includesList);
    includesCard.setPadding(new Insets(10));
    includesCard.getStyleClass().add("script-editor-card");
    VBox includedByCard = new VBox(6, sectionLabel("Included By"), includedByList);
    includedByCard.setPadding(new Insets(10));
    includedByCard.getStyleClass().add("script-editor-card");
    VBox inspectorBox = new VBox(10, sectionLabel("Selection"), selectionTitle, selectionPath, selectionMeta,
        outlineCard, includesCard, includedByCard);
    inspectorBox.setPadding(new Insets(10));
    inspectorBox.getStyleClass().add("script-editor-launcher-pane");

    SplitPane centerSplit = new SplitPane(explorerBox, inspectorBox);
    centerSplit.setOrientation(Orientation.VERTICAL);
    centerSplit.setDividerPositions(0.62);

    setTop(header);
    setCenter(centerSplit);

    wireListeners();
  }

  private void wireListeners() {
    filterField.textProperty().addListener((obs, oldVal, newVal) -> rebuildExplorerTree());
    refreshButton.setOnAction(e -> refreshWorkspace());
    openInEditorButton.setOnAction(e -> openSelectedInEditor());
    openWindowButton.setOnAction(e -> launchEditorWindow(selectedNode != null ? selectedNode.file() : null));
    revealButton.setOnAction(e -> revealSelection());
    newScriptButton.setOnAction(e -> createNewScript());

    explorerTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      selectedNode = newVal == null ? null : newVal.getValue();
      updateSelectionInspector();
      updateActionState();
    });
    explorerTree.setOnMouseClicked(ev -> {
      if (ev.getClickCount() >= 2 && selectedNode != null && selectedNode.file() != null) {
        openSelectedInEditor();
      }
    });
    explorerTree.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
      if (ev.getCode() == KeyCode.ENTER && selectedNode != null && selectedNode.file() != null) {
        openSelectedInEditor();
        ev.consume();
      } else if (ev.getCode() == KeyCode.F5) {
        refreshWorkspace();
        ev.consume();
      } else if (ev.getCode() == KeyCode.DELETE || ev.getCode() == KeyCode.BACK_SPACE) {
        if (selectedNode != null && selectedNode.file() != null) {
          deleteSelectedScript();
          ev.consume();
        }
      } else if (ev.getCode() == KeyCode.F2) {
        if (selectedNode != null && selectedNode.file() != null) {
          renameSelectedScript();
          ev.consume();
        }
      }
    });

    installContextMenu();

    // Search in scripts — debounced via PauseTransition
    javafx.animation.PauseTransition searchDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
    searchField.textProperty().addListener((obs, oldVal, newVal) -> {
      searchDebounce.setOnFinished(ev -> runContentSearch(newVal));
      searchDebounce.playFromStart();
    });
  }

  private void installContextMenu() {
    explorerTree.setContextMenu(null);
    explorerTree.setOnContextMenuRequested(ev -> {
      TreeItem<ExplorerNode> item = explorerTree.getSelectionModel().getSelectedItem();
      if (item == null || item.getValue() == null) return;
      ExplorerNode node = item.getValue();

      ContextMenu ctx = new ContextMenu();

      if (node.file() != null) {
        MenuItem open = menuItem("Open in Editor", CssIcon.list("#b8d4f0"), this::openSelectedInEditor);
        MenuItem openWindow = menuItem("Open in Script IDE", CssIcon.popOut("#f5c46b"), () -> launchEditorWindow(node.file()));
        ctx.getItems().addAll(open, openWindow, new SeparatorMenuItem());

        MenuItem rename = menuItem("Rename…", CssIcon.freehand("#d9b36a"), this::renameSelectedScript);
        MenuItem duplicate = menuItem("Duplicate", CssIcon.copy("#c7d0df"), this::duplicateSelectedScript);
        MenuItem delete = menuItem("Delete…", CssIcon.clearX("#f06a6a"), this::deleteSelectedScript);
        ctx.getItems().addAll(rename, duplicate, delete, new SeparatorMenuItem());

        MenuItem copyPath = menuItem("Copy Absolute Path", CssIcon.copy("#c7d0df"),
            () -> copyToClipboard(node.file().getAbsolutePath()));
        MenuItem copyRelPath = menuItem("Copy Relative Path", CssIcon.link("#c7d0df"), () -> {
          if (node.entry != null) copyToClipboard(node.entry.projectRelativePath());
        });
        MenuItem reveal = menuItem("Reveal in File Manager", CssIcon.folder("#d5b36a"), () -> revealFile(node.file()));
        ctx.getItems().addAll(copyPath, copyRelPath, reveal);
      } else if (node.directory) {
        MenuItem newScript = menuItem("New Script Here…", CssIcon.plus("#8bcf98"), () -> createNewScriptInFolder(node));
        MenuItem reveal = menuItem("Reveal in File Manager", CssIcon.folder("#d5b36a"), () -> {
          File dir = resolveNodeDirectory(node);
          if (dir != null && dir.exists()) revealFile(dir);
        });
        ctx.getItems().addAll(newScript, reveal);
      }

      if (!ctx.getItems().isEmpty()) {
        ctx.show(explorerTree, ev.getScreenX(), ev.getScreenY());
      }
    });
  }

  private void renameSelectedScript() {
    if (selectedNode == null || selectedNode.file() == null) return;
    File file = selectedNode.file();
    String currentName = file.getName();
    String stem = currentName.toLowerCase().endsWith(".vns")
        ? currentName.substring(0, currentName.length() - 4) : currentName;

    TextInputDialog dialog = new TextInputDialog(stem);
    EditorTheme.apply(dialog);
    dialog.setTitle("Rename Script");
    dialog.setHeaderText("Rename " + currentName);
    dialog.setContentText("New name (without .vns):");

    dialog.showAndWait().ifPresent(newName -> {
      try {
        File renamed = ScriptEditorWorkspaceModel.renameScript(file, newName);
        refreshWorkspace();
        restoreSelection(renamed.getAbsolutePath());
        setStatus("Renamed to " + renamed.getName());
      } catch (IOException ex) {
        showLaunchError("Failed to rename script:\n" + ex.getMessage());
      }
    });
  }

  private void duplicateSelectedScript() {
    if (selectedNode == null || selectedNode.file() == null) return;
    try {
      File copy = ScriptEditorWorkspaceModel.duplicateScript(selectedNode.file());
      refreshWorkspace();
      restoreSelection(copy.getAbsolutePath());
      setStatus("Duplicated as " + copy.getName());
    } catch (IOException ex) {
      showLaunchError("Failed to duplicate script:\n" + ex.getMessage());
    }
  }

  private void deleteSelectedScript() {
    if (selectedNode == null || selectedNode.file() == null) return;
    File file = selectedNode.file();

    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    EditorTheme.apply(confirm);
    confirm.setTitle("Delete Script");
    confirm.setHeaderText("Delete " + file.getName() + "?");
    confirm.setContentText("This action cannot be undone.\n" + file.getAbsolutePath());
    confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

    confirm.showAndWait().ifPresent(response -> {
      if (response != ButtonType.OK) return;
      try {
        ScriptEditorWorkspaceModel.deleteScript(file);
        refreshWorkspace();
        setStatus("Deleted " + file.getName());
      } catch (IOException ex) {
        showLaunchError("Failed to delete script:\n" + ex.getMessage());
      }
    });
  }

  private void createNewScriptInFolder(ExplorerNode folderNode) {
    String prefix = folderNode.relativePath != null ? folderNode.relativePath.toString().replace('\\', '/') + "/" : "";
    TextInputDialog dialog = new TextInputDialog(prefix + "new_scene.vns");
    EditorTheme.apply(dialog);
    dialog.setTitle("New Script");
    dialog.setHeaderText("Create a new VNS script in " + folderNode.displayName);
    dialog.setContentText("Relative path inside scripts/");

    dialog.showAndWait().ifPresent(input -> {
      File launchRoot = resolveLaunchRoot();
      if (launchRoot == null) return;
      try {
        File created = ScriptEditorWorkspaceModel.createScript(launchRoot, input);
        refreshWorkspace();
        restoreSelection(created.getAbsolutePath());
        setStatus("Created " + created.getName());
        if (onOpenFile != null) onOpenFile.accept(created);
      } catch (IOException ex) {
        showLaunchError("Failed to create script:\n" + ex.getMessage());
      }
    });
  }

  private void copyToClipboard(String text) {
    if (text == null || text.isBlank()) return;
    ClipboardContent content = new ClipboardContent();
    content.putString(text);
    Clipboard.getSystemClipboard().setContent(content);
    setStatus("Copied: " + text);
  }

  private void revealFile(File target) {
    if (target == null) return;
    File dir = target.isDirectory() ? target : target.getParentFile();
    if (dir == null || !dir.exists()) return;
    try {
      Desktop.getDesktop().open(dir);
      setStatus("Revealed " + dir.getAbsolutePath());
    } catch (Exception ex) {
      showLaunchError("Failed to reveal:\n" + ex.getMessage());
    }
  }

  private void refreshWorkspace() {
    String previousSelection = selectedNode != null && selectedNode.file() != null
        ? selectedNode.file().getAbsolutePath()
        : null;

    File launchRoot = resolveLaunchRoot();
    snapshot = ScriptEditorWorkspaceModel.index(launchRoot);

    if (launchRoot == null) {
      projectLabel.setText("No project or workspace root available");
      scriptsRootLabel.setText("Load or create a project to browse scripts");
    } else {
      String rootKind = projectRoot != null && projectRoot.equals(launchRoot) ? "Project" : "Workspace";
      projectLabel.setText(rootKind + ": " + launchRoot.getName());
      scriptsRootLabel.setText(snapshot.hasScriptsRoot()
          ? snapshot.scriptsRoot().toString().replace('\\', '/')
          : "No scripts root found under " + launchRoot.getAbsolutePath());
    }

    scriptsStat.setText(Integer.toString(snapshot.scripts().size()));
    foldersStat.setText(Integer.toString(snapshot.folderCount()));
    labelsStat.setText(Integer.toString(snapshot.totalLabelCount()));

    rebuildExplorerTree();
    if (previousSelection != null) {
      restoreSelection(previousSelection);
    }
    if (explorerTree.getSelectionModel().getSelectedItem() == null) {
      selectFirstLeaf();
    }
    updateSelectionInspector();
    updateActionState();
  }

  private void rebuildExplorerTree() {
    List<ScriptFileEntry> visibleScripts = ScriptEditorWorkspaceModel.filter(snapshot.scripts(), filterField.getText());
    TreeItem<ExplorerNode> rootItem = buildExplorerTree(visibleScripts);
    explorerTree.setRoot(rootItem);
    if (rootItem != null) {
      rootItem.setExpanded(true);
      rootItem.getChildren().forEach(child -> child.setExpanded(true));
    }
    explorerHint.setText(visibleScripts.isEmpty()
        ? "No scripts match the current filter."
        : "Double-click or press Enter to open the selected script in the main editor.");
  }

  private TreeItem<ExplorerNode> buildExplorerTree(List<ScriptFileEntry> scripts) {
    String rootName = snapshot.hasScriptsRoot() ? snapshot.scriptsRoot().getFileName().toString() : "scripts";
    TreeItem<ExplorerNode> root = new TreeItem<>(new ExplorerNode(rootName, true, null, null, scripts.size()));
    Map<Path, TreeItem<ExplorerNode>> folders = new HashMap<>();
    folders.put(Path.of(""), root);

    for (ScriptFileEntry entry : scripts) {
      Path relativePath = Path.of(entry.relativePath());
      Path parent = relativePath.getParent();
      TreeItem<ExplorerNode> parentItem = root;
      if (parent != null) {
        Path walk = Path.of("");
        for (Path part : parent) {
          walk = walk.resolve(part.toString());
          TreeItem<ExplorerNode> existing = folders.get(walk);
          if (existing == null) {
            ExplorerNode node = new ExplorerNode(part.toString(), true, walk, null, 0);
            existing = new TreeItem<>(node);
            folders.put(walk, existing);
            parentItem.getChildren().add(existing);
          }
          parentItem = existing;
        }
      }
      parentItem.getChildren().add(new TreeItem<>(new ExplorerNode(entry.displayName(), false, relativePath, entry, 0)));
    }

    sortTree(root);
    updateDirectoryCounts(root);
    return root;
  }

  private void sortTree(TreeItem<ExplorerNode> item) {
    if (item == null) return;
    item.getChildren().sort(Comparator
        .comparing((TreeItem<ExplorerNode> child) -> !child.getValue().directory)
        .thenComparing(child -> child.getValue().displayName.toLowerCase()));
    item.getChildren().forEach(this::sortTree);
  }

  private int updateDirectoryCounts(TreeItem<ExplorerNode> item) {
    if (item == null || item.getValue() == null) return 0;
    if (!item.getValue().directory) return item.getValue().file() != null ? 1 : 0;
    int total = 0;
    for (TreeItem<ExplorerNode> child : item.getChildren()) {
      total += updateDirectoryCounts(child);
    }
    item.getValue().scriptCount = total;
    return total;
  }

  private void restoreSelection(String absolutePath) {
    TreeItem<ExplorerNode> root = explorerTree.getRoot();
    if (root == null || absolutePath == null || absolutePath.isBlank()) return;
    TreeItem<ExplorerNode> match = findByAbsolutePath(root, absolutePath);
    if (match != null) {
      explorerTree.getSelectionModel().select(match);
    }
  }

  private TreeItem<ExplorerNode> findByAbsolutePath(TreeItem<ExplorerNode> item, String absolutePath) {
    if (item == null || item.getValue() == null) return null;
    File file = item.getValue().file();
    if (file != null && absolutePath.equals(file.getAbsolutePath())) return item;
    for (TreeItem<ExplorerNode> child : item.getChildren()) {
      TreeItem<ExplorerNode> match = findByAbsolutePath(child, absolutePath);
      if (match != null) return match;
    }
    return null;
  }

  private void selectFirstLeaf() {
    TreeItem<ExplorerNode> root = explorerTree.getRoot();
    if (root == null) return;
    TreeItem<ExplorerNode> leaf = firstLeaf(root);
    if (leaf != null) explorerTree.getSelectionModel().select(leaf);
  }

  private TreeItem<ExplorerNode> firstLeaf(TreeItem<ExplorerNode> item) {
    if (item == null) return null;
    if (item.getValue() != null && item.getValue().file() != null) return item;
    for (TreeItem<ExplorerNode> child : item.getChildren()) {
      TreeItem<ExplorerNode> leaf = firstLeaf(child);
      if (leaf != null) return leaf;
    }
    return null;
  }

  private void updateSelectionInspector() {
    outlineList.getChildren().clear();
    includesList.getChildren().clear();
    includedByList.getChildren().clear();
    if (selectedNode == null) {
      selectionTitle.setText("Workspace Overview");
      selectionPath.setText(snapshot.hasScriptsRoot()
          ? snapshot.scriptsRoot().toString().replace('\\', '/')
          : "No scripts directory available.");
      selectionMeta.setText(snapshot.hasScriptsRoot()
          ? snapshot.scripts().size() + " scripts • " + snapshot.folderCount() + " folders • "
              + snapshot.totalLabelCount() + " labels"
          : "Open a project with scripts to browse it here.");
      outlineList.getChildren().add(emptyHint("Select a script to inspect its labels and metadata."));
      includesList.getChildren().add(emptyHint("Select a script to see its includes."));
      includedByList.getChildren().add(emptyHint("Select a script to see what includes it."));
      return;
    }

    if (selectedNode.file() == null) {
      File folder = resolveNodeDirectory(selectedNode);
      selectionTitle.setText(selectedNode.displayName);
      selectionPath.setText(folder != null ? folder.getAbsolutePath() : "Folder");
      selectionMeta.setText(selectedNode.scriptCount + " scripts under this folder");
      outlineList.getChildren().add(emptyHint("Select a script file to see its outline."));
      includesList.getChildren().add(emptyHint("Select a script file."));
      includedByList.getChildren().add(emptyHint("Select a script file."));
      return;
    }

    ScriptFileEntry entry = selectedNode.entry;
    selectionTitle.setText(entry.displayName());
    selectionPath.setText(entry.projectRelativePath());
    selectionMeta.setText(entry.lineCount() + " lines • " + entry.labelCount() + " labels • "
        + entry.includeCount() + " includes • " + humanFileSize(entry.sizeBytes())
        + " • modified " + formatModified(entry.lastModifiedMillis()));

    if (entry.labelNames().isEmpty()) {
      outlineList.getChildren().add(emptyHint("This script does not declare any @label entries."));
    } else {
      int maxLabels = Math.min(entry.labelNames().size(), 50);
      for (int i = 0; i < maxLabels; i++) {
        String labelName = entry.labelNames().get(i);
        Integer lineNo = entry.labelLineNumbers().get(labelName);
        String lineHint = lineNo != null ? " (L" + lineNo + ")" : "";
        Label label = new Label("@label " + labelName + lineHint);
        label.setStyle("-fx-text-fill: #c9d6e8; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
            + "-fx-background-color: #1b2230; -fx-background-radius: 5; -fx-cursor: hand;");
        label.setOnMouseEntered(ev -> label.setStyle("-fx-text-fill: #e8f0ff; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
            + "-fx-background-color: #263048; -fx-background-radius: 5; -fx-cursor: hand;"));
        label.setOnMouseExited(ev -> label.setStyle("-fx-text-fill: #c9d6e8; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
            + "-fx-background-color: #1b2230; -fx-background-radius: 5; -fx-cursor: hand;"));
        label.setOnMouseClicked(ev -> {
          if (onOpenFileAtLine != null && lineNo != null) {
            onOpenFileAtLine.accept(entry.file(), lineNo);
            setStatus("Jumped to @label " + labelName + " (L" + lineNo + ")");
          } else if (onOpenFile != null) {
            onOpenFile.accept(entry.file());
            setStatus("Opened " + entry.file().getName() + " (@label " + labelName + ")");
          } else {
            launchEditorWindow(entry.file());
          }
        });
        outlineList.getChildren().add(label);
      }
      if (entry.labelNames().size() > maxLabels) {
        outlineList.getChildren().add(emptyHint("+ " + (entry.labelNames().size() - maxLabels) + " more labels"));
      }
    }

    // Includes
    if (entry.includeTargets().isEmpty()) {
      includesList.getChildren().add(emptyHint("This script does not @include any other scripts."));
    } else {
      for (String target : entry.includeTargets()) {
        ScriptFileEntry resolved = snapshot.findByRelativePath(target);
        Label link = new Label("@include " + target);
        link.setStyle(depLinkStyle(false));
        link.setOnMouseEntered(ev -> link.setStyle(depLinkStyle(true)));
        link.setOnMouseExited(ev -> link.setStyle(depLinkStyle(false)));
        if (resolved != null) {
          link.setOnMouseClicked(ev -> {
            if (onOpenFile != null) {
              onOpenFile.accept(resolved.file());
              setStatus("Opened included script: " + resolved.displayName());
            }
          });
        } else {
          link.setStyle("-fx-text-fill: #a07050; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
              + "-fx-background-color: #1b2230; -fx-background-radius: 5;");
          link.setTooltip(new Tooltip("Could not resolve: " + target));
        }
        includesList.getChildren().add(link);
      }
    }

    // Included by
    List<ScriptFileEntry> dependents = snapshot.includedBy(entry.relativePath());
    if (dependents.isEmpty()) {
      includedByList.getChildren().add(emptyHint("No other scripts @include this file."));
    } else {
      for (ScriptFileEntry dep : dependents) {
        Label link = new Label(dep.relativePath());
        link.setStyle(depLinkStyle(false));
        link.setOnMouseEntered(ev -> link.setStyle(depLinkStyle(true)));
        link.setOnMouseExited(ev -> link.setStyle(depLinkStyle(false)));
        link.setOnMouseClicked(ev -> {
          if (onOpenFile != null) {
            onOpenFile.accept(dep.file());
            setStatus("Opened dependent: " + dep.displayName());
          }
        });
        includedByList.getChildren().add(link);
      }
    }
  }

  private void runContentSearch(String query) {
    searchResults.getChildren().clear();
    // Find the searchResultsCard parent
    javafx.scene.Parent card = searchResults.getParent();
    if (query == null || query.isBlank()) {
      if (card != null) { card.setVisible(false); card.setManaged(false); }
      return;
    }
    List<ScriptEditorWorkspaceModel.SearchHit> hits =
        ScriptEditorWorkspaceModel.searchContent(snapshot, query, 50);
    if (hits.isEmpty()) {
      searchResults.getChildren().add(emptyHint("No matches for \"" + query + "\"."));
    } else {
      Label summary = new Label(hits.size() + (hits.size() >= 50 ? "+" : "") + " results");
      summary.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10px; -fx-padding: 0 0 2 0;");
      searchResults.getChildren().add(summary);
      for (ScriptEditorWorkspaceModel.SearchHit hit : hits) {
        String preview = hit.lineText();
        if (preview.length() > 80) preview = preview.substring(0, 80) + "…";
        Label link = new Label(hit.relativePath() + ":" + hit.lineNumber() + "  " + preview);
        link.setStyle(depLinkStyle(false));
        link.setWrapText(false);
        link.setMaxWidth(Double.MAX_VALUE);
        link.setOnMouseEntered(ev -> link.setStyle(depLinkStyle(true)));
        link.setOnMouseExited(ev -> link.setStyle(depLinkStyle(false)));
        link.setOnMouseClicked(ev -> openFileAtLabel(hit.file(), query, Integer.valueOf(hit.lineNumber())));
        searchResults.getChildren().add(link);
      }
    }
    if (card != null) { card.setVisible(true); card.setManaged(true); }
    setStatus("Search: " + hits.size() + " matches for \"" + query + "\"");
  }

  private static String depLinkStyle(boolean hover) {
    return hover
        ? "-fx-text-fill: #b8d4f0; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
            + "-fx-background-color: #243048; -fx-background-radius: 5; -fx-cursor: hand;"
        : "-fx-text-fill: #9ab0cc; -fx-font-size: 11px; -fx-padding: 2 6 2 6; "
            + "-fx-background-color: #1b2230; -fx-background-radius: 5; -fx-cursor: hand;";
  }

  private void updateActionState() {
    boolean hasLaunchRoot = resolveLaunchRoot() != null;
    boolean hasScriptsRoot = snapshot.hasScriptsRoot();
    boolean hasFileSelection = selectedNode != null && selectedNode.file() != null;

    openInEditorButton.setDisable(!hasFileSelection || onOpenFile == null);
    openWindowButton.setDisable(!hasScriptsRoot);
    newScriptButton.setDisable(!hasLaunchRoot);
    refreshButton.setDisable(!hasLaunchRoot);
    revealButton.setDisable(!(hasScriptsRoot || selectedNode != null));
  }

  private void openSelectedInEditor() {
    if (selectedNode == null || selectedNode.file() == null) return;
    if (onOpenFile != null) {
      onOpenFile.accept(selectedNode.file());
      setStatus("Opened " + selectedNode.entry.projectRelativePath() + " in the main editor");
    } else {
      launchEditorWindow(selectedNode.file());
    }
  }

  private void openFileAtLabel(File file, String labelName, Integer lineNo) {
    if (file == null) return;
    if (onOpenFileAtLine != null && lineNo != null) {
      onOpenFileAtLine.accept(file, lineNo);
      setStatus("Jumped to @label " + labelName + " (L" + lineNo + ")");
    } else if (onOpenFile != null) {
      onOpenFile.accept(file);
      setStatus("Opened " + file.getName() + " (@label " + labelName + ")");
    } else {
      launchEditorWindow(file);
    }
  }

  private void revealSelection() {
    File target = null;
    if (selectedNode != null && selectedNode.file() != null) {
      target = selectedNode.file().getParentFile();
    } else if (selectedNode != null && selectedNode.directory) {
      target = resolveNodeDirectory(selectedNode);
    } else if (snapshot.hasScriptsRoot()) {
      target = snapshot.scriptsRoot().toFile();
    }
    if (target == null || !target.exists()) {
      setStatus("Nothing to reveal.");
      return;
    }
    try {
      Desktop.getDesktop().open(target);
      setStatus("Revealed " + target.getAbsolutePath());
    } catch (Exception ex) {
      showLaunchError("Failed to reveal path:\n" + ex.getMessage());
    }
  }

  private void createNewScript() {
    File launchRoot = resolveLaunchRoot();
    if (launchRoot == null) {
      showLaunchError("No project or workspace root is available for creating scripts.");
      return;
    }

    TextInputDialog dialog = new TextInputDialog("story/new_scene.vns");
    EditorTheme.apply(dialog);
    dialog.setTitle("New Script");
    dialog.setHeaderText("Create a new VNS script");
    dialog.setContentText("Relative path inside scripts/");

    dialog.showAndWait().ifPresent(input -> {
      try {
        File created = ScriptEditorWorkspaceModel.createScript(launchRoot, input);
        refreshWorkspace();
        restoreSelection(created.getAbsolutePath());
        setStatus("Ready: " + created.getName());
        if (onOpenFile != null) {
          onOpenFile.accept(created);
          setStatus("Created and opened " + created.getName());
        } else {
          launchEditorWindow(created);
        }
      } catch (IOException ex) {
        showLaunchError("Failed to create script:\n" + ex.getMessage());
      }
    });
  }

  // ─── Standalone IDE Window ─────────────────────────────────────

  private void launchEditorWindow(File initialFile) {
    File launchRoot = resolveLaunchRoot();
    if (launchRoot == null) {
      showLaunchError("No project or workspace root is available for the script editor.");
      return;
    }
    Path scriptsRoot = ScriptEditorWorkspaceModel.resolveScriptsRoot(launchRoot);
    if (scriptsRoot == null) {
      showLaunchError("No scripts directory was found under:\n" + launchRoot.getAbsolutePath());
      return;
    }

    if (editorWindow != null && editorWindow.isShowing()) {
      if (initialFile != null && editorWindowTabs != null && editorWindowStatus != null && editorWindowLaunchRoot != null) {
        openFileInTab(editorWindowTabs, initialFile, editorWindowLaunchRoot, editorWindowStatus);
      }
      editorWindow.toFront();
      editorWindow.requestFocus();
      return;
    }

    try {
      editorWindow = new Stage();
      editorWindow.setTitle("JVN Script Editor — " + launchRoot.getName());

      BorderPane root = new BorderPane();
      root.getStyleClass().add("script-editor-window-root");

      TreeView<String> fileTree = new TreeView<>();
      configureStandaloneTree(fileTree);
      TreeItem<String> treeRoot = buildFileTree(scriptsRoot, scriptsRoot.getFileName().toString());
      treeRoot.setExpanded(true);
      fileTree.setRoot(treeRoot);
      fileTree.setShowRoot(true);
      fileTree.setPrefWidth(220);

      TabPane editorTabs = new TabPane();
      editorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

      Label windowStatus = new Label("Select a script to begin editing.");
      windowStatus.getStyleClass().add("script-editor-window-status");

      fileTree.setOnMouseClicked(ev -> {
        if (ev.getClickCount() == 2) {
          TreeItem<String> sel = fileTree.getSelectionModel().getSelectedItem();
          if (sel == null || !sel.isLeaf()) return;
          String path = buildTreePath(sel);
          File target = scriptsRoot.resolve(path).toFile();
          if (target.exists() && target.isFile()) {
            openFileInTab(editorTabs, target, launchRoot, windowStatus);
          }
        }
      });

      HBox toolbar = new HBox(6);
      toolbar.getStyleClass().add("script-editor-toolbar");
      toolbar.setPadding(new Insets(5, 10, 5, 10));
      toolbar.setAlignment(Pos.CENTER_LEFT);
      HBox titleRow = new HBox(8, CssIcon.list("#cfd6e6"), toolbarTitle("JVN Script Editor"));
      titleRow.setAlignment(Pos.CENTER_LEFT);

      Separator toolSep1 = new Separator(Orientation.VERTICAL);
      toolSep1.setPadding(new Insets(0, 4, 0, 4));

      Button saveBtn = toolbarButton(
          "Save",
          "Save current file (Ctrl+S)",
          CssIcon.save("#8bcf98"),
          true);
      saveBtn.setOnAction(e -> saveActiveTab(editorTabs, windowStatus));

      Button saveAllBtn = toolbarButton(
          "Save All",
          "Save all open files (Ctrl+Shift+S)",
          CssIcon.save("#6fb7ff"),
          false);
      saveAllBtn.setOnAction(e -> saveAllTabs(editorTabs, windowStatus));

      Separator toolSep2 = new Separator(Orientation.VERTICAL);
      toolSep2.setPadding(new Insets(0, 4, 0, 4));

      Button undoBtn = toolbarButton(
          "Undo",
          "Undo (Ctrl+Z)",
          CssIcon.undo("#d9b36a"),
          false);
      undoBtn.setOnAction(e -> {
        VnsCodeEditor ed = activeEditor(editorTabs);
        if (ed != null) ed.getCodeArea().undo();
      });

      Button redoBtn = toolbarButton(
          "Redo",
          "Redo (Ctrl+Shift+Z)",
          CssIcon.redo("#d9b36a"),
          false);
      redoBtn.setOnAction(e -> {
        VnsCodeEditor ed = activeEditor(editorTabs);
        if (ed != null) ed.getCodeArea().redo();
      });

      Separator toolSep3 = new Separator(Orientation.VERTICAL);
      toolSep3.setPadding(new Insets(0, 4, 0, 4));

      Button findBtn = toolbarButton(
          "Find",
          "Find & Replace (Ctrl+F)",
          CssIcon.search("#8ab4f8"),
          false);
      findBtn.setOnAction(e -> {
        VnsCodeEditor ed = activeEditor(editorTabs);
        if (ed != null) ed.showSearchBar();
      });

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);

      Button refreshBtn = toolbarButton(
          "Refresh",
          "Refresh file tree",
          CssIcon.redo("#c7d0df"),
          false);
      refreshBtn.setOnAction(e -> {
        TreeItem<String> refreshedRoot = buildFileTree(scriptsRoot, scriptsRoot.getFileName().toString());
        refreshedRoot.setExpanded(true);
        fileTree.setRoot(refreshedRoot);
        windowStatus.setText("File tree refreshed.");
      });

      toolbar.getChildren().addAll(
          titleRow, toolSep1,
          saveBtn, saveAllBtn, toolSep2,
          undoBtn, redoBtn, toolSep3,
          findBtn,
          spacer, refreshBtn
      );

      SplitPane split = new SplitPane(fileTree, editorTabs);
      split.setDividerPositions(0.22);
      SplitPane.setResizableWithParent(fileTree, false);

      root.setTop(toolbar);
      root.setCenter(split);
      root.setBottom(windowStatus);

      Scene scene = new Scene(root, 1100, 700);
      EditorTheme.apply(scene);

      scene.addEventFilter(KeyEvent.KEY_PRESSED, (KeyEvent ev) -> {
        if ((ev.isMetaDown() || ev.isControlDown()) && ev.getCode() == KeyCode.S) {
          if (ev.isShiftDown()) {
            saveAllTabs(editorTabs, windowStatus);
          } else {
            saveActiveTab(editorTabs, windowStatus);
          }
          ev.consume();
        }
      });

      editorWindowTabs = editorTabs;
      editorWindowStatus = windowStatus;
      editorWindowLaunchRoot = launchRoot;

      editorWindow.setScene(scene);
      editorWindow.setOnCloseRequest(e -> {
        editorWindow = null;
        editorWindowTabs = null;
        editorWindowStatus = null;
        editorWindowLaunchRoot = null;
      });
      editorWindow.show();
      if (initialFile != null) {
        openFileInTab(editorTabs, initialFile, launchRoot, windowStatus);
      }
      setStatus("Opened script editor window for " + launchRoot.getName());
    } catch (Exception ex) {
      editorWindow = null;
      editorWindowTabs = null;
      editorWindowStatus = null;
      editorWindowLaunchRoot = null;
      showLaunchError("Failed to open script editor window:\n" + ex.getMessage());
    }
  }

  private File resolveLaunchRoot() {
    if (projectRoot != null && projectRoot.isDirectory()) return projectRoot;
    if (workspaceRoot != null && workspaceRoot.isDirectory()) return workspaceRoot;
    return null;
  }

  private void showLaunchError(String message) {
    setStatus(message);
    Alert alert = new Alert(Alert.AlertType.ERROR);
    EditorTheme.apply(alert);
    alert.setTitle("Script Editor");
    alert.setHeaderText("Could not complete script editor action");
    alert.setContentText(message);
    alert.showAndWait();
  }

  private void setStatus(String message) {
    if (onStatus != null && message != null && !message.isBlank()) onStatus.accept(message);
  }

  private TreeItem<String> buildFileTree(Path dir, String displayName) {
    TreeItem<String> item = new TreeItem<>(displayName);
    if (dir == null || !Files.isDirectory(dir)) return item;
    try (var stream = Files.list(dir)) {
      stream.sorted().forEach(p -> {
        String name = p.getFileName().toString();
        if (Files.isDirectory(p)) {
          TreeItem<String> child = buildFileTree(p, name);
          child.setExpanded(true);
          item.getChildren().add(child);
        } else if (name.toLowerCase().endsWith(".vns")) {
          item.getChildren().add(new TreeItem<>(name));
        }
      });
    } catch (IOException ignored) {
    }
    return item;
  }

  private String buildTreePath(TreeItem<String> item) {
    List<String> parts = new ArrayList<>();
    TreeItem<String> cur = item;
    while (cur != null && cur.getParent() != null) {
      parts.add(0, cur.getValue());
      cur = cur.getParent();
    }
    return String.join("/", parts);
  }

  private void openFileInTab(TabPane tabs, File file, File launchRoot, Label status) {
    for (Tab t : tabs.getTabs()) {
      if (file.getAbsolutePath().equals(t.getUserData())) {
        tabs.getSelectionModel().select(t);
        return;
      }
    }

    String content;
    try {
      content = Files.readString(file.toPath());
    } catch (IOException ex) {
      status.setText("Error reading: " + file.getName());
      return;
    }

    VnsCodeEditor editor = new VnsCodeEditor();
    editor.setProjectRoot(launchRoot);
    editor.setFontSizePx(codeEditorFontSize);
    editor.setText(content);

    String baseName = file.getName();
    String rel = launchRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/');

    Tab tab = new Tab(baseName, editor);
    tab.setUserData(file.getAbsolutePath());
    tab.setTooltip(new Tooltip(rel));
    tab.getProperties().put("file", file);
    tab.getProperties().put("editor", editor);
    tab.getProperties().put("baseName", baseName);
    tab.getProperties().put("savedContent", content);
    tab.getProperties().put("dirty", Boolean.FALSE);
    markTabClean(tab);

    editor.setOnTextChanged(text -> {
      String saved = (String) tab.getProperties().getOrDefault("savedContent", "");
      boolean wasDirty = isTabDirty(tab);
      boolean nowDirty = !text.equals(saved);
      if (nowDirty != wasDirty) {
        tab.getProperties().put("dirty", nowDirty);
        if (nowDirty) markTabDirty(tab); else markTabClean(tab);
      }
    });

    tabs.getTabs().add(tab);
    tabs.getSelectionModel().select(tab);
    status.setText("Opened: " + rel);
  }

  private static Button toolbarButton(String text, String tooltip) {
    return toolbarButton(text, tooltip, null, false);
  }

  private static Button toolbarButton(String text, String tooltip, Region icon) {
    return toolbarButton(text, tooltip, icon, false);
  }

  private static Button toolbarButton(String text, String tooltip, Region icon, boolean accent) {
    Button btn = new Button(text);
    if (icon != null) {
      btn.setGraphic(icon);
      btn.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
      btn.setGraphicTextGap(7);
      btn.setAlignment(Pos.CENTER_LEFT);
    }
    btn.getStyleClass().add("script-editor-toolbar-button");
    if (accent) btn.getStyleClass().add("script-editor-toolbar-button-primary");
    btn.setTooltip(new Tooltip(tooltip));
    return btn;
  }

  private static VnsCodeEditor activeEditor(TabPane tabs) {
    Tab sel = tabs.getSelectionModel().getSelectedItem();
    if (sel == null) return null;
    Object ed = sel.getProperties().get("editor");
    return ed instanceof VnsCodeEditor ? (VnsCodeEditor) ed : null;
  }

  private void saveActiveTab(TabPane tabs, Label status) {
    Tab sel = tabs.getSelectionModel().getSelectedItem();
    if (sel == null) return;
    saveTab(sel, status);
  }

  private void saveAllTabs(TabPane tabs, Label status) {
    int saved = 0;
    for (Tab t : tabs.getTabs()) {
      if (isTabDirty(t)) {
        saveTab(t, status);
        saved++;
      }
    }
    if (saved == 0) {
      status.setText("All files are already saved.");
    } else {
      status.setText("Saved " + saved + " file" + (saved > 1 ? "s" : "") + ".");
    }
  }

  private void saveTab(Tab tab, Label status) {
    Object fileObj = tab.getProperties().get("file");
    Object edObj = tab.getProperties().get("editor");
    if (!(fileObj instanceof File file) || !(edObj instanceof VnsCodeEditor editor)) return;
    String text = editor.getText();
    try {
      Files.writeString(file.toPath(), text);
      tab.getProperties().put("savedContent", text);
      tab.getProperties().put("dirty", Boolean.FALSE);
      markTabClean(tab);
      status.setText("Saved: " + file.getName());
    } catch (IOException ex) {
      status.setText("Save failed: " + file.getName() + " — " + ex.getMessage());
    }
  }

  private static boolean isTabDirty(Tab tab) {
    return Boolean.TRUE.equals(tab.getProperties().get("dirty"));
  }

  private static void markTabDirty(Tab tab) {
    String base = (String) tab.getProperties().getOrDefault("baseName", tab.getText());
    tab.setText("\u25CF " + base);
    tab.setStyle("-fx-text-base-color: #f0a050;");
  }

  private static void markTabClean(Tab tab) {
    String base = (String) tab.getProperties().getOrDefault("baseName", tab.getText());
    tab.setText(base);
    tab.setStyle("-fx-text-base-color: #d0d0d0;");
  }

  private static Button styleActionButton(Button button, Region icon, String tooltip, boolean accent) {
    button.setGraphic(icon);
    button.setAlignment(Pos.CENTER_LEFT);
    button.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
    button.setGraphicTextGap(8);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setMinHeight(32);
    button.getStyleClass().removeAll("script-editor-action-button", "script-editor-action-button-accent");
    button.getStyleClass().add("script-editor-action-button");
    if (accent) button.getStyleClass().add("script-editor-action-button-accent");
    if (tooltip != null && !tooltip.isBlank()) button.setTooltip(new Tooltip(tooltip));
    return button;
  }

  private static VBox statCard(String labelText, Label valueLabel) {
    Label label = new Label(labelText);
    label.getStyleClass().add("script-editor-stat-label");
    VBox box = new VBox(2, label, valueLabel);
    box.setPadding(new Insets(8, 10, 8, 10));
    box.getStyleClass().addAll("script-editor-card", "script-editor-stat-card");
    HBox.setHgrow(box, Priority.ALWAYS);
    box.setMaxWidth(Double.MAX_VALUE);
    return box;
  }

  private static Label statValue(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("script-editor-stat-value");
    return label;
  }

  private static VBox labelledMeta(String labelText, Label valueLabel) {
    Label label = new Label(labelText);
    label.getStyleClass().add("script-editor-meta-label");
    valueLabel.setWrapText(true);
    valueLabel.getStyleClass().add("script-editor-meta-value");
    return new VBox(2, label, valueLabel);
  }

  private static Label sectionLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("script-editor-section-label");
    return label;
  }

  private static Label emptyHint(String text) {
    Label label = new Label(text);
    label.setWrapText(true);
    label.getStyleClass().add("script-editor-hint");
    return label;
  }

  private static Label toolbarTitle(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("script-editor-toolbar-title");
    return label;
  }

  private static MenuItem menuItem(String text, Region icon, Runnable action) {
    MenuItem item = new MenuItem(text, icon);
    if (action != null) item.setOnAction(e -> action.run());
    return item;
  }

  private static void configureStandaloneTree(TreeView<String> tree) {
    if (tree == null) return;
    tree.getStyleClass().add("script-editor-tree");
    tree.setCellFactory(view -> new TreeCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          return;
        }
        TreeItem<String> treeItem = getTreeItem();
        boolean directory = treeItem != null && !treeItem.isLeaf();
        Region icon = directory
            ? CssIcon.folder(treeItem != null && treeItem.getParent() == null ? "#f0c66c" : "#d4b169")
            : CssIcon.list("#bfc8d6");
        Label label = new Label(item);
        label.getStyleClass().add(directory ? "script-editor-tree-dir-label" : "script-editor-tree-file-label");
        HBox row = new HBox(6, icon, label);
        row.setAlignment(Pos.CENTER_LEFT);
        setText(null);
        setGraphic(row);
      }
    });
  }

  private static String humanFileSize(long sizeBytes) {
    if (sizeBytes < 1024) return sizeBytes + " B";
    if (sizeBytes < 1024 * 1024) return SIZE_FORMAT.format(sizeBytes / 1024.0) + " KB";
    return SIZE_FORMAT.format(sizeBytes / (1024.0 * 1024.0)) + " MB";
  }

  private static String formatModified(long lastModifiedMillis) {
    if (lastModifiedMillis <= 0) return "unknown";
    return MODIFIED_FORMAT.format(Instant.ofEpochMilli(lastModifiedMillis).atZone(ZoneId.systemDefault()));
  }

  private File resolveNodeDirectory(ExplorerNode node) {
    if (node == null || snapshot.scriptsRoot() == null) return null;
    Path relative = node.relativePath == null ? Path.of("") : node.relativePath;
    return snapshot.scriptsRoot().resolve(relative).toFile();
  }

  private static final class ExplorerNode {
    private final String displayName;
    private final boolean directory;
    private final Path relativePath;
    private final ScriptFileEntry entry;
    private int scriptCount;

    private ExplorerNode(String displayName, boolean directory, Path relativePath,
        ScriptFileEntry entry, int scriptCount) {
      this.displayName = displayName;
      this.directory = directory;
      this.relativePath = relativePath;
      this.entry = entry;
      this.scriptCount = scriptCount;
    }

    private File file() {
      return entry != null ? entry.file() : null;
    }
  }
}
