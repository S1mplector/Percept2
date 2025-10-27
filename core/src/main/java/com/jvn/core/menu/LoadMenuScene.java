package com.jvn.core.menu;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.DemoScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.save.VnSaveData;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.core.engine.Engine;
import com.jvn.core.audio.AudioFacade;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class LoadMenuScene implements Scene {
  private final Engine engine;
  private final VnSaveManager saveManager;
  private final String defaultScriptName;
  private final AudioFacade audio;
  private final List<String> saves = new ArrayList<>();
  private int selected = 0;

  public LoadMenuScene(Engine engine, VnSaveManager saveManager, String defaultScriptName, com.jvn.core.vn.VnSettings settingsModel, AudioFacade audio) {
    this.engine = engine;
    this.saveManager = saveManager;
    this.defaultScriptName = defaultScriptName == null ? "demo.vns" : defaultScriptName;
    this.audio = audio;
    refresh();
  }

  public void refresh() {
    saves.clear();
    saves.addAll(saveManager.listSaves());
    if (selected >= saves.size()) selected = Math.max(0, saves.size() - 1);
  }

  public List<String> getSaves() { return saves; }
  public int getSelected() { return selected; }
  public void moveSelection(int delta) {
    if (saves.isEmpty()) return;
    int count = saves.size();
    selected = (selected + delta + count) % count;
  }

  public String getSaveDirectory() { return saveManager.getSaveDirectory(); }
  public String getSelectedName() { return (saves.isEmpty() ? null : saves.get(selected)); }

  public boolean deleteSelected() {
    if (saves.isEmpty()) return false;
    String name = saves.get(selected);
    boolean ok = saveManager.deleteSave(name);
    refresh();
    return ok;
  }

  public boolean renameSelected(String newName) {
    if (saves.isEmpty() || newName == null || newName.isBlank()) return false;
    String old = saves.get(selected);
    boolean ok = saveManager.renameSave(old, newName);
    refresh();
    return ok;
  }

  /**
   * Try to provide a preview image path for the selected save.
   * Uses the currentBackgroundId from the saved data and maps it via the scenario's backgrounds.
   * Returns a classpath resource path (e.g., game/images/bg_room.png) or null on failure.
   */
  public String getSelectedPreviewImagePath() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      VnSaveData data = saveManager.load(name);
      String bgId = data.getCurrentBackgroundId();
      if (bgId == null) return null;
      // Load scenario to resolve background image path
      VnScenario scen = loadScenario(defaultScriptName);
      if (scen == null) return null;
      com.jvn.core.vn.VnBackground bg = scen.getBackground(bgId);
      return bg != null ? bg.getImagePath() : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  public void loadSelected() {
    if (saves.isEmpty()) return;
    String name = saves.get(selected);
    try {
      VnSaveData data = saveManager.load(name);
      VnScenario scenario = loadScenario(defaultScriptName);
      VnScene scene = new VnScene(scenario);
      if (audio != null) scene.setAudioFacade(audio);
      saveManager.applyToState(data, scene.getState());
      if (audio != null) {
        var s = scene.getState().getSettings();
        audio.setBgmVolume(s.getBgmVolume());
        audio.setSfxVolume(s.getSfxVolume());
        audio.setVoiceVolume(s.getVoiceVolume());
      }
      engine.scenes().push(scene);
    } catch (Exception ignored) {
    }
  }

  private VnScenario loadScenario(String scriptName) {
    try {
      AssetCatalog assets = new AssetCatalog();
      try (InputStream in = assets.open(AssetType.SCRIPT, scriptName)) {
        VnScriptParser parser = new VnScriptParser();
        return parser.parse(in);
      }
    } catch (Exception ignored) {
      return DemoScenario.createSimpleDemo();
    }
  }

  @Override
  public void update(long deltaMs) { }
}
