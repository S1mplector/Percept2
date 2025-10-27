package com.jvn.core.menu;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.DemoScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.core.engine.Engine;
import com.jvn.core.audio.AudioFacade;

import java.io.InputStream;

public class MainMenuScene implements Scene {
  private final Engine engine;
  private final VnSettings settingsModel;
  private final VnSaveManager saveManager;
  private final String defaultScriptName;
  private final AudioFacade audio;
  private int selected = 0;

  public MainMenuScene(Engine engine, VnSettings settingsModel, VnSaveManager saveManager, String defaultScriptName, AudioFacade audio) {
    this.engine = engine;
    this.settingsModel = settingsModel;
    this.saveManager = saveManager;
    this.defaultScriptName = defaultScriptName == null ? "demo.vns" : defaultScriptName;
    this.audio = audio;
  }

  public int getSelected() { return selected; }
  public void moveSelection(int delta) {
    int count = 4;
    selected = (selected + delta + count) % count;
  }

  public void activateSelected() {
    switch (selected) {
      case 0 -> startNewGame();
      case 1 -> engine.scenes().push(new LoadMenuScene(engine, saveManager, defaultScriptName, settingsModel, audio));
      case 2 -> engine.scenes().push(new SettingsScene(settingsModel));
      case 3 -> engine.stop();
    }
  }

  private void startNewGame() {
    VnScenario scenario = loadScenario(defaultScriptName);
    VnScene vnScene = new VnScene(scenario);
    if (audio != null) vnScene.setAudioFacade(audio);
    // Apply settings model to scene settings
    VnSettings s = vnScene.getState().getSettings();
    s.setTextSpeed(settingsModel.getTextSpeed());
    s.setBgmVolume(settingsModel.getBgmVolume());
    s.setSfxVolume(settingsModel.getSfxVolume());
    s.setVoiceVolume(settingsModel.getVoiceVolume());
    s.setAutoPlayDelay(settingsModel.getAutoPlayDelay());
    s.setSkipUnreadText(settingsModel.isSkipUnreadText());
    s.setSkipAfterChoices(settingsModel.isSkipAfterChoices());
    engine.scenes().push(vnScene);
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
  public void update(long deltaMs) {
  }
}
