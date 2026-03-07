package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
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
    if (projectRoot == null || !projectRoot.isDirectory()) return;

    // If already open, just bring to front
    if (editorWindow != null && editorWindow.isShowing()) {
      editorWindow.toFront();
      editorWindow.requestFocus();
      return;
    }

    editorWindow = new Stage();
    editorWindow.setTitle("JVN Script Editor — " + projectRoot.getName());

    BorderPane root = new BorderPane();
    root.setStyle("-fx-background-color: #121212;");

    // Left: file tree
    TreeView<String> fileTree = new TreeView<>();
    fileTree.setStyle("-fx-background-color: #121212;");
    TreeItem<String> treeRoot = buildFileTree(projectRoot.toPath().resolve("scripts"), "scripts");
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
        File target = projectRoot.toPath().resolve(path).toFile();
        if (target.exists() && target.isFile()) {
          openFileInTab(editorTabs, target, windowStatus);
        }
      }
    });

    // Toolbar
    HBox toolbar = new HBox(8);
    toolbar.setPadding(new Insets(6, 10, 6, 10));
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.setStyle("-fx-background-color: #000000; -fx-border-color: #1a1a1a; -fx-border-width: 0 0 1 0;");

    Label titleLabel = new Label("JVN Script Editor");
    titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #e6e6e6;");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Button refreshBtn = new Button("Refresh");
    refreshBtn.setStyle("-fx-font-size: 11px;");
    refreshBtn.setOnAction(e -> {
      TreeItem<String> newRoot = buildFileTree(projectRoot.toPath().resolve("scripts"), "scripts");
      newRoot.setExpanded(true);
      fileTree.setRoot(newRoot);
      windowStatus.setText("File tree refreshed.");
    });

    toolbar.getChildren().addAll(titleLabel, spacer, refreshBtn);

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

    editorWindow.setScene(scene);
    editorWindow.setOnCloseRequest(e -> editorWindow = null);
    editorWindow.show();
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
    while (cur != null) {
      parts.add(0, cur.getValue());
      cur = cur.getParent();
    }
    return String.join("/", parts);
  }

  private void openFileInTab(TabPane tabs, File file, Label status) {
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
    editor.setProjectRoot(projectRoot);
    editor.setText(content);
    editor.setOnTextChanged(text -> {
      // Auto-save on change (debounced would be ideal, but simple write here)
      try {
        Files.writeString(file.toPath(), text);
        status.setText("Saved: " + file.getName());
      } catch (IOException ex) {
        status.setText("Save failed: " + file.getName());
      }
    });

    String rel = projectRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/');
    String tabTitle = file.getName();

    Tab tab = new Tab(tabTitle, editor);
    tab.setUserData(file.getAbsolutePath());
    tab.setTooltip(new Tooltip(rel));
    tabs.getTabs().add(tab);
    tabs.getSelectionModel().select(tab);
    status.setText("Opened: " + rel);
  }
}
