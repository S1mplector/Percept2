package com.jvn.editor.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
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
  private final Label titleLabel = new Label("VNS Diagnostics");
  private final Label fileLabel = new Label("No active .vns file");
  private final Label summaryLabel = new Label("Open a .vns script to see diagnostics.");
  private final TextField filterField = new TextField();
  private final ListView<DiagnosticRow> listView = new ListView<>();

  private final List<DiagnosticRow> allRows = new ArrayList<>();
  private Consumer<Integer> onOpenLine;

  public VnsDiagnosticsView() {
    titleLabel.setStyle("-fx-font-weight: 700; -fx-font-size: 13px;");
    fileLabel.setStyle("-fx-text-fill: #99a0af;");
    summaryLabel.setWrapText(true);

    filterField.setPromptText("Filter diagnostics...");
    filterField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());

    listView.setPlaceholder(new Label("No diagnostics"));
    listView.setCellFactory(lv -> new DiagnosticCell());
    listView.setOnMouseClicked(e -> {
      if (e.getClickCount() < 2) return;
      DiagnosticRow row = listView.getSelectionModel().getSelectedItem();
      if (row == null || onOpenLine == null) return;
      onOpenLine.accept(row.issue().line() + 1);
    });
    listView.setOnKeyPressed(e -> {
      if (e.getCode() != KeyCode.ENTER) return;
      DiagnosticRow row = listView.getSelectionModel().getSelectedItem();
      if (row == null || onOpenLine == null) return;
      onOpenLine.accept(row.issue().line() + 1);
    });

    VBox header = new VBox(6, titleLabel, fileLabel, summaryLabel);
    header.setPadding(new Insets(10, 10, 6, 10));

    HBox filterRow = new HBox(filterField);
    filterRow.setPadding(new Insets(0, 10, 8, 10));
    HBox.setHgrow(filterField, Priority.ALWAYS);

    setTop(new VBox(header, filterRow));
    setCenter(listView);
    setPadding(new Insets(0));
  }

  public void setOnOpenLine(Consumer<Integer> onOpenLine) {
    this.onOpenLine = onOpenLine;
  }

  public void clear() {
    fileLabel.setText("No active .vns file");
    summaryLabel.setText("Open a .vns script to see diagnostics.");
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
      allRows.add(new DiagnosticRow(issue));
      if (issue.warning()) warnings++; else errors++;
    }

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
    if (normalized.isEmpty()) {
      listView.setItems(FXCollections.observableArrayList(allRows));
      return;
    }

    List<DiagnosticRow> filtered = new ArrayList<>();
    for (DiagnosticRow row : allRows) {
      VnsScriptAnalyzer.Diagnostic issue = row.issue();
      String haystack = (issue.kind() + " " + issue.message() + " " + (issue.line() + 1)).toLowerCase(Locale.ROOT);
      if (haystack.contains(normalized)) {
        filtered.add(row);
      }
    }
    listView.setItems(FXCollections.observableArrayList(filtered));
  }

  private record DiagnosticRow(VnsScriptAnalyzer.Diagnostic issue) {
  }

  private static final class DiagnosticCell extends ListCell<DiagnosticRow> {
    @Override
    protected void updateItem(DiagnosticRow item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setStyle("");
        return;
      }

      VnsScriptAnalyzer.Diagnostic issue = item.issue();
      String level = issue.warning() ? "Warning" : "Error";
      setText(level + "  L" + (issue.line() + 1) + "  " + issue.message());
      setStyle(issue.warning() ? "-fx-text-fill: #f0b673;" : "-fx-text-fill: #f38ba8;");
    }
  }
}
