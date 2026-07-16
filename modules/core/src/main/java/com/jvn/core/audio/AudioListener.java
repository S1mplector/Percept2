package com.jvn.core.audio;

/** Receives backend lifecycle and diagnostic events. */
@FunctionalInterface
public interface AudioListener {
  void onAudioEvent(AudioEvent event);
}
