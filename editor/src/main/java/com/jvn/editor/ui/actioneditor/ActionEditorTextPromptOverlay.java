package com.jvn.editor.ui.actioneditor;

import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class ActionEditorTextPromptOverlay extends StackPane {
    private final Label titleLabel = new Label("Prompt");
    private final Label messageLabel = new Label();
    private final TextField inputField = new TextField();
    private final Label hintLabel = new Label("Enter a name to continue.");
    private final Button cancelButton = new Button("Cancel");
    private final Button confirmButton = new Button("Confirm");
    private final VBox card = new VBox(10);

    private Consumer<String> onConfirm = value -> {};

    ActionEditorTextPromptOverlay() {
        setManaged(false);
        setVisible(false);
        setPickOnBounds(true);
        setMouseTransparent(false);
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: rgba(6, 8, 12, 0.64);");

        titleLabel.setStyle("-fx-text-fill: #f2f4f7; -fx-font-size: 14px; -fx-font-weight: bold;");
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: #a9b3c1; -fx-font-size: 11px;");

        inputField.setStyle(
            "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; "
                + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 8; -fx-font-size: 11px;");
        inputField.textProperty().addListener((obs, oldValue, newValue) -> refreshState());
        inputField.setOnAction(event -> {
            confirm();
            event.consume();
        });

        hintLabel.setWrapText(true);
        hintLabel.setStyle("-fx-text-fill: #707988; -fx-font-size: 10px;");

        cancelButton.setStyle(
            "-fx-background-color: #23262c; -fx-text-fill: #d7dde6; -fx-background-radius: 4; "
                + "-fx-border-color: #3a3f48; -fx-border-radius: 4; -fx-padding: 5 12; -fx-font-size: 11px; -fx-cursor: hand;");
        cancelButton.setOnAction(event -> {
            hideOverlay();
            event.consume();
        });

        confirmButton.setStyle(
            "-fx-background-color: #315d98; -fx-text-fill: white; -fx-background-radius: 4; "
                + "-fx-border-radius: 4; -fx-padding: 5 12; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
        confirmButton.setOnAction(event -> {
            confirm();
            event.consume();
        });

        HBox actions = new HBox(8, cancelButton, confirmButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.setMaxWidth(320);
        card.setFillWidth(true);
        card.setPadding(new Insets(12));
        card.setStyle(
            "-fx-background-color: #171a20;"
                + "-fx-background-radius: 8;"
                + "-fx-border-color: #2f3540;"
                + "-fx-border-radius: 8;");
        VBox.setVgrow(inputField, Priority.NEVER);
        card.getChildren().setAll(titleLabel, messageLabel, inputField, hintLabel, actions);

        getChildren().setAll(card);

        setOnMouseClicked(event -> {
            if (event.getTarget() == this) {
                hideOverlay();
                event.consume();
            }
        });
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideOverlay();
                event.consume();
            }
        });

        refreshState();
    }

    void showPrompt(
        String title,
        String message,
        String promptText,
        String initialValue,
        String confirmLabel,
        Consumer<String> onConfirm
    ) {
        titleLabel.setText(isBlank(title) ? "Prompt" : title.trim());
        messageLabel.setText(isBlank(message) ? "" : message.trim());
        messageLabel.setManaged(!messageLabel.getText().isBlank());
        messageLabel.setVisible(!messageLabel.getText().isBlank());
        inputField.setPromptText(isBlank(promptText) ? "" : promptText.trim());
        inputField.setText(initialValue == null ? "" : initialValue);
        confirmButton.setText(isBlank(confirmLabel) ? "Confirm" : confirmLabel.trim());
        this.onConfirm = onConfirm != null ? onConfirm : value -> {};
        refreshState();
        setVisible(true);
        setManaged(true);
        toFront();
        Platform.runLater(() -> {
            inputField.requestFocus();
            inputField.selectAll();
        });
    }

    void hideOverlay() {
        inputField.clear();
        onConfirm = value -> {};
        setVisible(false);
        setManaged(false);
    }

    boolean isShowingOverlay() {
        return isVisible();
    }

    private void confirm() {
        String value = normalize(inputField.getText());
        if (value.isBlank()) {
            refreshState();
            return;
        }
        Consumer<String> callback = onConfirm;
        hideOverlay();
        callback.accept(value);
    }

    private void refreshState() {
        boolean valid = !normalize(inputField.getText()).isBlank();
        confirmButton.setDisable(!valid);
        hintLabel.setText(valid ? "Press Enter to confirm." : "Enter a non-empty name.");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
