package com.jvn.editor.ui;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

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
    setPadding(new Insets(8));
    GridPane g = new GridPane();
    g.setHgap(8); g.setVgap(8);
    g.addRow(0, new Label("Text Speed (ms/char)"), slText);
    g.addRow(1, new Label("BGM Volume"), slBgm);
    g.addRow(2, new Label("SFX Volume"), slSfx);
    g.addRow(3, new Label("Voice Volume"), slVoice);
    g.addRow(4, new Label("Auto-play Delay (ms)"), slAuto);
    g.addRow(5, cbSkipUnread);
    g.addRow(6, cbSkipAfterChoices);

    ToolBar tb = new ToolBar();
    Button bLoad = new Button("Load"); bLoad.setOnAction(e -> load());
    Button bSave = new Button("Save"); bSave.setOnAction(e -> save());
    Button bDefaults = new Button("Defaults"); bDefaults.setOnAction(e -> setFromModel(new SettingsModel()));
    tb.getItems().addAll(bLoad, bSave, bDefaults);

    setTop(tb);
    setCenter(g);
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
    } catch (Exception ignored) {}
  }

  private void load() {
    File f = settingsFile();
    if (f == null || !f.exists()) return;
    Properties p = new Properties();
    try (FileInputStream fis = new FileInputStream(f)) { p.load(fis); } catch (Exception ignored) {}
    SettingsModel s = new SettingsModel();
    try { s.setTextSpeed(Integer.parseInt(p.getProperty("textSpeed", "40"))); } catch (Exception ignored) {}
    try { s.setBgmVolume(Float.parseFloat(p.getProperty("bgm", "0.7"))); } catch (Exception ignored) {}
    try { s.setSfxVolume(Float.parseFloat(p.getProperty("sfx", "0.7"))); } catch (Exception ignored) {}
    try { s.setVoiceVolume(Float.parseFloat(p.getProperty("voice", "0.7"))); } catch (Exception ignored) {}
    try { s.setAutoPlayDelay(Long.parseLong(p.getProperty("autoPlayDelay", "1500"))); } catch (Exception ignored) {}
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
      return new File(projectRoot, fallback);
    }
  }
}
