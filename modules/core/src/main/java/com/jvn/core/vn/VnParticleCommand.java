package com.jvn.core.vn;

/**
 * VNS command to start or stop particle effects.
 *
 * <p>A command names a {@link Preset} (e.g. {@link Preset#SNOW SNOW},
 * {@link Preset#RAIN RAIN}) and carries optional shaping parameters that the
 * runtime applies on top of the preset's tuned defaults:</p>
 *
 * <ul>
 *   <li><b>intensity</b> — 0..1 multiplier on emission rate / particle count.</li>
 *   <li><b>layer</b> — render layer (defaults to 100, "above the scene").</li>
 *   <li><b>opacity</b> — 0..1 multiplier on per-particle alpha.</li>
 *   <li><b>speed</b> — multiplier on the preset's velocity range.</li>
 *   <li><b>wind</b> — horizontal acceleration in world units / sec² (drift).</li>
 *   <li><b>duration</b> — auto-stop in milliseconds (0 = run forever).</li>
 *   <li><b>tint</b> — packed ARGB colour override; {@code null} keeps preset colour.</li>
 *   <li><b>texture</b> — optional project/classpath sprite drawn for each particle.</li>
 *   <li><b>size</b> — multiplier on the preset's particle-size range.</li>
 *   <li><b>prewarm</b> — milliseconds of simulation run before the first rendered frame.</li>
 * </ul>
 *
 * <p>Construct with the static {@link #start(Preset)} factory for a quick
 * default, or {@link #builder(Preset)} for full control. {@link #stop()}
 * produces a sentinel command that clears the active effect.</p>
 */
public class VnParticleCommand {
  /** Avoid pathological authored warmups from stalling the render thread. */
  private static final long MAX_PREWARM_MS = 60_000L;

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

  // ── Required ────────────────────────────────────────────────────────────
  private final Preset preset;
  private final float intensity;
  private final int layer;
  private final boolean stop;

  // ── Optional shaping parameters (defaults preserve preset behaviour) ───
  private final double opacityScale;
  private final double speedScale;
  private final double windX;
  private final long durationMs;
  private final String texturePath;
  private final double sizeScale;
  private final long prewarmMs;
  /** Packed ARGB; {@code null} = use preset colour. */
  private final Integer tintArgb;

  private VnParticleCommand(Builder b, boolean stop) {
    this.preset = b.preset;
    this.intensity = clamp01(b.intensity);
    this.layer = b.layer;
    this.stop = stop;
    this.opacityScale = clamp01d(b.opacityScale);
    this.speedScale = Math.max(0.0, b.speedScale);
    this.windX = b.windX;
    this.durationMs = Math.max(0L, b.durationMs);
    this.texturePath = normalizePath(b.texturePath);
    this.sizeScale = Math.max(0.0, b.sizeScale);
    this.prewarmMs = Math.max(0L, Math.min(MAX_PREWARM_MS, b.prewarmMs));
    this.tintArgb = b.tintArgb;
  }

  /** Create a default-configured start command for the given preset. */
  public static VnParticleCommand start(Preset preset) {
    return new Builder(preset).build();
  }

  /** Create a start command with explicit intensity and layer. */
  public static VnParticleCommand start(Preset preset, float intensity, int layer) {
    return new Builder(preset).intensity(intensity).layer(layer).build();
  }

  /** Sentinel command that stops/clears the active particle effect. */
  public static VnParticleCommand stop() {
    Builder b = new Builder(Preset.NONE);
    b.intensity = 0f;
    b.layer = 0;
    return new VnParticleCommand(b, true);
  }

  /** Begin building a fully-customised start command. */
  public static Builder builder(Preset preset) {
    return new Builder(preset);
  }

  // ── Accessors ──────────────────────────────────────────────────────────
  public Preset getPreset() { return preset; }
  public float getIntensity() { return intensity; }
  public int getLayer() { return layer; }
  public boolean isStop() { return stop; }
  public double getOpacityScale() { return opacityScale; }
  public double getSpeedScale() { return speedScale; }
  public double getWindX() { return windX; }
  public long getDurationMs() { return durationMs; }
  public String getTexturePath() { return texturePath; }
  public double getSizeScale() { return sizeScale; }
  public long getPrewarmMs() { return prewarmMs; }
  public Integer getTintArgb() { return tintArgb; }

  // ── Builder ────────────────────────────────────────────────────────────

  /** Fluent builder for {@link VnParticleCommand}. */
  public static final class Builder {
    private final Preset preset;
    private float intensity = 0.5f;
    private int layer = 100;
    private double opacityScale = 1.0;
    private double speedScale = 1.0;
    private double windX = 0.0;
    private long durationMs = 0L;
    private String texturePath = null;
    private double sizeScale = 1.0;
    private long prewarmMs = 0L;
    private Integer tintArgb = null;

    Builder(Preset preset) {
      this.preset = preset == null ? Preset.NONE : preset;
    }

    public Builder intensity(float v)       { this.intensity = v; return this; }
    public Builder layer(int v)             { this.layer = v; return this; }
    public Builder opacity(double v)        { this.opacityScale = v; return this; }
    public Builder speed(double v)          { this.speedScale = v; return this; }
    public Builder wind(double v)           { this.windX = v; return this; }
    public Builder duration(long ms)        { this.durationMs = ms; return this; }
    public Builder texture(String path)      { this.texturePath = path; return this; }
    public Builder size(double scale)        { this.sizeScale = scale; return this; }
    public Builder prewarm(long ms)          { this.prewarmMs = ms; return this; }
    public Builder tint(Integer argb)       { this.tintArgb = argb; return this; }

    public VnParticleCommand build() {
      return new VnParticleCommand(this, false);
    }
  }

  private static float clamp01(float v)   { return Math.max(0f, Math.min(1f, v)); }
  private static double clamp01d(double v) { return Math.max(0.0, Math.min(1.0, v)); }

  private static String normalizePath(String path) {
    if (path == null) return null;
    String normalized = path.trim().replace('\\', '/');
    return normalized.isEmpty() ? null : normalized;
  }
}
