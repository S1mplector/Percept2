package com.jvn.core.menu.gallery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.engine.Engine;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnPersistentStore;

/**
 * Runtime scene for the music room / sound replay screen.
 *
 * <p>Displays a list of unlocked tracks grouped by category.
 * Selecting a track plays it through the audio facade.</p>
 */
public class MusicRoomScene implements Scene {
  private final Engine engine;
  private final AudioFacade audio;
  private final VnPersistentStore persistentStore;
  private final MusicRoomRegistry registry;

  private List<String> categories = new ArrayList<>();
  private int categoryIndex = 0;
  private List<MusicRoomEntry> currentEntries = new ArrayList<>();
  private int selectedIndex = 0;

  private MusicRoomEntry nowPlaying = null;
  private boolean playing = false;

  public MusicRoomScene(Engine engine, AudioFacade audio, VnPersistentStore persistentStore) {
    this(engine, audio, persistentStore, MusicRoomRegistry.load());
  }

  public MusicRoomScene(Engine engine, AudioFacade audio, VnPersistentStore persistentStore, MusicRoomRegistry registry) {
    this.engine = engine;
    this.audio = audio;
    this.persistentStore = persistentStore != null ? persistentStore : new VnPersistentStore();
    this.registry = registry != null ? registry : MusicRoomRegistry.load();
    rebuildCategories();
  }

  // --- Accessors for renderer ---

  public MusicRoomRegistry getRegistry() { return registry; }
  public VnPersistentStore getPersistentStore() { return persistentStore; }
  public List<String> getCategories() { return categories; }
  public int getCategoryIndex() { return categoryIndex; }
  public String getCurrentCategory() { return categories.isEmpty() ? "" : categories.get(categoryIndex); }
  public List<MusicRoomEntry> getCurrentEntries() { return currentEntries; }
  public int getSelectedIndex() { return selectedIndex; }
  public MusicRoomEntry getNowPlaying() { return nowPlaying; }
  public boolean isPlaying() { return playing; }

  public boolean isUnlocked(MusicRoomEntry entry) {
    return registry.isUnlocked(entry, persistentStore);
  }

  // --- Navigation ---

  public void moveSelection(int delta) {
    if (currentEntries.isEmpty()) return;
    selectedIndex = Math.max(0, Math.min(selectedIndex + delta, currentEntries.size() - 1));
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
    if (selectedIndex < 0 || selectedIndex >= currentEntries.size()) return;
    MusicRoomEntry entry = currentEntries.get(selectedIndex);
    if (!isUnlocked(entry)) return;
    playTrack(entry);
  }

  public void stopPlayback() {
    if (audio != null) audio.stopBgm();
    nowPlaying = null;
    playing = false;
  }

  public void back() {
    stopPlayback();
    if (engine != null) engine.scenes().pop();
  }

  // --- Lifecycle ---

  @Override
  public void update(long deltaMs) {
    // Future: track playback progress for visualizer
  }

  @Override
  public void onEnter() {
    rebuildCategories();
  }

  @Override
  public void onExit() {
    stopPlayback();
  }

  // --- Internal ---

  private void playTrack(MusicRoomEntry entry) {
    if (audio == null || entry == null) return;
    audio.playBgm(entry.audioPath(), false);
    nowPlaying = entry;
    playing = true;
  }

  private void rebuildCategories() {
    Map<String, List<MusicRoomEntry>> cats = registry.byCategory();
    categories = new ArrayList<>(cats.keySet());
    if (categories.isEmpty()) {
      currentEntries = new ArrayList<>();
    } else {
      categoryIndex = Math.min(categoryIndex, categories.size() - 1);
      currentEntries = new ArrayList<>(cats.getOrDefault(categories.get(categoryIndex), List.of()));
    }
    selectedIndex = 0;
  }

  private void onCategoryChanged() {
    Map<String, List<MusicRoomEntry>> cats = registry.byCategory();
    currentEntries = new ArrayList<>(cats.getOrDefault(categories.get(categoryIndex), List.of()));
    selectedIndex = 0;
  }
}
