package com.jvn.editor.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Side panel showing live diagnostics for the active .vns file. */
public class VnsDiagnosticsView extends BorderPane {
  private static final String SEVERITY_ALL = "All";
  private static final String SEVERITY_ERRORS = "Errors";
  private static final String SEVERITY_WARNINGS = "Warnings";

  private final Label titleLabel = new Label("VNS Diagnostics");
  private final Label fileLabel = new Label("No active .vns file");
  private final Label summaryLabel = new Label("Open a .vns script to see diagnostics.");
  private final Label statsLabel = new Label("");
  private final Label errorCountLabel = new Label("0 Errors");
  private final Label warningCountLabel = new Label("0 Warnings");
  private final Label filteredCountLabel = new Label("No script");
  private final Label placeholderLabel = new Label("Open a .vns script to see diagnostics.");
  private final TextField filterField = new TextField();
  private final ComboBox<String> severityFilter = new ComboBox<>(
      FXCollections.observableArrayList(SEVERITY_ALL, SEVERITY_ERRORS, SEVERITY_WARNINGS)
  );
  private final Button openSelectedButton = actionButton("Open", CssIcon.expand("#7ec8e3"));
  private final Button copyDiagnosticsButton = actionButton("Copy", CssIcon.copy("#a8d0f0"));
  private final Button clearFilterButton = actionButton("Clear Filter", CssIcon.clearX("#f0a080"));
  private final ListView<DiagnosticRow> listView = new ListView<>();

  private final List<DiagnosticRow> allRows = new ArrayList<>();
  private boolean hasActiveScript;
  private Consumer<Integer> onOpenLine;
  private Consumer<OpenTarget> onOpenTarget;

  public VnsDiagnosticsView() {
    getStyleClass().add("vns-diagnostics-root");
    titleLabel.getStyleClass().add("vns-diagnostics-title");
    fileLabel.getStyleClass().add("vns-diagnostics-file");
    summaryLabel.getStyleClass().add("vns-diagnostics-summary");
    summaryLabel.setWrapText(true);
    statsLabel.getStyleClass().add("vns-diagnostics-stats");
    statsLabel.setWrapText(true);
    filteredCountLabel.getStyleClass().add("vns-diagnostics-filter-count");
    errorCountLabel.getStyleClass().addAll("vns-diagnostics-chip", "vns-diagnostics-chip-error");
    warningCountLabel.getStyleClass().addAll("vns-diagnostics-chip", "vns-diagnostics-chip-warning");

    filterField.setPromptText("Filter diagnostics...");
    filterField.getStyleClass().add("vns-diagnostics-filter");
    filterField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());
    filterField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ESCAPE) {
        clearFilters();
        e.consume();
      }
    });

    severityFilter.setValue(SEVERITY_ALL);
    severityFilter.setFocusTraversable(false);
    severityFilter.setPrefWidth(96);
    severityFilter.getStyleClass().add("vns-diagnostics-severity-filter");
    severityFilter.setButtonCell(new javafx.scene.control.ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? "" : item);
      }
    });
    severityFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> applyFilter());

    placeholderLabel.getStyleClass().add("vns-diagnostics-placeholder");
    placeholderLabel.setWrapText(true);
    listView.setPlaceholder(placeholderLabel);
    listView.getStyleClass().add("vns-diagnostics-list");
    listView.setCellFactory(lv -> new DiagnosticCell(lv));
    listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateActionState());
    listView.setOnMouseClicked(e -> {
      if (e.getClickCount() < 2) return;
      openSelectedRow();
    });
    listView.setOnKeyPressed(e -> {
      if (e.getCode() != KeyCode.ENTER) return;
      openSelectedRow();
    });

    openSelectedButton.setOnAction(e -> openSelectedRow());
    copyDiagnosticsButton.setOnAction(e -> copyVisibleDiagnostics());
    clearFilterButton.setOnAction(e -> clearFilters());

    HBox titleRow = new HBox(8, CssIcon.list("#d0d0d0"), titleLabel);
    titleRow.setAlignment(Pos.CENTER_LEFT);
    titleRow.getStyleClass().add("vns-diagnostics-title-row");

    HBox countsRow = new HBox(8, errorCountLabel, warningCountLabel);
    countsRow.setAlignment(Pos.CENTER_LEFT);

    VBox header = new VBox(6, titleRow, fileLabel, summaryLabel, statsLabel, countsRow);
    header.setPadding(new Insets(10, 10, 6, 10));
    header.getStyleClass().add("vns-diagnostics-header");

    HBox filterRow = new HBox(8, filterField, severityFilter);
    filterRow.setPadding(new Insets(0, 10, 8, 10));
    HBox.setHgrow(filterField, Priority.ALWAYS);
    filterRow.getStyleClass().add("vns-diagnostics-filter-row");

    HBox actionRow = new HBox(8, openSelectedButton, copyDiagnosticsButton, clearFilterButton, filteredCountLabel);
    actionRow.setAlignment(Pos.CENTER_LEFT);
    actionRow.setPadding(new Insets(0, 10, 8, 10));
    actionRow.getStyleClass().add("vns-diagnostics-action-row");

    setTop(new VBox(header, filterRow, actionRow));
    setCenter(listView);
    setPadding(new Insets(0));
    updateActionState();
  }

  public void setOnOpenLine(Consumer<Integer> onOpenLine) {
    this.onOpenLine = onOpenLine;
  }

  public void setOnOpenTarget(Consumer<OpenTarget> onOpenTarget) {
    this.onOpenTarget = onOpenTarget;
  }

  public void clear() {
    hasActiveScript = false;
    fileLabel.setText("No active .vns file");
    summaryLabel.setText("Open a .vns script to see diagnostics.");
    statsLabel.setText("");
    errorCountLabel.setText("0 Errors");
    warningCountLabel.setText("0 Warnings");
    allRows.clear();
    listView.getItems().clear();
    updatePlaceholder(0);
    updateActionState();
  }

  public void setAnalysis(File scriptFile, VnsScriptAnalyzer.Analysis analysis) {
    if (scriptFile == null || analysis == null) {
      clear();
      return;
    }

    hasActiveScript = true;
    fileLabel.setText(scriptFile.getName());
    fileLabel.setTooltip(new javafx.scene.control.Tooltip(scriptFile.getAbsolutePath()));
    statsLabel.setText(buildStatsSummary(analysis.stats()));
    allRows.clear();

    int errors = 0;
    int warnings = 0;
    for (VnsScriptAnalyzer.Diagnostic issue : analysis.diagnostics()) {
      allRows.add(buildRow(analysis.source(), issue));
      if (issue.warning()) warnings++; else errors++;
    }
    errorCountLabel.setText(errors + (errors == 1 ? " Error" : " Errors"));
    warningCountLabel.setText(warnings + (warnings == 1 ? " Warning" : " Warnings"));

    if (errors == 0 && warnings == 0) {
      summaryLabel.setText("No issues found.");
    } else if (errors > 0 && warnings > 0) {
      summaryLabel.setText(errors + " errors, " + warnings + " warnings");
    } else if (errors > 0) {
      summaryLabel.setText(errors + (errors == 1 ? " error" : " errors"));
    } else {
      summaryLabel.setText(warnings + (warnings == 1 ? " warning" : " warnings"));
    }

    applyFilter();
  }

  private void applyFilter() {
    String query = filterField.getText();
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    DiagnosticRow previousSelection = listView.getSelectionModel().getSelectedItem();
    List<DiagnosticRow> filtered = new ArrayList<>();
    for (DiagnosticRow row : allRows) {
      if (!matchesSeverity(row)) continue;
      if (!normalized.isEmpty() && !row.searchText().contains(normalized)) continue;
      filtered.add(row);
    }
    listView.setItems(FXCollections.observableArrayList(filtered));
    if (previousSelection != null && filtered.contains(previousSelection)) {
      listView.getSelectionModel().select(previousSelection);
    } else if (!filtered.isEmpty()) {
      listView.getSelectionModel().selectFirst();
    }
    updatePlaceholder(filtered.size());
    updateActionState();
  }

  private void openSelectedRow() {
    DiagnosticRow row = listView.getSelectionModel().getSelectedItem();
    if (row == null) return;

    OpenTarget target = row.toOpenTarget();
    if (onOpenTarget != null) {
      onOpenTarget.accept(target);
      return;
    }
    if (onOpenLine != null) {
      onOpenLine.accept(target.oneBasedLine());
    }
  }

  private boolean matchesSeverity(DiagnosticRow row) {
    String selected = severityFilter.getValue();
    if (SEVERITY_ERRORS.equals(selected)) {
      return !row.issue().warning();
    }
    if (SEVERITY_WARNINGS.equals(selected)) {
      return row.issue().warning();
    }
    return true;
  }

  private void clearFilters() {
    boolean changed = filterField.getText() != null && !filterField.getText().isBlank();
    changed = changed || !SEVERITY_ALL.equals(severityFilter.getValue());
    filterField.clear();
    severityFilter.setValue(SEVERITY_ALL);
    if (!changed) applyFilter();
  }

  private void copyVisibleDiagnostics() {
    List<DiagnosticRow> rows = listView.getItems();
    if (rows == null || rows.isEmpty()) return;

    StringBuilder out = new StringBuilder();
    for (DiagnosticRow row : rows) {
      out.append(row.level())
          .append(" ")
          .append(row.locationText())
          .append(" ")
          .append(row.kindLabel())
          .append(": ")
          .append(row.issue().message());
      if (!row.sourceLine().isBlank()) {
        out.append(System.lineSeparator())
            .append("  ")
            .append(row.sourceLine());
      }
      out.append(System.lineSeparator());
    }

    ClipboardContent content = new ClipboardContent();
    content.putString(out.toString().strip());
    Clipboard.getSystemClipboard().setContent(content);
    summaryLabel.setText("Copied " + rows.size() + " visible diagnostic" + (rows.size() == 1 ? "." : "s."));
  }

  private void updatePlaceholder(int filteredCount) {
    if (!hasActiveScript) {
      placeholderLabel.setText("Open a .vns script to see diagnostics.");
      filteredCountLabel.setText("No script");
      return;
    }
    if (allRows.isEmpty()) {
      placeholderLabel.setText("No issues found for this script.");
      filteredCountLabel.setText("0 issues");
      return;
    }
    if (filteredCount == 0) {
      placeholderLabel.setText("No diagnostics match the current filter.");
      filteredCountLabel.setText("Showing 0 of " + allRows.size());
      return;
    }
    filteredCountLabel.setText("Showing " + filteredCount + " of " + allRows.size());
  }

  private void updateActionState() {
    boolean hasVisibleRows = !listView.getItems().isEmpty();
    boolean hasSelection = listView.getSelectionModel().getSelectedItem() != null;
    boolean hasFilter = (filterField.getText() != null && !filterField.getText().isBlank())
        || !SEVERITY_ALL.equals(severityFilter.getValue());
    openSelectedButton.setDisable(!hasSelection);
    copyDiagnosticsButton.setDisable(!hasVisibleRows);
    clearFilterButton.setDisable(!hasFilter);
  }

  private static DiagnosticRow buildRow(String source, VnsScriptAnalyzer.Diagnostic issue) {
    int line = Math.max(1, issue.line() + 1);
    int column = computeOneBasedColumn(source, issue.line(), issue.start());
    String level = issue.warning() ? "Warning" : "Error";
    String kindLabel = formatKind(issue.kind());
    String location = "L" + line + ":" + column;
    String display = level + "  " + location + "  " + kindLabel + "  " + issue.message();
    SourcePreview preview = buildSourcePreview(source, issue.line(), column);
    String searchText = buildSearchText(issue, line, column, level, kindLabel, preview.sourceLine());
    return new DiagnosticRow(issue, line, column, level, kindLabel, display, searchText, preview.sourceLine(), preview.caretLine());
  }

  static int computeOneBasedColumn(String source, int zeroBasedLine, int offset) {
    String text = source == null ? "" : source;
    int clampedOffset = Math.max(0, Math.min(offset, text.length()));
    int lineStart = lineStartOffset(text, zeroBasedLine);
    return Math.max(1, clampedOffset - lineStart + 1);
  }

  static int lineStartOffset(String source, int zeroBasedLine) {
    if (source == null || source.isEmpty() || zeroBasedLine <= 0) return 0;
    int targetLine = zeroBasedLine;
    int currentLine = 0;
    for (int i = 0; i < source.length(); i++) {
      if (source.charAt(i) != '\n') continue;
      currentLine++;
      if (currentLine == targetLine) {
        return i + 1;
      }
    }
    return source.length();
  }

  static String buildSearchText(VnsScriptAnalyzer.Diagnostic issue,
                                int oneBasedLine,
                                int oneBasedColumn,
                                String level,
                                String kindLabel) {
    return buildSearchText(issue, oneBasedLine, oneBasedColumn, level, kindLabel, "");
  }

  static String buildSearchText(VnsScriptAnalyzer.Diagnostic issue,
                                int oneBasedLine,
                                int oneBasedColumn,
                                String level,
                                String kindLabel,
                                String sourceLine) {
    return (
        level + " " +
        issue.kind() + " " +
        kindLabel + " " +
        issue.message() + " " +
        sourceLine + " " +
        "line " + oneBasedLine + " column " + oneBasedColumn + " " +
        "l" + oneBasedLine + ":" + oneBasedColumn
    ).toLowerCase(Locale.ROOT);
  }

  static SourcePreview buildSourcePreview(String source, int zeroBasedLine, int oneBasedColumn) {
    String line = sourceLine(source, zeroBasedLine).replace('\t', ' ');
    if (line.isBlank()) {
      return new SourcePreview("(blank line)", "^");
    }

    int caretIndex = Math.max(0, oneBasedColumn - 1);
    int maxWidth = 140;
    if (line.length() <= maxWidth) {
      return new SourcePreview(line, " ".repeat(Math.min(caretIndex, line.length())) + "^");
    }

    int windowStart = Math.max(0, caretIndex - 56);
    windowStart = Math.min(windowStart, Math.max(0, line.length() - maxWidth));
    int windowEnd = Math.min(line.length(), windowStart + maxWidth);
    String prefix = windowStart > 0 ? "..." : "";
    String suffix = windowEnd < line.length() ? "..." : "";
    String visible = prefix + line.substring(windowStart, windowEnd) + suffix;
    int visibleCaret = Math.max(0, caretIndex - windowStart + prefix.length());
    return new SourcePreview(visible, " ".repeat(Math.min(visibleCaret, visible.length())) + "^");
  }

  static String sourceLine(String source, int zeroBasedLine) {
    if (source == null || source.isEmpty() || zeroBasedLine < 0) return "";
    int start = lineStartOffset(source, zeroBasedLine);
    if (start >= source.length()) return "";
    int end = source.indexOf('\n', start);
    if (end < 0) end = source.length();
    if (end > start && source.charAt(end - 1) == '\r') end--;
    return source.substring(start, end);
  }

  private static String formatKind(String kind) {
    if (kind == null || kind.isBlank()) return "Issue";
    String normalized = kind.replace('_', ' ').trim();
    if (normalized.isEmpty()) return "Issue";
    return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
  }

  private static String buildStatsSummary(VnsScriptAnalyzer.ScriptStats stats) {
    if (stats == null) return "";
    String minutes = stats.estimatedPlaytimeMinutes() <= 0
        ? "<0.1 min"
        : String.format(Locale.ROOT, "%.1f min", stats.estimatedPlaytimeMinutes());
    return stats.wordCount() + " words"
        + " | " + stats.dialogueLineCount() + " dialogue"
        + " | " + stats.choiceBranchCount() + " choices"
        + " | " + stats.labelCount() + " labels"
        + " | ~" + minutes;
  }

  private static Button actionButton(String text, javafx.scene.layout.Region icon) {
    Button button = new Button(text);
    button.getStyleClass().add("vns-diagnostics-action-button");
    button.setGraphic(icon);
    button.setGraphicTextGap(6);
    button.setMnemonicParsing(false);
    return button;
  }

  record SourcePreview(String sourceLine, String caretLine) {
  }

  public record OpenTarget(int oneBasedLine,
                           int oneBasedColumn,
                           int startOffset,
                           int endOffset,
                           VnsScriptAnalyzer.Diagnostic issue) {
  }

  private record DiagnosticRow(VnsScriptAnalyzer.Diagnostic issue,
                               int oneBasedLine,
                               int oneBasedColumn,
                               String level,
                               String kindLabel,
                               String displayText,
                               String searchText,
                               String sourceLine,
                               String caretLine) {
    String locationText() {
      return "L" + oneBasedLine + ":" + oneBasedColumn;
    }

    OpenTarget toOpenTarget() {
      return new OpenTarget(oneBasedLine, oneBasedColumn, issue.start(), issue.end(), issue);
    }
  }

  private static final class DiagnosticCell extends ListCell<DiagnosticRow> {
    private final Label severityBadge = new Label();
    private final Label locationBadge = new Label();
    private final Label kindBadge = new Label();
    private final Label messageLabel = new Label();
    private final Label sourceLabel = new Label();
    private final Label caretLabel = new Label();
    private final Label hintLabel = new Label("Enter or double-click to jump");
    private final HBox metaRow = new HBox(6, severityBadge, locationBadge, kindBadge);
    private final VBox content = new VBox(6, metaRow, messageLabel, sourceLabel, caretLabel, hintLabel);

    private DiagnosticCell(ListView<DiagnosticRow> parentList) {
      severityBadge.getStyleClass().add("vns-diagnostics-badge");
      locationBadge.getStyleClass().addAll("vns-diagnostics-badge", "vns-diagnostics-badge-location");
      kindBadge.getStyleClass().addAll("vns-diagnostics-badge", "vns-diagnostics-badge-kind");
      messageLabel.getStyleClass().add("vns-diagnostics-message");
      messageLabel.setWrapText(true);
      if (parentList != null) {
        messageLabel.prefWidthProperty().bind(parentList.widthProperty().subtract(60));
      }
      sourceLabel.getStyleClass().add("vns-diagnostics-source-line");
      sourceLabel.setWrapText(true);
      caretLabel.getStyleClass().add("vns-diagnostics-caret-line");
      hintLabel.getStyleClass().add("vns-diagnostics-hint");
      metaRow.setAlignment(Pos.CENTER_LEFT);
      content.getStyleClass().add("vns-diagnostics-row");
      content.setFillWidth(true);
    }

    @Override
    protected void updateItem(DiagnosticRow item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setGraphic(null);
        getStyleClass().remove("vns-diagnostics-cell");
        return;
      }

      if (!getStyleClass().contains("vns-diagnostics-cell")) {
        getStyleClass().add("vns-diagnostics-cell");
      }
      content.getStyleClass().removeAll("vns-diagnostics-row-error", "vns-diagnostics-row-warning");
      severityBadge.getStyleClass().removeAll("vns-diagnostics-badge-error", "vns-diagnostics-badge-warning");
      content.getStyleClass().add(item.issue().warning() ? "vns-diagnostics-row-warning" : "vns-diagnostics-row-error");
      severityBadge.getStyleClass().add(item.issue().warning() ? "vns-diagnostics-badge-warning" : "vns-diagnostics-badge-error");

      severityBadge.setText(item.level());
      locationBadge.setText(item.locationText());
      kindBadge.setText(item.kindLabel());
      messageLabel.setText(item.issue().message());
      sourceLabel.setText(item.sourceLine());
      caretLabel.setText(item.caretLine());

      setText(null);
      setGraphic(content);
    }
  }
}
