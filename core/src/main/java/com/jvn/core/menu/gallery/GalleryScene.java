package com.jvn.core.menu.gallery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jvn.core.engine.Engine;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnPersistentStore;

/**
 * Runtime scene for browsing unlocked CG/gallery images.
 *
 * <p>The scene presents a thumbnail grid grouped by category.
 * Selecting a thumbnail opens fullscreen CG viewing mode.</p>
 */
public class GalleryScene implements Scene {
  private final Engine engine;
  private final VnPersistentStore persistentStore;
  private final GalleryRegistry registry;

  private List<String> categories = new ArrayList<>();
  private int categoryIndex = 0;
  private List<GalleryEntry> currentEntries = new ArrayList<>();
  private int selectedIndex = 0;
  private int columns = 4;
  private int page = 0;
  private int pageSize = 12; // columns * rows (e.g. 4×3)

  // Fullscreen CG viewer
  private boolean viewingFullscreen = false;
  private GalleryEntry fullscreenEntry = null;

  public GalleryScene(Engine engine, VnPersistentStore persistentStore) {
    this(engine, persistentStore, GalleryRegistry.load());
  }

  public GalleryScene(Engine engine, VnPersistentStore persistentStore, GalleryRegistry registry) {
    this.engine = engine;
    this.persistentStore = persistentStore != null ? persistentStore : new VnPersistentStore();
    this.registry = registry != null ? registry : GalleryRegistry.load();
    rebuildCategories();
  }

  // --- Accessors for renderer ---

  public GalleryRegistry getRegistry() { return registry; }
  public VnPersistentStore getPersistentStore() { return persistentStore; }
  public List<String> getCategories() { return categories; }
  public int getCategoryIndex() { return categoryIndex; }
  public String getCurrentCategory() { return categories.isEmpty() ? "" : categories.get(categoryIndex); }
  public List<GalleryEntry> getCurrentEntries() { return currentEntries; }
  public int getSelectedIndex() { return selectedIndex; }
  public int getColumns() { return columns; }
  public int getPage() { return page; }
  public int getPageSize() { return pageSize; }
  public boolean isViewingFullscreen() { return viewingFullscreen; }
  public GalleryEntry getFullscreenEntry() { return fullscreenEntry; }

  public int getPageCount() {
    return currentEntries.isEmpty() ? 1 : (int) Math.ceil((double) currentEntries.size() / pageSize);
  }

  public List<GalleryEntry> getPageEntries() {
    int start = page * pageSize;
    int end = Math.min(start + pageSize, currentEntries.size());
    if (start >= currentEntries.size()) return List.of();
    return currentEntries.subList(start, end);
  }

  public boolean isUnlocked(GalleryEntry entry) {
    return registry.isUnlocked(entry, persistentStore);
  }

  public int getTotalCount() { return registry.entries().size(); }
  public int getUnlockedCount() { return registry.unlockedCount(persistentStore); }

  // --- Navigation ---

  public void moveSelection(int delta) {
    if (viewingFullscreen) return;
    int count = getPageEntries().size();
    if (count <= 0) return;
    selectedIndex = Math.max(0, Math.min(selectedIndex + delta, count - 1));
  }

  public void moveSelectionRow(int rowDelta) {
    moveSelection(rowDelta * columns);
  }

  public void nextPage() {
    if (page < getPageCount() - 1) {
      page++;
      selectedIndex = 0;
    }
  }

  public void prevPage() {
    if (page > 0) {
      page--;
      selectedIndex = 0;
    }
  }

  public void nextCategory() {
    if (categories.size() <= 1) return;
    categoryIndex = (categoryIndex + 1) % categories.size();
    onCategoryChanged();
  }

  public void prevCategory() {
    if (categories.size() <= 1) return;
    categoryIndex = (categoryIndex - 1 + categories.size()) % categories.size();
    onCategoryChanged();
  }

  public void activateSelected() {
    if (viewingFullscreen) {
      closeFullscreen();
      return;
    }
    List<GalleryEntry> pageEntries = getPageEntries();
    if (selectedIndex < 0 || selectedIndex >= pageEntries.size()) return;
    GalleryEntry entry = pageEntries.get(selectedIndex);
    if (isUnlocked(entry)) {
      viewingFullscreen = true;
      fullscreenEntry = entry;
    }
  }

  public void closeFullscreen() {
    viewingFullscreen = false;
    fullscreenEntry = null;
  }

  public void back() {
    if (viewingFullscreen) {
      closeFullscreen();
      return;
    }
    if (engine != null) engine.scenes().pop();
  }

  // --- Lifecycle ---

  @Override
  public void update(long deltaMs) {
    // No animation needed for now
  }

  @Override
  public void onEnter() {
    rebuildCategories();
  }

  // --- Internal ---

  private void rebuildCategories() {
    Map<String, List<GalleryEntry>> cats = registry.byCategory();
    categories = new ArrayList<>(cats.keySet());
    if (categories.isEmpty()) {
      currentEntries = new ArrayList<>();
    } else {
      categoryIndex = Math.min(categoryIndex, categories.size() - 1);
      currentEntries = new ArrayList<>(cats.getOrDefault(categories.get(categoryIndex), List.of()));
    }
    page = 0;
    selectedIndex = 0;
  }

  private void onCategoryChanged() {
    Map<String, List<GalleryEntry>> cats = registry.byCategory();
    currentEntries = new ArrayList<>(cats.getOrDefault(categories.get(categoryIndex), List.of()));
    page = 0;
    selectedIndex = 0;
  }
}
