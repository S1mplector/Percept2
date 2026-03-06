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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class HelpCenterView extends BorderPane {
  private static final int TITLE_SCAN_LINE_LIMIT = 100;
  private static final String DOC_OVERVIEW = "docs/Overview.md";
  private static final String DOC_EDITOR = "docs/Editor/Editor.md";
  private static final String DOC_VNS = "docs/VNS Scripting/VNS Scripting.md";
  private static final String DOC_JES = "docs/JES Scripting/JES Scripting.md";
  private static final String DOC_RUNTIME = "docs/Runtime/Runtime.md";
  private static final String DOC_TITLE = "docs/TitleScreen.md";
  private static final Pattern HEADING_LINE = Pattern.compile("^(#{1,6})\\s+(.*)$");
  private static final Pattern UNORDERED_LIST_LINE = Pattern.compile("^\\s*[-*+]\\s+(.*)$");
  private static final Pattern ORDERED_LIST_LINE = Pattern.compile("^\\s*(\\d+)\\.\\s+(.*)$");
  private static final Pattern HORIZONTAL_RULE_LINE = Pattern.compile("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$");
  private static final Pattern TABLE_SEPARATOR_LINE = Pattern.compile("^\\|?\\s*[-:]+(?:\\s*\\|\\s*[-:]+)+\\s*\\|?\\s*$");
  private static final Pattern IMAGE_LINE = Pattern.compile("^\\s*!\\[([^\\]]*)\\]\\((.+)\\)\\s*$");

  private final TextField filterField = new TextField();
  private final ListView<DocEntry> docsList = new ListView<>();
  private final ScrollPane contentScroll = new ScrollPane();
  private final VBox markdownContent = new VBox(8);
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
  private DocEntry activeDocEntry;

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

    markdownContent.getStyleClass().add("help-doc-content");
    markdownContent.setFillWidth(true);
    contentScroll.setFitToWidth(true);
    contentScroll.setContent(markdownContent);
    contentScroll.getStyleClass().add("help-doc-content-scroll");

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

    VBox right = new VBox(8, titleLabel, pathLabel, contentActions, hintFlow, contentScroll);
    right.setPadding(new Insets(12));
    VBox.setVgrow(contentScroll, Priority.ALWAYS);

    SplitPane split = new SplitPane(left, right);
    split.setDividerPositions(0.34);
    setCenter(split);
    renderMarkdown("");
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
      activeDocEntry = null;
      titleLabel.setText("Help Center");
      pathLabel.setText("Select a document to preview.");
      renderMarkdown("");
      return;
    }
    activeDocEntry = entry;
    titleLabel.setText(entry.title());
    pathLabel.setText(entry.sourceLabel() + " - " + entry.relativePath());
    try {
      renderMarkdown(Files.readString(entry.file().toPath()));
    } catch (Exception ex) {
      renderError("Failed to read file:\n" + entry.file().getAbsolutePath() + "\n\n" + ex.getMessage());
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

  private void renderMarkdown(String markdown) {
    markdownContent.getChildren().clear();
    String normalized = normalizeMarkdown(markdown);
    if (normalized.isBlank()) {
      addEmptyState("Documentation preview");
      return;
    }

    String[] lines = normalized.split("\n", -1);
    int i = 0;
    while (i < lines.length) {
      String line = lines[i];
      String trimmed = line.trim();
      if (trimmed.isBlank()) {
        i++;
        continue;
      }

      MarkdownImage image = parseStandaloneImage(line);
      if (image != null) {
        addImageBlock(image);
        i++;
        continue;
      }

      Matcher heading = HEADING_LINE.matcher(line);
      if (heading.matches()) {
        int level = Math.min(6, heading.group(1).length());
        addHeading(level, heading.group(2).trim());
        i++;
        continue;
      }

      if (trimmed.startsWith("```")) {
        i = renderCodeBlock(lines, i);
        continue;
      }

      if (HORIZONTAL_RULE_LINE.matcher(trimmed).matches()) {
        addHorizontalRule();
        i++;
        continue;
      }

      if (isTableStart(lines, i)) {
        i = renderTableBlock(lines, i);
        continue;
      }

      if (trimmed.startsWith(">")) {
        i = renderQuoteBlock(lines, i);
        continue;
      }

      Matcher unordered = UNORDERED_LIST_LINE.matcher(line);
      if (unordered.matches()) {
        i = renderListBlock(lines, i, false, 1);
        continue;
      }
      Matcher ordered = ORDERED_LIST_LINE.matcher(line);
      if (ordered.matches()) {
        i = renderListBlock(lines, i, true, safeParseInt(ordered.group(1), 1));
        continue;
      }

      i = renderParagraphBlock(lines, i);
    }

    if (markdownContent.getChildren().isEmpty()) {
      addEmptyState("No markdown content.");
    }
  }

  private void renderError(String message) {
    markdownContent.getChildren().clear();
    Label error = new Label(message);
    error.setWrapText(true);
    error.getStyleClass().add("help-md-error");
    markdownContent.getChildren().add(error);
  }

  private int renderParagraphBlock(String[] lines, int start) {
    StringBuilder paragraph = new StringBuilder();
    int i = start;
    while (i < lines.length) {
      String line = lines[i];
      String trimmed = line.trim();
      if (trimmed.isBlank()) break;
      if (trimmed.startsWith("```")
          || HEADING_LINE.matcher(line).matches()
          || HORIZONTAL_RULE_LINE.matcher(trimmed).matches()
          || trimmed.startsWith(">")
          || parseStandaloneImage(line) != null
          || isListLine(line)
          || isTableStart(lines, i)) {
        break;
      }
      if (paragraph.length() > 0) paragraph.append(' ');
      paragraph.append(trimmed);
      i++;
    }
    addParagraph(paragraph.toString());
    return i;
  }

  private int renderCodeBlock(String[] lines, int start) {
    String fence = lines[start].trim();
    String language = fence.length() > 3 ? fence.substring(3).trim() : "";
    StringBuilder code = new StringBuilder();
    int i = start + 1;
    while (i < lines.length) {
      String line = lines[i];
      if (line.trim().startsWith("```")) {
        i++;
        break;
      }
      if (code.length() > 0) code.append('\n');
      code.append(line);
      i++;
    }
    addCodeBlock(language, code.toString());
    return i;
  }

  private int renderQuoteBlock(String[] lines, int start) {
    StringBuilder quote = new StringBuilder();
    int i = start;
    while (i < lines.length) {
      String trimmed = lines[i].trim();
      if (!trimmed.startsWith(">")) break;
      String content = trimmed.length() > 1 ? trimmed.substring(1).trim() : "";
      if (quote.length() > 0) quote.append('\n');
      quote.append(content);
      i++;
    }
    addQuote(quote.toString());
    return i;
  }

  private int renderListBlock(String[] lines, int start, boolean ordered, int orderedStart) {
    VBox listBox = new VBox(4);
    listBox.getStyleClass().add("help-md-list");
    int index = orderedStart;
    int i = start;
    while (i < lines.length) {
      String line = lines[i];
      Matcher matcher = ordered ? ORDERED_LIST_LINE.matcher(line) : UNORDERED_LIST_LINE.matcher(line);
      if (!matcher.matches()) break;
      String content = ordered ? matcher.group(2).trim() : matcher.group(1).trim();
      listBox.getChildren().add(createListItem(content, ordered, index));
      if (ordered) index++;
      i++;
    }
    markdownContent.getChildren().add(listBox);
    return i;
  }

  private int renderTableBlock(String[] lines, int start) {
    List<String[]> rows = new ArrayList<>();
    String[] header = splitTableRow(lines[start]);
    if (header.length == 0) return start + 1;
    rows.add(header);

    int i = start + 2; // Skip markdown separator row.
    while (i < lines.length) {
      String raw = lines[i];
      String trimmed = raw.trim();
      if (trimmed.isBlank() || !raw.contains("|")) break;
      String[] row = splitTableRow(raw);
      if (row.length > 0) rows.add(row);
      i++;
    }
    addTable(rows);
    return i;
  }

  private void addHeading(int level, String text) {
    String style = "help-md-h" + Math.max(1, Math.min(level, 6));
    markdownContent.getChildren().add(createInlineFlow(text, style, 8));
  }

  private void addParagraph(String text) {
    if (text == null || text.isBlank()) return;
    markdownContent.getChildren().add(createInlineFlow(text, "help-md-paragraph", 8));
  }

  private void addQuote(String text) {
    TextFlow flow = createInlineFlow(text, "help-md-quote", 20);
    markdownContent.getChildren().add(flow);
  }

  private void addImageBlock(MarkdownImage image) {
    String source = resolveImageSource(image.target());
    if (source == null) {
      addMissingImageBlock(image, "Image not found: " + image.target());
      return;
    }

    Image fxImage;
    try {
      fxImage = new Image(source, false);
    } catch (Exception ex) {
      addMissingImageBlock(image, "Failed to load image: " + image.target());
      return;
    }
    if (fxImage.isError() || fxImage.getWidth() <= 0 || fxImage.getHeight() <= 0) {
      addMissingImageBlock(image, "Failed to decode image: " + image.target());
      return;
    }

    ImageView imageView = new ImageView(fxImage);
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true);
    imageView.setCache(true);
    imageView.fitWidthProperty().bind(markdownContent.widthProperty().subtract(36));

    StackPane frame = new StackPane(imageView);
    frame.getStyleClass().add("help-md-image-frame");
    frame.setMaxWidth(Double.MAX_VALUE);

    VBox box = new VBox(6, frame);
    box.getStyleClass().add("help-md-image-block");
    box.setMaxWidth(Double.MAX_VALUE);
    box.setFillWidth(true);
    if (image.altText() != null && !image.altText().isBlank()) {
      Label caption = new Label(image.altText());
      caption.getStyleClass().add("help-md-image-caption");
      caption.setWrapText(true);
      box.getChildren().add(caption);
    }
    markdownContent.getChildren().add(box);
  }

  private void addMissingImageBlock(MarkdownImage image, String message) {
    VBox box = new VBox(4);
    box.getStyleClass().add("help-md-image-block");
    Label missing = new Label(message);
    missing.setWrapText(true);
    missing.getStyleClass().add("help-md-image-missing");
    box.getChildren().add(missing);
    if (image.altText() != null && !image.altText().isBlank()) {
      Label caption = new Label(image.altText());
      caption.getStyleClass().add("help-md-image-caption");
      caption.setWrapText(true);
      box.getChildren().add(caption);
    }
    markdownContent.getChildren().add(box);
  }

  private HBox createListItem(String content, boolean ordered, int index) {
    Label marker = new Label(ordered ? (index + ".") : "•");
    marker.getStyleClass().add("help-md-list-marker");

    TextFlow flow = createInlineFlow(content, "help-md-list-item", 34);
    HBox row = new HBox(8, marker, flow);
    row.setAlignment(Pos.TOP_LEFT);
    HBox.setHgrow(flow, Priority.ALWAYS);
    return row;
  }

  private void addCodeBlock(String language, String code) {
    VBox box = new VBox(4);
    box.getStyleClass().add("help-md-code-wrapper");
    if (language != null && !language.isBlank()) {
      Label lang = new Label(language);
      lang.getStyleClass().add("help-md-code-lang");
      box.getChildren().add(lang);
    }
    Label body = new Label(code == null ? "" : code);
    body.setWrapText(true);
    body.setMaxWidth(Double.MAX_VALUE);
    body.prefWidthProperty().bind(markdownContent.widthProperty().subtract(20));
    body.getStyleClass().add("help-md-code-block");
    box.getChildren().add(body);
    markdownContent.getChildren().add(box);
  }

  private void addTable(List<String[]> rows) {
    if (rows == null || rows.isEmpty()) return;
    int colCount = 0;
    for (String[] row : rows) {
      if (row != null) colCount = Math.max(colCount, row.length);
    }
    if (colCount <= 0) return;

    GridPane table = new GridPane();
    table.getStyleClass().add("help-md-table");
    table.setMaxWidth(Double.MAX_VALUE);
    table.prefWidthProperty().bind(markdownContent.widthProperty().subtract(8));

    for (int r = 0; r < rows.size(); r++) {
      String[] row = rows.get(r);
      for (int c = 0; c < colCount; c++) {
        String value = (row != null && c < row.length) ? row[c] : "";
        Label cell = new Label(value);
        cell.setWrapText(true);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.getStyleClass().add(r == 0 ? "help-md-table-header" : "help-md-table-cell");
        GridPane.setHgrow(cell, Priority.ALWAYS);
        table.add(cell, c, r);
      }
    }
    markdownContent.getChildren().add(table);
  }

  private void addHorizontalRule() {
    Separator separator = new Separator();
    separator.getStyleClass().add("help-md-hr");
    markdownContent.getChildren().add(separator);
  }

  private void addEmptyState(String message) {
    Label empty = new Label(message);
    empty.getStyleClass().add("help-md-empty");
    markdownContent.getChildren().add(empty);
  }

  private TextFlow createInlineFlow(String source, String blockClass, double widthInset) {
    TextFlow flow = new TextFlow();
    flow.setLineSpacing(2);
    flow.getStyleClass().add("help-md-flow");
    if (blockClass != null && !blockClass.isBlank()) flow.getStyleClass().add(blockClass);
    flow.setMaxWidth(Double.MAX_VALUE);
    flow.prefWidthProperty().bind(markdownContent.widthProperty().subtract(widthInset));
    appendInlineMarkdown(flow, source == null ? "" : source);
    return flow;
  }

  private void appendInlineMarkdown(TextFlow flow, String source) {
    int i = 0;
    while (i < source.length()) {
      if (source.startsWith("**", i) || source.startsWith("__", i)) {
        String marker = source.substring(i, i + 2);
        int end = source.indexOf(marker, i + 2);
        if (end > i + 2) {
          appendStyledText(flow, source.substring(i + 2, end), "help-md-bold");
          i = end + 2;
          continue;
        }
      }
      if (source.startsWith("*", i) || source.startsWith("_", i)) {
        String marker = source.substring(i, i + 1);
        int end = source.indexOf(marker, i + 1);
        if (end > i + 1) {
          appendStyledText(flow, source.substring(i + 1, end), "help-md-italic");
          i = end + 1;
          continue;
        }
      }
      if (source.startsWith("`", i)) {
        int end = source.indexOf('`', i + 1);
        if (end > i + 1) {
          appendStyledText(flow, source.substring(i + 1, end), "help-md-inline-code");
          i = end + 1;
          continue;
        }
      }
      if (source.startsWith("[", i)) {
        int labelEnd = source.indexOf("](", i + 1);
        if (labelEnd > i + 1) {
          int urlEnd = source.indexOf(')', labelEnd + 2);
          if (urlEnd > labelEnd + 2) {
            appendLinkText(
                flow,
                source.substring(i + 1, labelEnd),
                source.substring(labelEnd + 2, urlEnd).trim());
            i = urlEnd + 1;
            continue;
          }
        }
      }
      int next = findNextMarkdownToken(source, i + 1);
      int end = next < 0 ? source.length() : next;
      appendStyledText(flow, source.substring(i, end));
      i = end;
    }
  }

  private int findNextMarkdownToken(String source, int fromIndex) {
    int best = -1;
    for (char ch : new char[] {'*', '_', '`', '['}) {
      int idx = source.indexOf(ch, fromIndex);
      if (idx >= 0 && (best < 0 || idx < best)) best = idx;
    }
    return best;
  }

  private void appendStyledText(TextFlow flow, String content, String... styleClasses) {
    if (content == null || content.isEmpty()) return;
    Text text = new Text(content);
    text.getStyleClass().add("help-md-text");
    if (styleClasses != null) {
      for (String styleClass : styleClasses) {
        if (styleClass != null && !styleClass.isBlank()) text.getStyleClass().add(styleClass);
      }
    }
    flow.getChildren().add(text);
  }

  private void appendLinkText(TextFlow flow, String label, String target) {
    String textLabel = (label == null || label.isBlank()) ? target : label;
    if (textLabel == null || textLabel.isBlank()) return;
    Text link = new Text(textLabel);
    link.getStyleClass().addAll("help-md-text", "help-md-link");
    link.setUnderline(true);
    if (target != null && !target.isBlank()) {
      link.setOnMouseClicked(e -> openMarkdownLink(target));
    }
    flow.getChildren().add(link);
  }

  private void openMarkdownLink(String target) {
    if (target == null || target.isBlank()) return;
    String resolvedTarget = extractLinkTarget(target);
    if (resolvedTarget.isBlank()) return;
    try {
      if ((resolvedTarget.startsWith("http://") || resolvedTarget.startsWith("https://")) && Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(java.net.URI.create(resolvedTarget));
        return;
      }
    } catch (Exception ignored) {
    }
    Path localPath = resolveLocalPath(resolvedTarget);
    if (localPath != null && Files.isRegularFile(localPath)) {
      if (resolvedTarget.toLowerCase(Locale.ROOT).endsWith(".md") && selectDocByPath(localPath)) {
        return;
      }
      try {
        if (Desktop.isDesktopSupported()) {
          Desktop.getDesktop().open(localPath.toFile());
          return;
        }
      } catch (Exception ignored) {
      }
      ClipboardContent filePath = new ClipboardContent();
      filePath.putString(localPath.toString());
      Clipboard.getSystemClipboard().setContent(filePath);
      statsLabel.setText("Copied file path: " + localPath);
      return;
    }
    ClipboardContent cc = new ClipboardContent();
    cc.putString(resolvedTarget);
    Clipboard.getSystemClipboard().setContent(cc);
    statsLabel.setText("Copied link: " + resolvedTarget);
  }

  private MarkdownImage parseStandaloneImage(String line) {
    if (line == null) return null;
    Matcher image = IMAGE_LINE.matcher(line.trim());
    if (!image.matches()) return null;
    String alt = image.group(1) == null ? "" : image.group(1).trim();
    String target = extractLinkTarget(image.group(2));
    if (target.isBlank()) return null;
    return new MarkdownImage(alt, target);
  }

  private String resolveImageSource(String target) {
    String resolved = extractLinkTarget(target);
    if (resolved.isBlank()) return null;
    if (resolved.startsWith("http://") || resolved.startsWith("https://") || resolved.startsWith("file:")) {
      return resolved;
    }
    Path local = resolveLocalPath(resolved);
    if (local == null || !Files.isRegularFile(local)) return null;
    return local.toUri().toString();
  }

  private Path resolveLocalPath(String target) {
    if (target == null || target.isBlank()) return null;
    try {
      Path asPath = Path.of(target);
      if (asPath.isAbsolute()) {
        return asPath.normalize();
      }
    } catch (Exception ignored) {
    }
    if (activeDocEntry != null && activeDocEntry.file() != null) {
      File parent = activeDocEntry.file().getParentFile();
      if (parent != null) {
        return parent.toPath().resolve(target).normalize();
      }
    }
    File ws = workspaceRoot != null ? workspaceRoot : detectWorkspaceRoot();
    if (ws != null) {
      return ws.toPath().resolve(target).normalize();
    }
    return Path.of(target).toAbsolutePath().normalize();
  }

  private boolean selectDocByPath(Path path) {
    if (path == null) return false;
    String wanted = canonicalPath(path.toFile());
    for (DocEntry entry : allDocs) {
      if (canonicalPath(entry.file()).equals(wanted)) {
        docsList.getSelectionModel().select(entry);
        docsList.scrollTo(entry);
        showDoc(entry);
        return true;
      }
    }
    return false;
  }

  private String extractLinkTarget(String raw) {
    if (raw == null) return "";
    String value = raw.trim();
    if (value.isBlank()) return "";
    if (value.startsWith("<")) {
      int close = value.indexOf('>');
      if (close > 1) {
        return value.substring(1, close).trim();
      }
    }
    int titleSeparator = findLinkTitleSeparator(value);
    if (titleSeparator > 0) {
      value = value.substring(0, titleSeparator).trim();
    }
    return value;
  }

  private int findLinkTitleSeparator(String value) {
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (!Character.isWhitespace(ch)) continue;
      String trailing = value.substring(i).trim();
      if (trailing.startsWith("\"") || trailing.startsWith("'") || trailing.startsWith("(")) {
        return i;
      }
    }
    return -1;
  }

  private boolean isListLine(String line) {
    return UNORDERED_LIST_LINE.matcher(line).matches() || ORDERED_LIST_LINE.matcher(line).matches();
  }

  private boolean isTableStart(String[] lines, int index) {
    if (lines == null || index < 0 || index + 1 >= lines.length) return false;
    String current = lines[index];
    if (current == null || !current.contains("|")) return false;
    return TABLE_SEPARATOR_LINE.matcher(lines[index + 1].trim()).matches();
  }

  private String[] splitTableRow(String line) {
    if (line == null) return new String[0];
    String row = line.trim();
    if (row.startsWith("|")) row = row.substring(1);
    if (row.endsWith("|")) row = row.substring(0, row.length() - 1);
    if (row.isBlank()) return new String[0];
    String[] rawCells = row.split("\\|", -1);
    for (int i = 0; i < rawCells.length; i++) {
      rawCells[i] = rawCells[i].trim();
    }
    return rawCells;
  }

  private int safeParseInt(String raw, int fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private String normalizeMarkdown(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
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
  private record MarkdownImage(String altText, String target) {}
}
