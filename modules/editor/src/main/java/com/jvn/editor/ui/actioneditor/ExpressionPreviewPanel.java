package com.jvn.editor.ui.actioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Compact expression browser for the selected character: search/filter list,
 * current-expression indicator, and Apply/Clear controls for a transient
 * preview. See docs/superpowers/specs/2026-08-19-puppeteer-expression-preview-design.md.
 */
public final class ExpressionPreviewPanel extends VBox {

    private final Label headerLabel = new Label("No character selected");
    private final TextField filterField = new TextField();
    private final ListView<String> listView = new ListView<>();
    private final Button applyButton = new Button("Apply");
    private final Button clearButton = new Button("Clear");

    private List<String> allExpressionNames = List.of();
    private String currentExpressionName;
    private String previewingExpressionName;
    private String activeCharacterId;

    private Consumer<String> onExpressionChosen;
    private Runnable onApply;
    private Runnable onClear;

    public ExpressionPreviewPanel() {
        super(6);
        setPadding(new Insets(8));
        headerLabel.getStyleClass().add("expression-preview-header");
        filterField.setPromptText("Filter expressions…");
        filterField.textProperty().addListener((obs, oldV, newV) -> refreshList());
        listView.setPrefHeight(220);
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            if (newV.equals(previewingExpressionName)) {
                fireClear();
                return;
            }
            previewingExpressionName = newV;
            updateHeaderAndButtons();
            if (onExpressionChosen != null) onExpressionChosen.accept(newV);
        });

        applyButton.setDisable(true);
        applyButton.setOnAction(e -> { if (onApply != null) onApply.run(); });
        clearButton.setDisable(true);
        clearButton.setOnAction(e -> fireClear());

        HBox buttons = new HBox(6, applyButton, clearButton);
        getChildren().addAll(headerLabel, filterField, listView, buttons);
    }

    private void fireClear() {
        previewingExpressionName = null;
        listView.getSelectionModel().clearSelection();
        updateHeaderAndButtons();
        if (onClear != null) onClear.run();
    }

    public void setCharacterContext(String characterId, List<String> expressionNames, String currentExpressionName) {
        this.activeCharacterId = characterId;
        this.allExpressionNames = expressionNames == null ? List.of() : List.copyOf(expressionNames);
        this.currentExpressionName = currentExpressionName;
        this.previewingExpressionName = null;
        filterField.clear();
        listView.getSelectionModel().clearSelection();
        refreshList();
        updateHeaderAndButtons();
    }

    public void setPreviewingState(String expressionName) {
        this.previewingExpressionName = expressionName;
        updateHeaderAndButtons();
    }

    private void refreshList() {
        List<String> filtered = filterExpressionNames(allExpressionNames, filterField.getText());
        ObservableList<String> items = FXCollections.observableArrayList(filtered);
        listView.setItems(items);
    }

    private void updateHeaderAndButtons() {
        boolean previewing = previewingExpressionName != null && !previewingExpressionName.isBlank();
        if (activeCharacterId == null || activeCharacterId.isBlank()) {
            headerLabel.setText("No character selected");
        } else if (previewing) {
            headerLabel.setText(activeCharacterId + " — Previewing: " + previewingExpressionName);
        } else {
            String shown = (currentExpressionName == null || currentExpressionName.isBlank())
                ? "neutral" : currentExpressionName;
            headerLabel.setText(activeCharacterId + " — Expression: " + shown);
        }
        applyButton.setDisable(!previewing);
        clearButton.setDisable(!previewing);
    }

    public void setOnExpressionChosen(Consumer<String> handler) { this.onExpressionChosen = handler; }
    public void setOnApply(Runnable handler) { this.onApply = handler; }
    public void setOnClear(Runnable handler) { this.onClear = handler; }

    static List<String> filterExpressionNames(List<String> names, String query) {
        if (names == null) return List.of();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return List.copyOf(names);
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (name != null && name.toLowerCase(Locale.ROOT).contains(needle)) result.add(name);
        }
        return List.copyOf(result);
    }
}
