package com.jvn.core.audio;

/** Feature declaration for an {@link AudioFacade} implementation. */
public record AudioCapabilities(
    boolean dedicatedVoiceChannel,
    boolean overlappingSfx,
    boolean pauseResume,
    boolean seek,
    boolean crossfade,
    boolean fadeOut,
    boolean spectrum,
    boolean lifecycleEvents
) {
  public static AudioCapabilities basic() {
    return new AudioCapabilities(false, false, false, false, false, false, false, false);
  }

  public static AudioCapabilities full(boolean spectrum) {
    return new AudioCapabilities(true, true, true, true, true, true, spectrum, true);
  }
}
