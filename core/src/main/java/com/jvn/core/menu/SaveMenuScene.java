package com.jvn.core.menu;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.engine.Engine;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Save menu for creating/overwriting/deleting/renaming save slots.
 * Requires an active VnScene to save state.
 */
public class SaveMenuScene implements Scene {
  private final Engine engine;
  private final VnSaveManager saveManager;
  private final VnScene currentVnScene;
  private int selected = 0;
  private List<String> saves = new ArrayList<>();

  public SaveMenuScene(Engine engine, VnSaveManager saveManager, VnScene vnScene) {
    this.engine = engine;
    this.saveManager = saveManager;
    this.currentVnScene = vnScene;
    refresh();
  }

  public void refresh() {
    List<String> list = saveManager.listSaves();
    // Sort by modified time desc
    Collections.sort(list, Comparator.naturalOrder());
    this.saves = list;
    if (selected >= getEntriesCount()) selected = getEntriesCount() - 1;
    if (selected < 0) selected = 0;
  }

  public List<String> getSaves() { return saves; }
  public int getSelected() { return selected; }
  public void moveSelection(int delta) {
    int count = getEntriesCount();
    selected = (selected + delta + count) % count;
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
}
