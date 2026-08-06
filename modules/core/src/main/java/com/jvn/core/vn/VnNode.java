package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.List;

import com.jvn.core.animation.Easing;

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
  private final Integer showLayerOrder;
  private final String displaySlot;
  private final Easing.Type moveEasingType;
  private final long moveDurationMs;
  private final long expressionDurationMs;
  private final VnExternalCommand externalCommand;
  private final VnParticleCommand particleCommand;
  private final String groupTargetId;
  private final String groupParentId;
  private final int sourceLine;

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
    this.showLayerOrder = builder.showLayerOrder;
    this.displaySlot = builder.displaySlot;
    this.moveEasingType = builder.moveEasingType;
    this.moveDurationMs = builder.moveDurationMs;
    this.expressionDurationMs = builder.expressionDurationMs;
    this.externalCommand = builder.externalCommand;
    this.particleCommand = builder.particleCommand;
    this.groupTargetId = builder.groupTargetId;
    this.groupParentId = builder.groupParentId;
    this.sourceLine = builder.sourceLine;
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
  public Integer getShowLayerOrder() { return showLayerOrder; }
  public String getDisplaySlot() { return displaySlot; }
  public Easing.Type getMoveEasingType() { return moveEasingType; }
  public long getMoveDurationMs() { return moveDurationMs; }
  /**
   * Expression transition duration for move nodes.
   *
   * @return milliseconds, or {@code -1} when the runtime default should be used
   */
  public long getExpressionDurationMs() { return expressionDurationMs; }
  public VnExternalCommand getExternalCommand() { return externalCommand; }
  public VnParticleCommand getParticleCommand() { return particleCommand; }
  public String getGroupTargetId() { return groupTargetId; }
  public String getGroupParentId() { return groupParentId; }
  public int getSourceLine() { return sourceLine; }

  VnNode withSourceLine(int line) {
    return builder(type)
        .dialogue(dialogue)
        .choices(choices)
        .backgroundId(backgroundId)
        .jumpLabel(jumpLabel)
        .audioCommand(audioCommand)
        .transition(transition)
        .waitMs(waitMs)
        .characterToShow(characterToShow)
        .characterToHide(characterToHide)
        .showPosition(showPosition)
        .showExpression(showExpression)
        .showLayerOrder(showLayerOrder)
        .displaySlot(displaySlot)
        .moveEasingType(moveEasingType)
        .moveDurationMs(moveDurationMs)
        .expressionDurationMs(expressionDurationMs)
        .external(externalCommand)
        .particleCommand(particleCommand)
        .groupTargetId(groupTargetId)
        .groupParentId(groupParentId)
        .sourceLine(line)
        .build();
  }

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
    private Integer showLayerOrder;
    private String displaySlot;
    private Easing.Type moveEasingType;
    private long moveDurationMs;
    private long expressionDurationMs = -1L;
    private VnExternalCommand externalCommand;
    private VnParticleCommand particleCommand;
    private String groupTargetId;
    private String groupParentId;
    private int sourceLine;

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
    public Builder showLayerOrder(Integer layerOrder) { this.showLayerOrder = layerOrder; return this; }
    public Builder displaySlot(String slot) { this.displaySlot = slot; return this; }
    public Builder moveEasingType(Easing.Type easing) { this.moveEasingType = easing; return this; }
    public Builder moveDurationMs(long ms) { this.moveDurationMs = ms; return this; }
    public Builder expressionDurationMs(long ms) { this.expressionDurationMs = ms; return this; }
    public Builder external(VnExternalCommand cmd) { this.externalCommand = cmd; return this; }
    public Builder particleCommand(VnParticleCommand cmd) { this.particleCommand = cmd; return this; }
    public Builder groupTargetId(String targetId) { this.groupTargetId = targetId; return this; }
    public Builder groupParentId(String parentId) { this.groupParentId = parentId; return this; }
    public Builder sourceLine(int line) { this.sourceLine = line; return this; }
    public VnNode build() { return new VnNode(this); }
  }
}
