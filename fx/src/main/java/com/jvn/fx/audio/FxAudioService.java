package com.jvn.fx.audio;

import com.jvn.core.assets.AssetPaths;
import com.jvn.core.assets.AssetType;
import com.jvn.core.audio.AudioFacade;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FxAudioService implements AudioFacade {
  private MediaPlayer bgmPlayer;
  private final List<MediaPlayer> sfxPlayers = new ArrayList<>();
  private float bgmVolume = 0.7f;
  private float sfxVolume = 0.8f;
  private float voiceVolume = 1.0f; // currently unused channel

  @Override
  public void playBgm(String trackId, boolean loop) {
    stopBgm();
    try {
      URL url = getClass().getClassLoader().getResource(AssetPaths.build(AssetType.AUDIO, trackId));
      if (url == null) return;
      Media media = new Media(url.toExternalForm());
      bgmPlayer = new MediaPlayer(media);
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
  }

  @Override
  public void playSfx(String sfxId) {
    try {
      URL url = getClass().getClassLoader().getResource(AssetPaths.build(AssetType.AUDIO, sfxId));
      if (url == null) return;
      Media media = new Media(url.toExternalForm());
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
  }

  private double clamp(float v) {
    if (v < 0f) return 0.0;
    if (v > 1f) return 1.0;
    return v;
  }
}
