package com.jvn.editor.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class EditorDialogs {
  public enum ButtonStyle {
    NEUTRAL,
    ACCENT,
    DANGER
  }

  public static final class ActionSpec {
    private final String id;
    private final String label;
    private final ButtonStyle style;
    private final boolean closeOnAction;
    private final boolean defaultFocus;
    private final Runnable action;

    private ActionSpec(String id,
                       String label,
                       ButtonStyle style,
                       boolean closeOnAction,
                       boolean defaultFocus,
                       Runnable action) {
      this.id = (id == null || id.isBlank()) ? "ok" : id.trim();
      this.label = (label == null || label.isBlank()) ? "OK" : label.trim();
      this.style = style == null ? ButtonStyle.NEUTRAL : style;
      this.closeOnAction = closeOnAction;
      this.defaultFocus = defaultFocus;
      this.action = action != null ? action : () -> {};
    }

    public static ActionSpec neutral(String id, String label, Runnable action) {
      return new ActionSpec(id, label, ButtonStyle.NEUTRAL, true, false, action);
    }

    public static ActionSpec accent(String id, String label, Runnable action) {
      return new ActionSpec(id, label, ButtonStyle.ACCENT, true, true, action);
    }

    public static ActionSpec danger(String id, String label, Runnable action) {
      return new ActionSpec(id, label, ButtonStyle.DANGER, true, false, action);
    }

    public ActionSpec closeOnAction(boolean value) {
      return new ActionSpec(id, label, style, value, defaultFocus, action);
    }

    public ActionSpec defaultFocus(boolean value) {
      return new ActionSpec(id, label, style, closeOnAction, value, action);
    }
  }

  private EditorDialogs() {}

  public static Optional<String> show(Window owner,
                                      String title,
                                      String message,
                                      Node content,
                                      Node focusTarget,
                                      ActionSpec... actions) {
    AtomicReference<String> result = new AtomicReference<>();
    Stage stage = createStage(owner, title, message, content, focusTarget, result, actions);
    stage.showAndWait();
    return Optional.ofNullable(result.get());
  }

  public static Optional<String> show(Window owner,
                                      String title,
                                      String message,
                                      Node content,
                                      ActionSpec... actions) {
    return show(owner, title, message, content, null, actions);
  }

  public static void info(Window owner, String title, String message) {
    show(owner, title, message, null, ActionSpec.accent("close", "Close", null));
  }

  public static void warning(Window owner, String title, String message) {
    show(owner, title, message, null, ActionSpec.accent("close", "Close", null));
  }

  public static void error(Window owner, String title, String message) {
    show(owner, title, message, null, ActionSpec.accent("close", "Close", null));
  }

  public static boolean confirm(Window owner,
                                String title,
                                String message,
                                String confirmLabel,
                                boolean danger) {
    ActionSpec cancel = ActionSpec.neutral("cancel", "Cancel", null).defaultFocus(true);
    ActionSpec confirm = danger
        ? ActionSpec.danger("confirm", confirmLabel == null ? "Confirm" : confirmLabel, null)
        : ActionSpec.accent("confirm", confirmLabel == null ? "Confirm" : confirmLabel, null);
    return show(owner, title, message, null, cancel, confirm).filter("confirm"::equals).isPresent();
  }

  public static Optional<String> promptText(Window owner,
                                            String title,
                                            String message,
                                            String labelText,
                                            String initialValue,
                                            String promptText,
                                            String confirmLabel) {
    TextField field = new TextField(initialValue == null ? "" : initialValue);
    field.setPromptText(promptText == null ? "" : promptText);
    field.getStyleClass().add("editor-dialog-text-field");

    Label fieldLabel = new Label(labelText == null || labelText.isBlank() ? "Value" : labelText);
    fieldLabel.getStyleClass().add("editor-dialog-field-label");

    VBox content = new VBox(8, fieldLabel, field);
    content.getStyleClass().add("editor-dialog-form");

    Optional<String> result = show(
        owner,
        title,
        message,
        content,
        field,
        ActionSpec.neutral("cancel", "Cancel", null).defaultFocus(false),
        ActionSpec.accent("submit", confirmLabel == null ? "OK" : confirmLabel, null).defaultFocus(true));
    if (result.filter("submit"::equals).isPresent()) {
      return Optional.ofNullable(field.getText());
    }
    return Optional.empty();
  }

  public static <T> Optional<T> choose(Window owner,
                                       String title,
                                       String message,
                                       List<T> options,
                                       T initialValue,
                                       Function<T, String> labeler,
                                       String confirmLabel) {
    if (options == null || options.isEmpty()) return Optional.empty();
    ListView<T> listView = new ListView<>();
    listView.getItems().setAll(options);
    listView.getStyleClass().add("editor-dialog-choice-list");
    listView.setCellFactory(v -> new ListCell<>() {
      @Override
      protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          return;
        }
        setText(labeler == null ? String.valueOf(item) : labeler.apply(item));
      }
    });
    listView.setPrefHeight(Math.min(320, 48 + options.size() * 30));
    if (initialValue != null) {
      listView.getSelectionModel().select(initialValue);
    } else {
      listView.getSelectionModel().selectFirst();
    }

    Optional<String> result = show(
        owner,
        title,
        message,
        listView,
        listView,
        ActionSpec.neutral("cancel", "Cancel", null),
        ActionSpec.accent("choose", confirmLabel == null ? "Choose" : confirmLabel, null));
    if (result.filter("choose"::equals).isPresent()) {
      return Optional.ofNullable(listView.getSelectionModel().getSelectedItem());
    }
    return Optional.empty();
  }

  public static void showTextBlock(Window owner,
                                   String title,
                                   String message,
                                   String body,
                                   String closeLabel) {
    TextArea area = new TextArea(body == null ? "" : body);
    area.setEditable(false);
    area.setWrapText(true);
    area.getStyleClass().add("editor-dialog-text-area");
    area.setPrefRowCount(16);
    show(owner, title, message, area, ActionSpec.accent("close", closeLabel == null ? "Close" : closeLabel, null));
  }

  private static Stage createStage(Window owner,
                                   String title,
                                   String message,
                                   Node content,
                                   Node focusTarget,
                                   AtomicReference<String> result,
                                   ActionSpec... actions) {
    StackPane overlay = new StackPane();
    overlay.getStyleClass().add("editor-dialog-overlay");

    VBox card = new VBox(12);
    card.getStyleClass().add("editor-dialog-card");
    card.setMaxWidth(560);
    card.setFillWidth(true);
    card.setPadding(new Insets(16));

    Label titleLabel = new Label(title == null || title.isBlank() ? "Dialog" : title.trim());
    titleLabel.getStyleClass().add("editor-dialog-title");

    Label messageLabel = new Label(message == null ? "" : message.trim());
    messageLabel.getStyleClass().add("editor-dialog-message");
    messageLabel.setWrapText(true);
    boolean showMessage = !messageLabel.getText().isBlank();
    messageLabel.setManaged(showMessage);
    messageLabel.setVisible(showMessage);

    VBox bodyBox = new VBox();
    bodyBox.getStyleClass().add("editor-dialog-body");
    if (content != null) {
      if (content instanceof Region region) {
        region.setMaxWidth(Double.MAX_VALUE);
      }
      bodyBox.getChildren().add(content);
    }
    bodyBox.setManaged(content != null);
    bodyBox.setVisible(content != null);
    VBox.setVgrow(bodyBox, Priority.ALWAYS);

    HBox actionsBox = new HBox(8);
    actionsBox.setAlignment(Pos.CENTER_RIGHT);

    List<ActionSpec> effectiveActions = new ArrayList<>();
    if (actions == null || actions.length == 0) {
      effectiveActions.add(ActionSpec.accent("close", "Close", null));
    } else {
      for (ActionSpec action : actions) {
        if (action != null) effectiveActions.add(action);
      }
    }

    Stage stage = new Stage(StageStyle.TRANSPARENT);
    if (owner != null) {
      stage.initOwner(owner);
      stage.initModality(Modality.WINDOW_MODAL);
    } else {
      stage.initModality(Modality.APPLICATION_MODAL);
    }

    AtomicReference<Runnable> cancelAction = new AtomicReference<>(stage::close);
    for (ActionSpec spec : effectiveActions) {
      javafx.scene.control.Button button = new javafx.scene.control.Button(spec.label);
      button.getStyleClass().add("editor-dialog-button");
      switch (spec.style) {
        case ACCENT -> button.getStyleClass().add("editor-dialog-button-accent");
        case DANGER -> button.getStyleClass().add("editor-dialog-button-danger");
        case NEUTRAL -> button.getStyleClass().add("editor-dialog-button-neutral");
      }
      button.setOnAction(event -> {
        result.set(spec.id);
        spec.action.run();
        if (spec.closeOnAction) stage.close();
        event.consume();
      });
      actionsBox.getChildren().add(button);
      if ("cancel".equalsIgnoreCase(spec.id)) {
        cancelAction.set(() -> {
          result.set(spec.id);
          stage.close();
        });
      }
      if (spec.defaultFocus) {
        Platform.runLater(button::requestFocus);
      }
    }

    card.getChildren().add(titleLabel);
    if (showMessage) card.getChildren().add(messageLabel);
    if (content != null) card.getChildren().add(bodyBox);
    card.getChildren().add(actionsBox);

    overlay.getChildren().add(card);
    overlay.setAlignment(Pos.CENTER);
    overlay.setOnMouseClicked(event -> {
      if (event.getTarget() == overlay) {
        cancelAction.get().run();
        event.consume();
      }
    });

    Scene scene = new Scene(overlay);
    scene.setFill(Color.TRANSPARENT);
    EditorTheme.apply(scene);
    scene.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE) {
        cancelAction.get().run();
        event.consume();
      }
    });

    stage.setTitle(titleLabel.getText());
    stage.setScene(scene);
    stage.setResizable(false);
    stage.sizeToScene();
    Platform.runLater(() -> {
      if (focusTarget != null) {
        focusTarget.requestFocus();
      }
    });
    return stage;
  }
}
