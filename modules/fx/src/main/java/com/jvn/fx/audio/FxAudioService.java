package com.jvn.fx.audio;

import com.jvn.core.assets.AudioAssetResolver;
import com.jvn.core.audio.AudioFacade;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.media.AudioSpectrumListener;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FxAudioService implements AudioFacade {
  private static final Logger log = LoggerFactory.getLogger(FxAudioService.class);

  private MediaPlayer bgmPlayer;
  private MediaPlayer crossfadePlayer;
  private Timeline bgmCrossfadeTimeline;
  private double bgmCrossfadeProgress;
  private final List<MediaPlayer> sfxPlayers = new ArrayList<>();
  private final List<MediaPlayer> voicePlayers = new ArrayList<>();
  private float bgmVolume = 0.7f;
  private float sfxVolume = 0.8f;
  private float voiceVolume = 1.0f;
  private File projectRoot;
  private volatile float[] latestBgmSpectrum;
  private volatile long latestBgmSpectrumUpdatedAtNanos;
  private final AudioSpectrumListener bgmSpectrumListener = (timestamp, duration, magnitudes, phases) -> {
    if (magnitudes == null || magnitudes.length == 0) return;
    float[] copy = new float[magnitudes.length];
    System.arraycopy(magnitudes, 0, copy, 0, magnitudes.length);
    latestBgmSpectrum = copy;
    latestBgmSpectrumUpdatedAtNanos = System.nanoTime();
  };

  public void setProjectRoot(File root) {
    this.projectRoot = root;
  }

  public FxAudioService() {
  }

  @Override
  public void playBgm(String trackId, boolean loop) {
    runOnFxThread(() -> playBgmFx(trackId, loop));
  }

  @Override
  public void stopBgm() {
    runOnFxThread(this::stopBgmFx);
  }

  @Override
  public void stopAllAudio() {
    runOnFxThread(() -> {
      stopBgmFx();
      stopPlayersFx(sfxPlayers);
      stopPlayersFx(voicePlayers);
    });
  }

  @Override
  public void playSfx(String sfxId) {
    runOnFxThread(() -> playClipFx(sfxId, sfxVolume, sfxPlayers, "sfx"));
  }

  @Override
  public void playVoice(String voiceId) {
    runOnFxThread(() -> playClipFx(voiceId, voiceVolume, voicePlayers, "voice"));
  }

  @Override
  public void stopSfx() {
    runOnFxThread(() -> stopPlayersFx(sfxPlayers));
  }

  @Override
  public void stopVoice() {
    runOnFxThread(() -> stopPlayersFx(voicePlayers));
  }

  @Override
  public void setBgmVolume(float volume) {
    this.bgmVolume = clamp01(volume);
    runOnFxThread(this::applyBgmVolumesFx);
  }

  @Override
  public void setSfxVolume(float volume) {
    this.sfxVolume = clamp01(volume);
    runOnFxThread(() -> applyVolumeFx(sfxPlayers, clamp(sfxVolume)));
  }

  @Override
  public void setVoiceVolume(float volume) {
    this.voiceVolume = clamp01(volume);
    runOnFxThread(() -> applyVolumeFx(voicePlayers, clamp(voiceVolume)));
  }

  @Override
  public void pauseBgm() {
    runOnFxThread(() -> {
      try {
        if (bgmPlayer != null) bgmPlayer.pause();
      } catch (Exception e) {
        log.debug("pauseBgm error", e);
      }
    });
  }

  @Override
  public void resumeBgm() {
    runOnFxThread(() -> {
      try {
        if (bgmPlayer != null) {
          applyBgmVolumesFx();
          bgmPlayer.play();
        }
      } catch (Exception e) {
        log.debug("resumeBgm error", e);
      }
    });
  }

  @Override
  public void pauseAllAudio() {
    runOnFxThread(() -> {
      try {
        if (bgmPlayer != null) bgmPlayer.pause();
      } catch (Exception e) {
        log.debug("pauseAllAudio bgm error", e);
      }
      pausePlayersFx(sfxPlayers);
      pausePlayersFx(voicePlayers);
    });
  }

  @Override
  public void resumeAllAudio() {
    runOnFxThread(() -> {
      try {
        if (bgmPlayer != null) {
          applyBgmVolumesFx();
          bgmPlayer.play();
        }
      } catch (Exception e) {
        log.debug("resumeAllAudio bgm error", e);
      }
      playPlayersFx(sfxPlayers);
      playPlayersFx(voicePlayers);
    });
  }

  @Override
  public void seekBgmSeconds(double seconds) {
    if (seconds < 0) return;
    runOnFxThread(() -> {
      try {
        if (bgmPlayer != null) bgmPlayer.seek(Duration.seconds(seconds));
      } catch (Exception e) {
        log.debug("seekBgmSeconds error", e);
      }
    });
  }

  @Override
  public void crossfadeBgm(String trackId, long ms, boolean loop) {
    runOnFxThread(() -> crossfadeBgmFx(trackId, ms, loop));
  }

  private void playBgmFx(String trackId, boolean loop) {
    cancelCrossfadeFx();
    MediaPlayer next = createPlayer(trackId, loop, clamp(bgmVolume), true, "bgm");
    if (next == null) return;

    stopPlayer(bgmPlayer);
    bgmPlayer = next;
    resetSpectrumData();
    next.play();
  }

  private void stopBgmFx() {
    cancelCrossfadeFx();
    stopPlayer(bgmPlayer);
    bgmPlayer = null;
    resetSpectrumData();
  }

  private void playClipFx(String trackId, float volume, List<MediaPlayer> players, String channelName) {
    cleanupStoppedPlayers(players);
    MediaPlayer player = createPlayer(trackId, false, clamp(volume), false, channelName);
    if (player == null) return;

    player.setOnEndOfMedia(() -> {
      stopPlayer(player);
      players.remove(player);
    });
    players.add(player);
    player.play();
  }

  private void crossfadeBgmFx(String trackId, long ms, boolean loop) {
    cancelCrossfadeFx();

    MediaPlayer next = createPlayer(trackId, loop, 0.0, true, "bgm");
    if (next == null) {
      playBgmFx(trackId, loop);
      return;
    }

    MediaPlayer previous = bgmPlayer;
    if (previous == null || ms <= 0L) {
      stopPlayer(previous);
      bgmPlayer = next;
      bgmCrossfadeProgress = 1.0;
      next.setVolume(clamp(bgmVolume));
      next.play();
      return;
    }

    final long startedAtNanos = System.nanoTime();
    final long durationMs = Math.max(1L, ms);
    final Timeline[] timelineRef = new Timeline[1];

    next.play();
    crossfadePlayer = next;
    bgmCrossfadeProgress = 0.0;

    KeyFrame tick = new KeyFrame(Duration.millis(33), event ->
        updateCrossfadeFx(previous, next, startedAtNanos, durationMs, timelineRef[0]));
    timelineRef[0] = new Timeline(new KeyFrame(Duration.ZERO, event ->
        updateCrossfadeFx(previous, next, startedAtNanos, durationMs, timelineRef[0])), tick);
    timelineRef[0].setCycleCount(Timeline.INDEFINITE);
    bgmCrossfadeTimeline = timelineRef[0];
    timelineRef[0].play();
  }

  private void updateCrossfadeFx(
      MediaPlayer previous,
      MediaPlayer next,
      long startedAtNanos,
      long durationMs,
      Timeline timeline
  ) {
    if (timeline == null || timeline != bgmCrossfadeTimeline || next != crossfadePlayer) {
      return;
    }

    double progress = Math.min(1.0, Math.max(0.0, (System.nanoTime() - startedAtNanos) / 1_000_000.0 / durationMs));
    bgmCrossfadeProgress = progress;

    double targetVolume = clamp(bgmVolume);
    safeSetVolume(previous, targetVolume * (1.0 - progress));
    safeSetVolume(next, targetVolume * progress);

    if (progress >= 1.0) {
      timeline.stop();
      stopPlayer(previous);
      bgmPlayer = next;
      crossfadePlayer = null;
      bgmCrossfadeTimeline = null;
      bgmCrossfadeProgress = 1.0;
      safeSetVolume(next, targetVolume);
    }
  }

  private MediaPlayer createPlayer(String trackId, boolean loop, double volume, boolean spectrum, String channelName) {
    String urlStr = resolveMediaUrl(trackId);
    if (urlStr == null) {
      log.warn("{} media not found for trackId={}", channelName, trackId);
      return null;
    }

    try {
      MediaPlayer player = new MediaPlayer(new Media(urlStr));
      if (loop) player.setCycleCount(MediaPlayer.INDEFINITE);
      player.setVolume(volume);
      player.setOnError(() -> log.warn("{} playback error for trackId={}: {}", channelName, trackId, player.getError()));
      if (spectrum) configureSpectrumListener(player);
      return player;
    } catch (Exception e) {
      log.warn("Failed to create {} player for trackId={}", channelName, trackId, e);
      return null;
    }
  }

  private void cancelCrossfadeFx() {
    if (bgmCrossfadeTimeline != null) {
      bgmCrossfadeTimeline.stop();
      bgmCrossfadeTimeline = null;
    }
    if (crossfadePlayer != null) {
      stopPlayer(crossfadePlayer);
      crossfadePlayer = null;
    }
    bgmCrossfadeProgress = 0.0;
  }

  private void stopPlayersFx(List<MediaPlayer> players) {
    for (MediaPlayer player : new ArrayList<>(players)) {
      stopPlayer(player);
    }
    players.clear();
  }

  private void pausePlayersFx(List<MediaPlayer> players) {
    for (MediaPlayer player : new ArrayList<>(players)) {
      try {
        player.pause();
      } catch (Exception e) {
        log.debug("pausePlayers error", e);
      }
    }
  }

  private void playPlayersFx(List<MediaPlayer> players) {
    for (MediaPlayer player : new ArrayList<>(players)) {
      try {
        player.play();
      } catch (Exception e) {
        log.debug("playPlayers error", e);
      }
    }
  }

  private void cleanupStoppedPlayers(List<MediaPlayer> players) {
    Iterator<MediaPlayer> it = players.iterator();
    while (it.hasNext()) {
      MediaPlayer player = it.next();
      MediaPlayer.Status status;
      try {
        status = player.getStatus();
      } catch (Exception e) {
        stopPlayer(player);
        it.remove();
        continue;
      }
      if (status == MediaPlayer.Status.STOPPED
          || status == MediaPlayer.Status.DISPOSED
          || status == MediaPlayer.Status.HALTED) {
        stopPlayer(player);
        it.remove();
      }
    }
  }

  private void applyBgmVolumesFx() {
    double target = clamp(bgmVolume);
    if (crossfadePlayer != null && bgmCrossfadeTimeline != null) {
      safeSetVolume(bgmPlayer, target * (1.0 - bgmCrossfadeProgress));
      safeSetVolume(crossfadePlayer, target * bgmCrossfadeProgress);
      return;
    }
    safeSetVolume(bgmPlayer, target);
  }

  private void applyVolumeFx(List<MediaPlayer> players, double volume) {
    for (MediaPlayer player : new ArrayList<>(players)) {
      safeSetVolume(player, volume);
    }
  }

  private void stopPlayer(MediaPlayer player) {
    if (player == null) return;
    try {
      player.stop();
    } catch (Exception e) {
      log.debug("stopPlayer error", e);
    }
    try {
      player.dispose();
    } catch (Exception e) {
      log.debug("disposePlayer error", e);
    }
  }

  private void safeSetVolume(MediaPlayer player, double volume) {
    if (player == null) return;
    try {
      player.setVolume(volume);
    } catch (Exception e) {
      log.debug("setVolume error", e);
    }
  }

  private String resolveMediaUrl(String id) {
    try {
      File file = AudioAssetResolver.resolveFile(projectRoot, id);
      if (file != null) return file.toURI().toString();

      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      if (loader == null) loader = getClass().getClassLoader();
      URL url = AudioAssetResolver.resolveClasspathUrl(loader, id);
      return url == null ? null : url.toExternalForm();
    } catch (Exception e) {
      log.warn("resolveMediaUrl error for id={}", id, e);
      return null;
    }
  }

  private void resetSpectrumData() {
    latestBgmSpectrum = null;
    latestBgmSpectrumUpdatedAtNanos = 0L;
  }

  private void runOnFxThread(Runnable action) {
    if (action == null) return;
    try {
      if (Platform.isFxApplicationThread()) {
        action.run();
      } else {
        Platform.runLater(action);
      }
    } catch (IllegalStateException ignored) {
            // reason: JavaFX state race on shutdown; not actionable at call site
      action.run();
    }
  }

  private float clamp01(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }

  private double clamp(float v) {
    if (v < 0f) return 0.0;
    if (v > 1f) return 1.0;
    return v;
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

  private void configureSpectrumListener(MediaPlayer player) {
    if (player == null) return;
    try {
      player.setAudioSpectrumNumBands(64);
      player.setAudioSpectrumInterval(0.033);
      player.setAudioSpectrumThreshold(-60);
      player.setAudioSpectrumListener(bgmSpectrumListener);
    } catch (Exception e) {
      log.debug("configureSpectrumListener error", e);
    }
  }
}
