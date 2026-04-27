package com.jvn.core.vn;

/**
 * VNS command to start or stop particle effects.
 * Preset-based particle configuration with optional intensity and layer overrides.
 */
public class VnParticleCommand {

  public enum Preset {
    SNOW,
    RAIN,
    SAKURA,
    FIREFLIES,
    DUST,
    LEAVES,
    NONE;

    public static Preset parse(String token) {
      if (token == null || token.isBlank()) return NONE;
      return switch (token.trim().toUpperCase(java.util.Locale.ENGLISH)) {
        case "SNOW" -> SNOW;
        case "RAIN" -> RAIN;
        case "SAKURA", "PETALS", "CHERRY" -> SAKURA;
        case "FIREFLIES", "FIREFLY" -> FIREFLIES;
        case "DUST", "MOTES" -> DUST;
        case "LEAVES", "LEAF" -> LEAVES;
        case "NONE", "OFF", "STOP", "CLEAR" -> NONE;
        default -> NONE;
      };
    }
  }

  private final Preset preset;
  private final float intensity;   // 0.0 to 1.0, default 0.5
  private final int layer;         // render layer, default 100
  private final boolean stop;

  private VnParticleCommand(Preset preset, float intensity, int layer, boolean stop) {
    this.preset = preset;
    this.intensity = Math.max(0f, Math.min(1f, intensity));
    this.layer = layer;
    this.stop = stop;
  }

  public static VnParticleCommand start(Preset preset, float intensity, int layer) {
    return new VnParticleCommand(preset, intensity, layer, false);
  }

  public static VnParticleCommand start(Preset preset) {
    return new VnParticleCommand(preset, 0.5f, 100, false);
  }

  public static VnParticleCommand stop() {
    return new VnParticleCommand(Preset.NONE, 0f, 0, true);
  }

  public Preset getPreset() { return preset; }
  public float getIntensity() { return intensity; }
  public int getLayer() { return layer; }
  public boolean isStop() { return stop; }
}
