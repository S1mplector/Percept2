package com.jvn.audio.simp3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.assets.AssetPaths;
import com.jvn.core.assets.AssetType;
import com.jvn.core.audio.AudioFacade;

public class Simp3AudioService implements AudioFacade {
  private static final Logger log = LoggerFactory.getLogger(Simp3AudioService.class);
  private static final Simp3Bridge BRIDGE = Simp3Bridge.discover();

  private Object bgmEngine;
  private Object crossEngine;
  private volatile boolean loopBgm = false;
  private volatile double bgmVolume = 0.7;
  private volatile double voiceVolume = 1.0;
  private volatile double sfxVolume = 0.8;
  private volatile ScheduledFuture<?> crossfadeTask = null;
  private BgmTrack bgmTrack = null;
  private final Map<String, File> extractedAudioCache = new HashMap<>();

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "simp3-audio-fader");
    t.setDaemon(true);
    return t;
  });

  private final List<Object> sfxEngines = new ArrayList<>();
  private final List<Object> voiceEngines = new ArrayList<>();

  public Simp3AudioService() {
    if (!BRIDGE.available()) {
      throw new IllegalStateException("Simp3 backend unavailable: " + BRIDGE.unavailableReason());
    }
    this.bgmEngine = BRIDGE.newEngine();
    if (this.bgmEngine == null) {
      throw new IllegalStateException("Simp3 backend engine could not be created");
    }
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
      if (bgmEngine == null) {
        bgmEngine = BRIDGE.newEngine();
      }
      if (bgmEngine == null) {
        log.warn("Simp3 backend not initialized, cannot play BGM");
        return;
      }

      this.loopBgm = loop;
      this.bgmTrack = new BgmTrack(trackId, audioFile.getAbsolutePath());
      Object song = BRIDGE.newSong(this.bgmTrack);
      if (song == null) {
        log.warn("Failed to construct BGM song model for trackId={} file={}", trackId, audioFile.getAbsolutePath());
        return;
      }
      if (song == null || !BRIDGE.loadSong(bgmEngine, song)) {
        log.warn("Failed to load BGM trackId={} file={}", trackId, audioFile.getAbsolutePath());
        return;
      }
      BRIDGE.setOnSongEnded(bgmEngine, createLoopCallback(bgmEngine));
      BRIDGE.setVolume(bgmEngine, bgmVolume);
      BRIDGE.play(bgmEngine);
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
      bgmEngine = BRIDGE.newEngine();
      if (bgmEngine != null) {
        BRIDGE.setVolume(bgmEngine, bgmVolume);
      }
    } catch (Exception e) {
      log.debug("stopBgm error", e);
    }
  }

  @Override
  public synchronized void playSfx(String sfxId) {
    try {
      File audioFile = resolveToFile(AssetType.AUDIO, sfxId);
      if (audioFile == null || !audioFile.exists()) return;

      Object eng = BRIDGE.newEngine();
      if (eng == null) return;
      Object song = BRIDGE.newSong(new BgmTrack(sfxId, audioFile.getAbsolutePath()));
      if (song == null || !BRIDGE.loadSong(eng, song)) return;

      BRIDGE.setVolume(eng, sfxVolume);
      BRIDGE.setOnSongEnded(eng, () -> {
        safeStop(eng);
        synchronized (Simp3AudioService.this) {
          sfxEngines.remove(eng);
        }
      });
      sfxEngines.add(eng);
      cleanupEngines(sfxEngines);
      BRIDGE.play(eng);
    } catch (Exception e) {
      log.debug("playSfx error", e);
    }
  }

  @Override
  public synchronized void playVoice(String voiceId) {
    try {
      File audioFile = resolveToFile(AssetType.AUDIO, voiceId);
      if (audioFile == null || !audioFile.exists()) return;

      Object eng = BRIDGE.newEngine();
      if (eng == null) return;
      Object song = BRIDGE.newSong(new BgmTrack(voiceId, audioFile.getAbsolutePath()));
      if (song == null || !BRIDGE.loadSong(eng, song)) return;

      BRIDGE.setVolume(eng, voiceVolume);
      BRIDGE.setOnSongEnded(eng, () -> {
        safeStop(eng);
        synchronized (Simp3AudioService.this) {
          voiceEngines.remove(eng);
        }
      });
      voiceEngines.add(eng);
      cleanupEngines(voiceEngines);
      BRIDGE.play(eng);
    } catch (Exception e) {
      log.debug("playVoice error", e);
    }
  }

  @Override
  public synchronized void setBgmVolume(float volume) {
    this.bgmVolume = clamp(volume);
    BRIDGE.setVolume(bgmEngine, bgmVolume);
  }

  @Override
  public synchronized void setSfxVolume(float volume) {
    this.sfxVolume = clamp(volume);
    for (Object e : new ArrayList<>(sfxEngines)) {
      BRIDGE.setVolume(e, sfxVolume);
    }
  }

  @Override
  public synchronized void setVoiceVolume(float volume) {
    this.voiceVolume = clamp(volume);
    for (Object e : new ArrayList<>(voiceEngines)) {
      BRIDGE.setVolume(e, voiceVolume);
    }
  }

  @Override
  public synchronized void pauseBgm() {
    BRIDGE.pause(bgmEngine);
  }

  @Override
  public synchronized void resumeBgm() {
    BRIDGE.setVolume(bgmEngine, bgmVolume);
    BRIDGE.play(bgmEngine);
  }

  @Override
  public synchronized void seekBgmSeconds(double seconds) {
    BRIDGE.seek(bgmEngine, Math.max(0.0, seconds));
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

      Object next = BRIDGE.newEngine();
      if (next == null) {
        log.warn("Crossfade failed to create target engine for trackId={}", trackId);
        return;
      }
      BgmTrack nextTrack = new BgmTrack(trackId, audioFile.getAbsolutePath());
      Object song = BRIDGE.newSong(nextTrack);
      if (song == null) {
        log.warn("Crossfade failed to construct song model for trackId={} file={}", trackId, audioFile.getAbsolutePath());
        return;
      }
      if (!BRIDGE.loadSong(next, song)) {
        log.warn("Crossfade failed to load target trackId={} file={}", trackId, audioFile.getAbsolutePath());
        return;
      }

      BRIDGE.setVolume(next, 0.0);
      BRIDGE.play(next);

      final Object prev = this.bgmEngine;
      final double startVol = this.bgmVolume;
      final long duration = Math.max(0, ms);
      final long start = System.nanoTime();

      this.loopBgm = loop;
      this.bgmTrack = nextTrack;

      if (duration == 0) {
        safeStop(prev);
        this.bgmEngine = next;
        this.crossEngine = null;
        BRIDGE.setOnSongEnded(next, createLoopCallback(next));
        BRIDGE.setVolume(next, startVol);
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
          if (prev != null) BRIDGE.setVolume(prev, outVol);
          BRIDGE.setVolume(next, inVol);
          if (p >= 1.0) {
            safeStop(prev);
            synchronized (Simp3AudioService.this) {
              bgmEngine = next;
              crossEngine = null;
              if (crossfadeTask == taskRef[0]) {
                crossfadeTask = null;
              }
            }
            BRIDGE.setOnSongEnded(next, createLoopCallback(next));
            if (taskRef[0] != null) {
              taskRef[0].cancel(false);
            }
            return;
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
          if (taskRef[0] != null) {
            taskRef[0].cancel(false);
          }
        }
      };
      taskRef[0] = scheduler.scheduleAtFixedRate(tick, 0, 33, TimeUnit.MILLISECONDS);
      this.crossfadeTask = taskRef[0];
    } catch (Exception e) {
      log.warn("crossfadeBgm error for trackId={}", trackId, e);
    }
  }

  private Runnable createLoopCallback(Object engine) {
    return () -> {
      if (!loopBgm || bgmTrack == null) return;
      try {
        BRIDGE.seek(engine, 0.0);
        BRIDGE.play(engine);
      } catch (Exception e) {
        log.warn("Loop seek/play failed for trackId={}; trying reload", bgmTrack.id, e);
        try {
          Object song = BRIDGE.newSong(bgmTrack);
          if (song != null && BRIDGE.loadSong(engine, song)) {
            BRIDGE.play(engine);
          } else {
            log.warn("Loop reload failed for trackId={} file={}", bgmTrack.id, bgmTrack.absolutePath);
          }
        } catch (Exception reloadEx) {
          log.warn("Loop reload threw for trackId={} file={}", bgmTrack.id, bgmTrack.absolutePath, reloadEx);
        }
      }
    };
  }

  private void cancelCrossfadeTaskLocked() {
    if (crossfadeTask != null) {
      crossfadeTask.cancel(false);
      crossfadeTask = null;
    }
    if (crossEngine != null) {
      safeStop(crossEngine);
      crossEngine = null;
    }
  }

  private void cleanupEngines(List<Object> list) {
    Iterator<Object> it = list.iterator();
    while (it.hasNext()) {
      Object e = it.next();
      if (!BRIDGE.isPlaying(e)) {
        BRIDGE.dispose(e);
        it.remove();
      }
    }
  }

  private void safeStop(Object eng) {
    if (eng == null) return;
    BRIDGE.stop(eng);
    BRIDGE.dispose(eng);
  }

  private File resolveToFile(AssetType type, String id) {
    try {
      String built = AssetPaths.build(type, id);

      File cached = extractedAudioCache.get(built);
      if (cached != null && cached.exists()) {
        return cached;
      }

      URL url = getClass().getClassLoader().getResource(built);
      if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
        return new File(url.toURI());
      }
      try (InputStream in = getClass().getClassLoader().getResourceAsStream(built)) {
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

  private static final class BgmTrack {
    final String id;
    final String absolutePath;

    BgmTrack(String id, String absolutePath) {
      this.id = id;
      this.absolutePath = absolutePath;
    }
  }

  private static final class Simp3Bridge {
    private final boolean available;
    private final String unavailableReason;

    private final Constructor<?> engineCtor;
    private final Constructor<?> songCtor;
    private final Method loadSongMethod;
    private final Method playMethod;
    private final Method pauseMethod;
    private final Method stopMethod;
    private final Method seekMethod;
    private final Method setVolumeMethod;
    private final Method isPlayingMethod;
    private final Method disposeMethod;
    private final Method setOnSongEndedMethod;

    private Simp3Bridge(
        boolean available,
        String unavailableReason,
        Constructor<?> engineCtor,
        Constructor<?> songCtor,
        Method loadSongMethod,
        Method playMethod,
        Method pauseMethod,
        Method stopMethod,
        Method seekMethod,
        Method setVolumeMethod,
        Method isPlayingMethod,
        Method disposeMethod,
        Method setOnSongEndedMethod
    ) {
      this.available = available;
      this.unavailableReason = unavailableReason;
      this.engineCtor = engineCtor;
      this.songCtor = songCtor;
      this.loadSongMethod = loadSongMethod;
      this.playMethod = playMethod;
      this.pauseMethod = pauseMethod;
      this.stopMethod = stopMethod;
      this.seekMethod = seekMethod;
      this.setVolumeMethod = setVolumeMethod;
      this.isPlayingMethod = isPlayingMethod;
      this.disposeMethod = disposeMethod;
      this.setOnSongEndedMethod = setOnSongEndedMethod;
    }

    static Simp3Bridge discover() {
      try {
        Class<?> engineInterface = Class.forName("com.musicplayer.core.audio.AudioEngine");
        Class<?> engineImpl = Class.forName("com.musicplayer.core.audio.HybridAudioEngine");
        Class<?> songClass = Class.forName("com.musicplayer.data.models.Song");

        Constructor<?> engineCtor = engineImpl.getDeclaredConstructor();
        Constructor<?> songCtor = songClass.getConstructor(
            long.class,
            String.class,
            String.class,
            String.class,
            String.class,
            long.class,
            String.class,
            int.class,
            int.class
        );

        Method loadSong = engineInterface.getMethod("loadSong", songClass);
        Method play = engineInterface.getMethod("play");
        Method pause = engineInterface.getMethod("pause");
        Method stop = engineInterface.getMethod("stop");
        Method seek = engineInterface.getMethod("seek", double.class);
        Method setVolume = engineInterface.getMethod("setVolume", double.class);
        Method isPlaying = engineInterface.getMethod("isPlaying");
        Method dispose = engineInterface.getMethod("dispose");
        Method setOnSongEnded = engineInterface.getMethod("setOnSongEnded", Runnable.class);

        return new Simp3Bridge(
            true,
            null,
            engineCtor,
            songCtor,
            loadSong,
            play,
            pause,
            stop,
            seek,
            setVolume,
            isPlaying,
            dispose,
            setOnSongEnded
        );
      } catch (Throwable t) {
        return new Simp3Bridge(
            false,
            t.toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
      }
    }

    boolean available() {
      return available;
    }

    String unavailableReason() {
      return unavailableReason == null ? "unknown" : unavailableReason;
    }

    Object newEngine() {
      if (!available || engineCtor == null) return null;
      try {
        return engineCtor.newInstance();
      } catch (Exception e) {
        return null;
      }
    }

    Object newSong(BgmTrack track) {
      if (!available || songCtor == null || track == null) return null;
      try {
        return songCtor.newInstance(
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
      } catch (Exception e) {
        return null;
      }
    }

    boolean loadSong(Object engine, Object song) {
      Object out = invoke(loadSongMethod, engine, song);
      return out instanceof Boolean b && b;
    }

    void play(Object engine) {
      invoke(playMethod, engine);
    }

    void pause(Object engine) {
      invoke(pauseMethod, engine);
    }

    void stop(Object engine) {
      invoke(stopMethod, engine);
    }

    void seek(Object engine, double seconds) {
      invoke(seekMethod, engine, seconds);
    }

    void setVolume(Object engine, double volume) {
      invoke(setVolumeMethod, engine, volume);
    }

    boolean isPlaying(Object engine) {
      Object out = invoke(isPlayingMethod, engine);
      return out instanceof Boolean b && b;
    }

    void dispose(Object engine) {
      invoke(disposeMethod, engine);
    }

    void setOnSongEnded(Object engine, Runnable callback) {
      invoke(setOnSongEndedMethod, engine, callback);
    }

    private Object invoke(Method method, Object target, Object... args) {
      if (!available || method == null || target == null) return null;
      try {
        return method.invoke(target, args);
      } catch (Exception e) {
        return null;
      }
    }
  }
}
