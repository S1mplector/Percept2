package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class HelpCenterView extends BorderPane {
  private static final String HELP_CENTER_VERSION = "1.2.2";
  private static final int TITLE_SCAN_LINE_LIMIT = 100;
  private static final int SUMMARY_SCAN_LINE_LIMIT = 140;
  private static final Pattern HEADING_LINE = Pattern.compile("^(#{1,6})\\s+(.*)$");
  private static final Pattern UNORDERED_LIST_LINE = Pattern.compile("^\\s*[-*+]\\s+(.*)$");
  private static final Pattern ORDERED_LIST_LINE = Pattern.compile("^\\s*(\\d+)\\.\\s+(.*)$");
  private static final Pattern HORIZONTAL_RULE_LINE = Pattern.compile("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$");
  private static final Pattern TABLE_SEPARATOR_LINE = Pattern.compile("^\\|?\\s*[-:]+(?:\\s*\\|\\s*[-:]+)+\\s*\\|?\\s*$");
  private static final Pattern IMAGE_LINE = Pattern.compile("^\\s*!\\[([^\\]]*)\\]\\((.+)\\)\\s*$");
  private static final GuideSection SECTION_START = new GuideSection("start", "Start Here", "Onboarding and orientation");
  private static final GuideSection SECTION_VNS = new GuideSection("vns", "Visual Novel Authoring", "Story-first docs for VNS projects");
  private static final GuideSection SECTION_JES = new GuideSection("jes", "Gameplay And JES", "Scene scripting, systems, and runtime entities");
  private static final GuideSection SECTION_PUPPETEER = new GuideSection("puppeteer", "Animation And Timelines", "Puppeteer, timeline runtime, and reuse");
  private static final GuideSection SECTION_UI = new GuideSection("ui", "Menus And UI Layout", "Menus, dialogue UI, layouts, and styling");
  private static final GuideSection SECTION_EDITOR = new GuideSection("editor", "Editor And Tools", "Core editor docs and sidebar workflows");
  private static final GuideSection SECTION_RUNTIME = new GuideSection("runtime", "Runtime And Project Setup", "Launch, packaging, assets, and project structure");
  private static final GuideSection SECTION_ARCHITECTURE = new GuideSection("architecture", "Architecture And Internals", "Engine structure, quality, and native systems");
  private static final GuideSection SECTION_GUIDES = new GuideSection("guides", "Guides And Recipes", "Practical walkthroughs and cookbook docs");
  private static final GuideSection SECTION_PROJECT = new GuideSection("project", "Current Project Docs", "Project-local guides and notes");
  private static final GuideSection SECTION_REFERENCE = new GuideSection("reference", "Reference And Generated Docs", "Low-priority or generated reference pages");
  private static final List<GuideSection> GUIDE_SECTIONS = List.of(
      SECTION_START,
      SECTION_VNS,
      SECTION_JES,
      SECTION_PUPPETEER,
      SECTION_UI,
      SECTION_EDITOR,
      SECTION_RUNTIME,
      SECTION_ARCHITECTURE,
      SECTION_GUIDES,
      SECTION_PROJECT,
      SECTION_REFERENCE
  );

  private final TextField filterField = new TextField();
  private final TreeView<HelpNode> docsTree = new TreeView<>();
  private final ScrollPane contentScroll = new ScrollPane();
  private final VBox markdownContent = new VBox(8);
  private final Label titleLabel = new Label("Help Center");
  private final Label sourceBadgeLabel = new Label("Guide");
  private final Label pathLabel = new Label("Select a document to preview.");
  private final Label summaryLabel = new Label("Follow the guided tree on the left to move from onboarding into deeper reference material.");
  private final Label statsLabel = new Label("No docs indexed");

  private final ObservableList<DocEntry> allDocs = FXCollections.observableArrayList();
  private final Map<String, DocEntry> workspaceDocIndex = new HashMap<>();
  private final Map<String, TreeItem<HelpNode>> visibleDocNodes = new HashMap<>();
  private final SplitPane contentSplit = new SplitPane();

  private File workspaceRoot;
  private File projectRoot;
  private Consumer<File> onOpenDoc;
  private DocEntry activeDocEntry;
  private VBox guideTreePane;
  private VBox previewPane;
  private VBox guideTreeRestoreRail;
  private double guideTreeDividerPosition = 0.34;
  private boolean guideTreeCollapsed;

  public HelpCenterView() {
    getStyleClass().addAll("help-center-root", "sidebar-tool-root");
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
    rebuildGuideTree(filterField.getText());
  }

  private void buildUi() {
    filterField.setPromptText("Filter docs...");
    filterField.getStyleClass().add("help-filter-field");
    filterField.textProperty().addListener((obs, oldVal, newVal) -> rebuildGuideTree(newVal));
    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ESCAPE && !filterField.getText().isBlank()) {
        filterField.clear();
        e.consume();
      } else if (e.getCode() == KeyCode.DOWN) {
        docsTree.requestFocus();
        if (docsTree.getSelectionModel().isEmpty() && docsTree.getRoot() != null) {
          TreeItem<HelpNode> first = firstDocNode(docsTree.getRoot());
          if (first != null) docsTree.getSelectionModel().select(first);
        }
        e.consume();
      }
    });

    Button clearFilterButton = new Button("\u2715");
    clearFilterButton.getStyleClass().addAll("help-toolbar-button", "help-toolbar-button-icon");
    clearFilterButton.setTooltip(new Tooltip("Clear filter (Esc)"));
    clearFilterButton.setOnAction(e -> filterField.clear());
    clearFilterButton.disableProperty().bind(
        javafx.beans.binding.Bindings.createBooleanBinding(
            () -> filterField.getText() == null || filterField.getText().isBlank(),
            filterField.textProperty()));

    Button expandAllButton = new Button("Expand");
    expandAllButton.getStyleClass().add("help-toolbar-button");
    expandAllButton.setTooltip(new Tooltip("Expand the guide tree"));
    expandAllButton.setOnAction(e -> setAllSectionsExpanded(true));

    Button collapseAllButton = new Button("Collapse");
    collapseAllButton.getStyleClass().add("help-toolbar-button");
    collapseAllButton.setTooltip(new Tooltip("Collapse guide sections and topic folders"));
    collapseAllButton.setOnAction(e -> setAllSectionsExpanded(false));

    Button refreshButton = new Button("Refresh");
    refreshButton.getStyleClass().add("help-toolbar-button");
    refreshButton.setTooltip(new Tooltip("Re-scan workspace and project docs"));
    refreshButton.setOnAction(e -> refresh());

    HBox filterRow = new HBox(6, filterField, clearFilterButton);
    filterRow.getStyleClass().add("help-filter-row");
    HBox.setHgrow(filterField, Priority.ALWAYS);
    filterRow.setAlignment(Pos.CENTER_LEFT);

    HBox treeToolbar = new HBox(6, expandAllButton, collapseAllButton, spacer(), refreshButton);
    treeToolbar.getStyleClass().add("help-tree-toolbar");
    treeToolbar.setAlignment(Pos.CENTER_LEFT);

    Label browserTitle = new Label("Guide Tree");
    browserTitle.getStyleClass().add("help-pane-title");
    Label versionChip = new Label("v" + HELP_CENTER_VERSION);
    versionChip.getStyleClass().add("help-version-chip");
    Button collapseGuideButton = new Button("<");
    collapseGuideButton.getStyleClass().addAll("help-toolbar-button", "help-toolbar-button-icon", "help-guide-collapse-button");
    collapseGuideButton.setTooltip(new Tooltip("Collapse guide tree"));
    collapseGuideButton.setOnAction(e -> setGuideTreeCollapsed(true));
    HBox browserTitleRow = new HBox(10, browserTitle, versionChip, spacer(), collapseGuideButton);
    browserTitleRow.getStyleClass().add("help-guide-title-row");
    browserTitleRow.setAlignment(Pos.CENTER_LEFT);
    Label browserSubtitle = new Label(
        "Topic folders keep docs in an onboarding-first order across scripting, animation, UI, runtime, tools, and internals.");
    browserSubtitle.getStyleClass().add("help-pane-subtitle");
    browserSubtitle.setWrapText(true);

    docsTree.setShowRoot(false);
    docsTree.getStyleClass().add("help-doc-tree");
    docsTree.setFixedCellSize(28);
    docsTree.setCellFactory(tree -> new TreeCell<>() {
      @Override
      protected void updateItem(HelpNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
          setText(null);
          setTooltip(null);
          return;
        }
        setText(null);
        if (item.section()) {
          Label name = new Label(item.title());
          name.getStyleClass().add("help-guide-section-title");
          name.setMaxWidth(Double.MAX_VALUE);
          HBox.setHgrow(name, Priority.ALWAYS);
          Label count = new Label(String.valueOf(item.documentCount()));
          count.getStyleClass().add("help-guide-section-count");
          HBox row = new HBox(8, name, count);
          row.setAlignment(Pos.CENTER_LEFT);
          row.getStyleClass().add("help-guide-section-row");
          setGraphic(row);
          if (item.subtitle() != null && !item.subtitle().isBlank()) {
            setTooltip(buildTooltip(item.title(), item.subtitle()));
          } else {
            setTooltip(null);
          }
          return;
        }

        if (item.group()) {
          Region folderIcon = CssIcon.folder("#d9ad64");
          folderIcon.getStyleClass().add("help-doc-icon");
          Label name = new Label(item.title());
          name.getStyleClass().add("help-guide-folder-title");
          name.setMaxWidth(Double.MAX_VALUE);
          HBox.setHgrow(name, Priority.ALWAYS);
          Label count = new Label(String.valueOf(item.documentCount()));
          count.getStyleClass().add("help-guide-folder-count");
          HBox row = new HBox(8, folderIcon, name, count);
          row.setAlignment(Pos.CENTER_LEFT);
          row.getStyleClass().add("help-guide-folder-row");
          setGraphic(row);
          if (item.subtitle() != null && !item.subtitle().isBlank()) {
            setTooltip(buildTooltip(item.title(), item.subtitle()));
          } else {
            setTooltip(null);
          }
          return;
        }

        if (item.isHeading()) {
          String indent = item.headingLevel() <= 2 ? "" : "  ";
          Label marker = new Label("§");
          marker.getStyleClass().add("help-heading-marker");
          Label name = new Label(indent + item.title());
          name.getStyleClass().add("help-heading-title");
          name.setMaxWidth(Double.MAX_VALUE);
          HBox.setHgrow(name, Priority.ALWAYS);
          HBox row = new HBox(6, marker, name);
          row.setAlignment(Pos.CENTER_LEFT);
          row.getStyleClass().add("help-heading-row");
          setGraphic(row);
          setTooltip(null);
          return;
        }

        DocEntry entry = item.doc();
        Region docIcon = CssIcon.document("#c6d1dc");
        docIcon.getStyleClass().add("help-doc-icon");
        Label name = new Label(entry.title());
        name.getStyleClass().add("help-doc-title");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        Label source = new Label(entry.isWorkspaceDoc() ? "W" : "P");
        source.getStyleClass().add("help-doc-source-mini");
        source.setTooltip(new Tooltip(entry.sourceLabel() + " doc"));

        HBox row = new HBox(8, docIcon, name, source);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("help-doc-row");
        setGraphic(row);
        setTooltip(buildTooltip(entry.title(), entry.relativePath() + "\n\n" + entry.summary()));
      }
    });
    docsTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal == null || newVal.getValue() == null) return;
      HelpNode node = newVal.getValue();
      if (node.section() || node.group()) return;
      DocEntry entry = node.doc();
      if (entry == null) return;
      if (node.isHeading()) {
        if (activeDocEntry == null || !canonicalPath(activeDocEntry.file()).equals(canonicalPath(entry.file()))) {
          showDoc(entry);
        }
        scrollToHeading(node.title());
      } else {
        showDoc(entry);
      }
    });
    docsTree.setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
        openSelectedDocInEditor();
      }
    });
    docsTree.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) openSelectedDocInEditor();
    });

    statsLabel.getStyleClass().add("help-stats-label");

    VBox left = new VBox(8, browserTitleRow, browserSubtitle, filterRow, treeToolbar, new Separator(), docsTree, statsLabel);
    left.getStyleClass().add("help-browser-pane");
    left.setPadding(new Insets(10));
    left.setPrefWidth(340);
    VBox.setVgrow(docsTree, Priority.ALWAYS);
    guideTreePane = left;
    guideTreeRestoreRail = createGuideTreeRestoreRail();

    markdownContent.getStyleClass().add("help-doc-content");
    markdownContent.setFillWidth(true);
    contentScroll.setFitToWidth(true);
    contentScroll.setContent(markdownContent);
    contentScroll.getStyleClass().add("help-doc-content-scroll");
    contentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    contentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    installSmoothScroll(contentScroll);

    titleLabel.getStyleClass().add("help-preview-title");
    sourceBadgeLabel.getStyleClass().add("help-doc-source-chip");
    pathLabel.getStyleClass().add("help-preview-path");
    summaryLabel.getStyleClass().add("help-preview-summary");
    summaryLabel.setWrapText(true);
    HBox sourceRow = new HBox(8, sourceBadgeLabel, pathLabel);
    sourceRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(pathLabel, Priority.ALWAYS);

    Button openButton = new Button("Open in Editor");
    openButton.getStyleClass().add("help-toolbar-button");
    openButton.setTooltip(new Tooltip("Open the selected documentation page in the editor"));
    openButton.setOnAction(e -> openSelectedDocInEditor());
    Button revealButton = new Button("Reveal File");
    revealButton.getStyleClass().add("help-toolbar-button");
    revealButton.setTooltip(new Tooltip("Reveal the selected documentation file on disk"));
    revealButton.setOnAction(e -> revealSelectedDoc());
    Button copyPathButton = new Button("Copy Path");
    copyPathButton.getStyleClass().add("help-toolbar-button");
    copyPathButton.setTooltip(new Tooltip("Copy the selected documentation path"));
    copyPathButton.setOnAction(e -> copySelectedDocPath());
    javafx.beans.binding.BooleanBinding noDocSelected = javafx.beans.binding.Bindings.createBooleanBinding(
        () -> selectedDocEntry() == null,
        docsTree.getSelectionModel().selectedItemProperty());
    openButton.disableProperty().bind(noDocSelected);
    revealButton.disableProperty().bind(noDocSelected);
    copyPathButton.disableProperty().bind(noDocSelected);
    HBox contentActions = new HBox(8, openButton, revealButton, copyPathButton);
    contentActions.setAlignment(Pos.CENTER_LEFT);

    Text quickHint = new Text("Tip: press F1 from anywhere in the editor to jump back here.");
    quickHint.getStyleClass().add("help-tip-text");
    TextFlow hintFlow = new TextFlow(quickHint);
    hintFlow.getStyleClass().add("help-tip-flow");

    VBox previewHeader = new VBox(6, titleLabel, sourceRow, summaryLabel);
    previewHeader.getStyleClass().add("help-preview-header");

    VBox right = new VBox(10, previewHeader, contentActions, hintFlow, contentScroll);
    right.getStyleClass().add("help-preview-pane");
    right.setPadding(new Insets(12));
    VBox.setVgrow(contentScroll, Priority.ALWAYS);
    previewPane = right;

    contentSplit.getItems().setAll(left, right);
    contentSplit.setDividerPositions(guideTreeDividerPosition);
    setCenter(contentSplit);
    showGuidePlaceholder(
        "Help Center",
        "Select a document to preview.",
        "Follow the guide tree on the left to move from onboarding into deeper authoring and engine reference.",
        "Pick a document from the guide tree to preview it here.");
  }

  private VBox createGuideTreeRestoreRail() {
    Button restoreButton = new Button(">");
    restoreButton.getStyleClass().addAll("help-toolbar-button", "help-toolbar-button-icon", "help-guide-restore-button");
    restoreButton.setTooltip(new Tooltip("Show guide tree"));
    restoreButton.setOnAction(e -> setGuideTreeCollapsed(false));

    Label label = new Label("Guide\nTree");
    label.getStyleClass().add("help-guide-restore-label");
    label.setAlignment(Pos.CENTER);
    label.setWrapText(true);
    label.setMaxWidth(34);

    VBox rail = new VBox(8, restoreButton, label);
    rail.getStyleClass().add("help-guide-restore-rail");
    rail.setAlignment(Pos.TOP_CENTER);
    rail.setPadding(new Insets(10, 4, 10, 4));
    rail.setMinWidth(42);
    rail.setPrefWidth(42);
    rail.setMaxWidth(42);
    return rail;
  }

  private void setGuideTreeCollapsed(boolean collapsed) {
    if (guideTreeCollapsed == collapsed || guideTreePane == null || previewPane == null) return;
    guideTreeCollapsed = collapsed;
    if (collapsed) {
      double[] positions = contentSplit.getDividerPositions();
      if (positions.length > 0) {
        guideTreeDividerPosition = Math.max(0.18, Math.min(0.60, positions[0]));
      }
      contentSplit.getItems().clear();
      setLeft(guideTreeRestoreRail);
      setCenter(previewPane);
      contentScroll.requestFocus();
      return;
    }

    setLeft(null);
    contentSplit.getItems().setAll(guideTreePane, previewPane);
    setCenter(contentSplit);
    javafx.application.Platform.runLater(() -> contentSplit.setDividerPositions(guideTreeDividerPosition));
    filterField.requestFocus();
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
    collectMarkdownUnder(ws, ws, "Workspace", docs, seen, true);
  }

  private void indexProjectDocs(File project, List<DocEntry> docs, Set<String> seen) {
    collectMarkdownUnder(project, project, "Project", docs, seen, false);
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
          .filter(path -> !shouldSkipMarkdownPath(base.toPath(), path))
          .forEach(path -> addDoc(base, path.toFile(), source, docs, seen, workspaceDoc));
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
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
    String summary = readSummary(file);
    List<HeadingInfo> headings = readHeadings(file);
    DocEntry entry = new DocEntry(file, source, relative, title, summary, workspaceDoc, headings);
    docs.add(entry);
    if (workspaceDoc) {
      workspaceDocIndex.put(normalizePath(relative), entry);
    }
  }

  private void rebuildGuideTree(String rawFilter) {
    String filter = rawFilter == null ? "" : rawFilter.trim().toLowerCase(Locale.ROOT);
    String selectedPath = activeDocEntry == null ? "" : canonicalPath(activeDocEntry.file());

    TreeItem<HelpNode> root = new TreeItem<>(HelpNode.section(new GuideSection("root", "Docs", ""), 0));
    root.setExpanded(true);
    visibleDocNodes.clear();

    int visibleDocCount = 0;
    int visibleSectionCount = 0;
    int visibleFolderCount = 0;
    for (GuideSection section : GUIDE_SECTIONS) {
      Map<GuideBucket, List<FilteredDocEntry>> docsByBucket = new LinkedHashMap<>();
      for (DocEntry entry : allDocs) {
        if (classifySection(entry) != section) continue;
        FilteredDocEntry filteredDoc = filterDoc(entry, filter);
        if (filteredDoc == null) continue;
        GuideBucket bucket = classifyBucket(section, entry);
        docsByBucket.computeIfAbsent(bucket, key -> new ArrayList<>()).add(filteredDoc);
      }
      if (docsByBucket.isEmpty()) continue;

      int sectionDocCount = docsByBucket.values().stream().mapToInt(List::size).sum();
      TreeItem<HelpNode> sectionItem = new TreeItem<>(HelpNode.section(section, sectionDocCount));
      sectionItem.setExpanded(shouldExpandSection(section, filter));

      List<Map.Entry<GuideBucket, List<FilteredDocEntry>>> bucketEntries = new ArrayList<>(docsByBucket.entrySet());
      bucketEntries.sort(Comparator
          .comparingInt((Map.Entry<GuideBucket, List<FilteredDocEntry>> e) -> e.getKey().rank())
          .thenComparing(e -> e.getKey().title(), String.CASE_INSENSITIVE_ORDER));
      boolean showBuckets = shouldShowBuckets(section, bucketEntries);
      for (Map.Entry<GuideBucket, List<FilteredDocEntry>> bucketEntry : bucketEntries) {
        GuideBucket bucket = bucketEntry.getKey();
        List<FilteredDocEntry> docsForBucket = bucketEntry.getValue();
        docsForBucket.sort((left, right) -> compareDocs(left.entry(), right.entry()));
        TreeItem<HelpNode> parent = sectionItem;
        if (showBuckets) {
          TreeItem<HelpNode> bucketItem = new TreeItem<>(HelpNode.group(bucket, docsForBucket.size()));
          bucketItem.setExpanded(shouldExpandBucket(bucket, filter));
          sectionItem.getChildren().add(bucketItem);
          parent = bucketItem;
          visibleFolderCount++;
        }
        for (FilteredDocEntry filteredDoc : docsForBucket) {
          DocEntry entry = filteredDoc.entry();
          TreeItem<HelpNode> docItem = new TreeItem<>(HelpNode.doc(entry));
          for (HeadingInfo h : filteredDoc.visibleHeadings()) {
            docItem.getChildren().add(new TreeItem<>(HelpNode.heading(entry, h.level(), h.text())));
          }
          docItem.setExpanded(!filter.isBlank() && !filteredDoc.visibleHeadings().isEmpty());
          parent.getChildren().add(docItem);
          visibleDocNodes.put(canonicalPath(entry.file()), docItem);
          visibleDocCount++;
        }
      }
      root.getChildren().add(sectionItem);
      visibleSectionCount++;
    }

    docsTree.setRoot(root);

    TreeItem<HelpNode> selectedNode = selectedPath.isBlank() ? null : visibleDocNodes.get(selectedPath);
    if (selectedNode == null) selectedNode = firstDocNode(root);
    if (selectedNode != null) {
      expandAncestors(selectedNode);
      docsTree.getSelectionModel().select(selectedNode);
      docsTree.scrollTo(Math.max(0, docsTree.getRow(selectedNode) - 2));
      DocEntry entry = extractDocEntry(selectedNode);
      if (entry != null) showDoc(entry);
    } else if (allDocs.isEmpty()) {
      showGuidePlaceholder(
          "No docs indexed",
          "No Markdown files were found in the current workspace.",
          "The Help Center scans workspace docs, module READMEs, and current project docs.",
          "No documentation files are available to preview yet.");
    } else {
      showGuidePlaceholder(
          "No matching docs",
          "Adjust the filter or clear it to see the full guide tree.",
          "Every Markdown file is still indexed, but none match the current filter.",
          "No documentation files matched the current filter.");
    }

    if (filter.isBlank()) {
      statsLabel.setText("Showing " + visibleDocCount + " docs in " + visibleFolderCount
          + " topic folders across " + visibleSectionCount + " guide sections");
    } else {
      statsLabel.setText("Showing " + visibleDocCount + " matching docs across " + visibleSectionCount
          + " sections • Indexed " + allDocs.size() + " total");
    }
  }

  private void showDoc(DocEntry entry) {
    if (entry == null) {
      showGuidePlaceholder(
          "Help Center",
          "Select a document to preview.",
          "Follow the guide tree on the left to move from onboarding into deeper authoring and engine reference.",
          "Pick a document from the guide tree to preview it here.");
      return;
    }
    activeDocEntry = entry;
    sourceBadgeLabel.setText(entry.sourceLabel());
    titleLabel.setText(entry.title());
    GuideSection section = classifySection(entry);
    String breadcrumb = section == null ? entry.relativePath()
        : section.title() + "  \u203A  " + entry.relativePath();
    pathLabel.setText(breadcrumb);
    summaryLabel.setText(entry.summary());
    contentScroll.setVvalue(0.0);
    try {
      renderMarkdown(Files.readString(entry.file().toPath()));
    } catch (Exception ex) {
      renderError("Failed to read file:\n" + entry.file().getAbsolutePath() + "\n\n" + ex.getMessage());
    }
  }

  private void scrollToHeading(String headingText) {
    if (headingText == null || headingText.isBlank()) return;
    String targetId = "heading:" + headingText;
    javafx.application.Platform.runLater(() -> {
      for (javafx.scene.Node child : markdownContent.getChildren()) {
        if (targetId.equals(child.getId())) {
          markdownContent.layout();
          double contentHeight = markdownContent.getBoundsInLocal().getHeight();
          double viewportHeight = contentScroll.getViewportBounds() != null
              ? contentScroll.getViewportBounds().getHeight() : 1.0;
          double scrollableRange = Math.max(1.0, contentHeight - viewportHeight);
          double nodeY = child.getBoundsInParent().getMinY();
          double targetVvalue = Math.min(1.0, Math.max(0.0, nodeY / scrollableRange));
          contentScroll.setVvalue(targetVvalue);
          break;
        }
      }
    });
  }

  private void openSelectedDocInEditor() {
    DocEntry entry = selectedDocEntry();
    if (entry == null || onOpenDoc == null) return;
    onOpenDoc.accept(entry.file());
  }

  private void revealSelectedDoc() {
    DocEntry entry = selectedDocEntry();
    if (entry == null) return;
    try {
      if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(entry.file().getParentFile());
      }
    } catch (Exception ex) {
      statsLabel.setText("Cannot reveal file: " + ex.getMessage());
    }
  }

  private void copySelectedDocPath() {
    DocEntry entry = selectedDocEntry();
    if (entry == null) return;
    ClipboardContent content = new ClipboardContent();
    content.putString(entry.file().getAbsolutePath());
    Clipboard.getSystemClipboard().setContent(content);
    statsLabel.setText("Copied path: " + entry.relativePath());
  }

  private DocEntry selectedDocEntry() {
    return extractDocEntry(docsTree.getSelectionModel().getSelectedItem());
  }

  private DocEntry extractDocEntry(TreeItem<HelpNode> item) {
    if (item == null || item.getValue() == null || item.getValue().doc() == null) return null;
    return item.getValue().doc();
  }

  private Region spacer() {
    Region r = new Region();
    HBox.setHgrow(r, Priority.ALWAYS);
    return r;
  }

  private void setAllSectionsExpanded(boolean expanded) {
    TreeItem<HelpNode> root = docsTree.getRoot();
    if (root == null) return;
    root.setExpanded(true);
    for (TreeItem<HelpNode> child : root.getChildren()) {
      setExpandedRecursive(child, expanded);
    }
  }

  private void setExpandedRecursive(TreeItem<HelpNode> item, boolean expanded) {
    if (item == null) return;
    item.setExpanded(expanded);
    for (TreeItem<HelpNode> child : item.getChildren()) {
      setExpandedRecursive(child, expanded);
    }
  }

  private void installSmoothScroll(ScrollPane pane) {
    pane.addEventFilter(ScrollEvent.SCROLL, ev -> {
      if (ev.getDeltaY() == 0) return;
      double contentHeight = pane.getContent() == null
          ? 0
          : pane.getContent().getBoundsInLocal().getHeight();
      double viewportHeight = pane.getViewportBounds() == null
          ? 1
          : pane.getViewportBounds().getHeight();
      double scrollableRange = Math.max(1.0, contentHeight - viewportHeight);
      double pixelDelta = ev.getDeltaY() * (ev.isShiftDown() ? 0.3 : 1.4);
      double valueDelta = pixelDelta / scrollableRange;
      double newValue = pane.getVvalue() - valueDelta;
      pane.setVvalue(Math.max(pane.getVmin(), Math.min(pane.getVmax(), newValue)));
      ev.consume();
    });
  }

  private Tooltip buildTooltip(String header, String body) {
    StringBuilder sb = new StringBuilder();
    if (header != null && !header.isBlank()) sb.append(header);
    if (body != null && !body.isBlank()) {
      if (sb.length() > 0) sb.append("\n\n");
      sb.append(body);
    }
    Tooltip tip = new Tooltip(sb.toString());
    tip.setWrapText(true);
    tip.setMaxWidth(360);
    tip.setShowDelay(javafx.util.Duration.millis(450));
    tip.setHideDelay(javafx.util.Duration.millis(120));
    tip.getStyleClass().add("help-tooltip");
    return tip;
  }

  private void showGuidePlaceholder(String title, String path, String summary, String bodyMessage) {
    activeDocEntry = null;
    sourceBadgeLabel.setText("Guide");
    titleLabel.setText(title);
    pathLabel.setText(path);
    summaryLabel.setText(summary);
    markdownContent.getChildren().clear();
    addEmptyState(bodyMessage);
  }

  private boolean shouldSkipMarkdownPath(Path base, Path path) {
    Path normalizedBase = base.toAbsolutePath().normalize();
    Path normalizedPath = path.toAbsolutePath().normalize();
    if (!normalizedPath.startsWith(normalizedBase)) return false;
    Path relative = normalizedBase.relativize(normalizedPath);
    for (Path part : relative) {
      String name = part.toString();
      if (name.equals(".git")
          || name.equals(".gradle")
          || name.equals(".idea")
          || name.equals(".jvn-gradle-user-home")
          || name.equals("build")
          || name.equals("out")
          || name.equals("target")) {
        return true;
      }
    }
    return false;
  }

  private FilteredDocEntry filterDoc(DocEntry entry, String filter) {
    if (entry == null) return null;
    if (filter == null || filter.isBlank()) {
      return new FilteredDocEntry(entry, entry.headings());
    }
    boolean docMatches = matchesDocFields(entry, filter);
    if (docMatches) {
      return new FilteredDocEntry(entry, entry.headings());
    }
    List<HeadingInfo> visibleHeadings = entry.headings().stream()
        .filter(h -> h.text().toLowerCase(Locale.ROOT).contains(filter))
        .toList();
    return visibleHeadings.isEmpty() ? null : new FilteredDocEntry(entry, visibleHeadings);
  }

  private boolean matchesDocFields(DocEntry entry, String filter) {
    if (filter == null || filter.isBlank()) return true;
    return entry.title().toLowerCase(Locale.ROOT).contains(filter)
        || entry.relativePath().toLowerCase(Locale.ROOT).contains(filter)
        || entry.sourceLabel().toLowerCase(Locale.ROOT).contains(filter)
        || entry.summary().toLowerCase(Locale.ROOT).contains(filter);
  }

  private GuideSection classifySection(DocEntry entry) {
    String path = normalizePath(entry.relativePath()).toLowerCase(Locale.ROOT);
    File ws = workspaceRoot != null ? workspaceRoot : detectWorkspaceRoot();
    File pr = normalizeDir(projectRoot);
    if (!entry.isWorkspaceDoc()) return SECTION_PROJECT;
    if (pr != null && !isSameDirectory(pr, ws) && isUnderDirectory(entry.file(), pr)) return SECTION_PROJECT;
    return classifyWorkspaceSection(path);
  }

  static String guideSectionKeyForWorkspacePath(String rawRelativePath) {
    return classifyWorkspaceSection(normalizeStaticPath(rawRelativePath)).key();
  }

  private static GuideSection classifyWorkspaceSection(String path) {
    if (path.equals("readme.md")
        || path.equals("docs/index.md")
        || path.equals("docs/readme.md")
        || path.equals("docs/guides/choose-your-path.md")
        || path.equals("docs/guides/getting-started.md")
        || path.equals("docs/guides/common-file-types.md")
        || path.equals("scripting/readme.md")) {
      return SECTION_START;
    }
    if (path.startsWith("docs/guides/vns-by-example")
        || path.equals("docs/guides/vns-by-example.md")
        || path.startsWith("docs/scripting/vns/")) {
      return SECTION_VNS;
    }
    if (path.startsWith("docs/guides/jes-by-example")
        || path.equals("docs/guides/jes-by-example.md")
        || path.startsWith("docs/scripting/jes/")) {
      return SECTION_JES;
    }
    if (path.contains("generated-")
        || path.endsWith("changelog.md")
        || path.endsWith("contributing.md")
        || path.equals("license.md")) {
      return SECTION_REFERENCE;
    }
    if (path.startsWith("docs/editor/puppeteer/") || path.startsWith("docs/scripting/timeline/")) return SECTION_PUPPETEER;
    if (path.startsWith("docs/scripting/ui/")) return SECTION_UI;
    if (path.startsWith("docs/editor/") || path.startsWith("editor/")) return SECTION_EDITOR;
    if (path.startsWith("docs/runtime/") || path.startsWith("docs/project-setup/") || path.startsWith("runtime/")) {
      return SECTION_RUNTIME;
    }
    if (path.startsWith("docs/architecture/")
        || path.startsWith("core/")
        || path.startsWith("fx/")
        || path.startsWith("audio/")) {
      return SECTION_ARCHITECTURE;
    }
    if (path.startsWith("docs/guides/")) return SECTION_GUIDES;
    return SECTION_REFERENCE;
  }

  private GuideBucket classifyBucket(GuideSection section, DocEntry entry) {
    return guideBucketForPath(section.key(), entry.relativePath());
  }

  static GuideBucket guideBucketForPath(String sectionKey, String rawRelativePath) {
    String path = normalizeStaticPath(rawRelativePath);
    String key = sectionKey == null ? "" : sectionKey.trim().toLowerCase(Locale.ROOT);
    return switch (key) {
      case "start" -> startBucket(path);
      case "vns" -> vnsBucket(path);
      case "jes" -> jesBucket(path);
      case "puppeteer" -> puppeteerBucket(path);
      case "ui" -> uiBucket(path);
      case "editor" -> editorBucket(path);
      case "runtime" -> runtimeBucket(path);
      case "architecture" -> architectureBucket(path);
      case "guides" -> guidesBucket(path);
      case "project" -> projectBucket(path);
      default -> referenceBucket(path);
    };
  }

  private static GuideBucket startBucket(String path) {
    if (path.equals("readme.md") || path.equals("docs/index.md") || path.equals("docs/readme.md")) {
      return bucket("overview", "Overview And Indexes", "Primary entry points and docs maps", 0);
    }
    if (path.contains("getting-started") || path.contains("choose-your-path") || path.contains("common-file-types")) {
      return bucket("first-project", "First Project Path", "Setup, file orientation, and first-time choices", 10);
    }
    return bucket("start-more", "More Starting Points", "Other onboarding docs", 20);
  }

  private static GuideBucket vnsBucket(String path) {
    if (path.contains("vns-by-example")) return bucket("examples", "VNS By Example", "Progressive story scripting lessons", 0);
    if (path.contains("/overview/")) return bucket("overview", "VNS Overview", "Concepts and quick reference", 10);
    if (path.contains("/language/")) return bucket("language", "Language Reference", "Directives, dialogue, choices, and variables", 20);
    if (path.contains("/presentation/")) return bucket("presentation", "Presentation", "Characters, audio, transitions, and text effects", 30);
    if (path.contains("/flow/")) return bucket("flow", "Flow Control", "Labels, jumps, calls, returns, and branching", 40);
    if (path.contains("/runtime/")) return bucket("runtime", "Runtime Systems", "Save, rollback, localization, settings, and scene lifecycle", 50);
    if (path.contains("/integration/")) return bucket("integration", "Integration", "VNS, JES, and Java bridge workflows", 60);
    if (path.contains("/internals/")) return bucket("internals", "Parser Internals", "Parser and implementation notes", 70);
    return bucket("vns-more", "Other VNS Docs", "Additional VNS material", 90);
  }

  private static GuideBucket jesBucket(String path) {
    if (path.contains("jes-by-example")) return bucket("examples", "JES By Example", "Progressive gameplay scripting lessons", 0);
    if (path.contains("/overview/")) return bucket("overview", "JES Overview", "Concepts and quick reference", 10);
    if (path.contains("/scene/")) return bucket("scene", "Scenes And Components", "Entity structure and component properties", 20);
    if (path.contains("/timeline/")) return bucket("timeline", "Timeline DSL", "Animation actions, cues, easing, and properties", 30);
    if (path.contains("/systems/")) return bucket("systems", "Engine Systems", "Input, camera, physics, and tilemaps", 40);
    if (path.contains("/gameplay/")) return bucket("gameplay", "Gameplay Modules", "AI, RPG, and UI widget helpers", 50);
    if (path.contains("/integration/")) return bucket("integration", "Integration", "Bridge and Java hook workflows", 60);
    if (path.contains("/internals/")) return bucket("internals", "Parser Internals", "Tokenizer, parser, AST, and validation notes", 70);
    return bucket("jes-more", "Other JES Docs", "Additional JES material", 90);
  }

  private static GuideBucket puppeteerBucket(String path) {
    if (path.startsWith("docs/editor/puppeteer/")) {
      if (path.endsWith("puppeteer-audit.md")) {
        return bucket("roadmap", "Roadmap And Audit", "Hardening notes and future work", 70);
      }
      return bucket("editor", "Puppeteer Editor", "Visual timeline authoring and exported DSL", 0);
    }
    if (path.contains("/overview/")) return bucket("overview", "Timeline Overview", "Timeline scripting concepts", 10);
    if (path.contains("/animation/")) return bucket("animation", "Timeline Animation", "Runtime animation data, keyframes, and hand coding", 20);
    if (path.contains("/story/")) return bucket("story", "Story Arcs", "Timeline story graph and arc validation", 30);
    return bucket("timeline-more", "Other Timeline Docs", "Additional animation and timeline material", 90);
  }

  private static GuideBucket uiBucket(String path) {
    if (path.contains("/menus/")) return bucket("menus", "Menu Profiles And Screens", "Menu files, actions, screens, and styles", 0);
    if (path.contains("/workflow/")) return bucket("workflow", "Text-First Workflow", "Authoring loops and validation workflow", 10);
    if (path.contains("/structure/")) return bucket("structure", "Layout Structure", "Layouts, registry, inheritance, and button placement", 20);
    if (path.contains("/components/")) return bucket("components", "UI Components", "Dialogue, choices, textbox controls, and character framing", 30);
    if (path.contains("/styling/")) return bucket("styling", "Styling", "Colors, fonts, assets, and themes", 40);
    if (path.contains("/screens/")) return bucket("screens", "Built-In Screens", "Save, settings, help, and screen patterns", 50);
    if (path.contains("/tooling/")) return bucket("tooling", "Tooling And Diagnostics", "Visual tools, scenarios, and validation", 60);
    if (path.contains("/reference/")) return bucket("reference", "Reference", "DSL cookbooks and syntax reference", 70);
    return bucket("ui-more", "Other UI Docs", "Additional UI material", 90);
  }

  private static GuideBucket editorBucket(String path) {
    if (path.startsWith("docs/editor/core/")) return bucket("core", "Core Editor", "Workspace, hub, settings, console, and core windows", 0);
    if (path.startsWith("docs/editor/sidebars/overview/")) return bucket("sidebars-overview", "Sidebar Overview", "Chooser and panel map", 10);
    if (path.startsWith("docs/editor/sidebars/left/")) return bucket("left-sidebars", "Left Sidebar Tools", "Project and story navigation panels", 20);
    if (path.startsWith("docs/editor/sidebars/right/")) return bucket("right-sidebars", "Right Sidebar Tools", "Inspector, help, assets, diagnostics, and utilities", 30);
    if (path.startsWith("docs/editor/tools/")) return bucket("tools", "Editor File Tools", "Tool-specific file format docs", 40);
    return bucket("editor-more", "Other Editor Docs", "Additional editor material", 90);
  }

  private static GuideBucket runtimeBucket(String path) {
    if (path.startsWith("docs/runtime/core/")) return bucket("runtime-core", "Runtime Core", "Runtime entry points and interop", 0);
    if (path.startsWith("docs/runtime/systems/")) return bucket("runtime-systems", "Runtime Systems", "Assets, audio, display, save, and VN settings", 10);
    if (path.startsWith("docs/project-setup/onboarding/")) return bucket("onboarding", "Project Onboarding", "New project setup and structure", 20);
    if (path.startsWith("docs/project-setup/content/")) return bucket("content", "Project Content", "Title screen, localization, and text effects", 30);
    if (path.startsWith("docs/project-setup/collaboration/")) return bucket("collaboration", "Collaboration", "Version-control workflows", 40);
    if (path.startsWith("docs/project-setup/release/")) return bucket("release", "Build And Release", "Packaging, deployment, and release tasks", 50);
    return bucket("runtime-more", "Other Runtime Docs", "Additional runtime and setup material", 90);
  }

  private static GuideBucket architectureBucket(String path) {
    if (path.startsWith("docs/architecture/core/")) return bucket("core", "Core Architecture", "Engine architecture and 2D systems", 0);
    if (path.startsWith("docs/architecture/quality/")) return bucket("quality", "Quality And Debugging", "Performance, debugging, and quality notes", 10);
    return bucket("modules", "Module Docs", "Module-local architecture notes", 50);
  }

  private static GuideBucket guidesBucket(String path) {
    if (path.contains("cookbook")) return bucket("cookbooks", "Cookbooks", "Task-based recipes", 0);
    if (path.contains("integration")) return bucket("integration", "Integration Guides", "Cross-system recipes", 10);
    return bucket("general", "General Guides", "Project and workflow guides", 20);
  }

  private static GuideBucket projectBucket(String path) {
    int slash = path.indexOf('/');
    String top = slash > 0 ? path.substring(0, slash) : "root";
    String title = top.equals("root") ? "Project Root" : toTitle(top);
    return bucket("project-" + top, title, "Project-local documentation", 0);
  }

  private static GuideBucket referenceBucket(String path) {
    if (path.contains("generated-")) return bucket("generated", "Generated References", "Generated screenshots and output docs", 0);
    if (path.endsWith("-audit.md")) return bucket("audits", "Audits And Roadmaps", "Audit notes and roadmap material", 10);
    return bucket("reference", "Other Reference", "Low-frequency reference material", 90);
  }

  private static GuideBucket bucket(String key, String title, String subtitle, int rank) {
    return new GuideBucket(key, title, subtitle, rank);
  }

  private boolean shouldShowBuckets(
      GuideSection section,
      List<Map.Entry<GuideBucket, List<FilteredDocEntry>>> bucketEntries
  ) {
    if (section == SECTION_PROJECT) return true;
    if (bucketEntries.size() > 1) return true;
    return !bucketEntries.isEmpty() && bucketEntries.get(0).getValue().size() > 6;
  }

  private boolean shouldExpandSection(GuideSection section, String filter) {
    if (filter != null && !filter.isBlank()) return true;
    return section == SECTION_START
        || section == SECTION_VNS
        || section == SECTION_JES
        || section == SECTION_PUPPETEER
        || section == SECTION_EDITOR;
  }

  private boolean shouldExpandBucket(GuideBucket bucket, String filter) {
    if (filter != null && !filter.isBlank()) return true;
    return bucket != null && bucket.rank() <= 20;
  }

  private int compareDocs(DocEntry left, DocEntry right) {
    int rankCompare = Integer.compare(docRank(left), docRank(right));
    if (rankCompare != 0) return rankCompare;
    int pathCompare = left.relativePath().compareToIgnoreCase(right.relativePath());
    if (pathCompare != 0) return pathCompare;
    return left.title().compareToIgnoreCase(right.title());
  }

  private int docRank(DocEntry entry) {
    String path = normalizePath(entry.relativePath()).toLowerCase(Locale.ROOT);
    if (path.equals("docs/index.md")) return 0;
    if (path.equals("docs/readme.md")) return 1;
    if (path.equals("docs/guides/choose-your-path.md")) return 2;
    if (path.equals("docs/guides/getting-started.md")) return 3;
    if (path.equals("docs/guides/common-file-types.md")) return 4;
    if (path.equals("readme.md")) return 5;
    if (path.contains("/overview/")) return 10;
    if (path.contains("/onboarding/")) return 15;
    if (path.contains("/workflow/")) return 20;
    if (path.contains("/guides/")) return 25;
    if (path.contains("/language/")) return 30;
    if (path.contains("/flow/")) return 35;
    if (path.contains("/presentation/")) return 40;
    if (path.contains("/runtime/")) return 45;
    if (path.contains("/integration/")) return 50;
    if (path.contains("/systems/")) return 55;
    if (path.contains("/scene/")) return 60;
    if (path.contains("/components/")) return 65;
    if (path.contains("/tooling/")) return 70;
    if (path.contains("/structure/")) return 75;
    if (path.contains("/styling/")) return 80;
    if (path.contains("/screens/")) return 82;
    if (path.contains("/reference/")) return 85;
    if (path.contains("/internals/")) return 90;
    if (path.contains("generated-") || path.endsWith("-audit.md")) return 95;
    return 88;
  }

  private TreeItem<HelpNode> firstDocNode(TreeItem<HelpNode> root) {
    if (root == null) return null;
    if (root.getValue() != null && root.getValue().isDoc()) {
      return root;
    }
    for (TreeItem<HelpNode> child : root.getChildren()) {
      TreeItem<HelpNode> found = firstDocNode(child);
      if (found != null) return found;
    }
    return null;
  }

  private void expandAncestors(TreeItem<HelpNode> node) {
    TreeItem<HelpNode> current = node;
    while (current != null) {
      current.setExpanded(true);
      current = current.getParent();
    }
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
    TextFlow flow = createInlineFlow(text, style, 8);
    flow.setId("heading:" + text);
    markdownContent.getChildren().add(flow);
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
    imageView.fitWidthProperty().bind(contentScroll.viewportBoundsProperty()
        .map(b -> Math.max(120.0, b.getWidth() - 56)));

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
    // Bind to viewport width (not the VBox width) to avoid layout feedback loops
    // when the scrollbar appears/disappears, which was causing scroll jitter.
    flow.maxWidthProperty().bind(contentScroll.viewportBoundsProperty()
        .map(b -> Math.max(80.0, b.getWidth() - widthInset)));
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
            // reason: non-critical operation; exception swallowed to prevent crash propagation
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
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
      return;
    }
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
            // reason: non-critical operation; exception swallowed to prevent crash propagation
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
        TreeItem<HelpNode> node = visibleDocNodes.get(wanted);
        if (node == null) {
          if (!filterField.getText().isBlank()) {
            filterField.clear();
          }
          rebuildGuideTree(filterField.getText());
          node = visibleDocNodes.get(wanted);
        }
        if (node != null) {
          expandAncestors(node);
          docsTree.getSelectionModel().select(node);
          docsTree.scrollTo(Math.max(0, docsTree.getRow(node) - 2));
        }
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
            // reason: non-critical operation; exception swallowed to prevent crash propagation
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
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return fallback;
  }

  private record HeadingInfo(int level, String text) {}

  private List<HeadingInfo> readHeadings(File file) {
    List<HeadingInfo> results = new ArrayList<>();
    try {
      List<String> lines = Files.readAllLines(file.toPath());
      boolean inFence = false;
      for (String raw : lines) {
        String line = raw == null ? "" : raw;
        String trimmed = line.trim();
        if (trimmed.startsWith("```")) {
          inFence = !inFence;
          continue;
        }
        if (inFence) continue;
        Matcher m = HEADING_LINE.matcher(trimmed);
        if (m.matches()) {
          int level = m.group(1).length();
          if (level >= 2 && level <= 4) {
            String text = m.group(2).trim();
            if (!text.isBlank()) {
              results.add(new HeadingInfo(level, text));
            }
          }
        }
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return results;
  }

  private String readSummary(File file) {
    try {
      List<String> lines = Files.readAllLines(file.toPath());
      StringBuilder paragraph = new StringBuilder();
      boolean inFence = false;
      int limit = Math.min(lines.size(), SUMMARY_SCAN_LINE_LIMIT);
      for (int i = 0; i < limit; i++) {
        String raw = lines.get(i);
        String line = raw == null ? "" : raw.trim();
        if (line.startsWith("```")) {
          inFence = !inFence;
          continue;
        }
        if (inFence) continue;
        if (line.isBlank()) {
          if (paragraph.length() > 0) break;
          continue;
        }
        if (line.startsWith("#")
            || line.startsWith(">")
            || HORIZONTAL_RULE_LINE.matcher(line).matches()
            || isListLine(line)
            || TABLE_SEPARATOR_LINE.matcher(line).matches()
            || line.startsWith("|")
            || parseStandaloneImage(line) != null) {
          if (paragraph.length() > 0) break;
          continue;
        }
        if (paragraph.length() > 0) paragraph.append(' ');
        paragraph.append(line);
        if (paragraph.length() >= 220) break;
      }
      String summary = paragraph.toString().trim();
      if (!summary.isBlank()) return compactSummary(summary, 190);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return "No summary available yet.";
  }

  private String compactSummary(String text, int maxLength) {
    if (text == null) return "";
    String normalized = text.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= maxLength) return normalized;
    return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
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

  private boolean isUnderDirectory(File file, File directory) {
    if (file == null || directory == null) return false;
    String filePath = canonicalPath(file);
    String dirPath = canonicalPath(directory);
    return filePath.equals(dirPath) || filePath.startsWith(dirPath + File.separator);
  }

  private String normalizePath(String path) {
    if (path == null) return "";
    return path.replace('\\', '/');
  }

  private static String normalizeStaticPath(String path) {
    if (path == null) return "";
    return path.replace('\\', '/').trim().toLowerCase(Locale.ROOT);
  }

  private static String toTitle(String raw) {
    if (raw == null || raw.isBlank()) return "Docs";
    String[] parts = raw.replace('-', ' ').replace('_', ' ').split("\\s+");
    StringBuilder title = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) continue;
      if (title.length() > 0) title.append(' ');
      title.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) title.append(part.substring(1));
    }
    return title.length() == 0 ? "Docs" : title.toString();
  }

  private record DocEntry(
      File file,
      String sourceLabel,
      String relativePath,
      String title,
      String summary,
      boolean isWorkspaceDoc,
      List<HeadingInfo> headings
  ) {}

  record GuideSection(String key, String title, String subtitle) {}

  record GuideBucket(String key, String title, String subtitle, int rank) {}

  private record FilteredDocEntry(DocEntry entry, List<HeadingInfo> visibleHeadings) {}

  private enum HelpNodeKind {
    SECTION,
    GROUP,
    DOC,
    HEADING
  }

  private record HelpNode(
      String title,
      String subtitle,
      DocEntry doc,
      HelpNodeKind kind,
      int documentCount,
      int headingLevel
  ) {
    private static HelpNode section(GuideSection section, int documentCount) {
      return new HelpNode(section.title(), section.subtitle(), null, HelpNodeKind.SECTION, documentCount, 0);
    }

    private static HelpNode group(GuideBucket bucket, int documentCount) {
      return new HelpNode(bucket.title(), bucket.subtitle(), null, HelpNodeKind.GROUP, documentCount, 0);
    }

    private static HelpNode doc(DocEntry entry) {
      return new HelpNode(entry.title(), entry.summary(), entry, HelpNodeKind.DOC, 0, 0);
    }

    private static HelpNode heading(DocEntry parentDoc, int level, String text) {
      return new HelpNode(text, null, parentDoc, HelpNodeKind.HEADING, 0, level);
    }

    boolean section() {
      return kind == HelpNodeKind.SECTION;
    }

    boolean group() {
      return kind == HelpNodeKind.GROUP;
    }

    boolean isDoc() {
      return kind == HelpNodeKind.DOC;
    }

    boolean isHeading() {
      return kind == HelpNodeKind.HEADING;
    }
  }

  private record MarkdownImage(String altText, String target) {}
}
