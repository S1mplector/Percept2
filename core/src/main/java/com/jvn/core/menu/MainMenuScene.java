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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class MainMenuScene implements Scene {
  private static final Logger LOG = LoggerFactory.getLogger(MainMenuScene.class);
  private final Engine engine;
  private final VnSettings settingsModel;
  private final VnSaveManager saveManager;
  private final String defaultScriptName;
  private final AudioFacade audio;
  private int selected = 0;
  
  // Title screen configuration
  private String titleBgmPath = null;
  private double titleBgmVolume = 0.7;
  private boolean bgmStarted = false;

  public MainMenuScene(Engine engine, VnSettings settingsModel, VnSaveManager saveManager, String defaultScriptName, AudioFacade audio) {
    this.engine = engine;
    this.settingsModel = settingsModel;
    this.saveManager = saveManager;
    this.defaultScriptName = defaultScriptName == null ? "demo.vns" : defaultScriptName;
    this.audio = audio;
  }

  /**
   * Configure title screen BGM
   */
  public void setTitleBgm(String path, double volume) {
    this.titleBgmPath = path;
    this.titleBgmVolume = Math.max(0, Math.min(1, volume));
  }

  public String getTitleBgmPath() { return titleBgmPath; }
  public double getTitleBgmVolume() { return titleBgmVolume; }

  public int getSelected() { return selected; }
  public void moveSelection(int delta) {
    int count = 4;
    selected = (selected + delta + count) % count;
  }
  public void setSelected(int idx) {
    int count = 4;
    if (idx < 0) idx = 0;
    if (idx >= count) idx = count - 1;
    selected = idx;
  }

  public void activateSelected() {
    switch (selected) {
      case 0 -> startNewGame();
      case 1 -> engine.scenes().push(new LoadMenuScene(engine, saveManager, defaultScriptName, settingsModel, audio));
      case 2 -> {
        com.jvn.core.input.ActionBindingProfile profile = com.jvn.core.input.ActionBindingProfile.deserialize(settingsModel.getInputProfileSerialized());
        engine.scenes().push(new SettingsScene(settingsModel, audio, profile));
      }
      case 3 -> engine.stop();
    }
  }

  private void startNewGame() {
    VnScenario scenario = loadScenario(defaultScriptName);
    VnScene vnScene = new VnScene(scenario);
    if (audio != null) vnScene.setAudioFacade(audio);
    if (engine != null && engine.getVnInteropFactory() != null) {
      vnScene.setInterop(engine.getVnInteropFactory().create(engine));
    }
    // Apply settings model to scene settings
    VnSettings s = vnScene.getState().getSettings();
    s.setTextSpeed(settingsModel.getTextSpeed());
    s.setBgmVolume(settingsModel.getBgmVolume());
    s.setSfxVolume(settingsModel.getSfxVolume());
    s.setVoiceVolume(settingsModel.getVoiceVolume());
    s.setAutoPlayDelay(settingsModel.getAutoPlayDelay());
    s.setSkipUnreadText(settingsModel.isSkipUnreadText());
    s.setSkipAfterChoices(settingsModel.isSkipAfterChoices());
    s.setPhysicsFixedStepMs(settingsModel.getPhysicsFixedStepMs());
    s.setPhysicsMaxSubSteps(settingsModel.getPhysicsMaxSubSteps());
    s.setPhysicsDefaultFriction(settingsModel.getPhysicsDefaultFriction());
    s.setInputProfilePath(settingsModel.getInputProfilePath());
    s.setInputProfileSerialized(settingsModel.getInputProfileSerialized());
    if (audio != null) {
      audio.setBgmVolume(s.getBgmVolume());
      audio.setSfxVolume(s.getSfxVolume());
      audio.setVoiceVolume(s.getVoiceVolume());
    }
    if (engine != null) {
      engine.setFixedUpdateStepMs(settingsModel.getPhysicsFixedStepMs(), settingsModel.getPhysicsMaxSubSteps());
    }
    engine.scenes().push(vnScene);
  }

  private VnScenario loadScenario(String scriptName) {
    try {
      AssetCatalog assets = new AssetCatalog();
      try (InputStream in = assets.open(AssetType.SCRIPT, scriptName)) {
        VnScriptParser parser = new VnScriptParser();
        return parser.parse(in);
      }
    } catch (Exception e) {
      LOG.warn("Failed to load script '{}', falling back to DemoScenario: {}", scriptName, e.toString());
      return DemoScenario.createSimpleDemo();
    }
  }

  @Override
  public void update(long deltaMs) {
    // Start title BGM on first update if configured
    if (!bgmStarted && titleBgmPath != null && audio != null) {
      audio.setBgmVolume((float) titleBgmVolume);
      audio.playBgm(titleBgmPath, true);
      bgmStarted = true;
    }
  }

  @Override
  public void onEnter() {
    // Resume title BGM if returning to menu
    if (titleBgmPath != null && audio != null) {
      if (!bgmStarted) {
        audio.setBgmVolume((float) titleBgmVolume);
        audio.playBgm(titleBgmPath, true);
        bgmStarted = true;
      }
    }
  }

  @Override
  public void onExit() {
    // Don't stop BGM when pushing new scene - let child scenes control it
  }
}
