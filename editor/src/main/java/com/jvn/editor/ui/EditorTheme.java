package com.jvn.editor.ui;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;

public final class EditorTheme {
  private static final String CSS_PATH = "/com/jvn/editor/editor.css";
  private static String cachedCssUrl;

  private EditorTheme() {}

  private static String cssUrl() {
    if (cachedCssUrl != null) return cachedCssUrl;
    var url = EditorTheme.class.getResource(CSS_PATH);
    cachedCssUrl = (url == null) ? "" : url.toExternalForm();
    return cachedCssUrl;
  }

  public static void apply(Scene scene) {
    if (scene == null) return;
    String css = cssUrl();
    if (css.isEmpty()) return;
    if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
  }

  public static void apply(Stage stage) {
    if (stage == null) return;
    apply(stage.getScene());
  }

  public static <T extends Dialog<?>> T apply(T dialog) {
    if (dialog == null) return null;
    DialogPane pane = dialog.getDialogPane();
    String css = cssUrl();
    if (!css.isEmpty() && !pane.getStylesheets().contains(css)) pane.getStylesheets().add(css);
    if (!pane.getStyleClass().contains("jvn-dialog")) pane.getStyleClass().add("jvn-dialog");
    return dialog;
  }
}
