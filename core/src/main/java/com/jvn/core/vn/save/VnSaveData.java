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
  public static final int CURRENT_SCHEMA_VERSION = 4;
  
  private int schemaVersion = CURRENT_SCHEMA_VERSION;
  private String scenarioId;
  private int currentNodeIndex;
  private String currentBackgroundId;
  private Map<String, Object> variables;
  private Set<Integer> readNodes;
  private Map<String, String[]> visibleCharacters; // position -> [characterId, expression]
  private java.util.List<Integer> callStack;
  private Set<String> globalPositionCharacters;
  private Map<String, String> characterDefinedPositions; // characterId -> CharacterPosition enum name
  private boolean skipMode;
  private boolean autoPlayMode;
  private long autoPlayTimer;
  private boolean uiHidden;
  private SettingsData settings;
  private Object rpgState;
  private long saveTimestamp;
  private String saveName;
  private String scriptName;
  
  public VnSaveData() {
    this.schemaVersion = CURRENT_SCHEMA_VERSION;
    this.variables = new HashMap<>();
    this.readNodes = new HashSet<>();
    this.visibleCharacters = new HashMap<>();
    this.callStack = new java.util.ArrayList<>();
    this.globalPositionCharacters = new HashSet<>();
    this.characterDefinedPositions = new HashMap<>();
    this.settings = new SettingsData();
    this.saveTimestamp = System.currentTimeMillis();
  }

  public int getSchemaVersion() { return schemaVersion; }
  public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
  
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

  public java.util.List<Integer> getCallStack() { return callStack; }
  public void setCallStack(java.util.List<Integer> callStack) { this.callStack = callStack != null ? callStack : new java.util.ArrayList<>(); }

  public Set<String> getGlobalPositionCharacters() { return globalPositionCharacters; }
  public void setGlobalPositionCharacters(Set<String> globalPositionCharacters) {
    this.globalPositionCharacters = globalPositionCharacters != null ? globalPositionCharacters : new HashSet<>();
  }

  public Map<String, String> getCharacterDefinedPositions() { return characterDefinedPositions; }
  public void setCharacterDefinedPositions(Map<String, String> characterDefinedPositions) {
    this.characterDefinedPositions = characterDefinedPositions != null ? characterDefinedPositions : new HashMap<>();
  }

  public boolean isSkipMode() { return skipMode; }
  public void setSkipMode(boolean skipMode) { this.skipMode = skipMode; }

  public boolean isAutoPlayMode() { return autoPlayMode; }
  public void setAutoPlayMode(boolean autoPlayMode) { this.autoPlayMode = autoPlayMode; }

  public long getAutoPlayTimer() { return autoPlayTimer; }
  public void setAutoPlayTimer(long autoPlayTimer) { this.autoPlayTimer = autoPlayTimer; }

  public boolean isUiHidden() { return uiHidden; }
  public void setUiHidden(boolean uiHidden) { this.uiHidden = uiHidden; }

  public SettingsData getSettings() { return settings; }
  public void setSettings(SettingsData settings) { this.settings = settings != null ? settings : new SettingsData(); }
  
  public long getSaveTimestamp() { return saveTimestamp; }
  public void setSaveTimestamp(long timestamp) { this.saveTimestamp = timestamp; }
  
  public String getSaveName() { return saveName; }
  public void setSaveName(String name) { this.saveName = name; }

  public String getScriptName() { return scriptName; }
  public void setScriptName(String scriptName) { this.scriptName = scriptName; }

  public Object getRpgState() { return rpgState; }
  public void setRpgState(Object rpgState) { this.rpgState = rpgState; }

  public static class SettingsData implements Serializable {
    private static final long serialVersionUID = 1L;
    private int textSpeed = 30;
    private float bgmVolume = 0.7f;
    private float sfxVolume = 0.8f;
    private float voiceVolume = 1.0f;
    private long autoPlayDelay = 2000L;
    private boolean skipUnreadText = false;
    private boolean skipAfterChoices = false;
    private boolean clickRevealBeforeAdvance = true;
    private long physicsFixedStepMs = 0;
    private int physicsMaxSubSteps = 4;
    private double physicsDefaultFriction = 0.2;
    private String inputProfilePath;
    private String inputProfileSerialized;

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
    public boolean isClickRevealBeforeAdvance() { return clickRevealBeforeAdvance; }
    public void setClickRevealBeforeAdvance(boolean clickRevealBeforeAdvance) { this.clickRevealBeforeAdvance = clickRevealBeforeAdvance; }
    public long getPhysicsFixedStepMs() { return physicsFixedStepMs; }
    public void setPhysicsFixedStepMs(long physicsFixedStepMs) { this.physicsFixedStepMs = physicsFixedStepMs; }
    public int getPhysicsMaxSubSteps() { return physicsMaxSubSteps; }
    public void setPhysicsMaxSubSteps(int physicsMaxSubSteps) { this.physicsMaxSubSteps = physicsMaxSubSteps; }
    public double getPhysicsDefaultFriction() { return physicsDefaultFriction; }
    public void setPhysicsDefaultFriction(double physicsDefaultFriction) { this.physicsDefaultFriction = physicsDefaultFriction; }
    public String getInputProfilePath() { return inputProfilePath; }
    public void setInputProfilePath(String inputProfilePath) { this.inputProfilePath = inputProfilePath; }
    public String getInputProfileSerialized() { return inputProfileSerialized; }
    public void setInputProfileSerialized(String inputProfileSerialized) { this.inputProfileSerialized = inputProfileSerialized; }
  }

}
