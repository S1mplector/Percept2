package com.jvn.audiofx;

import com.jvn.audiofx.spi.AmbienceSynthProvider;
import com.jvn.audiofx.spi.ChipSynthProvider;
import com.jvn.core.audio.AmbienceProfile;

public final class AudioFxController {
  private final AmbienceSynthProvider ambience;
  private final ChipSynthProvider beez;

  public AudioFxController() {
    ensureNativeBridgeAvailable();
    this.ambience = new NativeLoomAmbienceSynth();
    this.beez = new NativeBeezChipSynth();
  }

  public void playAmbience(String preset, float intensity, float volume, boolean loop) {
    playAmbience(preset, intensity, volume, AmbienceProfile.defaults(loop));
  }

  public void playAmbience(String preset, float intensity, float volume, AmbienceProfile profile) {
    ambience.play(preset, clamp01(intensity), clamp01(volume), profile == null ? AmbienceProfile.defaults(true) : profile);
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

  public boolean nativeBridgeAvailable() {
    return AudioFxNativeBridge.isAvailable();
  }

  public String diagnosticsSummary() {
    return "ambience=" + ambience.id()
        + ", chiptune=" + beez.id()
        + ", bridge=" + AudioFxNativeBridge.diagnostics();
  }

  private static float clamp01(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }

  private static void ensureNativeBridgeAvailable() {
    if (!AudioFxNativeBridge.isAvailable()) {
      throw new IllegalStateException("AudioFX native bridge unavailable: " + AudioFxNativeBridge.diagnostics());
    }
  }
}
