package com.jvn.editor.ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.*;
import java.util.Properties;

public class SettingsEditorView extends BorderPane {
  public static class SettingsModel {
    private int textSpeed = 40;
    private float bgmVolume = 0.7f;
    private float sfxVolume = 0.7f;
    private float voiceVolume = 0.7f;
    private long autoPlayDelay = 1500;
    private boolean skipUnreadText;
    private boolean skipAfterChoices;
    public int getTextSpeed() { return textSpeed; }
    public void setTextSpeed(int v) { textSpeed = v; }
    public float getBgmVolume() { return bgmVolume; }
    public void setBgmVolume(float v) { bgmVolume = v; }
    public float getSfxVolume() { return sfxVolume; }
    public void setSfxVolume(float v) { sfxVolume = v; }
    public float getVoiceVolume() { return voiceVolume; }
    public void setVoiceVolume(float v) { voiceVolume = v; }
    public long getAutoPlayDelay() { return autoPlayDelay; }
    public void setAutoPlayDelay(long v) { autoPlayDelay = v; }
    public boolean isSkipUnreadText() { return skipUnreadText; }
    public void setSkipUnreadText(boolean b) { skipUnreadText = b; }
    public boolean isSkipAfterChoices() { return skipAfterChoices; }
    public void setSkipAfterChoices(boolean b) { skipAfterChoices = b; }
  }
  private File projectRoot;
  private final Slider slText = new Slider(10, 120, 40);
  private final Slider slBgm = new Slider(0, 1, 0.7);
  private final Slider slSfx = new Slider(0, 1, 0.7);
  private final Slider slVoice = new Slider(0, 1, 0.7);
  private final Slider slAuto = new Slider(500, 5000, 1500);
  private final CheckBox cbSkipUnread = new CheckBox("Skip Unread Text");
  private final CheckBox cbSkipAfterChoices = new CheckBox("Skip After Choices");

  public SettingsEditorView() {
    getStyleClass().addAll("editor-settings-view", "vn-settings-editor-view");
    setPadding(Insets.EMPTY);
    configureSliders();

    GridPane g = new GridPane();
    g.getStyleClass().add("vn-settings-editor-grid");
    g.setHgap(12); g.setVgap(12);
    g.addRow(0, fieldLabel("Text Speed (ms/char)"), slText);
    g.addRow(1, fieldLabel("BGM Volume"), slBgm);
    g.addRow(2, fieldLabel("SFX Volume"), slSfx);
    g.addRow(3, fieldLabel("Voice Volume"), slVoice);
    g.addRow(4, fieldLabel("Auto-play Delay (ms)"), slAuto);
    g.addRow(5, cbSkipUnread);
    g.addRow(6, cbSkipAfterChoices);

    ToolBar tb = new ToolBar();
    tb.getStyleClass().add("editor-settings-toolbar");
    Button bLoad = new Button("Load"); bLoad.setOnAction(e -> load());
    Button bSave = new Button("Save"); bSave.setOnAction(e -> save());
    Button bDefaults = new Button("Defaults"); bDefaults.setOnAction(e -> setFromModel(new SettingsModel()));
    bLoad.setTooltip(new Tooltip("Load runtime settings from disk"));
    bSave.setTooltip(new Tooltip("Save runtime settings"));
    bDefaults.setTooltip(new Tooltip("Restore runtime setting defaults in the form"));
    bLoad.getStyleClass().add("editor-settings-button");
    bSave.getStyleClass().add("editor-settings-button");
    bDefaults.getStyleClass().add("editor-settings-button");
    tb.getItems().addAll(bLoad, bSave, bDefaults);

    Label title = new Label("Runtime Settings");
    title.getStyleClass().add("editor-settings-header");
    Label copy = new Label("Tune project playback defaults written to the VN settings file.");
    copy.getStyleClass().add("editor-settings-copy");
    copy.setWrapText(true);
    VBox header = new VBox(4, title, copy);

    VBox settingsSection = new VBox(12, sectionTitle("Playback"), g);
    settingsSection.getStyleClass().add("editor-settings-section");

    VBox content = new VBox(12, header, settingsSection);
    content.setPadding(new Insets(12));
    content.getStyleClass().add("editor-settings-content");

    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("editor-settings-scroll");

    setTop(tb);
    setCenter(scroll);
  }

  private void configureSliders() {
    configureSlider(slText, 10, 10);
    configureSlider(slBgm, 0.25, 0.05);
    configureSlider(slSfx, 0.25, 0.05);
    configureSlider(slVoice, 0.25, 0.05);
    configureSlider(slAuto, 500, 250);
    cbSkipUnread.getStyleClass().add("vn-settings-editor-check");
    cbSkipAfterChoices.getStyleClass().add("vn-settings-editor-check");
  }

  private void configureSlider(Slider slider, double majorTickUnit, double blockIncrement) {
    slider.setShowTickMarks(true);
    slider.setShowTickLabels(true);
    slider.setMajorTickUnit(majorTickUnit);
    slider.setBlockIncrement(blockIncrement);
    slider.setMaxWidth(Double.MAX_VALUE);
    slider.getStyleClass().add("vn-settings-editor-slider");
    GridPane.setHgrow(slider, Priority.ALWAYS);
  }

  private Label fieldLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("editor-settings-label");
    label.setMinWidth(150);
    label.setAlignment(Pos.CENTER_LEFT);
    return label;
  }

  private Label sectionTitle(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("editor-settings-section-title");
    return label;
  }

  public void setProjectRoot(File dir) {
    this.projectRoot = dir;
    load();
  }

  public void setFromModel(SettingsModel s) {
    if (s == null) return;
    slText.setValue(s.getTextSpeed());
    slBgm.setValue(s.getBgmVolume());
    slSfx.setValue(s.getSfxVolume());
    slVoice.setValue(s.getVoiceVolume());
    slAuto.setValue(s.getAutoPlayDelay());
    cbSkipUnread.setSelected(s.isSkipUnreadText());
    cbSkipAfterChoices.setSelected(s.isSkipAfterChoices());
  }

  public SettingsModel toModel() {
    SettingsModel s = new SettingsModel();
    s.setTextSpeed((int) Math.round(slText.getValue()));
    s.setBgmVolume((float) slBgm.getValue());
    s.setSfxVolume((float) slSfx.getValue());
    s.setVoiceVolume((float) slVoice.getValue());
    s.setAutoPlayDelay(Math.round(slAuto.getValue()));
    s.setSkipUnreadText(cbSkipUnread.isSelected());
    s.setSkipAfterChoices(cbSkipAfterChoices.isSelected());
    return s;
  }

  private void save() {
    File f = settingsFile();
    if (f == null) return;
    Properties p = new Properties();
    SettingsModel s = toModel();
    p.setProperty("textSpeed", Integer.toString(s.getTextSpeed()));
    p.setProperty("bgm", Float.toString(s.getBgmVolume()));
    p.setProperty("sfx", Float.toString(s.getSfxVolume()));
    p.setProperty("voice", Float.toString(s.getVoiceVolume()));
    p.setProperty("autoPlayDelay", Long.toString(s.getAutoPlayDelay()));
    p.setProperty("skipUnread", Boolean.toString(s.isSkipUnreadText()));
    p.setProperty("skipAfterChoices", Boolean.toString(s.isSkipAfterChoices()));
    try {
      File parent = f.getParentFile();
      if (parent != null) parent.mkdirs();
      try (FileOutputStream fos = new FileOutputStream(f)) { p.store(fos, "VN Settings"); }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
  }

  private void load() {
    File f = settingsFile();
    if (f == null || !f.exists()) return;
    Properties p = new Properties();
    try (FileInputStream fis = new FileInputStream(f)) { p.load(fis); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    SettingsModel s = new SettingsModel();
    try { s.setTextSpeed(Integer.parseInt(p.getProperty("textSpeed", "40"))); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    try { s.setBgmVolume(Float.parseFloat(p.getProperty("bgm", "0.7"))); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    try { s.setSfxVolume(Float.parseFloat(p.getProperty("sfx", "0.7"))); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    try { s.setVoiceVolume(Float.parseFloat(p.getProperty("voice", "0.7"))); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    try { s.setAutoPlayDelay(Long.parseLong(p.getProperty("autoPlayDelay", "1500"))); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    s.setSkipUnreadText(Boolean.parseBoolean(p.getProperty("skipUnread", "false")));
    s.setSkipAfterChoices(Boolean.parseBoolean(p.getProperty("skipAfterChoices", "false")));
    setFromModel(s);
  }

  private File settingsFile() {
    if (projectRoot == null) return null;
    File configured = resolvePathFromManifest("settingsFile", "config/settings/vn.settings");
    if (configured.exists()) return configured;
    // Compatibility with older projects.
    File legacyConfig = new File(projectRoot, "config/vn.settings");
    if (legacyConfig.exists()) return legacyConfig;
    File legacy = new File(projectRoot, "vn.settings");
    return legacy.exists() ? legacy : configured;
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
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return new File(projectRoot, fallback);
    }
  }
}
