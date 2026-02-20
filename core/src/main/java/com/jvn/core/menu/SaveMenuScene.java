package com.jvn.core.menu;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
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
import com.jvn.core.vn.VnBackground;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.save.VnSaveManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Save menu for creating/overwriting/deleting/renaming save slots.
 * Requires an active VnScene to save state.
 */
public class SaveMenuScene implements Scene {
  private final Engine engine;
  private final VnSaveManager saveManager;
  private final VnScene currentVnScene;
  private final MenuProfile menuProfile;
  private final MenuScreenSpec menuScreen;
  private final MenuLayoutSpec menuLayout;
  private int selected = 0;
  private List<String> saves = new ArrayList<>();

  public SaveMenuScene(Engine engine, VnSaveManager saveManager, VnScene vnScene) {
    this.engine = engine;
    this.saveManager = saveManager;
    this.currentVnScene = vnScene;
    this.menuProfile = MenuProfileLoader.loadFromAssets();
    this.menuScreen = menuProfile.screen("save");
    this.menuLayout = menuProfile.layout(menuScreen.layoutId());
    refresh();
  }

  public void refresh() {
    List<String> list = new ArrayList<>(saveManager.listSaves());
    try {
      var times = new HashMap<String, Long>();
      for (String n : list) {
        try {
          times.put(n, saveManager.load(n).getSaveTimestamp());
        } catch (Exception e) {
          times.put(n, 0L);
        }
      }
      list.sort(Comparator.comparing((String n) -> times.getOrDefault(n, 0L)).reversed());
    } catch (Exception e) {
    }
    this.saves = list;
    if (selected >= getEntriesCount()) selected = getEntriesCount() - 1;
    if (selected < 0) selected = 0;
  }

  public List<String> getSaves() { return saves; }
  public int getSelected() { return selected; }
  public MenuLayoutSpec getMenuLayout() { return menuLayout; }
  public int getItemCount() { return getEntriesCount(); }
  public boolean wrapsSelection() { return menuScreen.wrapSelection(); }

  public MenuStyleSpec getStyleForIndex(int idx) {
    String styleId = menuScreen.defaultStyleId();
    if (idx == 0) {
      MenuStyleSpec s = styleForItemId("new_save", "new_slot", "new");
      if (s != null) return s;
    } else {
      MenuStyleSpec s = styleForItemId("save_slot", "slot", "entry");
      if (s != null) return s;
    }
    if (idx >= 0 && idx < menuScreen.items().size()) {
      var item = menuScreen.items().get(idx);
      if (item != null && item.styleId() != null && !item.styleId().isBlank()) styleId = item.styleId();
    }
    return menuProfile.style(styleId);
  }

  public String getDisplayTitle() {
    String t = resolveDisplayText(menuScreen.titleText());
    return (t == null || t.isBlank()) ? Localization.t("save.title") : t;
  }

  public String getDisplayHints() {
    String t = resolveDisplayText(menuScreen.hintsText());
    if (t == null || t.isBlank()) {
      return Localization.t("common.select") + ": Enter    " + Localization.t("common.back") + ": Esc    "
          + Localization.t("save.delete") + ": Delete    " + Localization.t("save.rename") + ": R";
    }
    return t;
  }

  public String getNewSlotLabel() {
    String custom = labelForItemId("new_save", "new_slot", "new");
    return (custom == null || custom.isBlank()) ? Localization.t("save.new") : custom;
  }

  public MenuActionSpec getSelectedAction() {
    MenuActionSpec action;
    if (isNewItemSelected()) {
      action = actionForItemId("new_save", "new_slot", "new");
    } else {
      action = actionForItemId("save_slot", "slot", "entry");
    }
    return action != null ? action : new MenuActionSpec(MenuActionType.SAVE_MENU, null);
  }

  public MenuItemSpec getMenuItemSpec(int idx) {
    if (idx < 0) return null;
    if (idx == 0) {
      MenuItemSpec newSlot = itemForItemId("new_save", "new_slot", "new");
      if (newSlot != null) return newSlot;
    } else {
      MenuItemSpec saveSlot = itemForItemId("save_slot", "slot", "entry");
      if (saveSlot != null) return saveSlot;
    }
    if (idx < menuScreen.items().size()) {
      return menuScreen.items().get(idx);
    }
    return null;
  }

  public boolean activateSelectedWithoutPrompt() {
    MenuActionSpec action = getSelectedAction();
    return switch (action.type()) {
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
      default -> false;
    };
  }

  public void moveSelection(int delta) {
    int count = getEntriesCount();
    if (menuScreen.wrapSelection()) {
      selected = (selected + delta + count) % count;
    } else {
      selected = Math.max(0, Math.min(selected + delta, count - 1));
    }
  }
  public void setSelected(int idx) {
    int count = getEntriesCount();
    if (idx < 0) idx = 0;
    if (idx >= count) idx = count - 1;
    selected = idx;
  }
  public String getSaveDirectory() { return saveManager.getSaveDirectory(); }

  public boolean isNewItemSelected() { return selected == 0; }
  public String getSelectedName() {
    if (isNewItemSelected()) return null;
    int index = selected - 1;
    if (index >= 0 && index < saves.size()) return saves.get(index);
    return null;
  }
  public int getEntriesCount() { return saves.size() + 1; } // +1 for "New Save..."

  public void saveNew(String name) {
    if (name == null || name.isBlank()) return;
    try {
      saveManager.save(currentVnScene.getState(), name);
      writeThumbnailFor(name);
      refresh();
      engine.scenes().pop();
    } catch (Exception ignored) { }
  }

  public void saveOverwriteSelected() {
    String name = getSelectedName();
    if (name == null) return;
    try {
      saveManager.save(currentVnScene.getState(), name);
      writeThumbnailFor(name);
      refresh();
      engine.scenes().pop();
    } catch (Exception ignored) { }
  }

  public boolean deleteSelected() {
    String name = getSelectedName();
    if (name == null) return false;
    boolean ok = saveManager.deleteSave(name);
    refresh();
    return ok;
  }

  public boolean renameSelected(String newName) {
    String old = getSelectedName();
    if (old == null || newName == null || newName.isBlank()) return false;
    boolean ok = saveManager.renameSave(old, newName);
    refresh();
    return ok;
  }

  public String getCurrentBackgroundPreviewPath() {
    try {
      var state = currentVnScene.getState();
      String bgId = state.getCurrentBackgroundId();
      if (bgId == null) return null;
      VnBackground bg = state.getScenario().getBackground(bgId);
      return bg != null ? bg.getImagePath() : null;
    } catch (Exception ignored) { return null; }
  }

  public Long getSelectedTimestamp() {
    String name = getSelectedName();
    if (name == null) return null;
    try {
      return saveManager.load(name).getSaveTimestamp();
    } catch (Exception ignored) { return null; }
  }

  public String getCurrentRpgSummary() {
    try {
      Object rpg = currentVnScene.getState().getRpgState();
      return summarizeRpgState(rpg);
    } catch (Exception e) { return null; }
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

  private void writeThumbnailFor(String name) {
    try {
      var state = currentVnScene.getState();
      String bgId = state.getCurrentBackgroundId();
      if (bgId == null) return;
      VnScenario scen = state.getScenario();
      if (scen == null) return;
      VnBackground bg = scen.getBackground(bgId);
      if (bg == null) return;
      String path = bg.getImagePath();
      if (path == null) return;
      AssetCatalog assets = new AssetCatalog();
      try (InputStream in = assets.open(AssetType.IMAGE, path)) {
        if (in == null) return;
        BufferedImage bi = ImageIO.read(in);
        if (bi == null) return;
        Path dir = Paths.get(saveManager.getSaveDirectory());
        Files.createDirectories(dir);
        File out = dir.resolve(name + ".png").toFile();
        ImageIO.write(bi, "png", out);
      }
    } catch (Exception ignored) { }
  }

  @Override public void onEnter() { }
  @Override public void update(long deltaMs) { }
  @Override public void onExit() { }

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

  private String labelForItemId(String... ids) {
    if (ids == null || ids.length == 0) return null;
    for (String id : ids) {
      if (id == null || id.isBlank()) continue;
      for (MenuItemSpec item : menuScreen.items()) {
        if (item != null && id.equalsIgnoreCase(item.id())) {
          String label = resolveDisplayText(item.label());
          if (label != null && !label.isBlank()) return label;
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
