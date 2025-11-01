package com.jvn.core.vn;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.scene.Scene;

/**
 * Scene implementation for visual novel gameplay
 */
public class VnScene implements Scene {
  private final VnState state;
  private VnScenario scenario;
  private long textRevealTimer;
  private AudioFacade audioFacade; // Optional audio support
  private VnQuickSaveManager quickSaveManager;
  private boolean waitingNode = false;
  private long waitRemainingMs = 0;

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

  @Override
  public void onEnter() {
    // Process initial node
    processCurrentNode();
  }

  @Override
  public void update(long deltaMs) {
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

    // Update transitions
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
        int textLength = dialogue.getText().length();
        
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
   * Advance to the next node (called when player clicks/presses space)
   */
  public void advance() {
    VnNode current = state.getCurrentNode();
    if (current == null) return;

    // If text is still revealing, complete it instantly, then advance
    if (current.getType() == VnNodeType.DIALOGUE) {
      DialogueLine dialogue = current.getDialogue();
      if (dialogue != null && state.getTextRevealProgress() < dialogue.getText().length()) {
        state.setTextRevealProgress(dialogue.getText().length());
      }
    }

    state.advance();
    processCurrentNode();
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
      if (choice.getTargetLabel() != null) {
        state.jumpToLabel(choice.getTargetLabel());
        processCurrentNode();
      } else {
        state.advance();
        processCurrentNode();
      }
    }
  }

  private void processCurrentNode() {
    VnNode node = state.getCurrentNode();
    if (node == null) return;

    state.setWaitingForInput(false);
    state.setTextRevealProgress(0);
    textRevealTimer = 0;
    state.resetAutoPlayTimer();

    // Mark node as read
    state.markNodeAsRead(state.getCurrentNodeIndex());

    // Process audio commands
    if (node.getAudioCommand() != null) {
      processAudioCommand(node.getAudioCommand());
    }

    // Process transitions
    if (node.getTransition() != null) {
      state.setActiveTransition(node.getTransition());
      if (node.getTransition().getTargetBackgroundId() != null) {
        state.setCurrentBackgroundId(node.getTransition().getTargetBackgroundId());
      }
    }

    // Process character show/hide
    if (node.getCharacterToShow() != null && node.getShowPosition() != null) {
      String expr = node.getShowExpression() != null ? node.getShowExpression() : "neutral";
      state.showCharacter(node.getShowPosition(), node.getCharacterToShow(), expr);
    }
    if (node.getCharacterToHide() != null) {
      // Find and remove character
      CharacterPosition posToRemove = null;
      for (var entry : state.getVisibleCharacters().entrySet()) {
        if (entry.getValue().getCharacterId().equals(node.getCharacterToHide())) {
          posToRemove = entry.getKey();
          break;
        }
      }
      if (posToRemove != null) {
        state.hideCharacter(posToRemove);
      }
    }

    // Process wait (if any) and pause progression until elapsed
    if (node.getWaitMs() > 0) {
      waitingNode = true;
      waitRemainingMs = node.getWaitMs();
      return;
    }

    switch (node.getType()) {
      case DIALOGUE:
        processDialogueNode(node);
        break;
      case BACKGROUND:
        processBackgroundNode(node);
        state.advance();
        processCurrentNode();
        break;
      case JUMP:
        if (node.getJumpLabel() != null) {
          processJumpNode(node);
        } else {
          state.advance();
        }
        processCurrentNode();
        break;
      case CHOICE:
        // Choices wait for player input
        break;
      case END:
        // Scenario complete
        break;
    }
  }

  private void processDialogueNode(VnNode node) {
    DialogueLine dialogue = node.getDialogue();
    if (dialogue == null) return;

    // Add to history
    state.getHistory().addEntry(dialogue.getSpeakerName(), dialogue.getText());

    // Update character display
    if (dialogue.getCharacterId() != null) {
      state.showCharacter(
        dialogue.getPosition(),
        dialogue.getCharacterId(),
        dialogue.getExpression()
      );
    }
  }

  private void processBackgroundNode(VnNode node) {
    if (node.getBackgroundId() != null) {
      state.setCurrentBackgroundId(node.getBackgroundId());
    }
  }

  private void processJumpNode(VnNode node) {
    if (node.getJumpLabel() != null) {
      state.jumpToLabel(node.getJumpLabel());
    }
  }

  private void processAudioCommand(VnAudioCommand cmd) {
    if (audioFacade == null) return;

    switch (cmd.getType()) {
      case PLAY_BGM:
        audioFacade.playBgm(cmd.getTrackId(), cmd.isLoop());
        break;
      case STOP_BGM:
      case FADE_OUT_BGM:
        audioFacade.stopBgm();
        break;
      case PLAY_SFX:
        audioFacade.playSfx(cmd.getTrackId());
        break;
      case PLAY_VOICE:
        // Could play as SFX or have dedicated voice channel
        audioFacade.playSfx(cmd.getTrackId());
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

  public boolean hasQuickSave() {
    return quickSaveManager != null && quickSaveManager.hasQuickSave();
  }

  public VnScenario getScenario() {
    return scenario;
  }
}
