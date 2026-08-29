package com.jvn.web;

import java.io.File;

import org.teavm.jso.webaudio.AnalyserNode;
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
    throw new UnsupportedOperationException("implemented in Task 3");
  }

  @Override
  public void stopBgm() {
    throw new UnsupportedOperationException("implemented in Task 3");
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
