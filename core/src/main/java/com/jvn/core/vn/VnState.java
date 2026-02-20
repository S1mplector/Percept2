package com.jvn.core.vn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jvn.core.tween.Easings;

/**
 * Manages the current state of a visual novel playthrough
 */
public class VnState {
  private VnScenario scenario;
  private int currentNodeIndex;
  private String currentBackgroundId;
  private final Map<CharacterPosition, CharacterSlot> visibleCharacters;
  private final Map<CharacterPosition, CharacterVisual> characterVisuals;
  private final Map<CharacterPosition, PendingExpressionSwitch> pendingExpressionSwitches;
  private final Set<String> globalPositionCharacters;
  private final Map<String, CharacterPosition> characterDefinedPositions;
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
  private String previousBackgroundIdDuringTransition;
  private boolean uiHidden = false; // H key toggle
  private boolean historyOverlayShown = false; // Backlog toggle
  private int historyScroll = 0; // lines to scroll back from newest (0 = show newest)
  private String hudMessage;
  private long hudMessageExpireAt;
  
  // Save slot overlay state
  private boolean saveSlotOverlayShown = false;
  private boolean saveSlotOverlayIsSaveMode = true; // true = save, false = load
  private int saveSlotSelected = 0; // 0-9 slots (0 = quick save)
  private Object rpgState = new com.jvn.core.rpg.RpgState(); // Optional RPG state payload (serializable)

  private float screenShakeIntensity = 0f;
  private long screenShakeDurationMs = 0;
  private long screenShakeRemainingMs = 0;
  private float flashR = 1f;
  private float flashG = 1f;
  private float flashB = 1f;
  private float flashStrength = 0f;
  private long flashDurationMs = 0;
  private long flashRemainingMs = 0;

  private static final double CHARACTER_TWEEN_OFFSET = 60.0;
  private static final long CHARACTER_TWEEN_MS = 220;
  private static final double CHARACTER_MOVE_STEP_OFFSET = 220.0;
  private static final long CHARACTER_MOVE_MS = 320;
  private static final long CHARACTER_EXPRESSION_FADE_MS = 180;

  public VnState() {
    this.currentNodeIndex = 0;
    this.visibleCharacters = new HashMap<>();
    this.characterVisuals = new HashMap<>();
    this.pendingExpressionSwitches = new HashMap<>();
    this.globalPositionCharacters = new HashSet<>();
    this.characterDefinedPositions = new HashMap<>();
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
    CharacterPosition target = fallbackPositionFor(characterId, position);
    String resolvedExpression = normalizeExpression(expression, "neutral");
    removeOtherSlotsForCharacter(characterId, target);
    visibleCharacters.put(target, new CharacterSlot(characterId, resolvedExpression));
    pendingExpressionSwitches.remove(target);
    CharacterVisual visual = ensureCharacterVisual(target);
    visual.setImmediate(1.0, 0.0, 0.0);
    if (isCharacterGlobalPositionEnabled(characterId)) {
      characterDefinedPositions.put(characterId, target);
    }
  }

  public void hideCharacter(CharacterPosition position) {
    removeSlot(position);
  }

  public void clearAllCharacters() {
    visibleCharacters.clear();
    characterVisuals.clear();
    pendingExpressionSwitches.clear();
  }

  public void showCharacterAnimated(CharacterPosition position, String characterId, String expression) {
    CharacterPosition target = fallbackPositionFor(characterId, position);
    CharacterPosition existingPos = findCharacterPosition(characterId);
    CharacterSlot existingSlot = existingPos == null ? null : visibleCharacters.get(existingPos);
    String fallbackExpression = existingSlot != null ? existingSlot.getExpression() : "neutral";
    String resolvedExpression = normalizeExpression(expression, fallbackExpression);

    if (isCharacterGlobalPositionEnabled(characterId) && existingPos != null && existingPos != target) {
      // Move the same sprite between slots, then fade expression if needed.
      String movingExpression = normalizeExpression(existingSlot.getExpression(), resolvedExpression);
      removeSlot(existingPos);
      visibleCharacters.put(target, new CharacterSlot(characterId, movingExpression));
      CharacterVisual visual = ensureCharacterVisual(target);
      double startOffset = positionDeltaOffset(existingPos, target);
      visual.startAnimation(1.0, 1.0, startOffset, 0.0, 0.0, 0.0, CHARACTER_MOVE_MS, false);
      pendingExpressionSwitches.remove(target);
      if (!resolvedExpression.equals(movingExpression)) {
        pendingExpressionSwitches.put(target, new PendingExpressionSwitch(characterId, resolvedExpression, CHARACTER_MOVE_MS));
      }
      characterDefinedPositions.put(characterId, target);
      return;
    }

    removeOtherSlotsForCharacter(characterId, target);
    visibleCharacters.put(target, new CharacterSlot(characterId, resolvedExpression));
    pendingExpressionSwitches.remove(target);
    CharacterVisual visual = ensureCharacterVisual(target);
    double startX = entranceOffsetX(target);
    visual.startAnimation(0.0, 1.0, startX, 0.0, 0.0, 0.0, CHARACTER_TWEEN_MS, false);
    if (isCharacterGlobalPositionEnabled(characterId)) {
      characterDefinedPositions.put(characterId, target);
    }
  }

  public void hideCharacterAnimated(CharacterPosition position) {
    if (position == null || !visibleCharacters.containsKey(position)) return;
    CharacterVisual visual = ensureCharacterVisual(position);
    double endX = entranceOffsetX(position);
    visual.startAnimation(visual.getAlpha(), 0.0, visual.getOffsetX(), endX, visual.getOffsetY(), 0.0, CHARACTER_TWEEN_MS, true);
  }

  public CharacterVisual getCharacterVisual(CharacterPosition position) {
    return characterVisuals.get(position);
  }

  public void updateCharacterAnimations(long deltaMs) {
    if (!characterVisuals.isEmpty()) {
      var it = characterVisuals.entrySet().iterator();
      while (it.hasNext()) {
        var entry = it.next();
        CharacterVisual visual = entry.getValue();
        visual.update(deltaMs);
        if (visual.isFinished() && visual.isRemoveOnComplete()) {
          visibleCharacters.remove(entry.getKey());
          pendingExpressionSwitches.remove(entry.getKey());
          it.remove();
        }
      }
    }

    if (!pendingExpressionSwitches.isEmpty()) {
      var it = pendingExpressionSwitches.entrySet().iterator();
      while (it.hasNext()) {
        var entry = it.next();
        CharacterPosition position = entry.getKey();
        PendingExpressionSwitch pending = entry.getValue();
        if (!pending.tick(deltaMs)) continue;

        CharacterSlot slot = visibleCharacters.get(position);
        if (slot == null || !pending.characterId.equals(slot.getCharacterId())) {
          it.remove();
          continue;
        }
        visibleCharacters.put(position, new CharacterSlot(slot.getCharacterId(), pending.expression));
        CharacterVisual visual = ensureCharacterVisual(position);
        visual.startAnimation(0.0, 1.0, 0.0, 0.0, 0.0, 0.0, CHARACTER_EXPRESSION_FADE_MS, false);
        it.remove();
      }
    }
  }

  public void setCharacterGlobalPositionEnabled(String characterId, boolean enabled) {
    if (characterId == null || characterId.isBlank()) return;
    String id = characterId.trim();
    if (enabled) {
      globalPositionCharacters.add(id);
      CharacterPosition current = findCharacterPosition(id);
      if (current != null) characterDefinedPositions.put(id, current);
    } else {
      globalPositionCharacters.remove(id);
      characterDefinedPositions.remove(id);
    }
  }

  public boolean isCharacterGlobalPositionEnabled(String characterId) {
    if (characterId == null || characterId.isBlank()) return false;
    return globalPositionCharacters.contains(characterId.trim());
  }

  public void setCharacterDefinedPosition(String characterId, CharacterPosition position) {
    if (characterId == null || characterId.isBlank() || position == null) return;
    characterDefinedPositions.put(characterId.trim(), position);
  }

  public CharacterPosition getCharacterDefinedPosition(String characterId) {
    if (characterId == null || characterId.isBlank()) return null;
    return characterDefinedPositions.get(characterId.trim());
  }

  public CharacterPosition getCharacterPosition(String characterId) {
    return findCharacterPosition(characterId);
  }

  public String getCharacterExpression(String characterId) {
    CharacterPosition position = findCharacterPosition(characterId);
    if (position == null) return null;
    CharacterSlot slot = visibleCharacters.get(position);
    return slot == null ? null : slot.getExpression();
  }

  private CharacterVisual ensureCharacterVisual(CharacterPosition position) {
    return characterVisuals.computeIfAbsent(position, k -> new CharacterVisual());
  }

  private void removeSlot(CharacterPosition position) {
    if (position == null) return;
    visibleCharacters.remove(position);
    characterVisuals.remove(position);
    pendingExpressionSwitches.remove(position);
  }

  /**
   * A character should only occupy one slot at a time. If a character is re-shown at a new
   * position, clear any older slot to avoid duplicate rendering.
   */
  private void removeOtherSlotsForCharacter(String characterId, CharacterPosition keepPosition) {
    if (characterId == null || characterId.isBlank() || visibleCharacters.isEmpty()) return;
    var it = visibleCharacters.entrySet().iterator();
    while (it.hasNext()) {
      var entry = it.next();
      if (entry.getKey() == keepPosition) continue;
      CharacterSlot slot = entry.getValue();
      if (slot != null && characterId.equals(slot.getCharacterId())) {
        characterVisuals.remove(entry.getKey());
        pendingExpressionSwitches.remove(entry.getKey());
        it.remove();
      }
    }
  }

  private CharacterPosition fallbackPositionFor(String characterId, CharacterPosition requested) {
    if (requested != null) return requested;
    CharacterPosition defined = getCharacterDefinedPosition(characterId);
    return defined != null ? defined : CharacterPosition.CENTER;
  }

  private CharacterPosition findCharacterPosition(String characterId) {
    if (characterId == null || characterId.isBlank()) return null;
    String id = characterId.trim();
    for (var entry : visibleCharacters.entrySet()) {
      CharacterSlot slot = entry.getValue();
      if (slot != null && id.equals(slot.getCharacterId())) {
        return entry.getKey();
      }
    }
    return null;
  }

  private String normalizeExpression(String expression, String fallback) {
    if (expression == null || expression.isBlank()) return fallback == null ? "neutral" : fallback;
    return expression.trim();
  }

  private double positionDeltaOffset(CharacterPosition from, CharacterPosition to) {
    int fromOrd = positionOrdinal(from);
    int toOrd = positionOrdinal(to);
    return (fromOrd - toOrd) * CHARACTER_MOVE_STEP_OFFSET;
  }

  private int positionOrdinal(CharacterPosition position) {
    if (position == null) return 0;
    return switch (position) {
      case FAR_LEFT -> -2;
      case LEFT -> -1;
      case CENTER -> 0;
      case RIGHT -> 1;
      case FAR_RIGHT -> 2;
    };
  }

  private double entranceOffsetX(CharacterPosition position) {
    return switch (position) {
      case FAR_LEFT, LEFT -> -CHARACTER_TWEEN_OFFSET;
      case FAR_RIGHT, RIGHT -> CHARACTER_TWEEN_OFFSET;
      case CENTER -> 0.0;
    };
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
    this.previousBackgroundIdDuringTransition = this.currentBackgroundId;
  }
  public void clearActiveTransition() { this.activeTransition = null; }
  
  public long getTransitionStartTime() { return transitionStartTime; }
  public float getTransitionProgress() {
    if (activeTransition == null) return 1.0f;
    long elapsed = System.currentTimeMillis() - transitionStartTime;
    return Math.min(1.0f, elapsed / (float) activeTransition.getDurationMs());
  }

  public String getPreviousBackgroundIdDuringTransition() { return previousBackgroundIdDuringTransition; }
  public void clearPreviousBackgroundIdDuringTransition() { this.previousBackgroundIdDuringTransition = null; }

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

  // Save slot overlay
  public boolean isSaveSlotOverlayShown() { return saveSlotOverlayShown; }
  public void setSaveSlotOverlayShown(boolean shown) { this.saveSlotOverlayShown = shown; }
  public void showSaveSlotOverlay(boolean isSaveMode) {
    this.saveSlotOverlayShown = true;
    this.saveSlotOverlayIsSaveMode = isSaveMode;
    this.saveSlotSelected = 0;
  }
  public void hideSaveSlotOverlay() { this.saveSlotOverlayShown = false; }
  public boolean isSaveSlotOverlaySaveMode() { return saveSlotOverlayIsSaveMode; }
  public int getSaveSlotSelected() { return saveSlotSelected; }
  public void setSaveSlotSelected(int slot) { this.saveSlotSelected = Math.max(0, Math.min(9, slot)); }
  public void moveSaveSlotSelection(int delta) {
    int newSlot = saveSlotSelected + delta;
    if (newSlot < 0) newSlot = 9;
    if (newSlot > 9) newSlot = 0;
    this.saveSlotSelected = newSlot;
  }

  public String getHudMessage() { return hudMessage; }
  public long getHudMessageExpireAt() { return hudMessageExpireAt; }
  public void showHudMessage(String message, long durationMs) {
    this.hudMessage = VnVariableInterpolator.interpolate(message, variables);
    this.hudMessageExpireAt = System.currentTimeMillis() + Math.max(0, durationMs);
  }

  public void triggerScreenShake(float intensity, long durationMs) {
    this.screenShakeIntensity = Math.max(0f, intensity);
    this.screenShakeDurationMs = Math.max(0L, durationMs);
    this.screenShakeRemainingMs = this.screenShakeDurationMs;
  }

  public float getScreenShakeMagnitude() {
    if (screenShakeRemainingMs <= 0 || screenShakeDurationMs <= 0) return 0f;
    float t = screenShakeRemainingMs / (float) screenShakeDurationMs;
    return screenShakeIntensity * t;
  }

  public void triggerFlash(float r, float g, float b, float strength, long durationMs) {
    this.flashR = clamp01(r);
    this.flashG = clamp01(g);
    this.flashB = clamp01(b);
    this.flashStrength = Math.max(0f, strength);
    this.flashDurationMs = Math.max(0L, durationMs);
    this.flashRemainingMs = this.flashDurationMs;
  }

  public float getFlashAlpha() {
    if (flashRemainingMs <= 0 || flashDurationMs <= 0) return 0f;
    float t = flashRemainingMs / (float) flashDurationMs;
    return flashStrength * t;
  }

  public float getFlashR() { return flashR; }
  public float getFlashG() { return flashG; }
  public float getFlashB() { return flashB; }

  public void updateScreenEffects(long deltaMs) {
    if (screenShakeRemainingMs > 0) {
      screenShakeRemainingMs = Math.max(0L, screenShakeRemainingMs - deltaMs);
    }
    if (flashRemainingMs > 0) {
      flashRemainingMs = Math.max(0L, flashRemainingMs - deltaMs);
    }
  }

  public Map<String, Object> getVariables() { return variables; }
  public void setVariables(Map<String, Object> vars) {
    this.variables.clear();
    if (vars != null) this.variables.putAll(vars);
  }

  public Object getRpgState() { return rpgState; }
  public void setRpgState(Object rpgState) { this.rpgState = rpgState; }

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

  public static class CharacterVisual {
    private double alpha = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double startAlpha = 1.0;
    private double endAlpha = 1.0;
    private double startOffsetX = 0.0;
    private double startOffsetY = 0.0;
    private double endOffsetX = 0.0;
    private double endOffsetY = 0.0;
    private long durationMs = 1;
    private long elapsedMs = 0;
    private boolean animating = false;
    private boolean removeOnComplete = false;

    public double getAlpha() { return alpha; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public boolean isRemoveOnComplete() { return removeOnComplete; }
    public boolean isFinished() { return !animating; }

    public void setImmediate(double alpha, double offsetX, double offsetY) {
      this.alpha = alpha;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.animating = false;
      this.removeOnComplete = false;
    }

    public void startAnimation(double startAlpha, double endAlpha,
                               double startOffsetX, double endOffsetX,
                               double startOffsetY, double endOffsetY,
                               long durationMs, boolean removeOnComplete) {
      this.startAlpha = startAlpha;
      this.endAlpha = endAlpha;
      this.startOffsetX = startOffsetX;
      this.endOffsetX = endOffsetX;
      this.startOffsetY = startOffsetY;
      this.endOffsetY = endOffsetY;
      this.durationMs = Math.max(1L, durationMs);
      this.elapsedMs = 0L;
      this.animating = true;
      this.removeOnComplete = removeOnComplete;
      this.alpha = startAlpha;
      this.offsetX = startOffsetX;
      this.offsetY = startOffsetY;
    }

    public void update(long deltaMs) {
      if (!animating) return;
      elapsedMs += deltaMs;
      if (elapsedMs >= durationMs) {
        elapsedMs = durationMs;
      }
      double t = elapsedMs / (double) durationMs;
      double k = Easings.easeOutQuad(t);
      alpha = lerp(startAlpha, endAlpha, k);
      offsetX = lerp(startOffsetX, endOffsetX, k);
      offsetY = lerp(startOffsetY, endOffsetY, k);
      if (elapsedMs >= durationMs) {
        animating = false;
        alpha = endAlpha;
        offsetX = endOffsetX;
        offsetY = endOffsetY;
      }
    }

    private double lerp(double a, double b, double t) {
      return a + (b - a) * t;
    }
  }

  private static class PendingExpressionSwitch {
    private final String characterId;
    private final String expression;
    private long remainingMs;

    private PendingExpressionSwitch(String characterId, String expression, long delayMs) {
      this.characterId = characterId;
      this.expression = expression == null || expression.isBlank() ? "neutral" : expression.trim();
      this.remainingMs = Math.max(1L, delayMs);
    }

    private boolean tick(long deltaMs) {
      remainingMs = Math.max(0L, remainingMs - Math.max(0L, deltaMs));
      return remainingMs <= 0L;
    }
  }

  private float clamp01(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }
}
