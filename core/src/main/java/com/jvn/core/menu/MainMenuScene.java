package com.jvn.core.menu;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.engine.Engine;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.DemoScenario;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;

public class MainMenuScene implements Scene {
  private static final Logger LOG = LoggerFactory.getLogger(MainMenuScene.class);

  private final Engine engine;
  private final VnSettings settingsModel;
  private final VnSaveManager saveManager;
  private final String defaultScriptName;
  private final AudioFacade audio;
  private final String menuId;
  private final MenuProfile menuProfile;
  private final MenuScreenSpec menuScreen;
  private final MenuLayoutSpec menuLayout;
  private final VnScenarioLoader scenarioLoader = new VnScenarioLoader();
  private int selected = 0;

  // Title screen configuration
  private String titleBgmPath = null;
  private double titleBgmVolume = 0.7;
  private boolean bgmStarted = false;

  public MainMenuScene(Engine engine, VnSettings settingsModel, VnSaveManager saveManager, String defaultScriptName, AudioFacade audio) {
    this(engine, settingsModel, saveManager, defaultScriptName, audio, "main");
  }

  public MainMenuScene(Engine engine, VnSettings settingsModel, VnSaveManager saveManager, String defaultScriptName, AudioFacade audio, String menuId) {
    this.engine = engine;
    this.settingsModel = settingsModel == null ? new VnSettings() : settingsModel;
    this.saveManager = saveManager == null ? new VnSaveManager() : saveManager;
    this.defaultScriptName = defaultScriptName == null || defaultScriptName.isBlank() ? "demo.vns" : defaultScriptName;
    this.audio = audio;
    MenuProfileLoader.LoadResult menuLoad = MenuProfileLoader.loadWithDiagnostics();
    this.menuProfile = menuLoad.profile();
    for (String warning : menuLoad.diagnostics()) {
      LOG.warn("Menu profile: {}", warning);
    }
    this.menuId = normalize(menuId, menuProfile.defaultScreenId());
    this.menuScreen = menuProfile.screen(this.menuId);
    this.menuLayout = menuProfile.layout(menuScreen.layoutId());
    this.selected = firstSelectableIndex(0);
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
  public String getMenuId() { return menuId; }
  public MenuProfile getMenuProfile() { return menuProfile; }
  public MenuScreenSpec getMenuScreen() { return menuScreen; }
  public MenuLayoutSpec getMenuLayout() { return menuLayout; }
  public int getItemCount() { return menuScreen.items().size(); }
  public int getSelected() { return selected; }

  public boolean isItemEnabled(int idx) {
    MenuItemSpec item = getItem(idx);
    return item != null && item.enabled();
  }

  public MenuItemSpec getItem(int idx) {
    if (idx < 0 || idx >= getItemCount()) return null;
    return menuScreen.items().get(idx);
  }

  public MenuItemSpec getMenuItemSpec(int idx) {
    return getItem(idx);
  }

  public MenuStyleSpec getStyleForIndex(int idx) {
    MenuItemSpec item = getItem(idx);
    String styleId = item != null ? normalize(item.styleId(), menuScreen.defaultStyleId()) : menuScreen.defaultStyleId();
    return menuProfile.style(styleId);
  }

  public String getDisplayTitle() {
    return resolveDisplayText(menuScreen.titleText());
  }

  public String getDisplayHints() {
    return resolveDisplayText(menuScreen.hintsText());
  }

  public String[] getDisplayItems() {
    List<MenuItemSpec> items = menuScreen.items();
    String[] labels = new String[items.size()];
    for (int i = 0; i < items.size(); i++) {
      labels[i] = displayLabel(items.get(i));
    }
    return labels;
  }

  public void moveSelection(int delta) {
    int count = getItemCount();
    if (count <= 0 || delta == 0) return;
    int steps = Math.abs(delta);
    int dir = delta > 0 ? 1 : -1;
    int next = selected;
    for (int i = 0; i < steps; i++) {
      next = nextSelectable(next, dir);
    }
    selected = next;
  }

  public void setSelected(int idx) {
    int count = getItemCount();
    if (count <= 0) {
      selected = 0;
      return;
    }
    int clamped = Math.max(0, Math.min(idx, count - 1));
    if (isItemEnabled(clamped)) {
      selected = clamped;
      return;
    }
    selected = firstSelectableIndex(clamped);
  }

  public void activateSelected() {
    MenuItemSpec item = getItem(selected);
    if (item == null || !item.enabled()) return;
    MenuActionSpec action = item.action();
    switch (action.type()) {
      case NEW_GAME -> startNewGame(defaultScriptName);
      case RUN_SCRIPT -> startNewGame(normalize(action.target(), defaultScriptName));
      case LOAD_MENU -> {
        String script = normalize(action.target(), defaultScriptName);
        engine.scenes().push(new LoadMenuScene(engine, saveManager, script, settingsModel, audio));
      }
      case SETTINGS_MENU -> {
        com.jvn.core.input.ActionBindingProfile profile =
            com.jvn.core.input.ActionBindingProfile.deserialize(settingsModel.getInputProfileSerialized());
        engine.scenes().push(new SettingsScene(engine, saveManager, defaultScriptName, settingsModel, audio, profile));
      }
      case OPEN_MENU -> openConfiguredMenu(action.target());
      case MAIN_MENU -> openConfiguredMenu("main");
      case BACK -> {
        if (engine != null) engine.scenes().pop();
      }
      case QUIT -> {
        if (engine != null) engine.stop();
      }
      case SAVE_MENU -> LOG.debug("Ignoring save menu action in title context");
      case NOOP -> {
      }
    }
  }

  private void openConfiguredMenu(String targetMenu) {
    String requested = normalize(targetMenu, null);
    if (requested == null || engine == null) return;
    if (!menuProfile.screens().containsKey(requested)) {
      LOG.debug("Configured menu '{}' not found in profile", requested);
      return;
    }
    if (requested.equalsIgnoreCase(menuId)) return;
    MainMenuScene child = new MainMenuScene(engine, settingsModel, saveManager, defaultScriptName, audio, requested);
    if (titleBgmPath != null) {
      child.setTitleBgm(titleBgmPath, titleBgmVolume);
    }
    engine.scenes().push(child);
  }

  private void startNewGame(String scriptName) {
    VnScenario scenario = loadScenario(normalize(scriptName, defaultScriptName));
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
      engine.scenes().push(vnScene);
    }
  }

  private VnScenario loadScenario(String scriptName) {
    try {
      return scenarioLoader.load(scriptName);
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
    if (titleBgmPath != null && audio != null && !bgmStarted) {
      audio.setBgmVolume((float) titleBgmVolume);
      audio.playBgm(titleBgmPath, true);
      bgmStarted = true;
    }
  }

  @Override
  public void onExit() {
    // Don't stop BGM when pushing new scene - let child scenes control it
  }

  private int firstSelectableIndex(int fallback) {
    int count = getItemCount();
    if (count <= 0) return 0;
    int start = Math.max(0, Math.min(fallback, count - 1));
    if (isItemEnabled(start)) return start;
    for (int i = 1; i <= count; i++) {
      int idx = (start + i) % count;
      if (isItemEnabled(idx)) return idx;
    }
    return start;
  }

  private int nextSelectable(int from, int dir) {
    int count = getItemCount();
    if (count <= 0) return 0;
    int start = Math.max(0, Math.min(from, count - 1));
    if (!menuScreen.wrapSelection()) {
      int idx = start;
      while (true) {
        idx += dir;
        if (idx < 0 || idx >= count) return from;
        if (isItemEnabled(idx)) return idx;
      }
    }
    int idx = start;
    for (int i = 0; i < count; i++) {
      idx = (idx + dir + count) % count;
      if (isItemEnabled(idx)) return idx;
    }
    return from;
  }

  private String displayLabel(MenuItemSpec item) {
    if (item == null) return "";
    String explicit = resolveDisplayText(item.label());
    if (explicit != null) return explicit;
    return switch (item.action().type()) {
      case NEW_GAME -> Localization.t("menu.new_game");
      case LOAD_MENU -> Localization.t("menu.load");
      case SETTINGS_MENU -> Localization.t("menu.settings");
      case QUIT -> Localization.t("menu.quit");
      case BACK -> Localization.t("common.back");
      case SAVE_MENU -> Localization.t("save.title");
      case MAIN_MENU -> Localization.t("app.title");
      case RUN_SCRIPT -> Localization.t("menu.new_game");
      case OPEN_MENU -> titleize(normalize(item.action().target(), item.id()));
      case NOOP -> titleize(item.id());
    };
  }

  private String resolveDisplayText(String raw) {
    String value = normalize(raw, null);
    if (value == null) return null;
    if (value.startsWith("i18n:")) {
      String key = value.substring("i18n:".length()).trim();
      if (!key.isEmpty()) return Localization.t(key);
    }
    return value;
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }

  private String titleize(String raw) {
    String s = normalize(raw, "item").replace('_', ' ').replace('-', ' ');
    if (s.isEmpty()) return "item";
    StringBuilder out = new StringBuilder();
    boolean upper = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        upper = true;
        out.append(c);
      } else if (upper) {
        out.append(Character.toUpperCase(c));
        upper = false;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
