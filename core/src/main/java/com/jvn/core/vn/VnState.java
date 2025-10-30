package com.jvn.core.vn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages the current state of a visual novel playthrough
 */
public class VnState {
  private VnScenario scenario;
  private int currentNodeIndex;
  private String currentBackgroundId;
  private final Map<CharacterPosition, CharacterSlot> visibleCharacters;
  private final Map<String, Object> variables; // For future flag/variable system
  private boolean waitingForInput;
  private int textRevealProgress; // For text animation
  private final VnHistory history;
  private final VnSettings settings;
  private boolean skipMode = false;
  private boolean autoPlayMode = false;
  private long autoPlayTimer = 0;
  private final Set<Integer> readNodes; // Track which nodes have been read
  private VnTransition activeTransition;
  private long transitionStartTime;
  private boolean uiHidden = false; // H key toggle
  private boolean historyOverlayShown = false; // Backlog toggle
  private int historyScroll = 0; // lines to scroll back from newest (0 = show newest)
  private String hudMessage;
  private long hudMessageExpireAt;

  public VnState() {
    this.currentNodeIndex = 0;
    this.visibleCharacters = new HashMap<>();
    this.variables = new HashMap<>();
    this.waitingForInput = false;
    this.textRevealProgress = 0;
    this.history = new VnHistory();
    this.settings = new VnSettings();
    this.readNodes = new HashSet<>();
  }

  public VnScenario getScenario() { return scenario; }
  public void setScenario(VnScenario scenario) {
    this.scenario = scenario;
    this.currentNodeIndex = 0;
  }

  public int getCurrentNodeIndex() { return currentNodeIndex; }
  public void setCurrentNodeIndex(int index) { this.currentNodeIndex = index; }
  public void advance() { currentNodeIndex++; }

  public VnNode getCurrentNode() {
    return scenario != null ? scenario.getNode(currentNodeIndex) : null;
  }

  public String getCurrentBackgroundId() { return currentBackgroundId; }
  public void setCurrentBackgroundId(String id) { this.currentBackgroundId = id; }

  public Map<CharacterPosition, CharacterSlot> getVisibleCharacters() {
    return visibleCharacters;
  }

  public void showCharacter(CharacterPosition position, String characterId, String expression) {
    visibleCharacters.put(position, new CharacterSlot(characterId, expression));
  }

  public void hideCharacter(CharacterPosition position) {
    visibleCharacters.remove(position);
  }

  public void clearAllCharacters() {
    visibleCharacters.clear();
  }

  public boolean isWaitingForInput() { return waitingForInput; }
  public void setWaitingForInput(boolean waiting) { this.waitingForInput = waiting; }

  public int getTextRevealProgress() { return textRevealProgress; }
  public void setTextRevealProgress(int progress) { this.textRevealProgress = progress; }
  public void incrementTextReveal(int amount) { this.textRevealProgress += amount; }

  public void setVariable(String key, Object value) { variables.put(key, value); }
  public Object getVariable(String key) { return variables.get(key); }

  public void jumpToLabel(String label) {
    if (scenario != null) {
      Integer index = scenario.getLabelIndex(label);
      if (index != null) {
        currentNodeIndex = index;
      }
    }
  }

  public boolean isScenarioComplete() {
    if (scenario == null) return true;
    VnNode node = getCurrentNode();
    return node == null || node.getType() == VnNodeType.END;
  }

  public VnHistory getHistory() { return history; }
  public VnSettings getSettings() { return settings; }

  public boolean isSkipMode() { return skipMode; }
  public void setSkipMode(boolean skip) { this.skipMode = skip; }

  public boolean isAutoPlayMode() { return autoPlayMode; }
  public void setAutoPlayMode(boolean auto) { this.autoPlayMode = auto; }

  public long getAutoPlayTimer() { return autoPlayTimer; }
  public void setAutoPlayTimer(long timer) { this.autoPlayTimer = timer; }
  public void incrementAutoPlayTimer(long delta) { this.autoPlayTimer += delta; }
  public void resetAutoPlayTimer() { this.autoPlayTimer = 0; }

  public boolean isNodeRead(int nodeIndex) { return readNodes.contains(nodeIndex); }
  public void markNodeAsRead(int nodeIndex) { readNodes.add(nodeIndex); }

  public VnTransition getActiveTransition() { return activeTransition; }
  public void setActiveTransition(VnTransition transition) { 
    this.activeTransition = transition;
    this.transitionStartTime = System.currentTimeMillis();
  }
  public void clearActiveTransition() { this.activeTransition = null; }
  
  public long getTransitionStartTime() { return transitionStartTime; }
  public float getTransitionProgress() {
    if (activeTransition == null) return 1.0f;
    long elapsed = System.currentTimeMillis() - transitionStartTime;
    return Math.min(1.0f, elapsed / (float) activeTransition.getDurationMs());
  }

  public boolean isUiHidden() { return uiHidden; }
  public void setUiHidden(boolean hidden) { this.uiHidden = hidden; }
  public void toggleUiHidden() { this.uiHidden = !this.uiHidden; }

  public boolean isHistoryOverlayShown() { return historyOverlayShown; }
  public void setHistoryOverlayShown(boolean shown) { this.historyOverlayShown = shown; }
  public void toggleHistoryOverlay() { this.historyOverlayShown = !this.historyOverlayShown; }

  public int getHistoryScroll() { return Math.max(0, historyScroll); }
  public void clearHistoryScroll() { this.historyScroll = 0; }
  public void scrollHistoryByLines(int delta) {
    this.historyScroll = Math.max(0, this.historyScroll + delta);
  }

  public String getHudMessage() { return hudMessage; }
  public long getHudMessageExpireAt() { return hudMessageExpireAt; }
  public void showHudMessage(String message, long durationMs) {
    this.hudMessage = message;
    this.hudMessageExpireAt = System.currentTimeMillis() + Math.max(0, durationMs);
  }

  public Map<String, Object> getVariables() { return variables; }
  public void setVariables(Map<String, Object> vars) {
    this.variables.clear();
    if (vars != null) this.variables.putAll(vars);
  }

  public Set<Integer> getReadNodes() { return new HashSet<>(readNodes); }
  public void setReadNodes(Set<Integer> read) {
    this.readNodes.clear();
    if (read != null) this.readNodes.addAll(read);
  }

  public static class CharacterSlot {
    private final String characterId;
    private final String expression;

    public CharacterSlot(String characterId, String expression) {
      this.characterId = characterId;
      this.expression = expression;
    }

    public String getCharacterId() { return characterId; }
    public String getExpression() { return expression; }
  }
}
