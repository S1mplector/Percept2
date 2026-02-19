package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class MenuThemeEditorView extends BorderPane {
  private File projectRoot;

  // Colors
  private final TextField tfBackground = new TextField("#0A0C12");
  private final TextField tfTitleColor = new TextField("#FFFFFF");
  private final TextField tfItemColor = new TextField("#D3D3D3");
  private final TextField tfItemSelected = new TextField("#FFFF00");
  private final TextField tfHintColor = new TextField("rgba(200,200,200,0.8)");
  private final TextField tfAccentColor = new TextField("#FFFF00");

  // Fonts
  private final TextField tfTitleFont = new TextField("Arial");
  private final ChoiceBox<String> cbTitleWeight = new ChoiceBox<>(javafx.collections.FXCollections.observableArrayList("NORMAL","BOLD"));
  private final Spinner<Integer> spTitleSize = new Spinner<>(8, 96, 32);

  private final TextField tfItemFont = new TextField("Arial");
  private final ChoiceBox<String> cbItemWeight = new ChoiceBox<>(javafx.collections.FXCollections.observableArrayList("NORMAL","BOLD"));
  private final Spinner<Integer> spItemSize = new Spinner<>(8, 96, 20);

  private final TextField tfHintFont = new TextField("Arial");
  private final ChoiceBox<String> cbHintWeight = new ChoiceBox<>(javafx.collections.FXCollections.observableArrayList("NORMAL","BOLD"));
  private final Spinner<Integer> spHintSize = new Spinner<>(8, 96, 14);

  // Labels
  private final TextField tfTitleText = new TextField("");
  private final TextField tfLabelNew = new TextField("");
  private final TextField tfLabelLoad = new TextField("");
  private final TextField tfLabelSettings = new TextField("");
  private final TextField tfLabelQuit = new TextField("");

  public MenuThemeEditorView() {
    setPadding(new Insets(8));

    GridPane g = new GridPane();
    g.setHgap(8); g.setVgap(8);

    int r = 0;
    g.addRow(r++, new Label("Background Color"), tfBackground);
    g.addRow(r++, new Label("Title Color"), tfTitleColor);
    g.addRow(r++, new Label("Item Color"), tfItemColor);
    g.addRow(r++, new Label("Item Selected Color"), tfItemSelected);
    g.addRow(r++, new Label("Hint Color"), tfHintColor);
    g.addRow(r++, new Label("Accent Color"), tfAccentColor);

    cbTitleWeight.getSelectionModel().select("BOLD");
    cbItemWeight.getSelectionModel().select("NORMAL");
    cbHintWeight.getSelectionModel().select("NORMAL");

    g.addRow(r++, new Label("Title Font Family"), tfTitleFont);
    g.addRow(r++, new Label("Title Font Weight"), cbTitleWeight);
    g.addRow(r++, new Label("Title Font Size"), spTitleSize);

    g.addRow(r++, new Label("Item Font Family"), tfItemFont);
    g.addRow(r++, new Label("Item Font Weight"), cbItemWeight);
    g.addRow(r++, new Label("Item Font Size"), spItemSize);

    g.addRow(r++, new Label("Hint Font Family"), tfHintFont);
    g.addRow(r++, new Label("Hint Font Weight"), cbHintWeight);
    g.addRow(r++, new Label("Hint Font Size"), spHintSize);

    g.addRow(r++, new Label("Title Text (optional)"), tfTitleText);
    g.addRow(r++, new Label("Label: New Game"), tfLabelNew);
    g.addRow(r++, new Label("Label: Load"), tfLabelLoad);
    g.addRow(r++, new Label("Label: Settings"), tfLabelSettings);
    g.addRow(r++, new Label("Label: Quit"), tfLabelQuit);

    ToolBar tb = new ToolBar();
    Button bLoad = new Button("Load"); bLoad.setOnAction(e -> load());
    Button bSave = new Button("Save"); bSave.setOnAction(e -> save());
    Button bDefaults = new Button("Defaults"); bDefaults.setOnAction(e -> setDefaults());
    tb.getItems().addAll(bLoad, bSave, bDefaults);

    setTop(tb);
    setCenter(new ScrollPane(g));
  }

  public void setProjectRoot(File dir) {
    this.projectRoot = dir;
    load();
  }

  private File themeFile() {
    if (projectRoot == null) return null;
    File configured = resolvePathFromManifest("menuTheme", "config/menu/menu.theme");
    if (configured.exists()) return configured;
    // Compatibility with older projects.
    File legacyConfig = new File(projectRoot, "config/menu.theme");
    if (legacyConfig.exists()) return legacyConfig;
    File legacy = new File(projectRoot, "scripts/menu.theme");
    return legacy.exists() ? legacy : configured;
  }

  private void setDefaults() {
    tfBackground.setText("#0A0C12");
    tfTitleColor.setText("#FFFFFF");
    tfItemColor.setText("#D3D3D3");
    tfItemSelected.setText("#FFFF00");
    tfHintColor.setText("rgba(200,200,200,0.8)");
    tfAccentColor.setText("#FFFF00");

    tfTitleFont.setText("Arial"); cbTitleWeight.getSelectionModel().select("BOLD"); spTitleSize.getValueFactory().setValue(32);
    tfItemFont.setText("Arial"); cbItemWeight.getSelectionModel().select("NORMAL"); spItemSize.getValueFactory().setValue(20);
    tfHintFont.setText("Arial"); cbHintWeight.getSelectionModel().select("NORMAL"); spHintSize.getValueFactory().setValue(14);

    tfTitleText.setText("");
    tfLabelNew.setText("");
    tfLabelLoad.setText("");
    tfLabelSettings.setText("");
    tfLabelQuit.setText("");
  }

  private void save() {
    File f = themeFile(); if (f == null) return;
    try {
      f.getParentFile().mkdirs();
      Properties p = new Properties();
      p.setProperty("backgroundColor", tfBackground.getText());
      p.setProperty("titleColor", tfTitleColor.getText());
      p.setProperty("itemColor", tfItemColor.getText());
      p.setProperty("itemSelectedColor", tfItemSelected.getText());
      p.setProperty("hintColor", tfHintColor.getText());
      p.setProperty("accentColor", tfAccentColor.getText());

      p.setProperty("titleFontFamily", tfTitleFont.getText());
      p.setProperty("titleFontWeight", cbTitleWeight.getValue());
      p.setProperty("titleFontSize", Integer.toString(spTitleSize.getValue()));

      p.setProperty("itemFontFamily", tfItemFont.getText());
      p.setProperty("itemFontWeight", cbItemWeight.getValue());
      p.setProperty("itemFontSize", Integer.toString(spItemSize.getValue()));

      p.setProperty("hintFontFamily", tfHintFont.getText());
      p.setProperty("hintFontWeight", cbHintWeight.getValue());
      p.setProperty("hintFontSize", Integer.toString(spHintSize.getValue()));

      if (!tfTitleText.getText().trim().isEmpty()) p.setProperty("titleText", tfTitleText.getText().trim());
      if (!tfLabelNew.getText().trim().isEmpty()) p.setProperty("label.new", tfLabelNew.getText().trim());
      if (!tfLabelLoad.getText().trim().isEmpty()) p.setProperty("label.load", tfLabelLoad.getText().trim());
      if (!tfLabelSettings.getText().trim().isEmpty()) p.setProperty("label.settings", tfLabelSettings.getText().trim());
      if (!tfLabelQuit.getText().trim().isEmpty()) p.setProperty("label.quit", tfLabelQuit.getText().trim());

      try (FileOutputStream fos = new FileOutputStream(f)) { p.store(fos, "Menu Theme"); }
    } catch (Exception ignored) { }
  }

  private void load() {
    File f = themeFile(); if (f == null || !f.exists()) { setDefaults(); return; }
    try (FileInputStream fis = new FileInputStream(f)) {
      Properties p = new Properties();
      p.load(fis);
      tfBackground.setText(p.getProperty("backgroundColor", tfBackground.getText()));
      tfTitleColor.setText(p.getProperty("titleColor", tfTitleColor.getText()));
      tfItemColor.setText(p.getProperty("itemColor", tfItemColor.getText()));
      tfItemSelected.setText(p.getProperty("itemSelectedColor", tfItemSelected.getText()));
      tfHintColor.setText(p.getProperty("hintColor", tfHintColor.getText()));
      tfAccentColor.setText(p.getProperty("accentColor", tfAccentColor.getText()));

      tfTitleFont.setText(p.getProperty("titleFontFamily", tfTitleFont.getText()));
      cbTitleWeight.getSelectionModel().select(p.getProperty("titleFontWeight", cbTitleWeight.getValue()));
      try { spTitleSize.getValueFactory().setValue(Integer.parseInt(p.getProperty("titleFontSize", Integer.toString(spTitleSize.getValue())))); } catch (Exception ignored) {}

      tfItemFont.setText(p.getProperty("itemFontFamily", tfItemFont.getText()));
      cbItemWeight.getSelectionModel().select(p.getProperty("itemFontWeight", cbItemWeight.getValue()));
      try { spItemSize.getValueFactory().setValue(Integer.parseInt(p.getProperty("itemFontSize", Integer.toString(spItemSize.getValue())))); } catch (Exception ignored) {}

      tfHintFont.setText(p.getProperty("hintFontFamily", tfHintFont.getText()));
      cbHintWeight.getSelectionModel().select(p.getProperty("hintFontWeight", cbHintWeight.getValue()));
      try { spHintSize.getValueFactory().setValue(Integer.parseInt(p.getProperty("hintFontSize", Integer.toString(spHintSize.getValue())))); } catch (Exception ignored) {}

      tfTitleText.setText(p.getProperty("titleText", ""));
      tfLabelNew.setText(p.getProperty("label.new", ""));
      tfLabelLoad.setText(p.getProperty("label.load", ""));
      tfLabelSettings.setText(p.getProperty("label.settings", ""));
      tfLabelQuit.setText(p.getProperty("label.quit", ""));
    } catch (Exception ignored) {
      setDefaults();
    }
  }

  private File resolvePathFromManifest(String key, String fallback) {
    File manifest = new File(projectRoot, "jvn.project");
    if (!manifest.exists()) return new File(projectRoot, fallback);
    Properties p = new Properties();
    try (FileInputStream fis = new FileInputStream(manifest)) {
      p.load(fis);
      String rel = p.getProperty(key, fallback).trim();
      if (rel.isEmpty()) rel = fallback;
      return new File(projectRoot, rel);
    } catch (Exception ignored) {
      return new File(projectRoot, fallback);
    }
  }
}
