package com.jvn.core.menu;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.engine.Engine;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.DemoScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.save.VnSaveData;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.vn.script.VnScriptParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoadMenuScene implements Scene {
  private static final Logger LOG = LoggerFactory.getLogger(LoadMenuScene.class);
  private final Engine engine;
  private final VnSaveManager saveManager;
  private final String defaultScriptName;
  private final AudioFacade audio;
  private final MenuProfile menuProfile;
  private final MenuScreenSpec menuScreen;
  private final MenuLayoutSpec menuLayout;
  private final List<String> saves = new ArrayList<>();
  private int selected = 0;

  public LoadMenuScene(Engine engine, VnSaveManager saveManager, String defaultScriptName, com.jvn.core.vn.VnSettings settingsModel, AudioFacade audio) {
    this.engine = engine;
    this.saveManager = saveManager;
    this.defaultScriptName = defaultScriptName == null ? "demo.vns" : defaultScriptName;
    this.audio = audio;
    MenuProfileLoader.LoadResult menuLoad = MenuProfileLoader.loadWithDiagnostics();
    this.menuProfile = menuLoad.profile();
    for (String warning : menuLoad.diagnostics()) {
      LOG.warn("Menu profile: {}", warning);
    }
    this.menuScreen = menuProfile.screen("load");
    this.menuLayout = menuProfile.layout(menuScreen.layoutId());
    refresh();
  }

  public Long getSelectedTimestamp() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      VnSaveData data = saveManager.load(name);
      return data.getSaveTimestamp();
    } catch (Exception ignored) {
      return null;
    }
  }

  public String getSelectedScenarioId() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      VnSaveData data = saveManager.load(name);
      return data.getScenarioId();
    } catch (Exception ignored) {
      return null;
    }
  }

  public Integer getSelectedNodeIndex() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      VnSaveData data = saveManager.load(name);
      return data.getCurrentNodeIndex();
    } catch (Exception ignored) {
      return null;
    }
  }

  public void refresh() {
    saves.clear();
    List<String> names = new ArrayList<>(saveManager.listSaves());
    try {
      var times = new HashMap<String, Long>();
      for (String n : names) {
        try {
          VnSaveData d = saveManager.load(n);
          times.put(n, d.getSaveTimestamp());
        } catch (Exception e) {
          times.put(n, 0L);
        }
      }
      names.sort(Comparator.comparing((String n) -> times.getOrDefault(n, 0L)).reversed());
    } catch (Exception e) {
      // ignore sort issues, fall back to unsorted
    }
    saves.addAll(names);
    if (selected >= saves.size()) selected = Math.max(0, saves.size() - 1);
  }

  public List<String> getSaves() { return saves; }
  public int getSelected() { return selected; }
  public int getItemCount() { return saves.size(); }
  public MenuLayoutSpec getMenuLayout() { return menuLayout; }

  public MenuStyleSpec getStyleForIndex(int idx) {
    if (idx < 0) return menuProfile.style(menuScreen.defaultStyleId());
    if (idx < menuScreen.items().size()) {
      var item = menuScreen.items().get(idx);
      String styleId = (item != null && item.styleId() != null && !item.styleId().isBlank())
          ? item.styleId()
          : menuScreen.defaultStyleId();
      return menuProfile.style(styleId);
    }
    MenuStyleSpec slotStyle = styleForItemId("save_slot", "slot", "entry");
    if (slotStyle != null) return slotStyle;
    return menuProfile.style(menuScreen.defaultStyleId());
  }

  public String getDisplayTitle() {
    String t = resolveDisplayText(menuScreen.titleText());
    return (t == null || t.isBlank()) ? Localization.t("load.title") : t;
  }

  public String getDisplayHints() {
    String t = resolveDisplayText(menuScreen.hintsText());
    if (t == null || t.isBlank()) {
      return Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
          + Localization.t("load.delete") + ": Delete    " + Localization.t("load.rename") + ": R";
    }
    return t;
  }

  public boolean wrapsSelection() { return menuScreen.wrapSelection(); }

  public void moveSelection(int delta) {
    if (saves.isEmpty()) return;
    int count = saves.size();
    if (menuScreen.wrapSelection()) {
      selected = (selected + delta + count) % count;
    } else {
      selected = Math.max(0, Math.min(selected + delta, count - 1));
    }
  }
  public void setSelected(int idx) {
    int count = saves.size();
    if (count <= 0) { selected = 0; return; }
    selected = Math.max(0, Math.min(idx, count - 1));
  }

  public MenuActionSpec getSelectedAction() {
    MenuActionSpec action = actionForItemId("save_slot", "slot", "entry");
    return action != null ? action : new MenuActionSpec(MenuActionType.LOAD_MENU, null);
  }

  public MenuItemSpec getMenuItemSpec(int idx) {
    if (idx < 0) return null;
    if (idx < menuScreen.items().size()) {
      return menuScreen.items().get(idx);
    }
    MenuItemSpec template = itemForItemId("save_slot", "slot", "entry");
    if (template != null) return template;
    return null;
  }

  public boolean activateSelected() {
    MenuActionSpec action = getSelectedAction();
    return switch (action.type()) {
      case LOAD_MENU -> {
        loadSelected();
        yield true;
      }
      case BACK -> {
        if (engine != null) {
          engine.scenes().pop();
        }
        yield true;
      }
      case QUIT -> {
        if (engine != null) {
          engine.stop();
        }
        yield true;
      }
      case NOOP -> true;
      default -> {
        loadSelected();
        yield true;
      }
    };
  }

  public String getSaveDirectory() { return saveManager.getSaveDirectory(); }
  public String getSelectedName() { return (saves.isEmpty() ? null : saves.get(selected)); }
  public String getSelectedRpgSummary() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      VnSaveData data = saveManager.load(name);
      return summarizeRpgState(data.getRpgState());
    } catch (Exception ignored) { return null; }
  }

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
      String scenId = data.getScenarioId();
      String script = scenId != null ? resolveScriptForScenarioId(scenId) : null;
      VnScenario scen = loadScenario(script != null ? script : defaultScriptName);
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
      String scenId = data.getScenarioId();
      String script = scenId != null ? resolveScriptForScenarioId(scenId) : null;
      if (script == null) {
        LOG.warn("Could not resolve script for scenarioId={}, falling back to {}", scenId, defaultScriptName);
      }
      VnScenario scenario = loadScenario(script != null ? script : defaultScriptName);
      VnScene scene = new VnScene(scenario);
      if (audio != null) scene.setAudioFacade(audio);
      if (engine != null && engine.getVnInteropFactory() != null) {
        scene.setInterop(engine.getVnInteropFactory().create(engine));
      }
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

  private String summarizeRpgState(Object rpg) {
    if (rpg instanceof com.jvn.core.rpg.RpgState state) {
      int party = state.getActors().size();
      double totalHp = state.getActors().values().stream().mapToDouble(com.jvn.core.rpg.RpgStats::getHp).sum();
      double totalMax = state.getActors().values().stream().mapToDouble(com.jvn.core.rpg.RpgStats::getMaxHp).sum();
      return "Party " + party + " • HP " + Math.round(totalHp) + "/" + Math.round(totalMax);
    }
    return null;
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

  private String resolveScriptForScenarioId(String scenarioId) {
    try {
      AssetCatalog assets = new AssetCatalog();
      List<String> scripts = assets.listScripts();
      Pattern p = Pattern.compile("^@scenario\\s+(.+)$");
      for (String s : scripts) {
        try (InputStream in = assets.open(AssetType.SCRIPT, s);
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
          String line;
          int lines = 0;
          while ((line = br.readLine()) != null && lines++ < 50) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            Matcher m = p.matcher(line);
            if (m.matches()) {
              String id = m.group(1);
              if (scenarioId.equals(id)) return s;
              break;
            }
          }
        } catch (Exception e) {
          LOG.debug("Failed to inspect script {}: {}", s, e.toString());
        }
      }
    } catch (Exception e) {
      LOG.warn("Failed to resolve script for scenarioId {}: {}", scenarioId, e.toString());
    }
    return null;
  }

  @Override
  public void update(long deltaMs) { }

  private MenuStyleSpec styleForItemId(String... ids) {
    if (ids == null || ids.length == 0) return null;
    for (String id : ids) {
      if (id == null || id.isBlank()) continue;
      for (MenuItemSpec item : menuScreen.items()) {
        if (item != null && id.equalsIgnoreCase(item.id())) {
          if (item.styleId() != null && !item.styleId().isBlank()) {
            return menuProfile.style(item.styleId());
          }
        }
      }
    }
    return null;
  }

  private MenuActionSpec actionForItemId(String... ids) {
    if (ids == null || ids.length == 0) return null;
    for (String id : ids) {
      if (id == null || id.isBlank()) continue;
      for (MenuItemSpec item : menuScreen.items()) {
        if (item != null && id.equalsIgnoreCase(item.id())) {
          return item.action();
        }
      }
    }
    return null;
  }

  private MenuItemSpec itemForItemId(String... ids) {
    if (ids == null || ids.length == 0) return null;
    for (String id : ids) {
      if (id == null || id.isBlank()) continue;
      for (MenuItemSpec item : menuScreen.items()) {
        if (item != null && id.equalsIgnoreCase(item.id())) {
          return item;
        }
      }
    }
    return null;
  }

  private String resolveDisplayText(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String value = raw.trim();
    if (value.startsWith("i18n:")) {
      String key = value.substring("i18n:".length()).trim();
      if (!key.isEmpty()) return Localization.t(key);
    }
    return value;
  }
}
