package com.jvn.audiofx;

import com.jvn.audiofx.spi.AmbienceSynthProvider;
import com.jvn.audiofx.spi.ChipSynthProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

public final class AudioFxController {
  private final AmbienceSynthProvider ambience;
  private final ChipSynthProvider beez;

  public AudioFxController() {
    this.ambience = loadAmbienceProvider();
    this.beez = loadChipProvider();
  }

  public void playAmbience(String preset, float intensity, float volume, boolean loop) {
    ambience.play(preset, clamp01(intensity), clamp01(volume), loop);
  }

  public void stopAmbience() {
    ambience.stop();
  }

  public void setAmbienceVolume(float volume) {
    ambience.setVolume(clamp01(volume));
  }

  public void playBeez(String cueId, float intensity, float volume, boolean loop) {
    beez.play(cueId, clamp01(intensity), clamp01(volume), loop);
  }

  public void stopBeez() {
    beez.stop();
  }

  public void setBeezVolume(float volume) {
    beez.setVolume(clamp01(volume));
  }

  public String ambienceProviderId() {
    return ambience.id();
  }

  public String beezProviderId() {
    return beez.id();
  }

  private AmbienceSynthProvider loadAmbienceProvider() {
    if (AudioFxNativeBridge.isAvailable()) {
      return new NativeLoomAmbienceSynth();
    }
    List<AmbienceSynthProvider> providers = new ArrayList<>();
    ServiceLoader.load(AmbienceSynthProvider.class).forEach(providers::add);
    AmbienceSynthProvider best = null;
    int bestRank = Integer.MAX_VALUE;
    for (AmbienceSynthProvider provider : providers) {
      String id = provider.id() == null ? "" : provider.id().trim().toLowerCase(Locale.ROOT);
      int rank = switch (id) {
        case "beez" -> 0;
        case "fx" -> 1;
        case "loom" -> 2;
        default -> 100;
      };
      if (rank < bestRank) {
        bestRank = rank;
        best = provider;
      }
    }
    if (best != null) return best;
    return new BeezAmbienceSynth();
  }

  private ChipSynthProvider loadChipProvider() {
    if (AudioFxNativeBridge.isAvailable()) {
      return new NativeBeezChipSynth();
    }
    List<ChipSynthProvider> providers = new ArrayList<>();
    ServiceLoader.load(ChipSynthProvider.class).forEach(providers::add);
    for (ChipSynthProvider provider : providers) {
      String id = provider.id() == null ? "" : provider.id().trim().toLowerCase(Locale.ROOT);
      if ("beez".equals(id)) {
        return provider;
      }
    }
    return new BeezChipSynth();
  }

  private static float clamp01(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }
}
