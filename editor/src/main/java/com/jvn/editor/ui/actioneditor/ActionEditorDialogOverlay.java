package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class ActionEditorDialogOverlay extends StackPane {
    enum ButtonStyle {
        NEUTRAL,
        ACCENT,
        DANGER
    }

    static final class ActionSpec {
        private final String label;
        private final ButtonStyle style;
        private final boolean closeOnAction;
        private final boolean defaultFocus;
        private final Runnable action;

        private ActionSpec(
            String label,
            ButtonStyle style,
            boolean closeOnAction,
            boolean defaultFocus,
            Runnable action
        ) {
            this.label = label == null || label.isBlank() ? "OK" : label.trim();
            this.style = style == null ? ButtonStyle.NEUTRAL : style;
            this.closeOnAction = closeOnAction;
            this.defaultFocus = defaultFocus;
            this.action = action != null ? action : () -> {};
        }

        static ActionSpec neutral(String label, Runnable action) {
            return new ActionSpec(label, ButtonStyle.NEUTRAL, true, false, action);
        }

        static ActionSpec accent(String label, Runnable action) {
            return new ActionSpec(label, ButtonStyle.ACCENT, true, true, action);
        }

        static ActionSpec danger(String label, Runnable action) {
            return new ActionSpec(label, ButtonStyle.DANGER, true, false, action);
        }

        static ActionSpec stayOpen(String label, ButtonStyle style, Runnable action) {
            return new ActionSpec(label, style, false, false, action);
        }

        ActionSpec closeOnAction(boolean value) {
            return new ActionSpec(label, style, value, defaultFocus, action);
        }

        ActionSpec defaultFocus(boolean value) {
            return new ActionSpec(label, style, closeOnAction, value, action);
        }
    }

    private static final String STYLE_CARD =
        "-fx-background-color: #171a20;"
            + "-fx-background-radius: 10;"
            + "-fx-border-color: #2f3540;"
            + "-fx-border-radius: 10;";
    private static final String STYLE_BUTTON_NEUTRAL =
        "-fx-background-color: #23262c; -fx-text-fill: #d7dde6; -fx-background-radius: 4; "
            + "-fx-border-color: #3a3f48; -fx-border-radius: 4; -fx-padding: 5 12; -fx-font-size: 11px; -fx-cursor: hand;";
    private static final String STYLE_BUTTON_ACCENT =
        "-fx-background-color: #315d98; -fx-text-fill: white; -fx-background-radius: 4; "
            + "-fx-border-radius: 4; -fx-padding: 5 12; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String STYLE_BUTTON_DANGER =
        "-fx-background-color: #6d2f3a; -fx-text-fill: #ffe3e7; -fx-background-radius: 4; "
            + "-fx-border-radius: 4; -fx-padding: 5 12; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";

    private final Label titleLabel = new Label("Dialog");
    private final Label messageLabel = new Label();
    private final VBox bodyBox = new VBox(10);
    private final HBox actionsBox = new HBox(8);
    private final VBox card = new VBox(12);
    private final ScrollPane bodyScroll = new ScrollPane();

    private final List<Button> actionButtons = new ArrayList<>();

    ActionEditorDialogOverlay() {
        setManaged(false);
        setVisible(false);
        setPickOnBounds(true);
        setMouseTransparent(false);
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: rgba(6, 8, 12, 0.64);");

        titleLabel.setStyle("-fx-text-fill: #f2f4f7; -fx-font-size: 14px; -fx-font-weight: bold;");
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: #a9b3c1; -fx-font-size: 11px;");

        bodyBox.setFillWidth(true);

        bodyScroll.setFitToWidth(true);
        bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bodyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        bodyScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        bodyScroll.setContent(bodyBox);
        VBox.setVgrow(bodyScroll, Priority.ALWAYS);

        actionsBox.setAlignment(Pos.CENTER_RIGHT);

        card.setMaxWidth(560);
        card.setMaxHeight(620);
        card.setFillWidth(true);
        card.setPadding(new Insets(14));
        card.setStyle(STYLE_CARD);
        card.getChildren().setAll(titleLabel, messageLabel, bodyScroll, actionsBox);

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
    }

    void showDialog(String title, String message, Node content, ActionSpec... actions) {
        titleLabel.setText(title == null || title.isBlank() ? "Dialog" : title.trim());
        String normalizedMessage = message == null ? "" : message.trim();
        messageLabel.setText(normalizedMessage);
        messageLabel.setManaged(!normalizedMessage.isBlank());
        messageLabel.setVisible(!normalizedMessage.isBlank());

        bodyBox.getChildren().clear();
        if (content != null) {
            bodyBox.getChildren().add(content);
        }
        bodyScroll.setVisible(content != null);
        bodyScroll.setManaged(content != null);

        actionsBox.getChildren().clear();
        actionButtons.clear();

        ActionSpec[] effective = (actions == null || actions.length == 0)
            ? new ActionSpec[]{ActionSpec.accent("Close", this::hideOverlay)}
            : actions;

        Button focusTarget = null;
        for (ActionSpec spec : effective) {
            Button button = new Button(spec.label);
            button.setStyle(styleFor(spec.style));
            button.setOnAction(event -> {
                if (spec.closeOnAction) {
                    hideOverlay();
                }
                spec.action.run();
                event.consume();
            });
            actionButtons.add(button);
            actionsBox.getChildren().add(button);
            if (focusTarget == null || spec.defaultFocus) {
                focusTarget = button;
            }
        }

        setVisible(true);
        setManaged(true);
        toFront();

        Button initialFocus = focusTarget;
        Platform.runLater(() -> {
            if (initialFocus != null) {
                initialFocus.requestFocus();
            } else {
                card.requestFocus();
            }
        });
    }

    void hideOverlay() {
        setVisible(false);
        setManaged(false);
        bodyBox.getChildren().clear();
        actionsBox.getChildren().clear();
        actionButtons.clear();
    }

    boolean isShowingOverlay() {
        return isVisible();
    }

    private static String styleFor(ButtonStyle style) {
        return switch (style) {
            case ACCENT -> STYLE_BUTTON_ACCENT;
            case DANGER -> STYLE_BUTTON_DANGER;
            case NEUTRAL -> STYLE_BUTTON_NEUTRAL;
        };
    }
}
