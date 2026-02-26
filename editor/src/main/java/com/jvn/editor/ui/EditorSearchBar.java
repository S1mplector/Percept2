package com.jvn.editor.ui;

import com.jvn.core.nativebridge.NativeSearchBridge;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import org.fxmisc.richtext.CodeArea;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable search bar for code editors.
 * Provides find next/previous functionality with match highlighting.
 */
public class EditorSearchBar extends HBox {
  private final TextField searchField = new TextField();
  private final Label statusLabel = new Label("");
  private final Button prevButton = new Button("◀");
  private final Button nextButton = new Button("▶");
  private final Button closeButton = new Button("✕");

  private CodeArea codeArea;
  private List<int[]> matches = new ArrayList<>();
  private int currentMatchIndex = -1;
  private Runnable onClose;

  public EditorSearchBar() {
    setSpacing(8);
    setPadding(new Insets(6, 10, 6, 10));
    setAlignment(Pos.CENTER_LEFT);
    setStyle("-fx-background-color: #2d2d30; -fx-border-color: #3f3f46; -fx-border-width: 0 0 1 0;");

    searchField.setPromptText("Find...");
    searchField.setPrefWidth(200);
    HBox.setHgrow(searchField, Priority.NEVER);

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

    prevButton.setOnAction(e -> findPrevious());
    nextButton.setOnAction(e -> findNext());
    closeButton.setOnAction(e -> {
      if (onClose != null) onClose.run();
    });

    statusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    statusLabel.setMinWidth(80);

    getChildren().addAll(
        new Label("Find:"),
        searchField,
        prevButton,
        nextButton,
        statusLabel,
        closeButton
    );
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

    int[] found = NativeSearchBridge.findAllCaseInsensitive(text, query, text.length());
    int queryLen = query.length();
    for (int start : found) {
      if (start < 0) continue;
      int end = start + queryLen;
      if (end <= text.length()) {
        matches.add(new int[]{start, end});
      }
    }

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
}
