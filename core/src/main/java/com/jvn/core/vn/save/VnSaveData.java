package com.jvn.core.vn.save;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents saved VN game state that can be serialized
 */
public class VnSaveData implements Serializable {
  private static final long serialVersionUID = 1L;
  
  private String scenarioId;
  private int currentNodeIndex;
  private String currentBackgroundId;
  private Map<String, Object> variables;
  private long saveTimestamp;
  private String saveName;
  
  public VnSaveData() {
    this.variables = new HashMap<>();
    this.saveTimestamp = System.currentTimeMillis();
  }
  
  public String getScenarioId() { return scenarioId; }
  public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
  
  public int getCurrentNodeIndex() { return currentNodeIndex; }
  public void setCurrentNodeIndex(int index) { this.currentNodeIndex = index; }
  
  public String getCurrentBackgroundId() { return currentBackgroundId; }
  public void setCurrentBackgroundId(String id) { this.currentBackgroundId = id; }
  
  public Map<String, Object> getVariables() { return variables; }
  public void setVariables(Map<String, Object> variables) { this.variables = variables; }
  
  public long getSaveTimestamp() { return saveTimestamp; }
  public void setSaveTimestamp(long timestamp) { this.saveTimestamp = timestamp; }
  
  public String getSaveName() { return saveName; }
  public void setSaveName(String name) { this.saveName = name; }
}
