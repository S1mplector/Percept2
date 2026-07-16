package com.jvn.audio.simp3;

import com.jvn.core.assets.AudioAssetResolver;
import com.jvn.core.audio.AudioCapabilities;
import com.jvn.core.audio.AudioChannel;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.audio.AudioListener;
import com.jvn.core.audio.AudioSnapshot;
import com.jvn.core.audio.AudioStateTracker;
import com.musicplayer.core.audio.AudioEngine;
import com.musicplayer.core.audio.HybridAudioEngine;
import com.musicplayer.data.models.Song;
import javafx.scene.media.AudioSpectrumListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Direct Simp3-backed audio facade.
 *
 * This implementation no longer uses reflection bridge indirection.
 */
public class Simp3AudioService implements AudioFacade {
  private static final Logger log = LoggerFactory.getLogger(Simp3AudioService.class);
  private static final int MAX_SFX_ENGINES = 32;
  private static final int MAX_VOICE_ENGINES = 8;

  private AudioEngine bgmEngine;
  private AudioEngine crossEngine;
  private volatile boolean loopBgm = false;
  private final AudioStateTracker state = new AudioStateTracker("simp3-hybrid", AudioCapabilities.full(true));
  private volatile ScheduledFuture<?> crossfadeTask = null;
  private BgmTrack bgmTrack = null;
  private final Map<String, File> extractedAudioCache = new HashMap<>();
  private volatile float[] latestBgmSpectrum;
  private volatile long latestBgmSpectrumUpdatedAtNanos;
  private final AudioSpectrumListener bgmSpectrumListener = (timestamp, duration, magnitudes, phases) -> {
    if (magnitudes == null || magnitudes.length == 0) return;
    float[] copy = new float[magnitudes.length];
    System.arraycopy(magnitudes, 0, copy, 0, magnitudes.length);
    latestBgmSpectrum = copy;
    latestBgmSpectrumUpdatedAtNanos = System.nanoTime();
  };
  private File projectRoot;
  private volatile boolean closed;
  private volatile double crossfadeProgress;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "simp3-audio-fader");
    t.setDaemon(true);
    return t;
  });

  private final List<AudioEngine> sfxEngines = new ArrayList<>();
  private final List<AudioEngine> voiceEngines = new ArrayList<>();

  public Simp3AudioService() {
    this.bgmEngine = newEngine();
    attachBgmSpectrumListener(this.bgmEngine);
    this.bgmEngine.setVolume(state.mix().effective(AudioChannel.BGM));
  }

  @Override
  public synchronized void setProjectRoot(File root) {
    this.projectRoot = root;
  }

  @Override
  public synchronized void playBgm(String trackId, boolean loop) {
    try {
      ensureOpen();
      cancelCrossfadeTaskLocked();
      state.loading(trackId, loop);
      File audioFile = resolveToFile(trackId);
      if (audioFile == null || !audioFile.exists()) {
        log.warn("BGM file not found for trackId={}", trackId);
        state.error(AudioChannel.BGM, trackId, "Audio asset not found");
        return;
      }
      AudioEngine engine = ensureBgmEngine();
      BgmTrack track = new BgmTrack(trackId, audioFile.getAbsolutePath());
      Song song = toSong(track);
      if (!engine.loadSong(song)) {
        log.warn("Failed to load BGM trackId={} file={}", trackId, audioFile.getAbsolutePath());
        state.error(AudioChannel.BGM, trackId, "Backend could not load audio asset");
        return;
      }

      this.loopBgm = loop;
      this.bgmTrack = track;
      engine.setOnSongEnded(createLoopCallback(engine));
      engine.setVolume(state.mix().effective(AudioChannel.BGM));
      engine.play();
      state.started(AudioChannel.BGM, trackId);
    } catch (Exception e) {
      log.error("Error playing BGM {}", trackId, e);
      state.error(AudioChannel.BGM, trackId, e.getMessage());
    }
  }

  @Override
  public synchronized void stopBgm() {
    try {
      cancelCrossfadeTaskLocked();
      safeStop(crossEngine);
      crossEngine = null;
      safeStop(bgmEngine);
      bgmEngine = newEngine();
      attachBgmSpectrumListener(bgmEngine);
      bgmEngine.setVolume(state.mix().effective(AudioChannel.BGM));
      bgmTrack = null;
      loopBgm = false;
      latestBgmSpectrum = null;
      latestBgmSpectrumUpdatedAtNanos = 0L;
      state.stopped(AudioChannel.BGM, "");
    } catch (Exception e) {
      log.debug("stopBgm error", e);
    }
  }

  @Override
  public synchronized void playSfx(String sfxId) {
    playClip(sfxId, state.mix().effective(AudioChannel.SFX), sfxEngines, AudioChannel.SFX, MAX_SFX_ENGINES);
  }

  @Override
  public synchronized void playVoice(String voiceId) {
    playClip(voiceId, state.mix().effective(AudioChannel.VOICE), voiceEngines, AudioChannel.VOICE, MAX_VOICE_ENGINES);
  }

  @Override
  public synchronized void stopSfx() {
    stopEngines(sfxEngines);
  }

  @Override
  public synchronized void stopVoice() {
    stopEngines(voiceEngines);
  }

  @Override
  public synchronized void stopAllAudio() {
    stopSfx();
    stopVoice();
    stopBgm();
  }

  @Override
  public synchronized void setBgmVolume(float volume) {
    state.mix().setBgmVolume(volume);
    state.mixChanged(AudioChannel.BGM);
    applyBgmVolumes();
  }

  @Override
  public synchronized void setSfxVolume(float volume) {
    state.mix().setSfxVolume(volume);
    state.mixChanged(AudioChannel.SFX);
    applyVolume(sfxEngines, state.mix().effective(AudioChannel.SFX));
  }

  @Override
  public synchronized void setVoiceVolume(float volume) {
    state.mix().setVoiceVolume(volume);
    state.mixChanged(AudioChannel.VOICE);
    applyVolume(voiceEngines, state.mix().effective(AudioChannel.VOICE));
  }

  @Override
  public synchronized void setMasterVolume(float volume) {
    state.mix().setMasterVolume(volume);
    state.mixChanged(AudioChannel.MASTER);
    applyAllVolumes();
  }

  @Override
  public synchronized void setMuted(boolean muted) {
    state.mix().setMuted(muted);
    state.mixChanged(AudioChannel.MASTER);
    applyAllVolumes();
  }

  @Override public float getMasterVolume() { return state.mix().masterVolume(); }
  @Override public float getBgmVolume() { return state.mix().bgmVolume(); }
  @Override public float getSfxVolume() { return state.mix().sfxVolume(); }
  @Override public float getVoiceVolume() { return state.mix().voiceVolume(); }
  @Override public boolean isMuted() { return state.mix().muted(); }

  @Override
  public synchronized void pauseBgm() {
    if (bgmEngine != null) bgmEngine.pause();
    state.paused();
  }

  @Override
  public synchronized void resumeBgm() {
    if (bgmEngine != null) {
      bgmEngine.setVolume(state.mix().effective(AudioChannel.BGM));
      bgmEngine.play();
      state.resumed();
    }
  }

  @Override
  public synchronized void pauseAllAudio() {
    pauseBgm();
    for (AudioEngine engine : new ArrayList<>(sfxEngines)) {
      try {
        engine.pause();
      } catch (Exception ignored) {
        // reason: audio backend operation is non-critical; failure is recoverable
        log.trace("pauseAllAudio: sfx engine pause failed: {}", ignored.toString());
      }
    }
    for (AudioEngine engine : new ArrayList<>(voiceEngines)) {
      try {
        engine.pause();
      } catch (Exception ignored) {
        // reason: audio backend operation is non-critical; failure is recoverable
        log.trace("pauseAllAudio: voice engine pause failed: {}", ignored.toString());
      }
    }
  }

  @Override
  public synchronized void resumeAllAudio() {
    resumeBgm();
    for (AudioEngine engine : new ArrayList<>(sfxEngines)) {
      try {
        engine.play();
      } catch (Exception ignored) {
        // reason: audio backend operation is non-critical; failure is recoverable
        log.trace("resumeAllAudio: sfx engine play failed: {}", ignored.toString());
      }
    }
    for (AudioEngine engine : new ArrayList<>(voiceEngines)) {
      try {
        engine.play();
      } catch (Exception ignored) {
        // reason: audio backend operation is non-critical; failure is recoverable
        log.trace("resumeAllAudio: voice engine play failed: {}", ignored.toString());
      }
    }
  }

  @Override
  public synchronized void seekBgmSeconds(double seconds) {
    if (bgmEngine != null) bgmEngine.seek(Math.max(0.0, seconds));
  }

  @Override
  public synchronized void crossfadeBgm(String trackId, long ms, boolean loop) {
    try {
      ensureOpen();
      cancelCrossfadeTaskLocked();
      state.loading(trackId, loop);
      File audioFile = resolveToFile(trackId);
      if (audioFile == null || !audioFile.exists()) {
        log.warn("Crossfade target BGM file not found for trackId={}", trackId);
        state.error(AudioChannel.BGM, trackId, "Audio asset not found");
        return;
      }

      AudioEngine next = newEngine();
      attachBgmSpectrumListener(next);
      BgmTrack nextTrack = new BgmTrack(trackId, audioFile.getAbsolutePath());
      if (!next.loadSong(toSong(nextTrack))) {
        log.warn("Crossfade failed to load target trackId={} file={}", trackId, audioFile.getAbsolutePath());
        state.error(AudioChannel.BGM, trackId, "Backend could not load audio asset");
        return;
      }

      next.setVolume(0.0);
      next.play();

      final AudioEngine prev = this.bgmEngine;
      final double startVol = state.mix().effective(AudioChannel.BGM);
      final long duration = Math.max(0, ms);
      final long start = System.nanoTime();

      this.loopBgm = loop;
      this.bgmTrack = nextTrack;

      if (duration == 0) {
        safeStop(prev);
        this.bgmEngine = next;
        this.crossEngine = null;
        next.setOnSongEnded(createLoopCallback(next));
        next.setVolume(startVol);
        state.started(AudioChannel.BGM, trackId);
        return;
      }

      this.crossEngine = next;
      this.crossfadeProgress = 0.0;
      final ScheduledFuture<?>[] taskRef = new ScheduledFuture<?>[1];
      Runnable tick = () -> {
        try {
          long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
          double p = Math.min(1.0, Math.max(0.0, elapsedMs / (double) duration));
          crossfadeProgress = p;
          double effective = state.mix().effective(AudioChannel.BGM);
          double outVol = effective * (1.0 - p);
          double inVol = effective * p;
          if (prev != null) prev.setVolume(outVol);
          next.setVolume(inVol);
          if (p >= 1.0) {
            safeStop(prev);
            synchronized (Simp3AudioService.this) {
              bgmEngine = next;
              crossEngine = null;
              crossfadeProgress = 0.0;
              if (crossfadeTask == taskRef[0]) {
                crossfadeTask = null;
              }
            }
            next.setOnSongEnded(createLoopCallback(next));
            state.started(AudioChannel.BGM, trackId);
            if (taskRef[0] != null) taskRef[0].cancel(false);
          }
        } catch (Throwable t) {
          log.warn("crossfade tick error for trackId={}", trackId, t);
          state.error(AudioChannel.BGM, trackId, t.getMessage());
          safeStop(next);
          synchronized (Simp3AudioService.this) {
            if (crossEngine == next) {
              crossEngine = null;
            }
            if (crossfadeTask == taskRef[0]) {
              crossfadeTask = null;
            }
          }
          if (taskRef[0] != null) taskRef[0].cancel(false);
        }
      };
      taskRef[0] = scheduler.scheduleAtFixedRate(tick, 0, 33, TimeUnit.MILLISECONDS);
      this.crossfadeTask = taskRef[0];
    } catch (Exception e) {
      log.warn("crossfadeBgm error for trackId={}", trackId, e);
      state.error(AudioChannel.BGM, trackId, e.getMessage());
    }
  }

  @Override
  public synchronized void fadeOutBgm(long ms) {
    if (bgmEngine == null || ms <= 0L) {
      stopBgm();
      return;
    }
    cancelCrossfadeTaskLocked();
    final AudioEngine engine = bgmEngine;
    final long duration = Math.max(1L, ms);
    final long start = System.nanoTime();
    final double initial = engine.getVolume();
    final ScheduledFuture<?>[] taskRef = new ScheduledFuture<?>[1];
    Runnable tick = () -> {
      double progress = Math.min(1.0, (System.nanoTime() - start) / 1_000_000.0 / duration);
      try {
        engine.setVolume(initial * (1.0 - progress));
        if (progress >= 1.0) {
          synchronized (Simp3AudioService.this) {
            if (bgmEngine == engine) stopBgm();
          }
          taskRef[0].cancel(false);
        }
      } catch (RuntimeException error) {
        state.error(AudioChannel.BGM, bgmTrack == null ? "" : bgmTrack.id, error.getMessage());
        taskRef[0].cancel(false);
      }
    };
    taskRef[0] = scheduler.scheduleAtFixedRate(tick, 0, 33, TimeUnit.MILLISECONDS);
    crossfadeTask = taskRef[0];
  }

  private AudioEngine ensureBgmEngine() {
    if (bgmEngine == null) {
      bgmEngine = newEngine();
      attachBgmSpectrumListener(bgmEngine);
      bgmEngine.setVolume(state.mix().effective(AudioChannel.BGM));
    }
    return bgmEngine;
  }

  private AudioEngine newEngine() {
    return new HybridAudioEngine();
  }

  private void playClip(String trackId, double volume, List<AudioEngine> engines, AudioChannel channel, int limit) {
    String channelName = channel.name().toLowerCase();
    try {
      ensureOpen();
      File audioFile = resolveToFile(trackId);
      if (audioFile == null || !audioFile.exists()) {
        log.warn("{} file not found for trackId={}", channelName, trackId);
        state.error(channel, trackId, "Audio asset not found");
        return;
      }

      AudioEngine engine = newEngine();
      Song song = toSong(new BgmTrack(trackId, audioFile.getAbsolutePath()));
      if (!engine.loadSong(song)) {
        log.warn("Failed to load {} trackId={} file={}", channelName, trackId, audioFile.getAbsolutePath());
        state.error(channel, trackId, "Backend could not load audio asset");
        safeStop(engine);
        return;
      }

      cleanupEngines(engines);
      while (engines.size() >= limit) {
        safeStop(engines.remove(0));
      }
      engine.setVolume(volume);
      engine.setOnSongEnded(() -> {
        safeStop(engine);
        synchronized (Simp3AudioService.this) {
          engines.remove(engine);
        }
        state.completed(channel, trackId);
      });
      engines.add(engine);
      engine.play();
      state.started(channel, trackId);
    } catch (Exception e) {
      log.debug("play{} error for trackId={}", channelName, trackId, e);
      state.error(channel, trackId, e.getMessage());
    }
  }

  private void attachBgmSpectrumListener(AudioEngine engine) {
    if (engine == null) return;
    try {
      engine.setAudioSpectrumListener(bgmSpectrumListener);
    } catch (Exception ignored) {
      // reason: audio backend operation is non-critical; failure is recoverable
      log.trace("attachBgmSpectrumListener: setAudioSpectrumListener failed: {}", ignored.toString());
    }
  }

  private Song toSong(BgmTrack track) {
    return new Song(
        0L,
        track.id,
        null,
        null,
        null,
        0L,
        track.absolutePath,
        0,
        0
    );
  }

  private Runnable createLoopCallback(AudioEngine engine) {
    return () -> {
      if (!loopBgm || bgmTrack == null || engine == null) return;
      try {
        Song song = toSong(bgmTrack);
        if (engine.loadSong(song)) {
          engine.setVolume(state.mix().effective(AudioChannel.BGM));
          engine.play();
        } else {
          log.warn("Loop reload failed for trackId={} file={}", bgmTrack.id, bgmTrack.absolutePath);
        }
      } catch (Exception e) {
        log.warn("Loop reload threw for trackId={} file={}", bgmTrack.id, bgmTrack.absolutePath, e);
      }
    };
  }

  private void cancelCrossfadeTaskLocked() {
    if (crossfadeTask != null) {
      crossfadeTask.cancel(false);
      crossfadeTask = null;
    }
    crossfadeProgress = 0.0;
  }

  private void stopEngines(List<AudioEngine> engines) {
    for (AudioEngine engine : new ArrayList<>(engines)) {
      safeStop(engine);
    }
    engines.clear();
  }

  private void applyVolume(List<AudioEngine> engines, double volume) {
    for (AudioEngine engine : new ArrayList<>(engines)) {
      try {
        engine.setVolume(volume);
      } catch (Exception e) {
        safeStop(engine);
        engines.remove(engine);
      }
    }
  }

  private void cleanupEngines(List<AudioEngine> engines) {
    Iterator<AudioEngine> it = engines.iterator();
    while (it.hasNext()) {
      AudioEngine engine = it.next();
      try {
        if (engine == null || !engine.isPlaying()) {
          safeStop(engine);
          it.remove();
        }
      } catch (Exception e) {
        safeStop(engine);
        it.remove();
      }
    }
  }

  private void safeStop(AudioEngine engine) {
    if (engine == null) return;
    try {
      engine.stop();
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    try {
      engine.dispose();
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
  }

  private File resolveToFile(String id) {
    if (id == null || id.isBlank()) return null;
    try {
      File resolved = AudioAssetResolver.resolveFile(projectRoot, id);
      if (resolved != null) return resolved;

      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      URL url = AudioAssetResolver.resolveClasspathUrl(loader, id);
      if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
        try {
          return new File(url.toURI());
        } catch (Exception ignored) {
          // reason: audio backend operation is non-critical; failure is recoverable
          log.trace("resolveToFile: URL-to-URI conversion failed for id={}: {}", id, ignored.toString());
        }
      }

      synchronized (extractedAudioCache) {
        File cached = url == null ? null : extractedAudioCache.get(url.toExternalForm());
        if (cached != null && cached.exists()) return cached;

        if (url == null) {
          log.warn("Audio resource not found for id={}", id);
          return null;
        }

        String path = url.getPath() == null ? id : url.getPath();
        String ext = ".audio";
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1) {
          ext = path.substring(dot);
        }
        File extracted = Files.createTempFile("jvn_audio_", ext).toFile();
        extracted.deleteOnExit();
        try (InputStream in = url.openStream();
             FileOutputStream out = new FileOutputStream(extracted, false)) {
          in.transferTo(out);
        }
        extractedAudioCache.put(url.toExternalForm(), extracted);
        return extracted;
      }
    } catch (Exception e) {
      log.warn("resolveToFile error for id={}", id, e);
      return null;
    }
  }

  private void applyBgmVolumes() {
    double volume = state.mix().effective(AudioChannel.BGM);
    if (crossEngine != null && crossfadeTask != null) {
      if (bgmEngine != null) bgmEngine.setVolume(volume * (1.0 - crossfadeProgress));
      crossEngine.setVolume(volume * crossfadeProgress);
      return;
    }
    if (bgmEngine != null) bgmEngine.setVolume(volume);
  }

  private void applyAllVolumes() {
    applyBgmVolumes();
    applyVolume(sfxEngines, state.mix().effective(AudioChannel.SFX));
    applyVolume(voiceEngines, state.mix().effective(AudioChannel.VOICE));
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("Audio backend is closed");
  }

  private static final class BgmTrack {
    final String id;
    final String absolutePath;

    BgmTrack(String id, String absolutePath) {
      this.id = id;
      this.absolutePath = absolutePath;
    }
  }

  @Override
  public float[] getBgmSpectrumMagnitudes() {
    float[] data = latestBgmSpectrum;
    if (data == null || data.length == 0) return null;
    return data.clone();
  }

  @Override
  public boolean supportsBgmSpectrum() {
    return true;
  }

  @Override
  public long getBgmSpectrumUpdatedAtNanos() {
    return latestBgmSpectrumUpdatedAtNanos;
  }

  @Override public String backendId() { return state.backendId(); }
  @Override public AudioCapabilities capabilities() { return state.capabilities(); }

  @Override
  public synchronized AudioSnapshot snapshot() {
    double position = 0.0;
    double duration = 0.0;
    try {
      if (bgmEngine != null) {
        position = bgmEngine.getCurrentTime();
        duration = bgmEngine.getTotalTime();
      }
    } catch (RuntimeException ignored) {
      // Snapshot collection is best-effort during backend transitions.
    }
    return state.snapshot(position, duration, sfxEngines.size(), voiceEngines.size());
  }

  @Override public void addListener(AudioListener listener) { state.addListener(listener); }
  @Override public void removeListener(AudioListener listener) { state.removeListener(listener); }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    cancelCrossfadeTaskLocked();
    stopEngines(sfxEngines);
    stopEngines(voiceEngines);
    safeStop(crossEngine);
    safeStop(bgmEngine);
    crossEngine = null;
    bgmEngine = null;
    bgmTrack = null;
    scheduler.shutdownNow();
    synchronized (extractedAudioCache) {
      for (File file : extractedAudioCache.values()) {
        try {
          Files.deleteIfExists(file.toPath());
        } catch (Exception ignored) {
          // Temporary resource cleanup is best-effort during shutdown.
        }
      }
      extractedAudioCache.clear();
    }
    state.closed();
  }
}
