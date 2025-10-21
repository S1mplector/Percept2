package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single node in the visual novel script graph
 */
public class VnNode {
  private final VnNodeType type;
  private final DialogueLine dialogue;
  private final List<Choice> choices;
  private final String backgroundId;
  private final String jumpLabel;
  private final VnAudioCommand audioCommand;
  private final VnTransition transition;
  private final long waitMs;
  private final String characterToShow;
  private final String characterToHide;
  private final CharacterPosition showPosition;
  private final String showExpression;

  private VnNode(Builder builder) {
    this.type = builder.type;
    this.dialogue = builder.dialogue;
    this.choices = builder.choices != null ? new ArrayList<>(builder.choices) : new ArrayList<>();
    this.backgroundId = builder.backgroundId;
    this.jumpLabel = builder.jumpLabel;
    this.audioCommand = builder.audioCommand;
    this.transition = builder.transition;
    this.waitMs = builder.waitMs;
    this.characterToShow = builder.characterToShow;
    this.characterToHide = builder.characterToHide;
    this.showPosition = builder.showPosition;
    this.showExpression = builder.showExpression;
  }

  public VnNodeType getType() { return type; }
  public DialogueLine getDialogue() { return dialogue; }
  public List<Choice> getChoices() { return choices; }
  public String getBackgroundId() { return backgroundId; }
  public String getJumpLabel() { return jumpLabel; }
  public VnAudioCommand getAudioCommand() { return audioCommand; }
  public VnTransition getTransition() { return transition; }
  public long getWaitMs() { return waitMs; }
  public String getCharacterToShow() { return characterToShow; }
  public String getCharacterToHide() { return characterToHide; }
  public CharacterPosition getShowPosition() { return showPosition; }
  public String getShowExpression() { return showExpression; }

  public static Builder builder(VnNodeType type) { return new Builder(type); }

  public static class Builder {
    private final VnNodeType type;
    private DialogueLine dialogue;
    private List<Choice> choices;
    private String backgroundId;
    private String jumpLabel;
    private VnAudioCommand audioCommand;
    private VnTransition transition;
    private long waitMs;
    private String characterToShow;
    private String characterToHide;
    private CharacterPosition showPosition;
    private String showExpression = "neutral";

    private Builder(VnNodeType type) { this.type = type; }

    public Builder dialogue(DialogueLine dialogue) { this.dialogue = dialogue; return this; }
    public Builder choices(List<Choice> choices) { this.choices = choices; return this; }
    public Builder backgroundId(String id) { this.backgroundId = id; return this; }
    public Builder jumpLabel(String label) { this.jumpLabel = label; return this; }
    public Builder audioCommand(VnAudioCommand cmd) { this.audioCommand = cmd; return this; }
    public Builder transition(VnTransition transition) { this.transition = transition; return this; }
    public Builder waitMs(long ms) { this.waitMs = ms; return this; }
    public Builder characterToShow(String id) { this.characterToShow = id; return this; }
    public Builder characterToHide(String id) { this.characterToHide = id; return this; }
    public Builder showPosition(CharacterPosition pos) { this.showPosition = pos; return this; }
    public Builder showExpression(String expr) { this.showExpression = expr; return this; }
    public VnNode build() { return new VnNode(this); }
  }
}
