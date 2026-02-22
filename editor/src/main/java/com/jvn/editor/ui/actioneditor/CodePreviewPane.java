package com.jvn.editor.ui.actioneditor;

import com.jvn.editor.ui.JesCodeEditor;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class CodePreviewPane extends VBox {
    private final JesCodeEditor jesEditor;
    private final Button btnCopy;
    private final Button btnRegenerate;
    private final Label lblStatus;
    private Runnable onCopy;
    private Runnable onRegenerate;
    private boolean manuallyEdited = false;
    private boolean suppressManualEditTracking = false;

    private static final String STYLE_BTN_ACCENT =
        "-fx-background-color: #4da3ff; -fx-text-fill: #0a0a0a; -fx-background-radius: 4; " +
        "-fx-border-radius: 4; -fx-padding: 5 12; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String STYLE_BTN_DARK =
        "-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 4; " +
        "-fx-border-radius: 4; -fx-padding: 5 12; -fx-font-size: 11px; -fx-cursor: hand;";

    public CodePreviewPane() {
        setSpacing(10);
        setPadding(new Insets(12, 14, 12, 14));
        setStyle("-fx-background-color: #1a1a1a;");
        setMinWidth(300);

        Label header = new Label("Timeline Code");
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #e6e6e6; -fx-font-size: 12px;");

        lblStatus = new Label("Auto-generated");
        lblStatus.setStyle("-fx-text-fill: #555; -fx-font-size: 9px;");

        jesEditor = new JesCodeEditor();
        jesEditor.setOnTextChanged(text -> {
            if (!suppressManualEditTracking) {
                markManuallyEdited();
            }
        });
        VBox.setVgrow(jesEditor, Priority.ALWAYS);

        btnCopy = new Button("Copy to Clipboard");
        btnCopy.setMaxWidth(Double.MAX_VALUE);
        btnCopy.setStyle(STYLE_BTN_ACCENT);
        btnCopy.setTooltip(new Tooltip("Copy the code to clipboard"));
        btnCopy.setOnAction(e -> {
            if (onCopy != null) onCopy.run();
        });

        btnRegenerate = new Button("Regenerate");
        btnRegenerate.setStyle(STYLE_BTN_DARK);
        btnRegenerate.setTooltip(new Tooltip("Discard manual edits and regenerate from the timeline"));
        btnRegenerate.setOnAction(e -> {
            manuallyEdited = false;
            lblStatus.setText("Auto-generated");
            lblStatus.setStyle("-fx-text-fill: #555; -fx-font-size: 9px;");
            if (onRegenerate != null) onRegenerate.run();
        });

        HBox buttonRow = new HBox(10, btnCopy, btnRegenerate);
        HBox.setHgrow(btnCopy, Priority.ALWAYS);
        btnCopy.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(header, lblStatus, jesEditor, buttonRow);
    }

    public void setCode(String code) {
        if (manuallyEdited) return;
        try {
            suppressManualEditTracking = true;
            jesEditor.setTextNoEvent(code != null ? code : "");
        } finally {
            suppressManualEditTracking = false;
        }
    }

    public String getCode() {
        return jesEditor.getText();
    }

    public void setOnCopy(Runnable callback) {
        this.onCopy = callback;
    }

    public void setOnRegenerate(Runnable callback) {
        this.onRegenerate = callback;
    }

    public void setProjectRoot(java.io.File root) {
        jesEditor.setProjectRoot(root);
    }

    public void markManuallyEdited() {
        if (!manuallyEdited) {
            manuallyEdited = true;
            lblStatus.setText("Manually edited \u2014 click Regenerate to sync");
            lblStatus.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 9px;");
        }
    }

    public boolean isManuallyEdited() {
        return manuallyEdited;
    }
}
