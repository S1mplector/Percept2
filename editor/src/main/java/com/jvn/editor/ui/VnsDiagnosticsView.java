package com.jvn.editor.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
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
  private final Label errorCountLabel = new Label("0 Errors");
  private final Label warningCountLabel = new Label("0 Warnings");
  private final TextField filterField = new TextField();
  private final ComboBox<String> severityFilter = new ComboBox<>(
      FXCollections.observableArrayList(SEVERITY_ALL, SEVERITY_ERRORS, SEVERITY_WARNINGS)
  );
  private final ListView<DiagnosticRow> listView = new ListView<>();

  private final List<DiagnosticRow> allRows = new ArrayList<>();
  private Consumer<Integer> onOpenLine;
  private Consumer<OpenTarget> onOpenTarget;

  public VnsDiagnosticsView() {
    getStyleClass().add("vns-diagnostics-root");
    titleLabel.getStyleClass().add("vns-diagnostics-title");
    fileLabel.getStyleClass().add("vns-diagnostics-file");
    summaryLabel.getStyleClass().add("vns-diagnostics-summary");
    summaryLabel.setWrapText(true);
    errorCountLabel.getStyleClass().addAll("vns-diagnostics-chip", "vns-diagnostics-chip-error");
    warningCountLabel.getStyleClass().addAll("vns-diagnostics-chip", "vns-diagnostics-chip-warning");

    filterField.setPromptText("Filter diagnostics...");
    filterField.getStyleClass().add("vns-diagnostics-filter");
    filterField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());

    severityFilter.setValue(SEVERITY_ALL);
    severityFilter.setFocusTraversable(false);
    severityFilter.setPrefWidth(96);
    severityFilter.getStyleClass().add("run-console-filter-combo");
    severityFilter.setButtonCell(new javafx.scene.control.ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? "" : item);
      }
    });
    severityFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> applyFilter());

    Label placeholder = new Label("No diagnostics");
    placeholder.getStyleClass().add("vns-diagnostics-placeholder");
    listView.setPlaceholder(placeholder);
    listView.getStyleClass().add("vns-diagnostics-list");
    listView.setCellFactory(lv -> new DiagnosticCell());
    listView.setOnMouseClicked(e -> {
      if (e.getClickCount() < 2) return;
      openSelectedRow();
    });
    listView.setOnKeyPressed(e -> {
      if (e.getCode() != KeyCode.ENTER) return;
      openSelectedRow();
    });

    HBox titleRow = new HBox(8, CssIcon.list("#d7dde6"), titleLabel);
    titleRow.setAlignment(Pos.CENTER_LEFT);
    titleRow.getStyleClass().add("vns-diagnostics-title-row");

    HBox countsRow = new HBox(8, errorCountLabel, warningCountLabel);
    countsRow.setAlignment(Pos.CENTER_LEFT);

    VBox header = new VBox(6, titleRow, fileLabel, summaryLabel, countsRow);
    header.setPadding(new Insets(10, 10, 6, 10));
    header.getStyleClass().add("vns-diagnostics-header");

    HBox filterRow = new HBox(8, filterField, severityFilter);
    filterRow.setPadding(new Insets(0, 10, 8, 10));
    HBox.setHgrow(filterField, Priority.ALWAYS);
    filterRow.getStyleClass().add("vns-diagnostics-filter-row");

    setTop(new VBox(header, filterRow));
    setCenter(listView);
    setPadding(new Insets(0));
  }

  public void setOnOpenLine(Consumer<Integer> onOpenLine) {
    this.onOpenLine = onOpenLine;
  }

  public void setOnOpenTarget(Consumer<OpenTarget> onOpenTarget) {
    this.onOpenTarget = onOpenTarget;
  }

  public void clear() {
    fileLabel.setText("No active .vns file");
    summaryLabel.setText("Open a .vns script to see diagnostics.");
    errorCountLabel.setText("0 Errors");
    warningCountLabel.setText("0 Warnings");
    allRows.clear();
    listView.getItems().clear();
  }

  public void setAnalysis(File scriptFile, VnsScriptAnalyzer.Analysis analysis) {
    if (scriptFile == null || analysis == null) {
      clear();
      return;
    }

    fileLabel.setText(scriptFile.getName());
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
    List<DiagnosticRow> filtered = new ArrayList<>();
    for (DiagnosticRow row : allRows) {
      if (!matchesSeverity(row)) continue;
      if (!normalized.isEmpty() && !row.searchText().contains(normalized)) continue;
      filtered.add(row);
    }
    listView.setItems(FXCollections.observableArrayList(filtered));
    if (!filtered.isEmpty()) {
      listView.getSelectionModel().selectFirst();
    }
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

  private static DiagnosticRow buildRow(String source, VnsScriptAnalyzer.Diagnostic issue) {
    int line = Math.max(1, issue.line() + 1);
    int column = computeOneBasedColumn(source, issue.line(), issue.start());
    String level = issue.warning() ? "Warning" : "Error";
    String kindLabel = formatKind(issue.kind());
    String location = "L" + line + ":" + column;
    String display = level + "  " + location + "  " + kindLabel + "  " + issue.message();
    String searchText = buildSearchText(issue, line, column, level, kindLabel);
    return new DiagnosticRow(issue, line, column, level, kindLabel, display, searchText);
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
    return (
        level + " " +
        issue.kind() + " " +
        kindLabel + " " +
        issue.message() + " " +
        "line " + oneBasedLine + " column " + oneBasedColumn + " " +
        "l" + oneBasedLine + ":" + oneBasedColumn
    ).toLowerCase(Locale.ROOT);
  }

  private static String formatKind(String kind) {
    if (kind == null || kind.isBlank()) return "Issue";
    String normalized = kind.replace('_', ' ').trim();
    if (normalized.isEmpty()) return "Issue";
    return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
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
                               String searchText) {
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
    private final HBox metaRow = new HBox(6, severityBadge, locationBadge, kindBadge);
    private final VBox content = new VBox(6, metaRow, messageLabel);

    private DiagnosticCell() {
      severityBadge.getStyleClass().add("vns-diagnostics-badge");
      locationBadge.getStyleClass().addAll("vns-diagnostics-badge", "vns-diagnostics-badge-location");
      kindBadge.getStyleClass().addAll("vns-diagnostics-badge", "vns-diagnostics-badge-kind");
      messageLabel.getStyleClass().add("vns-diagnostics-message");
      messageLabel.setWrapText(true);
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

      setText(null);
      setGraphic(content);
    }
  }
}
