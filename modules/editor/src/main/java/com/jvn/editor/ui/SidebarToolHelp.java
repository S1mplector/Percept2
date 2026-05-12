package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

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
    Button btn = new Button("?");
    btn.getStyleClass().add("help-button");
    btn.setFocusTraversable(false);
    btn.setTooltip(new Tooltip("Click for help: " + title));
    btn.setOnAction(e -> {
      Stage stage = new Stage();
      stage.initModality(Modality.NONE);
      stage.initOwner(owner.getScene() != null ? owner.getScene().getWindow() : null);
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
      Scene ownerScene = owner.getScene();
      if (ownerScene != null) scene.getStylesheets().addAll(ownerScene.getStylesheets());
      stage.setScene(scene);
      stage.show();
    });
    return btn;
  }
}
