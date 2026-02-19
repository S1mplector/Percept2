package com.jvn.editor.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class HelpCenterView extends BorderPane {
  private static final int TITLE_SCAN_LINE_LIMIT = 100;
  private static final String DOC_OVERVIEW = "docs/Overview.md";
  private static final String DOC_EDITOR = "docs/Editor/Editor.md";
  private static final String DOC_VNS = "docs/VNS Scripting/VNS Scripting.md";
  private static final String DOC_JES = "docs/JES Scripting/JES Scripting.md";
  private static final String DOC_RUNTIME = "docs/Runtime/Runtime.md";
  private static final String DOC_TITLE = "docs/TitleScreen.md";

  private final TextField filterField = new TextField();
  private final ListView<DocEntry> docsList = new ListView<>();
  private final TextArea contentArea = new TextArea();
  private final Label titleLabel = new Label("Help Center");
  private final Label pathLabel = new Label("Select a document to preview.");
  private final Label statsLabel = new Label("No docs indexed");

  private final ObservableList<DocEntry> allDocs = FXCollections.observableArrayList();
  private final FilteredList<DocEntry> filteredDocs = new FilteredList<>(allDocs, e -> true);
  private final Map<String, DocEntry> workspaceDocIndex = new HashMap<>();
  private final Map<String, Button> quickDocButtons = new HashMap<>();

  private File workspaceRoot;
  private File projectRoot;
  private Consumer<File> onOpenDoc;

  public HelpCenterView() {
    getStyleClass().add("help-center-root");
    buildUi();
    refresh();
  }

  public void setWorkspaceRoot(File root) {
    this.workspaceRoot = normalizeDir(root);
    refresh();
  }

  public void setProjectRoot(File root) {
    this.projectRoot = normalizeDir(root);
    refresh();
  }

  public void setOnOpenDoc(Consumer<File> onOpenDoc) {
    this.onOpenDoc = onOpenDoc;
  }

  public void refresh() {
    rebuildIndex();
    applyFilter(filterField.getText());
    if (docsList.getSelectionModel().getSelectedItem() == null && !filteredDocs.isEmpty()) {
      docsList.getSelectionModel().select(0);
    }
    updateQuickDocButtonState();
  }

  private void buildUi() {
    filterField.setPromptText("Filter docs...");
    filterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter(newVal));

    Button refreshButton = new Button("Refresh");
    refreshButton.setOnAction(e -> refresh());
    HBox filterRow = new HBox(8, filterField, refreshButton);
    HBox.setHgrow(filterField, Priority.ALWAYS);
    filterRow.setAlignment(Pos.CENTER_LEFT);

    VBox quickAccessBox = buildQuickAccessBox();
    Label listHeader = new Label("Documents");
    listHeader.getStyleClass().add("help-section-title");

    docsList.setItems(filteredDocs);
    docsList.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(DocEntry item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
          setText(null);
          return;
        }
        Label name = new Label(item.title());
        name.getStyleClass().add("help-doc-title");
        Label info = new Label(item.sourceLabel() + " - " + item.relativePath());
        info.getStyleClass().add("help-doc-subtitle");
        VBox box = new VBox(2, name, info);
        setGraphic(box);
      }
    });
    docsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> showDoc(newVal));
    docsList.setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
        openSelectedDocInEditor();
      }
    });
    docsList.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) openSelectedDocInEditor();
    });

    VBox left = new VBox(10, filterRow, quickAccessBox, new Separator(), listHeader, docsList, statsLabel);
    left.setPadding(new Insets(10));
    left.setPrefWidth(340);
    VBox.setVgrow(docsList, Priority.ALWAYS);

    contentArea.setEditable(false);
    contentArea.setWrapText(true);
    contentArea.setPromptText("Documentation preview");
    contentArea.getStyleClass().add("help-doc-content");

    Button openButton = new Button("Open in Editor");
    openButton.setOnAction(e -> openSelectedDocInEditor());
    Button revealButton = new Button("Reveal File");
    revealButton.setOnAction(e -> revealSelectedDoc());
    Button copyPathButton = new Button("Copy Path");
    copyPathButton.setOnAction(e -> copySelectedPath());
    HBox contentActions = new HBox(8, openButton, revealButton, copyPathButton);

    Text quickHint = new Text("Tip: press F1 from anywhere in the editor to jump back here.");
    quickHint.getStyleClass().add("help-tip-text");
    TextFlow hintFlow = new TextFlow(quickHint);

    VBox right = new VBox(8, titleLabel, pathLabel, contentActions, hintFlow, contentArea);
    right.setPadding(new Insets(12));
    VBox.setVgrow(contentArea, Priority.ALWAYS);

    SplitPane split = new SplitPane(left, right);
    split.setDividerPositions(0.34);
    setCenter(split);
  }

  private VBox buildQuickAccessBox() {
    Label quickHeader = new Label("Quick Access");
    quickHeader.getStyleClass().add("help-section-title");

    HBox docsButtonsRow1 = new HBox(6,
        quickDocButton("README", "README.md"),
        quickDocButton("Overview", DOC_OVERVIEW),
        quickDocButton("Editor", DOC_EDITOR)
    );
    HBox docsButtonsRow2 = new HBox(6,
        quickDocButton("VNS", DOC_VNS),
        quickDocButton("JES", DOC_JES),
        quickDocButton("Runtime", DOC_RUNTIME),
        quickDocButton("Menus", DOC_TITLE)
    );

    Label commandsHeader = new Label("Quick Commands");
    commandsHeader.getStyleClass().add("help-section-title");
    HBox commandButtons = new HBox(6,
        copyCommandButton("Build", "./gradlew build"),
        copyCommandButton("Run Editor", "./gradlew :editor:run"),
        copyCommandButton("Run Runtime", "./gradlew :runtime:run")
    );

    VBox box = new VBox(8, quickHeader, docsButtonsRow1, docsButtonsRow2, commandsHeader, commandButtons);
    box.getStyleClass().add("help-quick-box");
    box.setPadding(new Insets(8));
    return box;
  }

  private Button quickDocButton(String label, String relativePath) {
    Button button = new Button(label);
    button.setOnAction(e -> selectWorkspaceDoc(relativePath));
    quickDocButtons.put(normalizePath(relativePath), button);
    return button;
  }

  private Button copyCommandButton(String label, String command) {
    Button b = new Button(label);
    b.setOnAction(e -> {
      ClipboardContent cc = new ClipboardContent();
      cc.putString(command);
      Clipboard.getSystemClipboard().setContent(cc);
      statsLabel.setText("Copied command: " + command);
    });
    return b;
  }

  private void rebuildIndex() {
    List<DocEntry> docs = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    workspaceDocIndex.clear();

    File ws = workspaceRoot != null ? workspaceRoot : detectWorkspaceRoot();
    if (ws != null) {
      indexWorkspaceDocs(ws, docs, seen);
    }
    File pr = normalizeDir(projectRoot);
    if (pr != null && !isSameDirectory(pr, ws)) {
      indexProjectDocs(pr, docs, seen);
    }

    docs.sort(Comparator
        .comparingInt((DocEntry d) -> d.isWorkspaceDoc() ? 0 : 1)
        .thenComparing(DocEntry::relativePath, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(DocEntry::title, String.CASE_INSENSITIVE_ORDER));

    allDocs.setAll(docs);
    statsLabel.setText("Indexed " + docs.size() + " docs");
  }

  private void indexWorkspaceDocs(File ws, List<DocEntry> docs, Set<String> seen) {
    addDocIfExists(ws, ws, "Workspace", "README.md", docs, seen, true);
    addDocIfExists(ws, ws, "Workspace", "CHANGELOG.md", docs, seen, true);
    collectMarkdownUnder(new File(ws, "docs"), ws, "Workspace", docs, seen, true);
  }

  private void indexProjectDocs(File project, List<DocEntry> docs, Set<String> seen) {
    addDocIfExists(project, project, "Project", "README.md", docs, seen, false);
    collectMarkdownUnder(new File(project, "docs"), project, "Project", docs, seen, false);
  }

  private void addDocIfExists(
      File root,
      File base,
      String source,
      String relativePath,
      List<DocEntry> docs,
      Set<String> seen,
      boolean workspaceDoc
  ) {
    File file = new File(root, relativePath);
    if (!file.exists() || !file.isFile()) return;
    addDoc(base, file, source, docs, seen, workspaceDoc);
  }

  private void collectMarkdownUnder(
      File dir,
      File base,
      String source,
      List<DocEntry> docs,
      Set<String> seen,
      boolean workspaceDoc
  ) {
    if (dir == null || !dir.exists() || !dir.isDirectory()) return;
    try (Stream<Path> stream = Files.walk(dir.toPath())) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
          .forEach(path -> addDoc(base, path.toFile(), source, docs, seen, workspaceDoc));
    } catch (IOException ignored) {
    }
  }

  private void addDoc(
      File base,
      File file,
      String source,
      List<DocEntry> docs,
      Set<String> seen,
      boolean workspaceDoc
  ) {
    String key = canonicalPath(file);
    if (!seen.add(key)) return;
    String relative = relativize(base, file);
    String title = readTitle(file, fallbackTitle(file));
    DocEntry entry = new DocEntry(file, source, relative, title, workspaceDoc);
    docs.add(entry);
    if (workspaceDoc) {
      workspaceDocIndex.put(normalizePath(relative), entry);
    }
  }

  private void applyFilter(String rawFilter) {
    String filter = rawFilter == null ? "" : rawFilter.trim().toLowerCase(Locale.ROOT);
    filteredDocs.setPredicate(entry -> {
      if (filter.isBlank()) return true;
      return entry.title().toLowerCase(Locale.ROOT).contains(filter)
          || entry.relativePath().toLowerCase(Locale.ROOT).contains(filter)
          || entry.sourceLabel().toLowerCase(Locale.ROOT).contains(filter);
    });
    if (!filteredDocs.isEmpty() && docsList.getSelectionModel().getSelectedItem() == null) {
      docsList.getSelectionModel().select(0);
    }
  }

  private void updateQuickDocButtonState() {
    for (Map.Entry<String, Button> e : quickDocButtons.entrySet()) {
      e.getValue().setDisable(!workspaceDocIndex.containsKey(e.getKey()));
    }
  }

  private void selectWorkspaceDoc(String relativePath) {
    DocEntry entry = workspaceDocIndex.get(normalizePath(relativePath));
    if (entry == null) {
      statsLabel.setText("Missing document: " + relativePath);
      return;
    }
    docsList.getSelectionModel().select(entry);
    docsList.scrollTo(entry);
    showDoc(entry);
  }

  private void showDoc(DocEntry entry) {
    if (entry == null) {
      titleLabel.setText("Help Center");
      pathLabel.setText("Select a document to preview.");
      contentArea.setText("");
      return;
    }
    titleLabel.setText(entry.title());
    pathLabel.setText(entry.sourceLabel() + " - " + entry.relativePath());
    try {
      contentArea.setText(Files.readString(entry.file().toPath()));
    } catch (Exception ex) {
      contentArea.setText("Failed to read file:\n" + entry.file().getAbsolutePath() + "\n\n" + ex.getMessage());
    }
  }

  private void openSelectedDocInEditor() {
    DocEntry entry = docsList.getSelectionModel().getSelectedItem();
    if (entry == null || onOpenDoc == null) return;
    onOpenDoc.accept(entry.file());
  }

  private void revealSelectedDoc() {
    DocEntry entry = docsList.getSelectionModel().getSelectedItem();
    if (entry == null) return;
    try {
      if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(entry.file().getParentFile());
      }
    } catch (Exception ex) {
      statsLabel.setText("Cannot reveal file: " + ex.getMessage());
    }
  }

  private void copySelectedPath() {
    DocEntry entry = docsList.getSelectionModel().getSelectedItem();
    if (entry == null) return;
    ClipboardContent cc = new ClipboardContent();
    cc.putString(entry.file().getAbsolutePath());
    Clipboard.getSystemClipboard().setContent(cc);
    statsLabel.setText("Copied path: " + entry.relativePath());
  }

  private File detectWorkspaceRoot() {
    File start = normalizeDir(new File(System.getProperty("user.dir", ".")));
    File cur = start;
    while (cur != null) {
      if (new File(cur, "docs").isDirectory() && new File(cur, "README.md").isFile()) {
        return cur;
      }
      cur = cur.getParentFile();
    }
    return start;
  }

  private String readTitle(File file, String fallback) {
    try {
      List<String> lines = Files.readAllLines(file.toPath());
      int limit = Math.min(lines.size(), TITLE_SCAN_LINE_LIMIT);
      for (int i = 0; i < limit; i++) {
        String line = lines.get(i).trim();
        if (line.startsWith("#")) {
          String clean = line.replaceFirst("^#+\\s*", "").trim();
          if (!clean.isBlank()) return clean;
        }
      }
    } catch (Exception ignored) {
    }
    return fallback;
  }

  private String fallbackTitle(File file) {
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private String canonicalPath(File f) {
    try {
      return f.getCanonicalPath();
    } catch (Exception ex) {
      return f.getAbsolutePath();
    }
  }

  private String relativize(File base, File file) {
    if (base == null) return file.getName();
    try {
      String rel = base.toPath().toAbsolutePath().normalize()
          .relativize(file.toPath().toAbsolutePath().normalize())
          .toString();
      return normalizePath(rel);
    } catch (Exception ex) {
      return file.getName();
    }
  }

  private File normalizeDir(File dir) {
    if (dir == null) return null;
    try {
      return dir.getCanonicalFile();
    } catch (IOException ex) {
      return dir.getAbsoluteFile();
    }
  }

  private boolean isSameDirectory(File a, File b) {
    if (a == null || b == null) return false;
    return canonicalPath(a).equals(canonicalPath(b));
  }

  private String normalizePath(String path) {
    if (path == null) return "";
    return path.replace('\\', '/');
  }

  private record DocEntry(File file, String sourceLabel, String relativePath, String title, boolean isWorkspaceDoc) {}
}
