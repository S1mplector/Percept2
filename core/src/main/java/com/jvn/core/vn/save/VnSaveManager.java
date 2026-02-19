package com.jvn.core.vn.save;

import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnState;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages saving and loading VN game state.
 */
public class VnSaveManager {
  private static final String SAVE_EXTENSION = ".sav";
  private static final String TEMP_SUFFIX = ".tmp";
  private static final String AUTOSAVE_PREFIX = "_autosave_";
  private static final int AUTOSAVE_SLOT_COUNT = 3;

  private final Path saveDirectory;

  public VnSaveManager(String saveDir) {
    this.saveDirectory = Paths.get(saveDir);
    try {
      Files.createDirectories(saveDirectory);
    } catch (IOException e) {
      System.err.println("Failed to create save directory: " + e.getMessage());
    }
  }

  public VnSaveManager() {
    this(System.getProperty("user.home") + "/.jvn/saves");
  }

  public String getSaveDirectory() {
    return saveDirectory.toString();
  }

  public int getAutosaveSlotCount() {
    return AUTOSAVE_SLOT_COUNT;
  }

  public String getAutosaveSlotName(int slotIndex) {
    int idx = Math.max(0, Math.min(slotIndex, AUTOSAVE_SLOT_COUNT - 1));
    return AUTOSAVE_PREFIX + idx;
  }

  /**
   * Save the current VN state to a named slot.
   */
  public void save(VnState state, String saveName) throws IOException {
    if (state == null) throw new IllegalArgumentException("state cannot be null");
    if (state.getScenario() == null) throw new IllegalStateException("VnState has no scenario");

    VnSaveData saveData = new VnSaveData();
    saveData.setSaveName(saveName);
    saveData.setSchemaVersion(VnSaveData.CURRENT_SCHEMA_VERSION);
    saveData.setScenarioId(state.getScenario().getId());
    saveData.setCurrentNodeIndex(state.getCurrentNodeIndex());
    saveData.setCurrentBackgroundId(state.getCurrentBackgroundId());
    saveData.setVariables(new java.util.HashMap<>(state.getVariables()));
    saveData.setReadNodes(state.getReadNodes());

    java.util.Map<String, String[]> vis = new java.util.HashMap<>();
    for (var entry : state.getVisibleCharacters().entrySet()) {
      String pos = entry.getKey().name();
      VnState.CharacterSlot slot = entry.getValue();
      vis.put(pos, new String[] { slot.getCharacterId(), slot.getExpression() });
    }
    saveData.setVisibleCharacters(vis);

    saveData.setSkipMode(state.isSkipMode());
    saveData.setAutoPlayMode(state.isAutoPlayMode());
    saveData.setUiHidden(state.isUiHidden());

    VnSettings s = state.getSettings();
    VnSaveData.SettingsData sd = new VnSaveData.SettingsData();
    sd.setTextSpeed(s.getTextSpeed());
    sd.setBgmVolume(s.getBgmVolume());
    sd.setSfxVolume(s.getSfxVolume());
    sd.setVoiceVolume(s.getVoiceVolume());
    sd.setAutoPlayDelay(s.getAutoPlayDelay());
    sd.setSkipUnreadText(s.isSkipUnreadText());
    sd.setSkipAfterChoices(s.isSkipAfterChoices());
    sd.setPhysicsFixedStepMs(s.getPhysicsFixedStepMs());
    sd.setPhysicsMaxSubSteps(s.getPhysicsMaxSubSteps());
    sd.setPhysicsDefaultFriction(s.getPhysicsDefaultFriction());
    sd.setInputProfilePath(s.getInputProfilePath());
    sd.setInputProfileSerialized(s.getInputProfileSerialized());
    saveData.setSettings(sd);

    // Optional RPG state passthrough.
    saveData.setRpgState(state.getRpgState());

    save(saveData, saveName);
  }

  /**
   * Persist a pre-built save payload to disk.
   */
  public void save(VnSaveData saveData, String saveName) throws IOException {
    if (saveData == null) throw new IllegalArgumentException("saveData cannot be null");
    String slot = sanitizeFileName(saveName);

    saveData.setSaveName(slot);
    saveData.setSchemaVersion(VnSaveData.CURRENT_SCHEMA_VERSION);
    saveData.setSaveTimestamp(System.currentTimeMillis());
    VnSaveMigration.migrateInPlace(saveData, slot);

    Path saveFile = saveDirectory.resolve(slot + SAVE_EXTENSION);
    writeAtomically(saveFile, saveData);
  }

  /**
   * Save to rotating autosave slots. Reuses the oldest existing autosave slot.
   *
   * @return The slot name that was written.
   */
  public String autosave(VnState state) throws IOException {
    int slot = chooseAutosaveSlot();
    String name = getAutosaveSlotName(slot);
    save(state, name);
    return name;
  }

  /**
   * Save to a specific autosave slot index.
   */
  public void autosave(VnState state, int slotIndex) throws IOException {
    String name = getAutosaveSlotName(slotIndex);
    save(state, name);
  }

  /**
   * Load a saved game state by name.
   */
  public VnSaveData load(String saveName) throws IOException, ClassNotFoundException {
    String slot = sanitizeFileName(saveName);
    Path saveFile = saveDirectory.resolve(slot + SAVE_EXTENSION);
    if (!Files.exists(saveFile)) {
      throw new FileNotFoundException("Save file not found: " + saveName);
    }

    VnSaveData data;
    try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(saveFile.toFile())))) {
      Object obj = ois.readObject();
      if (!(obj instanceof VnSaveData parsed)) {
        throw new IOException("Unsupported save payload in " + saveName);
      }
      data = parsed;
    }

    boolean migrated = VnSaveMigration.migrateInPlace(data, slot);
    if (migrated) {
      try {
        writeAtomically(saveFile, data);
      } catch (IOException e) {
        // Do not fail load because migration write-back failed.
        System.err.println("Warning: migrated save could not be written back: " + e.getMessage());
      }
    }

    return data;
  }

  /**
   * Load the newest autosave by timestamp.
   */
  public VnSaveData loadLatestAutoSave() throws IOException, ClassNotFoundException {
    List<String> autos = listAutoSaves();
    String best = null;
    long bestTs = Long.MIN_VALUE;
    for (String name : autos) {
      try {
        VnSaveData d = load(name);
        long ts = d != null ? d.getSaveTimestamp() : Long.MIN_VALUE;
        if (ts > bestTs) {
          bestTs = ts;
          best = name;
        }
      } catch (Exception ignored) {
      }
    }
    if (best == null) {
      throw new FileNotFoundException("No autosave slots found");
    }
    return load(best);
  }

  /**
   * Lists all save slot names (without file extension).
   */
  public List<String> listSaves() {
    List<String> saves = new ArrayList<>();
    File dir = saveDirectory.toFile();
    if (dir.exists() && dir.isDirectory()) {
      File[] files = dir.listFiles((d, name) -> name.endsWith(SAVE_EXTENSION));
      if (files != null) {
        for (File file : files) {
          String name = file.getName();
          saves.add(name.substring(0, name.length() - SAVE_EXTENSION.length()));
        }
      }
    }
    return saves;
  }

  /**
   * Lists autosave slot names only.
   */
  public List<String> listAutoSaves() {
    List<String> autos = new ArrayList<>();
    for (String name : listSaves()) {
      if (name.startsWith(AUTOSAVE_PREFIX)) {
        autos.add(name);
      }
    }
    return autos;
  }

  /**
   * Delete a save file.
   */
  public boolean deleteSave(String saveName) {
    Path saveFile = saveDirectory.resolve(sanitizeFileName(saveName) + SAVE_EXTENSION);
    try {
      boolean deleted = Files.deleteIfExists(saveFile);
      try {
        Path thumb = saveDirectory.resolve(sanitizeFileName(saveName) + ".png");
        Files.deleteIfExists(thumb);
      } catch (Exception ignored) {
      }
      return deleted;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Rename a save file.
   */
  public boolean renameSave(String oldName, String newName) {
    Path oldFile = saveDirectory.resolve(sanitizeFileName(oldName) + SAVE_EXTENSION);
    Path newFile = saveDirectory.resolve(sanitizeFileName(newName) + SAVE_EXTENSION);
    try {
      if (!Files.exists(oldFile)) return false;
      Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
      // Try to rename sidecar thumbnail if present.
      try {
        Path oldPng = saveDirectory.resolve(sanitizeFileName(oldName) + ".png");
        Path newPng = saveDirectory.resolve(sanitizeFileName(newName) + ".png");
        if (Files.exists(oldPng)) {
          Files.move(oldPng, newPng, StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (Exception ignored) {
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Apply loaded save data to a VN state.
   */
  public void applyToState(VnSaveData saveData, VnState state) {
    if (saveData == null || state == null) return;

    VnSaveMigration.migrateInPlace(saveData, saveData.getSaveName());

    state.setCurrentNodeIndex(saveData.getCurrentNodeIndex());
    state.setCurrentBackgroundId(saveData.getCurrentBackgroundId());
    state.setVariables(saveData.getVariables());
    state.setReadNodes(saveData.getReadNodes());

    state.clearAllCharacters();
    for (var entry : saveData.getVisibleCharacters().entrySet()) {
      String pos = entry.getKey();
      String[] data = entry.getValue();
      try {
        CharacterPosition position = CharacterPosition.valueOf(pos);
        String charId = data.length > 0 ? data[0] : null;
        String expr = data.length > 1 ? data[1] : "neutral";
        if (charId != null) state.showCharacter(position, charId, expr);
      } catch (IllegalArgumentException ignored) {
      }
    }

    state.setSkipMode(saveData.isSkipMode());
    state.setAutoPlayMode(saveData.isAutoPlayMode());
    state.setUiHidden(saveData.isUiHidden());

    if (saveData.getSettings() != null) {
      var sd = saveData.getSettings();
      VnSettings s = state.getSettings();
      s.setTextSpeed(sd.getTextSpeed());
      s.setBgmVolume(sd.getBgmVolume());
      s.setSfxVolume(sd.getSfxVolume());
      s.setVoiceVolume(sd.getVoiceVolume());
      s.setAutoPlayDelay(sd.getAutoPlayDelay());
      s.setSkipUnreadText(sd.isSkipUnreadText());
      s.setSkipAfterChoices(sd.isSkipAfterChoices());
      s.setPhysicsFixedStepMs(sd.getPhysicsFixedStepMs());
      s.setPhysicsMaxSubSteps(sd.getPhysicsMaxSubSteps());
      s.setPhysicsDefaultFriction(sd.getPhysicsDefaultFriction());
      if (sd.getInputProfilePath() != null) s.setInputProfilePath(sd.getInputProfilePath());
      if (sd.getInputProfileSerialized() != null) s.setInputProfileSerialized(sd.getInputProfileSerialized());
    }
    state.setRpgState(saveData.getRpgState());
  }

  private int chooseAutosaveSlot() {
    int candidate = 0;
    long oldest = Long.MAX_VALUE;
    for (int i = 0; i < AUTOSAVE_SLOT_COUNT; i++) {
      String slotName = getAutosaveSlotName(i);
      Path path = saveDirectory.resolve(slotName + SAVE_EXTENSION);
      if (!Files.exists(path)) {
        return i;
      }
      try {
        VnSaveData data = load(slotName);
        long ts = data != null ? data.getSaveTimestamp() : 0;
        if (ts < oldest) {
          oldest = ts;
          candidate = i;
        }
      } catch (Exception e) {
        // Corrupt/unreadable slot gets overwritten first.
        return i;
      }
    }
    return candidate;
  }

  private void writeAtomically(Path finalPath, VnSaveData saveData) throws IOException {
    Files.createDirectories(finalPath.getParent());

    String fileName = finalPath.getFileName().toString();
    Path tempPath = finalPath.resolveSibling(fileName + TEMP_SUFFIX);

    // Ensure stale temp files do not block writes.
    try {
      Files.deleteIfExists(tempPath);
    } catch (Exception ignored) {
    }

    try (FileOutputStream fos = new FileOutputStream(tempPath.toFile());
         BufferedOutputStream bos = new BufferedOutputStream(fos);
         ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(saveData);
      oos.flush();
      bos.flush();
      fos.getFD().sync();
    }

    try {
      Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ex) {
      Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      try {
        Files.deleteIfExists(tempPath);
      } catch (Exception ignored) {
      }
    }
  }

  private String sanitizeFileName(String name) {
    if (name == null || name.isBlank()) return "unnamed";
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
