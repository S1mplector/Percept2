package com.jvn.editor.ui.actioneditor;

import com.jvn.editor.ui.JesCodeEditor;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class CodePreviewPane extends VBox {
    private final JesCodeEditor jesEditor;
    private final Button btnCopy;
    private final Button btnRegenerate;
    private final Label lblStatus;
    private final Button btnPreviewApply;
    private final Button btnCommitPreview;
    private final Button btnDiscardPreview;
    private final ToggleButton btnToggleWarnings;
    private final Label lblDiagnostics;
    private boolean hasMessages = false;
    private Runnable onCopy;
    private Runnable onRegenerate;
    private Runnable onPreviewToModel;
    private Runnable onCommitPreview;
    private Runnable onDiscardPreview;
    private boolean manuallyEdited = false;
    private boolean suppressManualEditTracking = false;
    private boolean previewStaged = false;

    public CodePreviewPane() {
        setSpacing(10);
        setPadding(new Insets(12, 14, 12, 14));
        setStyle("-fx-background-color: #1a1a1a;");
        setMinWidth(0);
        setPrefWidth(340);

        Label header = new Label("Timeline Code");
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #e6e6e6; -fx-font-size: 12px;");
        header.setMinWidth(0);

        lblStatus = new Label("Auto-generated");
        lblStatus.setStyle("-fx-text-fill: #555; -fx-font-size: 9px;");
        lblStatus.setMinWidth(0);

        jesEditor = new JesCodeEditor();
        jesEditor.setLintMode(JesCodeEditor.LintMode.TIMELINE_BLOCK);
        jesEditor.setLintVisible(false);
        jesEditor.setMinWidth(0);
        jesEditor.setOnTextChanged(text -> {
            if (!suppressManualEditTracking) {
                markManuallyEdited();
            }
        });
        VBox.setVgrow(jesEditor, Priority.ALWAYS);

        btnCopy = makeIconButton(com.jvn.editor.ui.CssIcon.copy(), "Copy the code to clipboard");
        btnCopy.setTooltip(new Tooltip("Copy the code to clipboard"));
        btnCopy.setOnAction(e -> {
            if (onCopy != null) onCopy.run();
        });

        btnRegenerate = makeIconButton(com.jvn.editor.ui.CssIcon.auto(), "Discard manual edits and regenerate from the timeline");
        btnRegenerate.setTooltip(new Tooltip("Discard manual edits and regenerate from the timeline"));
        btnRegenerate.setOnAction(e -> {
            manuallyEdited = false;
            lblStatus.setText("Auto-generated");
            lblStatus.setStyle("-fx-text-fill: #555; -fx-font-size: 9px;");
            if (onRegenerate != null) onRegenerate.run();
        });

        btnPreviewApply = makeIconButton(com.jvn.editor.ui.CssIcon.check(), "Parse code and stage it in preview mode");
        btnPreviewApply.getStyleClass().add("puppeteer-toolbar-icon-button-success");
        btnPreviewApply.setTooltip(new Tooltip("Parse code and stage it in preview mode"));
        btnPreviewApply.setOnAction(e -> {
            if (onPreviewToModel != null) onPreviewToModel.run();
        });

        btnCommitPreview = makeIconButton(com.jvn.editor.ui.CssIcon.save(), "Commit staged preview changes to the model");
        btnCommitPreview.setTooltip(new Tooltip("Commit staged preview changes to the model"));
        btnCommitPreview.setDisable(true);
        btnCommitPreview.setOnAction(e -> {
            if (onCommitPreview != null) onCommitPreview.run();
        });

        btnDiscardPreview = makeIconButton(com.jvn.editor.ui.CssIcon.clearX(), "Discard staged preview and restore previous model");
        btnDiscardPreview.setTooltip(new Tooltip("Discard staged preview and restore previous model"));
        btnDiscardPreview.setDisable(true);
        btnDiscardPreview.setOnAction(e -> {
            if (onDiscardPreview != null) onDiscardPreview.run();
        });

        btnToggleWarnings = makeIconToggleButton(com.jvn.editor.ui.CssIcon.warning("#f0b673"), "Toggle warnings/diagnostics visibility");
        btnToggleWarnings.setSelected(true);
        btnToggleWarnings.setVisible(false);
        btnToggleWarnings.setManaged(false);
        btnToggleWarnings.setOnAction(e -> syncDiagnosticsVisibility());

        HBox buttonRow = new HBox(10, btnCopy, btnRegenerate, btnToggleWarnings, btnPreviewApply, btnCommitPreview, btnDiscardPreview);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        buttonRow.setMinWidth(0);

        lblDiagnostics = new Label("");
        lblDiagnostics.setWrapText(true);
        lblDiagnostics.setMinWidth(0);
        lblDiagnostics.setMaxWidth(Double.MAX_VALUE);
        lblDiagnostics.setStyle("-fx-text-fill: #888; -fx-font-size: 10px; -fx-padding: 4 0 0 0;");
        lblDiagnostics.setVisible(false);
        lblDiagnostics.setManaged(false);

        getChildren().addAll(header, lblStatus, jesEditor, buttonRow, lblDiagnostics);
    }

    private static Button makeIconButton(javafx.scene.layout.Region icon, String tooltip) {
        Button button = new Button();
        button.getStyleClass().add("puppeteer-toolbar-icon-button");
        button.setText("");
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setGraphicTextGap(0);
        button.setMinSize(34, 30);
        button.setPrefSize(34, 30);
        button.setMaxSize(34, 30);
        button.setFocusTraversable(false);
        return button;
    }

    private static ToggleButton makeIconToggleButton(javafx.scene.layout.Region icon, String tooltip) {
        ToggleButton button = new ToggleButton();
        button.getStyleClass().add("puppeteer-toolbar-icon-button");
        button.setText("");
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setGraphicTextGap(0);
        button.setMinSize(34, 30);
        button.setPrefSize(34, 30);
        button.setMaxSize(34, 30);
        button.setFocusTraversable(false);
        return button;
    }

    private void syncDiagnosticsVisibility() {
        boolean show = hasMessages && btnToggleWarnings.isSelected();
        lblDiagnostics.setVisible(show);
        lblDiagnostics.setManaged(show);
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

    public void setOnApplyToModel(Runnable callback) {
        this.onPreviewToModel = callback;
    }

    public void setOnCommitPreview(Runnable callback) {
        this.onCommitPreview = callback;
    }

    public void setOnDiscardPreview(Runnable callback) {
        this.onDiscardPreview = callback;
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

    public void setPreviewStaged(boolean staged) {
        previewStaged = staged;
        btnCommitPreview.setDisable(!staged);
        btnDiscardPreview.setDisable(!staged);
        if (staged) {
            lblStatus.setText("Preview staged — Commit or Discard");
            lblStatus.setStyle("-fx-text-fill: #4da3ff; -fx-font-size: 9px;");
        } else if (manuallyEdited) {
            lblStatus.setText("Manually edited — click Regenerate to sync");
            lblStatus.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 9px;");
        } else {
            lblStatus.setText("Auto-generated");
            lblStatus.setStyle("-fx-text-fill: #555; -fx-font-size: 9px;");
        }
    }

    public boolean isPreviewStaged() {
        return previewStaged;
    }

    public void markPreviewCommitted() {
        manuallyEdited = false;
        previewStaged = false;
        btnCommitPreview.setDisable(true);
        btnDiscardPreview.setDisable(true);
        lblStatus.setText("Preview committed to model");
        lblStatus.setStyle("-fx-text-fill: #58d68d; -fx-font-size: 9px;");
    }

    public void markPreviewDiscarded() {
        previewStaged = false;
        btnCommitPreview.setDisable(true);
        btnDiscardPreview.setDisable(true);
        lblStatus.setText("Preview discarded");
        lblStatus.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 9px;");
    }

    public void setDiagnostics(java.util.List<TimelineDiagnostic.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            hasMessages = false;
            btnToggleWarnings.setVisible(false);
            btnToggleWarnings.setManaged(false);
            lblDiagnostics.setText("");
            lblDiagnostics.setVisible(false);
            lblDiagnostics.setManaged(false);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (TimelineDiagnostic.Message m : messages) {
            String icon = switch (m.severity()) {
                case ERROR -> "\u274c";
                case WARNING -> "\u26a0";
                case INFO -> "\u2139";
            };
            sb.append(icon).append(" [").append(m.entityOrTrack()).append("] ");
            if (m.hasLine()) sb.append("L").append(m.line()).append(": ");
            sb.append(m.description());
            if (m.quickFix() != null) sb.append("  \u2192 ").append(m.quickFix());
            sb.append("\n");
        }
        boolean hasError = messages.stream().anyMatch(m -> m.severity() == TimelineDiagnostic.Severity.ERROR);
        lblDiagnostics.setStyle(hasError
            ? "-fx-text-fill: #e74c3c; -fx-font-size: 10px; -fx-padding: 4 0 0 0;"
            : "-fx-text-fill: #f0b673; -fx-font-size: 10px; -fx-padding: 4 0 0 0;");
        lblDiagnostics.setText(sb.toString().strip());
        hasMessages = true;
        btnToggleWarnings.setVisible(true);
        btnToggleWarnings.setManaged(true);
        syncDiagnosticsVisibility();
    }
}
