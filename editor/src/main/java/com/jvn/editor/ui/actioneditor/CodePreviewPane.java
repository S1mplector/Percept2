package com.jvn.editor.ui.actioneditor;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class CodePreviewPane extends VBox {
    private final TextArea codeArea;
    private final Button btnCopy;
    private Runnable onCopy;

    public CodePreviewPane() {
        setSpacing(8);
        setPadding(new Insets(8));
        setStyle("-fx-background-color: #1e1e2e;");
        setMinWidth(280);

        Label header = new Label("Generated Code");
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #cdd6f4;");

        codeArea = new TextArea();
        codeArea.setEditable(false);
        codeArea.setWrapText(false);
        codeArea.setStyle(
            "-fx-control-inner-background: #11111b; " +
            "-fx-text-fill: #cdd6f4; " +
            "-fx-font-family: 'JetBrains Mono', 'Fira Code', monospace; " +
            "-fx-font-size: 12px;"
        );
        VBox.setVgrow(codeArea, Priority.ALWAYS);

        btnCopy = new Button("📋 Copy to Clipboard");
        btnCopy.setMaxWidth(Double.MAX_VALUE);
        btnCopy.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e;");
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
