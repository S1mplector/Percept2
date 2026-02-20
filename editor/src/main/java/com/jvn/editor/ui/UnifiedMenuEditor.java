package com.jvn.editor.ui;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Unified menu editor that combines Layout, Style, and Screen editing
 * into a single cohesive workspace. Shows a live combined preview.
 */
public class UnifiedMenuEditor extends BorderPane {
  private final TabPane editorTabs = new TabPane();
  private final MenuLayoutVisualEditor layoutEditor = new MenuLayoutVisualEditor();
  private final MenuStyleVisualEditor styleEditor = new MenuStyleVisualEditor();
  private final MenuScreenVisualEditor screenEditor = new MenuScreenVisualEditor();

  private Consumer<String> onLayoutChanged;
  private Consumer<String> onStyleChanged;
  private Consumer<String> onScreenChanged;

  private File projectRoot;

  public UnifiedMenuEditor() {
    setPadding(new Insets(0));

    Tab layoutTab = new Tab("Layout", layoutEditor);
    layoutTab.setClosable(false);

    Tab styleTab = new Tab("Style", styleEditor);
    styleTab.setClosable(false);

    Tab screenTab = new Tab("Screen", screenEditor);
    screenTab.setClosable(false);

    editorTabs.getTabs().addAll(layoutTab, styleTab, screenTab);
    editorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

    VBox header = new VBox(4);
    header.setPadding(new Insets(8, 12, 8, 12));
    header.setStyle("-fx-background-color: #1a1c1e;");
    Label title = new Label("Unified Menu Editor");
    title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e8eaed;");
    Label hint = new Label("Edit layout, style, and screen content in one place. Changes sync automatically.");
    hint.setStyle("-fx-text-fill: #9aa0a6; -fx-font-size: 11px;");
    header.getChildren().addAll(title, hint);

    setTop(header);
    setCenter(editorTabs);

    setupSync();
  }

  private void setupSync() {
    layoutEditor.setOnLayoutTextChanged(text -> {
      if (onLayoutChanged != null) onLayoutChanged.accept(text);
      syncPreviewContent();
    });

    styleEditor.setOnStyleTextChanged(text -> {
      if (onStyleChanged != null) onStyleChanged.accept(text);
    });

    screenEditor.setOnMenuTextChanged(text -> {
      if (onScreenChanged != null) onScreenChanged.accept(text);
      syncPreviewContent();
    });
  }

  private void syncPreviewContent() {
    List<String> screenItems = screenEditor.getItemLabels();
    String title = screenEditor.getTitleText();
    if (screenItems != null && !screenItems.isEmpty()) {
      layoutEditor.setPreviewContent(title, screenItems);
      styleEditor.setPreviewContent(screenItems);
    }
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    styleEditor.setProjectRoot(root);
    screenEditor.setProjectRoot(root);
  }

  public void setLayoutText(String text) {
    layoutEditor.setLayoutText(text);
  }

  public void setStyleText(String text) {
    styleEditor.setStyleText(text);
  }

  public void setScreenText(String text) {
    screenEditor.setMenuText(text);
    syncPreviewContent();
  }

  public String getLayoutText() {
    return layoutEditor.getLayoutText();
  }

  public String getStyleText() {
    return styleEditor.getStyleText();
  }

  public String getScreenText() {
    return screenEditor.getMenuText();
  }

  public void setOnLayoutChanged(Consumer<String> listener) {
    this.onLayoutChanged = listener;
  }

  public void setOnStyleChanged(Consumer<String> listener) {
    this.onStyleChanged = listener;
  }

  public void setOnScreenChanged(Consumer<String> listener) {
    this.onScreenChanged = listener;
  }

  public void selectLayoutTab() {
    editorTabs.getSelectionModel().select(0);
  }

  public void selectStyleTab() {
    editorTabs.getSelectionModel().select(1);
  }

  public void selectScreenTab() {
    editorTabs.getSelectionModel().select(2);
  }
}
