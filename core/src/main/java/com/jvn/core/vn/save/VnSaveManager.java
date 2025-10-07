package com.jvn.core.vn.save;

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
    // Variables and other state can be restored here
  }
  
  private String sanitizeFileName(String name) {
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
