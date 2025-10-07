package com.jvn.core.vn;

import com.jvn.core.scene.Scene;

/**
 * Scene implementation for visual novel gameplay
 */
public class VnScene implements Scene {
  private final VnState state;
  private VnScenario scenario;
  private long textRevealTimer;
  private static final long TEXT_REVEAL_SPEED_MS = 30; // milliseconds per character

  public VnScene(VnScenario scenario) {
    this.scenario = scenario;
    this.state = new VnState();
    this.state.setScenario(scenario);
    this.textRevealTimer = 0;
  }

  public VnState getState() {
    return state;
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

    // Handle text reveal animation
    if (currentNode.getType() == VnNodeType.DIALOGUE) {
      DialogueLine dialogue = currentNode.getDialogue();
      if (dialogue != null) {
        int textLength = dialogue.getText().length();
        if (state.getTextRevealProgress() < textLength) {
          textRevealTimer += deltaMs;
          if (textRevealTimer >= TEXT_REVEAL_SPEED_MS) {
            state.incrementTextReveal(1);
            textRevealTimer = 0;
          }
        } else {
          state.setWaitingForInput(true);
        }
      }
    } else if (currentNode.getType() == VnNodeType.CHOICE) {
      state.setWaitingForInput(true);
    }
  }

  /**
   * Advance to the next node (called when player clicks/presses space)
   */
  public void advance() {
    VnNode current = state.getCurrentNode();
    if (current == null) return;

    // If text is still revealing, complete it instantly
    if (current.getType() == VnNodeType.DIALOGUE) {
      DialogueLine dialogue = current.getDialogue();
      if (dialogue != null && state.getTextRevealProgress() < dialogue.getText().length()) {
        state.setTextRevealProgress(dialogue.getText().length());
        return;
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
        processJumpNode(node);
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

  public VnScenario getScenario() {
    return scenario;
  }
}
