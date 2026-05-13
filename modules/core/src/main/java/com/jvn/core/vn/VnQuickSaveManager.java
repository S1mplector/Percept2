package com.jvn.core.vn;

import com.jvn.core.vn.save.VnSaveData;
import com.jvn.core.vn.save.VnSaveManager;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages quick save/load functionality (F5/F9 keys)
 */
public class VnQuickSaveManager {
  private static final Logger log = LoggerFactory.getLogger(VnQuickSaveManager.class);
  private final VnSaveManager saveManager;
  private static final String QUICK_SAVE_NAME = "_quicksave";
  
  public VnQuickSaveManager(VnSaveManager saveManager) {
    this.saveManager = saveManager;
  }
  
  public VnQuickSaveManager() {
    this(new VnSaveManager());
  }
  
  /**
   * Quick save current state (F5)
   */
  public boolean quickSave(VnState state) {
    try {
      saveManager.save(state, QUICK_SAVE_NAME);
      return true;
    } catch (IOException e) {
      log.error("Quick save failed: {}", e.getMessage());
      return false;
    }
  }
  
  /**
   * Quick load saved state (F9)
   */
  public VnSaveData quickLoad() {
    try {
      return saveManager.load(QUICK_SAVE_NAME);
    } catch (IOException | ClassNotFoundException e) {
      log.error("Quick load failed: {}", e.getMessage());
      return null;
    }
  }
  
  /**
   * Check if quick save exists
   */
  public boolean hasQuickSave() {
    return saveManager.listSaves().contains(QUICK_SAVE_NAME);
  }

  /**
   * Save into rotating autosave slots.
   */
  public boolean autoSave(VnState state) {
    try {
      saveManager.autosave(state);
      return true;
    } catch (IOException e) {
      log.error("Autosave failed: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Load the latest autosave snapshot.
   */
  public VnSaveData loadLatestAutoSave() {
    try {
      return saveManager.loadLatestAutoSave();
    } catch (IOException | ClassNotFoundException e) {
      log.error("Autosave load failed: {}", e.getMessage());
      return null;
    }
  }

  public boolean hasAutoSave() {
    return !saveManager.listAutoSaves().isEmpty();
  }
  
  /**
   * Apply quick load to current state
   */
  public boolean applyQuickLoad(VnState state, VnScenario scenario) {
    VnSaveData saveData = quickLoad();
    if (saveData == null) return false;
    
    // Verify scenario matches
    if (!saveData.getScenarioId().equals(scenario.getId())) {
      log.warn("Quick save is for different scenario");
      return false;
    }
    
    saveManager.applyToState(saveData, state);
    return true;
  }

  /**
   * Apply the newest autosave to current state.
   */
  public boolean applyLatestAutoSave(VnState state, VnScenario scenario) {
    VnSaveData saveData = loadLatestAutoSave();
    if (saveData == null) return false;

    // Verify scenario matches.
    if (scenario == null || saveData.getScenarioId() == null || !saveData.getScenarioId().equals(scenario.getId())) {
      log.warn("Autosave is for different scenario");
      return false;
    }

    saveManager.applyToState(saveData, state);
    return true;
  }

  /**
   * Directory path where saves are stored.
   */
  public String getSaveDirectory() {
    return saveManager.getSaveDirectory();
  }

  public String getQuickSaveSlotName() {
    return QUICK_SAVE_NAME;
  }
}
