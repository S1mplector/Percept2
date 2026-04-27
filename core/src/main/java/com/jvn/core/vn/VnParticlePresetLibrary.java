package com.jvn.core.vn;

import com.jvn.core.scene2d.ParticleEmitter2D;

/**
 * Maps a {@link VnParticleCommand} (preset + optional shaping parameters) onto
 * a fully-configured {@link ParticleEmitter2D}.
 *
 * <p>This is the engine-side bridge between the high-level VNS command surface
 * (e.g. {@code [pfx snow intensity=0.7 wind=20]}) and the low-level CPU
 * particle simulation. Each preset has a tuned set of defaults — emission
 * rate, lifetime, size, speed, angle, gravity, colour, blending — and the
 * library applies the command's optional shaping parameters on top:</p>
 *
 * <ul>
 *   <li><b>intensity</b> scales the emission rate and the maximum number of
 *       concurrent particles, so a lighter snowfall and a blizzard share the
 *       same preset.</li>
 *   <li><b>opacity</b> multiplies start/end alpha so users can dial weather
 *       back without re-tinting.</li>
 *   <li><b>speed</b> multiplies the velocity range so heavy rain falls
 *       faster than light rain from the same preset.</li>
 *   <li><b>wind</b> sets a horizontal acceleration on the emitter, giving
 *       drift to snow and slant to rain.</li>
 *   <li><b>tint</b> overrides the preset's start/end RGB while preserving the
 *       preset's alpha curve.</li>
 * </ul>
 *
 * <p>The library ships with tuned configurations for weather and ambience:
 * {@link VnParticleCommand.Preset#SNOW SNOW},
 * {@link VnParticleCommand.Preset#RAIN RAIN},
 * {@link VnParticleCommand.Preset#SAKURA SAKURA},
 * {@link VnParticleCommand.Preset#FIREFLIES FIREFLIES},
 * {@link VnParticleCommand.Preset#DUST DUST}, and
 * {@link VnParticleCommand.Preset#LEAVES LEAVES}.</p>
 */
public final class VnParticlePresetLibrary {

  /** A reasonable default ceiling on concurrent particles for ambient effects. */
  private static final int DEFAULT_MAX_PARTICLES = 600;

  /**
   * Reference scene width used by the presets when sizing emission ranges and
   * spawn extents. Runtimes that draw to a different surface size should still
   * see consistent behaviour; this is purely a conversion factor for tuning.
   */
  public static final double REFERENCE_SCENE_WIDTH = 1280.0;

  private VnParticlePresetLibrary() {}

  /**
   * Apply the preset and the command's shaping parameters to the given emitter.
   * Existing emitter state is overwritten in full, so callers can reuse a
   * single {@link ParticleEmitter2D} instance across commands.
   *
   * @param emitter target emitter (mutated in place)
   * @param cmd     command describing preset + optional overrides
   * @throws IllegalArgumentException if either argument is {@code null}
   */
  public static void apply(ParticleEmitter2D emitter, VnParticleCommand cmd) {
    apply(emitter, cmd, REFERENCE_SCENE_WIDTH, REFERENCE_SCENE_WIDTH * 9.0 / 16.0);
  }

  /**
   * Apply a preset using the current render surface dimensions. Weather
   * presets use these dimensions to spread particle spawn positions across the
   * visible scene instead of emitting from a single point.
   *
   * @param emitter     target emitter (mutated in place)
   * @param cmd         command describing preset + optional overrides
   * @param sceneWidth  current logical scene width
   * @param sceneHeight current logical scene height
   */
  public static void apply(ParticleEmitter2D emitter, VnParticleCommand cmd, double sceneWidth, double sceneHeight) {
    if (emitter == null) throw new IllegalArgumentException("emitter must not be null");
    if (cmd == null) throw new IllegalArgumentException("cmd must not be null");
    double width = sanitizeDimension(sceneWidth, REFERENCE_SCENE_WIDTH);
    double height = sanitizeDimension(sceneHeight, REFERENCE_SCENE_WIDTH * 9.0 / 16.0);

    // Stop sentinel — disable emission and let any in-flight particles fade.
    if (cmd.isStop() || cmd.getPreset() == VnParticleCommand.Preset.NONE) {
      emitter.setEmitting(false);
      emitter.setEmissionRate(0);
      return;
    }

    emitter.setRenderMode(ParticleEmitter2D.RenderMode.CIRCLE);
    emitter.setStreakLengthScale(0.05);

    switch (cmd.getPreset()) {
      case SNOW -> configureSnow(emitter, cmd, width, height);
      case RAIN -> configureRain(emitter, cmd, width, height);
      case SAKURA -> configureSakura(emitter, cmd, width, height);
      case FIREFLIES -> configureFireflies(emitter, cmd, width, height);
      case DUST -> configureDust(emitter, cmd, width, height);
      case LEAVES -> configureLeaves(emitter, cmd, width, height);
      default -> configureNeutral(emitter, cmd, width, height);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  SNOW
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Slow, soft, drifting white particles. No additive blending so flakes read
   * as opaque against any background. Wide horizontal angle so flakes cover
   * the scene; gentle gravity for a floaty feel.
   *
   * <p>Intensity 0.0 → 0.1× density, 1.0 → blizzard. Default 0.5 ≈ steady
   * snowfall.</p>
   */
  private static void configureSnow(ParticleEmitter2D emitter, VnParticleCommand cmd, double width, double height) {
    double i = scaleIntensity(cmd.getIntensity());
    double speedScale = cmd.getSpeedScale();

    emitter.setEmitting(true);
    emitter.setZ(cmd.getLayer());
    emitter.setPosition(width * 0.5, -height * 0.06);
    emitter.setSpawnArea(-width * 0.58, width * 0.58, -height * 0.04, height * 0.08);
    emitter.setMaxParticles(clampMax((int) Math.round(140 * i)));
    emitter.setEmissionRate(40.0 * i);

    emitter.setLifeRange(4.0, 7.5);
    emitter.setSizeRange(2.5, 6.0, 1.0);

    // Mostly downward, with a wide enough cone that flakes cover the scene
    // when the emitter is positioned near the top of the visible area.
    emitter.setAngleRange(70.0, 110.0);
    emitter.setSpeedRange(20.0 * speedScale, 55.0 * speedScale);

    emitter.setGravity(35.0);
    emitter.setWindX(cmd.getWindX());
    emitter.setAdditive(false);
    emitter.setTexture(null);

    // Soft white → nearly-white, with alpha fading to zero as flakes age.
    setColors(emitter, cmd, /*r*/1.0, /*g*/1.0, /*b*/1.0,
        /*startA*/0.95, /*endR*/0.92, /*endG*/0.94, /*endB*/1.0, /*endA*/0.0);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  RAIN
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Fast, narrow, slightly slanted streaks. Rain uses the emitter's velocity
   * streak renderer instead of dot particles, so drops read as motion-blurred
   * lines with a small bright leading edge. No additive blending; alpha tapers
   * to zero so droplets don't leave hard cutoffs near their lifetime end.
   *
   * <p>Intensity 0.0 → drizzle, 1.0 → downpour. Default 0.5 ≈ steady rain.</p>
   */
  private static void configureRain(ParticleEmitter2D emitter, VnParticleCommand cmd, double width, double height) {
    double i = scaleIntensity(cmd.getIntensity());
    double speedScale = cmd.getSpeedScale();

    emitter.setEmitting(true);
    emitter.setZ(cmd.getLayer());
    emitter.setPosition(width * 0.5, -height * 0.16);
    emitter.setSpawnArea(-width * 0.68, width * 0.68, -height * 0.10, height * 0.02);
    emitter.setMaxParticles(clampMax((int) Math.round(360 * i)));
    emitter.setEmissionRate(320.0 * i);

    emitter.setLifeRange(0.55, 0.90);
    emitter.setSizeRange(0.9, 1.7, 0.8);

    // Slightly slanted downward cone — enough directionality to feel windy
    // without turning default rain into diagonal speed lines.
    emitter.setAngleRange(82.0, 94.0);
    emitter.setSpeedRange(720.0 * speedScale, 980.0 * speedScale);

    emitter.setGravity(650.0);
    emitter.setWindX(cmd.getWindX());
    emitter.setAdditive(false);
    emitter.setTexture(null);
    emitter.setRenderMode(ParticleEmitter2D.RenderMode.STREAK);
    emitter.setStreakLengthScale(0.045);

    // Cool, slightly desaturated blue → fades transparent.
    setColors(emitter, cmd, /*r*/0.72, /*g*/0.82, /*b*/0.95,
        /*startA*/0.48, /*endR*/0.58, /*endG*/0.70, /*endB*/0.90, /*endA*/0.0);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  SAKURA
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Soft pink petals drifting across the scene. The preset uses a broad top
   * spawn strip, long lifetimes, and mostly downward angles so petals linger
   * without looking like snow. Use {@code wind=} to steer the fall across the
   * frame.
   */
  private static void configureSakura(ParticleEmitter2D emitter, VnParticleCommand cmd, double width, double height) {
    double i = scaleIntensity(cmd.getIntensity());
    double speedScale = cmd.getSpeedScale();

    emitter.setEmitting(true);
    emitter.setZ(cmd.getLayer());
    emitter.setPosition(width * 0.5, -height * 0.08);
    emitter.setSpawnArea(-width * 0.58, width * 0.58, -height * 0.02, height * 0.16);
    emitter.setMaxParticles(clampMax((int) Math.round(120 * i)));
    emitter.setEmissionRate(24.0 * i);

    emitter.setLifeRange(5.5, 9.0);
    emitter.setSizeRange(4.0, 9.0, 0.85);
    emitter.setAngleRange(68.0, 118.0);
    emitter.setSpeedRange(24.0 * speedScale, 68.0 * speedScale);
    emitter.setGravity(24.0);
    emitter.setWindX(cmd.getWindX());
    emitter.setAdditive(false);
    emitter.setTexture(null);

    setColors(emitter, cmd, /*r*/1.0, /*g*/0.70, /*b*/0.82,
        /*startA*/0.86, /*endR*/1.0, /*endG*/0.52, /*endB*/0.74, /*endA*/0.0);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  FIREFLIES
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Sparse warm glows for night scenes. Fireflies spawn in the lower-middle of
   * the viewport and rise gently with additive blending, so even low intensity
   * values remain readable without flooding the scene.
   */
  private static void configureFireflies(ParticleEmitter2D emitter, VnParticleCommand cmd, double width, double height) {
    double i = scaleIntensity(cmd.getIntensity());
    double speedScale = cmd.getSpeedScale();

    emitter.setEmitting(true);
    emitter.setZ(cmd.getLayer());
    emitter.setPosition(width * 0.5, height * 0.62);
    emitter.setSpawnArea(-width * 0.46, width * 0.46, -height * 0.20, height * 0.24);
    emitter.setMaxParticles(clampMax((int) Math.round(48 * i)));
    emitter.setEmissionRate(8.0 * i);

    emitter.setLifeRange(3.2, 6.0);
    emitter.setSizeRange(3.5, 7.0, 0.45);
    emitter.setAngleRange(210.0, 330.0);
    emitter.setSpeedRange(8.0 * speedScale, 26.0 * speedScale);
    emitter.setGravity(-10.0);
    emitter.setWindX(cmd.getWindX());
    emitter.setAdditive(true);
    emitter.setTexture(null);

    setColors(emitter, cmd, /*r*/1.0, /*g*/0.92, /*b*/0.32,
        /*startA*/0.90, /*endR*/0.55, /*endG*/1.0, /*endB*/0.42, /*endA*/0.0);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  DUST
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Subtle floating motes for sunbeams, interiors, and old rooms. Dust fills
   * the whole scene, drifts slowly upward, and uses very low alpha so it adds
   * texture without becoming visual noise.
   */
  private static void configureDust(ParticleEmitter2D emitter, VnParticleCommand cmd, double width, double height) {
    double i = scaleIntensity(cmd.getIntensity());
    double speedScale = cmd.getSpeedScale();

    emitter.setEmitting(true);
    emitter.setZ(cmd.getLayer());
    emitter.setPosition(width * 0.5, height * 0.5);
    emitter.setSpawnArea(-width * 0.52, width * 0.52, -height * 0.52, height * 0.52);
    emitter.setMaxParticles(clampMax((int) Math.round(180 * i)));
    emitter.setEmissionRate(18.0 * i);

    emitter.setLifeRange(6.0, 11.0);
    emitter.setSizeRange(1.2, 3.2, 0.65);
    emitter.setAngleRange(235.0, 305.0);
    emitter.setSpeedRange(5.0 * speedScale, 18.0 * speedScale);
    emitter.setGravity(-4.0);
    emitter.setWindX(cmd.getWindX());
    emitter.setAdditive(true);
    emitter.setTexture(null);

    setColors(emitter, cmd, /*r*/1.0, /*g*/0.88, /*b*/0.64,
        /*startA*/0.22, /*endR*/1.0, /*endG*/0.92, /*endB*/0.74, /*endA*/0.0);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  LEAVES
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Autumn leaves and broad foreground debris. Larger particles, faster fall,
   * and a long lifetime make this read as sparse leaves instead of rain.
   */
  private static void configureLeaves(ParticleEmitter2D emitter, VnParticleCommand cmd, double width, double height) {
    double i = scaleIntensity(cmd.getIntensity());
    double speedScale = cmd.getSpeedScale();

    emitter.setEmitting(true);
    emitter.setZ(cmd.getLayer());
    emitter.setPosition(width * 0.5, -height * 0.12);
    emitter.setSpawnArea(-width * 0.64, width * 0.64, -height * 0.06, height * 0.08);
    emitter.setMaxParticles(clampMax((int) Math.round(90 * i)));
    emitter.setEmissionRate(18.0 * i);

    emitter.setLifeRange(4.5, 8.0);
    emitter.setSizeRange(7.0, 15.0, 0.80);
    emitter.setAngleRange(62.0, 118.0);
    emitter.setSpeedRange(42.0 * speedScale, 96.0 * speedScale);
    emitter.setGravity(54.0);
    emitter.setWindX(cmd.getWindX());
    emitter.setAdditive(false);
    emitter.setTexture(null);

    setColors(emitter, cmd, /*r*/0.95, /*g*/0.42, /*b*/0.14,
        /*startA*/0.90, /*endR*/0.62, /*endG*/0.24, /*endB*/0.08, /*endA*/0.0);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Neutral fallback
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Minimal default for presets the library hasn't fully tuned yet — keeps
   * the emitter alive with mild settings instead of producing nothing, so
   * callers can verify the command pipeline works end-to-end while waiting
   * for a real preset implementation.
   */
  private static void configureNeutral(ParticleEmitter2D emitter, VnParticleCommand cmd, double width, double height) {
    double i = scaleIntensity(cmd.getIntensity());
    double speedScale = cmd.getSpeedScale();

    emitter.setEmitting(true);
    emitter.setZ(cmd.getLayer());
    emitter.setPosition(width * 0.5, height * 0.5);
    emitter.clearSpawnArea();
    emitter.setMaxParticles(clampMax((int) Math.round(120 * i)));
    emitter.setEmissionRate(20.0 * i);
    emitter.setLifeRange(2.0, 4.0);
    emitter.setSizeRange(2.0, 5.0, 0.6);
    emitter.setAngleRange(0.0, 360.0);
    emitter.setSpeedRange(40.0 * speedScale, 90.0 * speedScale);
    emitter.setGravity(0.0);
    emitter.setWindX(cmd.getWindX());
    emitter.setAdditive(true);
    emitter.setTexture(null);
    setColors(emitter, cmd, 1.0, 1.0, 1.0, 0.8, 1.0, 1.0, 1.0, 0.0);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Shared helpers
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Maps the linear 0..1 intensity from VNS into a slightly non-linear curve
   * so the lower half of the slider feels meaningful (a 0.1 still produces
   * visible snow, not a single flake every few seconds).
   */
  private static double scaleIntensity(float intensity) {
    double t = Math.max(0.0, Math.min(1.0, intensity));
    // Floor at 0.1 so requesting "on" with a tiny intensity still emits.
    return 0.10 + 0.90 * t;
  }

  private static int clampMax(int n) {
    return Math.max(1, Math.min(DEFAULT_MAX_PARTICLES, n));
  }

  private static double sanitizeDimension(double value, double fallback) {
    return Double.isFinite(value) && value > 1.0 ? value : fallback;
  }

  /**
   * Apply colour with optional tint override and opacity scaling. The tint
   * overrides the preset's RGB but the alpha curve from the preset is
   * preserved (so a tinted snowfall still fades out instead of leaving solid
   * particles at end-of-life).
   */
  private static void setColors(
      ParticleEmitter2D emitter,
      VnParticleCommand cmd,
      double startR, double startG, double startB, double startA,
      double endR,   double endG,   double endB,   double endA) {

    double opacity = cmd.getOpacityScale();
    Integer tint = cmd.getTintArgb();

    if (tint != null) {
      double tr = ((tint >> 16) & 0xFF) / 255.0;
      double tg = ((tint >> 8)  & 0xFF) / 255.0;
      double tb = ((tint)       & 0xFF) / 255.0;
      // Honour the alpha channel of the tint if non-zero; otherwise keep the
      // preset's start alpha (so users can specify tint=#ffaaff without
      // accidentally killing visibility).
      int tintA = (tint >>> 24) & 0xFF;
      double aMul = tintA == 0 ? 1.0 : (tintA / 255.0);
      startR = tr; startG = tg; startB = tb;
      endR   = tr; endG   = tg; endB   = tb;
      startA *= aMul;
      endA   *= aMul;
    }

    emitter.setStartColor(startR, startG, startB, startA * opacity);
    emitter.setEndColor(endR, endG, endB, endA * opacity);
  }
}
