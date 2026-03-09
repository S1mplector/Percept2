package com.jvn.audio.simp3;

import com.jvn.audiofx.AudioFxController;
import com.jvn.core.assets.AssetPaths;
import com.jvn.core.assets.AssetType;
import com.jvn.core.audio.AmbienceProfile;
import com.jvn.core.audio.AudioFacade;
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

  private AudioEngine bgmEngine;
  private AudioEngine crossEngine;
  private volatile boolean loopBgm = false;
  private volatile double bgmVolume = 0.7;
  private volatile double voiceVolume = 1.0;
  private volatile double sfxVolume = 0.8;
  private volatile ScheduledFuture<?> crossfadeTask = null;
  private BgmTrack bgmTrack = null;
  private final Map<String, File> extractedAudioCache = new HashMap<>();
  private volatile float[] latestBgmSpectrum;
  private volatile long latestBgmSpectrumUpdatedAtNanos;
  private final AudioFxController audioFx = new AudioFxController();
  private volatile float ambienceVolume = 0.45f;
  private volatile float chiptuneVolume = 0.70f;
  private final AudioSpectrumListener bgmSpectrumListener = (timestamp, duration, magnitudes, phases) -> {
    if (magnitudes == null || magnitudes.length == 0) return;
    float[] copy = new float[magnitudes.length];
    System.arraycopy(magnitudes, 0, copy, 0, magnitudes.length);
    latestBgmSpectrum = copy;
    latestBgmSpectrumUpdatedAtNanos = System.nanoTime();
  };
  private File projectRoot;

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
    this.bgmEngine.setVolume(bgmVolume);
    log.info("Audio FX backend -> {}", audioFx.diagnosticsSummary());
  }

  public synchronized void setProjectRoot(File root) {
    this.projectRoot = root;
  }

  @Override
  public synchronized void playBgm(String trackId, boolean loop) {
    try {
      cancelCrossfadeTaskLocked();
      File audioFile = resolveToFile(AssetType.AUDIO, trackId);
      if (audioFile == null || !audioFile.exists()) {
        log.warn("BGM file not found for trackId={}", trackId);
        return;
      }
      AudioEngine engine = ensureBgmEngine();
      BgmTrack track = new BgmTrack(trackId, audioFile.getAbsolutePath());
      Song song = toSong(track);
      if (!engine.loadSong(song)) {
        log.warn("Failed to load BGM trackId={} file={}", trackId, audioFile.getAbsolutePath());
        return;
      }

      this.loopBgm = loop;
      this.bgmTrack = track;
      engine.setOnSongEnded(createLoopCallback(engine));
      engine.setVolume(bgmVolume);
      engine.play();
    } catch (Exception e) {
      log.error("Error playing BGM {}", trackId, e);
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
      bgmEngine.setVolume(bgmVolume);
      latestBgmSpectrum = null;
      latestBgmSpectrumUpdatedAtNanos = 0L;
    } catch (Exception e) {
      log.debug("stopBgm error", e);
    }
  }

  @Override
  public synchronized void playSfx(String sfxId) {
    try {
      File audioFile = resolveToFile(AssetType.AUDIO, sfxId);
      if (audioFile == null || !audioFile.exists()) return;

      AudioEngine engine = newEngine();
      Song song = toSong(new BgmTrack(sfxId, audioFile.getAbsolutePath()));
      if (!engine.loadSong(song)) return;

      engine.setVolume(sfxVolume);
      engine.setOnSongEnded(() -> {
        safeStop(engine);
        synchronized (Simp3AudioService.this) {
          sfxEngines.remove(engine);
        }
      });
      sfxEngines.add(engine);
      cleanupEngines(sfxEngines);
      engine.play();
    } catch (Exception e) {
      log.debug("playSfx error", e);
    }
  }

  @Override
  public synchronized void playVoice(String voiceId) {
    try {
      File audioFile = resolveToFile(AssetType.AUDIO, voiceId);
      if (audioFile == null || !audioFile.exists()) return;

      AudioEngine engine = newEngine();
      Song song = toSong(new BgmTrack(voiceId, audioFile.getAbsolutePath()));
      if (!engine.loadSong(song)) return;

      engine.setVolume(voiceVolume);
      engine.setOnSongEnded(() -> {
        safeStop(engine);
        synchronized (Simp3AudioService.this) {
          voiceEngines.remove(engine);
        }
      });
      voiceEngines.add(engine);
      cleanupEngines(voiceEngines);
      engine.play();
    } catch (Exception e) {
      log.debug("playVoice error", e);
    }
  }

  @Override
  public synchronized void stopSfx() {
    for (AudioEngine engine : new ArrayList<>(sfxEngines)) {
      safeStop(engine);
    }
    sfxEngines.clear();
  }

  @Override
  public synchronized void stopVoice() {
    for (AudioEngine engine : new ArrayList<>(voiceEngines)) {
      safeStop(engine);
    }
    voiceEngines.clear();
  }

  @Override
  public synchronized void stopAllAudio() {
    stopSfx();
    stopVoice();
    stopBgm();
    stopAmbience();
    stopChiptune();
  }

  @Override
  public synchronized void setBgmVolume(float volume) {
    this.bgmVolume = clamp(volume);
    if (bgmEngine != null) bgmEngine.setVolume(bgmVolume);
  }

  @Override
  public synchronized void setSfxVolume(float volume) {
    this.sfxVolume = clamp(volume);
    for (AudioEngine engine : new ArrayList<>(sfxEngines)) {
      engine.setVolume(sfxVolume);
    }
  }

  @Override
  public synchronized void setVoiceVolume(float volume) {
    this.voiceVolume = clamp(volume);
    for (AudioEngine engine : new ArrayList<>(voiceEngines)) {
      engine.setVolume(voiceVolume);
    }
  }

  @Override
  public synchronized void pauseBgm() {
    if (bgmEngine != null) bgmEngine.pause();
  }

  @Override
  public synchronized void resumeBgm() {
    if (bgmEngine != null) {
      bgmEngine.setVolume(bgmVolume);
      bgmEngine.play();
    }
  }

  @Override
  public synchronized void pauseAllAudio() {
    pauseBgm();
    for (AudioEngine engine : new ArrayList<>(sfxEngines)) {
      try {
        engine.pause();
      } catch (Exception ignored) {
      }
    }
    for (AudioEngine engine : new ArrayList<>(voiceEngines)) {
      try {
        engine.pause();
      } catch (Exception ignored) {
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
      }
    }
    for (AudioEngine engine : new ArrayList<>(voiceEngines)) {
      try {
        engine.play();
      } catch (Exception ignored) {
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
      cancelCrossfadeTaskLocked();
      File audioFile = resolveToFile(AssetType.AUDIO, trackId);
      if (audioFile == null || !audioFile.exists()) {
        log.warn("Crossfade target BGM file not found for trackId={}", trackId);
        return;
      }

      AudioEngine next = newEngine();
      attachBgmSpectrumListener(next);
      BgmTrack nextTrack = new BgmTrack(trackId, audioFile.getAbsolutePath());
      if (!next.loadSong(toSong(nextTrack))) {
        log.warn("Crossfade failed to load target trackId={} file={}", trackId, audioFile.getAbsolutePath());
        return;
      }

      next.setVolume(0.0);
      next.play();

      final AudioEngine prev = this.bgmEngine;
      final double startVol = this.bgmVolume;
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
        return;
      }

      this.crossEngine = next;
      final ScheduledFuture<?>[] taskRef = new ScheduledFuture<?>[1];
      Runnable tick = () -> {
        try {
          long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
          double p = Math.min(1.0, Math.max(0.0, elapsedMs / (double) duration));
          double outVol = startVol * (1.0 - p);
          double inVol = startVol * p;
          if (prev != null) prev.setVolume(outVol);
          next.setVolume(inVol);
          if (p >= 1.0) {
            safeStop(prev);
            synchronized (Simp3AudioService.this) {
              bgmEngine = next;
              crossEngine = null;
              if (crossfadeTask == taskRef[0]) {
                crossfadeTask = null;
              }
            }
            next.setOnSongEnded(createLoopCallback(next));
            if (taskRef[0] != null) taskRef[0].cancel(false);
          }
        } catch (Throwable t) {
          log.warn("crossfade tick error for trackId={}", trackId, t);
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
    }
  }

  @Override
  public synchronized void playAmbience(String preset, float intensity, boolean loop) {
    playAmbience(preset, intensity, AmbienceProfile.defaults(loop));
  }

  @Override
  public synchronized void playAmbience(String preset, float intensity, AmbienceProfile profile) {
    audioFx.playAmbience(preset, clamp01(intensity), ambienceVolume, profile);
  }

  @Override
  public synchronized void stopAmbience() {
    audioFx.stopAmbience();
  }

  @Override
  public synchronized void setAmbienceVolume(float volume) {
    ambienceVolume = clamp01(volume);
    audioFx.setAmbienceVolume(ambienceVolume);
  }

  @Override
  public synchronized void playChiptune(String cueId, float intensity, boolean loop) {
    audioFx.playBeez(cueId, clamp01(intensity), chiptuneVolume, loop);
  }

  @Override
  public synchronized void stopChiptune() {
    audioFx.stopBeez();
  }

  @Override
  public synchronized void setChiptuneVolume(float volume) {
    chiptuneVolume = clamp01(volume);
    audioFx.setBeezVolume(chiptuneVolume);
  }

  private AudioEngine ensureBgmEngine() {
    if (bgmEngine == null) {
      bgmEngine = newEngine();
      attachBgmSpectrumListener(bgmEngine);
      bgmEngine.setVolume(bgmVolume);
    }
    return bgmEngine;
  }

  private AudioEngine newEngine() {
    return new HybridAudioEngine();
  }

  private void attachBgmSpectrumListener(AudioEngine engine) {
    if (engine == null) return;
    try {
      engine.setAudioSpectrumListener(bgmSpectrumListener);
    } catch (Exception ignored) {
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
          engine.setVolume(bgmVolume);
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
    }
    try {
      engine.dispose();
    } catch (Exception ignored) {
    }
  }

  private File resolveToFile(AssetType type, String id) {
    if (id == null || id.isBlank()) return null;
    String normalized = id.replace('\\', '/');
    if (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }

    File direct = new File(id);
    if (direct.exists() && direct.isFile()) return direct;

    // Also try with normalized path directly (important when id has backslashes)
    File directNormalized = new File(normalized);
    if (directNormalized.exists() && directNormalized.isFile()) return directNormalized;

    if (projectRoot != null) {
      File fromProject = new File(projectRoot, normalized);
      if (fromProject.exists() && fromProject.isFile()) return fromProject;
      String projectName = projectRoot.getName();
      if (normalized.startsWith(projectName + "/")) {
        String stripped = normalized.substring(projectName.length() + 1);
        File strippedFromProject = new File(projectRoot, stripped);
        if (strippedFromProject.exists() && strippedFromProject.isFile()) return strippedFromProject;
      }
    }

    try {
      // If a project-relative path accidentally includes the project root folder name,
      // strip it when resolving against cwd/project dir.
      String cwdName = new File(System.getProperty("user.dir", ".")).getName();
      if (normalized.startsWith(cwdName + "/")) {
        String stripped = normalized.substring(cwdName.length() + 1);
        File strippedFile = new File(stripped);
        if (strippedFile.exists() && strippedFile.isFile()) return strippedFile;
      }
    } catch (Exception ignored) {
    }

    if (type == AssetType.AUDIO) {
      if (normalized.startsWith("assets/demo/audio/")) {
        File local = new File(normalized);
        if (local.exists() && local.isFile()) return local;
      }
      if (normalized.startsWith("assets/audio/")) {
        File local = new File(normalized);
        if (local.exists() && local.isFile()) return local;
      }
      File demoAudio = new File("assets/demo/audio", normalized);
      if (demoAudio.exists() && demoAudio.isFile()) return demoAudio;
      File audioRoot = new File("assets/audio", normalized);
      if (audioRoot.exists() && audioRoot.isFile()) return audioRoot;
      File fallback = new File("game/audio", normalized);
      if (fallback.exists() && fallback.isFile()) return fallback;
    }

    try {
      String built = AssetPaths.build(type, id);
      String[] candidates = new String[] {
          built,
          normalized,
          built.startsWith("game/") ? built.substring("game/".length()) : built,
          built.startsWith("game/audio/") ? built.substring("game/audio/".length()) : built,
          "game/audio/" + normalized,
          "audio/" + normalized,
          "assets/demo/audio/" + normalized,
          "assets/audio/" + normalized,
          id
      };

      for (String c : candidates) {
        File f = new File(c);
        if (f.exists() && f.isFile()) return f;
        URL url = Thread.currentThread().getContextClassLoader().getResource(c);
        if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
          try {
            return new File(url.toURI());
          } catch (Exception ignored) {
          }
        }
      }

      synchronized (extractedAudioCache) {
        File cached = extractedAudioCache.get(built);
        if (cached != null && cached.exists()) return cached;

        InputStream in = null;
        for (String c : candidates) {
          in = Thread.currentThread().getContextClassLoader().getResourceAsStream(c);
          if (in != null) {
            break;
          }
        }

        if (in == null) {
          log.warn("Audio resource not found in classpath for asset path={} (id={})", built, id);
          return null;
        }

        String ext = ".audio";
        int dot = built.lastIndexOf('.');
        if (dot >= 0 && dot < built.length() - 1) {
          ext = built.substring(dot);
        }
        File extracted = Files.createTempFile("jvn_audio_", ext).toFile();
        extracted.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(extracted, false)) {
          in.transferTo(out);
        }
        extractedAudioCache.put(built, extracted);
        return extracted;
      }
    } catch (Exception e) {
      log.warn("resolveToFile error for assetType={} id={}", type, id, e);
      return null;
    }
  }

  private double clamp(double v) {
    if (v < 0) return 0;
    if (v > 1) return 1;
    return v;
  }

  private float clamp01(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
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
}
