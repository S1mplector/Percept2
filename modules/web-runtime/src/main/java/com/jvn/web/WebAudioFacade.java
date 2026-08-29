package com.jvn.web;

import java.io.File;

import org.teavm.jso.webaudio.AnalyserNode;
import org.teavm.jso.webaudio.AudioBuffer;
import org.teavm.jso.webaudio.AudioBufferSourceNode;
import org.teavm.jso.webaudio.AudioContext;
import org.teavm.jso.webaudio.GainNode;

import com.jvn.core.audio.AudioCapabilities;
import com.jvn.core.audio.AudioChannel;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.audio.AudioListener;
import com.jvn.core.audio.AudioSnapshot;
import com.jvn.core.audio.AudioStateTracker;

/**
 * Web Audio-backed {@link AudioFacade}. The {@link AudioContext} and its gain
 * graph are created lazily on first playback call so context creation lands
 * inside a user-gesture-driven call path (satisfying the browser's autoplay
 * policy) rather than at construction time.
 */
public final class WebAudioFacade implements AudioFacade {
  private static final int MAX_SFX_SOURCES = 32;
  private static final int MAX_VOICE_SOURCES = 8;

  private final AudioStateTracker state = new AudioStateTracker("webaudio", AudioCapabilities.full(true));
  private AudioContext context;
  private GainNode bgmGain;
  private GainNode sfxGain;
  private GainNode voiceGain;
  private GainNode masterGain;
  private AnalyserNode bgmAnalyser;
  private WebAudioAssetLoader loader;
  private volatile boolean closed;

  private AudioBufferSourceNode bgmSource;
  private boolean[] bgmSourceStopFlag;
  private AudioBuffer bgmBuffer;
  private String bgmTrackId = "";
  private boolean bgmLoop;
  private double bgmStartedAtContextTime;
  private double bgmPlaybackOffsetSeconds;

  @Override
  public void setProjectRoot(File root) {
    // Browser builds have no filesystem project root; asset ids resolve via
    // WebAudioAssetLoader.resolveUrl instead.
  }

  private AudioContext ensureContext() {
    if (context == null) {
      context = new AudioContext();
      masterGain = context.createGain();
      masterGain.getGain().setValue(state.mix().muted() ? 0f : state.mix().masterVolume());
      masterGain.connect(context.getDestination());

      bgmGain = context.createGain();
      bgmGain.getGain().setValue(state.mix().bgmVolume());
      bgmAnalyser = context.createAnalyser();
      bgmAnalyser.setFftSize(128);
      bgmGain.connect(masterGain);
      bgmGain.connect(bgmAnalyser);

      sfxGain = context.createGain();
      sfxGain.getGain().setValue(state.mix().sfxVolume());
      sfxGain.connect(masterGain);

      voiceGain = context.createGain();
      voiceGain.getGain().setValue(state.mix().voiceVolume());
      voiceGain.connect(masterGain);

      loader = new WebAudioAssetLoader(context);
    }
    return context;
  }

  @Override
  public void playBgm(String trackId, boolean loop) {
    AudioContext ctx = ensureContext();
    state.loading(trackId, loop);
    loader.getOrLoad(trackId, buffer -> {
      stopBgmSourceIfPlaying();
      bgmBuffer = buffer;
      bgmTrackId = trackId;
      bgmLoop = loop;
      bgmPlaybackOffsetSeconds = 0.0;
      startBgmSourceFrom(0.0);
      state.started(AudioChannel.BGM, trackId);
    });
  }

  @Override
  public void stopBgm() {
    stopBgmSourceIfPlaying();
    bgmBuffer = null;
    bgmTrackId = "";
    bgmPlaybackOffsetSeconds = 0.0;
    state.stopped(AudioChannel.BGM, "");
  }

  private void startBgmSourceFrom(double offsetSeconds) {
    AudioContext ctx = ensureContext();
    AudioBufferSourceNode source = ctx.createBufferSource();
    source.setBuffer(bgmBuffer);
    source.setLoop(bgmLoop);
    source.connect(bgmGain);
    // Per-node completion guard: stop() fires 'ended' asynchronously, so a
    // shared instance field could be reset by a subsequently-started source
    // before the old source's queued event fires. Capture a flag scoped to
    // THIS node and close over it directly.
    final boolean[] stopFlag = new boolean[1];
    String trackIdAtStart = bgmTrackId;
    source.onEnded(event -> {
      if (!stopFlag[0]) {
        state.completed(AudioChannel.BGM, trackIdAtStart);
      }
    });
    source.start(0, offsetSeconds);
    bgmSource = source;
    bgmSourceStopFlag = stopFlag;
    bgmStartedAtContextTime = ctx.getCurrentTime();
    bgmPlaybackOffsetSeconds = offsetSeconds;
  }

  private void stopBgmSourceIfPlaying() {
    if (bgmSource == null) return;
    if (bgmSourceStopFlag != null) bgmSourceStopFlag[0] = true;
    try {
      bgmSource.stop();
    } catch (RuntimeException ignored) {
      // Already stopped/ended; nothing further to do.
    }
    bgmSource = null;
    bgmSourceStopFlag = null;
  }

  @Override
  public void pauseBgm() {
    if (bgmSource == null || bgmBuffer == null) return;
    double elapsed = context.getCurrentTime() - bgmStartedAtContextTime;
    bgmPlaybackOffsetSeconds += Math.max(0.0, elapsed);
    stopBgmSourceIfPlaying();
    state.paused();
  }

  @Override
  public void resumeBgm() {
    if (bgmBuffer == null) return;
    startBgmSourceFrom(bgmPlaybackOffsetSeconds);
    state.resumed();
  }

  @Override
  public void seekBgmSeconds(double seconds) {
    if (bgmBuffer == null || seconds < 0) return;
    stopBgmSourceIfPlaying();
    startBgmSourceFrom(seconds);
  }

  @Override
  public void playSfx(String sfxId) {
    throw new UnsupportedOperationException("implemented in Task 3");
  }

  @Override
  public void setBgmVolume(float volume) {
    state.mix().setBgmVolume(volume);
    state.mixChanged(AudioChannel.BGM);
    if (bgmGain != null) bgmGain.getGain().setValue(state.mix().bgmVolume());
  }

  @Override
  public void setSfxVolume(float volume) {
    state.mix().setSfxVolume(volume);
    state.mixChanged(AudioChannel.SFX);
    if (sfxGain != null) sfxGain.getGain().setValue(state.mix().sfxVolume());
  }

  @Override
  public void setVoiceVolume(float volume) {
    state.mix().setVoiceVolume(volume);
    state.mixChanged(AudioChannel.VOICE);
    if (voiceGain != null) voiceGain.getGain().setValue(state.mix().voiceVolume());
  }

  @Override
  public void setMasterVolume(float volume) {
    state.mix().setMasterVolume(volume);
    state.mixChanged(AudioChannel.MASTER);
    if (masterGain != null) masterGain.getGain().setValue(state.mix().muted() ? 0f : state.mix().masterVolume());
  }

  @Override
  public void setMuted(boolean muted) {
    state.mix().setMuted(muted);
    state.mixChanged(AudioChannel.MASTER);
    if (masterGain != null) masterGain.getGain().setValue(muted ? 0f : state.mix().masterVolume());
  }

  @Override public float getMasterVolume() { return state.mix().masterVolume(); }
  @Override public float getBgmVolume() { return state.mix().bgmVolume(); }
  @Override public float getSfxVolume() { return state.mix().sfxVolume(); }
  @Override public float getVoiceVolume() { return state.mix().voiceVolume(); }
  @Override public boolean isMuted() { return state.mix().muted(); }

  @Override public String backendId() { return state.backendId(); }
  @Override public AudioCapabilities capabilities() { return state.capabilities(); }

  @Override
  public AudioSnapshot snapshot() {
    return state.snapshot(0.0, 0.0, 0, 0);
  }

  @Override public void addListener(AudioListener listener) { state.addListener(listener); }
  @Override public void removeListener(AudioListener listener) { state.removeListener(listener); }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    state.closed();
    if (context != null) context.close();
  }
}
