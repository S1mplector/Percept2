package com.jvn.editor.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.fxmisc.richtext.CodeArea;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Reusable search bar for code editors.
 * Provides find next/previous and replace functionality with match highlighting.
 */
public class EditorSearchBar extends VBox {
  private final TextField searchField = new TextField();
  private final TextField replaceField = new TextField();
  private final Label statusLabel = new Label("");
  private final Button prevButton = new Button("◀");
  private final Button nextButton = new Button("▶");
  private final Button replaceButton = new Button("Replace");
  private final Button replaceAllButton = new Button("All");
  private final Button closeButton = new Button("✕");
  private final HBox findRow = new HBox(8);
  private final HBox replaceRow = new HBox(8);

  private CodeArea codeArea;
  private List<int[]> matches = new ArrayList<>();
  private int currentMatchIndex = -1;
  private Runnable onClose;
  private boolean replaceVisible = false;

  public EditorSearchBar() {
    setSpacing(2);
    setPadding(new Insets(6, 10, 6, 10));
    getStyleClass().add("editor-search-bar");

    searchField.setPromptText("Find...");
    searchField.getStyleClass().add("editor-search-field");
    searchField.setTooltip(new Tooltip("Find text in the current editor"));
    searchField.setPrefWidth(200);
    HBox.setHgrow(searchField, Priority.NEVER);

    replaceField.setPromptText("Replace...");
    replaceField.getStyleClass().add("editor-search-field");
    replaceField.setTooltip(new Tooltip("Replacement text for the current match"));
    replaceField.setPrefWidth(200);
    HBox.setHgrow(replaceField, Priority.NEVER);

    searchField.textProperty().addListener((obs, oldVal, newVal) -> performSearch());
    searchField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        if (e.isShiftDown()) {
          findPrevious();
        } else {
          findNext();
        }
        e.consume();
      } else if (e.getCode() == KeyCode.ESCAPE) {
        if (onClose != null) onClose.run();
        e.consume();
      }
    });

    replaceField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        replaceCurrent();
        e.consume();
      } else if (e.getCode() == KeyCode.ESCAPE) {
        if (onClose != null) onClose.run();
        e.consume();
      }
    });

    prevButton.setOnAction(e -> findPrevious());
    nextButton.setOnAction(e -> findNext());
    replaceButton.setOnAction(e -> replaceCurrent());
    replaceAllButton.setOnAction(e -> replaceAll());
    closeButton.setOnAction(e -> {
      if (onClose != null) onClose.run();
    });

    statusLabel.getStyleClass().add("editor-search-status");
    statusLabel.setMinWidth(80);

    prevButton.getStyleClass().add("editor-search-button");
    nextButton.getStyleClass().add("editor-search-button");
    replaceButton.getStyleClass().add("editor-search-button");
    replaceAllButton.getStyleClass().add("editor-search-button");
    closeButton.getStyleClass().add("editor-search-button");
    prevButton.setTooltip(new Tooltip("Find previous match (Shift+Enter)"));
    nextButton.setTooltip(new Tooltip("Find next match (Enter)"));
    replaceButton.setTooltip(new Tooltip("Replace the current match"));
    replaceAllButton.setTooltip(new Tooltip("Replace all matches"));
    closeButton.setTooltip(new Tooltip("Close find bar (Esc)"));

    findRow.setAlignment(Pos.CENTER_LEFT);
    Label findLabel = new Label("Find:");
    findLabel.getStyleClass().add("editor-search-label");
    findRow.getChildren().addAll(
        findLabel,
        searchField,
        prevButton,
        nextButton,
        statusLabel,
        closeButton
    );

    replaceRow.setAlignment(Pos.CENTER_LEFT);
    Label replaceLabel = new Label("Replace:");
    replaceLabel.getStyleClass().add("editor-search-label");
    replaceRow.getChildren().addAll(
        replaceLabel,
        replaceField,
        replaceButton,
        replaceAllButton
    );
    replaceRow.setVisible(false);
    replaceRow.setManaged(false);

    getChildren().addAll(findRow, replaceRow);
  }

  public void setCodeArea(CodeArea codeArea) {
    this.codeArea = codeArea;
  }

  public void setOnClose(Runnable onClose) {
    this.onClose = onClose;
  }

  public void focus() {
    searchField.requestFocus();
    searchField.selectAll();
  }

  public void focusReplace() {
    replaceField.requestFocus();
    replaceField.selectAll();
  }

  public void showReplace(boolean show) {
    replaceVisible = show;
    replaceRow.setVisible(show);
    replaceRow.setManaged(show);
  }

  public boolean isReplaceVisible() {
    return replaceVisible;
  }

  public void setSearchText(String text) {
    searchField.setText(text);
  }

  private void performSearch() {
    matches.clear();
    currentMatchIndex = -1;

    if (codeArea == null) {
      updateStatus();
      return;
    }

    String query = searchField.getText();
    if (query == null || query.isEmpty()) {
      updateStatus();
      return;
    }

    String text = codeArea.getText();
    if (text == null || text.isEmpty()) {
      updateStatus();
      return;
    }

    addCaseInsensitiveMatches(text, query);

    if (!matches.isEmpty()) {
      int caretPos = codeArea.getCaretPosition();
      for (int i = 0; i < matches.size(); i++) {
        if (matches.get(i)[0] >= caretPos) {
          currentMatchIndex = i;
          break;
        }
      }
      if (currentMatchIndex < 0) currentMatchIndex = 0;
      highlightCurrentMatch();
    }

    updateStatus();
  }

  private void findNext() {
    if (matches.isEmpty()) return;
    currentMatchIndex = (currentMatchIndex + 1) % matches.size();
    highlightCurrentMatch();
    updateStatus();
  }

  private void findPrevious() {
    if (matches.isEmpty()) return;
    currentMatchIndex = (currentMatchIndex - 1 + matches.size()) % matches.size();
    highlightCurrentMatch();
    updateStatus();
  }

  private void highlightCurrentMatch() {
    if (codeArea == null || matches.isEmpty() || currentMatchIndex < 0) return;
    int[] match = matches.get(currentMatchIndex);
    codeArea.selectRange(match[0], match[1]);
    codeArea.requestFollowCaret();
  }

  private void replaceCurrent() {
    if (codeArea == null || matches.isEmpty() || currentMatchIndex < 0) return;
    int[] match = matches.get(currentMatchIndex);
    String replacement = replaceField.getText();
    if (replacement == null) replacement = "";
    codeArea.replaceText(match[0], match[1], replacement);
    performSearch();
  }

  private void replaceAll() {
    if (codeArea == null || matches.isEmpty()) return;
    String replacement = replaceField.getText();
    if (replacement == null) replacement = "";
    // Replace from end to start to preserve offsets
    for (int i = matches.size() - 1; i >= 0; i--) {
      int[] match = matches.get(i);
      codeArea.replaceText(match[0], match[1], replacement);
    }
    performSearch();
  }

  private void updateStatus() {
    if (matches.isEmpty()) {
      String query = searchField.getText();
      if (query != null && !query.isEmpty()) {
        statusLabel.setText("No results");
      } else {
        statusLabel.setText("");
      }
    } else {
      statusLabel.setText((currentMatchIndex + 1) + " of " + matches.size());
    }
  }

  private void addCaseInsensitiveMatches(String text, String query) {
    String haystack = text.toLowerCase(Locale.ROOT);
    String needle = query.toLowerCase(Locale.ROOT);
    int queryLen = query.length();
    int start = 0;
    while (start <= haystack.length() - needle.length()) {
      int found = haystack.indexOf(needle, start);
      if (found < 0) break;
      int end = found + queryLen;
      if (end <= text.length()) {
        matches.add(new int[]{found, end});
      }
      start = found + 1;
    }
  }
}
