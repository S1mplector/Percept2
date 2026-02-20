package com.jvn.editor.ui.actioneditor;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class CodePreviewPane extends VBox {
    private final TextArea codeArea;
    private final Button btnCopy;
    private Runnable onCopy;

    public CodePreviewPane() {
        setSpacing(6);
        setPadding(new Insets(6, 8, 6, 8));
        setStyle("-fx-background-color: #1a1a1a;");
        setMinWidth(260);

        Label header = new Label("Generated Code");
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #e6e6e6; -fx-font-size: 12px;");

        codeArea = new TextArea();
        codeArea.setEditable(false);
        codeArea.setWrapText(false);
        codeArea.setStyle(
            "-fx-control-inner-background: #121212; " +
            "-fx-text-fill: #e6e6e6; " +
            "-fx-font-family: 'JetBrains Mono', 'Fira Code', monospace; " +
            "-fx-font-size: 12px;"
        );
        VBox.setVgrow(codeArea, Priority.ALWAYS);

        btnCopy = new Button("Copy to Clipboard");
        btnCopy.setMaxWidth(Double.MAX_VALUE);
        btnCopy.setStyle("-fx-background-color: #4da3ff; -fx-text-fill: #0a0a0a; -fx-background-radius: 4; " +
            "-fx-border-radius: 4; -fx-padding: 5 12; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
        btnCopy.setOnMouseEntered(e -> btnCopy.setStyle("-fx-background-color: #5bb3ff; -fx-text-fill: #0a0a0a; -fx-background-radius: 4; " +
            "-fx-border-radius: 4; -fx-padding: 5 12; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;"));
        btnCopy.setOnMouseExited(e -> btnCopy.setStyle("-fx-background-color: #4da3ff; -fx-text-fill: #0a0a0a; -fx-background-radius: 4; " +
            "-fx-border-radius: 4; -fx-padding: 5 12; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;"));
        btnCopy.setOnAction(e -> {
            if (onCopy != null) onCopy.run();
        });

        getChildren().addAll(header, codeArea, btnCopy);
    }

    public void setCode(String code) {
        codeArea.setText(code != null ? code : "");
        codeArea.positionCaret(0);
    }

    public String getCode() {
        return codeArea.getText();
    }

    public void setOnCopy(Runnable callback) {
        this.onCopy = callback;
    }
}
