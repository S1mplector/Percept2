package com.jvn.fx.audio;

import com.jvn.core.assets.AssetPaths;
import com.jvn.core.assets.AssetType;
import com.jvn.core.audio.AudioFacade;
import com.jvn.audiofx.AudioFxController;
import javafx.scene.media.AudioSpectrumListener;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FxAudioService implements AudioFacade {
  private MediaPlayer bgmPlayer;
  private final List<MediaPlayer> sfxPlayers = new ArrayList<>();
  private final List<MediaPlayer> voicePlayers = new ArrayList<>();
  private float bgmVolume = 0.7f;
  private float sfxVolume = 0.8f;
  private float voiceVolume = 1.0f;
  private File projectRoot;
  private volatile float[] latestBgmSpectrum;
  private volatile long latestBgmSpectrumUpdatedAtNanos;
  private final AudioFxController audioFx = new AudioFxController();
  private float ambienceVolume = 0.45f;
  private float chiptuneVolume = 0.70f;
  private final AudioSpectrumListener bgmSpectrumListener = (timestamp, duration, magnitudes, phases) -> {
    if (magnitudes == null || magnitudes.length == 0) return;
    float[] copy = new float[magnitudes.length];
    System.arraycopy(magnitudes, 0, copy, 0, magnitudes.length);
    latestBgmSpectrum = copy;
    latestBgmSpectrumUpdatedAtNanos = System.nanoTime();
  };

  public void setProjectRoot(File root) { this.projectRoot = root; }

  @Override
  public void playBgm(String trackId, boolean loop) {
    stopBgm();
    try {
      String urlStr = resolveMediaUrl(trackId);
      if (urlStr == null) return;
      Media media = new Media(urlStr);
      bgmPlayer = new MediaPlayer(media);
      configureSpectrumListener(bgmPlayer);
      if (loop) bgmPlayer.setCycleCount(MediaPlayer.INDEFINITE);
      bgmPlayer.setVolume(clamp(bgmVolume));
      bgmPlayer.play();
    } catch (Exception ignored) {
    }
  }

  @Override
  public void stopBgm() {
    if (bgmPlayer != null) {
      try {
        bgmPlayer.stop();
      } finally {
        bgmPlayer.dispose();
        bgmPlayer = null;
      }
    }
    latestBgmSpectrum = null;
    latestBgmSpectrumUpdatedAtNanos = 0L;
  }

  /**
   * Stops and disposes all active channels (BGM + SFX).
   */
  public void stopAllAudio() {
    stopBgm();
    stopSfx();
    stopVoice();
    stopAmbience();
    stopChiptune();
  }

  @Override
  public void playSfx(String sfxId) {
    try {
      String urlStr = resolveMediaUrl(sfxId);
      if (urlStr == null) return;
      Media media = new Media(urlStr);
      MediaPlayer player = new MediaPlayer(media);
      player.setVolume(clamp(sfxVolume));
      player.setOnEndOfMedia(() -> {
        player.stop();
        player.dispose();
        sfxPlayers.remove(player);
      });
      sfxPlayers.add(player);
      cleanupSfx();
      player.play();
    } catch (Exception ignored) {
    }
  }

  @Override
  public void playVoice(String voiceId) {
    try {
      String urlStr = resolveMediaUrl(voiceId);
      if (urlStr == null) return;
      Media media = new Media(urlStr);
      MediaPlayer player = new MediaPlayer(media);
      player.setVolume(clamp(voiceVolume));
      player.setOnEndOfMedia(() -> {
        player.stop();
        player.dispose();
        voicePlayers.remove(player);
      });
      voicePlayers.add(player);
      cleanupVoices();
      player.play();
    } catch (Exception ignored) {
    }
  }

  @Override
  public void stopSfx() {
    for (MediaPlayer player : new ArrayList<>(sfxPlayers)) {
      try {
        player.stop();
      } catch (Exception ignored) {
      }
      try {
        player.dispose();
      } catch (Exception ignored) {
      }
    }
    sfxPlayers.clear();
  }

  @Override
  public void stopVoice() {
    for (MediaPlayer player : new ArrayList<>(voicePlayers)) {
      try {
        player.stop();
      } catch (Exception ignored) {
      }
      try {
        player.dispose();
      } catch (Exception ignored) {
      }
    }
    voicePlayers.clear();
  }

  private void cleanupSfx() {
    Iterator<MediaPlayer> it = sfxPlayers.iterator();
    while (it.hasNext()) {
      MediaPlayer p = it.next();
      MediaPlayer.Status st = p.getStatus();
      if (st == MediaPlayer.Status.STOPPED || st == MediaPlayer.Status.DISPOSED) {
        try { p.dispose(); } catch (Exception ignored) {}
        it.remove();
      }
    }
  }

  private void cleanupVoices() {
    Iterator<MediaPlayer> it = voicePlayers.iterator();
    while (it.hasNext()) {
      MediaPlayer p = it.next();
      MediaPlayer.Status st = p.getStatus();
      if (st == MediaPlayer.Status.STOPPED || st == MediaPlayer.Status.DISPOSED) {
        try { p.dispose(); } catch (Exception ignored) {}
        it.remove();
      }
    }
  }

  @Override
  public void setBgmVolume(float volume) {
    this.bgmVolume = volume;
    if (bgmPlayer != null) {
      try { bgmPlayer.setVolume(clamp(volume)); } catch (Exception ignored) {}
    }
  }

  @Override
  public void setSfxVolume(float volume) {
    this.sfxVolume = volume;
    // Apply to any still playing SFX
    for (MediaPlayer p : new ArrayList<>(sfxPlayers)) {
      try { p.setVolume(clamp(volume)); } catch (Exception ignored) {}
    }
  }

  @Override
  public void setVoiceVolume(float volume) {
    this.voiceVolume = volume;
    for (MediaPlayer p : new ArrayList<>(voicePlayers)) {
      try { p.setVolume(clamp(volume)); } catch (Exception ignored) {}
    }
  }

  @Override
  public void pauseBgm() {
    try { if (bgmPlayer != null) bgmPlayer.pause(); } catch (Exception ignored) {}
  }

  @Override
  public void resumeBgm() {
    try { if (bgmPlayer != null) { bgmPlayer.setVolume(clamp(bgmVolume)); bgmPlayer.play(); } } catch (Exception ignored) {}
  }

  @Override
  public void pauseAllAudio() {
    pauseBgm();
    for (MediaPlayer p : new ArrayList<>(sfxPlayers)) {
      try { p.pause(); } catch (Exception ignored) {}
    }
    for (MediaPlayer p : new ArrayList<>(voicePlayers)) {
      try { p.pause(); } catch (Exception ignored) {}
    }
  }

  @Override
  public void resumeAllAudio() {
    resumeBgm();
    for (MediaPlayer p : new ArrayList<>(sfxPlayers)) {
      try { p.play(); } catch (Exception ignored) {}
    }
    for (MediaPlayer p : new ArrayList<>(voicePlayers)) {
      try { p.play(); } catch (Exception ignored) {}
    }
  }

  @Override
  public void seekBgmSeconds(double seconds) {
    try { if (bgmPlayer != null && seconds >= 0) bgmPlayer.seek(javafx.util.Duration.seconds(seconds)); } catch (Exception ignored) {}
  }

  @Override
  public void crossfadeBgm(String trackId, long ms, boolean loop) {
    try {
      String urlStr = resolveMediaUrl(trackId);
      if (urlStr == null) { playBgm(trackId, loop); return; }
      final MediaPlayer oldPlayer = this.bgmPlayer;
      final MediaPlayer newPlayer = new MediaPlayer(new Media(urlStr));
      configureSpectrumListener(newPlayer);
      if (loop) newPlayer.setCycleCount(MediaPlayer.INDEFINITE);
      newPlayer.setVolume(0.0);
      newPlayer.play();

      long duration = ms <= 0 ? 1000L : ms;
      final double targetVol = clamp(this.bgmVolume);
      final int stepMs = 20;
      final int steps = (int) Math.max(1, duration / stepMs);

      Thread t = new Thread(() -> {
        try {
          for (int i = 0; i <= steps; i++) {
            double p = (double) i / (double) steps;
            double up = targetVol * p;
            double down = targetVol * (1.0 - p);
            try { newPlayer.setVolume(up); } catch (Exception ignored) {}
            if (oldPlayer != null) {
              try { oldPlayer.setVolume(down); } catch (Exception ignored) {}
            }
            Thread.sleep(stepMs);
          }
        } catch (InterruptedException ignored) {
        } finally {
          try {
            if (oldPlayer != null) {
              try { oldPlayer.stop(); } catch (Exception ignored) {}
              try { oldPlayer.dispose(); } catch (Exception ignored) {}
            }
          } finally {
            // Set the new player as active and normalize its volume to current bgmVolume
            try { newPlayer.setVolume(targetVol); } catch (Exception ignored) {}
            synchronized (FxAudioService.this) {
              FxAudioService.this.bgmPlayer = newPlayer;
            }
          }
        }
      }, "fx-bgm-crossfade");
      t.setDaemon(true);
      t.start();
    } catch (Exception ignored) {
      // Fallback if crossfade setup fails
      playBgm(trackId, loop);
    }
  }

  @Override
  public void playAmbience(String preset, float intensity, boolean loop) {
    audioFx.playAmbience(preset, intensity, ambienceVolume, loop);
  }

  @Override
  public void stopAmbience() {
    audioFx.stopAmbience();
  }

  @Override
  public void setAmbienceVolume(float volume) {
    ambienceVolume = clamp01(volume);
    audioFx.setAmbienceVolume(ambienceVolume);
  }

  @Override
  public void playChiptune(String cueId, float intensity, boolean loop) {
    audioFx.playBeez(cueId, intensity, chiptuneVolume, loop);
  }

  @Override
  public void stopChiptune() {
    audioFx.stopBeez();
  }

  @Override
  public void setChiptuneVolume(float volume) {
    chiptuneVolume = clamp01(volume);
    audioFx.setBeezVolume(chiptuneVolume);
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

  private String resolveMediaUrl(String id) {
    try {
      // 1) Classpath with asset routing (game/audio/...)
      URL url = getClass().getClassLoader().getResource(AssetPaths.build(AssetType.AUDIO, id));
      if (url != null) return url.toExternalForm();
      // 2) Raw classpath path as provided
      url = getClass().getClassLoader().getResource(id);
      if (url != null) return url.toExternalForm();
      // 3) Absolute or working-dir-relative file
      File f = new File(id);
      if (f.exists()) return f.toURI().toString();
      // 4) Project-root-relative file
      if (projectRoot != null) {
        String normalized = id.replace('\\', '/');
        String rootName = projectRoot.getName();
        if (normalized.startsWith(rootName + "/")) {
          normalized = normalized.substring(rootName.length() + 1);
        }
        File pf = new File(projectRoot, normalized);
        if (pf.exists()) return pf.toURI().toString();
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  @Override
  public float[] getBgmSpectrumMagnitudes() {
    float[] data = latestBgmSpectrum;
    if (data == null || data.length == 0) return null;
    return data.clone();
  }

  @Override
  public long getBgmSpectrumUpdatedAtNanos() {
    return latestBgmSpectrumUpdatedAtNanos;
  }

  private void configureSpectrumListener(MediaPlayer player) {
    if (player == null) return;
    try {
      player.setAudioSpectrumNumBands(64);
      player.setAudioSpectrumInterval(0.033); // ~30 FPS
      player.setAudioSpectrumThreshold(-60);
      player.setAudioSpectrumListener(bgmSpectrumListener);
    } catch (Exception ignored) {
    }
  }
}
