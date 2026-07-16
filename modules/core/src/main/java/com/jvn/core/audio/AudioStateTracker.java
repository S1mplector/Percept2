package com.jvn.core.audio;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reusable state/event component for platform audio adapters.
 *
 * <p>Backend callbacks may arrive on media, JavaFX, or scheduler threads, so all published state is
 * volatile and listener storage is copy-on-write. Listener failures never break playback.</p>
 */
public final class AudioStateTracker {
  private final String backendId;
  private final AudioCapabilities capabilities;
  private final AudioMix mix = new AudioMix();
  private final List<AudioListener> listeners = new CopyOnWriteArrayList<>();
  private volatile AudioPlaybackStatus bgmStatus = AudioPlaybackStatus.STOPPED;
  private volatile String bgmTrackId = "";
  private volatile boolean bgmLooping;
  private volatile String lastError = "";

  public AudioStateTracker(String backendId, AudioCapabilities capabilities) {
    this.backendId = backendId == null || backendId.isBlank() ? "unknown" : backendId;
    this.capabilities = capabilities == null ? AudioCapabilities.basic() : capabilities;
  }

  public String backendId() { return backendId; }
  public AudioCapabilities capabilities() { return capabilities; }
  public AudioMix mix() { return mix; }

  public void addListener(AudioListener listener) {
    if (listener != null) listeners.add(listener);
  }

  public void removeListener(AudioListener listener) {
    listeners.remove(listener);
  }

  public void loading(String trackId, boolean loop) {
    bgmTrackId = normalize(trackId);
    bgmLooping = loop;
    bgmStatus = AudioPlaybackStatus.LOADING;
    emit(AudioEvent.Type.LOADING, AudioChannel.BGM, trackId, "");
  }

  public void started(AudioChannel channel, String trackId) {
    if (channel == AudioChannel.BGM) {
      if (trackId != null && !trackId.isBlank()) bgmTrackId = trackId;
      bgmStatus = AudioPlaybackStatus.PLAYING;
      lastError = "";
    }
    emit(AudioEvent.Type.STARTED, channel, trackId, "");
  }

  public void paused() {
    if (bgmStatus == AudioPlaybackStatus.PLAYING) bgmStatus = AudioPlaybackStatus.PAUSED;
    emit(AudioEvent.Type.PAUSED, AudioChannel.BGM, bgmTrackId, "");
  }

  public void resumed() {
    if (!bgmTrackId.isBlank()) bgmStatus = AudioPlaybackStatus.PLAYING;
    emit(AudioEvent.Type.RESUMED, AudioChannel.BGM, bgmTrackId, "");
  }

  public void stopped(AudioChannel channel, String trackId) {
    if (channel == AudioChannel.BGM) {
      bgmStatus = AudioPlaybackStatus.STOPPED;
      bgmTrackId = "";
      bgmLooping = false;
    }
    emit(AudioEvent.Type.STOPPED, channel, trackId, "");
  }

  public void completed(AudioChannel channel, String trackId) {
    if (channel == AudioChannel.BGM && !bgmLooping) bgmStatus = AudioPlaybackStatus.STOPPED;
    emit(AudioEvent.Type.COMPLETED, channel, trackId, "");
  }

  public void mixChanged(AudioChannel channel) {
    emit(AudioEvent.Type.MIX_CHANGED, channel, bgmTrackId, "");
  }

  public void error(AudioChannel channel, String trackId, String message) {
    lastError = normalize(message);
    if (channel == AudioChannel.BGM) bgmStatus = AudioPlaybackStatus.ERROR;
    emit(AudioEvent.Type.ERROR, channel, trackId, lastError);
  }

  public void closed() {
    bgmStatus = AudioPlaybackStatus.CLOSED;
    emit(AudioEvent.Type.CLOSED, AudioChannel.MASTER, "", "");
    listeners.clear();
  }

  public AudioSnapshot snapshot(double position, double duration, int sfxCount, int voiceCount) {
    return new AudioSnapshot(
        backendId, bgmStatus, bgmTrackId, bgmLooping, position, duration,
        mix.masterVolume(), mix.bgmVolume(), mix.sfxVolume(), mix.voiceVolume(), mix.muted(),
        sfxCount, voiceCount, lastError);
  }

  private void emit(AudioEvent.Type type, AudioChannel channel, String trackId, String message) {
    AudioEvent event = new AudioEvent(type, channel, trackId, message, System.nanoTime());
    for (AudioListener listener : listeners) {
      try {
        listener.onAudioEvent(event);
      } catch (RuntimeException ignored) {
        // Observers are diagnostic consumers and must not affect audio playback.
      }
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
