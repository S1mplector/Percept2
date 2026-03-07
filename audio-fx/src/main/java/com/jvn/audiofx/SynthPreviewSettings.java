package com.jvn.audiofx;

import com.jvn.core.audio.AmbienceProfile;

/**
 * Mutable model holding all synthesizer authoring parameters.
 * Used by the editor sidebar to drive preview playback, VNS generation,
 * and persistence of the last-used profile.
 */
public final class SynthPreviewSettings {

  public enum SynthType { AMBIENCE, CHIPTUNE }

  private SynthType type = SynthType.AMBIENCE;
  private String preset = "wind";
  private String cueId = "blip";
  private float intensity = 0.65f;
  private float volume = 0.45f;
  private boolean loop = true;
  private float detail = 0.50f;
  private float motion = 0.50f;
  private float spread = 0.50f;
  private float accent = 0.50f;

  public SynthPreviewSettings() {}

  public SynthPreviewSettings(SynthPreviewSettings other) {
    this.type = other.type;
    this.preset = other.preset;
    this.cueId = other.cueId;
    this.intensity = other.intensity;
    this.volume = other.volume;
    this.loop = other.loop;
    this.detail = other.detail;
    this.motion = other.motion;
    this.spread = other.spread;
    this.accent = other.accent;
  }

  // --- Accessors ---

  public SynthType type() { return type; }
  public void setType(SynthType type) { this.type = type == null ? SynthType.AMBIENCE : type; }

  public String preset() { return preset; }
  public void setPreset(String preset) { this.preset = preset == null ? "wind" : preset; }

  public String cueId() { return cueId; }
  public void setCueId(String cueId) { this.cueId = cueId == null ? "blip" : cueId; }

  public float intensity() { return intensity; }
  public void setIntensity(float v) { this.intensity = clamp01(v); }

  public float volume() { return volume; }
  public void setVolume(float v) { this.volume = clamp01(v); }

  public boolean loop() { return loop; }
  public void setLoop(boolean loop) { this.loop = loop; }

  public float detail() { return detail; }
  public void setDetail(float v) { this.detail = clamp01(v); }

  public float motion() { return motion; }
  public void setMotion(float v) { this.motion = clamp01(v); }

  public float spread() { return spread; }
  public void setSpread(float v) { this.spread = clamp01(v); }

  public float accent() { return accent; }
  public void setAccent(float v) { this.accent = clamp01(v); }

  /** Build an {@link AmbienceProfile} from the current ambience parameters. */
  public AmbienceProfile toAmbienceProfile() {
    return new AmbienceProfile(detail, motion, spread, accent, loop);
  }

  /** Effective mode/cue string depending on synth type. */
  public String effectiveMode() {
    return type == SynthType.CHIPTUNE ? cueId : preset;
  }

  private static float clamp01(float v) {
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }
}
