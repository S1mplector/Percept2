package com.jvn.core.vn;

/**
 * Represents a single line of dialogue in a visual novel
 */
public class DialogueLine {
  private final String speakerName;
  private final String text;
  private final String characterId;
  private final String expression;
  private final CharacterPosition position;

  private DialogueLine(Builder builder) {
    this.speakerName = builder.speakerName;
    this.text = builder.text;
    this.characterId = builder.characterId;
    this.expression = builder.expression;
    this.position = builder.position;
  }

  public String getSpeakerName() { return speakerName; }
  public String getText() { return text; }
  public String getCharacterId() { return characterId; }
  public String getExpression() { return expression; }
  public CharacterPosition getPosition() { return position; }

  public static Builder builder() { return new Builder(); }

  public static class Builder {
    private String speakerName = "";
    private String text = "";
    private String characterId = null;
    private String expression = "neutral";
    private CharacterPosition position = CharacterPosition.CENTER;

    public Builder speakerName(String name) { this.speakerName = name; return this; }
    public Builder text(String text) { this.text = text; return this; }
    public Builder characterId(String id) { this.characterId = id; return this; }
    public Builder expression(String expression) { this.expression = expression; return this; }
    public Builder position(CharacterPosition position) { this.position = position; return this; }
    public DialogueLine build() { return new DialogueLine(this); }
  }
}
