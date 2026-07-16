package com.jvn.core.audio;

/** Lifecycle notification emitted by audio backends. */
public record AudioEvent(
    Type type,
    AudioChannel channel,
    String trackId,
    String message,
    long emittedAtNanos
) {
  public enum Type {
    LOADING,
    STARTED,
    PAUSED,
    RESUMED,
    STOPPED,
    COMPLETED,
    ERROR,
    MIX_CHANGED,
    CLOSED
  }

  public AudioEvent {
    type = type == null ? Type.ERROR : type;
    channel = channel == null ? AudioChannel.MASTER : channel;
    trackId = trackId == null ? "" : trackId;
    message = message == null ? "" : message;
    if (emittedAtNanos <= 0L) emittedAtNanos = System.nanoTime();
  }
}
