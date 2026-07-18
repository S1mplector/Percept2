package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Factory for the standardised sidebar-tool help (?) button. */
public final class SidebarToolHelp {

  private SidebarToolHelp() {}

  /**
   * Creates a circular {@code ?} button that opens a non-modal help window when clicked.
   *
   * @param owner the node whose scene/window the help stage should be owned by
   * @param title window title and help topic heading
   * @param body  multi-line help text displayed in the window
   */
  public static Button button(Node owner, String title, String body) {
    Button btn = createHelpButton(title);
    btn.setTooltip(new Tooltip("Click for help: " + title));
    btn.setOnAction(e -> {
      Scene ownerScene = owner.getScene();
      openHelp(ownerScene != null ? ownerScene.getWindow() : null,
               ownerScene != null ? ownerScene.getStylesheets() : null,
               title, body);
    });
    return btn;
  }

  /**
   * Creates a {@code ?} button owned by a {@link Window} (e.g. a {@link Stage}) rather than a Node.
   * Use this when the button lives inside a non-Node host such as {@code PuppeteerWindow}.
   */
  public static Button button(Window ownerWindow, String title, String body) {
    Button btn = createHelpButton(title);
    btn.setTooltip(new Tooltip("Click for help: " + title));
    btn.setOnAction(e -> {
      Scene ownerScene = ownerWindow != null ? ownerWindow.getScene() : null;
      openHelp(ownerWindow,
               ownerScene != null ? ownerScene.getStylesheets() : null,
               title, body);
    });
    return btn;
  }

  private static Button createHelpButton(String title) {
    Label glyph = new Label();
    glyph.setGraphic(CssIcon.help("#d6e6f2"));
    glyph.getStyleClass().add("help-button-glyph");
    glyph.setMouseTransparent(true);

    Button btn = new Button();
    btn.getStyleClass().add("help-button");
    btn.setGraphic(glyph);
    btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    btn.setFocusTraversable(false);
    btn.setAccessibleText("Help: " + title);
    btn.setAccessibleHelp("Open help for " + title);
    return btn;
  }

  private static void openHelp(Window ownerWindow,
                                java.util.List<String> stylesheets,
                                String title, String body) {
    Stage stage = new Stage();
    stage.initModality(Modality.NONE);
    stage.initOwner(ownerWindow);
    stage.setTitle(title);

    TextArea area = new TextArea(body);
    area.setEditable(false);
    area.setWrapText(true);
    area.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #e0e0e0;"
        + "-fx-font-size: 12px; -fx-background-color: #1e1e1e;");

    Button close = new Button("Got it");
    close.setDefaultButton(true);
    close.setOnAction(ev -> stage.close());

    VBox root = new VBox(10, area, close);
    root.setPadding(new Insets(14));
    root.setAlignment(Pos.BOTTOM_RIGHT);
    VBox.setVgrow(area, Priority.ALWAYS);

    Scene scene = new Scene(root, 490, 340);
    if (stylesheets != null) scene.getStylesheets().addAll(stylesheets);
    stage.setScene(scene);
    stage.show();
  }
}
