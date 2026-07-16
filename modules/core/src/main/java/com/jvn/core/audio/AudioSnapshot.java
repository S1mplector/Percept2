package com.jvn.core.audio;

/** Immutable diagnostic snapshot of an audio backend. */
public record AudioSnapshot(
    String backendId,
    AudioPlaybackStatus bgmStatus,
    String bgmTrackId,
    boolean bgmLooping,
    double bgmPositionSeconds,
    double bgmDurationSeconds,
    float masterVolume,
    float bgmVolume,
    float sfxVolume,
    float voiceVolume,
    boolean muted,
    int activeSfxCount,
    int activeVoiceCount,
    String lastError
) {
  public AudioSnapshot {
    backendId = backendId == null || backendId.isBlank() ? "unknown" : backendId;
    bgmStatus = bgmStatus == null ? AudioPlaybackStatus.STOPPED : bgmStatus;
    bgmTrackId = bgmTrackId == null ? "" : bgmTrackId;
    lastError = lastError == null ? "" : lastError;
    bgmPositionSeconds = finiteNonNegative(bgmPositionSeconds);
    bgmDurationSeconds = finiteNonNegative(bgmDurationSeconds);
    activeSfxCount = Math.max(0, activeSfxCount);
    activeVoiceCount = Math.max(0, activeVoiceCount);
  }

  public static AudioSnapshot unavailable() {
    return new AudioSnapshot(
        "unknown", AudioPlaybackStatus.STOPPED, "", false, 0, 0,
        1f, 1f, 1f, 1f, false, 0, 0, "");
  }

  private static double finiteNonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
  }
}
