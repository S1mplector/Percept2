package com.jvn.editor;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class EditorApp extends Application {
  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    primaryStage.setTitle("JVN Editor (Stub)");
    BorderPane root = new BorderPane(new Label("JVN Editor - Coming Soon"));
    primaryStage.setScene(new Scene(root, 1200, 800));
    primaryStage.show();
  }
}
