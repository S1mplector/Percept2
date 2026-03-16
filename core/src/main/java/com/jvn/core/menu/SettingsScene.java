package com.jvn.core.menu;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.engine.Engine;
import com.jvn.core.input.ActionBindingProfile;
import com.jvn.core.input.ActionBindingProfileStore;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnEntryScriptResolver;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnSettingsStore;
import com.jvn.core.vn.save.VnSaveManager;

public class SettingsScene implements Scene {
  private static final Logger LOG = LoggerFactory.getLogger(SettingsScene.class);
  private static final String KEY_TEXT_SPEED = "text_speed";
  private static final String KEY_BGM_VOLUME = "bgm_volume";
  private static final String KEY_SFX_VOLUME = "sfx_volume";
  private static final String KEY_VOICE_VOLUME = "voice_volume";
  private static final String KEY_AUTO_PLAY_DELAY = "auto_play_delay";
  private static final String KEY_SKIP_UNREAD = "skip_unread";
  private static final String KEY_SKIP_AFTER_CHOICES = "skip_after_choices";
  private static final String KEY_CLICK_REVEAL_BEFORE_ADVANCE = "click_reveal_before_advance";
  private static final String KEY_PHYSICS_FIXED_STEP = "physics_fixed_step";
  private static final String KEY_PHYSICS_MAX_SUBSTEPS = "physics_max_substeps";
  private static final String KEY_PHYSICS_DEFAULT_FRICTION = "physics_default_friction";
  private static final String KEY_INPUT_PROFILE = "input_profile";
  private static final String KEY_BACK = "back";

  private final VnSettings settings;
  private final AudioFacade audio; // optional, to apply volumes live
  private final Engine engine;
  private final VnSaveManager saveManager;
  private final String defaultScriptName;
  private final String menuId;
  private final MenuProfile menuProfile;
  private final MenuScreenSpec menuScreen;
  private final MenuLayoutSpec menuLayout;
  private final VnScenarioLoader scenarioLoader = new VnScenarioLoader();
  private final List<Row> rows;

  private int selected = 0;
  private ActionBindingProfile bindings;
  private String bindingStatus = "";
  private boolean closeRequested = false;
  private String requestedMenuId = null;

  private record Row(
      String id,
      String key,
      String label,
      boolean enabled,
      MenuStyleSpec style,
      MenuActionSpec action
  ) {}

  public SettingsScene(VnSettings settings) {
    this(null, null, null, settings, null, null, null, "settings");
  }

  public SettingsScene(VnSettings settings, AudioFacade audio) {
    this(null, null, null, settings, audio, null, null, "settings");
  }

  public SettingsScene(VnSettings settings, AudioFacade audio, MenuProfile profile, String menuId) {
    this(null, null, null, settings, audio, null, profile, menuId);
  }

  public SettingsScene(VnSettings settings, AudioFacade audio, ActionBindingProfile bindings) {
    this(null, null, null, settings, audio, bindings, null, "settings");
  }

  public SettingsScene(
      Engine engine,
      VnSaveManager saveManager,
      String defaultScriptName,
      VnSettings settings,
      AudioFacade audio
  ) {
    this(engine, saveManager, defaultScriptName, settings, audio, null, null, "settings");
  }

  public SettingsScene(
      Engine engine,
      VnSaveManager saveManager,
      String defaultScriptName,
      VnSettings settings,
      AudioFacade audio,
      ActionBindingProfile bindings
  ) {
    this(engine, saveManager, defaultScriptName, settings, audio, bindings, null, "settings");
  }

  SettingsScene(
      Engine engine,
      VnSaveManager saveManager,
      String defaultScriptName,
      VnSettings settings,
      AudioFacade audio,
      ActionBindingProfile bindings,
      MenuProfile profile
  ) {
    this(engine, saveManager, defaultScriptName, settings, audio, bindings, profile, "settings");
  }

  public SettingsScene(
      Engine engine,
      VnSaveManager saveManager,
      String defaultScriptName,
      VnSettings settings,
      AudioFacade audio,
      ActionBindingProfile bindings,
      String menuId
  ) {
    this(engine, saveManager, defaultScriptName, settings, audio, bindings, null, menuId);
  }

  SettingsScene(
      Engine engine,
      VnSaveManager saveManager,
      String defaultScriptName,
      VnSettings settings,
      AudioFacade audio,
      ActionBindingProfile bindings,
      MenuProfile profile,
      String menuId
  ) {
    this.engine = engine;
    this.saveManager = saveManager == null ? new VnSaveManager() : saveManager;
    String resolvedDefault = VnEntryScriptResolver.resolveEntryScript(defaultScriptName, null);
    this.defaultScriptName = normalize(resolvedDefault, "story/prologue.vns");
    this.settings = settings == null ? new VnSettings() : settings;
    this.audio = audio;
    if (bindings != null) {
      this.bindings = bindings;
    } else if (this.settings.getInputProfileSerialized() != null && !this.settings.getInputProfileSerialized().isBlank()) {
      this.bindings = ActionBindingProfile.deserialize(this.settings.getInputProfileSerialized());
    } else {
      this.bindings = new ActionBindingProfile();
    }
    if (profile == null) {
      MenuProfileLoader.LoadResult menuLoad = MenuProfileLoader.loadWithDiagnostics();
      this.menuProfile = menuLoad.profile();
      for (String warning : menuLoad.diagnostics()) {
        LOG.warn("Menu profile: {}", warning);
      }
    } else {
      this.menuProfile = profile;
    }
    String requestedMenu = normalize(menuId, "settings");
    if (!this.menuProfile.hasScreen(requestedMenu)) {
      LOG.warn("Settings menu '{}' is not defined in menu profile; using 'settings'", requestedMenu);
      requestedMenu = "settings";
    }
    this.menuId = requestedMenu;
    this.menuScreen = menuProfile.screen(this.menuId);
    this.menuLayout = menuProfile.layout(menuScreen.layoutId());
    this.rows = buildRows();
    this.selected = firstSelectableIndex(0);
  }

  public VnSettings model() { return settings; }
  public String getMenuId() { return menuId; }
  public int itemCount() { return rows.size(); }
  public int getSelected() { return selected; }
  public MenuLayoutSpec getMenuLayout() { return menuLayout; }
  public MenuScreenSpec getMenuScreen() { return menuScreen; }
  public MenuStyleSpec getDefaultMenuStyle() { return menuProfile.style(menuScreen.defaultStyleId()); }
  public String consumeRequestedMenuId() {
    String requested = requestedMenuId;
    requestedMenuId = null;
    return requested;
  }

  public MenuStyleSpec getStyleForIndex(int idx) {
    Row r = rowAt(idx);
    if (r == null || r.style() == null) return menuProfile.style(menuScreen.defaultStyleId());
    return r.style();
  }

  public MenuItemSpec getMenuItemSpec(int idx) {
    Row row = rowAt(idx);
    if (row == null) return null;
    for (MenuItemSpec item : menuScreen.items()) {
      if (item != null && item.id().equalsIgnoreCase(row.id())) {
        return item;
      }
    }
    return null;
  }

  public boolean isItemEnabled(int idx) {
    Row r = rowAt(idx);
    return r != null && r.enabled();
  }

  public String getDisplayTitle() {
    String t = resolveDisplayText(menuScreen.titleText());
    return t != null ? t : Localization.t("settings.title");
  }

  public String getDisplaySubtitle() {
    return resolveDisplayText(menuScreen.subtitleText());
  }

  public String getDisplayHints() {
    String t = resolveDisplayText(menuScreen.hintsText());
    if (t == null) {
      return "Up/Down, Left/Right, Enter • " + Localization.t("common.back") + ": Esc";
    }
    return t;
  }

  public String[] getDisplayItems() {
    String[] out = new String[rows.size()];
    for (int i = 0; i < rows.size(); i++) {
      Row r = rows.get(i);
      // Preserve explicit blank labels for decorative rows in menu profiles.
      // Only synthesize fallback labels when label is truly unspecified.
      String label = (r.label() != null) ? r.label() : defaultLabelForKey(r.key());
      String value = valueTextForKey(r.key());
      String text = applyValueTemplate(label, value);
      if (KEY_INPUT_PROFILE.equals(r.key()) && bindingStatus != null && !bindingStatus.isBlank()) {
        text += " • " + bindingStatus;
      }
      out[i] = text;
    }
    return out;
  }

  public boolean hasSliderAt(int idx) {
    Row r = rowAt(idx);
    if (r == null) return false;
    return switch (r.key()) {
      case KEY_TEXT_SPEED,
           KEY_BGM_VOLUME,
           KEY_SFX_VOLUME,
           KEY_VOICE_VOLUME,
           KEY_AUTO_PLAY_DELAY,
           KEY_PHYSICS_FIXED_STEP,
           KEY_PHYSICS_MAX_SUBSTEPS,
           KEY_PHYSICS_DEFAULT_FRICTION -> true;
      default -> false;
    };
  }

  public boolean hasToggleAt(int idx) {
    Row r = rowAt(idx);
    if (r == null) return false;
    return switch (r.key()) {
      case KEY_SKIP_UNREAD,
           KEY_SKIP_AFTER_CHOICES,
           KEY_CLICK_REVEAL_BEFORE_ADVANCE -> true;
      default -> false;
    };
  }

  public double sliderValue01At(int idx) {
    Row r = rowAt(idx);
    if (r == null) return 0;
    return switch (r.key()) {
      case KEY_TEXT_SPEED -> clamp01((settings.getTextSpeed() - 10.0) / (120.0 - 10.0));
      case KEY_BGM_VOLUME -> clamp01(settings.getBgmVolume());
      case KEY_SFX_VOLUME -> clamp01(settings.getSfxVolume());
      case KEY_VOICE_VOLUME -> clamp01(settings.getVoiceVolume());
      case KEY_AUTO_PLAY_DELAY -> clamp01((settings.getAutoPlayDelay() - 500.0) / (5000.0 - 500.0));
      case KEY_PHYSICS_FIXED_STEP -> clamp01(settings.getPhysicsFixedStepMs() / 50.0);
      case KEY_PHYSICS_MAX_SUBSTEPS -> clamp01((settings.getPhysicsMaxSubSteps() - 1) / 7.0);
      case KEY_PHYSICS_DEFAULT_FRICTION -> clamp01(settings.getPhysicsDefaultFriction());
      default -> 0.0;
    };
  }

  public boolean toggleValueAt(int idx) {
    Row r = rowAt(idx);
    if (r == null) return false;
    return switch (r.key()) {
      case KEY_SKIP_UNREAD -> settings.isSkipUnreadText();
      case KEY_SKIP_AFTER_CHOICES -> settings.isSkipAfterChoices();
      case KEY_CLICK_REVEAL_BEFORE_ADVANCE -> settings.isClickRevealBeforeAdvance();
      default -> false;
    };
  }

  public void moveSelection(int delta) {
    int count = itemCount();
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
    int count = itemCount();
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

  public void adjustCurrent(int delta) {
    Row row = rowAt(selected);
    if (row == null || !row.enabled()) return;
    if (handleAction(row, delta, false)) {
      applyLiveVolumes();
      return;
    }

    switch (row.key()) {
      case KEY_TEXT_SPEED -> settings.setTextSpeed(settings.getTextSpeed() + delta);
      case KEY_BGM_VOLUME -> settings.setBgmVolume(settings.getBgmVolume() + delta * 0.05f);
      case KEY_SFX_VOLUME -> settings.setSfxVolume(settings.getSfxVolume() + delta * 0.05f);
      case KEY_VOICE_VOLUME -> settings.setVoiceVolume(settings.getVoiceVolume() + delta * 0.05f);
      case KEY_AUTO_PLAY_DELAY -> settings.setAutoPlayDelay(settings.getAutoPlayDelay() + delta * 100L);
      case KEY_SKIP_UNREAD -> settings.setSkipUnreadText(!settings.isSkipUnreadText());
      case KEY_SKIP_AFTER_CHOICES -> settings.setSkipAfterChoices(!settings.isSkipAfterChoices());
      case KEY_CLICK_REVEAL_BEFORE_ADVANCE -> settings.setClickRevealBeforeAdvance(!settings.isClickRevealBeforeAdvance());
      case KEY_PHYSICS_FIXED_STEP -> settings.setPhysicsFixedStepMs(Math.max(0, settings.getPhysicsFixedStepMs() + delta * 5));
      case KEY_PHYSICS_MAX_SUBSTEPS -> settings.setPhysicsMaxSubSteps(Math.max(1, settings.getPhysicsMaxSubSteps() + delta));
      case KEY_PHYSICS_DEFAULT_FRICTION -> settings.setPhysicsDefaultFriction(settings.getPhysicsDefaultFriction() + delta * 0.05);
      case KEY_INPUT_PROFILE -> {
        if (delta > 0) saveBindingsToDisk();
        else loadBindingsFromDisk();
      }
      case KEY_BACK -> closeRequested = true;
      default -> {
      }
    }
    applyLiveVolumes();
  }

  public void toggleCurrent() {
    Row row = rowAt(selected);
    if (row == null || !row.enabled()) return;
    if (handleAction(row, 0, true)) {
      applyLiveVolumes();
      return;
    }

    switch (row.key()) {
      case KEY_SKIP_UNREAD -> settings.setSkipUnreadText(!settings.isSkipUnreadText());
      case KEY_SKIP_AFTER_CHOICES -> settings.setSkipAfterChoices(!settings.isSkipAfterChoices());
      case KEY_CLICK_REVEAL_BEFORE_ADVANCE -> settings.setClickRevealBeforeAdvance(!settings.isClickRevealBeforeAdvance());
      case KEY_INPUT_PROFILE -> loadBindingsFromDisk();
      case KEY_BACK -> closeRequested = true;
      default -> {
      }
    }
    applyLiveVolumes();
  }

  public void setValueByIndex(int idx, double value01) {
    Row row = rowAt(idx);
    if (row == null || !row.enabled()) return;

    if (handleAction(row, value01 > 0.5 ? 1 : -1, false)) {
      applyLiveVolumes();
      return;
    }

    double v = clamp01(value01);
    switch (row.key()) {
      case KEY_TEXT_SPEED -> {
        int min = 10, max = 120;
        int val = (int) Math.round(min + v * (max - min));
        settings.setTextSpeed(val);
      }
      case KEY_BGM_VOLUME -> settings.setBgmVolume((float) v);
      case KEY_SFX_VOLUME -> settings.setSfxVolume((float) v);
      case KEY_VOICE_VOLUME -> settings.setVoiceVolume((float) v);
      case KEY_AUTO_PLAY_DELAY -> {
        long min = 500, max = 5000;
        long val = Math.round(min + v * (max - min));
        settings.setAutoPlayDelay(val);
      }
      case KEY_SKIP_UNREAD -> settings.setSkipUnreadText(v >= 0.5);
      case KEY_SKIP_AFTER_CHOICES -> settings.setSkipAfterChoices(v >= 0.5);
      case KEY_CLICK_REVEAL_BEFORE_ADVANCE -> settings.setClickRevealBeforeAdvance(v >= 0.5);
      case KEY_PHYSICS_FIXED_STEP -> settings.setPhysicsFixedStepMs(Math.round(v * 50));
      case KEY_PHYSICS_MAX_SUBSTEPS -> settings.setPhysicsMaxSubSteps(1 + (int) Math.round(v * 7));
      case KEY_PHYSICS_DEFAULT_FRICTION -> settings.setPhysicsDefaultFriction(v);
      case KEY_INPUT_PROFILE -> {
        if (v > 0.5) saveBindingsToDisk(); else loadBindingsFromDisk();
      }
      case KEY_BACK -> {
        if (v > 0.5) closeRequested = true;
      }
      default -> {
      }
    }
    applyLiveVolumes();
  }

  public void resetValueByIndex(int idx) {
    Row row = rowAt(idx);
    if (row == null || !row.enabled()) return;

    VnSettings defaults = new VnSettings();
    switch (row.key()) {
      case KEY_TEXT_SPEED -> settings.setTextSpeed(defaults.getTextSpeed());
      case KEY_BGM_VOLUME -> settings.setBgmVolume(defaults.getBgmVolume());
      case KEY_SFX_VOLUME -> settings.setSfxVolume(defaults.getSfxVolume());
      case KEY_VOICE_VOLUME -> settings.setVoiceVolume(defaults.getVoiceVolume());
      case KEY_AUTO_PLAY_DELAY -> settings.setAutoPlayDelay(defaults.getAutoPlayDelay());
      case KEY_SKIP_UNREAD -> settings.setSkipUnreadText(defaults.isSkipUnreadText());
      case KEY_SKIP_AFTER_CHOICES -> settings.setSkipAfterChoices(defaults.isSkipAfterChoices());
      case KEY_CLICK_REVEAL_BEFORE_ADVANCE -> settings.setClickRevealBeforeAdvance(defaults.isClickRevealBeforeAdvance());
      case KEY_PHYSICS_FIXED_STEP -> settings.setPhysicsFixedStepMs(defaults.getPhysicsFixedStepMs());
      case KEY_PHYSICS_MAX_SUBSTEPS -> settings.setPhysicsMaxSubSteps(defaults.getPhysicsMaxSubSteps());
      case KEY_PHYSICS_DEFAULT_FRICTION -> settings.setPhysicsDefaultFriction(defaults.getPhysicsDefaultFriction());
      default -> {
      }
    }
    applyLiveVolumes();
  }

  public boolean consumeCloseRequested() {
    boolean out = closeRequested;
    closeRequested = false;
    return out;
  }

  @Override
  public void update(long deltaMs) { }

  @Override
  public void onExit() {
    try {
      new VnSettingsStore().save(settings);
    } catch (Exception ignored) {}
  }

  public String getBindingStatus() { return bindingStatus; }

  public void loadBindingsFromDisk() {
    ActionBindingProfileStore store = new ActionBindingProfileStore(settings.getInputProfilePath());
    bindings = store.load();
    settings.setInputProfileSerialized(bindings.serialize());
    bindingStatus = "Loaded from " + store.getPath();
  }

  public ActionBindingProfile getBindings() { return bindings; }

  private void saveBindingsToDisk() {
    ActionBindingProfileStore store = new ActionBindingProfileStore(settings.getInputProfilePath());
    try {
      store.save(bindings);
      settings.setInputProfileSerialized(bindings.serialize());
      bindingStatus = "Saved to " + store.getPath();
    } catch (Exception e) {
      bindingStatus = "Save failed";
    }
  }

  private List<Row> buildRows() {
    List<Row> out = new ArrayList<>();
    if (!menuScreen.items().isEmpty()) {
      for (MenuItemSpec item : menuScreen.items()) {
        if (item == null) continue;
        String id = normalize(item.id(), "item");
        MenuActionSpec action = item.action() == null ? MenuActionSpec.noop() : item.action();
        String key = canonicalKey(action.target() != null ? action.target() : id);
        MenuStyleSpec style = menuProfile.style(normalize(item.styleId(), menuScreen.defaultStyleId()));
        String label = resolveItemLabelText(item.label());
        out.add(new Row(id, key, label, item.enabled(), style, action));
      }
    }
    if (!out.isEmpty()) {
      return out;
    }

    MenuStyleSpec style = menuProfile.style(menuScreen.defaultStyleId());
    // Text & reading
    out.add(defaultRow(KEY_TEXT_SPEED, style));
    out.add(defaultRow(KEY_AUTO_PLAY_DELAY, style));
    out.add(defaultRow(KEY_CLICK_REVEAL_BEFORE_ADVANCE, style));
    out.add(defaultRow(KEY_SKIP_UNREAD, style));
    out.add(defaultRow(KEY_SKIP_AFTER_CHOICES, style));
    // Audio
    out.add(defaultRow(KEY_BGM_VOLUME, style));
    out.add(defaultRow(KEY_SFX_VOLUME, style));
    out.add(defaultRow(KEY_VOICE_VOLUME, style));
    // Navigation
    out.add(defaultRow(KEY_BACK, style));
    return out;
  }

  private Row defaultRow(String key, MenuStyleSpec style) {
    return new Row(key, key, null, true, style, MenuActionSpec.noop());
  }

  private void ensureBuiltInSettingVisible(List<Row> rows, String key, MenuStyleSpec style) {
    for (Row row : rows) {
      if (row != null && key.equals(row.key())) return;
    }
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      if (row != null && KEY_BACK.equals(row.key())) {
        rows.add(i, defaultRow(key, style));
        return;
      }
    }
    rows.add(defaultRow(key, style));
  }

  private boolean handleAction(Row row, int delta, boolean confirm) {
    MenuActionSpec action = row.action();
    if (action == null) return false;
    if (handleCustomMenuAction(action, row.id(), confirm)) {
      return true;
    }
    return switch (action.type()) {
      case BACK -> {
        closeRequested = true;
        yield true;
      }
      case QUIT -> {
        if (engine != null) {
          if (!openQuitConfirmationMenu(action.target())) {
            engine.stop();
          }
        } else {
          closeRequested = true;
        }
        yield true;
      }
      case NEW_GAME -> {
        startNewGame(defaultScriptName);
        yield true;
      }
      case RUN_SCRIPT -> {
        startNewGame(normalize(action.target(), defaultScriptName));
        yield true;
      }
      case LOAD_MENU -> {
        if (engine != null) {
          String script = normalize(action.target(), defaultScriptName);
          engine.scenes().push(new LoadMenuScene(engine, saveManager, script, settings, audio));
        } else if (confirm) {
          bindingStatus = "Load menu unavailable in standalone settings";
        }
        yield true;
      }
      case SAVE_MENU -> {
        if (engine != null) {
          com.jvn.core.scene.Scene current = engine.scenes().peek();
          if (current instanceof VnScene vnScene) {
            engine.scenes().push(new SaveMenuScene(engine, saveManager, vnScene, defaultScriptName));
          } else if (confirm) {
            bindingStatus = "Save menu requires an active VN scene";
          }
        } else if (confirm) {
          bindingStatus = "Save menu unavailable in standalone settings";
        }
        yield true;
      }
      case OPEN_MENU -> {
        boolean opened = openConfiguredMenu(action.target());
        if (!opened && confirm) {
          bindingStatus = "Menu not found: " + normalize(action.target(), "(empty)");
        }
        yield true;
      }
      case MAIN_MENU -> {
        boolean opened = openConfiguredMenu("main");
        if (!opened && confirm) bindingStatus = "Main menu not found";
        yield true;
      }
      case NOOP -> false;
      case SETTINGS_MENU -> {
        boolean opened = openSettingsMenu(action.target());
        if (!opened && confirm) {
          bindingStatus = "Settings menu not found: " + normalize(action.target(), "settings");
        }
        yield true;
      }
      default -> {
        if (confirm) {
          bindingStatus = "Unsupported settings action: " + action.type().name().toLowerCase();
        }
        yield true;
      }
    };
  }

  private boolean handleCustomMenuAction(MenuActionSpec action, String itemId, boolean confirm) {
    if (action == null || !action.isCustomAction() || engine == null) return false;
    var handler = engine.getMenuActionHandler();
    if (handler == null) return false;
    try {
      return handler.handle(new MenuActionContext(
          engine,
          menuId,
          normalize(itemId, ""),
          defaultScriptName,
          action
      ));
    } catch (Exception ex) {
      LOG.warn("Custom menu action '{}' failed in settings menu", action.actionKey(), ex);
      if (confirm) {
        bindingStatus = "Custom action failed: " + action.actionKey();
      }
      return false;
    }
  }

  private boolean openConfiguredMenu(String targetMenu) {
    String requested = normalize(targetMenu, null);
    if (requested == null) return false;
    if (engine == null) {
      closeRequested = true;
      return true;
    }
    if (!menuProfile.screens().containsKey(requested)) {
      LOG.debug("Configured menu '{}' not found in profile", requested);
      return false;
    }
    MainMenuScene child = new MainMenuScene(engine, settings, saveManager, defaultScriptName, audio, requested);
    engine.scenes().push(child);
    return true;
  }

  private boolean openSettingsMenu(String targetMenu) {
    String requested = normalize(targetMenu, "settings");
    if (requested == null) return false;
    if (!menuProfile.hasScreen(requested)) {
      LOG.debug("Configured settings menu '{}' not found in profile", requested);
      return false;
    }
    if (requested.equalsIgnoreCase(menuId)) return true;
    if (engine == null) {
      requestedMenuId = requested;
      return true;
    }
    SettingsScene child = new SettingsScene(
        engine,
        saveManager,
        defaultScriptName,
        settings,
        audio,
        bindings,
        menuProfile,
        requested
    );
    engine.scenes().replace(child);
    return true;
  }

  private boolean openQuitConfirmationMenu(String targetMenu) {
    String requested = normalize(targetMenu, null);
    if (requested == null && menuProfile.screens().containsKey("confirm_exit")) {
      requested = "confirm_exit";
    }
    if (requested == null || requested.isBlank() || requested.equalsIgnoreCase(menuId)) return false;
    return openConfiguredMenu(requested);
  }

  private void startNewGame(String scriptName) {
    if (engine == null) return;
    String resolvedScript = normalize(scriptName, defaultScriptName);
    VnScenario scenario = loadScenario(resolvedScript);
    VnScene vnScene = new VnScene(scenario);
    vnScene.getState().setSourceScriptName(resolvedScript);
    if (audio != null) vnScene.setAudioFacade(audio);
    if (engine.getVnInteropFactory() != null) {
      vnScene.setInterop(engine.getVnInteropFactory().create(engine));
    }
    VnSettings s = vnScene.getState().getSettings();
    s.setTextSpeed(settings.getTextSpeed());
    s.setBgmVolume(settings.getBgmVolume());
    s.setSfxVolume(settings.getSfxVolume());
    s.setVoiceVolume(settings.getVoiceVolume());
    s.setAutoPlayDelay(settings.getAutoPlayDelay());
    s.setSkipUnreadText(settings.isSkipUnreadText());
    s.setSkipAfterChoices(settings.isSkipAfterChoices());
    s.setClickRevealBeforeAdvance(settings.isClickRevealBeforeAdvance());
    s.setPhysicsFixedStepMs(settings.getPhysicsFixedStepMs());
    s.setPhysicsMaxSubSteps(settings.getPhysicsMaxSubSteps());
    s.setPhysicsDefaultFriction(settings.getPhysicsDefaultFriction());
    s.setInputProfilePath(settings.getInputProfilePath());
    s.setInputProfileSerialized(settings.getInputProfileSerialized());
    if (audio != null) {
      audio.setBgmVolume(s.getBgmVolume());
      audio.setSfxVolume(s.getSfxVolume());
      audio.setVoiceVolume(s.getVoiceVolume());
    }
    engine.setFixedUpdateStepMs(settings.getPhysicsFixedStepMs(), settings.getPhysicsMaxSubSteps());
    engine.scenes().push(vnScene);
  }

  private VnScenario loadScenario(String scriptName) {
    try {
      return scenarioLoader.load(scriptName);
    } catch (Exception e) {
      LOG.warn("Failed to load script '{}': {}", scriptName, e.toString());
      return MenuScenarioFallbacks.missingScriptScenario(scriptName, e);
    }
  }

  private int firstSelectableIndex(int fallback) {
    int count = itemCount();
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
    int count = itemCount();
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

  private Row rowAt(int idx) {
    if (idx < 0 || idx >= rows.size()) return null;
    return rows.get(idx);
  }

  private void applyLiveVolumes() {
    if (audio != null) {
      audio.setBgmVolume(settings.getBgmVolume());
      audio.setSfxVolume(settings.getSfxVolume());
      audio.setVoiceVolume(settings.getVoiceVolume());
    }
  }

  private String defaultLabelForKey(String key) {
    return switch (key) {
      case KEY_TEXT_SPEED -> fallbackLocalized("settings.text_speed", "Text Speed");
      case KEY_BGM_VOLUME -> fallbackLocalized("settings.bgm_volume", "Music");
      case KEY_SFX_VOLUME -> fallbackLocalized("settings.sfx_volume", "Sound Effects");
      case KEY_VOICE_VOLUME -> fallbackLocalized("settings.voice_volume", "Voices");
      case KEY_AUTO_PLAY_DELAY -> fallbackLocalized("settings.auto_play_delay", "Auto-Advance");
      case KEY_SKIP_UNREAD -> fallbackLocalized("settings.skip_unread", "Skip Unread");
      case KEY_SKIP_AFTER_CHOICES -> fallbackLocalized("settings.skip_after_choices", "Skip After Choices");
      case KEY_CLICK_REVEAL_BEFORE_ADVANCE -> fallbackLocalized("settings.click_reveal_before_advance", "Click to Reveal");
      case KEY_PHYSICS_FIXED_STEP -> "Physics: Fixed Step";
      case KEY_PHYSICS_MAX_SUBSTEPS -> "Physics: Max Substeps";
      case KEY_PHYSICS_DEFAULT_FRICTION -> "Physics: Friction";
      case KEY_INPUT_PROFILE -> "Input Profile";
      case KEY_BACK -> fallbackLocalized("common.back", "Back");
      default -> titleize(key);
    };
  }

  private static String fallbackLocalized(String i18nKey, String fallback) {
    String t = Localization.t(i18nKey);
    return (t == null || t.isBlank() || t.equals(i18nKey)) ? fallback : t;
  }

  private String valueTextForKey(String key) {
    return switch (key) {
      case KEY_TEXT_SPEED -> settings.getTextSpeed() + " ms";
      case KEY_BGM_VOLUME -> toPct(settings.getBgmVolume());
      case KEY_SFX_VOLUME -> toPct(settings.getSfxVolume());
      case KEY_VOICE_VOLUME -> toPct(settings.getVoiceVolume());
      case KEY_AUTO_PLAY_DELAY -> formatDelay(settings.getAutoPlayDelay());
      case KEY_SKIP_UNREAD -> settings.isSkipUnreadText() ? "On" : "Off";
      case KEY_SKIP_AFTER_CHOICES -> settings.isSkipAfterChoices() ? "On" : "Off";
      case KEY_CLICK_REVEAL_BEFORE_ADVANCE -> settings.isClickRevealBeforeAdvance() ? "On" : "Off";
      case KEY_PHYSICS_FIXED_STEP -> settings.getPhysicsFixedStepMs() + " ms";
      case KEY_PHYSICS_MAX_SUBSTEPS -> Integer.toString(settings.getPhysicsMaxSubSteps());
      case KEY_PHYSICS_DEFAULT_FRICTION -> toPct((float) settings.getPhysicsDefaultFriction());
      case KEY_INPUT_PROFILE -> "Save/Load";
      default -> null;
    };
  }

  private static String formatDelay(long delayMs) {
    if (delayMs < 1000) return delayMs + " ms";
    double seconds = delayMs / 1000.0;
    if (seconds == Math.floor(seconds)) return (int) seconds + " s";
    return String.format("%.1f s", seconds);
  }

  private String applyValueTemplate(String label, String value) {
    if (label == null) label = "";
    if (value == null || value.isBlank()) return label;
    if (label.contains("{value}")) return label.replace("{value}", value);
    if (label.contains("%value%")) return label.replace("%value%", value);
    return label + ": " + value;
  }

  private String resolveDisplayText(String raw) {
    if (raw == null) return null;
    String value = raw.trim();
    if (value.isEmpty()) return "";
    if (value.startsWith("i18n:")) {
      String key = value.substring("i18n:".length()).trim();
      if (!key.isEmpty()) return Localization.t(key);
    }
    return value;
  }

  private String resolveItemLabelText(String raw) {
    if (raw == null) return null;
    if (raw.isBlank()) return "";
    return resolveDisplayText(raw);
  }

  private String canonicalKey(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String v = raw.trim().toLowerCase().replace('-', '_').replace(' ', '_');
    return switch (v) {
      case "textspeed", "text_speed", "dialogue_speed" -> KEY_TEXT_SPEED;
      case "bgm", "music", "bgm_volume", "music_volume" -> KEY_BGM_VOLUME;
      case "sfx", "sfx_volume", "sound_volume" -> KEY_SFX_VOLUME;
      case "voice", "voice_volume" -> KEY_VOICE_VOLUME;
      case "autodelay", "auto_delay", "autoplay_delay", "auto_play_delay" -> KEY_AUTO_PLAY_DELAY;
      case "skipunread", "skip_unread" -> KEY_SKIP_UNREAD;
      case "skip_after_choices", "skipchoices", "skip_choices" -> KEY_SKIP_AFTER_CHOICES;
      case "click_reveal_before_advance", "click_reveal_first", "reveal_before_advance", "click_reveal" -> KEY_CLICK_REVEAL_BEFORE_ADVANCE;
      case "physics_fixed_step", "fixed_step", "fixed_step_ms" -> KEY_PHYSICS_FIXED_STEP;
      case "physics_max_substeps", "max_substeps", "max_steps" -> KEY_PHYSICS_MAX_SUBSTEPS;
      case "physics_default_friction", "physics_friction", "default_friction", "friction" -> KEY_PHYSICS_DEFAULT_FRICTION;
      case "input", "input_profile", "bindings", "input_bindings" -> KEY_INPUT_PROFILE;
      case "back", "close", "return" -> KEY_BACK;
      default -> v;
    };
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

  private String toPct(float v) {
    int pct = Math.round(v * 100f);
    return pct + "%";
  }

  private double clamp01(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
    if (v < 0) return 0;
    if (v > 1) return 1;
    return v;
  }

  private String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
