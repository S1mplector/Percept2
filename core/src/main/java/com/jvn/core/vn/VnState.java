package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.TimelineRunner;
import com.jvn.core.tween.Easings;
import com.jvn.core.vn.rollback.VnRollbackStack;

/**
 * Manages the current state of a visual novel playthrough
 */
public class VnState {
  private static final String VAR_DIALOGUE_PRESENTATION_MODE = "ui.dialogueMode";
  private static final String VAR_BUBBLE_ANCHOR_PREFIX = "ui.bubble.anchor.";
  private static final String VAR_BUBBLE_OFFSET_X_PREFIX = "ui.bubble.offsetX.";
  private static final String VAR_BUBBLE_OFFSET_Y_PREFIX = "ui.bubble.offsetY.";

  private VnScenario scenario;
  private String sourceScriptName;
  private int currentNodeIndex;
  private String currentBackgroundId;
  private final Map<CharacterPosition, CharacterSlot> visibleCharacters;
  private final List<Integer> callStack; // For CALL/RETURN subroutine support
  private final VnRollbackStack rollbackStack;
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

  private final List<TimelineRunner> activeTimelines = new ArrayList<>();

  private float screenShakeIntensity = 0f;
  private long screenShakeDurationMs = 0;
  private long screenShakeRemainingMs = 0;
  private float flashR = 1f;
  private float flashG = 1f;
  private float flashB = 1f;
  private float flashStrength = 0f;
  private long flashDurationMs = 0;
  private long flashRemainingMs = 0;

  private static final long CHARACTER_TWEEN_MS = 220;
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
    this.callStack = new ArrayList<>();
    this.rollbackStack = new VnRollbackStack();
  }

  public VnScenario getScenario() { return scenario; }
  public void setScenario(VnScenario scenario) {
    this.scenario = scenario;
    this.currentNodeIndex = 0;
  }

  public String getSourceScriptName() { return sourceScriptName; }
  public void setSourceScriptName(String sourceScriptName) { this.sourceScriptName = sourceScriptName; }

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
    showCharacter(position, characterId, expression, null);
  }

  public void showCharacter(CharacterPosition position, String characterId, String expression, Integer layerOrder) {
    CharacterPosition target = fallbackPositionFor(characterId, position);
    String resolvedExpression = normalizeExpression(expression, "neutral");
    CharacterPosition existingPos = findCharacterPosition(characterId);
    CharacterSlot existingSlot = existingPos == null ? null : visibleCharacters.get(existingPos);
    int resolvedLayerOrder = resolveLayerOrder(target, layerOrder, existingSlot != null ? existingSlot.getLayerOrder() : null);
    removeOtherSlotsForCharacter(characterId, target);
    visibleCharacters.put(target, new CharacterSlot(characterId, resolvedExpression, resolvedLayerOrder));
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
    showCharacterAnimated(position, characterId, expression, null);
  }

  public void showCharacterAnimated(CharacterPosition position, String characterId, String expression, Integer layerOrder) {
    showCharacterAnimated(position, characterId, expression, layerOrder, null, 0);
  }

  public void showCharacterAnimated(CharacterPosition position, String characterId, String expression,
                                     Integer layerOrder, Easing.Type easingType, long customDurationMs) {
    CharacterPosition target = fallbackPositionFor(characterId, position);
    CharacterPosition existingPos = findCharacterPosition(characterId);
    CharacterSlot existingSlot = existingPos == null ? null : visibleCharacters.get(existingPos);
    String fallbackExpression = existingSlot != null ? existingSlot.getExpression() : "neutral";
    String resolvedExpression = normalizeExpression(expression, fallbackExpression);
    int resolvedLayerOrder = resolveLayerOrder(target, layerOrder, existingSlot != null ? existingSlot.getLayerOrder() : null);

    if (isCharacterGlobalPositionEnabled(characterId) && existingPos != null && existingSlot != null && !existingPos.equals(target)) {
      // Move the same sprite between slots, then fade expression if needed.
      long moveDur = customDurationMs > 0 ? customDurationMs : CHARACTER_MOVE_MS;
      String movingExpression = normalizeExpression(existingSlot.getExpression(), resolvedExpression);
      removeSlot(existingPos);
      visibleCharacters.put(target, new CharacterSlot(characterId, movingExpression, resolvedLayerOrder));
      CharacterVisual visual = ensureCharacterVisual(target);
      double startOffset = positionDeltaOffset(existingPos, target);
      visual.startAnimation(1.0, 1.0, startOffset, 0.0, 0.0, 0.0, moveDur, false, easingType);
      pendingExpressionSwitches.remove(target);
      if (!resolvedExpression.equals(movingExpression)) {
        pendingExpressionSwitches.put(target, new PendingExpressionSwitch(characterId, resolvedExpression, moveDur));
      }
      characterDefinedPositions.put(characterId, target);
      return;
    }

    long tweenDur = customDurationMs > 0 ? customDurationMs : CHARACTER_TWEEN_MS;
    removeOtherSlotsForCharacter(characterId, target);
    visibleCharacters.put(target, new CharacterSlot(characterId, resolvedExpression, resolvedLayerOrder));
    pendingExpressionSwitches.remove(target);
    CharacterVisual visual = ensureCharacterVisual(target);
    double startX = entranceOffsetX(target);
    visual.startAnimation(0.0, 1.0, startX, 0.0, 0.0, 0.0, tweenDur, false, easingType);
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

  public CharacterVisual getOrCreateCharacterVisual(CharacterPosition position) {
    return ensureCharacterVisual(position);
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
        visibleCharacters.put(position, new CharacterSlot(slot.getCharacterId(), pending.expression, slot.getLayerOrder()));
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

  public Set<String> getGlobalPositionCharactersSnapshot() {
    return new HashSet<>(globalPositionCharacters);
  }

  public Map<String, CharacterPosition> getCharacterDefinedPositionsSnapshot() {
    return new HashMap<>(characterDefinedPositions);
  }

  public void setGlobalPositionState(Set<String> globalCharacters, Map<String, CharacterPosition> definedPositions) {
    globalPositionCharacters.clear();
    characterDefinedPositions.clear();

    if (globalCharacters != null) {
      for (String id : globalCharacters) {
        if (id != null && !id.isBlank()) {
          globalPositionCharacters.add(id.trim());
        }
      }
    }

    if (definedPositions != null) {
      for (var entry : definedPositions.entrySet()) {
        String id = entry.getKey();
        CharacterPosition position = entry.getValue();
        if (id == null || id.isBlank() || position == null) continue;
        characterDefinedPositions.put(id.trim(), position);
      }
    }
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
    return to.moveDeltaFrom(from);
  }

  private double entranceOffsetX(CharacterPosition position) {
    return position.getEntranceOffsetX();
  }

  private int resolveLayerOrder(CharacterPosition position, Integer requestedLayerOrder, Integer fallbackLayerOrder) {
    if (requestedLayerOrder != null) return requestedLayerOrder;
    if (fallbackLayerOrder != null) return fallbackLayerOrder;
    return position != null ? position.getDefaultLayerOrder() : 0;
  }

  public boolean isWaitingForInput() { return waitingForInput; }
  public void setWaitingForInput(boolean waiting) { this.waitingForInput = waiting; }

  public int getTextRevealProgress() { return textRevealProgress; }
  public void setTextRevealProgress(int progress) { this.textRevealProgress = progress; }
  public void incrementTextReveal(int amount) { this.textRevealProgress += amount; }

  public void setVariable(String key, Object value) { variables.put(key, value); }
  public Object getVariable(String key) { return variables.get(key); }

  public DialoguePresentationMode getDialoguePresentationMode() {
    return DialoguePresentationMode.fromVariable(variables.get(VAR_DIALOGUE_PRESENTATION_MODE));
  }

  public void setDialoguePresentationMode(DialoguePresentationMode mode) {
    DialoguePresentationMode resolved = mode == null ? DialoguePresentationMode.STANDARD : mode;
    variables.put(VAR_DIALOGUE_PRESENTATION_MODE, resolved.token());
  }

  public void resetDialoguePresentationMode() {
    setDialoguePresentationMode(DialoguePresentationMode.STANDARD);
  }

  public BubbleAnchor getBubbleAnchorPreference(String characterId) {
    if (characterId == null || characterId.isBlank()) return BubbleAnchor.AUTO;
    return BubbleAnchor.fromVariable(variables.get(VAR_BUBBLE_ANCHOR_PREFIX + characterId.trim()));
  }

  public void setBubbleAnchorPreference(String characterId, BubbleAnchor anchor) {
    if (characterId == null || characterId.isBlank()) return;
    String key = VAR_BUBBLE_ANCHOR_PREFIX + characterId.trim();
    BubbleAnchor resolved = anchor == null ? BubbleAnchor.AUTO : anchor;
    if (resolved == BubbleAnchor.AUTO) {
      variables.remove(key);
    } else {
      variables.put(key, resolved.token());
    }
  }

  public double getBubbleOffsetXPreference(String characterId) {
    return readBubbleOffset(characterId, VAR_BUBBLE_OFFSET_X_PREFIX);
  }

  public double getBubbleOffsetYPreference(String characterId) {
    return readBubbleOffset(characterId, VAR_BUBBLE_OFFSET_Y_PREFIX);
  }

  public void setBubbleOffsetPreference(String characterId, double x, double y) {
    if (characterId == null || characterId.isBlank()) return;
    String id = characterId.trim();
    variables.put(VAR_BUBBLE_OFFSET_X_PREFIX + id, x);
    variables.put(VAR_BUBBLE_OFFSET_Y_PREFIX + id, y);
  }

  public void clearBubblePlacementPreference(String characterId) {
    if (characterId == null || characterId.isBlank()) return;
    String id = characterId.trim();
    variables.remove(VAR_BUBBLE_ANCHOR_PREFIX + id);
    variables.remove(VAR_BUBBLE_OFFSET_X_PREFIX + id);
    variables.remove(VAR_BUBBLE_OFFSET_Y_PREFIX + id);
  }

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
    this.hudMessage = VnTextFormatter.format(message, variables);
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

  // --- Call stack for CALL/RETURN subroutine support ---

  public void pushCallStack(int returnIndex) {
    callStack.add(returnIndex);
  }

  public int popCallStack() {
    if (callStack.isEmpty()) return -1;
    return callStack.remove(callStack.size() - 1);
  }

  public boolean hasCallStack() {
    return !callStack.isEmpty();
  }

  public int getCallStackDepth() {
    return callStack.size();
  }

  public void clearCallStack() {
    callStack.clear();
  }

  public List<Integer> getCallStackSnapshot() {
    return new ArrayList<>(callStack);
  }

  public void setCallStack(List<Integer> stack) {
    callStack.clear();
    if (stack != null) callStack.addAll(stack);
  }

  // --- Rollback system ---

  public VnRollbackStack getRollbackStack() {
    return rollbackStack;
  }

  public void captureRollbackState(String speaker, String text) {
    rollbackStack.capture(this, speaker, text);
  }

  public boolean canRollback() {
    return rollbackStack.canRollback();
  }

  public boolean canRollforward() {
    return rollbackStack.canRollforward();
  }

  public static class CharacterSlot {
    private final String characterId;
    private final String expression;
    private final int layerOrder;

    public CharacterSlot(String characterId, String expression) {
      this(characterId, expression, 0);
    }

    public CharacterSlot(String characterId, String expression, int layerOrder) {
      this.characterId = characterId;
      this.expression = expression;
      this.layerOrder = layerOrder;
    }

    public String getCharacterId() { return characterId; }
    public String getExpression() { return expression; }
    public int getLayerOrder() { return layerOrder; }
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
    private Easing.Type easingType = null;

    public double getAlpha() { return alpha; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public boolean isRemoveOnComplete() { return removeOnComplete; }
    public boolean isFinished() { return !animating; }

    public void setOffsetX(double offsetX) { this.offsetX = offsetX; this.animating = false; }
    public void setOffsetY(double offsetY) { this.offsetY = offsetY; this.animating = false; }
    public void setAlpha(double alpha) { this.alpha = alpha; }

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
      startAnimation(startAlpha, endAlpha, startOffsetX, endOffsetX, startOffsetY, endOffsetY, durationMs, removeOnComplete, null);
    }

    public void startAnimation(double startAlpha, double endAlpha,
                               double startOffsetX, double endOffsetX,
                               double startOffsetY, double endOffsetY,
                               long durationMs, boolean removeOnComplete,
                               Easing.Type easingType) {
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
      this.easingType = easingType;
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
      double k = easingType != null ? Easing.apply(easingType, t) : Easings.easeOutQuad(t);
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

  // --- Timeline runner management ---

  public void addTimelineRunner(TimelineRunner runner) {
    if (runner != null) activeTimelines.add(runner);
  }

  public void updateTimelineRunners(long deltaMs) {
    activeTimelines.removeIf(r -> { r.update(deltaMs); return r.isFinished(); });
  }

  public boolean hasActiveTimelines() {
    return !activeTimelines.isEmpty();
  }

  public List<TimelineRunner> getActiveTimelines() {
    return activeTimelines;
  }

  private float clamp01(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }

  private double readBubbleOffset(String characterId, String prefix) {
    if (characterId == null || characterId.isBlank()) return 0.0;
    Object value = variables.get(prefix + characterId.trim());
    if (value instanceof Number n) return n.doubleValue();
    if (value instanceof String s) {
      try {
        return Double.parseDouble(s.trim());
      } catch (Exception ignored) {
        return 0.0;
      }
    }
    return 0.0;
  }
}
