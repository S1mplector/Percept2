package com.jvn.core.vn;

/**
 * Represents an audio command in a VN scenario
 */
public class VnAudioCommand {
  private final AudioCommandType type;
  private final String trackId;
  private final boolean loop;
  private final float volume;

  private VnAudioCommand(Builder builder) {
    this.type = builder.type;
    this.trackId = builder.trackId;
    this.loop = builder.loop;
    this.volume = builder.volume;
  }

  public AudioCommandType getType() { return type; }
  public String getTrackId() { return trackId; }
  public boolean isLoop() { return loop; }
  public float getVolume() { return volume; }

  public static Builder builder(AudioCommandType type) { return new Builder(type); }

  public static class Builder {
    private final AudioCommandType type;
    private String trackId;
    private boolean loop = true;
    private float volume = 1.0f;

    private Builder(AudioCommandType type) { this.type = type; }

    public Builder trackId(String id) { this.trackId = id; return this; }
    public Builder loop(boolean loop) { this.loop = loop; return this; }
    public Builder volume(float volume) { this.volume = volume; return this; }
    public VnAudioCommand build() { return new VnAudioCommand(this); }
  }

  public enum AudioCommandType {
    PLAY_BGM,
    STOP_BGM,
    FADE_OUT_BGM,
    PLAY_SFX,
    PLAY_VOICE
  }
}
