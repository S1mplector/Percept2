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
 * <p>Currently the library ships with fully-tuned configurations for
 * {@link VnParticleCommand.Preset#SNOW SNOW} and
 * {@link VnParticleCommand.Preset#RAIN RAIN}. Other presets defined on the
 * enum are reserved for future implementations and will fall back to a
 * neutral default if requested.</p>
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

    switch (cmd.getPreset()) {
      case SNOW -> configureSnow(emitter, cmd, width, height);
      case RAIN -> configureRain(emitter, cmd, width, height);
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
   * Fast, narrow, slightly slanted streaks. Strong gravity so droplets fall
   * convincingly; small particle size so they read as streaks rather than
   * blobs. No additive blending; alpha tapers to zero so droplets don't leave
   * hard cutoffs near their lifetime end.
   *
   * <p>Intensity 0.0 → drizzle, 1.0 → downpour. Default 0.5 ≈ steady rain.</p>
   */
  private static void configureRain(ParticleEmitter2D emitter, VnParticleCommand cmd, double width, double height) {
    double i = scaleIntensity(cmd.getIntensity());
    double speedScale = cmd.getSpeedScale();

    emitter.setEmitting(true);
    emitter.setZ(cmd.getLayer());
    emitter.setPosition(width * 0.5, -height * 0.10);
    emitter.setSpawnArea(-width * 0.62, width * 0.62, -height * 0.08, height * 0.04);
    emitter.setMaxParticles(clampMax((int) Math.round(260 * i)));
    emitter.setEmissionRate(220.0 * i);

    emitter.setLifeRange(0.9, 1.3);
    emitter.setSizeRange(1.4, 2.6, 0.9);

    // Narrow downward cone — rain doesn't spread the way snow does.
    emitter.setAngleRange(85.0, 95.0);
    emitter.setSpeedRange(420.0 * speedScale, 620.0 * speedScale);

    emitter.setGravity(900.0);
    emitter.setWindX(cmd.getWindX());
    emitter.setAdditive(false);
    emitter.setTexture(null);

    // Cool, slightly desaturated blue → fades transparent.
    setColors(emitter, cmd, /*r*/0.72, /*g*/0.82, /*b*/0.95,
        /*startA*/0.72, /*endR*/0.60, /*endG*/0.72, /*endB*/0.92, /*endA*/0.0);
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
