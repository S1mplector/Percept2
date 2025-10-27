package com.jvn.core.vn.save;

import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnState;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages saving and loading VN game state
 */
public class VnSaveManager {
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
  
  /**
   * Save the current VN state
   */
  public void save(VnState state, String saveName) throws IOException {
    VnSaveData saveData = new VnSaveData();
    saveData.setSaveName(saveName);
    saveData.setScenarioId(state.getScenario().getId());
    saveData.setCurrentNodeIndex(state.getCurrentNodeIndex());
    saveData.setCurrentBackgroundId(state.getCurrentBackgroundId());
    saveData.setVariables(new java.util.HashMap<>(state.getVariables()));
    saveData.setReadNodes(state.getReadNodes());

    java.util.Map<String, String[]> vis = new java.util.HashMap<>();
    for (var entry : state.getVisibleCharacters().entrySet()) {
      String pos = entry.getKey().name();
      VnState.CharacterSlot slot = entry.getValue();
      vis.put(pos, new String[]{slot.getCharacterId(), slot.getExpression()});
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
    saveData.setSettings(sd);

    saveData.setSaveTimestamp(System.currentTimeMillis());
    
    Path saveFile = saveDirectory.resolve(sanitizeFileName(saveName) + ".sav");
    try (ObjectOutputStream oos = new ObjectOutputStream(
        new FileOutputStream(saveFile.toFile()))) {
      oos.writeObject(saveData);
    }
  }
  
  /**
   * Load a saved game state
   */
  public VnSaveData load(String saveName) throws IOException, ClassNotFoundException {
    Path saveFile = saveDirectory.resolve(sanitizeFileName(saveName) + ".sav");
    if (!Files.exists(saveFile)) {
      throw new FileNotFoundException("Save file not found: " + saveName);
    }
    
    try (ObjectInputStream ois = new ObjectInputStream(
        new FileInputStream(saveFile.toFile()))) {
      return (VnSaveData) ois.readObject();
    }
  }
  
  /**
   * List all available save files
   */
  public List<String> listSaves() {
    List<String> saves = new ArrayList<>();
    File dir = saveDirectory.toFile();
    if (dir.exists() && dir.isDirectory()) {
      File[] files = dir.listFiles((d, name) -> name.endsWith(".sav"));
      if (files != null) {
        for (File file : files) {
          String name = file.getName();
          saves.add(name.substring(0, name.length() - 4));
        }
      }
    }
    return saves;
  }
  
  /**
   * Delete a save file
   */
  public boolean deleteSave(String saveName) {
    Path saveFile = saveDirectory.resolve(sanitizeFileName(saveName) + ".sav");
    try {
      return Files.deleteIfExists(saveFile);
    } catch (IOException e) {
      return false;
    }
  }
  
  /**
   * Apply loaded save data to a VN state
   */
  public void applyToState(VnSaveData saveData, VnState state) {
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
      } catch (IllegalArgumentException ignored) {}
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
    }
  }
  
  private String sanitizeFileName(String name) {
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
