package com.jvn.editor.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
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
    error(owner, title, message, null);
  }

  public static void error(Window owner,
                           String title,
                           String message,
                           Throwable cause,
                           String... recoveryHints) {
    ErrorDialogModel model = buildErrorDialogModel(title, message, cause, recoveryHints);
    show(
        owner,
        title == null || title.isBlank() ? "Error" : title.trim(),
        model.headline(),
        model.content(),
        ActionSpec.neutral("copy", "Copy Details", () -> copyToClipboard(model.report()))
            .closeOnAction(false),
        ActionSpec.accent("close", "Close", null));
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

  private static ErrorDialogModel buildErrorDialogModel(String title,
                                                        String message,
                                                        Throwable cause,
                                                        String... recoveryHints) {
    String summary = normalizeDialogText(message, "The operation could not be completed.");
    Throwable root = rootCause(cause);
    List<String> hints = recoveryHints(summary, root, recoveryHints);

    VBox content = new VBox(12);
    content.getStyleClass().add("editor-dialog-error-content");

    HBox badgeRow = new HBox(8);
    badgeRow.setAlignment(Pos.CENTER_LEFT);
    Label badge = new Label("ERROR");
    badge.getStyleClass().add("editor-dialog-error-badge");
    Label context = new Label(title == null || title.isBlank() ? "JVN" : title.trim());
    context.getStyleClass().add("editor-dialog-error-context");
    context.setWrapText(true);
    badgeRow.getChildren().addAll(badge, context);

    VBox whatHappened = new VBox(5, sectionLabel("What happened"), bodyLabel(summary));
    content.getChildren().addAll(badgeRow, whatHappened);

    if (root != null) {
      content.getChildren().add(new VBox(5, sectionLabel("Likely cause"), bodyLabel(causeSummary(root))));
    }

    if (!hints.isEmpty()) {
      VBox hintBox = new VBox(5);
      hintBox.getChildren().add(sectionLabel("What you can try"));
      for (String hint : hints) {
        hintBox.getChildren().add(hintLabel(hint));
      }
      content.getChildren().add(hintBox);
    }

    String report = buildErrorReport(title, summary, root, cause, hints);
    TitledPane technicalDetails = technicalDetailsPane(report);
    content.getChildren().add(technicalDetails);

    return new ErrorDialogModel(
        "JVN could not complete this action. Review the cause and next steps below.",
        content,
        report);
  }

  private static Label sectionLabel(String text) {
    Label label = new Label(text == null ? "" : text);
    label.getStyleClass().add("editor-dialog-section-title");
    return label;
  }

  private static Label bodyLabel(String text) {
    Label label = new Label(text == null ? "" : text);
    label.getStyleClass().add("editor-dialog-message");
    label.setWrapText(true);
    label.setMaxWidth(520);
    return label;
  }

  private static Label hintLabel(String text) {
    Label label = bodyLabel("- " + normalizeDialogText(text, "Try the action again."));
    label.getStyleClass().add("editor-dialog-error-hint");
    return label;
  }

  private static TitledPane technicalDetailsPane(String report) {
    TextArea details = new TextArea(report == null ? "" : report);
    details.setEditable(false);
    details.setWrapText(false);
    details.getStyleClass().add("editor-dialog-text-area");
    details.setPrefRowCount(7);

    TitledPane pane = new TitledPane("Technical details", details);
    pane.getStyleClass().add("editor-dialog-error-details");
    pane.setExpanded(false);
    pane.setCollapsible(true);
    return pane;
  }

  private static List<String> recoveryHints(String summary, Throwable root, String... providedHints) {
    LinkedHashSet<String> hints = new LinkedHashSet<>();
    if (providedHints != null) {
      for (String hint : providedHints) {
        if (hint != null && !hint.isBlank()) hints.add(hint.trim());
      }
    }
    if (!hints.isEmpty()) return new ArrayList<>(hints);

    String haystack = (summary + " " + (root == null ? "" : root.getMessage()))
        .toLowerCase(Locale.ROOT);
    if (haystack.contains("workspace root")) {
      hints.add("Launch JVN from the repository root or reopen the project through the launcher.");
    }
    if (haystack.contains("jvn.project") || haystack.contains("manifest")) {
      hints.add("Open a project folder that contains a readable jvn.project file.");
    }
    if (haystack.contains("open") || haystack.contains("load") || haystack.contains("read")
        || haystack.contains("not found")) {
      hints.add("Confirm the file or folder still exists and your account can read it.");
    }
    if (haystack.contains("save") || haystack.contains("write") || haystack.contains("create")) {
      hints.add("Confirm the destination folder exists and your account can write to it.");
    }
    if (haystack.contains("process") || haystack.contains("gradle") || haystack.contains("run")
        || haystack.contains("launch")) {
      hints.add("Check the run console or terminal output for process-specific errors.");
    }
    if (hints.isEmpty()) {
      hints.add("Check the selected project, file, or folder and try the action again.");
    }
    hints.add("Copy the technical details if you need to report the issue.");
    return new ArrayList<>(hints);
  }

  private static String buildErrorReport(String title,
                                         String summary,
                                         Throwable root,
                                         Throwable cause,
                                         List<String> hints) {
    StringBuilder report = new StringBuilder();
    report.append("Title: ").append(normalizeDialogText(title, "Error")).append('\n');
    report.append("Summary: ").append(summary).append('\n');
    if (root != null) {
      report.append("Root cause: ").append(causeSummary(root)).append('\n');
    }
    if (hints != null && !hints.isEmpty()) {
      report.append('\n').append("Suggested next steps:").append('\n');
      for (String hint : hints) {
        report.append("- ").append(hint).append('\n');
      }
    }
    if (cause != null) {
      report.append('\n').append("Stack trace:").append('\n');
      StringWriter stack = new StringWriter();
      cause.printStackTrace(new PrintWriter(stack));
      report.append(stack.toString().stripTrailing());
    }
    return report.toString().stripTrailing();
  }

  private static String causeSummary(Throwable throwable) {
    if (throwable == null) return "Unknown error";
    String message = normalizeDialogText(throwable.getMessage(), throwable.getClass().getSimpleName());
    return throwable.getClass().getSimpleName() + ": " + message;
  }

  private static Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    for (int depth = 0; current != null && current.getCause() != null && depth < 32; depth++) {
      Throwable next = current.getCause();
      if (next == current) break;
      current = next;
    }
    return current;
  }

  private static String normalizeDialogText(String text, String fallback) {
    String normalized = text == null ? "" : text.trim();
    if (normalized.isBlank()) return fallback == null ? "" : fallback;
    return normalized;
  }

  private static void copyToClipboard(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text == null ? "" : text);
    Clipboard.getSystemClipboard().setContent(content);
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

  private record ErrorDialogModel(String headline, VBox content, String report) {
  }
}
