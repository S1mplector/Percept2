package com.jvn.core.vn;

import com.jvn.core.vn.save.VnSaveData;
import com.jvn.core.vn.save.VnSaveManager;

import java.io.IOException;

/**
 * Manages quick save/load functionality (F5/F9 keys)
 */
public class VnQuickSaveManager {
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
      System.err.println("Quick save failed: " + e.getMessage());
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
      System.err.println("Quick load failed: " + e.getMessage());
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
   * Apply quick load to current state
   */
  public boolean applyQuickLoad(VnState state, VnScenario scenario) {
    VnSaveData saveData = quickLoad();
    if (saveData == null) return false;
    
    // Verify scenario matches
    if (!saveData.getScenarioId().equals(scenario.getId())) {
      System.err.println("Quick save is for different scenario");
      return false;
    }
    
    saveManager.applyToState(saveData, state);
    return true;
  }
}
