package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Sidebar utility panel that can launch a standalone VNS script editor window
 * for the current project. The sidebar view itself shows project script info
 * and a launch button. The launched window contains a file tree + tabbed
 * VNS code editors with all IDE features.
 */
public class ScriptEditorLauncherView extends VBox {
  private final Label projectLabel = new Label("No project loaded.");
  private final Label scriptCountLabel = new Label("");
  private final Button launchButton = new Button("Open Script Editor Window");
  private final VBox recentScriptsList = new VBox(2);

  private File projectRoot;
  private File workspaceRoot;
  private Consumer<String> onStatus;
  private Stage editorWindow;

  public ScriptEditorLauncherView() {
    setSpacing(10);
    setPadding(new Insets(12));
    setStyle("-fx-background-color: #121212;");

    Label heading = new Label("Script Editor");
    heading.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #e6e6e6;");

    Label desc = new Label(
        "Launch a dedicated VNS script editor window for this project. "
      + "Includes file browser, tabbed editing, syntax highlighting, "
      + "autocomplete, find & replace, and all IDE shortcuts.");
    desc.setWrapText(true);
    desc.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 12px;");

    projectLabel.setStyle("-fx-text-fill: #8ab4f8; -fx-font-size: 12px;");
    scriptCountLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

    launchButton.setStyle(
        "-fx-background-color: #1f6aa5; -fx-text-fill: #ffffff; "
      + "-fx-font-size: 13px; -fx-font-weight: 700; -fx-padding: 8 20 8 20; "
      + "-fx-background-radius: 6;");
    launchButton.setMaxWidth(Double.MAX_VALUE);
    launchButton.setTooltip(new Tooltip("Opens the script editor in a separate window"));
    launchButton.setOnAction(e -> launchEditorWindow());
    launchButton.setDisable(true);

    Label recentHeading = new Label("Scripts in Project");
    recentHeading.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #cdd6f4;");

    ScrollPane recentScroll = new ScrollPane(recentScriptsList);
    recentScroll.setFitToWidth(true);
    recentScroll.setStyle("-fx-background: #121212; -fx-background-color: #121212;");
    VBox.setVgrow(recentScroll, Priority.ALWAYS);

    getChildren().addAll(
        heading, desc, new Separator(),
        projectLabel, scriptCountLabel,
        launchButton, new Separator(),
        recentHeading, recentScroll
    );
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    if (root != null && root.isDirectory()) {
      projectLabel.setText(root.getName());
      launchButton.setDisable(false);
      refreshScriptList();
    } else {
      projectLabel.setText("No project loaded.");
      scriptCountLabel.setText("");
      launchButton.setDisable(true);
      recentScriptsList.getChildren().clear();
    }
  }

  public void setWorkspaceRoot(File root) {
    this.workspaceRoot = root;
  }

  public void setOnStatus(Consumer<String> onStatus) {
    this.onStatus = onStatus;
  }

  public File getProjectRoot() {
    return projectRoot;
  }

  private void refreshScriptList() {
    recentScriptsList.getChildren().clear();
    if (projectRoot == null || !projectRoot.isDirectory()) return;

    List<File> scripts = new ArrayList<>();
    collectVnsFiles(projectRoot.toPath().resolve("scripts"), scripts, 5);
    scriptCountLabel.setText(scripts.size() + " script" + (scripts.size() == 1 ? "" : "s") + " found");

    for (File f : scripts) {
      String rel = projectRoot.toPath().relativize(f.toPath()).toString().replace('\\', '/');
      Label lbl = new Label(rel);
      lbl.setStyle("-fx-text-fill: #b0b8c8; -fx-font-size: 11px; -fx-padding: 2 4 2 4;");
      lbl.setMaxWidth(Double.MAX_VALUE);
      lbl.setOnMouseEntered(ev -> lbl.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-padding: 2 4 2 4; -fx-background-color: #2a2a2a; -fx-background-radius: 3;"));
      lbl.setOnMouseExited(ev -> lbl.setStyle("-fx-text-fill: #b0b8c8; -fx-font-size: 11px; -fx-padding: 2 4 2 4;"));
      recentScriptsList.getChildren().add(lbl);
    }
  }

  private void collectVnsFiles(Path dir, List<File> out, int maxDepth) {
    if (dir == null || !Files.isDirectory(dir) || maxDepth <= 0) return;
    try (var stream = Files.list(dir)) {
      stream.sorted().forEach(p -> {
        if (Files.isDirectory(p)) {
          collectVnsFiles(p, out, maxDepth - 1);
        } else if (p.getFileName().toString().toLowerCase().endsWith(".vns")) {
          out.add(p.toFile());
        }
      });
    } catch (IOException ignored) {}
  }

  // ─── Launch Standalone Editor Window ────────────────────────────────
  public void launchEditorWindow() {
    File launchRoot = resolveLaunchRoot();
    if (launchRoot == null) {
      showLaunchError("No project or workspace root is available for the script editor.");
      return;
    }
    Path scriptsRoot = resolveScriptsRoot(launchRoot);
    if (scriptsRoot == null) {
      showLaunchError("No scripts directory was found under:\n" + launchRoot.getAbsolutePath());
      return;
    }

    // If already open, just bring to front
    if (editorWindow != null && editorWindow.isShowing()) {
      editorWindow.toFront();
      editorWindow.requestFocus();
      return;
    }

    try {
      editorWindow = new Stage();
      editorWindow.setTitle("JVN Script Editor — " + launchRoot.getName());

      BorderPane root = new BorderPane();
      root.setStyle("-fx-background-color: #121212;");

      // Left: file tree
      TreeView<String> fileTree = new TreeView<>();
      fileTree.setStyle("-fx-background-color: #121212;");
      TreeItem<String> treeRoot = buildFileTree(scriptsRoot, scriptsRoot.getFileName().toString());
      treeRoot.setExpanded(true);
      fileTree.setRoot(treeRoot);
      fileTree.setShowRoot(true);
      fileTree.setPrefWidth(220);

      // Center: tabbed editor area
      TabPane editorTabs = new TabPane();
      editorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

      // Status label
      Label windowStatus = new Label("Select a script to begin editing.");
      windowStatus.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px; -fx-padding: 4 10 4 10;");

      // Double-click tree item to open file
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

      // Toolbar
      HBox toolbar = new HBox(6);
      toolbar.setPadding(new Insets(5, 10, 5, 10));
      toolbar.setAlignment(Pos.CENTER_LEFT);
      toolbar.setStyle("-fx-background-color: #0a0a0a; -fx-border-color: #1e1e1e; -fx-border-width: 0 0 1 0;");

      Label titleLabel = new Label("JVN Script Editor");
      titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #e6e6e6;");

      Separator toolSep1 = new Separator(javafx.geometry.Orientation.VERTICAL);
      toolSep1.setPadding(new Insets(0, 4, 0, 4));

      Button saveBtn = toolbarButton("Save", "Save current file (Ctrl+S)");
      saveBtn.setOnAction(e -> saveActiveTab(editorTabs, windowStatus));

      Button saveAllBtn = toolbarButton("Save All", "Save all open files (Ctrl+Shift+S)");
      saveAllBtn.setOnAction(e -> saveAllTabs(editorTabs, windowStatus));

      Separator toolSep2 = new Separator(javafx.geometry.Orientation.VERTICAL);
      toolSep2.setPadding(new Insets(0, 4, 0, 4));

      Button undoBtn = toolbarButton("Undo", "Undo (Ctrl+Z)");
      undoBtn.setOnAction(e -> {
        VnsCodeEditor ed = activeEditor(editorTabs);
        if (ed != null) ed.getCodeArea().undo();
      });

      Button redoBtn = toolbarButton("Redo", "Redo (Ctrl+Shift+Z)");
      redoBtn.setOnAction(e -> {
        VnsCodeEditor ed = activeEditor(editorTabs);
        if (ed != null) ed.getCodeArea().redo();
      });

      Separator toolSep3 = new Separator(javafx.geometry.Orientation.VERTICAL);
      toolSep3.setPadding(new Insets(0, 4, 0, 4));

      Button findBtn = toolbarButton("Find", "Find & Replace (Ctrl+F)");
      findBtn.setOnAction(e -> {
        VnsCodeEditor ed = activeEditor(editorTabs);
        if (ed != null) ed.showSearchBar();
      });

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);

      Button refreshBtn = toolbarButton("Refresh", "Refresh file tree");
      refreshBtn.setOnAction(e -> {
        TreeItem<String> refreshedRoot = buildFileTree(scriptsRoot, scriptsRoot.getFileName().toString());
        refreshedRoot.setExpanded(true);
        fileTree.setRoot(refreshedRoot);
        windowStatus.setText("File tree refreshed.");
      });

      toolbar.getChildren().addAll(
          titleLabel, toolSep1,
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
      try {
        String css = ScriptEditorLauncherView.class.getResource("/com/jvn/editor/editor.css").toExternalForm();
        scene.getStylesheets().add(css);
      } catch (Exception ignore) {}

      // Keyboard shortcuts
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

      editorWindow.setScene(scene);
      editorWindow.setOnCloseRequest(e -> editorWindow = null);
      editorWindow.show();
      setStatus("Opened script editor window for " + launchRoot.getName());
    } catch (Exception ex) {
      editorWindow = null;
      showLaunchError("Failed to open script editor window:\n" + ex.getMessage());
    }
  }

  private File resolveLaunchRoot() {
    if (projectRoot != null && projectRoot.isDirectory()) return projectRoot;
    if (workspaceRoot != null && workspaceRoot.isDirectory()) return workspaceRoot;
    return null;
  }

  private Path resolveScriptsRoot(File root) {
    if (root == null) return null;
    Path[] candidates = new Path[] {
        root.toPath().resolve("scripts"),
        root.toPath().resolve("game/scripts"),
        root.toPath().resolve("runtime/src/main/resources/game/scripts")
    };
    for (Path candidate : candidates) {
      if (Files.isDirectory(candidate)) return candidate;
    }
    return null;
  }

  private void showLaunchError(String message) {
    setStatus(message);
    Alert alert = new Alert(Alert.AlertType.ERROR);
    EditorTheme.apply(alert);
    alert.setTitle("Script Editor");
    alert.setHeaderText("Could not open the script editor window");
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
    } catch (IOException ignored) {}
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
    // Check if already open
    for (Tab t : tabs.getTabs()) {
      if (file.getAbsolutePath().equals(t.getUserData())) {
        tabs.getSelectionModel().select(t);
        return;
      }
    }

    // Read file content
    String content;
    try {
      content = Files.readString(file.toPath());
    } catch (IOException ex) {
      status.setText("Error reading: " + file.getName());
      return;
    }

    VnsCodeEditor editor = new VnsCodeEditor();
    editor.setProjectRoot(launchRoot);
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

    // Track text changes — mark dirty when content differs from saved snapshot
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

  // ─── Toolbar / Save / Dirty-Tracking Helpers ─────────────────────

  private static Button toolbarButton(String text, String tooltip) {
    Button btn = new Button(text);
    btn.setStyle(
        "-fx-background-color: #1a1e28; -fx-text-fill: #c8cdd8; "
      + "-fx-font-size: 11px; -fx-padding: 4 10 4 10; "
      + "-fx-background-radius: 4; -fx-border-color: #2a2e38; "
      + "-fx-border-radius: 4; -fx-cursor: hand;");
    btn.setOnMouseEntered(e -> btn.setStyle(
        "-fx-background-color: #2a3040; -fx-text-fill: #e8ecf4; "
      + "-fx-font-size: 11px; -fx-padding: 4 10 4 10; "
      + "-fx-background-radius: 4; -fx-border-color: #3a4050; "
      + "-fx-border-radius: 4; -fx-cursor: hand;"));
    btn.setOnMouseExited(e -> btn.setStyle(
        "-fx-background-color: #1a1e28; -fx-text-fill: #c8cdd8; "
      + "-fx-font-size: 11px; -fx-padding: 4 10 4 10; "
      + "-fx-background-radius: 4; -fx-border-color: #2a2e38; "
      + "-fx-border-radius: 4; -fx-cursor: hand;"));
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
    tab.setStyle("-fx-text-base-color: #c8d0e0;");
  }
}
