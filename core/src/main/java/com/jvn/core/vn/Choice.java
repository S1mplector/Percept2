package com.jvn.core.vn;

/**
 * Represents a choice option in a branching visual novel scenario
 */
public class Choice {
  private final String text;
  private final String targetLabel;
  private final boolean enabled;

  private Choice(Builder builder) {
    this.text = builder.text;
    this.targetLabel = builder.targetLabel;
    this.enabled = builder.enabled;
  }

  public String getText() { return text; }
  public String getTargetLabel() { return targetLabel; }
  public boolean isEnabled() { return enabled; }

  public static Builder builder() { return new Builder(); }

  public static class Builder {
    private String text = "";
    private String targetLabel = null;
    private boolean enabled = true;

    public Builder text(String text) { this.text = text; return this; }
    public Builder targetLabel(String label) { this.targetLabel = label; return this; }
    public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
    public Choice build() { return new Choice(this); }
  }
}
