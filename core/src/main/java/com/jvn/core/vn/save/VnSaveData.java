package com.jvn.core.vn.save;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents saved VN game state that can be serialized
 */
public class VnSaveData implements Serializable {
  private static final long serialVersionUID = 1L;
  
  private String scenarioId;
  private int currentNodeIndex;
  private String currentBackgroundId;
  private Map<String, Object> variables;
  private Set<Integer> readNodes;
  private Map<String, String[]> visibleCharacters; // position -> [characterId, expression]
  private boolean skipMode;
  private boolean autoPlayMode;
  private boolean uiHidden;
  private SettingsData settings;
  private long saveTimestamp;
  private String saveName;
  
  public VnSaveData() {
    this.variables = new HashMap<>();
    this.readNodes = new HashSet<>();
    this.visibleCharacters = new HashMap<>();
    this.settings = new SettingsData();
    this.saveTimestamp = System.currentTimeMillis();
  }
  
  public String getScenarioId() { return scenarioId; }
  public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
  
  public int getCurrentNodeIndex() { return currentNodeIndex; }
  public void setCurrentNodeIndex(int index) { this.currentNodeIndex = index; }
  
  public String getCurrentBackgroundId() { return currentBackgroundId; }
  public void setCurrentBackgroundId(String id) { this.currentBackgroundId = id; }
  
  public Map<String, Object> getVariables() { return variables; }
  public void setVariables(Map<String, Object> variables) { this.variables = variables != null ? variables : new HashMap<>(); }
  
  public Set<Integer> getReadNodes() { return readNodes; }
  public void setReadNodes(Set<Integer> readNodes) { this.readNodes = readNodes != null ? readNodes : new HashSet<>(); }

  public Map<String, String[]> getVisibleCharacters() { return visibleCharacters; }
  public void setVisibleCharacters(Map<String, String[]> visibleCharacters) { this.visibleCharacters = visibleCharacters != null ? visibleCharacters : new HashMap<>(); }

  public boolean isSkipMode() { return skipMode; }
  public void setSkipMode(boolean skipMode) { this.skipMode = skipMode; }

  public boolean isAutoPlayMode() { return autoPlayMode; }
  public void setAutoPlayMode(boolean autoPlayMode) { this.autoPlayMode = autoPlayMode; }

  public boolean isUiHidden() { return uiHidden; }
  public void setUiHidden(boolean uiHidden) { this.uiHidden = uiHidden; }

  public SettingsData getSettings() { return settings; }
  public void setSettings(SettingsData settings) { this.settings = settings != null ? settings : new SettingsData(); }
  
  public long getSaveTimestamp() { return saveTimestamp; }
  public void setSaveTimestamp(long timestamp) { this.saveTimestamp = timestamp; }
  
  public String getSaveName() { return saveName; }
  public void setSaveName(String name) { this.saveName = name; }

  public static class SettingsData implements Serializable {
    private static final long serialVersionUID = 1L;
    private int textSpeed = 30;
    private float bgmVolume = 0.7f;
    private float sfxVolume = 0.8f;
    private float voiceVolume = 1.0f;
    private long autoPlayDelay = 2000L;
    private boolean skipUnreadText = false;
    private boolean skipAfterChoices = false;

    public int getTextSpeed() { return textSpeed; }
    public void setTextSpeed(int textSpeed) { this.textSpeed = textSpeed; }
    public float getBgmVolume() { return bgmVolume; }
    public void setBgmVolume(float bgmVolume) { this.bgmVolume = bgmVolume; }
    public float getSfxVolume() { return sfxVolume; }
    public void setSfxVolume(float sfxVolume) { this.sfxVolume = sfxVolume; }
    public float getVoiceVolume() { return voiceVolume; }
    public void setVoiceVolume(float voiceVolume) { this.voiceVolume = voiceVolume; }
    public long getAutoPlayDelay() { return autoPlayDelay; }
    public void setAutoPlayDelay(long autoPlayDelay) { this.autoPlayDelay = autoPlayDelay; }
    public boolean isSkipUnreadText() { return skipUnreadText; }
    public void setSkipUnreadText(boolean skipUnreadText) { this.skipUnreadText = skipUnreadText; }
    public boolean isSkipAfterChoices() { return skipAfterChoices; }
    public void setSkipAfterChoices(boolean skipAfterChoices) { this.skipAfterChoices = skipAfterChoices; }
  }
}
