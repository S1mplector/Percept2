package com.jvn.core.vn;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.rollback.VnRollbackEntry;

/**
 * Scene implementation for visual novel gameplay.
 * Uses an iterative command loop for node processing with support for:
 * - Rollback/forward navigation
 * - CALL/RETURN subroutine execution
 * - Explicit node types (SHOW, HIDE, WAIT, AUDIO, TRANSITION)
 */
public class VnScene implements Scene {
  private static final int MAX_INSTANT_CHAIN = 1000; // Safety limit for instant node chains
  private static final Logger LOGGER = Logger.getLogger(VnScene.class.getName());

  private final VnState state;
  private VnScenario scenario;
  private long textRevealTimer;
  private AudioFacade audioFacade;
  private VnQuickSaveManager quickSaveManager;
  private boolean waitingNode = false;
  private long waitRemainingMs = 0;
  private VnInterop interop;
  // BGM fade state
  private boolean bgmFadeActive = false;
  private long bgmFadeRemainingMs = 0;
  private long bgmFadeDurationMs = 0;
  private float bgmFadeStartVol = 1.0f;
  // Transition blocking state
  private boolean transitionBlocking = false;
  private long transitionRemainingMs = 0;
  private BooleanSupplier interopBlockCondition;

  public VnScene(VnScenario scenario) {
    this.scenario = scenario;
    this.state = new VnState();
    this.state.setScenario(scenario);
    this.textRevealTimer = 0;
    this.quickSaveManager = new VnQuickSaveManager();
  }

  public VnState getState() {
    return state;
  }

  public void setAudioFacade(AudioFacade audio) {
    this.audioFacade = audio;
  }

  public AudioFacade getAudioFacade() {
    return audioFacade;
  }

  public void setQuickSaveManager(VnQuickSaveManager manager) {
    this.quickSaveManager = manager;
  }

  public VnQuickSaveManager getQuickSaveManager() {
    return quickSaveManager;
  }

  public void setInterop(VnInterop interop) { this.interop = interop; }
  public VnInterop getInterop() { return interop; }

  @Override
  public void onEnter() {
    // Process initial node
    processCurrentNode();
  }

  /**
   * Fast-forward state from node 0 to {@code targetIndex - 1}, applying only
   * non-interactive, non-blocking effects: backgrounds, character visibility,
   * transitions (for their target background), audio commands, and a safe subset
   * of external state commands (var/ui/audio/char/settings/mode/screen/history).
   * Call this <em>before</em> {@link #onEnter()} when jumping to a label so
   * visuals and ambient state are already in sync.
   */
  public void preflightState(int targetIndex) {
    if (scenario == null || targetIndex <= 0) return;
    int limit = Math.min(targetIndex, scenario.getNodes().size());
    for (int i = 0; i < limit; i++) {
      VnNode node = scenario.getNode(i);
      if (node == null) continue;
      switch (node.getType()) {
        case BACKGROUND:
          if (node.getBackgroundId() != null) {
            state.setCurrentBackgroundId(node.getBackgroundId());
          }
          break;
        case SHOW:
          if (node.getCharacterToShow() != null && node.getShowPosition() != null) {
            String expr = node.getShowExpression() != null ? node.getShowExpression() : "neutral";
            state.showCharacter(node.getShowPosition(), node.getCharacterToShow(), expr, node.getShowLayerOrder());
          }
          break;
        case MOVE:
          if (node.getCharacterToShow() != null && node.getShowPosition() != null) {
            String moveExpr = node.getShowExpression();
            if (moveExpr == null) moveExpr = state.getCharacterExpression(node.getCharacterToShow());
            if (moveExpr == null) moveExpr = "neutral";
            state.showCharacter(node.getShowPosition(), node.getCharacterToShow(), moveExpr, null);
          }
          break;
        case HIDE:
          if (node.getCharacterToHide() != null) {
            for (var entry : new java.util.ArrayList<>(state.getVisibleCharacters().entrySet())) {
              VnState.CharacterSlot slot = entry.getValue();
              if (slot != null && node.getCharacterToHide().equals(slot.getCharacterId())) {
                state.getVisibleCharacters().remove(entry.getKey());
              }
            }
          }
          break;
        case TRANSITION:
          if (node.getTransition() != null && node.getTransition().getTargetBackgroundId() != null) {
            state.setCurrentBackgroundId(node.getTransition().getTargetBackgroundId());
          }
          break;
        case AUDIO:
          if (node.getAudioCommand() != null) {
            processAudioCommand(node.getAudioCommand());
          }
          break;
        case EXTERNAL:
          if (node.getExternalCommand() != null) {
            String prov = node.getExternalCommand().getProvider();
            if (isPreflightInteropProvider(prov) && interop != null) {
              try {
                interop.handle(node.getExternalCommand(), this);
              } catch (Exception ex) {
                reportInteropException("preflight", node.getExternalCommand(), ex);
              }
            }
          }
          break;
        default:
          break;
      }
    }
  }

  private boolean isPreflightInteropProvider(String provider) {
    if (provider == null || provider.isBlank()) return false;
    return switch (provider.trim().toLowerCase(Locale.ROOT)) {
      case "var", "ui", "audio", "char", "settings", "mode", "screen", "history", "stage" -> true;
      default -> false;
    };
  }

  @Override
  public void update(long deltaMs) {
    // Update any active BGM fade regardless of node processing
    if (bgmFadeActive && audioFacade != null) {
      bgmFadeRemainingMs = Math.max(0, bgmFadeRemainingMs - deltaMs);
      float p = bgmFadeDurationMs <= 0 ? 1f : 1f - (bgmFadeRemainingMs / (float) bgmFadeDurationMs);
      p = Math.min(1f, Math.max(0f, p));
      float vol = bgmFadeStartVol * (1f - p);
      audioFacade.setBgmVolume(vol);
      if (bgmFadeRemainingMs <= 0) {
        audioFacade.stopBgm();
        // restore configured volume for future playback
        audioFacade.setBgmVolume(state.getSettings().getBgmVolume());
        bgmFadeActive = false;
      }
    }

    state.updateScreenEffects(deltaMs);
    state.updateCharacterAnimations(deltaMs);
    state.updateOverlayScreens(deltaMs);
    state.updateTimelineRunners(deltaMs);

    VnNode currentNode = state.getCurrentNode();
    if (currentNode == null) return;

    // Handle timed wait nodes
    if (waitingNode) {
      waitRemainingMs -= deltaMs;
      if (waitRemainingMs <= 0) {
        waitingNode = false;
        state.advance();
        processCurrentNode();
      }
      return;
    }

    if (interopBlockCondition != null) {
      if (interopBlockCondition.getAsBoolean()) {
        interopBlockCondition = null;
        state.advance();
        processCurrentNode();
      }
      return;
    }

    // Update transition blocking
    if (transitionBlocking) {
      transitionRemainingMs -= deltaMs;
      if (transitionRemainingMs <= 0) {
        transitionBlocking = false;
        state.advance();
        processCurrentNode();
      }
    }

    // Update transitions (visual)
    if (state.getActiveTransition() != null) {
      if (state.getTransitionProgress() >= 1.0f) {
        state.clearActiveTransition();
        state.clearPreviousBackgroundIdDuringTransition();
      }
      return; // Don't process other updates during transitions
    }

    // Handle text reveal animation
    if (currentNode.getType() == VnNodeType.DIALOGUE) {
      DialogueLine dialogue = currentNode.getDialogue();
      if (dialogue != null) {
        int textLength = resolveInterpolatedText(dialogue.getText()).length();
        
        // Skip mode: instant text
        if (state.isSkipMode() && (state.getSettings().isSkipUnreadText() || state.isNodeRead(state.getCurrentNodeIndex()))) {
          state.setTextRevealProgress(textLength);
          state.setWaitingForInput(false);
          // Auto-advance in skip mode
          state.advance();
          processCurrentNode();
          return;
        }
        
        if (state.getTextRevealProgress() < textLength) {
          textRevealTimer += deltaMs;
          long textSpeed = state.getSettings().getTextSpeed();
          if (textRevealTimer >= textSpeed) {
            state.incrementTextReveal(1);
            textRevealTimer = 0;
          }
        } else {
          state.setWaitingForInput(true);
          
          // Auto-play mode
          if (state.isAutoPlayMode()) {
            state.incrementAutoPlayTimer(deltaMs);
            if (state.getAutoPlayTimer() >= state.getSettings().getAutoPlayDelay()) {
              state.resetAutoPlayTimer();
              advance();
            }
          }
        }
      }
    } else if (currentNode.getType() == VnNodeType.CHOICE) {
      state.setWaitingForInput(true);
      // Disable skip/auto-play at choices
      if (state.isSkipMode() && !state.getSettings().isSkipAfterChoices()) {
        state.setSkipMode(false);
      }
      if (state.isAutoPlayMode()) {
        state.setAutoPlayMode(false);
      }
    }
  }

  /**
   * Advance to the next node.
   */
  public void advance() {
    VnNode current = state.getCurrentNode();
    if (current == null) return;

    // If text is still revealing, complete it instantly, then advance
    if (current.getType() == VnNodeType.DIALOGUE) {
      DialogueLine dialogue = current.getDialogue();
      int textLength = dialogue == null ? 0 : resolveInterpolatedText(dialogue.getText()).length();
      if (state.getTextRevealProgress() < textLength) {
        state.setTextRevealProgress(textLength);
      }
      stopDialogueVoiceIfPresent(current);
    }

    state.advance();
    processCurrentNode();
  }

  /**
   * Mouse/touch advance path.
   * If enabled in settings, a click first reveals the full current dialogue line,
   * and only a subsequent click advances.
   */
  public void advanceFromClick() {
    VnNode current = state.getCurrentNode();
    if (current == null) return;
    if (current.getType() == VnNodeType.DIALOGUE
        && state.getSettings().isClickRevealBeforeAdvance()) {
      DialogueLine dialogue = current.getDialogue();
      int textLength = dialogue == null ? 0 : resolveInterpolatedText(dialogue.getText()).length();
      if (state.getTextRevealProgress() < textLength) {
        state.setTextRevealProgress(textLength);
        return;
      }
    }
    advance();
  }

  /**
   * Select a choice option
   */
  public void selectChoice(int choiceIndex) {
    VnNode current = state.getCurrentNode();
    if (current == null || current.getType() != VnNodeType.CHOICE) return;

    if (choiceIndex >= 0 && choiceIndex < current.getChoices().size()) {
      Choice choice = current.getChoices().get(choiceIndex);
      if (!choice.isEnabled()) return;
      String cond = choice.getCondition();
      if (cond != null && !cond.isEmpty()) {
        if (!evalChoiceCondition(cond)) return;
      }
      if (choice.getTargetLabel() != null) {
        state.jumpToLabel(choice.getTargetLabel());
        processCurrentNode();
      } else {
        state.advance();
        processCurrentNode();
      }
    }
  }

  private boolean evalChoiceCondition(String cond) {
    try {
      return VnConditionEvaluator.evaluate(cond, state.getVariables());
    } catch (Exception ignored) {
      return false;
    }
  }

  /**
   * Iterative command loop - processes nodes until reaching an interactive or blocking node.
   * Replaces recursive processCurrentNode() for safer execution on long action chains.
   */
  private void processCurrentNode() {
    int instantCount = 0;

    while (instantCount < MAX_INSTANT_CHAIN) {
      VnNode node = state.getCurrentNode();
      if (node == null) return;

      state.setWaitingForInput(false);
      state.setTextRevealProgress(0);
      textRevealTimer = 0;
      state.resetAutoPlayTimer();

      // Mark node as read
      state.markNodeAsRead(state.getCurrentNodeIndex());

      VnNodeType type = node.getType();

      // Handle each node type explicitly
      switch (type) {
        case DIALOGUE:
          processDialogueNode(node);
          return; // Interactive - stop loop

        case CHOICE:
          // Disable skip/auto-play at choices
          if (state.isSkipMode() && !state.getSettings().isSkipAfterChoices()) {
            state.setSkipMode(false);
          }
          if (state.isAutoPlayMode()) {
            state.setAutoPlayMode(false);
          }
          state.setWaitingForInput(true);
          return; // Interactive - stop loop

        case BACKGROUND:
          processBackgroundNode(node);
          state.advance();
          instantCount++;
          break; // Continue loop

        case TRANSITION:
          processTransitionNode(node);
          if (transitionBlocking) {
            return; // Blocking - stop loop
          }
          state.advance();
          instantCount++;
          break;

        case SHOW:
          processShowNode(node);
          state.advance();
          instantCount++;
          break;

        case HIDE:
          processHideNode(node);
          state.advance();
          instantCount++;
          break;

        case MOVE:
          processMoveNode(node);
          state.advance();
          instantCount++;
          break;

        case WAIT:
          processWaitNode(node);
          if (waitingNode) {
            return; // Blocking - stop loop
          }
          state.advance();
          instantCount++;
          break;

        case AUDIO:
          processAudioNode(node);
          state.advance();
          instantCount++;
          break;

        case JUMP:
          processJumpNode(node);
          instantCount++;
          break; // Continue loop (jump already updated index)

        case CALL:
          processCallNode(node);
          instantCount++;
          break; // Continue loop

        case RETURN:
          processReturnNode();
          instantCount++;
          break; // Continue loop

        case EXTERNAL:
          boolean shouldContinue = processExternalNode(node);
          if (!shouldContinue) {
            return; // External requested stop
          }
          instantCount++;
          break;

        case PARTICLE:
          processParticleNode(node);
          state.advance();
          instantCount++;
          break;

        case END:
          return; // Terminal - stop loop
      }
    }

    // Safety: if we hit MAX_INSTANT_CHAIN, log warning but don't crash
    System.err.println("[VnScene] Warning: Hit max instant chain limit (" + MAX_INSTANT_CHAIN + "). Possible infinite loop in script.");
  }

  private void processDialogueNode(VnNode node) {
    DialogueLine dialogue = node.getDialogue();
    if (dialogue == null) return;

    // Add to history
    String speaker = resolveInterpolatedText(dialogue.getSpeakerName());
    String text = resolveInterpolatedText(dialogue.getText());
    state.getHistory().addEntry(speaker, text);

    // Update character display
    if (dialogue.getCharacterId() != null) {
      state.showCharacter(
        dialogue.getPosition(),
        dialogue.getCharacterId(),
        dialogue.getExpression()
      );
    }

    if (audioFacade != null && dialogue.getVoiceTrackId() != null && !dialogue.getVoiceTrackId().isBlank()) {
      audioFacade.stopVoice();
      audioFacade.playVoice(dialogue.getVoiceTrackId());
    }

    // Capture rollback state after dialogue visuals are applied.
    state.captureRollbackState(speaker, text);
  }

  private void stopDialogueVoiceIfPresent(VnNode node) {
    if (audioFacade == null || node == null || node.getType() != VnNodeType.DIALOGUE) return;
    DialogueLine dialogue = node.getDialogue();
    if (dialogue == null || dialogue.getVoiceTrackId() == null || dialogue.getVoiceTrackId().isBlank()) return;
    audioFacade.stopVoice();
  }

  private void processBackgroundNode(VnNode node) {
    if (node.getBackgroundId() != null) {
      state.setCurrentBackgroundId(node.getBackgroundId());
    }
  }

  private String resolveInterpolatedText(String text) {
    return VnTextFormatter.format(text, state.getVariables());
  }

  /**
   * Process EXTERNAL node. Returns true if the command loop should continue.
   */
  private boolean processExternalNode(VnNode node) {
    VnExternalCommand cmd = node.getExternalCommand();
    VnInteropResult res = null;
    if (cmd != null && interop != null) {
      try {
        res = interop.handle(cmd, this);
      } catch (Exception ex) {
        reportInteropException("external", cmd, ex);
      }
    }
    if (res == null || res.shouldAdvance()) {
      state.advance();
    }
    return res == null || res.shouldContinueProcessing();
  }

  private void reportInteropException(String context, VnExternalCommand cmd, Exception ex) {
    String provider = "unknown";
    if (cmd != null && cmd.getProvider() != null && !cmd.getProvider().isBlank()) {
      provider = cmd.getProvider();
    }
    String detail = ex == null ? "unknown error" : ex.getClass().getSimpleName();
    if (ex != null && ex.getMessage() != null && !ex.getMessage().isBlank()) {
      detail += ": " + ex.getMessage();
    }
    LOGGER.log(Level.WARNING, "VN " + context + " interop failed for provider '" + provider + "'", ex);
    state.showHudMessage("VN " + context + " [" + provider + "] failed: " + detail, 2200);
  }

  private void processJumpNode(VnNode node) {
    if (node.getJumpLabel() != null) {
      state.jumpToLabel(node.getJumpLabel());
    } else {
      state.advance();
    }
  }

  private void processCallNode(VnNode node) {
    String targetLabel = node.getJumpLabel();
    if (targetLabel != null) {
      // Push return address (next node after this CALL)
      state.pushCallStack(state.getCurrentNodeIndex() + 1);
      state.jumpToLabel(targetLabel);
    } else {
      state.advance();
    }
  }

  private void processReturnNode() {
    int returnIndex = state.popCallStack();
    if (returnIndex >= 0) {
      state.setCurrentNodeIndex(returnIndex);
    } else {
      // No call stack - treat as advance (or could be error)
      state.advance();
    }
  }

  private void processShowNode(VnNode node) {
    if (node.getCharacterToShow() != null && node.getShowPosition() != null) {
      String expr = node.getShowExpression() != null ? node.getShowExpression() : "neutral";
      state.showCharacterAnimated(node.getShowPosition(), node.getCharacterToShow(), expr, node.getShowLayerOrder());
    }
  }

  private void processMoveNode(VnNode node) {
    if (node.getCharacterToShow() != null && node.getShowPosition() != null) {
      String expr = node.getShowExpression() != null ? node.getShowExpression() : null;
      state.showCharacterAnimated(node.getShowPosition(), node.getCharacterToShow(), expr,
          null, node.getMoveEasingType(), node.getMoveDurationMs());
    }
  }

  private void processHideNode(VnNode node) {
    String characterIdToHide = node.getCharacterToHide();
    if (characterIdToHide == null) return;

    java.util.List<CharacterPosition> positionsToRemove = new java.util.ArrayList<>();
    for (var entry : state.getVisibleCharacters().entrySet()) {
      VnState.CharacterSlot slot = entry.getValue();
      if (slot != null && characterIdToHide.equals(slot.getCharacterId())) {
        positionsToRemove.add(entry.getKey());
      }
    }
    for (CharacterPosition position : positionsToRemove) {
      state.hideCharacterAnimated(position);
    }
  }

  private void processWaitNode(VnNode node) {
    long waitMs = node.getWaitMs();
    if (waitMs > 0) {
      waitingNode = true;
      waitRemainingMs = waitMs;
    }
  }

  private void processAudioNode(VnNode node) {
    VnAudioCommand cmd = node.getAudioCommand();
    if (cmd != null) {
      processAudioCommand(cmd);
    }
  }

  private void processTransitionNode(VnNode node) {
    VnTransition transition = node.getTransition();
    if (transition != null) {
      state.setActiveTransition(transition);
      if (transition.getTargetBackgroundId() != null) {
        state.setCurrentBackgroundId(transition.getTargetBackgroundId());
      }
      // Block until transition completes
      transitionBlocking = true;
      transitionRemainingMs = transition.getDurationMs();
    }
  }

  private void processParticleNode(VnNode node) {
    VnParticleCommand cmd = node.getParticleCommand();
    if (cmd != null) {
      if (cmd.isStop()) {
        state.clearParticleEffect();
      } else {
        state.setActiveParticleCommand(cmd);
      }
    }
  }

  private void processAudioCommand(VnAudioCommand cmd) {
    if (audioFacade == null) return;

    switch (cmd.getType()) {
      case PLAY_BGM:
        // Cancel any active fade and ensure volume is restored from settings
        if (bgmFadeActive) {
          bgmFadeActive = false;
          audioFacade.setBgmVolume(state.getSettings().getBgmVolume());
        }
        audioFacade.playBgm(cmd.getTrackId(), cmd.isLoop());
        audioFacade.setBgmVolume(state.getSettings().getBgmVolume());
        break;
      case STOP_BGM:
        // Cancel fade and stop immediately
        bgmFadeActive = false;
        audioFacade.stopBgm();
        break;
      case FADE_OUT_BGM:
        long dur = Math.max(0, cmd.getDurationMs());
        if (dur <= 0) {
          audioFacade.stopBgm();
        } else {
          bgmFadeActive = true;
          bgmFadeDurationMs = dur;
          bgmFadeRemainingMs = dur;
          bgmFadeStartVol = state.getSettings().getBgmVolume();
          // ensure we start from current setting
          audioFacade.setBgmVolume(bgmFadeStartVol);
        }
        break;
      case PLAY_SFX:
        audioFacade.playSfx(cmd.getTrackId());
        break;
      case PLAY_VOICE:
        audioFacade.playVoice(cmd.getTrackId());
        break;
    }
  }

  public void toggleSkipMode() {
    state.setSkipMode(!state.isSkipMode());
    if (state.isSkipMode()) {
      state.setAutoPlayMode(false); // Can't have both
    }
  }

  public void toggleAutoPlayMode() {
    state.setAutoPlayMode(!state.isAutoPlayMode());
    if (state.isAutoPlayMode()) {
      state.setSkipMode(false); // Can't have both
      state.resetAutoPlayTimer();
    }
  }

  public boolean quickSave() {
    return quickSaveManager != null && quickSaveManager.quickSave(state);
  }

  public boolean quickLoad() {
    if (quickSaveManager == null) return false;
    boolean ok = quickSaveManager.applyQuickLoad(state, scenario);
    if (ok) {
      if (audioFacade != null) {
        audioFacade.stopVoice();
      }
      if (audioFacade != null) {
        VnSettings s = state.getSettings();
        audioFacade.setBgmVolume(s.getBgmVolume());
        audioFacade.setSfxVolume(s.getSfxVolume());
        audioFacade.setVoiceVolume(s.getVoiceVolume());
      }
      // Normalize node processing after loading state so the scene reflects the saved node immediately
      processCurrentNode();
    }
    return ok;
  }

  public boolean autoSave() {
    return quickSaveManager != null && quickSaveManager.autoSave(state);
  }

  public boolean autoLoadLatest() {
    if (quickSaveManager == null) return false;
    boolean ok = quickSaveManager.applyLatestAutoSave(state, scenario);
    if (ok) {
      if (audioFacade != null) {
        audioFacade.stopVoice();
      }
      if (audioFacade != null) {
        VnSettings s = state.getSettings();
        audioFacade.setBgmVolume(s.getBgmVolume());
        audioFacade.setSfxVolume(s.getSfxVolume());
        audioFacade.setVoiceVolume(s.getVoiceVolume());
      }
      processCurrentNode();
    }
    return ok;
  }

  public boolean hasQuickSave() {
    return quickSaveManager != null && quickSaveManager.hasQuickSave();
  }

  public VnScenario getScenario() {
    return scenario;
  }

  // --- Rollback support ---

  /**
   * Roll back to the previous dialogue state.
   * @return true if rollback was successful
   */
  public boolean rollback() {
    if (!state.canRollback()) return false;

    // Capture current state for potential roll-forward
    VnNode current = state.getCurrentNode();
    String speaker = null;
    String text = null;
    if (current != null && current.getType() == VnNodeType.DIALOGUE) {
      DialogueLine dl = current.getDialogue();
      if (dl != null) {
        speaker = resolveInterpolatedText(dl.getSpeakerName());
        text = resolveInterpolatedText(dl.getText());
      }
    }
    VnRollbackEntry currentEntry = VnRollbackEntry.capture(state, speaker, text);

    // Get previous state and apply
    VnRollbackEntry previous = state.getRollbackStack().rollback(currentEntry);
    if (previous != null) {
      if (audioFacade != null) audioFacade.stopVoice();
      previous.applyTo(state);
      // Reset blocking states
      waitingNode = false;
      waitRemainingMs = 0;
      transitionBlocking = false;
      transitionRemainingMs = 0;
      // Cancel any audio fades
      if (bgmFadeActive && audioFacade != null) {
        bgmFadeActive = false;
        audioFacade.setBgmVolume(state.getSettings().getBgmVolume());
      }
      return true;
    }
    return false;
  }

  /**
   * Roll forward (redo) to the next state after a rollback.
   * @return true if roll-forward was successful
   */
  public boolean rollforward() {
    if (!state.canRollforward()) return false;

    // Capture current state
    VnNode current = state.getCurrentNode();
    String speaker = null;
    String text = null;
    if (current != null && current.getType() == VnNodeType.DIALOGUE) {
      DialogueLine dl = current.getDialogue();
      if (dl != null) {
        speaker = resolveInterpolatedText(dl.getSpeakerName());
        text = resolveInterpolatedText(dl.getText());
      }
    }
    VnRollbackEntry currentEntry = VnRollbackEntry.capture(state, speaker, text);

    // Get next state and apply
    VnRollbackEntry next = state.getRollbackStack().rollforward(currentEntry);
    if (next != null) {
      if (audioFacade != null) audioFacade.stopVoice();
      next.applyTo(state);
      waitingNode = false;
      waitRemainingMs = 0;
      transitionBlocking = false;
      transitionRemainingMs = 0;
      if (bgmFadeActive && audioFacade != null) {
        bgmFadeActive = false;
        audioFacade.setBgmVolume(state.getSettings().getBgmVolume());
      }
      return true;
    }
    return false;
  }

  /**
   * Check if rollback is available.
   */
  public boolean canRollback() {
    return state.canRollback();
  }

  /**
   * Check if roll-forward is available.
   */
  public boolean canRollforward() {
    return state.canRollforward();
  }

  public void beginInteropBlock(BooleanSupplier condition) {
    this.interopBlockCondition = condition;
  }

  public void jumpToLabel(String label) {
    if (label == null || label.isBlank()) return;
    state.jumpToLabel(label);
    processCurrentNode();
  }
}
