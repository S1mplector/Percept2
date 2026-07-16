package com.jvn.core.audio;

/** Backend-independent state of the primary BGM transport. */
public enum AudioPlaybackStatus {
  STOPPED,
  LOADING,
  PLAYING,
  PAUSED,
  ERROR,
  CLOSED
}
