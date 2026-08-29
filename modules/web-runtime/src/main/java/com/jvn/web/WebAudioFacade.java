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
  private volatile long lastSpectrumReadAtNanos;

  private final java.util.List<AudioBufferSourceNode> sfxSources = new java.util.ArrayList<>();
  private final java.util.List<AudioBufferSourceNode> voiceSources = new java.util.ArrayList<>();
  private final java.util.Map<AudioBufferSourceNode, Boolean> stoppedIntentionally = new java.util.IdentityHashMap<>();

  private final java.util.Map<AudioBufferSourceNode, PausedSource> pausedPoolOffsets = new java.util.IdentityHashMap<>();

  private record PausedSource(AudioBuffer buffer, double offsetSeconds, GainNode destinationGain, AudioChannel channel, String id) {}

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
  public void crossfadeBgm(String trackId, long ms, boolean loop) {
    AudioContext ctx = ensureContext();
    if (bgmSource == null || ms <= 0L) {
      playBgm(trackId, loop);
      return;
    }
    state.loading(trackId, loop);
    loader.getOrLoad(trackId, buffer -> {
      AudioBufferSourceNode oldSource = bgmSource;
      GainNode oldFadeGain = ctx.createGain();
      GainNode newFadeGain = ctx.createGain();
      oldFadeGain.getGain().setValue(1f);
      newFadeGain.getGain().setValue(0f);

      oldSource.disconnect();
      oldSource.connect(oldFadeGain);
      oldFadeGain.connect(bgmGain);

      AudioBufferSourceNode newSource = ctx.createBufferSource();
      newSource.setBuffer(buffer);
      newSource.setLoop(loop);
      newSource.connect(newFadeGain);
      newFadeGain.connect(bgmGain);

      double rampEndTime = ctx.getCurrentTime() + ms / 1000.0;
      oldFadeGain.getGain().linearRampToValueAtTime(0f, rampEndTime);
      newFadeGain.getGain().linearRampToValueAtTime(1f, rampEndTime);

      // The old source's onEnded is irrelevant during the crossfade window: it is
      // always stopped intentionally by the setTimeout below before it could ever
      // reach a natural end (crossfade duration is finite and the old source keeps
      // playing until then), so it needs no listener at all here.
      //
      // The new source's onEnded uses the exact same per-node completion-guard
      // pattern as startBgmSourceFrom (Task 3): a boolean[] flag is created here,
      // closed over by this source's onEnded callback, and stored as the
      // instance-level bgmSourceStopFlag only once newSource is promoted to
      // bgmSource below. Because the flag array is scoped to this one node (not a
      // shared field read/written by unrelated sources), it stays correct
      // regardless of what happens to oldSource or any subsequent source.
      final boolean[] newSourceStopFlag = new boolean[1];
      String trackIdAtStart = trackId;
      newSource.onEnded(event -> {
        if (!newSourceStopFlag[0]) {
          state.completed(AudioChannel.BGM, trackIdAtStart);
        }
      });
      newSource.start();

      org.teavm.jso.browser.Window.setTimeout(() -> {
        try {
          oldSource.stop();
        } catch (RuntimeException ignored) {
          // Already stopped/ended.
        }
        oldSource.disconnect();
        oldFadeGain.disconnect();
        newSource.disconnect();
        newFadeGain.disconnect();
        newSource.connect(bgmGain);

        bgmSource = newSource;
        bgmSourceStopFlag = newSourceStopFlag;
        bgmBuffer = buffer;
        bgmTrackId = trackId;
        bgmLoop = loop;
        bgmStartedAtContextTime = ctx.getCurrentTime();
        bgmPlaybackOffsetSeconds = 0.0;
        state.started(AudioChannel.BGM, trackId);
      }, (double) ms);
    });
  }

  @Override
  public void fadeOutBgm(long ms) {
    if (bgmSource == null || context == null || ms <= 0L) {
      stopBgm();
      return;
    }
    double rampEndTime = context.getCurrentTime() + ms / 1000.0;
    bgmGain.getGain().linearRampToValueAtTime(0f, rampEndTime);
    org.teavm.jso.browser.Window.setTimeout(() -> {
      stopBgm();
      bgmGain.getGain().setValue(state.mix().bgmVolume());
    }, (double) ms);
  }

  @Override
  public void playSfx(String sfxId) {
    playPooled(sfxId, sfxSources, sfxGain, MAX_SFX_SOURCES, AudioChannel.SFX);
  }

  @Override
  public void playVoice(String voiceId) {
    playPooled(voiceId, voiceSources, voiceGain, MAX_VOICE_SOURCES, AudioChannel.VOICE);
  }

  private void playPooled(
      String id,
      java.util.List<AudioBufferSourceNode> pool,
      GainNode destinationGain,
      int cap,
      AudioChannel channel
  ) {
    ensureContext();
    loader.getOrLoad(id, buffer -> {
      while (pool.size() >= cap) {
        stopPooledSource(pool.remove(0));
      }
      AudioBufferSourceNode source = context.createBufferSource();
      source.setBuffer(buffer);
      source.connect(destinationGain);
      stoppedIntentionally.put(source, false);
      source.onEnded(event -> {
        pool.remove(source);
        boolean intentional = Boolean.TRUE.equals(stoppedIntentionally.remove(source));
        if (!intentional) state.completed(channel, id);
      });
      source.start();
      pool.add(source);
      state.started(channel, id);
    });
  }

  private void stopPooledSource(AudioBufferSourceNode source) {
    if (source == null) return;
    stoppedIntentionally.put(source, true);
    try {
      source.stop();
    } catch (RuntimeException ignored) {
      // Already stopped/ended.
    }
  }

  @Override
  public void stopSfx() {
    for (AudioBufferSourceNode source : new java.util.ArrayList<>(sfxSources)) stopPooledSource(source);
    sfxSources.clear();
  }

  @Override
  public void stopVoice() {
    for (AudioBufferSourceNode source : new java.util.ArrayList<>(voiceSources)) stopPooledSource(source);
    voiceSources.clear();
  }

  @Override
  public void stopAllAudio() {
    stopVoice();
    stopSfx();
    stopBgm();
  }

  @Override
  public void pauseAllAudio() {
    pauseBgm();
    pausePool(sfxSources, sfxGain, AudioChannel.SFX);
    pausePool(voiceSources, voiceGain, AudioChannel.VOICE);
  }

  @Override
  public void resumeAllAudio() {
    resumeBgm();
    resumePool(sfxSources);
    resumePool(voiceSources);
  }

  private void pausePool(java.util.List<AudioBufferSourceNode> pool, GainNode destinationGain, AudioChannel channel) {
    if (context == null) return;
    for (AudioBufferSourceNode source : new java.util.ArrayList<>(pool)) {
      AudioBuffer buffer = source.getBuffer();
      // Pooled sources have no per-node "started at" bookkeeping (unlike BGM),
      // so a paused SFX/voice resumes from the beginning of its buffer rather
      // than mid-playback — an accepted simplification since SFX/voice clips
      // are short fire-and-forget sounds, not long tracks like BGM.
      pausedPoolOffsets.put(source, new PausedSource(buffer, 0.0, destinationGain, channel, ""));
      stopPooledSource(source);
      pool.remove(source);
    }
  }

  private void resumePool(java.util.List<AudioBufferSourceNode> pool) {
    if (context == null) return;
    java.util.List<AudioBufferSourceNode> toResume = new java.util.ArrayList<>(pausedPoolOffsets.keySet());
    for (AudioBufferSourceNode oldSource : toResume) {
      PausedSource paused = pausedPoolOffsets.remove(oldSource);
      if (paused == null) continue;
      AudioBufferSourceNode fresh = context.createBufferSource();
      fresh.setBuffer(paused.buffer());
      fresh.connect(paused.destinationGain());
      stoppedIntentionally.put(fresh, false);
      java.util.List<AudioBufferSourceNode> targetPool = paused.channel() == AudioChannel.SFX ? sfxSources : voiceSources;
      fresh.onEnded(event -> {
        targetPool.remove(fresh);
        boolean intentional = Boolean.TRUE.equals(stoppedIntentionally.remove(fresh));
        if (!intentional) state.completed(paused.channel(), paused.id());
      });
      fresh.start(0, paused.offsetSeconds());
      targetPool.add(fresh);
    }
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
    double position = bgmSource != null && context != null
        ? bgmPlaybackOffsetSeconds + Math.max(0.0, context.getCurrentTime() - bgmStartedAtContextTime)
        : bgmPlaybackOffsetSeconds;
    double duration = bgmBuffer != null ? bgmBuffer.getDuration() : 0.0;
    return state.snapshot(position, duration, sfxSources.size(), voiceSources.size());
  }

  @Override public void addListener(AudioListener listener) { state.addListener(listener); }
  @Override public void removeListener(AudioListener listener) { state.removeListener(listener); }

  @Override
  public float[] getBgmSpectrumMagnitudes() {
    if (bgmAnalyser == null) return null;
    float[] data = new float[bgmAnalyser.getFrequencyBinCount()];
    bgmAnalyser.getFloatFrequencyData(data);
    lastSpectrumReadAtNanos = System.nanoTime();
    return data;
  }

  @Override
  public boolean supportsBgmSpectrum() {
    return bgmAnalyser != null;
  }

  @Override
  public long getBgmSpectrumUpdatedAtNanos() {
    return lastSpectrumReadAtNanos;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    stopAllAudio();
    state.closed();
    if (context != null) context.close();
  }
}
