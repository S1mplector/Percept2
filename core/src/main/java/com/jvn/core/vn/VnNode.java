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

  private VnNode(Builder builder) {
    this.type = builder.type;
    this.dialogue = builder.dialogue;
    this.choices = builder.choices != null ? new ArrayList<>(builder.choices) : new ArrayList<>();
    this.backgroundId = builder.backgroundId;
    this.jumpLabel = builder.jumpLabel;
  }

  public VnNodeType getType() { return type; }
  public DialogueLine getDialogue() { return dialogue; }
  public List<Choice> getChoices() { return choices; }
  public String getBackgroundId() { return backgroundId; }
  public String getJumpLabel() { return jumpLabel; }

  public static Builder builder(VnNodeType type) { return new Builder(type); }

  public static class Builder {
    private final VnNodeType type;
    private DialogueLine dialogue;
    private List<Choice> choices;
    private String backgroundId;
    private String jumpLabel;

    private Builder(VnNodeType type) { this.type = type; }

    public Builder dialogue(DialogueLine dialogue) { this.dialogue = dialogue; return this; }
    public Builder choices(List<Choice> choices) { this.choices = choices; return this; }
    public Builder backgroundId(String id) { this.backgroundId = id; return this; }
    public Builder jumpLabel(String label) { this.jumpLabel = label; return this; }
    public VnNode build() { return new VnNode(this); }
  }
}
