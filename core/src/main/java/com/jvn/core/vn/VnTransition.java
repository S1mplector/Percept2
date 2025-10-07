package com.jvn.core.vn;

/**
 * Represents a visual transition effect
 */
public class VnTransition {
  private final TransitionType type;
  private final long durationMs;
  private final String targetBackgroundId;

  private VnTransition(Builder builder) {
    this.type = builder.type;
    this.durationMs = builder.durationMs;
    this.targetBackgroundId = builder.targetBackgroundId;
  }

  public TransitionType getType() { return type; }
  public long getDurationMs() { return durationMs; }
  public String getTargetBackgroundId() { return targetBackgroundId; }

  public static Builder builder(TransitionType type) { return new Builder(type); }

  public static class Builder {
    private final TransitionType type;
    private long durationMs = 500;
    private String targetBackgroundId;

    private Builder(TransitionType type) { this.type = type; }

    public Builder durationMs(long ms) { this.durationMs = ms; return this; }
    public Builder targetBackgroundId(String id) { this.targetBackgroundId = id; return this; }
    public VnTransition build() { return new VnTransition(this); }
  }

  public enum TransitionType {
    NONE,
    FADE,
    DISSOLVE,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    WIPE
  }
}
